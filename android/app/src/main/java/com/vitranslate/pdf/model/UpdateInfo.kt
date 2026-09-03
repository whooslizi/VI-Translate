package com.vitranslate.pdf.model

data class UpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    val isNewerAvailable: Boolean,
    val apkUrl: String? = null,
    val apkSize: Long = 0L,
    val apkName: String? = null
)
