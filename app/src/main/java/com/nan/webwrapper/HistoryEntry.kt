package com.nan.webwrapper

data class HistoryEntry(
    val url: String,
    val timestamp: Long,
    val customName: String? = null,
    val logoPath: String? = null
)

