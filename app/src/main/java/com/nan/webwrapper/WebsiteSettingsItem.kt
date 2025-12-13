package com.nan.webwrapper

data class WebsiteSettingsItem(
    val url: String,
    val cacheSize: Long = 0L,
    val ignoreNotch: Boolean = false,
    val hardwareAccel: Boolean = true,
    val highRefresh: Boolean = true
)

