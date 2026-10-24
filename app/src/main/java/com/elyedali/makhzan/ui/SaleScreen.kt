package com.elyedali.makhzan.ui

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import com.elyedali.makhzan.data.Qty
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import com.elyedali.makhzan.data.TrustedClock
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
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
import com.elyedali.makhzan.data.Product
import com.elyedali.makhzan.data.SaleItem
import com.elyedali.makhzan.data.ShopDao
import kotlinx.coroutines.launch
import com.elyedali.makhzan.i18n.tr

/** سطر في سلة البيع: الكمية بالألف، والسعر قابل للتعديل (المساومة) دون تغيير سعر المنتج الأصلي */
private data class SaleCartLine(val product: Product, val qty: Long, val price: Long) {
    val amount: Long get() = Qty.amount(price, qty)
}

/** المنتج الذي يُكتب له الكمية/السعر الآن (qty = null: منتج جديد في السلة) */
private data class LineEdit(val product: Product, val qty: Long?, val price: Long)

@Composable
fun SaleScreen(
    dao: ShopDao, shopId: Long, onBack: () -> Unit, onInvoice: (Long) -> Unit,
    /** العامل البائع في وضع البائع (null = صاحب المحل) */
    workerId: Long? = null,
    allowCredit: Boolean = true,
    /** السماح بتغيير السعر والتخفيض */
    allowDiscount: Boolean = true,
) {
    val cart = remember { mutableStateListOf<SaleCartLine>() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    // العامل لا يرى أسعار الشراء
    val showCost = workerId == null

    var confirmLeave by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<LineEdit?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showCreditPicker by remember { mutableStateOf(false) }
    var showDiscount by remember { mutableStateOf(false) }
    var discount by remember { mutableStateOf(0L) }
    fun tryBack() { if (cart.isNotEmpty()) confirmLeave = true else onBack() }
    BackHandler(enabled = cart.isNotEmpty()) { confirmLeave = true }

    fun toast(msg: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    fun setLine(product: Product, qty: Long, price: Long) {
        val i = cart.indexOfFirst { it.product.id == product.id }
        when {
            qty <= 0 -> { if (i >= 0) cart.removeAt(i); if (cart.isEmpty()) discount = 0L }
            qty > product.qty -> toast(tr("الكمية المتوفرة من «%1\$s» هي %2\$s فقط", product.name, Qty.format(product.qty, product.unit)))
            i >= 0 -> cart[i] = SaleCartLine(product, qty, price)
            else -> cart.add(0, SaleCartLine(product, qty, price))
        }
    }

    /** زيادة أو إنقاص قطعة؛ المنتج الموزون يُفتح له مربع الكمية عند إضافته أول مرة */
    fun changeQty(product: Product, delta: Long) {
        val line = cart.firstOrNull { it.product.id == product.id }
        if (line == null && Qty.isWeighed(product.unit) && delta > 0) {
            if (product.qty <= 0) toast(tr("نفد «%1\$s» من المخزون", product.name))
            else edit = LineEdit(product, null, product.sellPrice)
            return
        }
        setLine(product, (line?.qty ?: 0L) + delta, line?.price ?: product.sellPrice)
    }

    val gross = cart.sumOf { it.amount }
    val appliedDiscount = discount.coerceIn(0L, gross)
    val total = gross - appliedDiscount

    /** customerId = null: بيع نقدي؛ غير ذلك: بيع بالدَّين على هذا الزبون */
    fun confirmSale(customerId: Long?) {
        busy = true
        val saleTotal = total
        val disc = appliedDiscount
        val items = cart.map {
            SaleItem(
                productId = it.product.id, name = it.product.name,
                barcode = it.product.barcode, qty = it.qty,
                unitPrice = it.price, unitCost = it.product.buyPrice,
            )
        }
        scope.launch {
            try {
                val opId = dao.recordSale(shopId, items, TrustedClock.now(), customerId, workerId, disc)
                cart.clear()
                discount = 0L
                busy = false
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar(
                    message = if (customerId == null) tr("تم تسجيل البيع: %1\$s", Money.format(saleTotal))
                    else tr("تم تسجيل البيع بالدَّين: %1\$s", Money.format(saleTotal)),
                    actionLabel = tr("الفاتورة"),
                    duration = SnackbarDuration.Long,
                )
                if (r == SnackbarResult.ActionPerformed) onInvoice(opId)
            } catch (e: IllegalStateException) {
                toast(e.message ?: tr("تعذر تسجيل البيع"))
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = { AppTopBar(tr("بيع"), ::tryBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 6.dp, shadowElevation = 6.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("المجموع"), style = MaterialTheme.typography.bodySmall)
                        Text(
                            Money.format(total), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, maxLines = 1,
                        )
                        if (appliedDiscount > 0) {
                            Text(
                                tr("بعد تخفيض %1\$s", Money.format(appliedDiscount)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // البيع بالدَّين: يُسجَّل على زبون ولا يدخل الصندوق حتى يُسدَّد
                        if (allowCredit) {
                            OutlinedButton(
                                enabled = cart.isNotEmpty() && !busy,
                                modifier = Modifier.height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                                onClick = { showCreditPicker = true },
                            ) { Text(tr("دَين")) }
                        }
                        Button(
                            enabled = cart.isNotEmpty() && !busy,
                            modifier = Modifier.height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            onClick = { confirmSale(null) },
                        ) { Text(tr("تأكيد البيع"), style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp)) {
            BarcodeScannerView(
                modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = 12.dp),
                paused = busy || edit != null,
                onCode = { code ->
                    scope.launch {
                        val p = dao.findByBarcode(shopId, code)
                        if (p == null) toast(tr("هذا المنتج غير مسجل. سجّله أولًا من شاشة الشراء."))
                        else changeQty(p, Qty.ONE)
                    }
                },
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("السلة (%1\$s)", cart.size), style = MaterialTheme.typography.titleMedium)
                Row {
                    // بديل عند تلف الكود أو صعوبة قراءته: اختيار المنتج من القائمة
                    TextButton(onClick = { showSearch = true }) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Text(" " + tr("بحث"))
                    }
                    if (allowDiscount && cart.isNotEmpty()) TextButton(onClick = { showDiscount = true }) { Text(tr("تخفيض")) }
                    if (cart.isNotEmpty()) TextButton(onClick = { cart.clear(); discount = 0L }) { Text(tr("إفراغ")) }
                }
            }
            if (cart.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(tr("وجّه الكاميرا نحو الكود بار لإضافة المنتج"), textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(cart, key = { it.product.id }) { line ->
                        val changed = line.price != line.product.sellPrice
                        CartRow(
                            name = line.product.name,
                            subtitle = "${Money.format(line.price)} × ${Qty.format(line.qty, line.product.unit)}" +
                                if (changed) "  (" + tr("السعر الأصلي %1\$s", Money.format(line.product.sellPrice)) + ")" else "",
                            lineTotal = Money.format(line.amount),
                            qty = Qty.plain(line.qty),
                            onPlus = { changeQty(line.product, Qty.ONE) },
                            onMinus = {
                                // الموزون: إنقاص كيلو قد يجعل الكمية سالبة، فنحذف السطر إذا كان أقل من وحدة
                                if (line.qty <= Qty.ONE) setLine(line.product, 0, line.price) else changeQty(line.product, -Qty.ONE)
                            },
                            onQtyClick = { edit = LineEdit(line.product, line.qty, line.price) },
                        )
                    }
                }
            }
        }
    }

    edit?.let { e ->
        val p = e.product
        val weighed = Qty.isWeighed(p.unit)
        var qtyText by remember(p.id) { mutableStateOf(e.qty?.let { Qty.toInput(it) } ?: "") }
        var priceText by remember(p.id) { mutableStateOf(Money.toInput(e.price)) }
        // المبلغ المطلوب (للموزون): يكتب البائع «بـ 200» فتُحسب الكمية تلقائيًا
        var byAmount by remember(p.id) { mutableStateOf("") }
        val price = if (allowDiscount) Money.parse(priceText) else e.price
        val amountWanted = Money.parse(byAmount)
        val q = if (weighed && amountWanted != null && amountWanted > 0 && price != null && price > 0) {
            (amountWanted * Qty.ONE + price / 2) / price
        } else Qty.parse(qtyText, p.unit)
        val tooMuch = q != null && q > p.qty
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(p.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("الموجود في المخزون: %1\$s", Qty.format(p.qty, p.unit)), style = MaterialTheme.typography.bodySmall)
                    MoneyField(qtyText, { qtyText = it; byAmount = "" }, if (weighed) tr("الكمية (%1\$s)", Qty.unitLabel(p.unit)) else tr("الكمية"))
                    if (weighed) {
                        MoneyField(byAmount, { byAmount = it }, tr("أو المبلغ المطلوب (تُحسب الكمية)"))
                    }
                    if (allowDiscount) {
                        MoneyField(priceText, { priceText = it }, if (weighed) tr("سعر الكيلو/الوحدة") else tr("سعر الوحدة"))
                        if (showCost && price != null && price < p.buyPrice) ErrorText(tr("تنبيه: السعر أقل من سعر الشراء (%1\$s)", Money.format(p.buyPrice)))
                    }
                    if (q != null && price != null) {
                        Text(
                            tr("%1\$s × %2\$s = %3\$s", Qty.format(q, p.unit), Money.format(price), Money.format(Qty.amount(price, q))),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (tooMuch) ErrorText(tr("الكمية المتوفرة من «%1\$s» هي %2\$s فقط", p.name, Qty.format(p.qty, p.unit)))
                    if (e.qty != null) {
                        TextButton(onClick = { setLine(p, 0, e.price); edit = null }) {
                            Text(tr("حذف من السلة"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = q != null && q > 0 && !tooMuch && price != null, onClick = {
                    setLine(p, q!!, price!!)
                    edit = null
                }) { Text(if (e.qty == null) tr("إضافة") else tr("حفظ")) }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text(tr("إلغاء")) } },
        )
    }

    if (showDiscount) {
        var mode by remember { mutableStateOf(0) } // 0 = مبلغ التخفيض، 1 = المبلغ النهائي
        var text by remember { mutableStateOf(if (appliedDiscount > 0) Money.toInput(appliedDiscount) else "") }
        val v = Money.parse(text)
        val newDiscount = when {
            v == null -> null
            mode == 0 -> v
            else -> gross - v
        }
        val ok = newDiscount != null && newDiscount in 0..gross
        AlertDialog(
            onDismissRequest = { showDiscount = false },
            title = { Text(tr("تخفيض على البيع")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("المجموع قبل التخفيض: %1\$s", Money.format(gross)))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mode == 0, onClick = { mode = 0; text = "" }, label = { Text(tr("مبلغ التخفيض")) })
                        FilterChip(selected = mode == 1, onClick = { mode = 1; text = "" }, label = { Text(tr("المبلغ النهائي")) })
                    }
                    MoneyField(text, { text = it }, if (mode == 0) tr("مبلغ التخفيض") else tr("المبلغ الذي سيدفعه الزبون"))
                    if (ok) Text(tr("المجموع بعد التخفيض: %1\$s", Money.format(gross - newDiscount!!)), fontWeight = FontWeight.Bold)
                    else if (v != null) ErrorText(tr("التخفيض أكبر من المجموع"))
                }
            },
            confirmButton = {
                TextButton(enabled = ok, onClick = { discount = newDiscount!!; showDiscount = false }) { Text(tr("تطبيق")) }
            },
            dismissButton = {
                Row {
                    if (appliedDiscount > 0) TextButton(onClick = { discount = 0L; showDiscount = false }) { Text(tr("إزالة التخفيض")) }
                    TextButton(onClick = { showDiscount = false }) { Text(tr("إلغاء")) }
                }
            },
        )
    }

    if (showSearch) {
        val products by dao.products(shopId).collectAsState(initial = emptyList())
        var query by remember { mutableStateOf("") }
        val shown = products.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text(tr("اختيار منتج")) },
            text = {
                Column {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        label = { Text(tr("بحث بالاسم")) }, modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(shown, key = { it.id }) { p ->
                            Row(
                                Modifier.fillMaxWidth().clickable { showSearch = false; changeQty(p, Qty.ONE) }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(p.name, modifier = Modifier.weight(1f))
                                Text(
                                    if (p.qty > 0) (Money.format(p.sellPrice) + (if (Qty.isWeighed(p.unit)) " / " + Qty.unitLabel(p.unit) else ""))
                                    else tr("نفد"),
                                    color = if (p.qty > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSearch = false }) { Text(tr("إغلاق")) } },
        )
    }

    if (showCreditPicker) {
        CustomerPickerDialog(
            dao, shopId, total,
            onDismiss = { showCreditPicker = false },
            onPick = { id -> showCreditPicker = false; confirmSale(id) },
        )
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
fun CartRow(
    name: String,
    subtitle: String,
    lineTotal: String,
    qty: String,
    onPlus: (() -> Unit)?,
    onMinus: () -> Unit,
    onQtyClick: (() -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                Text(lineTotal, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onMinus) { Icon(Icons.Default.Remove, contentDescription = tr("إنقاص")) }
            // الضغط على الرقم يسمح بكتابة الكمية مباشرة (مثلًا 24 قطعة)
            Text(
                qty, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = if (onQtyClick != null) Modifier.clickable(onClick = onQtyClick).padding(horizontal = 8.dp, vertical = 4.dp) else Modifier,
            )
            if (onPlus != null) {
                IconButton(onClick = onPlus) { Icon(Icons.Default.Add, contentDescription = tr("زيادة")) }
            }
        }
    }
}
