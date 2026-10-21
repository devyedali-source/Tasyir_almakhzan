package com.elyedali.makhzan

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.elyedali.makhzan.activation.Activation
import com.elyedali.makhzan.activation.ActivationRequest
import com.elyedali.makhzan.backup.DriveAccess
import com.elyedali.makhzan.backup.DriveBackup
import com.elyedali.makhzan.data.AppDatabase
import com.elyedali.makhzan.data.Money
import com.elyedali.makhzan.data.Prefs
import com.elyedali.makhzan.data.ShopDao
import com.elyedali.makhzan.i18n.I18n
import com.elyedali.makhzan.i18n.tr
import com.elyedali.makhzan.ui.AdminActivateScreen
import com.elyedali.makhzan.ui.CashScreen
import com.elyedali.makhzan.ui.InvoiceScreen
import com.elyedali.makhzan.ui.LoginScreen
import com.elyedali.makhzan.ui.ProductDetailScreen
import com.elyedali.makhzan.ui.PurchaseScreen
import com.elyedali.makhzan.ui.SaleScreen
import com.elyedali.makhzan.ui.SettingsScreen
import com.elyedali.makhzan.ui.ShopHomeScreen
import com.elyedali.makhzan.ui.ShopsScreen
import com.elyedali.makhzan.ui.StockScreen
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface Route {
    data object Shops : Route
    data class Home(val shopId: Long) : Route
    data class Sale(val shopId: Long) : Route
    data class Purchase(val shopId: Long) : Route
    data class Cash(val shopId: Long) : Route
    data class Stock(val shopId: Long) : Route
    data class ProductDetail(val productId: Long) : Route
    data class Settings(val shopId: Long) : Route
    /** opId = null: فاتورة تجريبية لمعاينة التصميم */
    data class Invoice(val shopId: Long, val opId: Long?) : Route
    data class AdminActivate(val req: ActivationRequest) : Route
}

private fun Route.shopId(): Long? = when (this) {
    is Route.Home -> shopId
    is Route.Sale -> shopId
    is Route.Purchase -> shopId
    is Route.Cash -> shopId
    is Route.Stock -> shopId
    is Route.Settings -> shopId
    is Route.Invoice -> shopId
    else -> null
}

private val AppColors = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE1),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF8A5A00),
    secondaryContainer = Color(0xFFFFE3B0),
    background = Color(0xFFF7F8F6),
    surface = Color(0xFFFFFFFF),
)

class MainActivity : ComponentActivity() {
    private var deepLink by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLink = intent?.data
        val dao = AppDatabase.get(this).dao()
        setContent {
            MaterialTheme(colorScheme = AppColors) {
                val dir = if (I18n.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides dir) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppGate(dao, deepLink, onDeepLinkConsumed = { deepLink = null })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink = intent.data
    }
}

/** تسجيل الدخول إجباري (مرة واحدة)، ثم التحقق من Drive، ثم التطبيق */
@Composable
private fun AppGate(dao: ShopDao, deepLink: Uri?, onDeepLinkConsumed: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(Unit) {
        val l = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(l)
        onDispose { auth.removeAuthStateListener(l) }
    }
    val u = user
    if (u == null) {
        LoginScreen()
        return
    }
    DriveSetup(dao, u.uid) { driveOk, requestDrive ->
        AppRoot(dao, deepLink, onDeepLinkConsumed, driveOk, requestDrive)
    }
}

private sealed interface Phase {
    data object Checking : Phase
    data object Restoring : Phase
    data class Ask(val info: DriveBackup.BackupInfo, val token: String) : Phase
    data object Ready : Phase
}

/**
 * بعد الدخول: البحث عن نسخة احتياطية على Drive.
 * - الهاتف فارغ ووُجدت نسخة: تُسترجع تلقائيًا.
 * - في الهاتف بيانات ووُجدت نسخة: يُسأل المستخدم.
 * لا تُرفع أي نسخة قبل هذا التحقق حتى لا نكتب فوق نسخة قديمة.
 */
@Composable
private fun DriveSetup(
    dao: ShopDao,
    uid: String,
    content: @Composable (driveOk: Boolean, requestDrive: () -> Unit) -> Unit,
) {
    val activity = LocalContext.current as Activity
    var phase by remember(uid) {
        mutableStateOf<Phase>(
            if (Prefs.restoreChecked(activity, uid) && !Prefs.driveNeedsConsent(activity)) Phase.Ready else Phase.Checking
        )
    }
    var attempt by remember { mutableStateOf(0) }
    var consentToken by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val token = if (res.resultCode == Activity.RESULT_OK) DriveBackup.tokenFromConsent(activity, res.data) else null
        if (token != null) {
            Prefs.setDriveNeedsConsent(activity, false)
            consentToken = token
            attempt++
        } else {
            Prefs.setDriveNeedsConsent(activity, true)
            phase = Phase.Ready
        }
    }

    // مفتاحه attempt فقط: تغيير phase داخله لا يجب أن يلغي الاسترجاع الجاري
    LaunchedEffect(attempt) {
        if (phase != Phase.Checking) return@LaunchedEffect
        val token = consentToken ?: when (val a = withTimeoutOrNull(25_000) { DriveBackup.access(activity) }) {
            is DriveAccess.Granted -> a.token
            is DriveAccess.NeedsConsent -> {
                consent.launch(IntentSenderRequest.Builder(a.intent).build())
                return@LaunchedEffect
            }
            else -> { phase = Phase.Ready; return@LaunchedEffect }
        }
        consentToken = null
        Prefs.setDriveNeedsConsent(activity, false)
        if (Prefs.restoreChecked(activity, uid)) {
            DriveBackup.schedule(activity)
            phase = Phase.Ready
            return@LaunchedEffect
        }
        // Result يميّز بين «لا توجد نسخة» و«تعذر الاتصال»
        val found = withTimeoutOrNull(25_000) { runCatching { DriveBackup.findBackup(token) } }
        if (found == null || found.isFailure) {
            phase = Phase.Ready
            return@LaunchedEffect
        }
        val info = found.getOrNull()
        when {
            info == null -> {
                Prefs.setRestoreChecked(activity, uid, true)
                DriveBackup.schedule(activity)
                phase = Phase.Ready
            }
            dao.shopCount() == 0 -> {
                phase = Phase.Restoring
                try {
                    DriveBackup.restore(activity, token, info)
                    Prefs.setRestoreChecked(activity, uid, true)
                    DriveBackup.restartApp(activity)
                } catch (e: Exception) {
                    error = tr("تعذر استرجاع النسخة الاحتياطية: %1\$s", e.message ?: "")
                    phase = Phase.Ready
                }
            }
            else -> phase = Phase.Ask(info, token)
        }
    }

    when (val p = phase) {
        Phase.Checking, Phase.Restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(
                    if (p == Phase.Restoring) tr("جارٍ استرجاع بياناتك من Google Drive…")
                    else tr("جارٍ التحقق من النسخة الاحتياطية على Google Drive…"),
                    textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp),
                )
            }
        }
        is Phase.Ask -> AlertDialog(
            onDismissRequest = {},
            title = { Text(tr("توجد نسخة احتياطية على Drive")) },
            text = {
                Text(
                    tr(
                        "وُجدت نسخة محفوظة على Google Drive بتاريخ %1\$s. استرجاعها يستبدل البيانات الموجودة على هذا الهاتف.",
                        p.info.modified.take(16).replace('T', ' '),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    phase = Phase.Restoring
                    scope.launch {
                        try {
                            DriveBackup.restore(activity, p.token, p.info)
                            Prefs.setRestoreChecked(activity, uid, true)
                            DriveBackup.restartApp(activity)
                        } catch (e: Exception) {
                            error = tr("تعذر استرجاع النسخة الاحتياطية: %1\$s", e.message ?: "")
                            phase = Phase.Ready
                        }
                    }
                }) { Text(tr("استرجاع النسخة")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    Prefs.setRestoreChecked(activity, uid, true)
                    DriveBackup.schedule(activity)
                    phase = Phase.Ready
                }) { Text(tr("الإبقاء على بيانات الهاتف")) }
            },
        )
        Phase.Ready -> {
            val driveOk = Prefs.restoreChecked(activity, uid) && !Prefs.driveNeedsConsent(activity)
            content(driveOk) { phase = Phase.Checking; attempt++ }
            error?.let { msg ->
                AlertDialog(
                    onDismissRequest = { error = null },
                    text = { Text(msg) },
                    confirmButton = { TextButton(onClick = { error = null }) { Text(tr("حسنًا")) } },
                )
            }
        }
    }

}

@Composable
private fun AppRoot(
    dao: ShopDao,
    deepLink: Uri?,
    onDeepLinkConsumed: () -> Unit,
    driveOk: Boolean,
    requestDrive: () -> Unit,
) {
    var stack by remember { mutableStateOf(listOf<Route>(Route.Shops)) }
    fun go(r: Route) { stack = stack + r }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) }

    BackHandler(enabled = stack.size > 1) { back() }

    LaunchedEffect(deepLink) {
        val req = Activation.parse(deepLink)
        if (req != null) go(Route.AdminActivate(req))
        if (deepLink != null) onDeepLinkConsumed()
    }

    // عملة المحل المفتوح
    val currentShop = stack.lastOrNull { it.shopId() != null }?.shopId()
    LaunchedEffect(currentShop) {
        Money.currency = currentShop?.let { dao.profileNow(it)?.currency } ?: ""
    }

    when (val r = stack.last()) {
        Route.Shops -> ShopsScreen(dao, onOpen = { go(Route.Home(it)) }, driveOk = driveOk, requestDrive = requestDrive)
        is Route.Home -> ShopHomeScreen(dao, r.shopId, onBack = ::back, go = ::go)
        is Route.Sale -> SaleScreen(dao, r.shopId, onBack = ::back, onInvoice = { go(Route.Invoice(r.shopId, it)) })
        is Route.Purchase -> PurchaseScreen(dao, r.shopId, onBack = ::back)
        is Route.Cash -> CashScreen(dao, r.shopId, onBack = ::back, onInvoice = { go(Route.Invoice(r.shopId, it)) })
        is Route.Stock -> StockScreen(dao, r.shopId, onBack = ::back, onOpen = { go(Route.ProductDetail(it)) })
        is Route.ProductDetail -> ProductDetailScreen(dao, r.productId, onBack = ::back)
        is Route.Settings -> SettingsScreen(dao, r.shopId, onBack = ::back, onPreview = { go(Route.Invoice(r.shopId, null)) })
        is Route.Invoice -> InvoiceScreen(dao, r.shopId, r.opId, onBack = ::back)
        is Route.AdminActivate -> AdminActivateScreen(r.req, onBack = ::back)
    }
}

