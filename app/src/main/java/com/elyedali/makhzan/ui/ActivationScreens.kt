package com.elyedali.makhzan.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.elyedali.makhzan.activation.Activation
import com.elyedali.makhzan.activation.ActivationRequest
import com.elyedali.makhzan.auth.Auth
import com.elyedali.makhzan.billing.PlayBilling
import com.elyedali.makhzan.data.Dates
import com.elyedali.makhzan.data.Plans
import com.elyedali.makhzan.data.Shop
import com.elyedali.makhzan.data.ShopDao
import com.elyedali.makhzan.i18n.tr
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * نافذة تفعيل المحل: اختيار الباقة، إرسال الطلب عبر واتساب،
 * والاستماع للتفعيل حتى ينفتح المحل تلقائيًا فور تفعيله.
 */
@Composable
fun ActivationDialog(dao: ShopDao, shop: Shop, productCount: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val needed = Plans.neededTier(productCount)
    var tier by remember { mutableStateOf(maxOf(needed, if (Plans.isActive(shop, System.currentTimeMillis())) shop.tier else 1)) }
    var current by remember { mutableStateOf(shop) }
    var checking by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var paying by remember { mutableStateOf(false) }
    val activity = context as Activity
    val mauritania = remember { Activation.isMauritania(context) }
    LaunchedEffect(Unit) {
        PlayBilling.status = null
        PlayBilling.loadPrices()
    }

    // ضمان وجود معرّف المحل على الخادم
    LaunchedEffect(shop.id) {
        if (current.remoteId.isBlank()) {
            val rid = UUID.randomUUID().toString()
            dao.setRemoteId(shop.id, rid)
            current = current.copy(remoteId = rid)
        }
    }

    // الاستماع للتفعيل ما دامت النافذة مفتوحة: يُفتح المحل فور تفعيله
    DisposableEffect(current.remoteId) {
        val reg = if (current.remoteId.isBlank()) null else Activation.listen(current) { t, exp ->
            if (t != current.tier || exp != current.expiresAt) {
                scope.launch {
                    dao.updateActivation(current.id, t, exp)
                    current = current.copy(tier = t, expiresAt = exp)
                    if (exp > System.currentTimeMillis()) {
                        info = tr("تم تفعيل المحل حتى %1\$s", Dates.formatDay(exp))
                    }
                }
            }
        }
        onDispose { reg?.remove() }
    }

    val now = System.currentTimeMillis()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("تفعيل المحل: %1\$s", current.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("الحالة: %1\$s", Plans.label(current, now)), fontWeight = FontWeight.SemiBold)
                Text(tr("عدد المنتجات الحالي: %1\$s", productCount), style = MaterialTheme.typography.bodySmall)
                Text(tr("اختر الباقة (مدة كل باقة %1\$s أشهر):", Activation.MONTHS))
                Activation.plans.forEach { p ->
                    val enabled = p.tier >= needed
                    Card(
                        Modifier.fillMaxWidth().selectable(selected = tier == p.tier, enabled = enabled) { tier = p.tier },
                        colors = CardDefaults.cardColors(
                            containerColor = if (tier == p.tier) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = tier == p.tier, enabled = enabled, onClick = { tier = p.tier })
                            Column {
                                Text(tr(p.label), fontWeight = FontWeight.SemiBold)
                                Text(
                                    PlayBilling.prices[p.tier]?.takeIf { !mauritania } ?: tr("%1\$s أوقية", p.price),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                // طرق الدفع
                Button(
                    enabled = current.remoteId.isNotBlank() && PlayBilling.prices[tier] != null && !paying,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        paying = true
                        info = null
                        scope.launch {
                            if (!PlayBilling.buy(activity, tier, current.remoteId)) {
                                info = tr("الدفع بالبطاقة غير متاح الآن. تحقق من الإنترنت ومن متجر Google Play.")
                            }
                            paying = false
                        }
                    },
                ) {
                    Text(
                        PlayBilling.prices[tier]?.let { tr("الدفع بالبطاقة البنكية — %1\$s", it) }
                            ?: tr("الدفع بالبطاقة البنكية")
                    )
                }
                if (mauritania) {
                    OutlinedButton(
                        enabled = current.remoteId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            try {
                                context.startActivity(Activation.whatsappIntent(Activation.buildMessage(current, tier)))
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, tr("تطبيق واتساب غير موجود على الهاتف"), Toast.LENGTH_LONG).show()
                            }
                        },
                    ) { Text(tr("الدفع عبر واتساب")) }
                }
                Text(
                    tr("فور الدفع يُفتح المحل تلقائيًا على هذا الهاتف."),
                    style = MaterialTheme.typography.bodySmall,
                )
                when (val st = PlayBilling.status) {
                    "PENDING" -> Text(tr("الدفع قيد المعالجة. سيُفتح المحل تلقائيًا عند اكتماله."), color = Color(0xFF8A5A00))
                    null -> Unit
                    else -> if (st.startsWith("ERROR:")) {
                        Text(tr("تعذر إتمام الدفع: %1\$s", st.removePrefix("ERROR:")), color = MaterialTheme.colorScheme.error)
                    }
                }
                if (checking || paying) CircularProgressIndicator()
                info?.let { Text(it, color = Color(0xFF0B6E4F), fontWeight = FontWeight.Bold) }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row {
                TextButton(enabled = !checking, onClick = {
                    checking = true
                    info = null
                    scope.launch {
                        val ok = Activation.refresh(dao, current)
                        current = dao.shopNow(current.id) ?: current
                        info = if (!ok) tr("تعذر الاتصال. تحقق من الإنترنت.")
                        else if (Plans.isActive(current, System.currentTimeMillis()))
                            tr("تم تفعيل المحل حتى %1\$s", Dates.formatDay(current.expiresAt))
                        else tr("لم يُفعَّل المحل بعد")
                        checking = false
                    }
                }) { Text(tr("تحقق")) }
                TextButton(onClick = onDismiss) { Text(tr("إغلاق")) }
            }
        },
    )
}

/** شاشة المسؤول: تُفتح من رابط التفعيل الذي يصله عبر واتساب */
@Composable
fun AdminActivateScreen(req: ActivationRequest, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val isAdmin = Auth.isAdmin()
    val plan = Activation.plan(req.tier)

    LaunchedEffect(req) {
        if (isAdmin) {
            try { state = Activation.adminRead(req) } catch (e: Exception) { error = tr("تعذر الاتصال. تحقق من الإنترنت.") }
        }
        loading = false
    }

    Scaffold(topBar = { AppTopBar(tr("تفعيل محل"), onBack) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            if (!isAdmin) {
                Text(tr("هذا الرابط خاص بمسؤول التفعيل."), modifier = Modifier.align(Alignment.Center))
                return@Box
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tr("المحل: %1\$s", req.shopName), fontWeight = FontWeight.Bold)
                        Text(tr("صاحب الحساب: %1\$s", req.ownerEmail))
                        Text(tr("الباقة المطلوبة: %1\$s", tr(plan?.label ?: "")))
                        Text(tr("المبلغ: %1\$s أوقية لمدة %2\$s أشهر", plan?.price ?: 0, Activation.MONTHS), fontWeight = FontWeight.SemiBold)
                        if (loading) CircularProgressIndicator()
                        state?.let { (t, exp) ->
                            Text(
                                if (t > 0 && exp > System.currentTimeMillis())
                                    tr("الحالة الحالية: مفعّل (%1\$s) حتى %2\$s", tr(Activation.plan(t)?.label ?: ""), Dates.formatDay(exp))
                                else tr("الحالة الحالية: غير مفعّل"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Button(
                    enabled = !busy && !loading && result == null,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                val exp = Activation.adminActivate(req)
                                result = tr("تم التفعيل حتى %1\$s", Dates.formatDay(exp))
                            } catch (e: Exception) {
                                error = tr("فشل التفعيل: %1\$s", e.message ?: "")
                            }
                            busy = false
                        }
                    },
                ) { Text(tr("تأكيد الدفع وتفعيل المحل")) }
                if (busy) CircularProgressIndicator()
                result?.let { Text(it, color = Color(0xFF0B6E4F), fontWeight = FontWeight.Bold) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
