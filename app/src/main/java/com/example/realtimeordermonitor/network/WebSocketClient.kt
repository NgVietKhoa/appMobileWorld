package com.example.realtimeordermonitor.network

import android.util.Log
import com.example.realtimeordermonitor.data.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.*
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.LifecycleEvent

class OrderWebSocketClient {
    private var stompClient: StompClient? = null
    private val gson = Gson()
    private val disposables = CompositeDisposable()

    private var messageCallback: ((List<HoaDonDetailResponse>, Boolean) -> Unit)? = null
    private var customerCallback: ((KhachHang) -> Unit)? = null
    private var connectionCallback: ((Boolean) -> Unit)? = null
    private var voucherOrderCallback: ((VoucherOrderUpdateResponse) -> Unit)? = null

    private var isConnecting = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val reconnectDelay = 3000L

    companion object {
        private const val TAG = "OrderWebSocketClient"
        private const val WS_URL = "ws://192.168.1.3:8080/ws"
    }

    suspend fun connect(
        onMessage: (List<HoaDonDetailResponse>, Boolean) -> Unit,
        onConnectionChange: (Boolean) -> Unit,
        onCustomerUpdate: ((KhachHang) -> Unit)? = null,
        onVoucherOrderUpdate: ((VoucherOrderUpdateResponse) -> Unit)? = null
    ) {
        if (isConnecting || isConnected()) return

        isConnecting = true
        messageCallback = onMessage
        connectionCallback = onConnectionChange
        customerCallback = onCustomerUpdate
        voucherOrderCallback = onVoucherOrderUpdate

        withContext(Dispatchers.IO) {
            try {
                stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, WS_URL)
                stompClient?.withServerHeartbeat(30000)?.withClientHeartbeat(30000)

                disposables.add(
                    stompClient!!.lifecycle().subscribe { event ->
                        when (event.type) {
                            LifecycleEvent.Type.OPENED -> {
                                isConnecting = false
                                reconnectAttempts = 0
                                Log.d(TAG, "✅ WebSocket connected successfully")
                                connectionCallback?.invoke(true)
                                subscribeTopics()
                            }
                            LifecycleEvent.Type.ERROR, LifecycleEvent.Type.CLOSED -> {
                                isConnecting = false
                                Log.w(TAG, "❌ WebSocket disconnected: ${event.type}")
                                if (event.exception != null) {
                                    Log.e(TAG, "Connection exception:", event.exception)
                                }
                                connectionCallback?.invoke(false)
                                if (reconnectAttempts < maxReconnectAttempts) scheduleReconnect()
                            }
                            else -> {
                                Log.d(TAG, "WebSocket lifecycle event: ${event.type}")
                            }
                        }
                    }
                )
                stompClient?.connect()
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                isConnecting = false
                connectionCallback?.invoke(false)
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectAttempts++
        Log.d(TAG, "⏳ Scheduling reconnect attempt $reconnectAttempts/$maxReconnectAttempts")
        GlobalScope.launch(Dispatchers.IO) {
            delay(reconnectDelay)
            messageCallback?.let { callback ->
                connectionCallback?.let { connCallback ->
                    connect(callback, connCallback, customerCallback, voucherOrderCallback)
                }
            }
        }
    }

    private fun subscribeTopics() {
        val topics = mapOf(
            "/topic/gio-hang-update" to ::parseGioHangUpdate,
            "/topic/payment-success" to ::parsePaymentSuccess,
            "/topic/khach-hang-update" to ::parseKhachHangUpdate,
            "/topic/voucher-order-update" to ::parseVoucherOrderUpdate
        )

        Log.d(TAG, "🔄 Starting topic subscriptions...")

        topics.forEach { (topic, handler) ->
            try {
                val subscription = stompClient!!.topic(topic).subscribe(
                    { message ->
                        Log.d(TAG, "📨 ✅ MESSAGE RECEIVED from $topic")
                        Log.d(TAG, "📄 Raw message length: ${message.payload.length}")
                        Log.d(TAG, "📄 Raw message preview: ${message.payload.take(200)}...")

                        try {
                            handler(message.payload)
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error processing message from $topic", e)
                            Log.e(TAG, "Failed message: ${message.payload}")
                        }
                    },
                    { error ->
                        Log.e(TAG, "❌ SUBSCRIPTION ERROR on $topic", error)
                    }
                )

                disposables.add(subscription)
                Log.d(TAG, "✅ Successfully subscribed to $topic")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to subscribe to $topic", e)
            }
        }

        Log.d(TAG, "🔗 Completed subscriptions to ${topics.size} topics")

        Log.d(TAG, "🔍 WebSocket client status:")
        Log.d(TAG, "   - Is connected: ${stompClient?.isConnected}")
        Log.d(TAG, "   - Active disposables: ${disposables.size()}")

        try {
            val testDisposable = stompClient!!.topic("/topic/test").subscribe(
                { msg -> Log.d(TAG, "📨 Test message received: ${msg.payload}") },
                { err -> Log.d(TAG, "Test subscription error (expected): ${err.message}") }
            )
            disposables.add(testDisposable)
            Log.d(TAG, "✅ Test subscription created successfully")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Test subscription failed: ${e.message}")
        }
    }

    private fun parseGioHangUpdate(message: String) {
        try {
            Log.d(TAG, "🛒 Processing gio-hang-update:")
            Log.d(TAG, "📄 Message length: ${message.length}")

            val update = gson.fromJson(message, GioHangUpdateMessage::class.java)

            if (update.hoaDonId <= 0) {
                Log.w(TAG, "⚠️ Invalid hoaDonId: ${update.hoaDonId}")
                return
            }

            if (update.hasVoucherInfo()) {
                Log.d(TAG, "🎫 Voucher info from cart update: ${update.maPhieuGiamGia} - Amount: ${update.soTienGiam}")
                val voucherUpdate = VoucherOrderUpdateResponse(
                    action = "VOUCHER_APPLIED",
                    hoaDonId = update.hoaDonId,
                    phieuGiamGiaId = update.idPhieuGiamGia ?: 0,
                    maPhieu = update.maPhieuGiamGia ?: "",
                    giaTriGiam = update.soTienGiam ?: 0.0,
                    trangThai = true,
                    timestamp = update.timestamp
                )
                voucherOrderCallback?.invoke(voucherUpdate)
            }

            val order = createOrderFromCart(update)
            messageCallback?.invoke(listOf(order), true)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Parse gio hang update error: ${e.message}", e)
        }
    }

    private fun parsePaymentSuccess(message: String) {
        try {
            Log.d(TAG, "💳 Processing payment-success:")
            Log.d(TAG, "📄 Message length: ${message.length}")

            val info = gson.fromJson(message, Map::class.java)
            val order = gson.fromJson(gson.toJson(info["hoaDon"]), HoaDonDetailResponse::class.java)

            Log.d(TAG, "✅ Payment success for order: ${order.id}")
            messageCallback?.invoke(listOf(order), false)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Parse payment success error", e)
        }
    }

    private fun parseKhachHangUpdate(message: String) {
        Log.d(TAG, "🚨 KHACH HANG UPDATE RECEIVED!")
        Log.d(TAG, "📄 Message length: ${message.length}")
        Log.d(TAG, "📄 Full message: $message")

        try {
            val jsonElement = JsonParser.parseString(message)
            if (!jsonElement.isJsonObject) {
                Log.w(TAG, "⚠️ Message is not a valid JSON object")
                return
            }

            try {
                val customerUpdate = gson.fromJson(message, KhachHangUpdateMessage::class.java)
                Log.d(TAG, "📋 Parsed as KhachHangUpdateMessage:")
                Log.d(TAG, "   - Action: '${customerUpdate.action}'")
                Log.d(TAG, "   - Customer ID: ${customerUpdate.khachHangId}")
                Log.d(TAG, "   - Name: '${customerUpdate.ten}'")
                Log.d(TAG, "   - Phone: '${customerUpdate.soDienThoai}'")
                Log.d(TAG, "   - Email: '${customerUpdate.email}'")
                Log.d(TAG, "   - Has valid data: ${customerUpdate.hasValidData()}")

                if (customerUpdate.hasValidData()) {
                    val customer = customerUpdate.toKhachHang()
                    Log.d(TAG, "✅ CUSTOMER UPDATE FROM DEDICATED TOPIC SUCCESS!")
                    Log.d(TAG, "   Customer object: ID=${customer.id}, Name=${customer.ten}, Phone=${customer.soDienThoai}")

                    customerCallback?.let { callback ->
                        Log.d(TAG, "🔄 Invoking customer callback...")
                        callback.invoke(customer)
                        Log.d(TAG, "✅ Customer callback invoked successfully")
                    } ?: run {
                        Log.w(TAG, "⚠️ Customer callback is NULL!")
                    }
                    return
                } else {
                    Log.w(TAG, "⚠️ Customer update has invalid data")
                }
            } catch (e: Exception) {
                Log.d(TAG, "⚠️ Not structured format, trying legacy: ${e.message}")
            }

            val data = gson.fromJson(message, Map::class.java)
            Log.d(TAG, "📋 Parsed as Map - Available keys: ${data.keys}")

            val id = when (val idValue = data["khachHangId"]) {
                is Number -> idValue.toInt()
                is String -> idValue.toIntOrNull() ?: 0
                else -> 0
            }

            val ten = (data["ten"] as? String)?.trim() ?: ""
            val sdt = (data["soDienThoai"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            val email = (data["email"] as? String)?.trim()?.takeIf { it.isNotEmpty() }

            Log.d(TAG, "📊 Extracted legacy data: ID=$id, Name='$ten', Phone='$sdt', Email='$email'")

            if (id > 0 && ten.isNotEmpty()) {
                val customer = KhachHang(id = id, ten = ten, soDienThoai = sdt, email = email)
                Log.d(TAG, "✅ CUSTOMER UPDATE FROM LEGACY FORMAT SUCCESS!")

                customerCallback?.let { callback ->
                    Log.d(TAG, "🔄 Invoking customer callback (legacy)...")
                    callback.invoke(customer)
                    Log.d(TAG, "✅ Customer callback invoked successfully (legacy)")
                } ?: run {
                    Log.w(TAG, "⚠️ Customer callback is NULL (legacy)!")
                }
            } else {
                Log.w(TAG, "❌ Invalid customer data - ID: $id, Name: '$ten'")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Parse khach hang update FATAL ERROR: ${e.message}", e)
            e.printStackTrace()
        }
    }

    private fun parseVoucherOrderUpdate(message: String) {
        try {
            Log.d(TAG, "🎫 Processing voucher-order-update:")
            Log.d(TAG, "📄 Message length: ${message.length}")

            val voucherOrderUpdate = gson.fromJson(message, VoucherOrderUpdateResponse::class.java)
            Log.d(TAG, "✅ Voucher update: ${voucherOrderUpdate.maPhieu} for order ${voucherOrderUpdate.hoaDonId}")

            voucherOrderCallback?.invoke(voucherOrderUpdate)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Parse voucher order update error", e)
        }
    }

    private fun createOrderFromCart(update: GioHangUpdateMessage): HoaDonDetailResponse {
        val cart = update.gioHang ?: return HoaDonDetailResponse(
            id = update.hoaDonId,
            maHoaDon = update.maHoaDon.takeIf { it.isNotEmpty() }
                ?: "HD${update.hoaDonId.toString().padStart(6, '0')}",
            tenKhachHang = update.tenKhachHang ?: "Khách lẻ",
            soDienThoaiKhachHang = update.soDienThoaiKhachHang ?: "",
            emailKhachHang = update.emailKhachHang ?: "",
            khachHangId = update.idKhachHang ?: 0, // Giữ nguyên, nhưng có thể thêm logic nếu cần
            tongTien = 0L,
            tongTienSauGiam = 0L,
            tienGiamGia = 0L,
            maPhieuGiamGia = update.maPhieuGiamGia ?: "",
            trangThai = 0,
            trangThaiText = "Chờ xác nhận",
            ngayTao = getCurrentTimestamp(),
            sanPhamChiTiet = emptyList(),
            thanhToanInfo = emptyList()
        )

        val products = cart.chiTietGioHangDTOS.map { item ->
            Log.d(TAG, "   Product: ${item.tenSanPham} - Qty: ${item.soLuong}")
            item.toSanPhamChiTiet()
        }

        val calculatedTotal = products.sumOf { it.getActualThanhTien() }
        val cartTotal = cart.tongTien.toLong()
        val cartOriginalTotal = cart.tongTienGoc.toLong()

        val finalTotal = if (cartOriginalTotal > 0) cartOriginalTotal else calculatedTotal
        val finalAfterDiscount = cartTotal
        val voucherDiscountAmount = update.soTienGiam?.toLong() ?: 0L
        val discountAmount = maxOf(0L, finalTotal - finalAfterDiscount)
        val effectiveDiscountAmount = if (voucherDiscountAmount > 0) voucherDiscountAmount else discountAmount

        return HoaDonDetailResponse(
            id = update.hoaDonId,
            maHoaDon = update.maHoaDon.takeIf { it.isNotEmpty() }
                ?: "HD${update.hoaDonId.toString().padStart(6, '0')}",
            tenKhachHang = update.tenKhachHang ?: "Khách lẻ",
            soDienThoaiKhachHang = update.soDienThoaiKhachHang ?: "",
            emailKhachHang = update.emailKhachHang ?: "",
            khachHangId = update.idKhachHang ?: 0,
            tongTien = finalTotal,
            tongTienSauGiam = finalAfterDiscount,
            tienGiamGia = effectiveDiscountAmount,
            maPhieuGiamGia = update.maPhieuGiamGia ?: "",
            ghiChu = "",
            trangThai = 0,
            trangThaiText = "Chờ xác nhận",
            ngayTao = update.timestamp.takeIf { it.isNotEmpty() } ?: getCurrentTimestamp(),
            sanPhamChiTiet = products,
            thanhToanInfo = emptyList()
        )
    }

    private fun getCurrentTimestamp(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
    }

    fun disconnect() {
        Log.d(TAG, "🔌 Disconnecting WebSocket")
        stompClient?.disconnect()
        disposables.clear()
        isConnecting = false
        reconnectAttempts = 0
        connectionCallback?.invoke(false)
    }

    fun isConnected(): Boolean = stompClient?.isConnected ?: false
}