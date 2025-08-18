package com.example.realtimeordermonitor.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.realtimeordermonitor.data.*
import com.example.realtimeordermonitor.network.NetworkMonitor
import com.example.realtimeordermonitor.network.OrderWebSocketClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OrderViewModel(application: Application) : AndroidViewModel(application) {
    private val webSocketClient = OrderWebSocketClient()
    private val networkMonitor = NetworkMonitor(application)

    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "OrderViewModel"
        private const val MAX_ORDERS = 30 // Giảm từ 50 xuống 30
        private const val MAX_CUSTOMERS = 50 // Giảm từ 100 xuống 50
        private const val AUTO_LINK_TIME_WINDOW = 2 * 60 * 1000L // 2 phút thay vì 3 phút
    }

    init {
        viewModelScope.launch {
            networkMonitor.isConnected.collect { isConnected ->
                if (isConnected && !webSocketClient.isConnected()) {
                    connectWebSocket()
                }
                _uiState.value = _uiState.value.copy(isConnected = isConnected)
            }
        }
        connectWebSocket()
    }

    private fun connectWebSocket() {
        viewModelScope.launch {
            try {
                webSocketClient.connect(
                    onMessage = ::handleOrderMessage,
                    onConnectionChange = { connected ->
                        _uiState.value = _uiState.value.copy(isConnected = connected)
                    },
                    onCustomerUpdate = ::handleCustomerUpdate,
                    onVoucherOrderUpdate = ::handleVoucherOrderUpdate
                )
            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                _uiState.value = _uiState.value.copy(isConnected = false)
            }
        }
    }

    private fun handleVoucherOrderUpdate(voucherOrder: VoucherOrderUpdateResponse) {
        val currentState = _uiState.value
        val updatedVoucherInfo = currentState.orderVoucherInfo.toMutableMap()

        when {
            voucherOrder.isApplied() -> {
                updatedVoucherInfo[voucherOrder.hoaDonId] = voucherOrder
                Log.d(TAG, "✅ Voucher applied: ${voucherOrder.maPhieu} -> Order ${voucherOrder.hoaDonId}")
            }
            voucherOrder.isRemoved() -> {
                updatedVoucherInfo.remove(voucherOrder.hoaDonId)
                Log.d(TAG, "❌ Voucher removed: ${voucherOrder.maPhieu}")
            }
        }

        // Cleanup - chỉ giữ voucher cho các order gần đây
        val activeOrderIds = currentState.orders.take(20).map { it.id }.toSet()
        val cleanedVoucherInfo = updatedVoucherInfo.filterKeys { it in activeOrderIds }

        _uiState.value = currentState.copy(
            orderVoucherInfo = cleanedVoucherInfo,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun handleOrderMessage(orders: List<HoaDonDetailResponse>) {
        val currentState = _uiState.value
        val existingOrders = currentState.orders.associateBy { it.id }.toMutableMap()

        // Cập nhật hoặc thêm orders mới
        orders.forEach { order ->
            existingOrders[order.id] = order

            // Auto-link customer cho order mới
            if (!currentState.orderCustomerMapping.containsKey(order.id)) {
                tryAutoLinkCustomer(order, currentState)
            }
        }

        // Sắp xếp và giới hạn số lượng
        val sortedOrders = existingOrders.values
            .sortedByDescending { it.id }
            .take(MAX_ORDERS)

        _uiState.value = currentState.copy(
            orders = sortedOrders,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun handleCustomerUpdate(customer: KhachHang) {
        if (!customer.isValidForDisplay()) return

        val currentState = _uiState.value
        val updatedCustomers = currentState.khachHangInfo.toMutableMap()

        // Lưu customer với nhiều key để dễ tìm
        updatedCustomers["id_${customer.id}"] = customer
        customer.soDienThoai?.takeIf { it.isNotEmpty() }?.let {
            updatedCustomers[it] = customer
        }
        customer.email?.takeIf { it.isNotEmpty() }?.let {
            updatedCustomers[it] = customer
        }

        // Auto-link với orders gần đây
        val newMapping = currentState.orderCustomerMapping.toMutableMap()
        currentState.orders.take(5).forEach { order ->
            if (!newMapping.containsKey(order.id) && shouldLinkCustomer(customer, order)) {
                newMapping[order.id] = customer.id
            }
        }

        // Cleanup - giữ số lượng customer hợp lý
        val finalCustomers = if (updatedCustomers.size > MAX_CUSTOMERS) {
            val recentCustomers = updatedCustomers.values
                .distinctBy { it.id }
                .sortedByDescending { it.id }
                .take(25)

            recentCustomers.flatMap { c ->
                listOfNotNull(
                    "id_${c.id}" to c,
                    c.soDienThoai?.takeIf { it.isNotEmpty() }?.let { it to c },
                    c.email?.takeIf { it.isNotEmpty() }?.let { it to c }
                )
            }.toMap()
        } else updatedCustomers

        _uiState.value = currentState.copy(
            khachHangInfo = finalCustomers,
            orderCustomerMapping = newMapping,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun tryAutoLinkCustomer(order: HoaDonDetailResponse, state: OrderUiState) {
        if (state.orderCustomerMapping.containsKey(order.id)) return

        val customer = state.khachHangInfo.values.find { shouldLinkCustomer(it, order) }
        customer?.let {
            val newMapping = state.orderCustomerMapping + (order.id to it.id)
            _uiState.value = state.copy(orderCustomerMapping = newMapping)
        }
    }

    private fun shouldLinkCustomer(customer: KhachHang, order: HoaDonDetailResponse): Boolean {
        // Kiểm tra trực tiếp bằng SĐT
        if (order.soDienThoaiKhachHang.isNotEmpty() &&
            order.soDienThoaiKhachHang == customer.soDienThoai) return true

        // Kiểm tra trực tiếp bằng email
        if (order.emailKhachHang.isNotEmpty() &&
            order.emailKhachHang == customer.email) return true

        // Liên kết theo thời gian cho khách lẻ (trong vòng 2 phút)
        if (order.tenKhachHang == "Khách vãng lai" || order.soDienThoaiKhachHang.isEmpty()) {
            val orderTime = parseOrderTime(order.ngayTao)
            val timeDiff = Math.abs(System.currentTimeMillis() - orderTime)
            return timeDiff <= AUTO_LINK_TIME_WINDOW
        }

        return false
    }

    private fun parseOrderTime(dateString: String): Long {
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            format.parse(dateString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            webSocketClient.disconnect()
            connectWebSocket()
        }
    }

    fun clearOrders() {
        _uiState.value = _uiState.value.copy(
            orders = emptyList(),
            orderCustomerMapping = emptyMap(),
            orderVoucherInfo = emptyMap(),
            lastUpdated = System.currentTimeMillis()
        )
    }

    override fun onCleared() {
        super.onCleared()
        webSocketClient.disconnect()
        networkMonitor.cleanup()
    }
}