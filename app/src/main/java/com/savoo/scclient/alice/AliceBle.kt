package com.savoo.scclient.alice

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.savoo.scclient.data.local.openSecurePrefsStrict
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class AliceBle @Inject constructor(@ApplicationContext private val context: Context) {
    private val settings = context.getSharedPreferences("debug_experiments", Context.MODE_PRIVATE)
    private val securePrefs by lazy { openSecurePrefsStrict(context, "alice_ble_secure_prefs") }
    private val _enabled = MutableStateFlow(settings.getBoolean("alice_ble", false))
    private val _key = MutableStateFlow<ByteArray?>(if (_enabled.value) loadKey() else null)
    val enabled = _enabled.asStateFlow()
    val key = _key.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        if (enabled) _key.value = loadKey()
        settings.edit().putBoolean("alice_ble", enabled).apply()
        _enabled.value = enabled
        if (!enabled) _key.value = null
    }

    fun saveKey(code: String): Boolean {
        if (!_enabled.value) return false
        val normalized = code.filterNot { it.isWhitespace() }.lowercase()
        val parsed = parseAliceKey(normalized) ?: return false
        val saved = runCatching { securePrefs.edit().putString("station_key", normalized).apply() }.isSuccess
        if (saved) _key.value = parsed
        return saved
    }

    private fun loadKey(): ByteArray? =
        runCatching { securePrefs.getString("station_key", null)?.let(::parseAliceKey) }.getOrNull()

    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun nearby(key: ByteArray) = callbackFlow<BluetoothDevice?> {
        if (!hasPermissions()) {
            close()
            return@callbackFlow
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            close()
            return@callbackFlow
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(aliceServiceUuid(key))).build()
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        var scanner: BluetoothLeScanner? = null
        var nearby: BluetoothDevice? = null
        var lastSeen = 0L
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                launch {
                    val limit = if (nearby == result.device) -77 else -70
                    if (result.rssi >= limit) {
                        lastSeen = SystemClock.elapsedRealtime()
                        if (nearby != result.device) {
                            nearby = result.device
                            trySend(result.device)
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                launch {
                    scanner = null
                    nearby = null
                    trySend(null)
                }
            }
        }

        fun start() {
            if (scanner != null || !hasPermissions()) return
            runCatching {
                adapter.bluetoothLeScanner?.let {
                    it.startScan(listOf(filter), scanSettings, callback)
                    scanner = it
                }
            }
        }

        fun stop() {
            runCatching { scanner?.stopScan(callback) }
            scanner = null
            nearby = null
            trySend(null)
        }

        val bluetoothChanges = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_ON -> start()
                    BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> stop()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            bluetoothChanges,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        start()
        val expire = launch {
            while (isActive) {
                delay(1_000)
                if (nearby != null && SystemClock.elapsedRealtime() - lastSeen >= 7_000) {
                    nearby = null
                    trySend(null)
                }
            }
        }
        awaitClose {
            expire.cancel()
            stop()
            context.unregisterReceiver(bluetoothChanges)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun send(device: BluetoothDevice, key: ByteArray, trackId: Long): Boolean {
        if (!_enabled.value || _key.value !== key || !hasPermissions() || trackId <= 0L) return false
        val uuid = aliceServiceUuid(key)
        val result = CompletableDeferred<Boolean>()
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    result.complete(false)
                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (!runCatching { gatt.discoverServices() }.getOrDefault(false)) result.complete(false)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val challenge = gatt.getService(uuid)?.getCharacteristic(CHALLENGE_UUID)
                if (status != BluetoothGatt.GATT_SUCCESS || challenge == null ||
                    !runCatching { gatt.readCharacteristic(challenge) }.getOrDefault(false)
                ) {
                    result.complete(false)
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                if (characteristic.uuid == CHALLENGE_UUID) reply(gatt, value, status)
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (Build.VERSION.SDK_INT < 33 && characteristic.uuid == CHALLENGE_UUID) {
                    reply(gatt, characteristic.value, status)
                }
            }

            @Suppress("DEPRECATION")
            private fun reply(gatt: BluetoothGatt, nonce: ByteArray?, status: Int) {
                val play = gatt.getService(uuid)?.getCharacteristic(PLAY_UUID)
                if (_key.value !== key || play == null || status != BluetoothGatt.GATT_SUCCESS || nonce?.size != 16) {
                    result.complete(false)
                    return
                }
                val accepted = runCatching {
                    val payload = alicePlayPayload(key, nonce, trackId)
                    if (Build.VERSION.SDK_INT >= 33) {
                        gatt.writeCharacteristic(play, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                            BluetoothStatusCodes.SUCCESS
                    } else {
                        play.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        play.value = payload
                        gatt.writeCharacteristic(play)
                    }
                }.getOrDefault(false)
                if (!accepted) result.complete(false)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (characteristic.uuid == PLAY_UUID) result.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        val gatt = runCatching { device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) }
            .getOrNull() ?: return false
        return try {
            withTimeoutOrNull(20_000) { result.await() } ?: false
        } finally {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }
}

private val CHALLENGE_UUID = UUID.fromString("e1750de0-a21e-4254-a53f-33783cdd4354")
private val PLAY_UUID = UUID.fromString("e1750de1-a21e-4254-a53f-33783cdd4354")

private fun parseAliceKey(code: String): ByteArray? {
    if (code.length != 32 || code.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
    return ByteArray(16) { i -> code.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

internal fun aliceServiceUuid(key: ByteArray): UUID {
    val digest = MessageDigest.getInstance("SHA-256").digest("ysc-alice-service-v1".toByteArray() + key)
    digest[6] = ((digest[6].toInt() and 0x0f) or 0x80).toByte()
    digest[8] = ((digest[8].toInt() and 0x3f) or 0x80).toByte()
    val bytes = ByteBuffer.wrap(digest)
    return UUID(bytes.long, bytes.long)
}

internal fun alicePlayPayload(key: ByteArray, nonce: ByteArray, trackId: Long): ByteArray {
    val id = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(trackId).array()
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    mac.update("ysc-alice-play-v1".toByteArray())
    mac.update(nonce)
    return id + mac.doFinal(id).copyOf(12)
}
