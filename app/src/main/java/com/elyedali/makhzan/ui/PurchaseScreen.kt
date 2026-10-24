package com.elyedali.makhzan.ui

import androidx.activity.compose.BackHandler
import com.elyedali.makhzan.data.TrustedClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    val currentStock: Long?,
    val unit: String = "",
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

    var confirmLeave by remember { mutableStateOf(false) }
    fun tryBack() { if (cart.isNotEmpty()) confirmLeave = true else onBack() }
    BackHandler(enabled = cart.isNotEmpty()) { confirmLeave = true }

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
                    code, inCart.productId, inCart.name, inCart.buyPrice, inCart.sellPrice, existing?.qty, inCart.unit,
                )
                existing != null -> pending = PendingScan(
                    code, existing.id, existing.name, existing.buyPrice, existing.sellPrice, existing.qty, existing.unit,
                )
                else -> {
                    // منتج جديد: نتحقق من حد المنتجات المسموح به لهذا المحل
                    val shop = dao.shopNow(shopId) ?: return@launch
                    val now = TrustedClock.now()
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

    val total = cart.sumOf { Qty.amount(it.buyPrice, it.qty) }
    var showSupplierPicker by remember { mutableStateOf(false) }

    /** supplierId = null: شراء نقدي من الصندوق؛ غير ذلك: بالدَّين على المحل لهذا المورد */
    fun confirmPurchase(supplierId: Long?) {
        busy = true
        val items = cart.toList()
        val sum = total
        scope.launch {
            try {
                dao.recordPurchase(shopId, items, TrustedClock.now(), supplierId)
                cart.clear()
                toast(
                    if (supplierId == null) tr("تم تسجيل الشراء: %1\$s", Money.format(sum))
                    else tr("تم تسجيل الشراء بالدَّين: %1\$s", Money.format(sum))
                )
            } catch (e: Exception) {
                toast(tr("تعذر تسجيل الشراء: %1\$s", e.message ?: ""))
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = { AppTopBar(tr("شراء"), ::tryBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 6.dp, shadowElevation = 6.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(tr("مجموع الشراء"), style = MaterialTheme.typography.bodySmall)
                        Text(
                            Money.format(total), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // الشراء بالدَّين من مورد: لا يخرج من الصندوق حتى يُدفع له
                    OutlinedButton(
                        enabled = cart.isNotEmpty() && !busy,
                        modifier = Modifier.height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        onClick = { showSupplierPicker = true },
                    ) { Text(tr("دَين")) }
                    Button(
                        enabled = cart.isNotEmpty() && !busy,
                        modifier = Modifier.height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        onClick = { confirmPurchase(null) },
                    ) { Text(tr("تأكيد الشراء"), style = MaterialTheme.typography.titleMedium) }
                    }
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
                Text(tr("البضاعة المُدخلة (%1\$s)", cart.size), style = MaterialTheme.typography.titleMedium)
                Row {
                    // سلعة لا تحمل كودًا (خبز، سلع سائبة…): يُنشأ لها رمز داخلي وتُباع من زر «بحث» في شاشة البيع
                    TextButton(onClick = { onScanned("IN-" + java.lang.Long.toString(System.currentTimeMillis(), 36).uppercase()) }) { Text(tr("بلا كود")) }
                    if (cart.isNotEmpty()) TextButton(onClick = { cart.clear() }) { Text(tr("إفراغ")) }
                }
            }
            if (cart.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        tr("امسح الكود بار للمنتج.\nإذا كان جديدًا سيُطلب منك اسمه وأسعاره."),
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
                            name = if (item.productId == null) tr("%1\$s (جديد)", item.name) else item.name,
                            subtitle = tr("شراء %1\$s — بيع %2\$s", Money.format(item.buyPrice), Money.format(item.sellPrice)) +
                                if (Qty.isWeighed(item.unit)) " / " + Qty.unitLabel(item.unit) else "",
                            lineTotal = Money.format(Qty.amount(item.buyPrice, item.qty)),
                            qty = Qty.format(item.qty, item.unit),
                            onPlus = { onScanned(item.barcode) },
                            onMinus = {
                                val i = cart.indexOfFirst { it.barcode == item.barcode }
                                if (i >= 0) {
                                    if (cart[i].qty <= Qty.ONE) cart.removeAt(i) else cart[i] = cart[i].copy(qty = cart[i].qty - Qty.ONE)
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
            onConfirm = { name, buy, sell, qty, unit ->
                pending = null
                val i = cart.indexOfFirst { it.barcode == p.barcode }
                if (i >= 0) {
                    cart[i] = cart[i].copy(name = name, buyPrice = buy, sellPrice = sell, qty = cart[i].qty + qty, unit = unit)
                } else {
                    cart.add(0, PurchaseItem(p.productId, p.barcode, name, buy, sell, qty, unit))
                }
            },
        )
    }

    limitMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { limitMessage = null },
            title = { Text(tr("حد المنتجات")) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = {
                    limitMessage = null
                    scope.launch {
                        activationShop = dao.shopNow(shopId)
                        activationCount = dao.productCount(shopId)
                    }
                }) { Text(tr("تفعيل المحل")) }
            },
            dismissButton = { TextButton(onClick = { limitMessage = null }) { Text(tr("إغلاق")) } },
        )
    }
    if (showSupplierPicker) {
        SupplierPickerDialog(
            dao, shopId, total,
            onDismiss = { showSupplierPicker = false },
            onPick = { id -> showSupplierPicker = false; confirmPurchase(id) },
        )
    }
    activationShop?.let { sh ->
        ActivationDialog(dao, sh, activationCount, onDismiss = { activationShop = null })
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(tr("الخروج دون تأكيد؟")) },
            text = { Text(tr("في السلة منتجات لم تُؤكَّد بعد. إذا خرجت ستُلغى.")) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }) {
                    Text(tr("خروج"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(tr("البقاء")) } },
        )
    }
}

@Composable
private fun PurchaseItemDialog(
    scan: PendingScan,
    onDismiss: () -> Unit,
    onConfirm: (name: String, buy: Long, sell: Long, qty: Long, unit: String) -> Unit,
) {
    val isNew = scan.productId == null && scan.name.isEmpty()
    var unit by remember { mutableStateOf(scan.unit) }
    val weighed = Qty.isWeighed(unit)
    var name by remember { mutableStateOf(scan.name) }
    var buy by remember { mutableStateOf(scan.buyPrice?.let { Money.toInput(it) } ?: "") }
    var sell by remember { mutableStateOf(scan.sellPrice?.let { Money.toInput(it) } ?: "") }
    var qty by remember { mutableStateOf("") }

    val buyV = Money.parse(buy)
    val sellV = Money.parse(sell)
    val qtyV = Qty.parse(qty, unit)
    val valid = name.isNotBlank() && buyV != null && sellV != null && qtyV != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) tr("منتج جديد") else scan.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("الكود: %1\$s", scan.barcode), style = MaterialTheme.typography.bodySmall)
                scan.currentStock?.let {
                    Text(tr("الموجود في المخزون: %1\$s", Qty.format(it, unit)), style = MaterialTheme.typography.bodySmall)
                }
                if (isNew) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text(tr("اسم المنتج")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    // طريقة البيع: بالقطعة أو بالوزن (أرز، سكر، زيت سائب…)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Qty.units.forEach { u ->
                            FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(Qty.unitName(u)) })
                        }
                    }
                }
                val per = if (weighed) tr("للـ%1\$s", Qty.unitLabel(unit)) else tr("للوحدة")
                MoneyField(buy, { buy = it }, tr("سعر الشراء %1\$s", per))
                MoneyField(sell, { sell = it }, tr("سعر البيع %1\$s", per))
                if (weighed) MoneyField(qty, { qty = it }, tr("الكمية المشتراة (%1\$s)", Qty.unitLabel(unit)))
                else NumberField(qty, { qty = it }, tr("الكمية المشتراة"))
                if (qty.isNotBlank() && qtyV == null) ErrorText(if (weighed) tr("كمية غير صحيحة") else tr("اكتب عددًا صحيحًا من القطع"))
                if (buyV != null && sellV != null && sellV < buyV) {
                    ErrorText(tr("تنبيه: سعر البيع أقل من سعر الشراء"))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(name.trim(), buyV!!, sellV!!, qtyV!!, unit) }) {
                Text(tr("إضافة"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء")) } },
    )
}
