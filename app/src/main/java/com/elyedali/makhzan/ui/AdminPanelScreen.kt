package com.elyedali.makhzan.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elyedali.makhzan.activation.Activation
import com.elyedali.makhzan.activation.ActivationRequest
import com.elyedali.makhzan.auth.Auth
import com.elyedali.makhzan.i18n.tr
import com.elyedali.makhzan.data.DemoData
import com.elyedali.makhzan.data.ShopDao
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * لوحة مدير التطبيق (للحساب المسؤول فقط): أرقام فقط، بعمليات عدّ رخيصة على الخادم،
 * وزر التفعيل من رابط واتساب منسوخ.
 */
@Composable
fun AdminPanelScreen(dao: ShopDao, onBack: () -> Unit, onActivate: (ActivationRequest) -> Unit) {
    val demo by dao.demoShop().collectAsState(initial = null)
    var demoBusy by remember { mutableStateOf(false) }
    var confirmDeleteDemo by remember { mutableStateOf(false) }
    var demoMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<Activation.AdminStats?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(refreshKey) {
        if (!Auth.isAdmin()) return@LaunchedEffect
        loading = true
        error = null
        try {
            stats = Activation.adminStats()
        } catch (e: Exception) {
            error = tr("تعذر جلب الإحصائيات: %1\$s", e.message ?: "")
        }
        loading = false
    }

    Scaffold(
        topBar = {
            AppTopBar(tr("لوحة المدير"), onBack, actions = {
                IconButton(onClick = { refreshKey++ }) { Icon(Icons.Default.Refresh, contentDescription = tr("تحديث")) }
            })
        },
    ) { pad ->
        if (!Auth.isAdmin()) {
            Text(tr("هذه الصفحة خاصة بمدير التطبيق."), modifier = Modifier.padding(pad).padding(24.dp))
            return@Scaffold
        }
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                onClick = {
                    val text = clipboard.getText()?.text.orEmpty()
                    val url = Regex("https://stockcaisse\\.web\\.app/activate\\S+").find(text)?.value
                    val req = url?.let { Activation.parse(Uri.parse(it)) }
                    if (req != null) onActivate(req) else error = tr("انسخ رسالة طلب التفعيل أولًا ثم أعد المحاولة")
                },
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null)
                Text("  " + tr("تفعيل من رابط منسوخ"))
            }

            // محل تجريبي ببيانات افتراضية لشهر كامل (يبقى على هذا الهاتف فقط ولا يُرسل إلى الخادم)
            SectionLabel(tr("المحل التجريبي"))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr("محل فيه بيانات افتراضية لشهر كامل: منتجات، مبيعات، ديون، موردون، مصاريف، مرتجع وتالف — لتجربة كل الشاشات والتقارير."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val d = demo
                    if (demoBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                            Text("  " + tr("جارٍ التحضير…"))
                        }
                    } else if (d == null) {
                        OutlinedButton(onClick = {
                            demoBusy = true
                            scope.launch {
                                demoMessage = try {
                                    DemoData.create(dao)
                                    tr("تم إنشاء «محل تجريبي». ستجده في قائمة المحلات.")
                                } catch (e: Exception) {
                                    tr("تعذر الإنشاء: %1\$s", e.message ?: "")
                                }
                                demoBusy = false
                            }
                        }) { Text(tr("إنشاء محل تجريبي")) }
                    } else {
                        Text(tr("موجود: %1\$s", d.name), fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { confirmDeleteDemo = true }) {
                            Text(tr("حذف المحل التجريبي"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    demoMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }

            if (loading) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            stats?.let { s ->
                SectionLabel(tr("المستخدمون والمحلات"))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat(tr("المستخدمون"), s.users, Color(0xFF455A64), Modifier.weight(1f))
                    Stat(tr("المحلات"), s.shops, Color(0xFF6A1B9A), Modifier.weight(1f))
                }
                SectionLabel(tr("التفعيل"))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat(tr("مفعّلة الآن"), s.active, Color(0xFF0B6E4F), Modifier.weight(1f))
                    Stat(tr("تنتهي خلال أسبوع"), s.expiringSoon, Color(0xFF8A5A00), Modifier.weight(1f))
                }
                Stat(tr("لم تُفعَّل أبدًا"), s.neverActivated, Color(0xFFC62828), Modifier.fillMaxWidth())
                SectionLabel(tr("آخر باقة لكل محل (منذ البداية)"))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat(tr("400 أوقية"), s.tier1, Color(0xFF1565C0), Modifier.weight(1f))
                    Stat(tr("600 أوقية"), s.tier2, Color(0xFF1565C0), Modifier.weight(1f))
                    Stat(tr("900 أوقية"), s.tier3, Color(0xFF1565C0), Modifier.weight(1f))
                }
            }
        }
    }

    val d = demo
    if (confirmDeleteDemo && d != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteDemo = false },
            title = { Text(tr("حذف المحل التجريبي")) },
            text = { Text(tr("ستُحذف كل بياناته الافتراضية. محلاتك الحقيقية لا تتأثر.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteDemo = false
                    demoBusy = true
                    scope.launch {
                        DemoData.delete(dao, d.id)
                        demoMessage = tr("تم الحذف")
                        demoBusy = false
                    }
                }) { Text(tr("حذف"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteDemo = false }) { Text(tr("إلغاء")) } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
}

@Composable
private fun Stat(label: String, value: Long, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = color)
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
