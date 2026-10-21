package com.elyedali.makhzan.ui

import androidx.compose.material3.AlertDialog
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

private data class SaleCartLine(val product: Product, val qty: Int)

@Composable
fun SaleScreen(dao: ShopDao, shopId: Long, onBack: () -> Unit, onInvoice: (Long) -> Unit) {
    val cart = remember { mutableStateListOf<SaleCartLine>() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    var confirmLeave by remember { mutableStateOf(false) }
    fun tryBack() { if (cart.isNotEmpty()) confirmLeave = true else onBack() }
    BackHandler(enabled = cart.isNotEmpty()) { confirmLeave = true }

    fun toast(msg: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    fun changeQty(product: Product, delta: Int) {
        val i = cart.indexOfFirst { it.product.id == product.id }
        val current = if (i >= 0) cart[i].qty else 0
        val newQty = current + delta
        when {
            newQty <= 0 -> if (i >= 0) cart.removeAt(i)
            newQty > product.qty -> toast(tr("الكمية المتوفرة من «%1\$s» هي %2\$s فقط", product.name, product.qty))
            i >= 0 -> cart[i] = cart[i].copy(product = product, qty = newQty)
            else -> cart.add(0, SaleCartLine(product, newQty))
        }
    }

    val total = cart.sumOf { it.product.sellPrice * it.qty }

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
                    Column {
                        Text(tr("المجموع"), style = MaterialTheme.typography.bodySmall)
                        Text(
                            Money.format(total), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Button(
                        enabled = cart.isNotEmpty() && !busy,
                        modifier = Modifier.height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        onClick = {
                            busy = true
                            val items = cart.map {
                                SaleItem(
                                    productId = it.product.id, name = it.product.name,
                                    barcode = it.product.barcode, qty = it.qty,
                                    unitPrice = it.product.sellPrice, unitCost = it.product.buyPrice,
                                )
                            }
                            scope.launch {
                                try {
                                    val opId = dao.recordSale(shopId, items, System.currentTimeMillis())
                                    cart.clear()
                                    busy = false
                                    snackbar.currentSnackbarData?.dismiss()
                                    val r = snackbar.showSnackbar(
                                        message = tr("تم تسجيل البيع: %1\$s", Money.format(total)),
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
                        },
                    ) { Text(tr("تأكيد البيع"), style = MaterialTheme.typography.titleMedium) }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp)) {
            BarcodeScannerView(
                modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = 12.dp),
                paused = busy,
                onCode = { code ->
                    scope.launch {
                        val p = dao.findByBarcode(shopId, code)
                        if (p == null) toast(tr("هذا المنتج غير مسجل. سجّله أولًا من شاشة الشراء."))
                        else changeQty(p, +1)
                    }
                },
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("السلة (%1\$s)", cart.sumOf { it.qty }), style = MaterialTheme.typography.titleMedium)
                if (cart.isNotEmpty()) TextButton(onClick = { cart.clear() }) { Text(tr("إفراغ السلة")) }
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
                        CartRow(
                            name = line.product.name,
                            subtitle = "${Money.format(line.product.sellPrice)} × ${line.qty}",
                            lineTotal = Money.format(line.product.sellPrice * line.qty),
                            qty = line.qty,
                            onPlus = { changeQty(line.product, +1) },
                            onMinus = { changeQty(line.product, -1) },
                        )
                    }
                }
            }
        }
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
    qty: Int,
    onPlus: (() -> Unit)?,
    onMinus: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                Text(lineTotal, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onMinus) { Icon(Icons.Default.Remove, contentDescription = tr("إنقاص")) }
            Text("$qty", style = MaterialTheme.typography.titleMedium)
            if (onPlus != null) {
                IconButton(onClick = onPlus) { Icon(Icons.Default.Add, contentDescription = tr("زيادة")) }
            }
        }
    }
}
