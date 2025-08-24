package com.example.realtimeordermonitor.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.realtimeordermonitor.R
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

    Column(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFFF8FAFC))) {
        AppHeader(uiState, viewModel::reconnect, viewModel::clearOrders)

        if (uiState.orders.isEmpty()) {
            EmptyState(uiState.isConnected, viewModel::reconnect)
        } else {
            ExpandedOrderCards(uiState, listState)
        }

        AppFooter()
    }
}

@Composable
private fun AppHeader(
    uiState: OrderUiState,
    onReconnect: () -> Unit,
    onClearOrders: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF16A34A),
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "Logo",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Mobile World",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (uiState.isConnected) Color(0xFF34D399).copy(alpha = pulseAlpha)
                                else Color(0xFFEF4444).copy(alpha = pulseAlpha)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (uiState.isConnected) "Đang kết nối" else "Mất kết nối",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.lastUpdated > 0) {
                    Text(
                        "Cập nhật: ${uiState.lastUpdated.toTimeString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Row {
                    IconButton(
                        onClick = onReconnect,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Kết nối lại",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onClearOrders,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Xóa đơn",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedOrderCards(uiState: OrderUiState, listState: LazyListState) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(
            items = uiState.orders,
            key = { _, order -> order.id }
        ) { _, order ->
            ExpandedOrderCard(order, uiState.getCustomerForOrder(order), uiState)
        }
    }
}

@Composable
private fun ExpandedOrderCard(
    order: HoaDonDetailResponse,
    customer: KhachHang?,
    uiState: OrderUiState
) {
    val voucherInfo = uiState.getVoucherForOrder(order.id)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = order.maHoaDon.takeIf { it.isNotEmpty() }
                                ?: "HD${order.id.toString().padStart(6, '0')}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A)
                        )

                        if (voucherInfo?.isApplied() == true) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFF34D399),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "PROMO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OrderStatusChip(order.trangThaiText)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = order.getFormattedDate(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Divider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 1.dp,
                color = Color(0xFFF1F5F9)
            )

            CustomerInfoSection(customer, order)

            Spacer(modifier = Modifier.height(12.dp))

            if (voucherInfo?.isApplied() == true) {
                VoucherInfoSection(voucherInfo, order)
                Spacer(modifier = Modifier.height(12.dp))
            }

            ProductsSection(order.sanPhamChiTiet)

            Spacer(modifier = Modifier.height(12.dp))

            PaymentSummarySection(order, voucherInfo)
        }
    }
}

@Composable
private fun OrderStatusChip(status: String) {
    val (bgColor, textColor) = when (status.lowercase()) {
        "chờ xác nhận" -> Color(0xFFFFF7ED) to Color(0xFFEA580C)
        "chờ giao hàng" -> Color(0xFFE0F2FE) to Color(0xFF0EA5E9)
        "đang giao" -> Color(0xFFF3E8FF) to Color(0xFF9333EA)
        "hoàn thành" -> Color(0xFFF0FDF4) to Color(0xFF16A34A)
        "đã hủy" -> Color(0xFFFEF2F2) to Color(0xFFDC2626)
        else -> Color(0xFFF8FAFC) to Color(0xFF64748B)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CustomerInfoSection(customer: KhachHang?, order: HoaDonDetailResponse) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            tint = Color(0xFF64748B),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))

        when {
            customer?.isValidForDisplay() == true -> {
                Text(
                    "${customer.getDisplayName()} • ${customer.soDienThoai?.toFormattedPhone() ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            order.hasCustomerInfo() -> {
                Text(
                    "${order.tenKhachHang} • ${order.soDienThoaiKhachHang.toFormattedPhone()}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            else -> {
                Text("Khách lẻ", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun VoucherInfoSection(
    voucherInfo: VoucherOrderUpdateResponse?,
    order: HoaDonDetailResponse
) {
    if (voucherInfo?.isApplied() != true) return

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF0FDF4),
        border = BorderStroke(1.dp, Color(0xFFBBF7D0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        voucherInfo.maPhieu,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF15803D),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Voucher giảm giá",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF15803D)
                    )
                }
            }

            Text(
                "-${voucherInfo.getFormattedValue()}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Red,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ProductsSection(products: List<SanPhamChiTiet>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Sản phẩm (${products.size})",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF374151)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            products.forEach { product ->
                ProductItem(product)
            }
        }
    }
}

@Composable
private fun ProductItem(product: SanPhamChiTiet) {
    // Log image URL for debugging
    LaunchedEffect(product.anhSanPham) {
        android.util.Log.d("ProductImage", "📸 Product: ${product.tenSanPham}")
        android.util.Log.d("ProductImage", "🔗 Image URL: '${product.anhSanPham}'")
        android.util.Log.d("ProductImage", "📏 URL length: ${product.anhSanPham.length}")
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Product Image
        if (product.anhSanPham.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(product.anhSanPham)
                    .crossfade(true)
                    .listener(
                        onStart = {
                            android.util.Log.d("ProductImage", "🚀 Loading image: ${product.anhSanPham}")
                        },
                        onSuccess = { _, _ ->
                            android.util.Log.d("ProductImage", "✅ Image loaded successfully: ${product.tenSanPham}")
                        },
                        onError = { _, error ->
                            android.util.Log.e("ProductImage", "❌ Image load error for ${product.tenSanPham}: ${error.throwable.message}")
                        }
                    )
                    .build(),
                contentDescription = "Ảnh ${product.tenSanPham}",
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF8FAFC)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_placeholder_image),
                error = painterResource(id = R.drawable.ic_placeholder_image)
            )
        } else {
            android.util.Log.w("ProductImage", "⚠️ No image URL for product: ${product.tenSanPham}")
            // Placeholder when no image
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
            }
        }

        // Product Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                product.tenSanPham,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SL: ${product.soLuong}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (product.getSpecs().isNotEmpty()) {
                    Text(
                        " • ${product.getSpecs()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Price
        Text(
            text = product.getActualThanhTien().toVNDCurrency(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
    }
}

@Composable
private fun PaymentSummarySection(
    order: HoaDonDetailResponse,
    voucherInfo: VoucherOrderUpdateResponse?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val calculatedTotal = order.getCalculatedTotal()
        val discountAmount = order.getEffectiveDiscountAmount(voucherInfo)
        val finalTotal = calculatedTotal - discountAmount

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Tổng tiền hàng",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF64748B)
            )
            Text(
                calculatedTotal.toVNDCurrency(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        if (voucherInfo?.isApplied() == true && discountAmount > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Giảm giá voucher",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF64748B)
                    )
                }
                Text(
                    "-${discountAmount.toVNDCurrency()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Divider(
            thickness = 1.dp,
            color = Color(0xFFE5E7EB)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Thành tiền",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937)
            )
            Text(
                maxOf(0L, finalTotal).toVNDCurrency(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF16A34A)
            )
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
            text = if (isConnected) "Chưa có đơn hàng mới" else "Mất kết nối máy chủ",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF6B7280)
        )
        if (!isConnected) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onReconnect,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF16A34A)
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Kết nối lại")
            }
        }
    }
}

@Composable
private fun AppFooter() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF16A34A)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "RealTime Order Monitor v1.0",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                "© 2023 - Hệ thống quản lý đơn hàng",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

private fun Long.toTimeString(): String {
    val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return format.format(java.util.Date(this))
}