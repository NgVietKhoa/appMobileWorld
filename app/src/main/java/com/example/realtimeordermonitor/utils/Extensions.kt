// Extensions.kt
package com.example.realtimeordermonitor.utils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object CurrencyFormatter {
    private val vietnameseFormat = NumberFormat.getCurrencyInstance(Locale("vi", "VN"))

    fun formatVND(amount: Long): String {
        return try {
            vietnameseFormat.format(amount)
        } catch (e: Exception) {
            "${amount} VNĐ"
        }
    }

    fun formatVNDShort(amount: Long): String {
        return when {
            amount >= 1_000_000_000 -> "${String.format("%.1f", amount / 1_000_000_000.0)}B VNĐ"
            amount >= 1_000_000 -> "${String.format("%.1f", amount / 1_000_000.0)}M VNĐ"
            amount >= 1_000 -> "${String.format("%.1f", amount / 1_000.0)}K VNĐ"
            else -> "${amount} VNĐ"
        }
    }
}

object DateTimeFormatter {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }
}

object PhoneFormatter {
    fun formatPhoneNumber(phone: String): String {
        if (phone.isEmpty()) return ""

        // Remove all non-digit characters
        val digitsOnly = phone.filter { it.isDigit() }

        return when {
            digitsOnly.length == 10 && digitsOnly.startsWith("0") -> {
                // Format: 0xxx xxx xxx
                "${digitsOnly.substring(0, 4)} ${digitsOnly.substring(4, 7)} ${digitsOnly.substring(7)}"
            }
            digitsOnly.length == 11 && digitsOnly.startsWith("84") -> {
                // Format: +84 xxx xxx xxx
                "+${digitsOnly.substring(0, 2)} ${digitsOnly.substring(2, 5)} ${digitsOnly.substring(5, 8)} ${digitsOnly.substring(8)}"
            }
            else -> phone
        }
    }
}

// Extension functions for better readability
fun Long.toVNDCurrency(): String = CurrencyFormatter.formatVND(this)
fun Long.toVNDShort(): String = CurrencyFormatter.formatVNDShort(this)
fun Long.toTimeString(): String = DateTimeFormatter.formatTime(this)
fun String.toFormattedPhone(): String = PhoneFormatter.formatPhoneNumber(this)

