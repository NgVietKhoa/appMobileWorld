package com.example.realtimeordermonitor.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// Customer Model - sync with backend KhachHang entity
@Parcelize
data class KhachHang(
    val id: Int = 0,
    val ten: String = "",
    val soDienThoai: String? = null,
    val email: String? = null
) : Parcelable {

    fun getDisplayName(): String = ten.takeIf { it.isNotEmpty() } ?: "Khách lẻ"
    fun isValidForDisplay(): Boolean = id > 0 && (ten.isNotEmpty() || !soDienThoai.isNullOrEmpty())
    fun isValidForOrderDisplay(): Boolean = isValidForDisplay()

    fun getContactInfo(): String {
        val parts = mutableListOf<String>()
        if (ten.isNotEmpty() && ten != "Khách lẻ") parts.add(ten)
        if (!soDienThoai.isNullOrEmpty()) parts.add(soDienThoai)
        return if (parts.isEmpty()) "Khách lẻ" else parts.joinToString(" - ")
    }
}

// Order Model - sync with backend HoaDonDTO
@Parcelize
data class HoaDonDetailResponse(
    val id: Int = 0,
    val maHoaDon: String = "",
    val tenKhachHang: String = "",
    val soDienThoaiKhachHang: String = "",
    val emailKhachHang: String = "",
    val tongTien: Long = 0L,
    val tongTienSauGiam: Long = 0L,
    val tienGiamGia: Long = 0L,
    val maPhieuGiamGia: String = "",
    val ghiChu: String = "",
    val trangThai: Int = 0,
    val trangThaiText: String = "",
    val ngayTao: String = "",
    val sanPhamChiTiet: List<SanPhamChiTiet> = emptyList(),
    val thanhToanInfo: List<ThanhToanInfo> = emptyList()
) : Parcelable {

    fun hasCustomerInfo(): Boolean = tenKhachHang.isNotEmpty() && tenKhachHang != "Khách lẻ"
    fun hasDiscount(): Boolean = tienGiamGia > 0
    fun getTotalItems(): Int = sanPhamChiTiet.sumOf { it.soLuong }

    fun getStatusColor(): String = when (trangThai) {
        0 -> "#FF9800" // Chờ xác nhận
        1 -> "#2196F3" // Chờ giao hàng
        2 -> "#9C27B0" // Đang giao
        3 -> "#4CAF50" // Hoàn thành
        4 -> "#F44336" // Đã hủy
        else -> "#607D8B"
    }

    fun getEffectiveVoucherInfo(voucherOrderInfo: VoucherOrderUpdateResponse?): Pair<String, Long> {
        // Nếu có thông tin voucher realtime và đang active
        if (voucherOrderInfo?.isApplied() == true) {
            return voucherOrderInfo.maPhieu to voucherOrderInfo.giaTriGiam.toLong()
        }
        // Fallback về thông tin voucher trong order
        return maPhieuGiamGia to tienGiamGia
    }

    fun getCalculatedTotal(): Long {
        return sanPhamChiTiet.sumOf { it.thanhTien }
    }

    fun getCalculatedTotalAfterDiscount(voucherOrderInfo: VoucherOrderUpdateResponse?): Long {
        val baseTotal = getCalculatedTotal()
        val (_, discountAmount) = getEffectiveVoucherInfo(voucherOrderInfo)
        return baseTotal - discountAmount
    }

    fun getFormattedDate(): String {
        return try {
            val input = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val output = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            input.parse(ngayTao)?.let { output.format(it) } ?: ngayTao
        } catch (e: Exception) {
            ngayTao
        }
    }
}

// Product Model
@Parcelize
data class SanPhamChiTiet(
    val tenSanPham: String = "",
    val mauSac: String = "",
    val ram: String = "",
    val boNhoTrong: String = "",
    val soLuong: Int = 0,
    val giaBan: Long = 0L,
    val thanhTien: Long = 0L,
    val imel: String = "",
    val anhSanPham: String = ""
) : Parcelable {

    fun getSpecs(): String {
        val specs = listOfNotNull(
            mauSac.takeIf { it.isNotEmpty() },
            ram.takeIf { it.isNotEmpty() }?.let { if (it.contains("GB")) it else "${it}GB" },
            boNhoTrong.takeIf { it.isNotEmpty() }?.let { if (it.contains("GB")) it else "${it}GB" }
        )
        return specs.joinToString(" • ")
    }
}

// Payment Info
@Parcelize
data class ThanhToanInfo(
    val phuongThuc: String = "",
    val soTien: Long = 0L
) : Parcelable

// UI State
@Parcelize
data class OrderUiState(
    val orders: List<HoaDonDetailResponse> = emptyList(),
    val khachHangInfo: Map<String, KhachHang> = emptyMap(),
    val orderCustomerMapping: Map<Int, Int> = emptyMap(),
    val orderVoucherInfo: Map<Int, VoucherOrderUpdateResponse> = emptyMap(),
    val isConnected: Boolean = false,
    val lastUpdated: Long = 0L
) : Parcelable {

    fun getCustomerForOrder(order: HoaDonDetailResponse): KhachHang? {
        // 1. Check direct mapping
        orderCustomerMapping[order.id]?.let { customerId ->
            khachHangInfo["id_$customerId"]?.let { return it }
        }

        // 2. Check by phone
        if (order.soDienThoaiKhachHang.isNotEmpty()) {
            khachHangInfo[order.soDienThoaiKhachHang]?.let { return it }
        }

        // 3. Check by email
        if (order.emailKhachHang.isNotEmpty()) {
            khachHangInfo[order.emailKhachHang]?.let { return it }
        }

        return null
    }

    fun getVoucherForOrder(orderId: Int): VoucherOrderUpdateResponse? {
        return orderVoucherInfo[orderId]?.takeIf { it.trangThai }
    }
}

// Extensions
fun Long.toVNDCurrency(): String {
    return try {
        val formatter = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("vi", "VN"))
        formatter.format(this)
    } catch (e: Exception) {
        "${this} VNĐ"
    }
}

fun String.toFormattedPhone(): String {
    if (isEmpty()) return ""
    val digits = filter { it.isDigit() }
    return when {
        digits.length == 10 && digits.startsWith("0") ->
            "${digits.substring(0, 4)} ${digits.substring(4, 7)} ${digits.substring(7)}"
        else -> this
    }
}