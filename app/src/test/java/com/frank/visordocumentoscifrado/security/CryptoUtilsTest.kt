package com.frank.visordocumentoscifrado.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoUtilsTest {
    @Test
    fun base64_roundTrip() {
        val original = "Visor Seguro".toByteArray()
        assertArrayEquals(original, CryptoUtils.b64d(CryptoUtils.b64(original)))
    }

    @Test
    fun sha256_isStable() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            CryptoUtils.sha256Hex("abc")
        )
    }

    @Test
    fun secureEquals_distinguishesDifferentValues() {
        assertTrue(CryptoUtils.secureEquals("firma", "firma"))
        assertFalse(CryptoUtils.secureEquals("firma", "otra"))
    }
}
