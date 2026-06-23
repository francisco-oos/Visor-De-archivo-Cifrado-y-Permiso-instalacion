package com.frank.visordocumentoscifrado.license

data class LicenseData(
    val employeeName: String,
    val employeeId: String,
    val project: String,
    val area: String,
    val position: String,
    val deviceHash: String,
    val installId: String,
    val expiresAt: String,
    val issuedAt: String,
    val accessMode: String,
    val allowedAreas: List<String>
)
