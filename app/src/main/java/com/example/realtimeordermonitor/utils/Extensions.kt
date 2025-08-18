package com.example.realtimeordermonitor.utils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// Đơn giản hóa CurrencyFormatter - chỉ giữ function cần thiết
object CurrencyFormatter {
    private val vietnameseFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("vi", "VN"))
    }

    fun formatVND(amount: Long): String {
        return try {
            vietnameseFormat.format(amount)
        } catch (e: Exception) {
            "${amount} VNĐ"
        }
    }
}

// Đơn giản hóa DateTimeFormatter
object DateTimeFormatter {
    private val timeFormat by lazy {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }
}

// Tối ưu PhoneFormatter - chỉ format cho số VN
object PhoneFormatter {
    fun formatPhoneNumber(phone: String): String {
        if (phone.isEmpty()) return ""

        val digitsOnly = phone.filter { it.isDigit() }

        return if (digitsOnly.length == 10 && digitsOnly.startsWith("0")) {
            "${digitsOnly.substring(0, 4)} ${digitsOnly.substring(4, 7)} ${digitsOnly.substring(7)}"
        } else phone
    }
}

// Extension functions - giữ nguyên để dễ sử dụng
fun Long.toVNDCurrency(): String = CurrencyFormatter.formatVND(this)
fun Long.toTimeString(): String = DateTimeFormatter.formatTime(this)
fun String.toFormattedPhone(): String = PhoneFormatter.formatPhoneNumber(this)