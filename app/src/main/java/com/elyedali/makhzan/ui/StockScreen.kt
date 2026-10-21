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
import com.elyedali.makhzan.data.Dates
import com.elyedali.makhzan.data.Money
import com.elyedali.makhzan.data.OpType
import com.elyedali.makhzan.data.ShopDao
import kotlinx.coroutines.launch
import com.elyedali.makhzan.i18n.tr

@Composable
fun StockScreen(dao: ShopDao, shopId: Long, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val products by dao.products(shopId).collectAsState(initial = emptyList())
    val value by dao.stockValue(shopId).collectAsState(initial = 0L)
    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) products else products.filter {
        it.name.contains(query.trim(), ignoreCase = true) || it.barcode.contains(query.trim())
    }

    Scaffold(topBar = { AppTopBar(tr("المخزون"), onBack) }) { pad ->
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
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text(tr("بحث بالاسم")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (products.isEmpty()) {
                item { Text(tr("لا توجد منتجات بعد. أدخل البضاعة من شاشة الشراء.")) }
            }
            items(shown, key = { it.id }) { p ->
                Card(onClick = { onOpen(p.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall)
                            Text(tr("سعر البيع: %1\$s", Money.format(p.sellPrice)), style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${p.qty}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                color = if (p.qty <= 0) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(tr("الكمية"), style = MaterialTheme.typography.bodySmall)
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
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { AppTopBar(product?.name ?: "", onBack) }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            product?.let { p ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tr("الكود: %1\$s", p.barcode))
                            Text(tr("الكمية في المخزون: %1\$s", p.qty), fontWeight = FontWeight.Bold)
                            Text(tr("سعر الشراء: %1\$s", Money.format(p.buyPrice)))
                            Text(tr("سعر البيع: %1\$s", Money.format(p.sellPrice)))
                            OutlinedButton(onClick = { editing = true }) { Text(tr("تعديل الاسم وسعر البيع")) }
                            if (p.qty == 0) {
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
                val isIn = m.type == OpType.PURCHASE
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isIn) tr("دخول (شراء)") else tr("خروج (بيع)"),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                tr("%1\$s — %2\$s للوحدة", Dates.format(m.time), Money.format(m.line.unitPrice)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            (if (isIn) "+" else "−") + m.line.qty,
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
        val sellV = Money.parse(sell)
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
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && sellV != null, onClick = {
                    editing = false
                    scope.launch { dao.updateNameAndSellPrice(p.id, name.trim(), sellV!!) }
                }) { Text(tr("حفظ")) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(tr("إلغاء")) } },
        )
    }
}
