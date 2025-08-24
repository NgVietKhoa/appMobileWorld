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

    private val pendingCustomerUpdates = mutableMapOf<Int, KhachHang>()

    companion object {
        private const val TAG = "OrderViewModel"
        private const val MAX_ORDERS = 30
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
                        Log.d(TAG, "🔗 Connection status changed: $connected")
                        _uiState.value = _uiState.value.copy(isConnected = connected)
                    },
                    onCustomerUpdate = ::handleCustomerUpdate,
                    onVoucherOrderUpdate = ::handleVoucherOrderUpdate,
                    onPaymentSuccess = ::handlePaymentSuccess // Thêm callback cho payment success
                )
            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                _uiState.value = _uiState.value.copy(isConnected = false)
            }
        }
    }

    private fun handlePaymentSuccess(paymentInfo: PaymentSuccessInfo) {
        Log.d(TAG, "💳 ============ PAYMENT SUCCESS RECEIVED ============")
        Log.d(TAG, "   - Order ID: ${paymentInfo.hoaDonId}")
        Log.d(TAG, "   - Action: ${paymentInfo.action}")
        Log.d(TAG, "   - Order Code: ${paymentInfo.hoaDon.ma}")
        Log.d(TAG, "   - Customer: ${paymentInfo.hoaDon.tenKhachHang}")
        Log.d(TAG, "   - Status: ${paymentInfo.hoaDon.trangThai}")

        val currentState = _uiState.value

        // Tìm và xóa hóa đơn đã thanh toán thành công
        val updatedOrders = currentState.orders.filter { it.id != paymentInfo.hoaDonId }

        // Xóa thông tin voucher liên quan đến hóa đơn này
        val updatedVoucherInfo = currentState.orderVoucherInfo.filterKeys { it != paymentInfo.hoaDonId }

        // Xóa thông tin customer pending nếu có
        pendingCustomerUpdates.clear()

        // Cập nhật UI state - reset về trạng thái ban đầu
        _uiState.value = currentState.copy(
            orders = updatedOrders,
            orderVoucherInfo = updatedVoucherInfo,
            lastUpdated = System.currentTimeMillis()
        )

        Log.d(TAG, "✅ Payment success processed:")
        Log.d(TAG, "   - Removed order ${paymentInfo.hoaDonId} from active orders")
        Log.d(TAG, "   - Remaining orders: ${updatedOrders.size}")
        Log.d(TAG, "   - Remaining vouchers: ${updatedVoucherInfo.size}")
        Log.d(TAG, "   - Pending customers cleared")
        Log.d(TAG, "================ PAYMENT SUCCESS COMPLETED ================")
    }

    private fun handleVoucherOrderUpdate(voucherOrder: VoucherOrderUpdateResponse) {
        Log.d(
            TAG,
            "🎫 Voucher update received: ${voucherOrder.action} - ${voucherOrder.maPhieu} - Order: ${voucherOrder.hoaDonId} - Amount: ${voucherOrder.giaTriGiam}"
        )

        val currentState = _uiState.value
        val updatedVoucherInfo = currentState.orderVoucherInfo.toMutableMap()

        when {
            voucherOrder.isApplied() -> {
                updatedVoucherInfo[voucherOrder.hoaDonId] = voucherOrder
                Log.d(
                    TAG,
                    "✅ Voucher applied: ${voucherOrder.maPhieu} -> Order ${voucherOrder.hoaDonId}"
                )
            }

            voucherOrder.isRemoved() -> {
                updatedVoucherInfo.remove(voucherOrder.hoaDonId)
                Log.d(
                    TAG,
                    "❌ Voucher removed: ${voucherOrder.maPhieu} from Order ${voucherOrder.hoaDonId}"
                )
            }
        }

        val activeOrderIds = currentState.orders.map { it.id }.toSet()
        val cleanedVoucherInfo = updatedVoucherInfo.filterKeys { it in activeOrderIds }

        _uiState.value = currentState.copy(
            orderVoucherInfo = cleanedVoucherInfo,
            lastUpdated = System.currentTimeMillis()
        )

        Log.d(TAG, "📊 Current voucher state: ${cleanedVoucherInfo.keys}")
    }

    private fun handleOrderMessage(orders: List<HoaDonDetailResponse>, shouldReplace: Boolean) {
        Log.d(TAG, "📦 Orders received: ${orders.size}, shouldReplace: $shouldReplace")

        val currentState = _uiState.value

        val ordersWithCustomerInfo = orders.map { order ->
            // Áp dụng thông tin khách hàng từ pendingCustomerUpdates nếu có
            val pendingCustomer = pendingCustomerUpdates.values.lastOrNull()
            if (pendingCustomer != null && pendingCustomer.isValidForDisplay()) {
                Log.d(TAG, "👤 Applying pending customer update to order ${order.id}: ${pendingCustomer.ten}")
                order.copy(
                    tenKhachHang = pendingCustomer.ten,
                    soDienThoaiKhachHang = pendingCustomer.soDienThoai ?: "",
                    emailKhachHang = pendingCustomer.email ?: "",
                    khachHangId = pendingCustomer.id // Thay thế khachHangId
                )
            } else {
                order
            }
        }

        val finalOrders = if (shouldReplace) {
            Log.d(TAG, "🔄 Replacing all orders with new cart data")
            ordersWithCustomerInfo
        } else {
            Log.d(TAG, "🔄 Updating existing orders list")
            val existingOrders = currentState.orders.associateBy { it.id }.toMutableMap()

            ordersWithCustomerInfo.forEach { order ->
                val wasExisting = existingOrders.containsKey(order.id)
                existingOrders[order.id] = order
                Log.d(TAG, "Order ${order.id}: ${if (wasExisting) "updated" else "new"}")
            }

            existingOrders.values.sortedByDescending { it.id }.take(MAX_ORDERS)
        }

        val currentOrderIds = finalOrders.map { it.id }.toSet()
        val filteredVoucherInfo = currentState.orderVoucherInfo.filterKeys { it in currentOrderIds }

        pendingCustomerUpdates.keys.retainAll(finalOrders.map { it.khachHangId }.toSet()) // Clean up by khachHangId

        _uiState.value = currentState.copy(
            orders = finalOrders,
            orderVoucherInfo = filteredVoucherInfo,
            lastUpdated = System.currentTimeMillis()
        )

        Log.d(
            TAG,
            "📋 Final orders count: ${finalOrders.size}, vouchers: ${filteredVoucherInfo.size}, pending customers: ${pendingCustomerUpdates.size}"
        )
    }

    private fun handleCustomerUpdate(customer: KhachHang) {
        Log.d(TAG, "👤 ============ CUSTOMER UPDATE RECEIVED ============")
        Log.d(TAG, "   - ID: ${customer.id}")
        Log.d(TAG, "   - Name: '${customer.ten}'")
        Log.d(TAG, "   - Phone: '${customer.soDienThoai}'")
        Log.d(TAG, "   - Email: '${customer.email}'")
        Log.d(TAG, "   - Valid for display: ${customer.isValidForDisplay()}")

        val currentState = _uiState.value

        // Kiểm tra nếu là khách vãng lai (tên là "Khách lẻ" hoặc tương tự)
        val isWalkInCustomer = customer.ten.equals("Khách vãng lai", ignoreCase = true) ||
                customer.ten.equals("Khách lẻ", ignoreCase = true) ||
                customer.id <= 0

        if (isWalkInCustomer) {
            Log.d(TAG, "🚶 Walk-in customer detected, clearing previous customer info")

            // Xóa thông tin khách hàng cũ và reset về khách vãng lai
            val updatedOrders = currentState.orders.map { order ->
                Log.d(TAG, "🔄 Resetting order ${order.id} to walk-in customer")
                order.copy(
                    tenKhachHang = "Khách vãng lai",
                    soDienThoaiKhachHang = "", // Xóa số điện thoại cũ
                    emailKhachHang = "", // Xóa email cũ
                    khachHangId = 0 // Reset khachHangId
                )
            }

            // Clear pending customer updates
            pendingCustomerUpdates.clear()

            _uiState.value = currentState.copy(
                orders = updatedOrders,
                lastUpdated = System.currentTimeMillis()
            )

            Log.d(TAG, "✅ All orders reset to walk-in customer")
            return
        }

        if (!customer.isValidForDisplay()) {
            Log.w(TAG, "⚠️ Invalid customer data, ignoring")
            return
        }

        // Thay thế thông tin khách hàng cho đơn hàng mới nhất hoặc tất cả đơn hàng
        val updatedOrders = currentState.orders.map { order ->
            Log.d(TAG, "✅ Updating order ${order.id} with customer info: ${customer.ten}")
            order.copy(
                tenKhachHang = customer.ten,
                soDienThoaiKhachHang = customer.soDienThoai ?: "", // Sử dụng empty string thay vì giữ lại giá trị cũ
                emailKhachHang = customer.email ?: "", // Sử dụng empty string thay vì giữ lại giá trị cũ
                khachHangId = customer.id
            )
        }

        // Lưu thông tin khách hàng vào pendingCustomerUpdates cho các đơn hàng mới
        pendingCustomerUpdates[customer.id] = customer

        // Cập nhật UI state
        _uiState.value = currentState.copy(
            orders = updatedOrders,
            lastUpdated = System.currentTimeMillis()
        )

        Log.d(TAG, "🎉 UI updated with customer info for all orders")
        Log.d(TAG, "================ CUSTOMER UPDATE COMPLETED ================")
    }

    fun reconnect() {
        Log.d(TAG, "🔄 Manual reconnect triggered")
        viewModelScope.launch {
            webSocketClient.disconnect()
            pendingCustomerUpdates.clear()
            _uiState.value = _uiState.value.copy(
                orderVoucherInfo = emptyMap(),
                isConnected = false
            )
            connectWebSocket()
        }
    }

    fun clearOrders() {
        Log.d(TAG, "🗑️ Clearing all orders and state")
        pendingCustomerUpdates.clear()
        _uiState.value = _uiState.value.copy(
            orders = emptyList(),
            orderVoucherInfo = emptyMap(),
            lastUpdated = System.currentTimeMillis()
        )
    }

    override fun onCleared() {
        super.onCleared()
        webSocketClient.disconnect()
        networkMonitor.cleanup()
        pendingCustomerUpdates.clear()
    }
}