package com.frank.visordocumentoscifrado.security

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Utilidades criptográficas pequeñas y testeables sin depender de android.util.Base64. */
object CryptoUtils {
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun b64d(text: String): ByteArray = Base64.getDecoder().decode(text.trim())

    fun hmacSha256(key: ByteArray, data: String): String =
        hmacSha256Base64(key, data.toByteArray(Charsets.UTF_8))

    fun hmacSha256Base64(key: ByteArray, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return b64(mac.doFinal(data))
    }

    /** Comparación resistente a diferencias de tiempo para firmas/códigos autenticados. */
    fun secureEquals(left: String, right: String): Boolean =
        MessageDigest.isEqual(
            left.toByteArray(Charsets.UTF_8),
            right.toByteArray(Charsets.UTF_8)
        )

    fun aesGcmDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        cipherText: ByteArray,
        aad: ByteArray? = null
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(cipherText)
    }
}
