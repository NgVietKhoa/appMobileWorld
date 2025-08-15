package com.example.realtimeordermonitor.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.realtimeordermonitor.data.*
import com.example.realtimeordermonitor.network.NetworkMonitor
import com.example.realtimeordermonitor.network.OrderWebSocketClient
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class OrderViewModel(application: Application) : AndroidViewModel(application) {
    private val webSocketClient = OrderWebSocketClient()
    private val networkMonitor = NetworkMonitor(application)
    private val gson = Gson()

    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    private val _connectionStatus =
        MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    sealed class ConnectionStatus {
        object Connected : ConnectionStatus()
        object Connecting : ConnectionStatus()
        object Disconnected : ConnectionStatus()
        data class Error(val message: String) : ConnectionStatus()
    }

    companion object {
        private const val TAG = "OrderViewModel"
        private const val MAX_ORDERS_IN_MEMORY = 100
        private const val MAX_VOUCHERS_IN_MEMORY = 50
    }

    init {
        // Monitor network and WebSocket connection changes
        viewModelScope.launch {
            combine(
                networkMonitor.isConnected,
                networkMonitor.networkType,
                networkMonitor.connectionQuality
            ) { isNetworkConnected, networkType, quality ->
                Triple(isNetworkConnected, networkType, quality)
            }.collect { (isNetworkConnected, networkType, quality) ->
                handleNetworkChange(isNetworkConnected, networkType, quality)
            }
        }

        connectWebSocket()
    }

    private fun handleNetworkChange(
        isNetworkConnected: Boolean,
        networkType: NetworkMonitor.NetworkType,
        quality: NetworkMonitor.ConnectionQuality
    ) {
        Log.d(
            TAG,
            "Network changed - Connected: $isNetworkConnected, Type: $networkType, Quality: $quality"
        )

        if (isNetworkConnected && !webSocketClient.isConnected()) {
            Log.d(TAG, "Network available, attempting WebSocket reconnection")
            connectWebSocket()
        } else if (!isNetworkConnected) {
            Log.d(TAG, "Network lost, updating connection status")
            _connectionStatus.value = ConnectionStatus.Disconnected
            updateConnectionStatusInUiState(false)
        }

        when (quality) {
            NetworkMonitor.ConnectionQuality.POOR -> {
                _errorMessage.value = "Kết nối mạng yếu, có thể ảnh hưởng đến hiệu suất"
            }

            NetworkMonitor.ConnectionQuality.EXCELLENT,
            NetworkMonitor.ConnectionQuality.GOOD -> {
                _errorMessage.value = null
            }

            NetworkMonitor.ConnectionQuality.UNKNOWN -> {
                // Do nothing
            }
        }
    }

    private fun connectWebSocket() {
        viewModelScope.launch {
            try {
                _connectionStatus.value = ConnectionStatus.Connecting
                _errorMessage.value = null

                webSocketClient.connect(
                    onMessage = { messageList ->
                        handleWebSocketMessage(messageList)
                    },
                    onConnectionChange = { isConnected ->
                        handleWebSocketConnectionChange(isConnected)
                    },
                    onVoucherUpdate = { voucherResponse ->
                        handlePhieuGiamGiaUpdate(voucherResponse)
                    },
                    onCustomerUpdate = { khachHang ->
                        // Đảm bảo customer callback được gọi
                        Log.d(TAG, "Customer callback triggered: ${khachHang.ten}")
                        handleCustomerUpdate(khachHang)
                    },
                    onCustomerVoucherUpdate = { customerVoucherResponse ->
                        handleKhachHangPhieuGiamGiaUpdate(customerVoucherResponse)
                    },
                    onVoucherOrderUpdate = { voucherOrderUpdate ->
                        handleVoucherOrderUpdate(voucherOrderUpdate)
                    },
                    onDiscountCheck = { discountResponse ->
                        handleDiscountCheckResponse(discountResponse)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting WebSocket", e)
                _connectionStatus.value = ConnectionStatus.Error("Lỗi kết nối: ${e.message}")
                _errorMessage.value = "Không thể kết nối đến server: ${e.message}"
                updateConnectionStatusInUiState(false)
            }
        }
    }

    private fun handleVoucherOrderUpdate(voucherOrderUpdate: VoucherOrderUpdate) {
        Log.d(TAG, "Handling voucher order update: ${voucherOrderUpdate.maPhieu} for order ${voucherOrderUpdate.hoaDonId}")

        val currentState = _uiState.value
        val updatedVoucherOrderUpdates = currentState.voucherOrderUpdates.toMutableMap()

        updatedVoucherOrderUpdates[voucherOrderUpdate.hoaDonId] = voucherOrderUpdate

        _uiState.value = currentState.copy(
            voucherOrderUpdates = updatedVoucherOrderUpdates,
            lastUpdated = System.currentTimeMillis()
        )

        Log.d(TAG, "Voucher order update processed successfully")
    }

    private fun handlePhieuGiamGiaUpdate(response: PhieuGiamGiaResponse) {
        Log.d(TAG, "Handling voucher update with ${response.phieuGiamGias.size} vouchers")

        val currentState = _uiState.value
        val activeVouchers = response.phieuGiamGias.filter { it.isActive() }

        _uiState.value = currentState.copy(
            phieuGiamGias = response.phieuGiamGias.take(MAX_VOUCHERS_IN_MEMORY),
            totalActiveVouchers = activeVouchers.size,
            lastUpdated = System.currentTimeMillis()
        )

        Log.d(
            TAG,
            "Updated vouchers: ${response.phieuGiamGias.size} total, ${activeVouchers.size} active"
        )
    }


    // OrderViewModel.kt - Updated handleCustomerUpdate method

    private fun handleCustomerUpdate(khachHang: KhachHang) {
        Log.d(TAG, "Handling customer update - ID: ${khachHang.id}, Name: ${khachHang.ten}, Phone: ${khachHang.soDienThoai}")

        if (!khachHang.isValidForOrderDisplay()) {
            Log.d(TAG, "Skipping invalid customer: ${khachHang.id} - ${khachHang.ten}")
            return
        }

        val currentState = _uiState.value
        val updatedCustomerInfo = currentState.khachHangInfo.toMutableMap()

        // Store customer with multiple keys
        val keys = mutableSetOf<String>()
        keys.add("id_${khachHang.id}")
        khachHang.soDienThoai?.let { if (it.isNotEmpty()) keys.add(it) }
        khachHang.email?.let { if (it.isNotEmpty()) keys.add(it) }

        keys.forEach { key ->
            updatedCustomerInfo[key] = khachHang
            Log.d(TAG, "Storing customer with key: $key")
        }

        // SMART LINKING: Try to link this customer with recent orders
        val recentOrders = currentState.orders.take(5) // 5 đơn gần nhất
        var newOrderCustomerMapping = currentState.orderCustomerMapping.toMutableMap()

        for (order in recentOrders) {
            // Skip if already has mapping
            if (newOrderCustomerMapping.containsKey(order.id)) continue

            val shouldLink = shouldLinkCustomerToOrder(khachHang, order)

            if (shouldLink) {
                newOrderCustomerMapping[order.id] = khachHang.id
                Log.d(TAG, "🔗 Smart linking: Order ${order.maHoaDon} → Customer ${khachHang.getDisplayName()}")
            }
        }

        // Clean up old entries if too many
        val maxCustomers = 200
        val finalCustomerInfo = if (updatedCustomerInfo.size > maxCustomers) {
            val latestCustomers = updatedCustomerInfo.values
                .distinctBy { it.id }
                .sortedByDescending { it.id }
                .take(50)

            val newMap = mutableMapOf<String, KhachHang>()
            latestCustomers.forEach { customer ->
                newMap["id_${customer.id}"] = customer
                customer.soDienThoai?.let { if (it.isNotEmpty()) newMap[it] = customer }
                customer.email?.let { if (it.isNotEmpty()) newMap[it] = customer }
            }
            newMap
        } else {
            updatedCustomerInfo
        }

        _uiState.value = currentState.copy(
            khachHangInfo = finalCustomerInfo,
            orderCustomerMapping = newOrderCustomerMapping,
            lastUpdated = System.currentTimeMillis()
        )

        Log.d(TAG, "Customer updated successfully: ${khachHang.getOrderDisplayInfo()}")
        Log.d(TAG, "Total customer entries in cache: ${finalCustomerInfo.size}")
        Log.d(TAG, "Total order-customer mappings: ${newOrderCustomerMapping.size}")
    }

    // Smart logic to determine if customer should be linked to order
    private fun shouldLinkCustomerToOrder(customer: KhachHang, order: HoaDonDetailResponse): Boolean {
        Log.d(TAG, "Checking if customer ${customer.getDisplayName()} should link to order ${order.maHoaDon}")

        // Direct matches
        if (order.soDienThoaiKhachHang.isNotEmpty() &&
            order.soDienThoaiKhachHang == customer.soDienThoai) {
            Log.d(TAG, "✓ Direct phone match")
            return true
        }

        if (order.emailKhachHang.isNotEmpty() &&
            order.emailKhachHang == customer.email) {
            Log.d(TAG, "✓ Direct email match")
            return true
        }

        // Temporal matching for "guest" orders
        if (order.tenKhachHang == "Khách vãng lai" ||
            (order.soDienThoaiKhachHang.isEmpty() && order.emailKhachHang.isEmpty())) {

            val orderTime = parseOrderTime(order.ngayTao)
            val currentTime = System.currentTimeMillis()
            val timeDiff = Math.abs(currentTime - orderTime)

            // Link if order was created within 3 minutes of now (assuming customer just placed order)
            if (timeDiff <= 3 * 60 * 1000) {
                Log.d(TAG, "✓ Temporal match - guest order created ${timeDiff}ms ago")
                return true
            }
        }

        // Name similarity for non-guest orders
        if (order.tenKhachHang.isNotEmpty() &&
            order.tenKhachHang != "Khách vãng lai" &&
            order.tenKhachHang != "Khách lẻ") {

            val similarity = calculateNameSimilarity(order.tenKhachHang, customer.ten)
            if (similarity > 0.8) {
                Log.d(TAG, "✓ High name similarity: $similarity")
                return true
            }
        }

        Log.d(TAG, "✗ No match criteria met")
        return false
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

        val maxLength = maxOf(s1.length, s2.length)
        val commonChars = s1.toSet().intersect(s2.toSet()).size

        return commonChars.toDouble() / maxLength
    }

    // Enhanced handleWebSocketMessage to trigger customer linking check
    private fun handleWebSocketMessage(hoaDonList: List<HoaDonDetailResponse>) {
        val currentState = _uiState.value
        val updatedOrders = currentState.orders.toMutableList()

        Log.d(TAG, "Handling WebSocket message with ${hoaDonList.size} orders")

        hoaDonList.forEach { hoaDonDetail ->
            Log.d(TAG, "Processing order: ID=${hoaDonDetail.id}, Ma=${hoaDonDetail.maHoaDon}, TrangThai=${hoaDonDetail.trangThai}")
            Log.d(TAG, "Order customer info: Name='${hoaDonDetail.tenKhachHang}', Phone='${hoaDonDetail.soDienThoaiKhachHang}', Email='${hoaDonDetail.emailKhachHang}'")

            val existingIndex = updatedOrders.indexOfFirst { it.id == hoaDonDetail.id }
            if (existingIndex >= 0) {
                updatedOrders[existingIndex] = hoaDonDetail
                Log.d(TAG, "Updated existing order at index $existingIndex")
            } else {
                updatedOrders.add(0, hoaDonDetail)
                Log.d(TAG, "Added new order to top of list")

                // Try to auto-link with existing customers
                tryAutoLinkNewOrder(hoaDonDetail, currentState)
            }
        }

        updatedOrders.sortByDescending { it.id }
        val finalOrders = updatedOrders.take(MAX_ORDERS_IN_MEMORY)

        _uiState.value = currentState.copy(
            orders = finalOrders,
            lastUpdated = System.currentTimeMillis()
        )

        Log.d(TAG, "UI updated with ${finalOrders.size} total orders")
        _errorMessage.value = null
    }

    // Try to auto-link new order with existing customers
    private fun tryAutoLinkNewOrder(order: HoaDonDetailResponse, currentState: OrderUiState) {
        if (currentState.orderCustomerMapping.containsKey(order.id)) {
            Log.d(TAG, "Order ${order.id} already has customer mapping")
            return
        }

        val potentialCustomers = currentState.khachHangInfo.values.distinctBy { it.id }

        for (customer in potentialCustomers) {
            if (shouldLinkCustomerToOrder(customer, order)) {
                val newMapping = currentState.orderCustomerMapping + (order.id to customer.id)
                _uiState.value = currentState.copy(orderCustomerMapping = newMapping)
                Log.d(TAG, "🔗 Auto-linked new order ${order.maHoaDon} with customer ${customer.getDisplayName()}")
                return
            }
        }

        Log.d(TAG, "No auto-link found for order ${order.maHoaDon}")
    }

    // Đơn giản hóa customer voucher handling
    private fun handleKhachHangPhieuGiamGiaUpdate(response: KhachHangUpdateResponse) {
        Log.d(TAG, "Customer voucher update: ${response.count} vouchers")

        // Chỉ cập nhật customer info nếu có
        response.khachHang?.let { customer ->
            handleCustomerUpdate(customer)
        }

        // Không xử lý chi tiết voucher ở đây để giảm complexity
        // Chỉ log để biết có voucher update
        if (response.count > 0) {
            Log.d(TAG, "Customer has ${response.count} vouchers available")
        }
    }

    private fun handleDiscountCheckResponse(response: DiscountCodeCheckResponse) {
        Log.d(TAG, "Handling discount check response: ${response.valid}")
        // Handle discount check if needed
        // This could update UI to show discount validation result
    }

    private fun handleWebSocketConnectionChange(isConnected: Boolean) {
        Log.d(TAG, "WebSocket connection status changed: $isConnected")

        _connectionStatus.value = if (isConnected) {
            ConnectionStatus.Connected
        } else {
            ConnectionStatus.Disconnected
        }

        updateConnectionStatusInUiState(isConnected)

        if (isConnected) {
            _errorMessage.value = null
        } else {
            _errorMessage.value = "Mất kết nối với server"
        }
    }

    private fun updateConnectionStatusInUiState(isConnected: Boolean) {
        _uiState.value = _uiState.value.copy(isConnected = isConnected)
    }

    fun reconnect() {
        Log.d(TAG, "Manual reconnection requested")
        _errorMessage.value = null
        disconnect()
        connectWebSocket()
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        webSocketClient.disconnect()
        _connectionStatus.value = ConnectionStatus.Disconnected
        updateConnectionStatusInUiState(false)
    }

    fun clearOrders() {
        Log.d(TAG, "Clearing all orders")
        _uiState.value = _uiState.value.copy(
            orders = emptyList(),
            lastUpdated = System.currentTimeMillis()
        )
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, cleaning up resources")
        disconnect()
        networkMonitor.cleanup()
    }
}