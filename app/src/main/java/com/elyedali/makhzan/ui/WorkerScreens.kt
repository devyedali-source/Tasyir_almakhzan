package com.elyedali.makhzan.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elyedali.makhzan.auth.Auth
import com.elyedali.makhzan.data.Money
import com.elyedali.makhzan.data.Plans
import com.elyedali.makhzan.data.Prefs
import com.elyedali.makhzan.data.ShopDao
import com.elyedali.makhzan.data.TrustedClock
import com.elyedali.makhzan.data.Worker
import com.elyedali.makhzan.i18n.tr
import com.elyedali.makhzan.security.Pins
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(8)) },
        label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier.fillMaxWidth(),
    )
}

// ======================= إدارة العمال (لصاحب المحل) =======================

@Composable
fun WorkersScreen(dao: ShopDao, shopId: Long, onBack: () -> Unit, onEnterWorkerMode: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workers by dao.workers(shopId).collectAsState(initial = emptyList())
    var ownerPinSet by remember { mutableStateOf(Prefs.ownerPinHash(context) != null) }
    var showOwnerPin by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var pinFor by remember { mutableStateOf<Worker?>(null) }
    var deleteFor by remember { mutableStateOf<Worker?>(null) }
    var confirmMode by remember { mutableStateOf(false) }

    Scaffold(topBar = { AppTopBar(tr("العمال ووضع البائع"), onBack) }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    tr("في وضع البائع يبيع العامل برمزه الخاص، وتُسجَّل مبيعاته باسمه، ولا يرى الصندوق ولا الأرباح ولا أسعار الشراء ولا التقارير ولا الإعدادات."),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("رمز صاحب المحل"), fontWeight = FontWeight.SemiBold)
                            Text(
                                if (ownerPinSet) tr("محدَّد — يلزم للخروج من وضع البائع") else tr("غير محدَّد بعد"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { showOwnerPin = true }) { Text(if (ownerPinSet) tr("تغيير") else tr("تحديد")) }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("العمال"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showAdd = true }) { Text(tr("إضافة عامل")) }
                }
            }
            if (workers.isEmpty()) item { Text(tr("لا يوجد عمال بعد")) }
            items(workers, key = { it.id }) { w ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(10.dp))
                            Text(w.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { pinFor = w }) { Text(tr("تغيير الرمز")) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("يسمح له بالبيع بالدَّين"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Switch(checked = w.canCredit, onCheckedChange = { v -> scope.launch { dao.setWorkerCredit(w.id, v) } })
                        }
                        TextButton(onClick = { deleteFor = w }) {
                            Text(tr("حذف العامل"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = ownerPinSet && workers.isNotEmpty(),
                    onClick = { confirmMode = true },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(tr("تسليم المحل للعمال (وضع البائع)"), fontWeight = FontWeight.Bold) }
                if (!ownerPinSet || workers.isEmpty()) {
                    Text(
                        tr("حدّد رمزك وأضف عاملًا واحدًا على الأقل أولًا."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showOwnerPin) {
        OwnerPinDialog(
            requireCurrent = ownerPinSet,
            onDismiss = { showOwnerPin = false },
            onSaved = { showOwnerPin = false; ownerPinSet = true },
        )
    }
    if (showAdd) {
        WorkerFormDialog(dao, shopId, existing = null, onDismiss = { showAdd = false })
    }
    pinFor?.let { w -> WorkerFormDialog(dao, shopId, existing = w, onDismiss = { pinFor = null }) }
    deleteFor?.let { w ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text(tr("حذف العامل")) },
            text = { Text(tr("سيُحذف «%1\$s» ولن يستطيع الدخول. مبيعاته السابقة تبقى في السجل.", w.name)) },
            confirmButton = {
                TextButton(onClick = { deleteFor = null; scope.launch { dao.deleteWorker(w.id) } }) {
                    Text(tr("حذف"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text(tr("إلغاء")) } },
        )
    }
    if (confirmMode) {
        AlertDialog(
            onDismissRequest = { confirmMode = false },
            title = { Text(tr("تسليم المحل للعمال")) },
            text = {
                Text(tr("سيُقفل التطبيق على شاشة البيع لهذا المحل فقط. يدخل كل عامل برمزه، ولا يُفتح باقي التطبيق إلا برمزك."))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmMode = false
                    Prefs.setWorkerModeShop(context, shopId)
                    onEnterWorkerMode()
                }) { Text(tr("تسليم")) }
            },
            dismissButton = { TextButton(onClick = { confirmMode = false }) { Text(tr("إلغاء")) } },
        )
    }
}

@Composable
private fun OwnerPinDialog(requireCurrent: Boolean, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("رمز صاحب المحل")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (requireCurrent) PinField(current, { current = it }, tr("الرمز الحالي"))
                PinField(pin, { pin = it }, tr("الرمز الجديد (4 إلى 8 أرقام)"))
                PinField(confirm, { confirm = it }, tr("تأكيد الرمز"))
                error?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    requireCurrent && !Pins.matches(current, Prefs.ownerPinHash(context)) -> tr("الرمز الحالي غير صحيح")
                    !Pins.isValid(pin) -> tr("الرمز يجب أن يكون من 4 إلى 8 أرقام")
                    pin != confirm -> tr("الرمزان غير متطابقين")
                    else -> null
                }
                if (error == null) {
                    Prefs.setOwnerPinHash(context, Pins.hash(pin))
                    onSaved()
                }
            }) { Text(tr("حفظ")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء")) } },
    )
}

/** إضافة عامل جديد، أو تغيير رمز عامل موجود */
@Composable
private fun WorkerFormDialog(dao: ShopDao, shopId: Long, existing: Worker?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var canCredit by remember { mutableStateOf(existing?.canCredit ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) tr("إضافة عامل") else tr("تغيير رمز %1\$s", existing.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existing == null) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        label = { Text(tr("اسم العامل")) }, modifier = Modifier.fillMaxWidth(),
                    )
                }
                PinField(pin, { pin = it }, tr("رمز العامل (4 إلى 8 أرقام)"))
                PinField(confirm, { confirm = it }, tr("تأكيد الرمز"))
                if (existing == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canCredit, onCheckedChange = { canCredit = it })
                        Text(tr("يسمح له بالبيع بالدَّين"))
                    }
                }
                error?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    // كل رمز يجب أن يكون فريدًا حتى يُعرف صاحبه عند الدخول
                    val others = dao.workersNow(shopId).filter { it.id != existing?.id }
                    error = when {
                        existing == null && name.isBlank() -> tr("اسم العامل مطلوب")
                        !Pins.isValid(pin) -> tr("الرمز يجب أن يكون من 4 إلى 8 أرقام")
                        pin != confirm -> tr("الرمزان غير متطابقين")
                        Pins.matches(pin, Prefs.ownerPinHash(context)) || others.any { Pins.matches(pin, it.pinHash) } ->
                            tr("هذا الرمز مستعمل، اختر رمزًا آخر")
                        else -> null
                    }
                    if (error == null) {
                        if (existing == null) {
                            dao.insertWorker(
                                Worker(
                                    shopId = shopId, name = name.trim(), pinHash = Pins.hash(pin),
                                    canCredit = canCredit, createdAt = TrustedClock.now(),
                                )
                            )
                        } else {
                            dao.setWorkerPin(existing.id, Pins.hash(pin))
                        }
                        onDismiss()
                    }
                }
            }) { Text(tr("حفظ")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء")) } },
    )
}

// ======================= وضع البائع =======================

private sealed interface WRoute {
    data object Lock : WRoute
    data object Home : WRoute
    data object Sale : WRoute
    data class Invoice(val opId: Long) : WRoute
}

/**
 * التطبيق مقفل على محل واحد: شاشة رمز، ثم صفحة العامل والبيع فقط.
 * رمز صاحب المحل وحده يعيد التطبيق إلى وضعه الكامل.
 */
@Composable
fun WorkerRoot(dao: ShopDao, shopId: Long, onExit: () -> Unit) {
    var route by remember { mutableStateOf<WRoute>(WRoute.Lock) }
    var worker by remember { mutableStateOf<Worker?>(null) }

    BackHandler(enabled = route is WRoute.Sale || route is WRoute.Invoice) {
        route = if (route is WRoute.Invoice) WRoute.Sale else WRoute.Home
    }

    when (val r = route) {
        WRoute.Lock -> WorkerLockScreen(
            dao, shopId,
            onWorker = { w -> worker = w; route = WRoute.Home },
            onOwner = onExit,
        )
        WRoute.Home -> worker?.let { w ->
            WorkerHomeScreen(
                dao, shopId, w,
                onSell = { route = WRoute.Sale },
                onEndShift = { worker = null; route = WRoute.Lock },
            )
        }
        WRoute.Sale -> worker?.let { w ->
            SaleScreen(
                dao, shopId,
                onBack = { route = WRoute.Home },
                onInvoice = { route = WRoute.Invoice(it) },
                workerId = w.id, allowCredit = w.canCredit,
            )
        }
        is WRoute.Invoice -> InvoiceScreen(dao, shopId, r.opId, onBack = { route = WRoute.Sale })
    }
}

@Composable
private fun WorkerLockScreen(dao: ShopDao, shopId: Long, onWorker: (Worker) -> Unit, onOwner: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val shop by dao.shop(shopId).collectAsState(initial = null)
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var failures by remember { mutableStateOf(0) }
    var waitUntil by remember { mutableStateOf(0L) }
    var busy by remember { mutableStateOf(false) }

    // حماية من التخمين: بعد 5 محاولات خاطئة انتظار 30 ثانية
    LaunchedEffect(waitUntil) {
        if (waitUntil > 0) {
            delay(waitUntil - System.currentTimeMillis())
            waitUntil = 0
            error = null
        }
    }

    fun submit() {
        if (waitUntil > 0 || pin.isEmpty()) return
        scope.launch {
            if (Pins.matches(pin, Prefs.ownerPinHash(context))) {
                pin = ""
                onOwner()
                return@launch
            }
            val w = dao.workersNow(shopId).firstOrNull { Pins.matches(pin, it.pinHash) }
            pin = ""
            if (w != null) {
                failures = 0
                if (Prefs.shiftStart(context, w.id) == 0L) Prefs.setShiftStart(context, w.id, TrustedClock.now())
                onWorker(w)
            } else {
                failures++
                if (failures >= 5) {
                    failures = 0
                    waitUntil = System.currentTimeMillis() + 30_000
                    error = tr("محاولات كثيرة خاطئة. انتظر 30 ثانية.")
                } else error = tr("الرمز غير صحيح")
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary).systemBarsPadding().padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            Text(shop?.name ?: "", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(tr("أدخل رمزك للبدء"), color = Color.White.copy(alpha = 0.9f))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PinField(pin, { pin = it; error = null }, tr("الرمز"))
                    Button(
                        enabled = waitUntil == 0L && pin.length >= 4,
                        onClick = { submit() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(tr("دخول"), fontWeight = FontWeight.Bold) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            if (busy) CircularProgressIndicator(color = Color.White)
            TextButton(onClick = {
                busy = true
                scope.launch {
                    // نسيان رمز صاحب المحل: يكفي إثبات ملكية حساب Google نفسه
                    if (Auth.verifyOwnerAccount(activity)) {
                        Prefs.setOwnerPinHash(context, null)
                        onOwner()
                    } else error = tr("لم يتم التحقق من حساب صاحب المحل")
                    busy = false
                }
            }) { Text(tr("نسيت رمز صاحب المحل؟"), color = Color.White) }
        }
    }
}

@Composable
private fun WorkerHomeScreen(dao: ShopDao, shopId: Long, worker: Worker, onSell: () -> Unit, onEndShift: () -> Unit) {
    val context = LocalContext.current
    val shop by dao.shop(shopId).collectAsState(initial = null)
    val count by dao.productCountFlow(shopId).collectAsState(initial = 0)
    val start = remember(worker.id) { Prefs.shiftStart(context, worker.id) }
    val sales by dao.workerSaleCount(worker.id, start).collectAsState(initial = 0)
    val cash by dao.workerCashSales(worker.id, start).collectAsState(initial = 0L)
    val credit by dao.workerCreditSales(worker.id, start).collectAsState(initial = 0L)
    var confirmEnd by remember { mutableStateOf(false) }
    val locked = shop?.let { Plans.isLocked(it, count, TrustedClock.now()) } == true

    Scaffold(topBar = { AppTopBar(shop?.name ?: "") }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(tr("مرحبًا %1\$s", worker.name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("ورديتي"), fontWeight = FontWeight.SemiBold)
                    Text(tr("عدد المبيعات: %1\$s", sales))
                    Text(tr("نقدًا (يُسلَّم لصاحب المحل): %1\$s", Money.format(cash)), fontWeight = FontWeight.Bold)
                    if (worker.canCredit) Text(tr("بالدَّين: %1\$s", Money.format(credit)))
                }
            }
            if (locked) {
                Text(
                    tr("البيع متوقف: يحتاج المحل إلى تفعيل. أبلغ صاحب المحل."),
                    color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                )
            }
            Button(
                enabled = !locked,
                onClick = onSell,
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B6E4F)),
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.size(10.dp))
                Text(tr("بيع"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { confirmEnd = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(tr("إنهاء ورديتي")) }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(tr("إنهاء الوردية")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("عدد المبيعات: %1\$s", sales))
                    Text(tr("نقدًا (يُسلَّم لصاحب المحل): %1\$s", Money.format(cash)), fontWeight = FontWeight.Bold)
                    if (worker.canCredit) Text(tr("بالدَّين: %1\$s", Money.format(credit)))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    Prefs.setShiftStart(context, worker.id, 0L)
                    onEndShift()
                }) { Text(tr("إنهاء")) }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(tr("إلغاء")) } },
        )
    }
}
