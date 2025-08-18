package com.example.realtimeordermonitor.network

import android.util.Log
import com.example.realtimeordermonitor.data.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.*
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.LifecycleEvent

class OrderWebSocketClient {
    private var stompClient: StompClient? = null
    private val gson = Gson()
    private val disposables = CompositeDisposable()

    private var messageCallback: ((List<HoaDonDetailResponse>) -> Unit)? = null
    private var customerCallback: ((KhachHang) -> Unit)? = null
    private var connectionCallback: ((Boolean) -> Unit)? = null
    private var voucherOrderCallback: ((VoucherOrderUpdateResponse) -> Unit)? = null

    private var isConnecting = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val reconnectDelay = 3000L

    companion object {
        private const val TAG = "OrderWebSocketClient"
        private const val WS_URL = "ws://192.168.1.29:8080/ws"
    }

    suspend fun connect(
        onMessage: (List<HoaDonDetailResponse>) -> Unit,
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
                                connectionCallback?.invoke(true)
                                subscribeTopics()
                            }
                            LifecycleEvent.Type.ERROR, LifecycleEvent.Type.CLOSED -> {
                                isConnecting = false
                                connectionCallback?.invoke(false)
                                if (reconnectAttempts < maxReconnectAttempts) scheduleReconnect()
                            }
                            else -> {}
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
        // Chỉ subscribe các topic mà backend thực sự gửi
        val topics = mapOf(
            "/topic/hoa-don-list" to ::parseHoaDonList,
            "/topic/gio-hang-update" to ::parseGioHangUpdate,
            "/topic/payment-success" to ::parsePaymentSuccess,
            "/topic/khach-hang-update" to ::parseKhachHangUpdate,
            "/topic/voucher-order-update" to ::parseVoucherOrderUpdate
        )

        topics.forEach { (topic, handler) ->
            disposables.add(
                stompClient!!.topic(topic).subscribe(
                    { message -> handler(message.payload) },
                    { error -> Log.e(TAG, "Error on $topic", error) }
                )
            )
        }
    }

    private fun parseHoaDonList(message: String) {
        try {
            val type = object : TypeToken<List<HoaDonDetailResponse>>() {}.type
            val orders: List<HoaDonDetailResponse> = gson.fromJson(message, type)
            messageCallback?.invoke(orders)
        } catch (e: Exception) {
            Log.e(TAG, "Parse hoa don list error", e)
        }
    }

    private fun parseGioHangUpdate(message: String) {
        try {
            val update = gson.fromJson(message, GioHangUpdateMessage::class.java)
            val order = createOrderFromCart(update)
            messageCallback?.invoke(listOf(order))
        } catch (e: Exception) {
            Log.e(TAG, "Parse gio hang update error", e)
        }
    }

    private fun parsePaymentSuccess(message: String) {
        try {
            val info = gson.fromJson(message, Map::class.java)
            val order = gson.fromJson(gson.toJson(info["hoaDon"]), HoaDonDetailResponse::class.java)
            messageCallback?.invoke(listOf(order))
        } catch (e: Exception) {
            Log.e(TAG, "Parse payment success error", e)
        }
    }

    private fun parseKhachHangUpdate(message: String) {
        try {
            val data = gson.fromJson(message, Map::class.java)
            val id = (data["khachHangId"] as? Number)?.toInt() ?: 0
            val ten = data["ten"] as? String ?: ""
            val sdt = data["soDienThoai"] as? String
            val email = data["email"] as? String

            if (id > 0) {
                val customer = KhachHang(id = id, ten = ten, soDienThoai = sdt, email = email)
                customerCallback?.invoke(customer)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse khach hang update error", e)
        }
    }

    private fun parseVoucherOrderUpdate(message: String) {
        try {
            val voucherOrderUpdate = gson.fromJson(message, VoucherOrderUpdateResponse::class.java)
            voucherOrderCallback?.invoke(voucherOrderUpdate)
            Log.d(TAG, "Voucher order update: ${voucherOrderUpdate.maPhieu} for order ${voucherOrderUpdate.hoaDonId}, action: ${voucherOrderUpdate.action}")
        } catch (e: Exception) {
            Log.e(TAG, "Parse voucher order update error", e)
        }
    }

    private fun createOrderFromCart(update: GioHangUpdateMessage): HoaDonDetailResponse {
        val cart = update.gioHang ?: return HoaDonDetailResponse(
            id = update.hoaDonId,
            maHoaDon = update.maHoaDon
        )

        val products = cart.chiTietGioHangDTOS.map { it.toSanPhamChiTiet() }

        return HoaDonDetailResponse(
            id = update.hoaDonId,
            maHoaDon = update.maHoaDon,
            tenKhachHang = "Khách vãng lai", // Sẽ được update qua customer callback
            soDienThoaiKhachHang = "",
            emailKhachHang = "",
            tongTien = cart.tongTienGoc.toLong(),
            tongTienSauGiam = cart.tongTien.toLong(),
            tienGiamGia = (cart.tongTienGoc - cart.tongTien).toLong(),
            trangThai = 0,
            trangThaiText = "Chờ xác nhận",
            ngayTao = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date()),
            sanPhamChiTiet = products,
            thanhToanInfo = emptyList()
        )
    }

    fun disconnect() {
        stompClient?.disconnect()
        disposables.clear()
        isConnecting = false
        reconnectAttempts = 0
        connectionCallback?.invoke(false)
    }

    fun isConnected(): Boolean = stompClient?.isConnected ?: false
}