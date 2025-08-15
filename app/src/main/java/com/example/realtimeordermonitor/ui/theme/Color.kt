package com.example.realtimeordermonitor.ui.theme

import androidx.compose.ui.graphics.Color

// Material3 Color Palette
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// App specific colors
val StatusConnected = Color(0xFF4CAF50)
val StatusDisconnected = Color(0xFFF44336)

// Order status colors
object OrderStatusColors {
    val PendingBg = Color(0xFFFFE0B2)
    val PendingText = Color(0xFFE65100)

    val WaitingBg = Color(0xFFE1F5FE)
    val WaitingText = Color(0xFF01579B)

    val ShippingBg = Color(0xFFF3E5F5)
    val ShippingText = Color(0xFF4A148C)

    val CompletedBg = Color(0xFFE8F5E8)
    val CompletedText = Color(0xFF1B5E20)

    val CancelledBg = Color(0xFFFFEBEE)
    val CancelledText = Color(0xFFC62828)

    val DefaultBg = Color(0xFFF5F5F5)
    val DefaultText = Color(0xFF424242)
}