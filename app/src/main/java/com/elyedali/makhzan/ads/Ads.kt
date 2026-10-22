package com.elyedali.makhzan.ads

import android.app.Activity
import com.elyedali.makhzan.data.TrustedClock
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.elyedali.makhzan.R
import com.elyedali.makhzan.auth.Auth
import com.elyedali.makhzan.data.ShopDao
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * إعلانات بسيطة (شريط صغير أسفل بعض الشاشات) لمن لم يفعّل أي محل بعد.
 * لا نوافذ منبثقة ولا إعلانات بملء الشاشة، ولا شيء في شاشات البيع والشراء حتى لا يتوقف عمل التاجر.
 * بدون إنترنت أو إن لم يتوفر إعلان: لا يظهر أي فراغ.
 */
object Ads {
    /** صار بالإمكان طلب الإعلانات (بعد موافقة الخصوصية حين تكون مطلوبة، مثل أوروبا) */
    var ready by mutableStateOf(false)
        private set
    private var started = false

    fun start(activity: Activity) {
        if (started) return
        started = true
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        val afterConsent = {
            if (!ready && consent.canRequestAds()) {
                MobileAds.initialize(activity.applicationContext) {}
                ready = true
            }
        }
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { afterConsent() } },
            { afterConsent() },
        )
        // موافقة سابقة محفوظة: لا داعي لانتظار الشبكة
        if (consent.canRequestAds()) afterConsent()
    }
}

/** شريط الإعلان: يظهر فقط إذا لم يكن لدى المستخدم أي محل مفعّل */
@Composable
fun AdBanner(dao: ShopDao) {
    val now = remember { TrustedClock.now() }
    val activeShops by dao.activeShopCount(now).collectAsState(initial = -1)
    if (!Ads.ready || activeShops != 0 || Auth.isAdmin()) return

    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    var loaded by remember { mutableStateOf(false) }
    val adView = remember {
        AdView(context).apply {
            adUnitId = context.getString(R.string.admob_banner_id)
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            adListener = object : AdListener() {
                override fun onAdLoaded() { loaded = true }
                override fun onAdFailedToLoad(error: LoadAdError) { loaded = false }
            }
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(Unit) { onDispose { adView.destroy() } }

    AndroidView(
        factory = { adView },
        modifier = if (loaded) Modifier.fillMaxWidth().navigationBarsPadding() else Modifier.height(0.dp),
    )
}
