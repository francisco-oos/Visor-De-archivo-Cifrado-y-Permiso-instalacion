package com.frank.visordocumentoscifrado.documents

import android.content.Context
import com.frank.visordocumentoscifrado.config.AppConfig
import com.frank.visordocumentoscifrado.config.AreaCatalog
import com.frank.visordocumentoscifrado.license.LicenseManager
import com.frank.visordocumentoscifrado.security.CryptoUtils
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException

data class SecureDocument(
    val title: String,
    val file: String,
    val category: String,
    val keywords: String,
    val sha256: String,
    val areaId: String = AreaCatalog.normalize(category),
    val format: String = "VSDOC1"
)

object DocumentRepository {
    private val MAGIC_V1 = byteArrayOf(0x56, 0x53, 0x44, 0x4F, 0x43, 0x31) // VSDOC1
    private val MAGIC_V2 = byteArrayOf(0x56, 0x53, 0x44, 0x4F, 0x43, 0x32) // VSDOC2

    fun list(context: Context): List<SecureDocument> {
        return try {
            val json = context.assets.open(AppConfig.MANUAL_INDEX_FILE).bufferedReader().readText()
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val category = o.optString("category", AreaCatalog.OTRO)
                val areaId = AreaCatalog.normalize(o.optString("area_id", category))
                SecureDocument(
                    title = o.getString("title"),
                    file = o.getString("file"),
                    category = category,
                    keywords = o.optString("keywords", ""),
                    sha256 = o.optString("sha256", ""),
                    areaId = areaId,
                    format = o.optString("format", "VSDOC1")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun listAllowed(context: Context): List<SecureDocument> {
        if (AppConfig.DEBUG_MODE) return list(context)
        val lic = LicenseManager.current(context) ?: return emptyList()
        return list(context).filter { LicenseManager.canAccessArea(lic, it.areaId) }
    }

    fun categoriesAllowed(context: Context): List<String> = listAllowed(context).map { it.areaId }.distinct().sorted()

    fun search(context: Context, query: String): List<SecureDocument> {
        val q = query.trim().lowercase()
        return listAllowed(context).filter {
            q.isBlank() ||
                it.title.lowercase().contains(q) ||
                it.category.lowercase().contains(q) ||
                AreaCatalog.displayName(it.areaId).lowercase().contains(q) ||
                it.keywords.lowercase().contains(q)
        }
    }

    fun decrypt(context: Context, doc: SecureDocument): ByteArray {
        DocumentAccessGuard.ensureCanOpen(context, doc)
        val key = LicenseManager.documentKey(context)
        val raw = context.assets.open("${AppConfig.ENCRYPTED_MANUALS_ASSET_DIR}/${doc.file}").readBytes()
        if (raw.size < 18) throw SecurityException("Archivo cifrado incompleto o dañado.")

        val pdf = try {
            when {
                raw.take(MAGIC_V2.size).toByteArray().contentEquals(MAGIC_V2) -> decryptV2(context, doc, key, raw)
                raw.take(MAGIC_V1.size).toByteArray().contentEquals(MAGIC_V1) -> {
                    val nonce = raw.copyOfRange(MAGIC_V1.size, MAGIC_V1.size + 12)
                    val cipher = raw.copyOfRange(MAGIC_V1.size + 12, raw.size)
                    CryptoUtils.aesGcmDecrypt(key, nonce, cipher, null)
                }
                else -> {
                    val nonce = raw.copyOfRange(0, 12)
                    val cipher = raw.copyOfRange(12, raw.size)
                    try {
                        CryptoUtils.aesGcmDecrypt(key, nonce, cipher, doc.title.toByteArray())
                    } catch (_: AEADBadTagException) {
                        CryptoUtils.aesGcmDecrypt(key, nonce, cipher, null)
                    }
                }
            }
        } catch (e: AEADBadTagException) {
            throw SecurityException("BAD_DECRYPT: los documentos de la APK no fueron cifrados con la clave embebida actual. Cifra manuales desde la herramienta Python y recompila la APK.")
        }

        val hash = CryptoUtils.sha256Hex(pdf)
        if (doc.sha256.isNotBlank() && hash.lowercase() != doc.sha256.lowercase()) {
            throw SecurityException("Integridad inválida. El PDF descifrado no coincide con el hash registrado.")
        }
        return pdf
    }

    private fun decryptV2(context: Context, doc: SecureDocument, key: ByteArray, raw: ByteArray): ByteArray {
        if (raw.size < MAGIC_V2.size + 4 + 12 + 16) throw SecurityException("Archivo VSDOC2 incompleto.")
        val metaLen = ByteBuffer.wrap(raw, MAGIC_V2.size, 4).int
        if (metaLen <= 0 || metaLen > 128 * 1024) throw SecurityException("Metadatos VSDOC2 inválidos.")
        val metaStart = MAGIC_V2.size + 4
        val metaEnd = metaStart + metaLen
        if (raw.size <= metaEnd + 12) throw SecurityException("Archivo VSDOC2 dañado.")
        val metaBytes = raw.copyOfRange(metaStart, metaEnd)
        val metadata = JSONObject(String(metaBytes, Charsets.UTF_8))
        val internalArea = AreaCatalog.normalize(metadata.optString("area_id", metadata.optString("category", doc.areaId)))
        DocumentAccessGuard.ensureCanOpen(context, doc, internalArea)
        val expectedFile = metadata.optString("file", doc.file)
        if (expectedFile != doc.file) throw SecurityException("Metadatos internos no corresponden al archivo solicitado.")
        val nonce = raw.copyOfRange(metaEnd, metaEnd + 12)
        val cipher = raw.copyOfRange(metaEnd + 12, raw.size)
        return CryptoUtils.aesGcmDecrypt(key, nonce, cipher, metaBytes)
    }
}
