package com.elyedali.makhzan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.elyedali.makhzan.data.Operation
import com.elyedali.makhzan.data.OperationLine
import com.elyedali.makhzan.data.cashEffect
import com.elyedali.makhzan.data.Qty
import com.elyedali.makhzan.data.TrustedClock
import com.elyedali.makhzan.data.title
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.input.KeyboardType
import com.elyedali.makhzan.data.ShopDao
import kotlinx.coroutines.launch
import com.elyedali.makhzan.i18n.tr

private val InColor = Color(0xFF0B6E4F)
private val OutColor = Color(0xFFC62828)

@Composable
fun CashScreen(dao: ShopDao, shopId: Long, onBack: () -> Unit, onInvoice: (Long) -> Unit) {
    // 0 = اليوم، 1 = هذا الشهر، 2 = الكل
    var period by remember { mutableStateOf(0) }
    val from = when (period) {
        0 -> Dates.startOfToday()
        1 -> Dates.startOfMonth()
        else -> 0L
    }

    val balance by dao.balance(shopId).collectAsState(initial = 0L)
    val sales by remember(from) { dao.sumType(shopId, OpType.SALE, from) }.collectAsState(initial = 0L)
    val purchases by remember(from) { dao.sumType(shopId, OpType.PURCHASE, from) }.collectAsState(initial = 0L)
    val expenses by remember(from) { dao.sumType(shopId, OpType.EXPENSE, from) }.collectAsState(initial = 0L)
    val payments by remember(from) { dao.sumType(shopId, OpType.PAYMENT, from) }.collectAsState(initial = 0L)
    val returns by remember(from) { dao.sumType(shopId, OpType.RETURN, from) }.collectAsState(initial = 0L)
    val adjustLoss by remember(from) { dao.sumType(shopId, OpType.ADJUST, from) }.collectAsState(initial = 0L)
    val supplierPaid by remember(from) { dao.sumType(shopId, OpType.SUPPLIER_PAYMENT, from) }.collectAsState(initial = 0L)
    val supplierDebt by dao.totalSupplierDebt(shopId).collectAsState(initial = 0L)
    val debts by dao.totalDebt(shopId).collectAsState(initial = 0L)
    val profit by remember(from) { dao.profit(shopId, from) }.collectAsState(initial = 0L)
    val ops by remember(from) { dao.operations(shopId, from) }.collectAsState(initial = emptyList())
    val deposits by remember(from) { dao.sumType(shopId, OpType.DEPOSIT, from) }.collectAsState(initial = 0L)
    val withdrawals by remember(from) { dao.sumType(shopId, OpType.WITHDRAW, from) }.collectAsState(initial = 0L)
    var opened by remember { mutableStateOf<Operation?>(null) }
    /** OpType.DEPOSIT أو OpType.WITHDRAW */
    var moveType by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { AppTopBar(tr("الصندوق"), onBack) }, bottomBar = { AdBanner(dao) }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(tr("رصيد الصندوق"), color = Color.White.copy(alpha = 0.85f))
                        Text(
                            Money.format(balance), style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (balance < 0) Color(0xFFFFCDD2) else Color.White,
                        )
                    }
                }
            }
            item {
                // إيداع: رصيد افتتاحي أو مال يضعه صاحب المحل. سحب: ما يأخذه صاحب المحل لنفسه (ليس مصروفًا)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { moveType = OpType.DEPOSIT }, modifier = Modifier.weight(1f)) { Text(tr("إيداع في الصندوق")) }
                    OutlinedButton(onClick = { moveType = OpType.WITHDRAW }, modifier = Modifier.weight(1f)) { Text(tr("سحب من الصندوق")) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = period == 0, onClick = { period = 0 }, label = { Text(tr("اليوم")) })
                    FilterChip(selected = period == 1, onClick = { period = 1 }, label = { Text(tr("هذا الشهر")) })
                    FilterChip(selected = period == 2, onClick = { period = 2 }, label = { Text(tr("الكل")) })
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(tr("المبيعات"), sales, InColor, Modifier.weight(1f))
                        StatTile(tr("المشتريات"), purchases, Color(0xFF1565C0), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(tr("المصاريف"), expenses, OutColor, Modifier.weight(1f))
                        StatTile(tr("ربح المبيعات"), profit, InColor, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(tr("مقبوضات الديون"), payments, InColor, Modifier.weight(1f))
                        StatTile(tr("الديون على الزبائن"), debts, Color(0xFFB45309), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(tr("المرتجعات"), returns, OutColor, Modifier.weight(1f))
                        StatTile(tr("خسائر المخزون"), adjustLoss, OutColor, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(tr("المدفوع للموردين"), supplierPaid, Color(0xFF7B1FA2), Modifier.weight(1f))
                        StatTile(tr("على المحل للموردين"), supplierDebt, Color(0xFF7B1FA2), Modifier.weight(1f))
                    }
                    if (deposits != 0L || withdrawals != 0L) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(tr("الإيداعات"), deposits, InColor, Modifier.weight(1f))
                            StatTile(tr("سحب صاحب المحل"), withdrawals, OutColor, Modifier.weight(1f))
                        }
                    }
                    // الربح الصافي = ربح المبيعات − المصاريف − خسائر المخزون (التالف والضائع وفروق الجرد)
                    val net = profit - expenses - adjustLoss
                    StatTile(tr("الربح الصافي"), net, if (net < 0) OutColor else InColor, Modifier.fillMaxWidth())
                }
            }
            item {
                Text(tr("حركة الصندوق"), style = MaterialTheme.typography.titleMedium)
            }
            if (ops.isEmpty()) {
                item { Text(tr("لا توجد عمليات في هذه الفترة")) }
            }
            items(ops, key = { it.id }) { op ->
                val effect = op.cashEffect
                Card(
                    onClick = { opened = op },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                op.title() + if (op.note.isNotBlank()) " — ${op.note}" else "",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(Dates.format(op.time), style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            // ما لا يمس الصندوق (الدَّين، التسوية): يُعرض المبلغ دون إشارة وبلون مختلف
                            (if (effect > 0) "+" else if (effect < 0) "−" else "") + Money.format(kotlin.math.abs(op.total)),
                            color = if (effect > 0) InColor else if (effect < 0) OutColor else Color(0xFFB45309),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    moveType?.let { type ->
        val isDeposit = type == OpType.DEPOSIT
        var amount by remember(type) { mutableStateOf("") }
        var note by remember(type) { mutableStateOf("") }
        val v = Money.parse(amount)
        val tooMuch = !isDeposit && v != null && v > balance
        AlertDialog(
            onDismissRequest = { moveType = null },
            title = { Text(if (isDeposit) tr("إيداع في الصندوق") else tr("سحب من الصندوق")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isDeposit) tr("مثلًا: المال الموجود في الصندوق عند بدء استعمال التطبيق، أو مال تضعه من جيبك. لا يُحسب ربحًا.")
                        else tr("ما تأخذه لنفسك من الصندوق. لا يُحسب مصروفًا ولا ينقص الربح."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    MoneyField(amount, { amount = it }, tr("المبلغ"))
                    OutlinedTextField(
                        value = note, onValueChange = { note = it }, singleLine = true,
                        label = { Text(tr("ملاحظة (اختياري)")) }, modifier = Modifier.fillMaxWidth(),
                    )
                    if (tooMuch) ErrorText(tr("المبلغ أكبر من رصيد الصندوق (%1\$s)", Money.format(balance)))
                }
            },
            confirmButton = {
                TextButton(enabled = v != null && v > 0 && !tooMuch, onClick = {
                    moveType = null
                    scope.launch {
                        dao.insertOperation(
                            Operation(
                                shopId = shopId, type = type, total = v!!,
                                note = note.trim().ifBlank { if (isDeposit && balance == 0L) tr("رصيد افتتاحي") else "" },
                                time = TrustedClock.now(),
                            )
                        )
                    }
                }) { Text(tr("حفظ")) }
            },
            dismissButton = { TextButton(onClick = { moveType = null }) { Text(tr("إلغاء")) } },
        )
    }

    opened?.let { op ->
        OperationLinesDialog(dao, op, onDismiss = { opened = null }, onInvoice = { opened = null; onInvoice(op.id) })
    }
}

@Composable
private fun StatTile(label: String, value: Long, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.09f)),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = color)
            Text(Money.format(value), fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun OperationLinesDialog(dao: ShopDao, op: Operation, onDismiss: () -> Unit, onInvoice: () -> Unit) {
    var lines by remember { mutableStateOf<List<OperationLine>>(emptyList()) }
    var confirmCancel by remember { mutableStateOf(false) }
    var showReturn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var customerName by remember { mutableStateOf<String?>(null) }
    var supplierName by remember { mutableStateOf<String?>(null) }
    var workerName by remember { mutableStateOf<String?>(null) }
    var units by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(op.id) {
        lines = dao.lines(op.id)
        units = lines.associate { it.productId to (dao.productNow(it.productId)?.unit ?: "") }
        customerName = op.customerId?.let { dao.customerNow(it)?.name }
        supplierName = op.supplierId?.let { dao.supplierNow(it)?.name }
        workerName = op.workerId?.let { dao.workerNow(it)?.name ?: tr("عامل محذوف") }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(op.title() + " — " + Dates.format(op.time)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (op.note.isNotBlank()) Text(op.note)
                customerName?.let { Text(tr("الزبون: %1\$s", it), fontWeight = FontWeight.SemiBold) }
                supplierName?.let { Text(tr("المورد: %1\$s", it), fontWeight = FontWeight.SemiBold) }
                workerName?.let { Text(tr("البائع: %1\$s", it)) }
                if (op.type == OpType.RETURN && op.refId != null) Text(tr("من البيع رقم %1\$s", op.refId))
                lines.forEach { l ->
                    val u = units[l.productId] ?: ""
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${l.productName} × " + (if (l.qty < 0) "−" else "") + Qty.format(kotlin.math.abs(l.qty), u),
                            modifier = Modifier.weight(1f),
                        )
                        Text(Money.format(kotlin.math.abs(l.amount)))
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (op.discount > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr("التخفيض"))
                        Text("−" + Money.format(op.discount))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (op.type == OpType.ADJUST) (if (op.total >= 0) tr("قيمة النقص") else tr("قيمة الزيادة")) else tr("المجموع"), fontWeight = FontWeight.Bold)
                    Text(Money.format(kotlin.math.abs(op.total)), fontWeight = FontWeight.Bold)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (op.type == OpType.SALE) {
                    TextButton(onClick = { showReturn = true }) { Text(tr("إرجاع سلع من هذا البيع")) }
                }
                TextButton(onClick = { confirmCancel = true }) {
                    Text(tr("إلغاء هذه العملية"), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("إغلاق")) } },
        dismissButton = {
            if (op.type == OpType.SALE) TextButton(onClick = onInvoice) { Text(tr("الفاتورة")) }
        },
    )
    if (showReturn) {
        ReturnDialog(dao, op, lines, units, onDismiss = { showReturn = false }, onDone = {
            showReturn = false
            onDismiss()
        })
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(tr("إلغاء العملية")) },
            text = { Text(tr("ستُحذف هذه العملية ويعود المخزون والصندوق كما كانا قبلها. هل تريد المتابعة؟")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    scope.launch {
                        try {
                            dao.cancelOperation(op.id)
                            onDismiss()
                        } catch (e: IllegalStateException) {
                            error = e.message
                        }
                    }
                }) { Text(tr("نعم، إلغاء العملية"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text(tr("تراجع")) } },
        )
    }
}

/** إرجاع جزء من بيع: يختار التاجر الكمية المرجعة من كل منتج */
@Composable
private fun ReturnDialog(
    dao: ShopDao, sale: Operation, lines: List<OperationLine>, units: Map<Long, String>,
    onDismiss: () -> Unit, onDone: () -> Unit,
) {
    var returned by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    val texts = remember { mutableStateMapOf<Long, String>() }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sale.id) { returned = dao.returnedFor(sale.id).associate { it.productId to it.qty } }

    val wanted = lines.associate { l ->
        l.productId to (texts[l.productId]?.let { Qty.parse(it, units[l.productId] ?: "") } ?: 0L)
    }
    val bad = lines.any { l ->
        val t = texts[l.productId]
        val left = l.qty - (returned[l.productId] ?: 0L)
        (!t.isNullOrBlank() && Qty.parse(t, units[l.productId] ?: "") == null) || (wanted[l.productId] ?: 0L) > left
    }
    val gross = sale.total + sale.discount
    val estimate = lines.sumOf { l ->
        val q = wanted[l.productId] ?: 0L
        if (q <= 0) 0L else Qty.share(if (q == l.qty) l.amount else Qty.amount(l.unitPrice, q), sale.total, gross)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("إرجاع سلع")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEach { l ->
                    val u = units[l.productId] ?: ""
                    val left = l.qty - (returned[l.productId] ?: 0L)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.productName, fontWeight = FontWeight.SemiBold)
                            Text(tr("يمكن إرجاع %1\$s", Qty.format(left, u)), style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedTextField(
                            value = texts[l.productId] ?: "", onValueChange = { texts[l.productId] = it },
                            enabled = left > 0, singleLine = true, label = { Text(tr("الكمية")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(100.dp),
                        )
                    }
                }
                if (bad) Text(tr("كمية غير صحيحة أو أكبر مما يمكن إرجاعه"), color = MaterialTheme.colorScheme.error)
                if (estimate > 0) {
                    Text(
                        if (sale.customerId != null) tr("يُنقص من دين الزبون: %1\$s", Money.format(estimate))
                        else tr("يُردّ للزبون من الصندوق: %1\$s", Money.format(estimate)),
                        fontWeight = FontWeight.Bold,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !bad && wanted.values.any { it > 0 } && !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        dao.recordReturn(sale.id, wanted, TrustedClock.now())
                        onDone()
                    } catch (e: IllegalStateException) {
                        error = e.message
                    } finally {
                        busy = false
                    }
                }
            }) { Text(tr("تأكيد الإرجاع")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء")) } },
    )
}
