package com.elyedali.makhzan.ui

import androidx.compose.foundation.layout.Arrangement
import com.elyedali.makhzan.data.TrustedClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.People
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elyedali.makhzan.Route
import com.elyedali.makhzan.ads.AdBanner
import com.elyedali.makhzan.activation.Activation
import com.elyedali.makhzan.i18n.tr
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
    var showActivation by remember { mutableStateOf(false) }

    // قراءة واحدة لحالة التفعيل من الخادم عند أول فتح للمحل في هذه الجلسة
    LaunchedEffect(shopId) {
        if (Activation.markRefreshed(shopId)) dao.shopNow(shopId)?.let { Activation.refresh(dao, it) }
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val now = TrustedClock.now()

    Scaffold(
        topBar = { AppTopBar(shop?.name ?: "", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { AdBanner(dao) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // بطاقة الرصيد
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(tr("رصيد الصندوق"), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        Money.format(balance),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (balance < 0) Color(0xFFFFCDD2) else Color.White,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniStat(tr("مبيعات اليوم"), Money.format(salesToday), Modifier.weight(1f))
                        MiniStat(tr("ربح اليوم"), Money.format(profitToday), Modifier.weight(1f))
                    }
                }
            }

            // حالة الباقة
            shop?.let {
                val locked = Plans.isLocked(it, count, now)
                val soon = Plans.daysLeftIfSoon(it, now)
                val (bg, fg) = when {
                    locked -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
                    soon != null -> Color(0xFFFFF3CD) to Color(0xFF8A5A00)
                    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Card(
                    onClick = { showActivation = true },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = bg),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (locked) Icons.Default.Lock else Icons.Default.Verified,
                            contentDescription = null, tint = fg,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                tr("المنتجات: %1\$s من %2\$s", count, Plans.limitText(it, now)),
                                fontWeight = FontWeight.SemiBold, color = fg,
                            )
                            Text(
                                when {
                                    locked -> tr("انتهى التفعيل: البيع والشراء متوقفان حتى التجديد. بياناتك محفوظة كاملة.")
                                    soon != null -> tr("ينتهي التفعيل بعد %1\$s يوم", soon)
                                    else -> Plans.label(it, now)
                                },
                                style = MaterialTheme.typography.bodySmall, color = fg,
                            )
                        }
                        Text(
                            if (Plans.isActive(it, now)) tr("تجديد / ترقية") else tr("تفعيل"),
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // العمليات الرئيسية
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigAction(tr("بيع"), Icons.Default.QrCodeScanner, Color(0xFF0B6E4F), Modifier.weight(1f)) {
                    if (shop?.let { Plans.isLocked(it, count, TrustedClock.now()) } == true) showActivation = true
                    else go(Route.Sale(shopId))
                }
                BigAction(tr("شراء"), Icons.Default.AddShoppingCart, Color(0xFF1565C0), Modifier.weight(1f)) {
                    if (shop?.let { Plans.isLocked(it, count, TrustedClock.now()) } == true) showActivation = true
                    else go(Route.Purchase(shopId))
                }
            }

            // باقي الأقسام
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallAction(tr("مصروف"), Icons.Default.MoneyOff, Color(0xFFC62828), Modifier.weight(1f)) { showExpense = true }
                SmallAction(tr("الصندوق"), Icons.Default.AccountBalanceWallet, Color(0xFF8A5A00), Modifier.weight(1f)) {
                    go(Route.Cash(shopId))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallAction(tr("المخزون"), Icons.Default.Inventory2, Color(0xFF6A1B9A), Modifier.weight(1f)) {
                    go(Route.Stock(shopId))
                }
                SmallAction(tr("الديون"), Icons.Default.People, Color(0xFFB45309), Modifier.weight(1f)) {
                    go(Route.Customers(shopId))
                }
            }
            SmallAction(tr("الإعدادات"), Icons.Default.Settings, Color(0xFF455A64), Modifier.fillMaxWidth()) {
                go(Route.Settings(shopId))
            }
        }
    }

    val s = shop
    if (showActivation && s != null) {
        ActivationDialog(dao, s, count, onDismiss = { showActivation = false })
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
                            note = note, time = TrustedClock.now(),
                        )
                    )
                    snackbar.showSnackbar(tr("تم تسجيل المصروف"))
                }
            },
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun BigAction(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.height(128.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SmallAction(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.height(76.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).background(color, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(10.dp))
            Text(label, fontWeight = FontWeight.SemiBold, color = color, textAlign = TextAlign.Start)
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
        title = { Text(tr("تسجيل مصروف")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(amount, { amount = it }, tr("المبلغ"))
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text(tr("البيان (مثال: كهرباء، كراء…)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(parsed!!, note.trim()) }) { Text(tr("حفظ")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء")) } },
    )
}
