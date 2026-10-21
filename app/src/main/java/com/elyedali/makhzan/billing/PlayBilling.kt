package com.elyedali.makhzan.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.elyedali.makhzan.activation.Activation
import com.elyedali.makhzan.auth.Auth
import com.elyedali.makhzan.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * الدفع بالبطاقة البنكية عبر Google Play (في موريتانيا وفي العالم).
 * كل باقة منتج «مستهلك» في Play Console، مدته 4 أشهر، يُشترى من جديد عند التجديد.
 * فور نجاح الدفع يُكتب التفعيل على الخادم ثم يُستهلك المنتج، فيُفتح المحل تلقائيًا.
 * إذا انقطع الاتصال قبل الكتابة يبقى الشراء معلقًا ويُعالَج تلقائيًا عند الفتح القادم.
 */
object PlayBilling {
    private val PRODUCT_IDS = mapOf(1 to "shop_tier_1", 2 to "shop_tier_2", 3 to "shop_tier_3")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContext: Context
    private var client: BillingClient? = null

    /** الأسعار كما يعرضها Google Play بعملة المستخدم (tier → السعر) */
    var prices by mutableStateOf<Map<Int, String>>(emptyMap())
        private set
    private var details: Map<Int, ProductDetails> = emptyMap()

    /** آخر رسالة عن الدفع لعرضها في النافذة (null = لا شيء) */
    var status by mutableStateOf<String?>(null)

    fun tierOf(productId: String): Int? = PRODUCT_IDS.entries.firstOrNull { it.value == productId }?.key

    fun init(context: Context) {
        appContext = context.applicationContext
        client = BillingClient.newBuilder(appContext)
            .setListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    purchases.forEach { p -> scope.launch { handle(p) } }
                } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                    status = null
                } else {
                    status = "ERROR:${result.debugMessage}"
                }
            }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
    }

    private suspend fun ready(): BillingClient? {
        val c = client ?: return null
        if (c.isReady) return c
        val ok = suspendCancellableCoroutine { cont ->
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(r: BillingResult) {
                    if (cont.isActive) cont.resume(r.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
        return if (ok) c else null
    }

    /** يجلب الأسعار من Google Play (إن لم تكن المنتجات منشورة بعد يبقى الزر معطلًا) */
    suspend fun loadPrices() {
        val c = ready() ?: return
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            PRODUCT_IDS.values.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it).setProductType(BillingClient.ProductType.INAPP).build()
            }
        ).build()
        val res = c.queryProductDetails(params)
        val list = res.productDetailsList.orEmpty()
        details = list.mapNotNull { d -> tierOf(d.productId)?.let { it to d } }.toMap()
        prices = details.mapValues { it.value.oneTimePurchaseOfferDetails?.formattedPrice ?: "" }
    }

    /** يفتح نافذة الدفع من Google Play لهذه الباقة وهذا المحل */
    suspend fun buy(activity: Activity, tier: Int, shopRemoteId: String): Boolean {
        val c = ready() ?: return false
        if (details.isEmpty()) loadPrices()
        val d = details[tier] ?: return false
        val uid = Auth.auth.currentUser?.uid ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build())
            )
            // يربط الدفع بالحساب والمحل، فلا يُفعَّل إلا المحل المقصود
            .setObfuscatedAccountId(uid)
            .setObfuscatedProfileId(shopRemoteId)
            .build()
        status = null
        return c.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    /** يعالج المشتريات التي لم تكتمل (انقطاع الاتصال بعد الدفع مثلًا) */
    fun processPending() {
        scope.launch {
            val c = ready() ?: return@launch
            val res = c.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            )
            res.purchasesList.forEach { handle(it) }
        }
    }

    private suspend fun handle(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            status = "PENDING"
            return
        }
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val ids = purchase.accountIdentifiers ?: return
        val uid = Auth.auth.currentUser?.uid ?: return
        // شراء خاص بحساب آخر على نفس الهاتف: نتركه حتى يدخل صاحبه
        if (ids.obfuscatedAccountId != uid) return
        val shopRemoteId = ids.obfuscatedProfileId ?: return
        val tier = purchase.products.firstNotNullOfOrNull { tierOf(it) } ?: return
        val dao = AppDatabase.get(appContext).dao()
        val shop = dao.shopByRemoteId(shopRemoteId)

        val newExp = try {
            Activation.ownerActivateFromPurchase(shopRemoteId, shop?.name.orEmpty(), tier, purchase.purchaseToken)
        } catch (e: Exception) {
            status = "ERROR:${e.message}"
            return // يبقى الشراء غير مستهلك ويُعاد لاحقًا
        }
        if (shop != null) dao.updateActivation(shop.id, tier, newExp)

        val c = ready() ?: return
        val consumed = c.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
        if (consumed.billingResult.responseCode != BillingClient.BillingResponseCode.OK && !purchase.isAcknowledged) {
            // احتياط: إقرار الشراء حتى لا يُسترد تلقائيًا بعد 3 أيام
            c.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            ) {}
        }
        status = "OK:$newExp"
    }
}
