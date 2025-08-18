package com.example.realtimeordermonitor.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.realtimeordermonitor.data.*
import com.example.realtimeordermonitor.viewmodel.OrderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(viewModel: OrderViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.orders.size) {
        if (uiState.orders.isNotEmpty() && listState.firstVisibleItemIndex <= 2) {
            listState.animateScrollToItem(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Compact Header
        CompactHeader(uiState, viewModel::reconnect, viewModel::clearOrders)

        Spacer(modifier = Modifier.height(12.dp))

        // Orders list
        if (uiState.orders.isEmpty()) {
            EmptyState(uiState.isConnected, viewModel::reconnect)
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(
                    items = uiState.orders,
                    key = { _, order -> order.id }
                ) { _, order ->
                    CompactOrderCard(order, uiState.getCustomerForOrder(order), uiState)
                }
            }
        }
    }
}

@Composable
private fun CompactHeader(
    uiState: OrderUiState,
    onReconnect: () -> Unit,
    onClearOrders: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (uiState.isConnected) Color(0xFF4CAF50).copy(alpha = pulseAlpha)
                            else Color(0xFFE53E3E).copy(alpha = pulseAlpha)
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        "Theo dõi đơn hàng",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.lastUpdated > 0) {
                        Text(
                            "${uiState.lastUpdated.toTimeString()}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row {
                IconButton(onClick = onReconnect, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClearOrders, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun CompactOrderCard(order: HoaDonDetailResponse, customer: KhachHang?, uiState: OrderUiState) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(250), label = "rotation"
    )

    val voucherInfo = uiState.getVoucherForOrder(order.id)
    val (_, discountAmount) = order.getEffectiveVoucherInfo(voucherInfo)
    val calculatedTotal = order.getCalculatedTotal()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        color = Color(android.graphics.Color.parseColor(order.getStatusColor())).copy(alpha = 0.06f),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Compact header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = order.maHoaDon.takeIf { it.isNotEmpty() }
                                ?: "HD${order.id.toString().padStart(6, '0')}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CompactStatusChip(order.trangThaiText)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = order.getFormattedDate(),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Expand",
                        modifier = Modifier.size(20.dp).rotate(rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded content - more compact
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Customer info - compact
                    CompactCustomerInfo(customer, order)

                    // Voucher info - compact
                    if (voucherInfo?.isApplied() == true || order.hasDiscount()) {
                        CompactVoucherInfo(voucherInfo, order)
                    }

                    // Products - compact
                    CompactProductsList(order.sanPhamChiTiet)

                    // Payment summary - compact
                    CompactPaymentSummary(order, voucherInfo)
                }
            }
        }
    }
}

@Composable
private fun MiniVoucherTag() {
    Surface(
        color = Color(0xFF4CAF50),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "PGG",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun CompactStatusChip(status: String) {
    val (bgColor, textColor) = when (status.lowercase()) {
        "chờ xác nhận" -> Color(0xFFFFE0B2) to Color(0xFFE65100)
        "chờ giao hàng" -> Color(0xFFE1F5FE) to Color(0xFF01579B)
        "đang giao" -> Color(0xFFF3E5F5) to Color(0xFF4A148C)
        "hoàn thành" -> Color(0xFFE8F5E8) to Color(0xFF1B5E20)
        "đã hủy" -> Color(0xFFFFEBEE) to Color(0xFFC62828)
        else -> Color(0xFFF5F5F5) to Color(0xFF424242)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = textColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun CompactCustomerInfo(customer: KhachHang?, order: HoaDonDetailResponse) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))

            when {
                customer?.isValidForDisplay() == true -> {
                    Text(
                        "${customer.getDisplayName()} • ${customer.soDienThoai?.toFormattedPhone() ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                order.hasCustomerInfo() -> {
                    Text(
                        "${order.tenKhachHang} • ${order.soDienThoaiKhachHang.toFormattedPhone()}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                else -> {
                    Text("Khách lẻ", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CompactVoucherInfo(voucherInfo: VoucherOrderUpdateResponse?, order: HoaDonDetailResponse) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFE8F5E8).copy(alpha = 0.8f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(6.dp))

                val voucherCode = if (voucherInfo?.isApplied() == true) {
                    voucherInfo.maPhieu
                } else {
                    order.maPhieuGiamGia.takeIf { it.isNotEmpty() } ?: "PGG"
                }

                Text(
                    voucherCode,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Medium
                )
            }

            val discountValue = if (voucherInfo?.isApplied() == true) {
                voucherInfo.getFormattedValue()
            } else {
                order.tienGiamGia.toVNDCurrency()
            }

            Text(
                "-$discountValue",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CompactProductsList(products: List<SanPhamChiTiet>) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Sản phẩm (${products.size})",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            products.take(3).forEach { product ->
                CompactProductItem(product)
            }

            if (products.size > 3) {
                Text(
                    "... và ${products.size - 3} sản phẩm khác",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CompactProductItem(product: SanPhamChiTiet) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                product.tenSanPham,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row {
                Text(
                    "SL: ${product.soLuong}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (product.getSpecs().isNotEmpty()) {
                    Text(
                        " • ${product.getSpecs()}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Text(
            text = product.thanhTien.toVNDCurrency(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun CompactPaymentSummary(order: HoaDonDetailResponse, voucherInfo: VoucherOrderUpdateResponse?) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            val calculatedTotal = order.getCalculatedTotal()
            val (_, discountAmount) = order.getEffectiveVoucherInfo(voucherInfo)
            val finalTotal = calculatedTotal - discountAmount

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tổng tiền",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    calculatedTotal.toVNDCurrency(),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (discountAmount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Giảm giá",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "-${discountAmount.toVNDCurrency()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE53E3E)
                    )
                }
            }

            Divider(
                modifier = Modifier.padding(vertical = 6.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Thành tiền",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    finalTotal.toVNDCurrency(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun EmptyState(isConnected: Boolean, onReconnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isConnected) "Chưa có đơn hàng mới" else "Mất kết nối",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!isConnected) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onReconnect,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Kết nối lại")
            }
        }
    }
}

// Extension function for time formatting
private fun Long.toTimeString(): String {
    val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return format.format(java.util.Date(this))
}