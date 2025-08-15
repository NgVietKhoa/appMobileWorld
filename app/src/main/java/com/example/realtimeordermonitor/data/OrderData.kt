// OrderData.kt
package com.example.realtimeordermonitor.data

import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Locale

// ===== SIMPLIFIED CUSTOMER MODELS =====

@Parcelize
data class KhachHang(
    @SerializedName("id")
    val id: Int = 0,
    @SerializedName("ten")
    val ten: String = "",
    @SerializedName("soDienThoai")
    val soDienThoai: String? = null,
    @SerializedName("email")
    val email: String? = null
) : Parcelable {

    fun getDisplayName(): String = ten.takeIf { it.isNotEmpty() } ?: "Khách lẻ"

    fun hasCompleteInfo(): Boolean = ten.isNotEmpty() && !soDienThoai.isNullOrEmpty()

    // Lấy thông tin hiển thị cho OrderScreen
    fun getOrderDisplayInfo(): String {
        val parts = mutableListOf<String>()

        if (ten.isNotEmpty() && ten != "Khách lẻ") {
            parts.add(ten)
        }

        if (!soDienThoai.isNullOrEmpty()) {
            parts.add(soDienThoai)
        }

        return if (parts.isEmpty()) "Khách lẻ" else parts.joinToString(" - ")
    }

    // Kiểm tra có đủ thông tin để hiển thị không
    fun isValidForOrderDisplay(): Boolean {
        return id > 0 && (ten.isNotEmpty() || !soDienThoai.isNullOrEmpty())
    }

    // Lấy key để lưu trong Map (ưu tiên số điện thoại, fallback email)
    fun getCacheKey(): String {
        return when {
            !soDienThoai.isNullOrEmpty() -> soDienThoai
            !email.isNullOrEmpty() -> email
            else -> "customer_$id"
        }
    }
}

@Parcelize
data class KhachHangUpdateMessage(
    @SerializedName("action")
    val action: String = "",
    @SerializedName("khachHangId")
    val khachHangId: Int = 0,
    @SerializedName("ten")
    val ten: String = "",
    @SerializedName("soDienThoai")
    val soDienThoai: String? = null,
    @SerializedName("email")
    val email: String? = null,
    @SerializedName("timestamp")
    val timestamp: String = ""
) : Parcelable {

    // Chuyển đổi message thành KhachHang object
    fun toKhachHang(): KhachHang {
        return KhachHang(
            id = khachHangId,
            ten = ten,
            soDienThoai = soDienThoai,
            email = email
        )
    }

    // Kiểm tra message có hợp lệ không
    fun isValid(): Boolean {
        return khachHangId > 0 && ten.isNotEmpty()
    }
}

// ===== ORDER UI STATE =====

// OrderData.kt - Enhanced getCustomerForOrder method

@Parcelize
data class OrderUiState(
    val orders: List<HoaDonDetailResponse> = emptyList(),
    val phieuGiamGias: List<PhieuGiamGiaDetail> = emptyList(),
    val voucherOrderUpdates: Map<Int, VoucherOrderUpdate> = emptyMap(),
    val khachHangInfo: Map<String, KhachHang> = emptyMap(),

    // NEW: Map để liên kết đơn hàng với khách hàng
    val orderCustomerMapping: Map<Int, Int> = emptyMap(), // orderId -> customerId

    val isConnected: Boolean = false,
    val lastUpdated: Long = 0L,
    val totalActiveVouchers: Int = 0,
    val connectionError: String? = null,
    val isLoading: Boolean = false
) : Parcelable {

    // Enhanced customer lookup với thời gian và logic thông minh hơn
    fun getCustomerForOrder(order: HoaDonDetailResponse): KhachHang? {
        Log.d("OrderUiState", "=== ENHANCED CUSTOMER LOOKUP ===")
        Log.d("OrderUiState", "Order: ${order.maHoaDon} (ID: ${order.id})")
        Log.d("OrderUiState", "Order customer info - Name: '${order.tenKhachHang}', Phone: '${order.soDienThoaiKhachHang}', Email: '${order.emailKhachHang}'")

        // Strategy 1: Kiểm tra mapping trực tiếp
        orderCustomerMapping[order.id]?.let { customerId ->
            val customer = khachHangInfo["id_$customerId"]
            if (customer != null) {
                Log.d("OrderUiState", "✓ Found via direct mapping: ${customer.getDisplayName()}")
                return customer
            }
        }

        // Strategy 2: Match chính xác theo SĐT
        if (order.soDienThoaiKhachHang.isNotEmpty()) {
            khachHangInfo[order.soDienThoaiKhachHang]?.let { customer ->
                Log.d("OrderUiState", "✓ Found via exact phone match: ${customer.getDisplayName()}")
                return customer
            }
        }

        // Strategy 3: Match chính xác theo email
        if (order.emailKhachHang.isNotEmpty()) {
            khachHangInfo[order.emailKhachHang]?.let { customer ->
                Log.d("OrderUiState", "✓ Found via exact email match: ${customer.getDisplayName()}")
                return customer
            }
        }

        // Strategy 4: SMART TEMPORAL MATCHING
        // Nếu đơn hàng có thông tin "Khách vãng lai" nhưng được tạo gần thời điểm có customer update
        if (order.tenKhachHang == "Khách vãng lai" &&
            (order.soDienThoaiKhachHang.isEmpty() || order.emailKhachHang.isEmpty())) {

            Log.d("OrderUiState", "Order appears to be guest but checking for recent customers...")

            val orderTime = parseOrderTime(order.ngayTao)
            val currentTime = System.currentTimeMillis()

            // Lấy khách hàng được cập nhật gần đây (trong vòng 5 phút)
            val recentCustomers = khachHangInfo.values
                .distinctBy { it.id }
                .sortedByDescending { it.id }
                .take(3) // 3 khách hàng gần nhất

            for (customer in recentCustomers) {
                // Kiểm tra thời gian - nếu customer update trong vòng 5 phút so với order
                val timeDiff = Math.abs(currentTime - orderTime)
                if (timeDiff <= 5 * 60 * 1000) { // 5 phút
                    Log.d("OrderUiState", "✓ Found via temporal matching: ${customer.getDisplayName()} (time diff: ${timeDiff}ms)")
                    return customer
                }
            }
        }

        // Strategy 5: Fuzzy name matching (nếu order có tên thật)
        if (order.tenKhachHang.isNotEmpty() &&
            order.tenKhachHang != "Khách lẻ" &&
            order.tenKhachHang != "Khách vãng lai") {

            val foundCustomer = khachHangInfo.values.find { customer ->
                val similarity = calculateNameSimilarity(order.tenKhachHang, customer.ten)
                similarity > 0.7 // 70% similarity threshold
            }

            foundCustomer?.let { customer ->
                Log.d("OrderUiState", "✓ Found via name similarity: ${customer.getDisplayName()}")
                return customer
            }
        }

        // Strategy 6: Last resort - newest customer (if order is very recent)
        val orderTime = parseOrderTime(order.ngayTao)
        val currentTime = System.currentTimeMillis()

        if (Math.abs(currentTime - orderTime) <= 2 * 60 * 1000) { // 2 phút gần đây
            val newestCustomer = khachHangInfo.values
                .distinctBy { it.id }
                .maxByOrNull { it.id }

            newestCustomer?.let { customer ->
                Log.d("OrderUiState", "✓ Found via newest customer fallback: ${customer.getDisplayName()}")
                return customer
            }
        }

        Log.d("OrderUiState", "✗ No customer found for order ${order.maHoaDon}")
        return null
    }

    private fun parseOrderTime(dateString: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            format.parse(dateString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun calculateNameSimilarity(name1: String, name2: String): Double {
        if (name1.isEmpty() || name2.isEmpty()) return 0.0

        val s1 = name1.lowercase().trim()
        val s2 = name2.lowercase().trim()

        if (s1 == s2) return 1.0
        if (s1.contains(s2) || s2.contains(s1)) return 0.8

        // Simple character-based similarity
        val maxLength = maxOf(s1.length, s2.length)
        val commonChars = s1.toSet().intersect(s2.toSet()).size

        return commonChars.toDouble() / maxLength
    }

    // Method to create order-customer mapping when we have context
    fun createOrderCustomerMapping(orderId: Int, customerId: Int): OrderUiState {
        return copy(orderCustomerMapping = orderCustomerMapping + (orderId to customerId))
    }

    // Other existing methods...
    fun getCompletedOrders(): List<HoaDonDetailResponse> = orders.filter { it.trangThai == 3 }
    fun getPendingOrders(): List<HoaDonDetailResponse> = orders.filter { it.trangThai == 0 }
    fun getCancelledOrders(): List<HoaDonDetailResponse> = orders.filter { it.trangThai == 4 }
    fun getTotalRevenue(): Long = getCompletedOrders().sumOf { it.tongTienSauGiam }
    fun getTotalDiscount(): Long = orders.sumOf { it.tienGiamGia }
    fun getOrdersByStatus(status: Int): List<HoaDonDetailResponse> = orders.filter { it.trangThai == status }
    fun getVoucherForOrder(hoaDonId: Int): VoucherOrderUpdate? = voucherOrderUpdates[hoaDonId]
    fun getUniqueCustomerCount(): Int = khachHangInfo.values.distinctBy { it.id }.size
}

// ===== ORDER DETAIL MODEL =====

@Parcelize
data class HoaDonDetailResponse(
    val id: Int = 0,
    val maHoaDon: String = "",
    val tenKhachHang: String = "",
    val soDienThoaiKhachHang: String = "",
    val emailKhachHang: String = "",
    val diaChiKhachHang: String = "",
    val tongTien: Long = 0L,
    val tongTienSauGiam: Long = 0L,
    val phiVanChuyen: Long = 0L,
    val tienGiamGia: Long = 0L,
    val maPhieuGiamGia: String = "",
    val ghiChu: String = "",
    val trangThai: Int = 0,
    val trangThaiText: String = "",
    val loaiDon: String = "",
    val ngayTao: String = "",
    val ngayThanhToan: String = "",
    val tenNhanVien: String = "",
    val sanPhamChiTiet: List<SanPhamChiTiet> = emptyList(),
    val thanhToanInfo: List<ThanhToanInfo> = emptyList()
) : Parcelable {

    fun isCompleted(): Boolean = trangThai == 3
    fun isCancelled(): Boolean = trangThai == 4
    fun isPending(): Boolean = trangThai == 0
    fun isProcessing(): Boolean = trangThai in 1..2

    fun hasCustomerInfo(): Boolean = tenKhachHang.isNotEmpty() && tenKhachHang != "Khách lẻ"
    fun hasDiscount(): Boolean = tienGiamGia > 0
    fun hasShippingFee(): Boolean = phiVanChuyen > 0
    fun hasVoucher(): Boolean = maPhieuGiamGia.isNotEmpty()

    fun getTotalItems(): Int = sanPhamChiTiet.sumOf { it.soLuong }
    fun getUniqueProductCount(): Int = sanPhamChiTiet.size

    fun getStatusColor(): String = when (trangThai) {
        0 -> "#FF9800" // Orange - Chờ xác nhận
        1 -> "#2196F3" // Blue - Chờ giao hàng
        2 -> "#9C27B0" // Purple - Đang giao
        3 -> "#4CAF50" // Green - Hoàn thành
        4 -> "#F44336" // Red - Đã hủy
        5 -> "#FF5722" // Deep Orange - Trả hàng
        6 -> "#795548" // Brown - Hoàn tiền
        else -> "#607D8B" // Blue Grey
    }

    fun getFormattedDate(): String {
        return try {
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val outputFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            val date = inputFormat.parse(ngayTao)
            date?.let { outputFormat.format(it) } ?: ngayTao
        } catch (e: Exception) {
            ngayTao
        }
    }
}

// ===== PRODUCT DETAIL MODEL =====

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

    fun getFormattedSpecs(): String {
        val specs = listOfNotNull(
            mauSac.takeIf { it.isNotEmpty() },
            ram.takeIf { it.isNotEmpty() }?.let { if (it.contains("GB")) it else "${it}GB" },
            boNhoTrong.takeIf { it.isNotEmpty() }?.let { if (it.contains("GB")) it else "${it}GB" }
        )
        return specs.joinToString(" • ")
    }
}

// ===== PAYMENT INFO MODEL =====

@Parcelize
data class ThanhToanInfo(
    val phuongThuc: String = "",
    val soTien: Long = 0L,
    val ghiChu: String = ""
) : Parcelable

// ===== VOUCHER MODELS =====

@Parcelize
data class VoucherOrderUpdate(
    @SerializedName("action")
    val action: String = "",
    @SerializedName("hoaDonId")
    val hoaDonId: Int = 0,
    @SerializedName("phieuGiamGiaId")
    val phieuGiamGiaId: Int = 0,
    @SerializedName("maPhieu")
    val maPhieu: String = "",
    @SerializedName("tenPhieu")
    val tenPhieu: String = "",
    @SerializedName("giaTriGiam")
    val giaTriGiam: Double = 0.0,
    @SerializedName("soLuongDung")
    val soLuongDung: Int = 0,
    @SerializedName("trangThai")
    val trangThai: Boolean = false,
    @SerializedName("timestamp")
    val timestamp: String = ""
) : Parcelable

@Parcelize
data class PhieuGiamGiaDetail(
    val id: Int = 0,
    @SerializedName("ma")
    val ma: String = "",
    @SerializedName("tenPhieuGiamGia")
    val tenPhieuGiamGia: String = "",
    @SerializedName("loaiPhieuGiamGia")
    val loaiPhieuGiamGia: String = "",
    @SerializedName("phanTramGiamGia")
    val phanTramGiamGia: Double? = null,
    @SerializedName("soTienGiamToiDa")
    val soTienGiamToiDa: Double = 0.0,
    @SerializedName("hoaDonToiThieu")
    val hoaDonToiThieu: Double = 0.0,
    @SerializedName("soLuongDung")
    val soLuongDung: Int = 0,
    @SerializedName("soLuongConLai")
    val soLuongConLai: Int = 0,
    @SerializedName("ngayBatDau")
    val ngayBatDau: String = "",
    @SerializedName("ngayKetThuc")
    val ngayKetThuc: String = "",
    @SerializedName("trangThai")
    val trangThai: Boolean = false
) : Parcelable {

    fun getDiscountText(): String {
        return when (loaiPhieuGiamGia.lowercase()) {
            "phần trăm", "percent" -> "${phanTramGiamGia?.toInt() ?: 0}%"
            "tiền mặt", "fixed", "cash" -> "${soTienGiamToiDa.toLong().toVNDCurrency()}"
            else -> "Giảm giá"
        }
    }

    fun getStatusText(): String {
        return when {
            !trangThai -> "Tạm dừng"
            isExpired() -> "Hết hạn"
            !isStarted() -> "Chưa bắt đầu"
            soLuongConLai <= 0 -> "Hết lượt"
            else -> "Đang hoạt động"
        }
    }

    fun isActive(): Boolean {
        return trangThai && !isExpired() && isStarted() && soLuongConLai > 0
    }

    fun isExpired(): Boolean {
        return try {
            if (ngayKetThuc.isEmpty()) return false
            val endDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .parse(ngayKetThuc.substringBefore("T"))
            val now = java.util.Date()
            endDate?.before(now) ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun isStarted(): Boolean {
        return try {
            if (ngayBatDau.isEmpty()) return true
            val startDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .parse(ngayBatDau.substringBefore("T"))
            val now = java.util.Date()
            startDate?.before(now) ?: true
        } catch (e: Exception) {
            true
        }
    }

    fun calculateDiscountAmount(orderTotal: Double): Double {
        if (!isActive() || orderTotal < hoaDonToiThieu) return 0.0

        return when (loaiPhieuGiamGia.lowercase()) {
            "phần trăm", "percent" -> {
                val discountAmount = orderTotal * (phanTramGiamGia ?: 0.0) / 100
                minOf(discountAmount, soTienGiamToiDa)
            }
            "tiền mặt", "fixed", "cash" -> {
                minOf(soTienGiamToiDa, orderTotal)
            }
            else -> 0.0
        }
    }
}

// ===== EXTENSION FUNCTIONS & HELPERS =====

// Extension function để format currency
fun Long.toVNDCurrency(): String {
    return try {
        val formatter = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("vi", "VN"))
        formatter.format(this)
    } catch (e: Exception) {
        "${this} VNĐ"
    }
}

// Helper function để tạo khách hàng từ thông tin đơn hàng (fallback)
fun createCustomerFromOrder(order: HoaDonDetailResponse): KhachHang {
    return KhachHang(
        id = 0, // Unknown ID
        ten = order.tenKhachHang.takeIf { it.isNotEmpty() } ?: "Khách lẻ",
        soDienThoai = order.soDienThoaiKhachHang.takeIf { it.isNotEmpty() },
        email = order.emailKhachHang.takeIf { it.isNotEmpty() }
    )
}