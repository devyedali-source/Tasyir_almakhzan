package com.elyedali.makhzan.activation

import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import java.util.Locale
import android.net.Uri
import com.elyedali.makhzan.auth.Auth
import com.elyedali.makhzan.data.Shop
import com.elyedali.makhzan.data.ShopDao
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Calendar

data class PlanInfo(val tier: Int, val label: String, val price: Int)

/** طلب تفعيل مقروء من الرابط الذي يصل المسؤولَ عبر واتساب */
data class ActivationRequest(
    val ownerUid: String,
    val shopRemoteId: String,
    val tier: Int,
    val shopName: String,
    val ownerEmail: String,
)

/**
 * التفعيل على الخادم: وثيقة واحدة لكل محل في المجموعة shops، يكتبها المسؤول وحده.
 * لا شيء من بيانات المحل (منتجات، مبيعات…) يُرسل إلى الخادم.
 */
object Activation {
    const val WHATSAPP_NUMBER = "22237786585"
    private const val LINK_BASE = "https://stockcaisse.web.app/activate"
    const val MONTHS = 4

    val plans = listOf(
        PlanInfo(1, "من 10 إلى 50 منتجًا", 400),
        PlanInfo(2, "من 51 إلى 100 منتج", 600),
        PlanInfo(3, "أكثر من 100 منتج", 900),
    )

    private val refreshed = mutableSetOf<Long>()

    /** true أول مرة فقط لكل محل في هذه الجلسة (لتقليل القراءات من الخادم) */
    @Synchronized
    fun markRefreshed(shopId: Long): Boolean = refreshed.add(shopId)

    fun plan(tier: Int) = plans.firstOrNull { it.tier == tier }

    private fun doc(remoteId: String) =
        FirebaseFirestore.getInstance().collection("shops").document(remoteId)

    fun buildLink(shop: Shop, tier: Int): String {
        val user = Auth.auth.currentUser
        return Uri.parse(LINK_BASE).buildUpon()
            .appendQueryParameter("u", user?.uid ?: "")
            .appendQueryParameter("s", shop.remoteId)
            .appendQueryParameter("t", tier.toString())
            .appendQueryParameter("n", shop.name)
            .appendQueryParameter("e", user?.email ?: "")
            .build().toString()
    }

    fun buildMessage(shop: Shop, tier: Int): String {
        val p = plan(tier) ?: plans.first()
        val email = Auth.auth.currentUser?.email ?: ""
        return "السلام عليكم،\n" +
            "أنا صاحب الحساب: $email\n" +
            "أريد تفعيل المحل التجاري: ${shop.name}\n" +
            "الباقة: ${p.label} — ${p.price} أوقية لمدة $MONTHS أشهر\n\n" +
            "🔑 رقم المحل: ${shop.remoteId.take(8).uppercase()}\n" +
            "------------------------------------\n" +
            "رابط التفعيل:\n" +
            buildLink(shop, tier)
    }

    fun whatsappIntent(message: String): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://api.whatsapp.com/send?phone=$WHATSAPP_NUMBER&text=" + Uri.encode(message)),
    )

    fun parse(uri: Uri?): ActivationRequest? {
        if (uri == null || uri.host != "stockcaisse.web.app" || uri.path?.startsWith("/activate") != true) return null
        val u = uri.getQueryParameter("u").orEmpty()
        val s = uri.getQueryParameter("s").orEmpty()
        val t = uri.getQueryParameter("t")?.toIntOrNull() ?: return null
        if (u.isBlank() || s.isBlank() || plan(t) == null) return null
        return ActivationRequest(
            ownerUid = u, shopRemoteId = s, tier = t,
            shopName = uri.getQueryParameter("n").orEmpty(),
            ownerEmail = uri.getQueryParameter("e").orEmpty(),
        )
    }

    /** يقرأ حالة تفعيل المحل من الخادم مرة واحدة ويحدّثها في الهاتف. يُرجع true إذا نجحت القراءة. */
    suspend fun refresh(dao: ShopDao, shop: Shop): Boolean {
        val uid = Auth.auth.currentUser?.uid ?: return false
        if (shop.remoteId.isBlank()) return false
        return try {
            val snap = doc(shop.remoteId).get().await()
            applySnapshot(dao, shop, uid, snap.getString("ownerUid"), snap.getLong("tier"), snap.getLong("expiresAt"))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** يستمع لتفعيل المحل لحظيًا (فقط ما دامت نافذة التفعيل مفتوحة) */
    fun listen(shop: Shop, onUpdate: (tier: Int, expiresAt: Long) -> Unit): ListenerRegistration? {
        val uid = Auth.auth.currentUser?.uid ?: return null
        if (shop.remoteId.isBlank()) return null
        return doc(shop.remoteId).addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists() && snap.getString("ownerUid") == uid) {
                onUpdate((snap.getLong("tier") ?: 0L).toInt(), snap.getLong("expiresAt") ?: 0L)
            }
        }
    }

    private suspend fun applySnapshot(
        dao: ShopDao, shop: Shop, uid: String, owner: String?, tier: Long?, expiresAt: Long?,
    ) {
        if (owner == uid && tier != null && expiresAt != null) {
            if (tier.toInt() != shop.tier || expiresAt != shop.expiresAt) {
                dao.updateActivation(shop.id, tier.toInt(), expiresAt)
            }
        }
    }

    fun addMonths(from: Long, months: Int): Long =
        Calendar.getInstance().apply { timeInMillis = from; add(Calendar.MONTH, months) }.timeInMillis

    /** حالة المحل الحالية على الخادم (للمسؤول قبل التفعيل) */
    suspend fun adminRead(req: ActivationRequest): Pair<Int, Long> {
        val snap = doc(req.shopRemoteId).get().await()
        return (snap.getLong("tier") ?: 0L).toInt() to (snap.getLong("expiresAt") ?: 0L)
    }

    /**
     * المسؤول يفعّل المحل لمدة 4 أشهر. إذا كانت نفس الباقة ما زالت سارية تُضاف المدة إلى نهايتها،
     * وإلا تبدأ من اليوم. يُرجع تاريخ الانتهاء الجديد.
     */
    suspend fun adminActivate(req: ActivationRequest): Long {
        require(Auth.isAdmin()) { "هذا الإجراء خاص بالمسؤول" }
        val ref = doc(req.shopRemoteId)
        val db = FirebaseFirestore.getInstance()
        return db.runTransaction { tx ->
            val snap = tx.get(ref)
            val now = System.currentTimeMillis()
            val owner = snap.getString("ownerUid")
            if (owner != null && owner != req.ownerUid) {
                throw IllegalStateException("هذا المحل مسجل باسم حساب آخر")
            }
            val curTier = (snap.getLong("tier") ?: 0L).toInt()
            val curExp = snap.getLong("expiresAt") ?: 0L
            val base = if (curTier == req.tier && curExp > now) curExp else now
            val newExp = addMonths(base, MONTHS)
            tx.set(
                ref,
                mapOf(
                    "ownerUid" to req.ownerUid,
                    "ownerEmail" to req.ownerEmail,
                    "shopName" to req.shopName,
                    "tier" to req.tier,
                    "expiresAt" to newExp,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            newExp
        }.await()
    }

    /**
     * تفعيل بعد دفع ناجح عبر Google Play: يكتبه صاحب المحل نفسه (قواعد الخادم تسمح له بذلك
     * فقط مع رمز شراء جديد ومدة لا تتجاوز 4 أشهر). يُرجع تاريخ الانتهاء الجديد.
     */
    suspend fun ownerActivateFromPurchase(shopRemoteId: String, shopName: String, tier: Int, purchaseToken: String): Long {
        val user = Auth.auth.currentUser ?: throw IllegalStateException("not signed in")
        val ref = doc(shopRemoteId)
        return FirebaseFirestore.getInstance().runTransaction { tx ->
            val snap = tx.get(ref)
            val now = System.currentTimeMillis()
            val curTier = (snap.getLong("tier") ?: 0L).toInt()
            val curExp = snap.getLong("expiresAt") ?: 0L
            // نفس الشراء طُبّق من قبل (مثلًا انقطع الاتصال قبل الاستهلاك)
            if (snap.getString("lastPurchaseToken") == purchaseToken) return@runTransaction curExp
            val base = if (curTier == tier && curExp > now) curExp else now
            val newExp = addMonths(base, MONTHS)
            tx.set(
                ref,
                mapOf(
                    "ownerUid" to user.uid,
                    "ownerEmail" to (user.email ?: ""),
                    "shopName" to shopName,
                    "tier" to tier,
                    "expiresAt" to newExp,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "lastPurchaseToken" to purchaseToken,
                    "source" to "play",
                ),
                SetOptions.merge(),
            )
            newExp
        }.await()
    }

    /** الدفع عبر واتساب متاح لمستخدمي موريتانيا (حسب شريحة الهاتف أو الشبكة أو إعدادات اللغة) */
    fun isMauritania(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val codes = listOfNotNull(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country)
        return codes.any { it.equals("mr", ignoreCase = true) }
    }

    // ---------------- تسجيل المستخدمين والمحلات (لإحصائيات لوحة المدير) ----------------

    /** يسجّل المحل على الخادم مرة واحدة عند إنشائه (يُرسل لاحقًا تلقائيًا إن لم يتوفر الإنترنت) */
    fun registerShop(shop: Shop) {
        val user = Auth.auth.currentUser ?: return
        // المسؤول يملك صلاحية الكتابة الكاملة: لا نعيد تسجيل محلاته حتى لا يُمسح تفعيلها
        if (shop.remoteId.isBlank() || Auth.isAdmin()) return
        doc(shop.remoteId).set(
            mapOf(
                "ownerUid" to user.uid,
                "ownerEmail" to (user.email ?: ""),
                "shopName" to shop.name,
                "tier" to 0,
                "expiresAt" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
    }

    /** يسجّل المستخدم مرة واحدة بعد أول دخول */
    fun registerUser() {
        val user = Auth.auth.currentUser ?: return
        FirebaseFirestore.getInstance().collection("users").document(user.uid).set(
            mapOf("email" to (user.email ?: ""), "createdAt" to FieldValue.serverTimestamp())
        )
    }

    data class AdminStats(
        val users: Long,
        val shops: Long,
        val active: Long,
        val expiringSoon: Long,
        val neverActivated: Long,
        val tier1: Long,
        val tier2: Long,
        val tier3: Long,
    )

    /** إحصائيات المدير بعمليات عدّ فقط (قراءة واحدة لكل ألف وثيقة تقريبًا، دون تحميل البيانات) */
    suspend fun adminStats(): AdminStats {
        val db = FirebaseFirestore.getInstance()
        val shops = db.collection("shops")
        val now = System.currentTimeMillis()
        suspend fun count(q: com.google.firebase.firestore.Query) = q.count().get(AggregateSource.SERVER).await().count
        return AdminStats(
            users = count(db.collection("users")),
            shops = count(shops),
            active = count(shops.whereGreaterThan("expiresAt", now)),
            expiringSoon = count(shops.whereGreaterThan("expiresAt", now).whereLessThan("expiresAt", now + 7 * 86_400_000L)),
            neverActivated = count(shops.whereEqualTo("tier", 0)),
            tier1 = count(shops.whereEqualTo("tier", 1)),
            tier2 = count(shops.whereEqualTo("tier", 2)),
            tier3 = count(shops.whereEqualTo("tier", 3)),
        )
    }
}
