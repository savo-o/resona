package com.savoo.scclient.alice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AliceBleProtocolTest {
    @Test
    fun matchesStationProtocol() {
        val key = ByteArray(16) { it.toByte() }
        val nonce = ByteArray(16) { (it + 16).toByte() }
        val expected = "00000000075bcd15afac5214c8521d47bccebec4".hex()

        assertEquals("f99a3e68-1319-8924-8ba9-8d7d81441000", aliceServiceUuid(key).toString())
        assertArrayEquals(expected, alicePlayPayload(key, nonce, 123456789))
    }

    private fun String.hex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
