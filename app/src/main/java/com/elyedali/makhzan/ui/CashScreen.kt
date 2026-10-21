package com.elyedali.makhzan.ui

import androidx.compose.foundation.layout.Arrangement
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

private val InColor = Color(0xFF0B6E4F)
private val OutColor = Color(0xFFC62828)

@Composable
fun CashScreen(dao: ShopDao, shopId: Long, onBack: () -> Unit, onInvoice: (Long) -> Unit) {
    var todayOnly by remember { mutableStateOf(true) }
    val from = if (todayOnly) Dates.startOfToday() else 0L

    val balance by dao.balance(shopId).collectAsState(initial = 0L)
    val sales by remember(from) { dao.sumType(shopId, OpType.SALE, from) }.collectAsState(initial = 0L)
    val purchases by remember(from) { dao.sumType(shopId, OpType.PURCHASE, from) }.collectAsState(initial = 0L)
    val expenses by remember(from) { dao.sumType(shopId, OpType.EXPENSE, from) }.collectAsState(initial = 0L)
    val profit by remember(from) { dao.profit(shopId, from) }.collectAsState(initial = 0L)
    val ops by remember(from) { dao.operations(shopId, from) }.collectAsState(initial = emptyList())
    var opened by remember { mutableStateOf<Operation?>(null) }

    Scaffold(topBar = { AppTopBar("الصندوق", onBack) }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("الرصيد الحالي للصندوق", style = MaterialTheme.typography.titleSmall)
                        Text(
                            Money.format(balance), style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (balance < 0) OutColor else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = todayOnly, onClick = { todayOnly = true }, label = { Text("اليوم") })
                    FilterChip(selected = !todayOnly, onClick = { todayOnly = false }, label = { Text("كل الفترات") })
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SummaryRow("المبيعات", sales, InColor)
                        SummaryRow("المشتريات", purchases, OutColor)
                        SummaryRow("المصاريف", expenses, OutColor)
                        SummaryRow("الربح من المبيعات", profit, InColor)
                    }
                }
            }
            item {
                Text("حركة الصندوق", style = MaterialTheme.typography.titleMedium)
            }
            if (ops.isEmpty()) {
                item { Text("لا توجد عمليات في هذه الفترة") }
            }
            items(ops, key = { it.id }) { op ->
                val isIn = op.type == OpType.SALE
                Card(
                    onClick = { if (op.type != OpType.EXPENSE) opened = op },
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
private fun SummaryRow(label: String, value: Long, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(Money.format(value), color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OperationLinesDialog(dao: ShopDao, op: Operation, onDismiss: () -> Unit, onInvoice: () -> Unit) {
    var lines by remember { mutableStateOf<List<OperationLine>>(emptyList()) }
    LaunchedEffect(op.id) { lines = dao.lines(op.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${OpType.label(op.type)} — ${Dates.format(op.time)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEach { l ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${l.productName} × ${l.qty}", modifier = Modifier.weight(1f))
                        Text(Money.format(l.unitPrice * l.qty))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("المجموع", fontWeight = FontWeight.Bold)
                    Text(Money.format(op.total), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
        dismissButton = {
            if (op.type == OpType.SALE) TextButton(onClick = onInvoice) { Text("الفاتورة") }
        },
    )
}
