package com.elyedali.makhzan.data

import android.content.Context
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL

/**
 * ساعة موثوقة تقاوم العبث بتاريخ الهاتف، وتعمل دون إنترنت.
 *
 * تعتمد على ثلاثة مصادر وتأخذ أكبرها:
 *  1) ساعة الهاتف.
 *  2) أكبر وقت رآه التطبيق من قبل (لا ينقص أبدًا) — فيُبطل رجوع التاريخ إلى الوراء.
 *  3) وقت الخادم عند آخر اتصال + المدة المنقضية من عدّاد الهاتف الداخلي (لا يتأثر بتغيير التاريخ يدويًا،
 *     ويُهمل بعد إعادة تشغيل الهاتف لأن العدّاد يبدأ من الصفر).
 *
 * وعند أول اتصال بالخادم يصير وقت الخادم هو المرجع النهائي، فيُصحَّح أي وقت مبالَغ فيه
 * (مثلًا ساعة ضُبطت خطأً إلى المستقبل).
 */
object TrustedClock {

    private const val PREFS_NAME = "trusted_clock_prefs"
    private const val KEY_MAX_SEEN = "max_seen_ms"
    private const val KEY_SERVER_MS = "server_ms"
    private const val KEY_SERVER_ELAPSED = "server_elapsed_ms"

    // نحفظ في الذاكرة الدائمة كلما تقدّم الوقت بهذا المقدار، لتفادي كتابة متكررة
    private const val PERSIST_STEP_MS = 10L * 60L * 1000L

    private val lock = Any()

    @Volatile private var appContext: Context? = null
    @Volatile private var loaded = false
    @Volatile private var maxSeen = 0L
    @Volatile private var persistedMax = 0L
    @Volatile private var serverAnchorMs = 0L
    @Volatile private var anchorElapsedMs = 0L

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        ensureLoaded()
    }

    private fun ensureLoaded() {
        if (loaded) return
        val ctx = appContext ?: return
        synchronized(lock) {
            if (loaded) return
            try {
                val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                maxSeen = p.getLong(KEY_MAX_SEEN, 0L)
                persistedMax = maxSeen
                serverAnchorMs = p.getLong(KEY_SERVER_MS, 0L)
                anchorElapsedMs = p.getLong(KEY_SERVER_ELAPSED, 0L)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            loaded = true
        }
    }

    private fun anchorEstimate(): Long {
        val anchor = serverAnchorMs
        val base = anchorElapsedMs
        if (anchor <= 0L || base <= 0L) return 0L
        val elapsedNow = SystemClock.elapsedRealtime()
        // انخفاض العدّاد يعني إعادة تشغيل الهاتف: نُهمل المرساة
        if (elapsedNow < base) return 0L
        return anchor + (elapsedNow - base)
    }

    private fun persist() {
        val ctx = appContext ?: return
        try {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong(KEY_MAX_SEEN, maxSeen)
                .putLong(KEY_SERVER_MS, serverAnchorMs)
                .putLong(KEY_SERVER_ELAPSED, anchorElapsedMs)
                .commit()
            persistedMax = maxSeen
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** الوقت الموثوق بالمللي ثانية. إن لم يُهيّأ التطبيق بعد يعود إلى ساعة الهاتف. */
    fun now(): Long {
        val wall = System.currentTimeMillis()
        ensureLoaded()
        if (!loaded) return wall
        var trusted = wall
        val seen = maxSeen
        if (seen > trusted) trusted = seen
        val estimate = anchorEstimate()
        if (estimate > trusted) trusted = estimate
        if (trusted > seen) {
            synchronized(lock) {
                if (trusted > maxSeen) {
                    maxSeen = trusted
                    if (trusted - persistedMax >= PERSIST_STEP_MS) persist()
                }
            }
        }
        return trusted
    }

    /** يُستدعى عند استلام وقت الخادم: يصير هو المرجع النهائي. */
    fun onServerTime(serverMs: Long) {
        if (serverMs <= 0L) return
        ensureLoaded()
        synchronized(lock) {
            serverAnchorMs = serverMs
            anchorElapsedMs = SystemClock.elapsedRealtime()
            maxSeen = serverMs
            persist()
        }
    }

    /** يقرأ وقت الخادم من ترويسة Date، ويعيد true عند النجاح. يُستدعى من خيط خلفي. */
    fun syncWithServer(): Boolean {
        val urls = listOf(
            "https://www.gstatic.com/generate_204",
            "https://www.google.com/generate_204"
        )
        for (address in urls) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(address).openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 6_000
                conn.readTimeout = 6_000
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.connect()
                val serverDate = conn.getHeaderFieldDate("Date", 0L)
                if (serverDate > 0L) {
                    onServerTime(serverDate)
                    return true
                }
            } catch (e: Exception) {
                // نجرّب العنوان التالي
            } finally {
                try {
                    conn?.disconnect()
                } catch (e: Exception) {
                }
            }
        }
        return false
    }
}
