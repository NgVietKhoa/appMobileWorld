package com.example.realtimeordermonitor.network

import android.util.Log
import com.example.realtimeordermonitor.data.*
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.LifecycleEvent
import ua.naiksoftware.stomp.dto.StompMessage

class OrderWebSocketClient {
    private var stompClient: StompClient? = null
    private val gson = Gson()
    private var messageCallback: ((List<HoaDonDetailResponse>) -> Unit)? = null
    private var voucherCallback: ((PhieuGiamGiaResponse) -> Unit)? = null
    private var customerCallback: ((KhachHang) -> Unit)? = null
    private var customerVoucherCallback: ((KhachHangUpdateResponse) -> Unit)? = null
    private var voucherOrderCallback: ((VoucherOrderUpdate) -> Unit)? = null
    private var discountCheckCallback: ((DiscountCodeCheckResponse) -> Unit)? = null
    private var connectionCallback: ((Boolean) -> Unit)? = null
    private val disposables = CompositeDisposable()
    private var isConnecting = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10
    private val reconnectDelay = 5000L

    companion object {
        private const val TAG = "OrderWebSocketClient"
        private const val WS_URL = "ws://192.168.1.29:8080/ws"
    }

    suspend fun connect(
        onMessage: (List<HoaDonDetailResponse>) -> Unit,
        onConnectionChange: (Boolean) -> Unit,
        onVoucherUpdate: ((PhieuGiamGiaResponse) -> Unit)? = null,
        onCustomerUpdate: ((KhachHang) -> Unit)? = null,
        onCustomerVoucherUpdate: ((KhachHangUpdateResponse) -> Unit)? = null,
        onVoucherOrderUpdate: ((VoucherOrderUpdate) -> Unit)? = null,
        onDiscountCheck: ((DiscountCodeCheckResponse) -> Unit)? = null
    ) {
        if (isConnecting || isConnected()) {
            Log.d(TAG, "Already connecting or connected, skipping new connection")
            return
        }

        isConnecting = true
        messageCallback = onMessage
        connectionCallback = onConnectionChange
        voucherCallback = onVoucherUpdate
        customerCallback = onCustomerUpdate
        customerVoucherCallback = onCustomerVoucherUpdate
        voucherOrderCallback = onVoucherOrderUpdate
        discountCheckCallback = onDiscountCheck

        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to connect to WebSocket at $WS_URL")
                stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, WS_URL)

                stompClient?.withServerHeartbeat(30000)
                stompClient?.withClientHeartbeat(30000)

                disposables.add(
                    stompClient!!.lifecycle().subscribe { lifecycleEvent ->
                        when (lifecycleEvent.type) {
                            LifecycleEvent.Type.OPENED -> {
                                Log.d(TAG, "STOMP connection opened successfully")
                                isConnecting = false
                                reconnectAttempts = 0
                                connectionCallback?.invoke(true)
                                subscribeAllTopics()
                            }
                            LifecycleEvent.Type.ERROR -> {
                                Log.e(TAG, "STOMP connection error: ${lifecycleEvent.message}", lifecycleEvent.exception)
                                isConnecting = false
                                connectionCallback?.invoke(false)
                                scheduleReconnect()
                            }
                            LifecycleEvent.Type.CLOSED -> {
                                Log.d(TAG, "STOMP connection closed")
                                isConnecting = false
                                connectionCallback?.invoke(false)
                                scheduleReconnect()
                            }
                            else -> {
                                Log.d(TAG, "Other lifecycle event: ${lifecycleEvent.type}")
                            }
                        }
                    }
                )

                stompClient?.connect()
                Log.d(TAG, "Initiated STOMP connection")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to WebSocket", e)
                isConnecting = false
                connectionCallback?.invoke(false)
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++
            Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempts/$maxReconnectAttempts")
            GlobalScope.launch(Dispatchers.IO) {
                delay(reconnectDelay)
                messageCallback?.let { callback ->
                    connectionCallback?.let { connCallback ->
                        connect(callback, connCallback, voucherCallback, customerCallback, customerVoucherCallback, voucherOrderCallback, discountCheckCallback)
                    }
                }
            }
        } else {
            Log.e(TAG, "Max reconnection attempts reached")
        }
    }

    private fun subscribeAllTopics() {
        Log.d(TAG, "Starting to subscribe to all topics...")

        subscribeTopic("/topic/hoa-don-list") { message ->
            parseHoaDonListMessage(message.payload)
        }

        subscribeTopic("/topic/hoa-don-create") { message ->
            parseHoaDonCreateMessage(message.payload)
        }

        subscribeTopic("/topic/hoa-don-delete") { message ->
            parseHoaDonDeleteMessage(message.payload)
        }

        subscribeTopic("/topic/hoa-don-detail") { message ->
            parseHoaDonDetailMessage(message.payload)
        }

        subscribeTopic("/topic/single-hoa-don") { message ->
            parseSingleHoaDonMessage(message.payload)
        }

        subscribeTopic("/topic/gio-hang-update") { message ->
            parseGioHangUpdateMessage(message.payload)
        }

        subscribeTopic("/topic/gio-hang-delete") { message ->
            parseGioHangDeleteMessage(message.payload)
        }

        subscribeTopic("/topic/payment-success") { message ->
            parsePaymentSuccessMessage(message.payload)
        }

        // Cập nhật để chỉ lấy thông tin khách hàng cần thiết
        subscribeTopic("/topic/khach-hang-update") { message ->
            parseKhachHangUpdateMessage(message.payload)
        }

        subscribeTopic("/topic/voucher-update") { message ->
            parsePhieuGiamGiaMessage(message.payload)
        }

        subscribeTopic("/topic/khach-hang-phieu-giam-gia") { message ->
            parsePhieuGiamGiaCaNhanMessage(message.payload)
        }

        // Thêm topic mới cho voucher order update
        subscribeTopic("/topic/voucher-order-update") { message ->
            parseVoucherOrderUpdateMessage(message.payload)
        }

        subscribeTopic("/topic/discount-code-check") { message ->
            parseDiscountCheckMessage(message.payload)
        }

        Log.d(TAG, "Subscribed to all topics successfully")
    }

    private fun subscribeTopic(topic: String, handler: (StompMessage) -> Unit) {
        disposables.add(
            stompClient!!.topic(topic).subscribe({ message ->
                Log.d(TAG, "Received message on $topic: ${message.payload}")
                handler(message)
            }, { error ->
                Log.e(TAG, "Error on topic $topic", error)
            })
        )
        Log.d(TAG, "Subscribed to topic: $topic")
    }

    private fun parseHoaDonListMessage(message: String) {
        try {
            val type = object : TypeToken<List<HoaDonDetailResponse>>() {}.type
            val hoaDonList: List<HoaDonDetailResponse> = gson.fromJson(message, type)
            messageCallback?.invoke(hoaDonList)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON parsing error in hoa-don-list: $message", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing hoa-don-list message", e)
        }
    }

    private fun parseHoaDonCreateMessage(message: String) {
        try {
            val hoaDonDTO = gson.fromJson(message, HoaDonDetailResponse::class.java)
            messageCallback?.invoke(listOf(hoaDonDTO))
        } catch (e: Exception) {
            Log.e(TAG, "Error processing hoa-don-create message", e)
        }
    }

    private fun parseHoaDonDeleteMessage(message: String) {
        try {
            val deleteInfo = gson.fromJson(message, Map::class.java)
            val hoaDonId = (deleteInfo["hoaDonId"] as? Number)?.toInt() ?: return
            messageCallback?.invoke(emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "Error processing hoa-don-delete message", e)
        }
    }

    private fun parseHoaDonDetailMessage(message: String) {
        try {
            val hoaDonDTO = gson.fromJson(message, HoaDonDetailResponse::class.java)
            messageCallback?.invoke(listOf(hoaDonDTO))
        } catch (e: Exception) {
            Log.e(TAG, "Error processing hoa-don-detail message", e)
        }
    }

    private fun parseSingleHoaDonMessage(message: String) {
        try {
            val update = gson.fromJson(message, Map::class.java)
            val hoaDon = gson.fromJson(gson.toJson(update["hoaDon"]), HoaDonDetailResponse::class.java)
            messageCallback?.invoke(listOf(hoaDon))
        } catch (e: Exception) {
            Log.e(TAG, "Error processing single-hoa-don message", e)
        }
    }

    private fun parseGioHangUpdateMessage(message: String) {
        try {
            val gioHangUpdate = gson.fromJson(message, GioHangUpdateMessage::class.java)
            val hoaDonId = gioHangUpdate.hoaDonId
            val gioHang = gioHangUpdate.gioHang ?: return

            Log.d(TAG, "Processing gio hang update for order: $hoaDonId")

            // Extract customer info from the cart update
            val khachHang = gioHangUpdate.khachHang

            val createdOrder = createOrderFromGioHang(hoaDonId, gioHang, gioHangUpdate)

            // If we have customer info, process it separately to ensure it's in cache
            if (khachHang != null && khachHang.isValidForOrderDisplay()) {
                Log.d(TAG, "Found customer in cart update: ${khachHang.getDisplayName()}")
                customerCallback?.invoke(khachHang)

                // Create order-customer context
                messageCallback?.invoke(listOf(createdOrder))

                // Notify about the customer-order relationship
                notifyOrderCustomerLink(hoaDonId, khachHang.id)
            } else {
                messageCallback?.invoke(listOf(createdOrder))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing gio-hang-update message", e)
        }
    }

    private fun parseGioHangDeleteMessage(message: String) {
        try {
            val deleteInfo = gson.fromJson(message, Map::class.java)
            val hoaDonId = (deleteInfo["hoaDonId"] as? Number)?.toInt() ?: return
            // Xử lý xóa trong UI nếu cần
        } catch (e: Exception) {
            Log.e(TAG, "Error processing gio-hang-delete message", e)
        }
    }

    private fun parsePaymentSuccessMessage(message: String) {
        try {
            val paymentInfo = gson.fromJson(message, Map::class.java)
            val hoaDon = gson.fromJson(gson.toJson(paymentInfo["hoaDon"]), HoaDonDetailResponse::class.java)
            messageCallback?.invoke(listOf(hoaDon))
        } catch (e: Exception) {
            Log.e(TAG, "Error processing payment-success message", e)
        }
    }

    private fun parseKhachHangUpdateMessage(message: String) {
        Log.d(TAG, "Parsing khach hang update message: $message")
        try {
            val rawMessage = gson.fromJson(message, Map::class.java)
            Log.d(TAG, "Raw khach hang message: $rawMessage")

            val action = rawMessage["action"] as? String
            val khachHangId = (rawMessage["khachHangId"] as? Number)?.toInt() ?: 0
            val ten = rawMessage["ten"] as? String ?: ""
            val soDienThoai = rawMessage["soDienThoai"] as? String
            val email = rawMessage["email"] as? String
            val timestamp = rawMessage["timestamp"] as? String ?: ""

            Log.d(TAG, "Parsed customer info - ID: $khachHangId, Tên: $ten, SĐT: $soDienThoai, Email: $email")

            if (khachHangId > 0 && customerCallback != null) {
                val khachHang = KhachHang(
                    id = khachHangId,
                    ten = ten,
                    soDienThoai = soDienThoai,
                    email = email
                )

                // Enhanced callback với context information
                customerCallback?.invoke(khachHang)
                Log.d(TAG, "Customer info processed successfully: ${khachHang.getDisplayName()} - ${khachHang.soDienThoai ?: khachHang.email}")

                // Try to link with recent orders if possible
                linkCustomerToRecentOrders(khachHang)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing khach hang update message", e)
        }
    }

    // New method to link customer with recent orders
    private fun linkCustomerToRecentOrders(customer: KhachHang) {
        Log.d(TAG, "Attempting to link customer ${customer.id} with recent orders...")
    }

    // Thêm hàm xử lý voucher order update mới
    private fun parseVoucherOrderUpdateMessage(message: String) {
        Log.d(TAG, "Parsing voucher order update message: $message")
        try {
            val voucherOrderUpdate = gson.fromJson(message, VoucherOrderUpdate::class.java)
            voucherOrderCallback?.invoke(voucherOrderUpdate)
            Log.d(TAG, "Voucher order update processed: ${voucherOrderUpdate.maPhieu} for order ${voucherOrderUpdate.hoaDonId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing voucher order update message", e)
        }
    }

    private fun parsePhieuGiamGiaMessage(message: String) {
        Log.d(TAG, "Parsing phieu giam gia message: $message")
        try {
            val rawMessage = gson.fromJson(message, Map::class.java)
            Log.d(TAG, "Raw voucher message: $rawMessage")

            val action = rawMessage["action"] as? String
            val voucherId = (rawMessage["phieuGiamGiaId"] as? Number)?.toInt()
            val voucherCode = rawMessage["maPhieu"] as? String

            Log.d(TAG, "Voucher update - Action: $action, ID: $voucherId, Code: $voucherCode")

            if (voucherCallback != null && voucherId != null) {
                val response = PhieuGiamGiaResponse(
                    phieuGiamGias = emptyList(),
                    totalCount = 0,
                    activeCount = 0
                )
                voucherCallback?.invoke(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing phieu giam gia message", e)

            try {
                val phieuGiamGiaResponse = gson.fromJson(message, PhieuGiamGiaResponse::class.java)
                voucherCallback?.invoke(phieuGiamGiaResponse)
                Log.d(TAG, "Direct parsing successful: ${phieuGiamGiaResponse.phieuGiamGias.size} vouchers")
            } catch (e2: Exception) {
                Log.e(TAG, "Both parsing methods failed", e2)
            }
        }
    }

    private fun parsePhieuGiamGiaCaNhanMessage(message: String) {
        Log.d(TAG, "Parsing phieu giam gia ca nhan message: $message")
        try {
            val customerVoucherResponse = gson.fromJson(message, KhachHangUpdateResponse::class.java)
            customerVoucherCallback?.invoke(customerVoucherResponse)
            Log.d(TAG, "Parsed ${customerVoucherResponse.phieuGiamGias.size} customer vouchers")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing phieu giam gia ca nhan message", e)
        }
    }

    private fun parseDiscountCheckMessage(message: String) {
        Log.d(TAG, "Parsing discount check message: $message")
        try {
            val discountCheckResponse = gson.fromJson(message, DiscountCodeCheckResponse::class.java)
            discountCheckCallback?.invoke(discountCheckResponse)
            Log.d(TAG, "Discount check result processed")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing discount check message", e)
        }
    }

    private fun createOrderFromGioHang(
        hoaDonId: Int,
        gioHang: GioHang,
        gioHangUpdate: GioHangUpdateMessage
    ): HoaDonDetailResponse {
        val sanPhamList = gioHang.chiTietGioHangDTOS.map { it.toSanPhamChiTiet() }

        val khachHang = gioHangUpdate.khachHang

        // Enhanced customer name handling
        val customerName = when {
            khachHang?.ten?.isNotEmpty() == true && khachHang.ten != "Khách lẻ" -> khachHang.ten
            else -> "Khách vãng lai"
        }

        val customerPhone = khachHang?.soDienThoai?.takeIf { it.isNotEmpty() } ?: ""
        val customerEmail = khachHang?.email?.takeIf { it.isNotEmpty() } ?: ""

        val totalOriginalPrice = gioHang.chiTietGioHangDTOS.sumOf {
            it.giaBanGoc * it.soLuong
        }
        val discountAmount = (totalOriginalPrice - gioHang.tongTien).coerceAtLeast(0.0)

        val discountVoucher = if (discountAmount > 0) {
            gioHang.chiTietGioHangDTOS.find { it.idPhieuGiamGia != null }?.let {
                "PGG_${it.idPhieuGiamGia}"
            } ?: ""
        } else ""

        Log.d(TAG, "Creating order with customer info - Name: '$customerName', Phone: '$customerPhone', Email: '$customerEmail'")

        return HoaDonDetailResponse(
            id = hoaDonId,
            maHoaDon = "HD_CART_$hoaDonId",
            tenKhachHang = customerName,
            soDienThoaiKhachHang = customerPhone,
            emailKhachHang = customerEmail,
            tongTien = totalOriginalPrice.toLong(),
            tongTienSauGiam = gioHang.tongTien.toLong(),
            tienGiamGia = discountAmount.toLong(),
            maPhieuGiamGia = discountVoucher,
            phiVanChuyen = 0L,
            ghiChu = "Đơn hàng được tạo từ giỏ hàng",
            trangThai = 0,
            trangThaiText = "Chờ xác nhận",
            loaiDon = "trực tiếp",
            ngayTao = gioHangUpdate.timestamp.takeIf { it.isNotEmpty() }
                ?: java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(System.currentTimeMillis())),
            ngayThanhToan = "",
            tenNhanVien = "",
            sanPhamChiTiet = sanPhamList,
            thanhToanInfo = emptyList()
        )
    }

    // Method to notify about order-customer relationship
    private fun notifyOrderCustomerLink(orderId: Int, customerId: Int) {
        Log.d(TAG, "Linking order $orderId with customer $customerId")
    }

    fun disconnect() {
        if (isConnected()) {
            Log.d(TAG, "Disconnecting WebSocket")
            stompClient?.disconnect()
            disposables.clear()
            connectionCallback?.invoke(false)
        } else {
            Log.d(TAG, "No active WebSocket connection to disconnect")
        }
        isConnecting = false
        reconnectAttempts = 0
    }

    fun isConnected(): Boolean {
        val connected = stompClient?.isConnected ?: false
        Log.d(TAG, "WebSocket connection status: $connected")
        return connected
    }
}