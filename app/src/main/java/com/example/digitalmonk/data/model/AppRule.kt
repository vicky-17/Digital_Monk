package com.example.digitalmonk.data.model

data class AppRule(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean = false,
    val blockShorts: Boolean = false
)