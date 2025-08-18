package com.example.realtimeordermonitor.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.google.gson.annotations.SerializedName

// ===== CUSTOMER RESPONSE MODELS =====

@Parcelize
data class KhachHangResponse(
    @SerializedName("khachHang")
    val khachHang: KhachHang? = null,
    @SerializedName("success")
    val success: Boolean = false,
    @SerializedName("message")
    val message: String = ""
) : Parcelable {
    fun hasValidCustomer(): Boolean = khachHang?.isValidForOrderDisplay() == true
}

// ===== SHOPPING CART MODELS =====

@Parcelize
data class GioHangUpdateMessage(
    @SerializedName("hoaDonId")
    val hoaDonId: Int = 0,
    @SerializedName("maHoaDon")
    val maHoaDon: String = "",
    @SerializedName("gioHang")
    val gioHang: GioHangDTO? = null,
    @SerializedName("timestamp")
    val timestamp: String = ""
) : Parcelable

@Parcelize
data class GioHangDTO(
    @SerializedName("gioHangId")
    val gioHangId: String = "",
    @SerializedName("khachHangId")
    val khachHangId: Int = 0,
    @SerializedName("chiTietGioHangDTOS")
    val chiTietGioHangDTOS: List<ChiTietGioHangDTO> = emptyList(),
    @SerializedName("tongTien")
    val tongTien: Double = 0.0,
    @SerializedName("tongTienGoc")
    val tongTienGoc: Double = 0.0,
    @SerializedName("tongGiamGia")
    val tongGiamGia: Double = 0.0
) : Parcelable

@Parcelize
data class ChiTietGioHangDTO(
    @SerializedName("chiTietSanPhamId")
    val chiTietSanPhamId: Int = 0,
    @SerializedName("maImel")
    val maImel: String = "",
    @SerializedName("tenSanPham")
    val tenSanPham: String = "",
    @SerializedName("mauSac")
    val mauSac: String = "",
    @SerializedName("ram")
    val ram: String = "",
    @SerializedName("boNhoTrong")
    val boNhoTrong: String = "",
    @SerializedName("soLuong")
    val soLuong: Int = 0,
    @SerializedName("giaBan")
    val giaBan: Double = 0.0,
    @SerializedName("giaBanGoc")
    val giaBanGoc: Double = 0.0,
    @SerializedName("tongTien")
    val tongTien: Double = 0.0,
    @SerializedName("image")
    val image: String? = null
) : Parcelable {

    fun toSanPhamChiTiet(): SanPhamChiTiet {
        return SanPhamChiTiet(
            tenSanPham = this.tenSanPham,
            mauSac = this.mauSac,
            ram = this.ram,
            boNhoTrong = this.boNhoTrong,
            soLuong = this.soLuong,
            giaBan = this.giaBan.toLong(),
            thanhTien = this.tongTien.toLong(),
            imel = this.maImel,
            anhSanPham = this.image ?: ""
        )
    }

    fun hasDiscount(): Boolean = giaBanGoc > giaBan
    fun getDiscountAmount(): Double = (giaBanGoc - giaBan) * soLuong
    fun getDiscountPercent(): Double = if (giaBanGoc > 0) ((giaBanGoc - giaBan) / giaBanGoc) * 100 else 0.0
}

// ===== VOUCHER ORDER UPDATE RESPONSE =====

@Parcelize
data class VoucherOrderUpdateResponse(
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
) : Parcelable {

    fun isApplied(): Boolean = action == "VOUCHER_USED" || action == "VOUCHER_APPLIED"
    fun isRemoved(): Boolean = action == "VOUCHER_REMOVED" || action == "VOUCHER_CANCELLED"

    fun getFormattedValue(): String {
        return try {
            val formatter = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("vi", "VN"))
            formatter.format(giaTriGiam.toLong())
        } catch (e: Exception) {
            "${giaTriGiam.toLong()} VNĐ"
        }
    }
}