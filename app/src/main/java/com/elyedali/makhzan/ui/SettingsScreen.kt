package com.elyedali.makhzan.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elyedali.makhzan.data.Money
import com.elyedali.makhzan.data.ShopDao
import com.elyedali.makhzan.data.ShopProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.elyedali.makhzan.i18n.tr

private val designs = listOf(
    "كلاسيكي (ورقة A4)" to "رأسية بيضاء وجدول بإطارات",
    "عصري (ورقة A4)" to "شريط ملون في الأعلى وصفوف متناوبة",
    "إيصال (طابعة 80 مم)" to "شريط طويل ضيق للطابعات الحرارية",
)

private val languages = listOf(
    "AR" to "العربية",
    "FR" to "Français",
    "EN" to "English",
    "BI" to "مزدوجة (العربية + Français)",
)

@Composable
fun SettingsScreen(dao: ShopDao, shopId: Long, onBack: () -> Unit, onPreview: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var loaded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var tax by remember { mutableStateOf("") }
    var logoPath by remember { mutableStateOf<String?>(null) }
    var logoBmp by remember { mutableStateOf<Bitmap?>(null) }
    var design by remember { mutableStateOf(0) }
    var language by remember { mutableStateOf("AR") }
    var stamp by remember { mutableStateOf(true) }
    var currency by remember { mutableStateOf("") }

    LaunchedEffect(shopId) {
        val shop = dao.shopNow(shopId)
        val p = dao.profileNow(shopId) ?: ShopProfile(shopId = shopId)
        name = shop?.name ?: ""
        address = p.address; phone = p.phone; email = p.email; tax = p.taxNumber
        logoPath = p.logoPath; design = p.design; language = p.language; stamp = p.showStamp
        currency = p.currency
        logoBmp = p.logoPath?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it) } }
        loaded = true
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val saved = withContext(Dispatchers.IO) { saveLogo(context, shopId, uri) }
            if (saved != null) {
                logoPath = saved.first
                logoBmp = saved.second
            } else snackbar.showSnackbar(tr("تعذرت قراءة الصورة"))
        }
    }

    suspend fun save(): Boolean {
        if (name.isBlank()) { snackbar.showSnackbar(tr("اسم المحل مطلوب")); return false }
        dao.updateShopName(shopId, name.trim())
        dao.saveProfile(
            ShopProfile(
                shopId = shopId, address = address.trim(), phone = phone.trim(), email = email.trim(),
                taxNumber = tax.trim(), logoPath = logoPath, design = design,
                language = language, showStamp = stamp, currency = currency.trim(),
            )
        )
        Money.currency = currency.trim()
        return true
    }

    Scaffold(
        topBar = { AppTopBar(tr("إعدادات المحل والفواتير"), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(tr("معلومات المحل (تظهر في رأسية الفاتورة)"))
            Field(name, { name = it }, tr("اسم المحل"))
            Field(address, { address = it }, tr("الموقع / العنوان"))
            Field(phone, { phone = it }, tr("رقم الهاتف"))
            Field(email, { email = it }, tr("معلومات التواصل (واتساب، بريد…)"))
            Field(tax, { tax = it }, tr("الرقم الضريبي (NIF)"))
            Field(currency, { currency = it }, tr("العملة (اتركها فارغة للأوقية)"))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFEEEEEE)),
                    contentAlignment = Alignment.Center,
                ) {
                    val b = logoBmp
                    if (b != null) Image(b.asImageBitmap(), contentDescription = tr("الشعار"), modifier = Modifier.fillMaxSize())
                    else Text(tr("الشعار"), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    OutlinedButton(onClick = { picker.launch("image/*") }) {
                        Text(if (logoBmp == null) tr("اختيار شعار المحل") else tr("تغيير الشعار"))
                    }
                    if (logoBmp != null) {
                        TextButton(onClick = { logoPath = null; logoBmp = null }) { Text(tr("حذف الشعار")) }
                    }
                }
            }

            HorizontalDivider()
            SectionTitle(tr("تصميم الفاتورة"))
            designs.forEachIndexed { i, (title, desc) ->
                Choice(selected = design == i, title = tr(title), desc = tr(desc)) { design = i }
            }

            HorizontalDivider()
            SectionTitle(tr("لغة الفاتورة"))
            languages.forEach { (code, label) ->
                Choice(selected = language == code, title = tr(label), desc = null) { language = code }
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("طابع المحل على الفاتورة"), fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("طابع دائري فيه اسم المحل والرقم الضريبي ورقم الهاتف"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = stamp, onCheckedChange = { stamp = it })
            }

            HorizontalDivider()
            SectionTitle(tr("طابعة البلوتوث (إيصالات حرارية)"))
            PrinterSettings(dao, shopId, onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } })

            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(modifier = Modifier.weight(1f), onClick = {
                    scope.launch { if (save()) snackbar.showSnackbar(tr("تم الحفظ")) }
                }) { Text(tr("حفظ")) }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                    scope.launch { if (save()) onPreview() }
                }) { Text(tr("معاينة الفاتورة")) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Choice(selected: Boolean, title: String, desc: String?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (desc != null) Text(desc, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** ينسخ الصورة المختارة داخل التطبيق بحجم مناسب (512 بكسل على الأكثر) */
private fun saveLogo(context: Context, shopId: Long, uri: Uri): Pair<String, Bitmap>? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 512 || bounds.outHeight / (sample * 2) >= 512) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        ?: return null
    val maxSide = maxOf(decoded.width, decoded.height)
    val bmp = if (maxSide > 512) {
        val s = 512f / maxSide
        Bitmap.createScaledBitmap(decoded, (decoded.width * s).toInt(), (decoded.height * s).toInt(), true)
    } else decoded
    val dir = File(context.filesDir, "logos").apply { mkdirs() }
    val file = File(dir, "shop_$shopId.png")
    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    file.absolutePath to bmp
}.getOrNull()
