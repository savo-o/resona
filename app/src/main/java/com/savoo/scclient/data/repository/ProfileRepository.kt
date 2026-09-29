package com.savoo.scclient.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.media.ExifInterface
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.remote.BridgeResponse
import com.savoo.scclient.data.remote.SoundCloudApi
import com.savoo.scclient.data.remote.WebViewApiBridge
import com.savoo.scclient.debug.DebugLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ProfileFields(
    val username: String,
    val firstName: String,
    val lastName: String,
    val city: String,
    val description: String,
)

fun User.toProfileFields(): ProfileFields = ProfileFields(
    username = username,
    firstName = firstName.orEmpty(),
    lastName = lastName.orEmpty(),
    city = city.orEmpty(),
    description = description.orEmpty(),
)

class ProfileUpdateException(val code: Int, val serverMessage: String?) :
    Exception(serverMessage ?: "HTTP $code")

@Singleton
class ProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SoundCloudApi,
    private val webBridge: WebViewApiBridge,
) {
    suspend fun updateProfile(original: ProfileFields, edited: ProfileFields) {
        val changes = JSONObject()
        if (edited.username != original.username) changes.put("username", edited.username)
        if (edited.firstName != original.firstName) changes.put("first_name", edited.firstName)
        if (edited.lastName != original.lastName) changes.put("last_name", edited.lastName)
        if (edited.city != original.city) changes.put("city", edited.city)
        if (edited.description != original.description) changes.put("description", edited.description)
        if (changes.length() == 0) return
        val response = webBridge.updateMe(changes.toString())
        DebugLog.log(TAG, "updateMe(${changes.keys().asSequence().toList()}) -> ${response.code}")
        response.throwIfFailed()
    }

    suspend fun uploadAvatar(uri: Uri) {
        val base64 = withContext(Dispatchers.Default) { encodeAvatar(uri) }
        val response = webBridge.uploadAvatar(base64)
        DebugLog.log(TAG, "uploadAvatar(${base64.length} chars) -> ${response.code}")
        response.throwIfFailed()
    }

    suspend fun deleteAvatar() {
        val response = webBridge.deleteAvatar()
        DebugLog.log(TAG, "deleteAvatar -> ${response.code}")
        if (response.code != 404) response.throwIfFailed()
    }

    suspend fun reloadMe(): User = api.getMe()

    private fun BridgeResponse.throwIfFailed() {
        if (!isSuccess) throw ProfileUpdateException(code, serverMessage(body))
    }

    private fun serverMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return body.take(200)
        val errors = json.opt("errors")
        val messages = when (errors) {
            is JSONArray -> (0 until errors.length()).mapNotNull { index ->
                when (val item = errors.opt(index)) {
                    is JSONObject -> item.optString("error_message").ifBlank { item.optString("message") }.ifBlank { null }
                    is String -> item
                    else -> null
                }
            }
            is JSONObject -> errors.keys().asSequence().map { key -> "$key: ${errors.opt(key)}" }.toList()
            else -> listOfNotNull(json.optString("error").ifBlank { null }, json.optString("message").ifBlank { null })
        }
        return messages.joinToString("; ").ifBlank { null }
    }

    private fun encodeAvatar(uri: Uri): String {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= AVATAR_SIZE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Cannot decode image")
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
        val side = minOf(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            val scale = AVATAR_SIZE.toFloat() / side
            postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val square = Bitmap.createBitmap(
            decoded,
            (decoded.width - side) / 2,
            (decoded.height - side) / 2,
            side,
            side,
            matrix,
            true,
        )
        val bytes = ByteArrayOutputStream().use { out ->
            square.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.toByteArray()
        }
        if (square !== decoded) square.recycle()
        decoded.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private companion object {
        const val TAG = "ProfileRepository"
        const val AVATAR_SIZE = 500
    }
}
