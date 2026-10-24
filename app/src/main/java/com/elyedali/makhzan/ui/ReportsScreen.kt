package com.elyedali.makhzan.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elyedali.makhzan.data.Dates
import com.elyedali.makhzan.data.Money
import com.elyedali.makhzan.data.OpType
import com.elyedali.makhzan.data.ShopDao
import com.elyedali.makhzan.i18n.tr
import com.elyedali.makhzan.invoice.PeriodSummary
import com.elyedali.makhzan.invoice.ReportPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private fun dayStart(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun nextDay(t: Long): Long = Calendar.getInstance().apply { timeInMillis = dayStart(t); add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

/**
 * التقارير: ملخص أي فترة بين تاريخين (مع رصيد البداية والنهاية) وسحبه PDF،
 * وتقرير المخزون والوضع الراهن.
 */
@Composable
fun ReportsScreen(dao: ShopDao, shopId: Long, onBack: () -> Unit, onPdf: (path: String, title: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 0 اليوم، 1 هذا الشهر، 2 الشهر الماضي، 3 فترة مخصصة
    var preset by remember { mutableStateOf(1) }
    var customFrom by remember { mutableStateOf(dayStart(System.currentTimeMillis())) }
    var customTo by remember { mutableStateOf(dayStart(System.currentTimeMillis())) }
    var summary by remember { mutableStateOf<PeriodSummary?>(null) }
    var busy by remember { mutableStateOf(false) }

    val (from, toExclusive) = when (preset) {
        0 -> Dates.startOfToday() to nextDay(Dates.startOfToday())
        1 -> Dates.startOfMonth() to nextDay(System.currentTimeMillis())
        2 -> {
            val start = Calendar.getInstance().apply { timeInMillis = Dates.startOfMonth(); add(Calendar.MONTH, -1) }.timeInMillis
            start to Dates.startOfMonth()
        }
        else -> minOf(customFrom, customTo) to nextDay(maxOf(customFrom, customTo))
    }

    suspend fun compute(): PeriodSummary = PeriodSummary(
        from = from, toExclusive = toExclusive,
        openingCash = dao.balanceBefore(shopId, from),
        closingCash = dao.balanceBefore(shopId, toExclusive),
        cashSales = dao.sumTypeRange(shopId, OpType.SALE, from, toExclusive) - dao.creditSalesRange(shopId, from, toExclusive),
        creditSales = dao.creditSalesRange(shopId, from, toExclusive),
        payments = dao.sumTypeRange(shopId, OpType.PAYMENT, from, toExclusive),
        purchases = dao.sumTypeRange(shopId, OpType.PURCHASE, from, toExclusive),
        expenses = dao.sumTypeRange(shopId, OpType.EXPENSE, from, toExclusive),
        profit = dao.profitRange(shopId, from, toExclusive),
    )

    LaunchedEffect(from, toExclusive) { summary = compute() }

    fun pickDate(initial: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(context, { _, y, m, d ->
            onPicked(Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    Scaffold(topBar = { AppTopBar(tr("التقارير"), onBack) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = preset == 0, onClick = { preset = 0 }, label = { Text(tr("اليوم")) })
                FilterChip(selected = preset == 1, onClick = { preset = 1 }, label = { Text(tr("هذا الشهر")) })
                FilterChip(selected = preset == 2, onClick = { preset = 2 }, label = { Text(tr("الشهر الماضي")) })
                FilterChip(selected = preset == 3, onClick = { preset = 3 }, label = { Text(tr("بين تاريخين")) })
            }
            if (preset == 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { pickDate(customFrom) { customFrom = it } }, modifier = Modifier.weight(1f)) {
                        Text(tr("من: %1\$s", Dates.formatDay(customFrom)))
                    }
                    OutlinedButton(onClick = { pickDate(customTo) { customTo = it } }, modifier = Modifier.weight(1f)) {
                        Text(tr("إلى: %1\$s", Dates.formatDay(customTo)))
                    }
                }
            }
            Text(
                tr("من %1\$s إلى %2\$s", Dates.formatDay(from), Dates.formatDay(toExclusive - 1)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            )

            val s = summary
            if (s == null) CircularProgressIndicator()
            else Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Line(tr("رصيد بداية الفترة"), s.openingCash)
                    Line(tr("المبيعات النقدية"), s.cashSales, Color(0xFF0B6E4F))
                    Line(tr("المبيعات بالدَّين"), s.creditSales, Color(0xFFB45309))
                    Line(tr("مقبوضات الديون"), s.payments, Color(0xFF0B6E4F))
                    Line(tr("المشتريات"), s.purchases, Color(0xFFC62828))
                    Line(tr("المصاريف"), s.expenses, Color(0xFFC62828))
                    Line(tr("رصيد نهاية الفترة"), s.closingCash, bold = true)
                    Line(tr("ربح المبيعات"), s.profit)
                    Line(tr("الربح الصافي"), s.netProfit, if (s.netProfit < 0) Color(0xFFC62828) else Color(0xFF0B6E4F), bold = true)
                }
            }

            Button(
                enabled = !busy && s != null, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                onClick = {
                    busy = true
                    scope.launch {
                        val shop = dao.shopNow(shopId)
                        val sum = compute()
                        val top = dao.productSalesRange(shopId, from, toExclusive)
                        val ops = dao.operationsRange(shopId, from, toExclusive)
                        val customers = dao.customersNow(shopId).associateBy { it.id }
                        val f = withContext(Dispatchers.IO) {
                            ReportPdf.period(context, shop?.name ?: "", sum, top, ops, customers)
                        }
                        busy = false
                        onPdf(f.absolutePath, tr("تقرير الفترة"))
                    }
                },
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Text("  " + tr("سحب تقرير الفترة"))
            }
            OutlinedButton(
                enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                onClick = {
                    busy = true
                    scope.launch {
                        val shop = dao.shopNow(shopId)
                        val products = dao.productsNow(shopId)
                        val balance = dao.balance(shopId).first()
                        val debt = dao.totalDebt(shopId).first()
                        val f = withContext(Dispatchers.IO) {
                            ReportPdf.stock(context, shop?.name ?: "", products, balance, debt)
                        }
                        busy = false
                        onPdf(f.absolutePath, tr("تقرير المخزون والوضع الراهن"))
                    }
                },
            ) {
                Icon(Icons.Default.Inventory2, contentDescription = null)
                Text("  " + tr("سحب تقرير المخزون والوضع الراهن"))
            }
            if (busy) CircularProgressIndicator()
        }
    }
}

@Composable
private fun Line(label: String, value: Long, color: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(Money.format(value), color = color, fontWeight = FontWeight.Bold)
    }
}
