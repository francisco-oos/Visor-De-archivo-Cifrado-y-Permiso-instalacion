package com.frank.visordocumentoscifrado.config

/**
 * Clave maestra de documentos embebida en la APK.
 *
 * La genera tools/document_encryptor.py al cifrar los manuales.
 * No pertenece a licencia.key. La licencia solo decide qué áreas puede ver el usuario.
 *
 * Si vuelves a cifrar con nueva clave, recompila la APK.
 */
object DocumentKeyConfig {
    const val EMBEDDED_DOCUMENT_KEY_B64 = "elzRMlQhBW/xfMQIZwirspW0diN1xCBaxiAQj23JwCE="
    const val DOCUMENT_KEY_SHA256 = "311984fce63f096f06fb74cadccbffdda952d8a568af4f9c857e7dcbfe0529c3"
}
