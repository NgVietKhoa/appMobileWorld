package com.example.realtimeordermonitor.ui

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.realtimeordermonitor.data.*
import com.example.realtimeordermonitor.utils.*
import com.example.realtimeordermonitor.viewmodel.OrderViewModel
import com.example.realtimeordermonitor.utils.toVNDCurrency
import ua.naiksoftware.stomp.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(viewModel: OrderViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to top when new orders arrive
    LaunchedEffect(uiState.orders.size) {
        if (uiState.orders.isNotEmpty() && listState.firstVisibleItemIndex <= 2) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Enhanced Header with connection status
        HeaderCard(
            uiState = uiState,
            onReconnect = viewModel::reconnect,
            onClearOrders = viewModel::clearOrders
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Statistics Cards
//        StatsRow(uiState = uiState)

        Spacer(modifier = Modifier.height(16.dp))

        // Orders list or empty state
        if (uiState.orders.isEmpty()) {
            EmptyState(
                isConnected = uiState.isConnected,
                onReconnect = viewModel::reconnect
            )
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(
                    items = uiState.orders,
                    key = { _, order -> order.id }
                ) { index, order ->
                    ImprovedOrderCard(
                        order = order,
                        customer = uiState.getCustomerForOrder(order),
                        voucherInfo = uiState.getVoucherForOrder(order.id),
                        index = index
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsRow(uiState: OrderUiState) { //This Composable is not used locally
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Order count card
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = uiState.orders.size.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Đơn hàng",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Revenue card
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val totalRevenue = uiState.orders
                    .filter { it.trangThai == 3 }
                    .sumOf { it.tongTienSauGiam }

                Text(
                    text = if (totalRevenue > 0) totalRevenue.toVNDShort() else "0",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Doanh thu",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Active customers card
        if (uiState.khachHangInfo.isNotEmpty()) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.khachHangInfo.size.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Khách hàng",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(
    uiState: OrderUiState,
    onReconnect: () -> Unit,
    onClearOrders: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Theo dõi đơn hàng",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Cập nhật realtime",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (uiState.lastUpdated > 0) {
                    Text(
                        text = "Cập nhật lần cuối: ${uiState.lastUpdated.toTimeString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (uiState.isConnected) Color.Green.copy(alpha = pulseAlpha)
                            else Color.Red.copy(alpha = pulseAlpha)
                        )
                )
                IconButton(onClick = onReconnect) {
                    Icon(Icons.Default.Refresh, contentDescription = "Làm mới")
                }
                IconButton(onClick = onClearOrders) {
                    Icon(Icons.Default.Clear, contentDescription = "Xóa đơn hàng")
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    isConnected: Boolean,
    onReconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isConnected) "Không có đơn hàng nào" else "Không có kết nối",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (!isConnected) {
            Button(onClick = onReconnect) {
                Text("Thử kết nối lại")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImprovedOrderCard(
    order: HoaDonDetailResponse,
    customer: KhachHang?,
    voucherInfo: VoucherOrderUpdate?,
    index: Int
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "arrow rotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(android.graphics.Color.parseColor(order.getStatusColor()))
                .copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header với mã đơn hàng và trạng thái
            OrderHeader(order = order, rotation = rotation)

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Thông tin khách hàng
                    CustomerInfoCard(customer = customer, order = order)

                    // 2. Thông tin phiếu giảm giá (nếu có)
                    if (voucherInfo != null || order.hasVoucher()) {
                        VoucherInfoCard(voucherInfo = voucherInfo, order = order)
                    }

                    // 3. Danh sách sản phẩm
                    ProductListCard(products = order.sanPhamChiTiet)

                    // 4. Tổng tiền và thanh toán
                    PaymentSummaryCard(order = order)
                }
            }
        }
    }
}

@Composable
private fun OrderHeader(order: HoaDonDetailResponse, rotation: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = order.maHoaDon,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            StatusChip(status = order.trangThaiText)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = order.getFormattedDate(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = order.tongTienSauGiam.toVNDCurrency(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (order.hasDiscount()) {
                    Text(
                        text = "Tiết kiệm ${order.tienGiamGia.toVNDCurrency()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Toggle expand",
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation)
            )
        }
    }
}

// OrderScreen.kt - Enhanced CustomerInfoCard with debugging

@Composable
private fun CustomerInfoCard(customer: KhachHang?, order: HoaDonDetailResponse) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Customer",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Thông tin khách hàng",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Enhanced debug logging
            Log.d("CustomerInfoCard", "=== CUSTOMER CARD DEBUG ===")
            Log.d("CustomerInfoCard", "Order: ${order.maHoaDon}")
            Log.d("CustomerInfoCard", "Order customer name: '${order.tenKhachHang}'")
            Log.d("CustomerInfoCard", "Order customer phone: '${order.soDienThoaiKhachHang}'")
            Log.d("CustomerInfoCard", "Order customer email: '${order.emailKhachHang}'")

            if (customer != null) {
                Log.d("CustomerInfoCard", "Found customer: ID=${customer.id}, Name='${customer.ten}', Phone='${customer.soDienThoai}', Email='${customer.email}'")
            } else {
                Log.d("CustomerInfoCard", "NO CUSTOMER FOUND for order: ${order.maHoaDon}")
            }

            // Hiển thị thông tin khách hàng
            when {
                // Có thông tin customer đầy đủ từ WebSocket
                customer?.isValidForOrderDisplay() == true -> {
                    CustomerFullInfoNew(customer = customer)

                    // Debug info để kiểm tra
                    if (BuildConfig.DEBUG) {
                        Text(
                            text = "🔍 Debug: Found via WebSocket (ID: ${customer.id})",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Green,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Chỉ có thông tin cơ bản từ order
                order.hasCustomerInfo() -> {
                    CustomerBasicInfo(order = order)

                    // Debug info
                    if (BuildConfig.DEBUG) {
                        Text(
                            text = "🔍 Debug: Using order info only",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Khách lẻ
                else -> {
                    CustomerGuestInfo()

                    // Debug info
                    if (BuildConfig.DEBUG) {
                        Text(
                            text = "🔍 Debug: Guest customer",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Red,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerFullInfoNew(customer: KhachHang) {
    // Tên khách hàng
    InfoRow(
        label = "Tên khách hàng",
        value = customer.getDisplayName(),
        fontWeight = FontWeight.Medium
    )

    // Số điện thoại - ưu tiên từ trường trực tiếp
    val phoneNumber = customer.soDienThoai
    if (!phoneNumber.isNullOrEmpty()) {
        InfoRow(
            label = "SĐT",
            value = phoneNumber.toFormattedPhone()
        )
    }

    // Email - chỉ hiển thị nếu có và không có SĐT
    val email = customer.email
    if (!email.isNullOrEmpty() && phoneNumber.isNullOrEmpty()) {
        InfoRow(label = "Email", value = email)
    }

    // Status badge để biết nguồn dữ liệu
    Surface(
        color = Color.Green.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text(
            text = "✓ Dữ liệu realtime",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Green,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun CustomerBasicInfo(order: HoaDonDetailResponse) {
    // Tên từ order
    if (order.tenKhachHang.isNotEmpty() && order.tenKhachHang != "Khách lẻ") {
        InfoRow(
            label = "Tên khách hàng",
            value = order.tenKhachHang,
            fontWeight = FontWeight.Medium
        )
    }

    // SĐT từ order
    if (order.soDienThoaiKhachHang.isNotEmpty()) {
        InfoRow(
            label = "SĐT",
            value = order.soDienThoaiKhachHang.toFormattedPhone()
        )
    }

    // Email từ order (chỉ nếu không có SĐT)
    if (order.emailKhachHang.isNotEmpty() && order.soDienThoaiKhachHang.isEmpty()) {
        InfoRow(label = "Email", value = order.emailKhachHang)
    }

    // Địa chỉ từ order (chỉ nếu có)
    if (order.diaChiKhachHang.isNotEmpty()) {
        InfoRow(
            label = "Địa chỉ",
            value = order.diaChiKhachHang,
            multiline = true
        )
    }
}

@Composable
private fun CustomerGuestInfo() {
    Text(
        text = "Khách lẻ",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontStyle = FontStyle.Italic
    )
}

// Tối ưu: Helper extension cho HoaDonDetailResponse
fun HoaDonDetailResponse.hasCustomerInfo(): Boolean {
    return (tenKhachHang.isNotEmpty() && tenKhachHang != "Khách lẻ") ||
            soDienThoaiKhachHang.isNotEmpty() ||
            emailKhachHang.isNotEmpty()
}

@Composable
private fun VoucherInfoCard(voucherInfo: VoucherOrderUpdate?, order: HoaDonDetailResponse) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Phiếu giảm giá",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Ưu tiên thông tin từ voucherInfo, fallback về order
            val voucherCode = voucherInfo?.maPhieu?.takeIf { it.isNotEmpty() } ?: order.maPhieuGiamGia
            val voucherName = voucherInfo?.tenPhieu?.takeIf { it.isNotEmpty() } ?: ""
            val discountAmount = if (voucherInfo?.giaTriGiam != null && voucherInfo.giaTriGiam > 0) {
                voucherInfo.giaTriGiam.toLong()
            } else {
                order.tienGiamGia
            }

            InfoRow(label = "Mã phiếu", value = voucherCode, fontWeight = FontWeight.Bold)
            if (voucherName.isNotEmpty()) {
                InfoRow(label = "Tên phiếu", value = voucherName)
            }
            InfoRow(
                label = "Giá trị giảm",
                value = discountAmount.toVNDCurrency(),
                valueColor = Color.Red,
                fontWeight = FontWeight.Bold
            )

            if (voucherInfo?.trangThai == true) {
                Surface(
                    color = Color.Green,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Đã áp dụng",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductListCard(products: List<SanPhamChiTiet>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = "Products",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Sản phẩm (${products.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            products.forEach { product ->
                ProductItem(product = product)
                if (product != products.last()) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ProductItem(product: SanPhamChiTiet) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = product.tenSanPham,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (product.getFormattedSpecs().isNotEmpty()) {
                Text(
                    text = product.getFormattedSpecs(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SL: ${product.soLuong}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = product.giaBan.toVNDCurrency(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (product.imel.isNotEmpty()) {
                Text(
                    text = "IMEI: ${product.imel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Text(
            text = product.thanhTien.toVNDCurrency(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PaymentSummaryCard(order: HoaDonDetailResponse) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Tổng kết thanh toán",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tổng tiền gốc
            InfoRow(
                label = "Tổng tiền hàng",
                value = order.tongTien.toVNDCurrency(),
                valueColor = MaterialTheme.colorScheme.onPrimaryContainer
            )

            // Phí vận chuyển (nếu có)
            if (order.hasShippingFee()) {
                InfoRow(
                    label = "Phí vận chuyển",
                    value = order.phiVanChuyen.toVNDCurrency(),
                    valueColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Giảm giá (nếu có)
            if (order.hasDiscount()) {
                InfoRow(
                    label = "Giảm giá",
                    value = "-${order.tienGiamGia.toVNDCurrency()}",
                    valueColor = Color.Red,
                    fontWeight = FontWeight.Medium
                )
            }

            Divider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
            )

            // Tổng thanh toán
            InfoRow(
                label = "Tổng thanh toán",
                value = order.tongTienSauGiam.toVNDCurrency(),
                valueColor = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                labelStyle = MaterialTheme.typography.titleSmall,
                valueStyle = MaterialTheme.typography.titleMedium
            )

            // Thông tin thanh toán (nếu có)
            if (order.thanhToanInfo.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Phương thức thanh toán:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                order.thanhToanInfo.forEach { payment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = payment.phuongThuc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = payment.soTien.toVNDCurrency(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Ghi chú (nếu có)
            if (order.ghiChu.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ghi chú:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = order.ghiChu,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    labelStyle: TextStyle = MaterialTheme.typography.bodySmall,
    valueStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.Normal,
    multiline: Boolean = false
) {
    if (multiline) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = label,
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = valueStyle,
                color = valueColor,
                fontWeight = fontWeight
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = label,
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.4f)
            )
            Text(
                text = value,
                style = valueStyle,
                color = valueColor,
                fontWeight = fontWeight,
                modifier = Modifier.weight(0.6f),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (backgroundColor, textColor) = when (status.lowercase()) {
        "chờ xác nhận" -> Color(0xFFFFE0B2) to Color(0xFFE65100)
        "chờ giao hàng" -> Color(0xFFE1F5FE) to Color(0xFF01579B)
        "đang giao" -> Color(0xFFF3E5F5) to Color(0xFF4A148C)
        "hoàn thành" -> Color(0xFFE8F5E8) to Color(0xFF1B5E20)
        "đã hủy" -> Color(0xFFFFEBEE) to Color(0xFFC62828)
        else -> Color(0xFFF5F5F5) to Color(0xFF424242)
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium.copy(
                color = textColor,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}