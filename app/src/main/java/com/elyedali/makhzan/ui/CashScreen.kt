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
import com.elyedali.makhzan.data.Dates
import com.elyedali.makhzan.data.Money
import com.elyedali.makhzan.data.OpType
import com.elyedali.makhzan.data.Operation
import com.elyedali.makhzan.data.OperationLine
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
    val profit by remember(from) { dao.profit(shopId, from) }.collectAsState(initial = 0L)
    val ops by remember(from) { dao.operations(shopId, from) }.collectAsState(initial = emptyList())
    var opened by remember { mutableStateOf<Operation?>(null) }

    Scaffold(topBar = { AppTopBar(tr("الصندوق"), onBack) }) { pad ->
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
                    // الربح الصافي = ربح المبيعات − المصاريف
                    StatTile(tr("الربح الصافي"), profit - expenses, if (profit - expenses < 0) OutColor else InColor, Modifier.fillMaxWidth())
                }
            }
            item {
                Text(tr("حركة الصندوق"), style = MaterialTheme.typography.titleMedium)
            }
            if (ops.isEmpty()) {
                item { Text(tr("لا توجد عمليات في هذه الفترة")) }
            }
            items(ops, key = { it.id }) { op ->
                val isIn = op.type == OpType.SALE
                Card(
                    onClick = { opened = op },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                OpType.label(op.type) + if (op.note.isNotBlank()) " — ${op.note}" else "",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(Dates.format(op.time), style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            (if (isIn) "+" else "−") + Money.format(op.total),
                            color = if (isIn) InColor else OutColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
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
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(op.id) { lines = dao.lines(op.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(OpType.label(op.type) + " — " + Dates.format(op.time)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (op.note.isNotBlank()) Text(op.note)
                lines.forEach { l ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${l.productName} × ${l.qty}", modifier = Modifier.weight(1f))
                        Text(Money.format(l.unitPrice * l.qty))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("المجموع"), fontWeight = FontWeight.Bold)
                    Text(Money.format(op.total), fontWeight = FontWeight.Bold)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
