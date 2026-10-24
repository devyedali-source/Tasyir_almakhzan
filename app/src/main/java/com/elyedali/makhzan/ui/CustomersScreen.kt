package com.elyedali.makhzan.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.elyedali.makhzan.data.Customer
import com.elyedali.makhzan.data.Dates
import com.elyedali.makhzan.data.Money
import com.elyedali.makhzan.data.OpType
import com.elyedali.makhzan.data.ShopDao
import com.elyedali.makhzan.data.TrustedClock
import com.elyedali.makhzan.i18n.tr
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.elyedali.makhzan.invoice.ReportPdf

private val DebtColor = Color(0xFFB45309)
private val PaidColor = Color(0xFF0B6E4F)

/** قائمة الزبائن وديونهم */
@Composable
fun CustomersScreen(dao: ShopDao, shopId: Long, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val customers by dao.customersWithDebt(shopId).collectAsState(initial = emptyList())
    val total by dao.totalDebt(shopId).collectAsState(initial = 0L)
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shown = customers.filter {
        query.isBlank() || it.customer.name.contains(query.trim(), ignoreCase = true) || it.customer.phone.contains(query.trim())
    }

    Scaffold(
        topBar = { AppTopBar(tr("الديون"), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text(tr("زبون جديد")) },
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
                    colors = CardDefaults.cardColors(containerColor = DebtColor),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(tr("مجموع الديون على الزبائن"), color = Color.White.copy(alpha = 0.85f))
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
            if (customers.isEmpty()) {
                item {
                    Text(
                        tr("لا يوجد زبائن بعد. يُضاف الزبون عند البيع بالدَّين أو من زر «زبون جديد»."),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(shown, key = { it.customer.id }) { cd ->
                Card(onClick = { onOpen(cd.customer.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(cd.customer.name, fontWeight = FontWeight.SemiBold)
                            if (cd.customer.phone.isNotBlank()) {
                                Text(cd.customer.phone, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(
                            if (cd.debt > 0) Money.format(cd.debt) else tr("لا دَين"),
                            color = if (cd.debt > 0) DebtColor else PaidColor, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        CustomerFormDialog(
            title = tr("زبون جديد"),
            onDismiss = { showAdd = false },
            onSave = { name, phone ->
                showAdd = false
                scope.launch {
                    dao.insertCustomer(Customer(shopId = shopId, name = name, phone = phone, createdAt = TrustedClock.now()))
                }
            },
        )
    }
}

/** صفحة الزبون: دينه، سجل مشترياته وتسديداته، التسديد والتذكير عبر واتساب */
@Composable
fun CustomerDetailScreen(
    dao: ShopDao, shopId: Long, customerId: Long, onBack: () -> Unit, onInvoice: (Long) -> Unit,
    onPdf: (path: String, title: String) -> Unit,
) {
    val customer by dao.customer(customerId).collectAsState(initial = null)
    val debt by dao.customerDebt(customerId).collectAsState(initial = 0L)
    val ops by dao.customerOperations(customerId).collectAsState(initial = emptyList())
    var showPay by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = { AppTopBar(customer?.name ?: "", onBack) },
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
                    colors = CardDefaults.cardColors(containerColor = if (debt > 0) DebtColor else PaidColor),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(tr("الدَّين المتبقي"), color = Color.White.copy(alpha = 0.85f))
                        Text(
                            Money.format(debt), style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold, color = Color.White,
                        )
                        customer?.phone?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = debt > 0, onClick = { showPay = true },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    ) { Text(tr("تسديد")) }
                    OutlinedButton(
                        enabled = debt > 0 && customer?.phone?.isNotBlank() == true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                        onClick = onClick@{
                            val c = customer ?: return@onClick
                            val msg = tr("السلام عليكم %1\$s، نذكّركم بأن المبلغ المتبقي عليكم هو %2\$s. شكرًا لكم.", c.name, Money.format(debt))
                            val phone = c.phone.filter { it.isDigit() }
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=" + Uri.encode(msg)))
                                )
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, tr("تطبيق واتساب غير موجود على الهاتف"), Toast.LENGTH_LONG).show()
                            }
                        },
                    ) { Text(tr("تذكير")) }
                }
            }
            item {
                Row {
                    TextButton(onClick = {
                        scope.launch {
                            val c = dao.customerNow(customerId) ?: return@launch
                            val shop = dao.shopNow(shopId)
                            val list = dao.customerOperationsNow(customerId)
                            val f = withContext(Dispatchers.IO) {
                                ReportPdf.customerStatement(context, shop?.name ?: "", c, list)
                            }
                            onPdf(f.absolutePath, tr("كشف حساب: %1\$s", c.name))
                        }
                    }) { Text(tr("كشف حساب PDF")) }
                    TextButton(onClick = { showEdit = true }) { Text(tr("تعديل الاسم والهاتف")) }
                }
            }
            item { Text(tr("السجل"), style = MaterialTheme.typography.titleMedium) }
            if (ops.isEmpty()) item { Text(tr("لا توجد عمليات")) }
            items(ops, key = { it.id }) { op ->
                // أثر العملية على دين الزبون: البيع يزيده، والتسديد والمرتجع ينقصانه، وردّ المال يعيده إلى الصفر
                val effect = if (op.type == OpType.SALE) op.total else -op.total
                Card(
                    onClick = { if (op.type == OpType.SALE) onInvoice(op.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (op.type) {
                                    OpType.SALE -> tr("شراء بالدَّين")
                                    OpType.PAYMENT -> if (op.total < 0) tr("ردّ مال لزبون") else tr("تسديد")
                                    else -> tr("مرتجع")
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(Dates.format(op.time), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            (if (effect >= 0) "+" else "−") + Money.format(kotlin.math.abs(effect)),
                            color = if (effect > 0) DebtColor else PaidColor, fontWeight = FontWeight.Bold,
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
            title = { Text(tr("تسديد دَين")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("الدَّين المتبقي: %1\$s", Money.format(debt)))
                    MoneyField(amount, { amount = it }, tr("المبلغ المدفوع"))
                    if (v != null && v > debt) ErrorText(tr("المبلغ أكبر من الدَّين المتبقي"))
                }
            },
            confirmButton = {
                TextButton(enabled = v != null && v > 0 && v <= debt, onClick = {
                    showPay = false
                    scope.launch {
                        try {
                            dao.recordPayment(shopId, customerId, v!!, TrustedClock.now())
                            snackbar.showSnackbar(tr("تم تسجيل التسديد: %1\$s", Money.format(v)))
                        } catch (e: IllegalStateException) {
                            snackbar.showSnackbar(e.message ?: "")
                        }
                    }
                }) { Text(tr("حفظ")) }
            },
            dismissButton = { TextButton(onClick = { showPay = false }) { Text(tr("إلغاء")) } },
        )
    }

    val c = customer
    if (showEdit && c != null) {
        CustomerFormDialog(
            title = tr("تعديل الزبون"),
            initialName = c.name, initialPhone = c.phone,
            onDismiss = { showEdit = false },
            onSave = { name, phone ->
                showEdit = false
                scope.launch { dao.updateCustomer(c.id, name, phone) }
            },
        )
    }
}

@Composable
fun CustomerFormDialog(
    title: String,
    initialName: String = "",
    initialPhone: String = "",
    nameLabel: String = tr("اسم الزبون"),
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(nameLabel) }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it }, singleLine = true,
                    label = { Text(tr("رقم الهاتف (اختياري)")) }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), phone.trim()) }) { Text(tr("حفظ")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء")) } },
    )
}

/** اختيار الزبون عند البيع بالدَّين (أو إضافة زبون جديد مباشرة) */
@Composable
fun CustomerPickerDialog(dao: ShopDao, shopId: Long, total: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val customers by dao.customersWithDebt(shopId).collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shown = customers.filter { query.isBlank() || it.customer.name.contains(query.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("بيع بالدَّين: %1\$s", Money.format(total))) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(tr("ابحث عن الزبون")) }, modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("زبون جديد"))
                }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(shown, key = { it.customer.id }) { cd ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(cd.customer.id) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(cd.customer.name, modifier = Modifier.weight(1f))
                            if (cd.debt > 0) Text(Money.format(cd.debt), color = DebtColor, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء")) } },
    )

    if (showAdd) {
        CustomerFormDialog(
            title = tr("زبون جديد"),
            initialName = query.trim(),
            onDismiss = { showAdd = false },
            onSave = { name, phone ->
                showAdd = false
                scope.launch {
                    val id = dao.insertCustomer(Customer(shopId = shopId, name = name, phone = phone, createdAt = TrustedClock.now()))
                    onPick(id)
                }
            },
        )
    }
}
