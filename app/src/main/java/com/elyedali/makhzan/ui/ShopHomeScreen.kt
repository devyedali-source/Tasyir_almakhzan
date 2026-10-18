package com.elyedali.makhzan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elyedali.makhzan.Route
import com.elyedali.makhzan.data.Dates
import com.elyedali.makhzan.data.Money
import com.elyedali.makhzan.data.OpType
import com.elyedali.makhzan.data.Operation
import com.elyedali.makhzan.data.Plans
import com.elyedali.makhzan.data.ShopDao
import kotlinx.coroutines.launch

@Composable
fun ShopHomeScreen(dao: ShopDao, shopId: Long, onBack: () -> Unit, go: (Route) -> Unit) {
    val shop by dao.shop(shopId).collectAsState(initial = null)
    val today = remember { Dates.startOfToday() }
    val balance by dao.balance(shopId).collectAsState(initial = 0L)
    val salesToday by dao.sumType(shopId, OpType.SALE, today).collectAsState(initial = 0L)
    val profitToday by dao.profit(shopId, today).collectAsState(initial = 0L)
    val count by dao.productCountFlow(shopId).collectAsState(initial = 0)
    var showExpense by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val now = System.currentTimeMillis()

    Scaffold(
        topBar = { AppTopBar(shop?.name ?: "", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("رصيد الصندوق", style = MaterialTheme.typography.titleSmall)
                    Text(
                        Money.format(balance),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("مبيعات اليوم", style = MaterialTheme.typography.bodySmall)
                            Text(Money.format(salesToday), fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("ربح اليوم", style = MaterialTheme.typography.bodySmall)
                            Text(Money.format(profitToday), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            shop?.let {
                Text(
                    "المنتجات: $count من ${Plans.limitText(it, now)} — ${Plans.label(it, now)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile("بيع", Icons.Default.PointOfSale, Color(0xFF0B6E4F), Modifier.weight(1f)) {
                    go(Route.Sale(shopId))
                }
                ActionTile("شراء (إدخال بضاعة)", Icons.Default.AddShoppingCart, Color(0xFF1565C0), Modifier.weight(1f)) {
                    go(Route.Purchase(shopId))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile("مصاريف", Icons.Default.MoneyOff, Color(0xFFC62828), Modifier.weight(1f)) {
                    showExpense = true
                }
                ActionTile("الصندوق", Icons.Default.AccountBalanceWallet, Color(0xFF8A5A00), Modifier.weight(1f)) {
                    go(Route.Cash(shopId))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile("المخزون", Icons.Default.Inventory2, Color(0xFF6A1B9A), Modifier.weight(1f)) {
                    go(Route.Stock(shopId))
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }

    if (showExpense) {
        ExpenseDialog(
            onDismiss = { showExpense = false },
            onConfirm = { amount, note ->
                showExpense = false
                scope.launch {
                    dao.insertOperation(
                        Operation(
                            shopId = shopId, type = OpType.EXPENSE, total = amount,
                            note = note, time = System.currentTimeMillis(),
                        )
                    )
                    snackbar.showSnackbar("تم تسجيل المصروف")
                }
            },
        )
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(110.dp),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ExpenseDialog(onDismiss: () -> Unit, onConfirm: (Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val parsed = Money.parse(amount)
    val valid = parsed != null && parsed > 0 && note.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل مصروف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(amount, { amount = it }, "المبلغ")
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("البيان (مثال: كهرباء، كراء…)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(parsed!!, note.trim()) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
