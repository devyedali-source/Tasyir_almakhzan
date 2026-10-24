package com.elyedali.makhzan.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elyedali.makhzan.data.Prefs
import com.elyedali.makhzan.data.ShopDao
import com.elyedali.makhzan.data.ShopProfile
import com.elyedali.makhzan.i18n.tr
import com.elyedali.makhzan.invoice.InvoiceData
import com.elyedali.makhzan.invoice.InvoicePdf
import com.elyedali.makhzan.print.PairedPrinter
import com.elyedali.makhzan.print.ThermalPrinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** يطبع إيصال البيع على طابعة البلوتوث (دائمًا بتصميم الإيصال الضيق مهما كان تصميم الفاتورة المختار) */
internal suspend fun printReceipt(context: Context, dao: ShopDao, shopId: Long, data: InvoiceData) {
    val shop = dao.shopNow(shopId)
    val profile = (dao.profileNow(shopId) ?: ShopProfile(shopId = shopId)).copy(design = 2)
    val file = withContext(Dispatchers.IO) {
        InvoicePdf.create(context, shop?.name ?: "", profile, data, "receipt_${data.number}.pdf")
    }
    ThermalPrinter.printPdf(context, file)
}

/** قسم طابعة البلوتوث في الإعدادات: اختيار طابعة مقترنة، عرض الورق، وطباعة تجريبية */
@Composable
fun PrinterSettings(dao: ShopDao, shopId: Long, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(Prefs.printerName(context)) }
    var dots by remember { mutableStateOf(Prefs.printerDots(context)) }
    var choosing by remember { mutableStateOf<List<PairedPrinter>?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun openChooser() {
        if (!ThermalPrinter.isAvailable(context)) { onMessage(tr("هذا الهاتف لا يدعم البلوتوث")); return }
        if (!ThermalPrinter.isEnabled(context)) { onMessage(tr("شغّل البلوتوث في الهاتف")); return }
        choosing = ThermalPrinter.paired(context)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) openChooser() else onMessage(tr("اسمح للتطبيق باستعمال البلوتوث"))
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(name ?: tr("لم تُختر طابعة بعد"), fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("قرِن الطابعة أولًا من إعدادات البلوتوث في الهاتف، ثم اخترها هنا."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = dots == ThermalPrinter.DOTS_58, onClick = {
                    dots = ThermalPrinter.DOTS_58; Prefs.setPrinterDots(context, dots)
                }, label = { Text(tr("ورق 58 مم")) })
                FilterChip(selected = dots == ThermalPrinter.DOTS_80, onClick = {
                    dots = ThermalPrinter.DOTS_80; Prefs.setPrinterDots(context, dots)
                }, label = { Text(tr("ورق 80 مم")) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (ThermalPrinter.hasPermission(context)) openChooser() else permission.launch(ThermalPrinter.permission)
                }) { Text(if (name == null) tr("اختيار الطابعة") else tr("تغيير الطابعة")) }
                if (name != null) {
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                printReceipt(context, dao, shopId, sampleInvoice())
                                onMessage(tr("أُرسلت الطباعة التجريبية"))
                            } catch (e: Exception) {
                                onMessage(e.message ?: tr("تعذر الطباعة"))
                            } finally {
                                busy = false
                            }
                        }
                    }) { Text(tr("طباعة تجريبية")) }
                }
            }
            if (name != null) {
                TextButton(onClick = { Prefs.setPrinter(context, null, null); name = null }) {
                    Text(tr("إلغاء الطابعة"), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    choosing?.let { list ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text(tr("اختر الطابعة")) },
            text = {
                Column {
                    if (list.isEmpty()) {
                        Text(tr("لا توجد أجهزة مقترنة. افتح إعدادات البلوتوث في الهاتف واقرن الطابعة (الرمز غالبًا 0000 أو 1234)."))
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                        }) { Text(tr("فتح إعدادات البلوتوث")) }
                    }
                    list.forEach { p ->
                        Text(
                            p.name,
                            modifier = Modifier.fillMaxWidth().clickable {
                                Prefs.setPrinter(context, p.name, p.address)
                                name = p.name
                                choosing = null
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = null }) { Text(tr("إغلاق")) } },
        )
    }
}
