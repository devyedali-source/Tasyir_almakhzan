package com.elyedali.makhzan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elyedali.makhzan.data.Money
import com.elyedali.makhzan.data.Plans
import com.elyedali.makhzan.data.PurchaseItem
import com.elyedali.makhzan.data.Qty
import com.elyedali.makhzan.data.Shop
import com.elyedali.makhzan.data.ShopDao
import com.elyedali.makhzan.i18n.tr
import kotlinx.coroutines.launch

/** منتج مقروء ينتظر إدخال الكمية والأسعار */
private data class PendingScan(
    val barcode: String,
    val productId: Long?,
    val name: String,
    val buyPrice: Long?,
    val sellPrice: Long?,
    val currentStock: Int?,
)

@Composable
fun PurchaseScreen(dao: ShopDao, shopId: Long, onBack: () -> Unit) {
    val cart = remember { mutableStateListOf<PurchaseItem>() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingScan?>(null) }
    var limitMessage by remember { mutableStateOf<String?>(null) }
    var activationShop by remember { mutableStateOf<Shop?>(null) }
    var activationCount by remember { mutableStateOf(0) }

    fun toast(msg: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    fun onScanned(code: String) {
        if (pending != null || busy) return
        scope.launch {
            val inCart = cart.firstOrNull { it.barcode == code }
            val existing = dao.findByBarcode(shopId, code)
            when {
                inCart != null -> pending = PendingScan(
                    code, inCart.productId, inCart.name, inCart.buyPrice, inCart.sellPrice, existing?.qty
                )
                existing != null -> pending = PendingScan(
                    code, existing.id, existing.name, existing.buyPrice, existing.sellPrice, existing.qty
                )
                else -> {
                    // منتج جديد: نتحقق من حد المنتجات المسموح به لهذا المحل
                    val shop = dao.shopNow(shopId) ?: return@launch
                    val now = System.currentTimeMillis()
                    val newInCart = cart.count { it.productId == null }
                    val limit = Plans.limit(shop, now)
                    if (dao.productCount(shopId) + newInCart + 1 > limit) {
                        limitMessage = tr(
                            "وصل هذا المحل إلى حد %1\$s منتجات.\nلإضافة منتجات جديدة يجب تفعيل المحل أو ترقية الباقة.",
                            Plans.limitText(shop, now),
                        )
                    } else {
                        pending = PendingScan(code, null, "", null, null, null)
                    }
                }
            }
        }
    }

    val total = cart.sumOf { it.buyPrice * it.qty }

    Scaffold(
        topBar = { AppTopBar("شراء (إدخال بضاعة)", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 6.dp, shadowElevation = 6.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("مجموع الشراء", style = MaterialTheme.typography.bodySmall)
                        Text(
                            Money.format(total), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Button(
                        enabled = cart.isNotEmpty() && !busy,
                        onClick = {
                            busy = true
                            val items = cart.toList()
                            scope.launch {
                                try {
                                    dao.recordPurchase(shopId, items, System.currentTimeMillis())
                                    cart.clear()
                                    toast("تم تسجيل الشراء: ${Money.format(total)}")
                                } catch (e: Exception) {
                                    toast("تعذر تسجيل الشراء: ${e.message ?: ""}")
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) { Text("تأكيد الشراء") }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp)) {
            BarcodeScannerView(
                modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = 12.dp),
                paused = pending != null || busy || limitMessage != null,
                onCode = ::onScanned,
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("البضاعة المُدخلة (${cart.size})", style = MaterialTheme.typography.titleMedium)
                if (cart.isNotEmpty()) TextButton(onClick = { cart.clear() }) { Text("إفراغ") }
            }
            if (cart.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "امسح الكود بار للمنتج.\nإذا كان جديدًا سيُطلب منك اسمه وأسعاره.",
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(cart, key = { it.barcode }) { item ->
                        CartRow(
                            name = if (item.productId == null) "${item.name} (جديد)" else item.name,
                            subtitle = "شراء ${Money.format(item.buyPrice)} — بيع ${Money.format(item.sellPrice)}",
                            lineTotal = Money.format(item.buyPrice * item.qty),
                            qty = item.qty,
                            onPlus = { onScanned(item.barcode) },
                            onMinus = {
                                val i = cart.indexOfFirst { it.barcode == item.barcode }
                                if (i >= 0) {
                                    if (cart[i].qty <= 1) cart.removeAt(i) else cart[i] = cart[i].copy(qty = cart[i].qty - 1)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    pending?.let { p ->
        PurchaseItemDialog(
            scan = p,
            onDismiss = { pending = null },
            onConfirm = { name, buy, sell, qty ->
                pending = null
                val i = cart.indexOfFirst { it.barcode == p.barcode }
                if (i >= 0) {
                    cart[i] = cart[i].copy(name = name, buyPrice = buy, sellPrice = sell, qty = cart[i].qty + qty)
                } else {
                    cart.add(0, PurchaseItem(p.productId, p.barcode, name, buy, sell, qty))
                }
            },
        )
    }

    limitMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { limitMessage = null },
            title = { Text("حد المنتجات") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = {
                    limitMessage = null
                    scope.launch {
                        activationShop = dao.shopNow(shopId)
                        activationCount = dao.productCount(shopId)
                    }
                }) { Text("تفعيل المحل") }
            },
            dismissButton = { TextButton(onClick = { limitMessage = null }) { Text("إغلاق") } },
        )
    }
    activationShop?.let { sh ->
        ActivationDialog(dao, sh, activationCount, onDismiss = { activationShop = null })
    }
}

@Composable
private fun PurchaseItemDialog(
    scan: PendingScan,
    onDismiss: () -> Unit,
    onConfirm: (name: String, buy: Long, sell: Long, qty: Int) -> Unit,
) {
    val isNew = scan.productId == null && scan.name.isEmpty()
    var name by remember { mutableStateOf(scan.name) }
    var buy by remember { mutableStateOf(scan.buyPrice?.let { Money.toInput(it) } ?: "") }
    var sell by remember { mutableStateOf(scan.sellPrice?.let { Money.toInput(it) } ?: "") }
    var qty by remember { mutableStateOf("") }

    val buyV = Money.parse(buy)
    val sellV = Money.parse(sell)
    val qtyV = Qty.parse(qty)
    val valid = name.isNotBlank() && buyV != null && sellV != null && qtyV != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "منتج جديد" else scan.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الكود: ${scan.barcode}", style = MaterialTheme.typography.bodySmall)
                scan.currentStock?.let {
                    Text("الموجود في المخزون: $it", style = MaterialTheme.typography.bodySmall)
                }
                if (isNew) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("اسم المنتج") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                MoneyField(buy, { buy = it }, "سعر الشراء للوحدة")
                MoneyField(sell, { sell = it }, "سعر البيع للوحدة")
                NumberField(qty, { qty = it }, "الكمية المشتراة")
                if (buyV != null && sellV != null && sellV < buyV) {
                    ErrorText("تنبيه: سعر البيع أقل من سعر الشراء")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(name.trim(), buyV!!, sellV!!, qtyV!!) }) {
                Text("إضافة")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
