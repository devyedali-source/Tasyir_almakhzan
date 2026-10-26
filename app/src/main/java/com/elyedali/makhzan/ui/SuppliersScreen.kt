package com.elyedali.makhzan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.elyedali.makhzan.data.Dates
import com.elyedali.makhzan.data.Money
import com.elyedali.makhzan.data.OpType
import com.elyedali.makhzan.data.Operation
import com.elyedali.makhzan.data.Qty
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.input.KeyboardType
import com.elyedali.makhzan.data.ShopDao
import com.elyedali.makhzan.data.Supplier
import com.elyedali.makhzan.data.TrustedClock
import com.elyedali.makhzan.i18n.tr
import kotlinx.coroutines.launch

private val OweColor = Color(0xFF7B1FA2)
private val SettledColor = Color(0xFF0B6E4F)

/** الموردون وما على المحل لكل واحد منهم */
@Composable
fun SuppliersScreen(dao: ShopDao, shopId: Long, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val suppliers by dao.suppliersWithDebt(shopId).collectAsState(initial = emptyList())
    val total by dao.totalSupplierDebt(shopId).collectAsState(initial = 0L)
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shown = suppliers.filter {
        query.isBlank() || it.supplier.name.contains(query.trim(), ignoreCase = true) || it.supplier.phone.contains(query.trim())
    }

    Scaffold(
        topBar = { AppTopBar(tr("الموردون"), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.LocalShipping, contentDescription = null) },
                text = { Text(tr("مورد جديد")) },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = OweColor),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(tr("مجموع ما على المحل للموردين"), color = Color.White.copy(alpha = 0.85f))
                        Text(
                            Money.format(total), style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold, color = Color.White,
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(tr("بحث بالاسم أو الهاتف")) }, modifier = Modifier.fillMaxWidth(),
                )
            }
            if (suppliers.isEmpty()) {
                item {
                    Text(tr("لا يوجد موردون بعد. يُضاف المورد عند الشراء بالدَّين أو من زر «مورد جديد»."))
                }
            }
            items(shown, key = { it.supplier.id }) { sd ->
                Card(onClick = { onOpen(sd.supplier.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sd.supplier.name, fontWeight = FontWeight.SemiBold)
                            if (sd.supplier.phone.isNotBlank()) Text(sd.supplier.phone, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            if (sd.debt > 0) Money.format(sd.debt) else tr("لا دَين"),
                            color = if (sd.debt > 0) OweColor else SettledColor, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        CustomerFormDialog(
            title = tr("مورد جديد"), nameLabel = tr("اسم المورد"),
            onDismiss = { showAdd = false },
            onSave = { name, phone ->
                showAdd = false
                scope.launch { dao.insertSupplier(Supplier(shopId = shopId, name = name, phone = phone, createdAt = TrustedClock.now())) }
            },
        )
    }
}

/** صفحة المورد: ما على المحل له، المشتريات بالدَّين والدفعات */
@Composable
fun SupplierDetailScreen(dao: ShopDao, shopId: Long, supplierId: Long, onBack: () -> Unit) {
    val supplier by dao.supplier(supplierId).collectAsState(initial = null)
    val debt by dao.supplierDebt(supplierId).collectAsState(initial = 0L)
    val ops by dao.supplierOperations(supplierId).collectAsState(initial = emptyList())
    var showPay by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showOldDebt by remember { mutableStateOf(false) }
    var showReturn by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { AppTopBar(supplier?.name ?: "", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = if (debt > 0) OweColor else SettledColor),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(tr("المتبقي على المحل"), color = Color.White.copy(alpha = 0.85f))
                        Text(
                            Money.format(debt), style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold, color = Color.White,
                        )
                        supplier?.phone?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White.copy(alpha = 0.85f)) }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = debt > 0, onClick = { showPay = true },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    ) { Text(tr("دفع للمورد")) }
                    OutlinedButton(
                        enabled = debt > 0, onClick = { showReturn = true },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    ) { Text(tr("إرجاع بضاعة")) }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { showEdit = true }) { Text(tr("تعديل")) }
                    // دين كان على المحل لهذا المورد قبل استعمال التطبيق
                    TextButton(onClick = { showOldDebt = true }) { Text(tr("دين سابق")) }
                }
            }
            item { Text(tr("السجل"), style = MaterialTheme.typography.titleMedium) }
            if (ops.isEmpty()) item { Text(tr("لا توجد عمليات")) }
            items(ops, key = { it.id }) { op ->
                // الدفع والإرجاع ينقصان ما على المحل، والشراء بالدَّين والدين السابق يزيدانه
                val isPay = op.type == OpType.SUPPLIER_PAYMENT || op.type == OpType.SUPPLIER_RETURN
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (op.type) {
                                    OpType.SUPPLIER_PAYMENT -> tr("دفع للمورد")
                                    OpType.SUPPLIER_RETURN -> tr("إرجاع للمورد")
                                    OpType.OLD_DEBT -> tr("دين سابق") + if (op.note.isNotBlank()) " — ${op.note}" else ""
                                    else -> tr("شراء بالدَّين")
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(Dates.format(op.time), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            (if (isPay) "−" else "+") + Money.format(op.total),
                            color = if (isPay) SettledColor else OweColor, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    if (showPay) {
        var amount by remember { mutableStateOf(Money.toInput(debt)) }
        val v = Money.parse(amount)
        AlertDialog(
            onDismissRequest = { showPay = false },
            title = { Text(tr("دفع للمورد")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("المتبقي على المحل: %1\$s", Money.format(debt)))
                    MoneyField(amount, { amount = it }, tr("المبلغ المدفوع (يخرج من الصندوق)"))
                    if (v != null && v > debt) ErrorText(tr("المبلغ أكبر من الدَّين المتبقي"))
                }
            },
            confirmButton = {
                TextButton(enabled = v != null && v > 0 && v <= debt, onClick = {
                    showPay = false
                    scope.launch {
                        try {
                            dao.recordSupplierPayment(shopId, supplierId, v!!, TrustedClock.now())
                            snackbar.showSnackbar(tr("تم تسجيل الدفع: %1\$s", Money.format(v)))
                        } catch (e: IllegalStateException) {
                            snackbar.showSnackbar(e.message ?: "")
                        }
                    }
                }) { Text(tr("حفظ")) }
            },
            dismissButton = { TextButton(onClick = { showPay = false }) { Text(tr("إلغاء")) } },
        )
    }

    if (showOldDebt) {
        OldDebtDialog(
            title = tr("دين سابق لمورد"),
            hint = tr("مبلغ كان على المحل لهذا المورد قبل أن تبدأ استعمال التطبيق. يُضاف إلى ما عليك ولا يمس الصندوق ولا المخزون."),
            onDismiss = { showOldDebt = false },
            onSave = { amount, note ->
                showOldDebt = false
                scope.launch {
                    dao.insertOldDebt(
                        Operation(shopId = shopId, type = OpType.OLD_DEBT, total = amount, note = note, time = TrustedClock.now(), supplierId = supplierId)
                    )
                    snackbar.showSnackbar(tr("تم التسجيل"))
                }
            },
        )
    }
    if (showReturn) {
        SupplierReturnDialog(dao, shopId, supplierId, debt, onDismiss = { showReturn = false }, onDone = {
            showReturn = false
            scope.launch { snackbar.showSnackbar(tr("تم التسجيل")) }
        })
    }

    val s = supplier
    if (showEdit && s != null) {
        CustomerFormDialog(
            title = tr("تعديل المورد"), nameLabel = tr("اسم المورد"),
            initialName = s.name, initialPhone = s.phone,
            onDismiss = { showEdit = false },
            onSave = { name, phone ->
                showEdit = false
                scope.launch { dao.updateSupplier(s.id, name, phone) }
            },
        )
    }
}

/** إرجاع بضاعة إلى المورد: يختار التاجر المنتجات والكميات، وتُخصم قيمتها بسعر الشراء مما عليه للمورد */
@Composable
private fun SupplierReturnDialog(
    dao: ShopDao, shopId: Long, supplierId: Long, debt: Long, onDismiss: () -> Unit, onDone: () -> Unit,
) {
    val products by dao.products(shopId).collectAsState(initial = emptyList())
    val texts = remember { mutableStateMapOf<Long, String>() }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val byId = products.associateBy { it.id }
    val wanted = texts.mapNotNull { (id, t) ->
        val p = byId[id] ?: return@mapNotNull null
        if (t.isBlank()) null else id to (Qty.parse(t, p.unit) ?: -1L)
    }.toMap()
    val bad = wanted.any { (id, q) -> q <= 0 || q > (byId[id]?.qty ?: 0L) }
    val value = wanted.entries.sumOf { (id, q) -> if (q > 0) Qty.amount(byId[id]?.buyPrice ?: 0L, q) else 0L }
    val shown = products.filter { it.qty > 0 && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("إرجاع بضاعة للمورد")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("تُخصم قيمتها بسعر الشراء مما على المحل لهذا المورد (%1\$s).", Money.format(debt)), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(tr("بحث بالاسم")) }, modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    items(shown, key = { it.id }) { p ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name)
                                Text(tr("الموجود: %1\$s", Qty.format(p.qty, p.unit)), style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedTextField(
                                value = texts[p.id] ?: "", onValueChange = { texts[p.id] = it }, singleLine = true,
                                label = { Text(tr("الكمية")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(96.dp),
                            )
                        }
                    }
                }
                if (bad) ErrorText(tr("كمية غير صحيحة أو أكبر من الموجود"))
                if (value > 0) {
                    Text(tr("القيمة: %1\$s", Money.format(value)), fontWeight = FontWeight.Bold)
                    if (value > debt) ErrorText(tr("قيمة البضاعة المرجعة أكبر مما على المحل لهذا المورد"))
                }
                error?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            TextButton(enabled = !bad && value > 0 && value <= debt && !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        dao.recordSupplierReturn(shopId, supplierId, wanted, TrustedClock.now())
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

/** اختيار المورد عند الشراء بالدَّين (أو إضافة مورد جديد مباشرة) */
@Composable
fun SupplierPickerDialog(dao: ShopDao, shopId: Long, total: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val suppliers by dao.suppliersWithDebt(shopId).collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shown = suppliers.filter { query.isBlank() || it.supplier.name.contains(query.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("شراء بالدَّين: %1\$s", Money.format(total))) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(tr("ابحث عن المورد")) }, modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.LocalShipping, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("مورد جديد"))
                }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(shown, key = { it.supplier.id }) { sd ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(sd.supplier.id) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(sd.supplier.name, modifier = Modifier.weight(1f))
                            if (sd.debt > 0) Text(Money.format(sd.debt), color = OweColor, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء")) } },
    )

    if (showAdd) {
        CustomerFormDialog(
            title = tr("مورد جديد"), nameLabel = tr("اسم المورد"),
            initialName = query.trim(),
            onDismiss = { showAdd = false },
            onSave = { name, phone ->
                showAdd = false
                scope.launch {
                    val id = dao.insertSupplier(Supplier(shopId = shopId, name = name, phone = phone, createdAt = TrustedClock.now()))
                    onPick(id)
                }
            },
        )
    }
}
