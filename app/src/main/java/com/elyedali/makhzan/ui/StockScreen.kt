package com.elyedali.makhzan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Inventory2
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.input.KeyboardType
import com.elyedali.makhzan.data.Qty
import com.elyedali.makhzan.data.TrustedClock
import com.elyedali.makhzan.invoice.ReportPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elyedali.makhzan.ads.AdBanner
import com.elyedali.makhzan.data.Dates
import com.elyedali.makhzan.data.Money
import com.elyedali.makhzan.data.OpType
import com.elyedali.makhzan.data.ShopDao
import kotlinx.coroutines.launch
import com.elyedali.makhzan.i18n.tr

@Composable
fun StockScreen(
    dao: ShopDao, shopId: Long, onBack: () -> Unit, onOpen: (Long) -> Unit, onStocktake: () -> Unit,
    onOpeningStock: () -> Unit,
) {
    val products by dao.products(shopId).collectAsState(initial = emptyList())
    val value by dao.stockValue(shopId).collectAsState(initial = 0L)
    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) products else products.filter {
        it.name.contains(query.trim(), ignoreCase = true) || it.barcode.contains(query.trim())
    }

    Scaffold(
        topBar = {
            AppTopBar(tr("المخزون"), onBack) {
                TextButton(onClick = onStocktake) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color.White)
                    Text(" " + tr("الجرد"), color = Color.White)
                }
            }
        },
        bottomBar = { AdBanner(dao) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(tr("عدد المنتجات"), style = MaterialTheme.typography.bodySmall)
                            Text("${products.size}", fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(tr("قيمة المخزون (بسعر الشراء)"), style = MaterialTheme.typography.bodySmall)
                            Text(Money.format(value), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                // البضاعة التي كانت عند التاجر قبل التطبيق: تدخل المخزون دون أن تخرج من الصندوق
                OutlinedButton(onClick = onOpeningStock, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("إدخال المخزون الموجود عندي (دون شراء)"))
                }
            }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text(tr("بحث بالاسم")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (products.isEmpty()) {
                item { Text(tr("لا توجد منتجات بعد. أدخل البضاعة الموجودة عندك من الزر أعلاه، والمشتريات الجديدة من شاشة الشراء.")) }
            }
            items(shown, key = { it.id }) { p ->
                Card(onClick = { onOpen(p.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                tr("سعر البيع: %1\$s", Money.format(p.sellPrice)) +
                                    if (Qty.isWeighed(p.unit)) " / " + Qty.unitLabel(p.unit) else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                Qty.plain(p.qty), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                color = when {
                                    p.qty <= 0 -> Color(0xFFC62828)
                                    p.qty <= ReportPdf.RESTOCK_LEVEL * Qty.ONE -> Color(0xFFB45309)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(if (Qty.isWeighed(p.unit)) Qty.unitLabel(p.unit) else tr("الكمية"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductDetailScreen(dao: ShopDao, productId: Long, onBack: () -> Unit) {
    val product by dao.product(productId).collectAsState(initial = null)
    val moves by dao.movements(productId).collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { AppTopBar(product?.name ?: "", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            product?.let { p ->
                val per = if (Qty.isWeighed(p.unit)) " / " + Qty.unitLabel(p.unit) else ""
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tr("الكود: %1\$s", p.barcode))
                            Text(tr("الكمية في المخزون: %1\$s", Qty.format(p.qty, p.unit)), fontWeight = FontWeight.Bold)
                            Text(tr("سعر الشراء: %1\$s", Money.format(p.buyPrice)) + per)
                            Text(tr("سعر البيع: %1\$s", Money.format(p.sellPrice)) + per)
                            Text(tr("طريقة البيع: %1\$s", Qty.unitName(p.unit)), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { editing = true }) { Text(tr("تعديل")) }
                                OutlinedButton(enabled = p.qty > 0, onClick = { adjusting = true }) { Text(tr("تالف أو ضائع")) }
                            }
                            if (p.qty == 0L) {
                                TextButton(onClick = { confirmDelete = true }) {
                                    Text(tr("حذف المنتج"), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            item { Text(tr("حركة المخزون"), style = MaterialTheme.typography.titleMedium) }
            if (moves.isEmpty()) item { Text(tr("لا توجد حركات")) }
            items(moves, key = { it.line.id }) { m ->
                val q = when (m.type) {
                    OpType.PURCHASE, OpType.RETURN, OpType.OPENING -> m.line.qty
                    OpType.ADJUST -> m.line.qty
                    else -> -m.line.qty
                }
                val isIn = q > 0
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (m.type) {
                                    OpType.PURCHASE -> tr("دخول (شراء)")
                                    OpType.OPENING -> tr("دخول (مخزون افتتاحي)")
                                    OpType.RETURN -> tr("دخول (مرتجع)")
                                    OpType.ADJUST -> if (isIn) tr("زيادة (تسوية)") else tr("نقص (تسوية)")
                                    else -> tr("خروج (بيع)")
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                tr("%1\$s — %2\$s للوحدة", Dates.format(m.time), Money.format(m.line.unitPrice)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            (if (isIn) "+" else "−") + Qty.plain(kotlin.math.abs(q)),
                            color = if (isIn) Color(0xFF0B6E4F) else Color(0xFFC62828),
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }

    val p = product
    if (confirmDelete && p != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("حذف المنتج")) },
            text = { Text(tr("سيُحذف «%1\$s» من قائمة المنتجات. سجل عملياته السابقة يبقى كما هو.", p.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { if (dao.deleteEmptyProduct(p.id) > 0) onBack() }
                }) { Text(tr("حذف"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("إلغاء")) } },
        )
    }
    if (editing && p != null) {
        var name by remember { mutableStateOf(p.name) }
        var sell by remember { mutableStateOf(Money.toInput(p.sellPrice)) }
        var unit by remember { mutableStateOf(p.unit) }
        val sellV = Money.parse(sell)
        // من الوزن إلى القطعة: الكمية يجب أن تكون عددًا صحيحًا
        val unitOk = Qty.isWeighed(unit) || p.qty % Qty.ONE == 0L
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(tr("تعديل المنتج")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text(tr("اسم المنتج")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    MoneyField(sell, { sell = it }, tr("سعر البيع للوحدة"))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Qty.units.forEach { u ->
                            FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(Qty.unitName(u)) })
                        }
                    }
                    if (!unitOk) ErrorText(tr("الكمية الحالية فيها كسور، فلا يمكن جعل المنتج بالقطعة"))
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && sellV != null && unitOk, onClick = {
                    editing = false
                    scope.launch { dao.updateProductInfo(p.id, name.trim(), sellV!!, unit) }
                }) { Text(tr("حفظ")) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(tr("إلغاء")) } },
        )
    }
    if (adjusting && p != null) {
        val reasons = listOf(tr("تالف"), tr("منتهي الصلاحية"), tr("ضائع"), tr("استهلاك شخصي"))
        var reason by remember { mutableStateOf(reasons[0]) }
        var qtyText by remember { mutableStateOf("") }
        val q = Qty.parse(qtyText, p.unit)
        val tooMuch = q != null && q > p.qty
        AlertDialog(
            onDismissRequest = { adjusting = false },
            title = { Text(tr("إخراج سلعة من المخزون")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("تُنقص الكمية دون أن تمسّ الصندوق، وتُحسب قيمتها بسعر الشراء خسارة في التقارير."), style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        reasons.forEach { r -> FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(r) }) }
                    }
                    MoneyField(qtyText, { qtyText = it }, tr("الكمية (%1\$s)", Qty.unitLabel(p.unit)))
                    if (q != null && !tooMuch) Text(tr("الخسارة: %1\$s", Money.format(Qty.amount(p.buyPrice, q))), fontWeight = FontWeight.SemiBold)
                    if (tooMuch) ErrorText(tr("الكمية المتوفرة من «%1\$s» هي %2\$s فقط", p.name, Qty.format(p.qty, p.unit)))
                }
            },
            confirmButton = {
                TextButton(enabled = q != null && !tooMuch, onClick = {
                    adjusting = false
                    scope.launch {
                        try {
                            dao.recordAdjustment(p.shopId, mapOf(p.id to -q!!), reason, TrustedClock.now())
                            snackbar.showSnackbar(tr("تم التسجيل"))
                        } catch (e: IllegalStateException) {
                            snackbar.showSnackbar(e.message ?: "")
                        }
                    }
                }) { Text(tr("حفظ")) }
            },
            dismissButton = { TextButton(onClick = { adjusting = false }) { Text(tr("إلغاء")) } },
        )
    }
}

/**
 * الجرد: يكتب التاجر العدد الفعلي لكل منتج، فيُسجَّل الفرق تسويةً واحدة
 * (النقص خسارة بسعر الشراء، والزيادة تُضاف للمخزون).
 */
@Composable
fun StocktakeScreen(dao: ShopDao, shopId: Long, onBack: () -> Unit) {
    val products by dao.products(shopId).collectAsState(initial = emptyList())
    val counted = remember { mutableStateMapOf<Long, String>() }
    var query by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val byId = products.associateBy { it.id }
    val deltas: Map<Long, Long> = counted.mapNotNull { (id, text) ->
        val p = byId[id] ?: return@mapNotNull null
        val c = Qty.parse(text, p.unit, allowZero = true) ?: return@mapNotNull null
        id to (c - p.qty)
    }.toMap().filterValues { it != 0L }
    val invalid = counted.count { (id, text) -> text.isNotBlank() && byId[id]?.let { Qty.parse(text, it.unit, allowZero = true) } == null }
    val lossValue = -deltas.entries.sumOf { (id, d) -> Qty.amount(byId[id]?.buyPrice ?: 0L, d) }
    val shown = if (query.isBlank()) products else products.filter {
        it.name.contains(query.trim(), ignoreCase = true) || it.barcode.contains(query.trim())
    }
    val dirty = counted.values.any { it.isNotBlank() }
    BackHandler(enabled = dirty) { confirmLeave = true }

    Scaffold(
        topBar = { AppTopBar(tr("الجرد"), { if (dirty) { confirmLeave = true } else onBack() }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 6.dp, shadowElevation = 6.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("فروق: %1\$s منتج", deltas.size), style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (lossValue >= 0) tr("نقص: %1\$s", Money.format(lossValue)) else tr("زيادة: %1\$s", Money.format(-lossValue)),
                            fontWeight = FontWeight.Bold,
                            color = if (lossValue > 0) Color(0xFFC62828) else Color(0xFF0B6E4F),
                        )
                    }
                    Button(enabled = deltas.isNotEmpty() && invalid == 0 && !busy, onClick = { confirm = true }) { Text(tr("حفظ الجرد")) }
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    tr("اكتب العدد الموجود فعلًا على الرفوف. اترك الخانة فارغة للمنتجات التي لم تعدّها."),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text(tr("بحث بالاسم")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            items(shown, key = { it.id }) { p ->
                val text = counted[p.id] ?: ""
                val c = Qty.parse(text, p.unit, allowZero = true)
                val d = c?.let { it - p.qty }
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall)
                            Text(tr("في التطبيق: %1\$s", Qty.format(p.qty, p.unit)), style = MaterialTheme.typography.bodySmall)
                            if (d != null && d != 0L) {
                                Text(
                                    (if (d > 0) "+" else "−") + Qty.format(kotlin.math.abs(d), p.unit),
                                    color = if (d > 0) Color(0xFF0B6E4F) else Color(0xFFC62828), fontWeight = FontWeight.Bold,
                                )
                            } else if (d == 0L) {
                                Text(tr("مطابق"), color = Color(0xFF0B6E4F), style = MaterialTheme.typography.bodySmall)
                            } else if (text.isNotBlank()) {
                                ErrorText(tr("كمية غير صحيحة"))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = text, onValueChange = { counted[p.id] = it }, singleLine = true,
                            label = { Text(tr("الفعلي")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(110.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(tr("حفظ الجرد")) },
            text = {
                Text(
                    tr("ستُصحَّح كميات %1\$s منتج لتطابق العدد الفعلي.", deltas.size) + "\n" +
                        (if (lossValue >= 0) tr("قيمة النقص بسعر الشراء: %1\$s", Money.format(lossValue))
                        else tr("قيمة الزيادة بسعر الشراء: %1\$s", Money.format(-lossValue)))
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    busy = true
                    val d = deltas
                    scope.launch {
                        try {
                            dao.recordAdjustment(shopId, d, tr("جرد"), TrustedClock.now())
                            counted.clear()
                            snackbar.showSnackbar(tr("تم حفظ الجرد"))
                        } catch (e: IllegalStateException) {
                            snackbar.showSnackbar(e.message ?: "")
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(tr("حفظ")) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("إلغاء")) } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(tr("الخروج دون حفظ؟")) },
            text = { Text(tr("الأعداد التي كتبتها لم تُحفظ بعد.")) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }) { Text(tr("خروج"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(tr("البقاء")) } },
        )
    }
}
