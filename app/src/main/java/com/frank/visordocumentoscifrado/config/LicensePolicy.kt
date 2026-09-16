package com.frank.visordocumentoscifrado.config

/** Política de licenciamiento offline vigente. */
object LicensePolicy {
    const val LICENSE_SCHEMA_VERSION = 2
    const val REQUIRE_DEVICE_HASH = true
    const val REQUIRE_INSTALL_ID = true
    const val REQUIRE_EMPLOYEE_DATA = true
    const val REQUIRE_EXPIRATION_DATE = true
    const val REQUIRES_NEW_LICENSE_AFTER_REINSTALL = true
}
