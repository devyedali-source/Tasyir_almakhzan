package com.elyedali.makhzan.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.compose.ui.platform.LocalClipboardManager
import com.elyedali.makhzan.activation.Activation
import com.elyedali.makhzan.activation.ActivationRequest
import com.elyedali.makhzan.auth.Auth
import com.elyedali.makhzan.backup.DriveAccess
import com.elyedali.makhzan.backup.DriveBackup
import com.elyedali.makhzan.data.Dates
import com.elyedali.makhzan.data.Prefs
import com.elyedali.makhzan.i18n.I18n
import com.elyedali.makhzan.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen() {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showLang by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary).systemBarsPadding().padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(104.dp).background(Color.White, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.QrCodeScanner, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp),
                )
            }
            Text(
                tr("تسيير المخزون والصندوق"), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
            )
            Text(
                tr("سجّل الدخول مرة واحدة بحساب Google. بعدها يعمل التطبيق دون إنترنت."),
                textAlign = TextAlign.Center, color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(8.dp))
            if (busy) CircularProgressIndicator(color = Color.White)
            else Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                onClick = {
                busy = true
                error = null
                scope.launch {
                    error = Auth.signInWithGoogle(activity)
                    busy = false
                }
            }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text(tr("تسجيل الدخول بحساب Google"), fontWeight = FontWeight.Bold)
            }
            error?.let { Text(it, color = Color(0xFFFFE0E0), textAlign = TextAlign.Center) }
            TextButton(onClick = { showLang = true }) {
                Text("🌐 " + (I18n.languages.first { it.code == I18n.lang }.nativeName), color = Color.White)
            }
        }
    }
    if (showLang) LanguageDialog(onDismiss = { showLang = false })
}

@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("لغة التطبيق")) },
        text = {
            Column {
                I18n.languages.forEach { l ->
                    Row(
                        Modifier.fillMaxWidth().clickable { I18n.set(context, l.code); onDismiss() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = I18n.lang == l.code, onClick = { I18n.set(context, l.code); onDismiss() })
                        Text(l.nativeName)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("إغلاق")) } },
    )
}

/** معلومات الحساب: البريد، آخر حفظ على Drive، وتسجيل الخروج */
@Composable
fun AccountDialog(onDismiss: () -> Unit, onEnableDrive: () -> Unit, onAdminLink: (ActivationRequest) -> Unit) {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    val last = Prefs.lastBackupAt(activity)
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(tr("حسابي")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Auth.auth.currentUser?.email ?: "", fontWeight = FontWeight.SemiBold)
                Text(
                    if (last > 0) tr("آخر حفظ على Google Drive: %1\$s", Dates.format(last))
                    else tr("لم تُحفظ نسخة على Google Drive بعد"),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (Prefs.driveNeedsConsent(activity)) {
                    OutlinedButton(onClick = onEnableDrive) { Text(tr("تفعيل الحفظ على Drive")) }
                }
                if (Auth.isAdmin()) {
                    // للمسؤول: إذا لم يفتح الرابط التطبيقَ مباشرة، ينسخ رسالة واتساب ثم يضغط هنا
                    OutlinedButton(onClick = {
                        val text = clipboard.getText()?.text.orEmpty()
                        val url = Regex("https://stockcaisse\\.web\\.app/activate\\S+").find(text)?.value
                        val req = url?.let { Activation.parse(Uri.parse(it)) }
                        if (req != null) onAdminLink(req) else message = tr("انسخ رسالة طلب التفعيل أولًا ثم أعد المحاولة")
                    }) { Text(tr("تفعيل من رابط منسوخ")) }
                }
                if (busy) CircularProgressIndicator()
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("إغلاق")) } },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { confirmLogout = true }) {
                Text(tr("تسجيل الخروج"), color = MaterialTheme.colorScheme.error)
            }
        },
    )

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(tr("تسجيل الخروج")) },
            text = {
                Text(tr("ستُحفظ بياناتك على Google Drive أولًا، ثم تُحذف من هذا الهاتف. يمكنك استرجاعها بتسجيل الدخول من جديد. يلزم الاتصال بالإنترنت."))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    busy = true
                    message = null
                    scope.launch {
                        when (val a = DriveBackup.access(activity)) {
                            is DriveAccess.Granted -> {
                                val err = DriveBackup.backupNow(activity, a.token)
                                if (err == null) {
                                    withContext(Dispatchers.IO) { DriveBackup.wipeLocal(activity) }
                                    Auth.signOut(activity)
                                    DriveBackup.restartApp(activity)
                                } else {
                                    message = tr("تعذر الحفظ على Drive. تحقق من الإنترنت ثم أعد المحاولة.")
                                }
                            }
                            is DriveAccess.NeedsConsent -> message = tr("يجب أولًا تفعيل الحفظ على Drive حتى لا تضيع بياناتك.")
                            is DriveAccess.Failed -> message = tr("تعذر الحفظ على Drive. تحقق من الإنترنت ثم أعد المحاولة.")
                        }
                        busy = false
                    }
                }) { Text(tr("حفظ ثم خروج")) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(tr("إلغاء")) } },
        )
    }
}
