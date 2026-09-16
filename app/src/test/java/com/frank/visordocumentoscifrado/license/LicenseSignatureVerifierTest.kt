package com.frank.visordocumentoscifrado.license

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class LicenseSignatureVerifierTest {
    @Test
    fun ecdsaV2_acceptsValidSignature_andRejectsTampering() {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()

        val payloadB64 = Base64.getEncoder().encodeToString(
            "{\"employee_id\":\"123\",\"access_mode\":\"AREA\"}".toByteArray()
        )

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(pair.private)
        signer.update(payloadB64.toByteArray())
        val signatureB64 = Base64.getEncoder().encodeToString(signer.sign())
        val publicKeyB64 = Base64.getEncoder().encodeToString(pair.public.encoded)

        assertTrue(
            LicenseSignatureVerifier.verifyEcdsaP256Sha256(
                publicKeyB64,
                payloadB64,
                signatureB64
            )
        )
        assertFalse(
            LicenseSignatureVerifier.verifyEcdsaP256Sha256(
                publicKeyB64,
                payloadB64 + "alterado",
                signatureB64
            )
        )
    }

    @Test
    fun legacyHmac_rejectsWrongSecret() {
        val payload = Base64.getEncoder().encodeToString("{}".toByteArray())
        val expected = com.frank.visordocumentoscifrado.security.CryptoUtils.hmacSha256Base64(
            "secreto-a".toByteArray(),
            payload.toByteArray()
        )

        assertTrue(LicenseSignatureVerifier.verifyLegacyHmac("secreto-a", payload, expected))
        assertFalse(LicenseSignatureVerifier.verifyLegacyHmac("secreto-b", payload, expected))
    }
}
