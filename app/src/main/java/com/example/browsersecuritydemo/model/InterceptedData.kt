package com.example.browsersecuritydemo.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class InterceptedData(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val source: String, // "WebView" or "CustomBrowser"
    val eventType: String, // "INPUT", "SUBMIT", "COOKIE", "REQUEST", "KEYPRESS"
    val fieldName: String,
    val fieldType: String,
    val value: String,
    val pageUrl: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}
