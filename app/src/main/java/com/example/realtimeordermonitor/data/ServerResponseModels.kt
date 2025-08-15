package com.example.realtimeordermonitor.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.google.gson.annotations.SerializedName

// ===== DISCOUNT CODE RESPONSE =====

@Parcelize
data class DiscountCodeCheckResponse(
    @SerializedName("valid")
    val valid: Boolean = false,
    @SerializedName("message")
    val message: String = "",
    @SerializedName("discountInfo")
    val discountInfo: PhieuGiamGiaDetail? = null,
    @SerializedName("discountAmount")
    val discountAmount: Double = 0.0,
    @SerializedName("errorCode")
    val errorCode: String? = null
) : Parcelable

@Parcelize
data class PhieuGiamGiaResponse(
    @SerializedName("phieuGiamGias")
    val phieuGiamGias: List<PhieuGiamGiaDetail> = emptyList(),
    @SerializedName("totalCount")
    val totalCount: Int = 0,
    @SerializedName("activeCount")
    val activeCount: Int = 0
) : Parcelable

// ===== CUSTOMER RESPONSE MODELS (SIMPLIFIED) =====

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

// Legacy support - removed complex nested models
@Parcelize
data class KhachHangUpdateResponse(
    @SerializedName("khachHang")
    val khachHang: KhachHang? = null,
    @SerializedName("phieuGiamGias")
    val phieuGiamGias: List<KhachHangPhieuGiamGia> = emptyList(),
    @SerializedName("count")
    val count: Int = 0
) : Parcelable {

    fun hasValidCustomer(): Boolean = khachHang?.isValidForOrderDisplay() == true
    fun getUsableVoucherCount(): Int = phieuGiamGias.count { it.isUsable() }
}

@Parcelize
data class KhachHangPhieuGiamGia(
    @SerializedName("phieuGiamGia")
    val phieuGiamGia: PhieuGiamGiaDetail = PhieuGiamGiaDetail(),
    @SerializedName("khachHang")
    val khachHang: KhachHang = KhachHang(),
    @SerializedName("soLuongDung")
    val soLuongDung: Int = 0,
    @SerializedName("soLuongConLai")
    val soLuongConLai: Int = 0,
    @SerializedName("trangThai")
    val trangThai: Boolean = false
) : Parcelable {

    fun isUsable(): Boolean = trangThai && soLuongConLai > 0 && phieuGiamGia.isActive()
}

// ===== SHOPPING CART MODELS =====

@Parcelize
data class GioHangUpdateMessage(
    @SerializedName("hoaDonId")
    val hoaDonId: Int = 0,
    @SerializedName("khachHangId")
    val khachHangId: Int? = null,
    @SerializedName("khachHang")
    val khachHang: KhachHang? = null,
    @SerializedName("gioHang")
    val gioHang: GioHang? = null,
    @SerializedName("timestamp")
    val timestamp: String = "",
    @SerializedName("action")
    val action: String = "update"
) : Parcelable

@Parcelize
data class GioHang(
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
    @SerializedName("idPhieuGiamGia")
    val idPhieuGiamGia: Int? = null,
    @SerializedName("maPhieuGiamGia")
    val maPhieuGiamGia: String? = null,
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