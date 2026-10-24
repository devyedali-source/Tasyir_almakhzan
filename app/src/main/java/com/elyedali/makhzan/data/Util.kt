package com.elyedali.makhzan.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.elyedali.makhzan.i18n.tr
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private fun String.westernDigits(): String = buildString {
    for (c in this@westernDigits) {
        append(
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                '٫', ',' -> '.'
                else -> c
            }
        )
    }
}

object Money {
    /** عملة المحل المفتوح حاليًا (فارغة = الأوقية بلغة الواجهة) */
    var currency by mutableStateOf("")

    fun currencyLabel(): String = currency.ifBlank { tr("أوقية") }

    fun format(minor: Long, withCurrency: Boolean = true): String {
        val a = abs(minor)
        val whole = a / 100
        val frac = a % 100
        val num = if (frac == 0L) String.format(Locale.US, "%,d", whole)
        else String.format(Locale.US, "%,d.%02d", whole, frac)
        val sign = if (minor < 0) "-" else ""
        return if (withCurrency) "$sign$num ${currencyLabel()}" else "$sign$num"
    }

    /** يحول النص المكتوب إلى مبلغ بالوحدة الصغرى، أو null إذا لم يكن صالحًا */
    /** أكبر مبلغ مقبول (لتفادي أخطاء الكتابة الضخمة) */
    private const val MAX_MINOR = 100_000_000_000_00L

    fun parse(text: String): Long? {
        var t = text.trim().replace(" ", "").replace("\u00A0", "")
        // «1,500» أو «1.500,75»: الفاصلة قبل 3 أرقام في آخر الرقم فاصل آلاف، وإلا فهي فاصلة عشرية
        t = t.replace('٬', ',')
        if (t.contains(',') && t.contains('.')) t = t.replace(",", "")
        else if (Regex("^\\d{1,3}(,\\d{3})+$").matches(t)) t = t.replace(",", "")
        t = t.westernDigits()
        if (t.isEmpty()) return null
        val bd = t.toBigDecimalOrNull() ?: return null
        if (bd.signum() < 0) return null
        if (bd.movePointRight(2) > java.math.BigDecimal.valueOf(MAX_MINOR)) return null
        return try {
            bd.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    fun toInput(minor: Long): String =
        BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString()
}

/**
 * الكميات مخزنة بالألف (1000 = قطعة واحدة أو كيلو واحد) حتى يمكن البيع بالوزن (1.5 كغ = 1500)
 * دون أخطاء الكسور العشرية.
 */
object Qty {
    const val ONE = 1000L
    private const val MAX = 1_000_000L * ONE

    /** الوحدات المتاحة: فارغة = بالقطعة */
    val units = listOf("", "kg", "l", "m")

    fun isWeighed(unit: String) = unit.isNotEmpty()

    fun unitLabel(unit: String): String = when (unit) {
        "kg" -> tr("كغ")
        "l" -> tr("لتر")
        "m" -> tr("متر")
        else -> tr("قطعة")
    }

    /** اسم الوحدة في اختيارها */
    fun unitName(unit: String): String = when (unit) {
        "kg" -> tr("بالكيلو")
        "l" -> tr("باللتر")
        "m" -> tr("بالمتر")
        else -> tr("بالقطعة")
    }

    /**
     * يحوّل الكمية المكتوبة إلى قيمة بالألف: بالقطعة أعداد صحيحة فقط، وبالوزن حتى 3 أرقام بعد الفاصلة.
     * allowZero للجرد (الكمية الفعلية قد تكون صفرًا).
     */
    fun parse(text: String, unit: String = "", allowZero: Boolean = false): Long? {
        val t = text.trim().replace(" ", "").westernDigits()
        if (t.isEmpty()) return null
        val bd = t.toBigDecimalOrNull() ?: return null
        if (bd.signum() < 0 || (!allowZero && bd.signum() == 0)) return null
        if (!isWeighed(unit) && bd.stripTrailingZeros().scale() > 0) return null
        if (bd.stripTrailingZeros().scale() > 3) return null
        val v = bd.movePointRight(3)
        if (v > BigDecimal.valueOf(MAX)) return null
        return v.setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    }

    /** نص الكمية دون الوحدة: 3 أو 1.5 */
    fun plain(q: Long): String = BigDecimal.valueOf(q, 3).stripTrailingZeros().toPlainString()

    /** نص الكمية مع الوحدة للمنتجات الموزونة: «1.5 كغ»، وللقطع الرقم وحده */
    fun format(q: Long, unit: String = ""): String =
        if (isWeighed(unit)) "${plain(q)} ${unitLabel(unit)}" else plain(q)

    fun toInput(q: Long): String = plain(q)

    /** قيمة كمية بسعر الوحدة، مقرّبة لأقرب وحدة صغرى (الإشارة تتبع الكمية) */
    fun amount(unitPrice: Long, q: Long): Long {
        val neg = q < 0
        val a = abs(q)
        val v = BigDecimal.valueOf(unitPrice).multiply(BigDecimal.valueOf(a))
            .divide(BigDecimal.valueOf(ONE), 0, RoundingMode.HALF_UP).toLong()
        return if (neg) -v else v
    }

    /** متوسط مرجّح لتكلفة الوحدة بين مخزون قديم وكمية جديدة */
    fun weightedCost(oldCost: Long, oldQty: Long, newCost: Long, newQty: Long): Long {
        val o = oldQty.coerceAtLeast(0)
        if (o + newQty <= 0) return newCost
        return BigDecimal.valueOf(oldCost).multiply(BigDecimal.valueOf(o))
            .add(BigDecimal.valueOf(newCost).multiply(BigDecimal.valueOf(newQty)))
            .divide(BigDecimal.valueOf(o + newQty), 0, RoundingMode.HALF_UP).toLong()
    }

    /** نسبة من مبلغ: value × part ÷ whole مقرّبة (لتوزيع التخفيض على المرتجع) */
    fun share(value: Long, part: Long, whole: Long): Long {
        if (whole <= 0) return value
        return BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(part))
            .divide(BigDecimal.valueOf(whole), 0, RoundingMode.HALF_UP).toLong()
    }
}

object Dates {
    // نسخة جديدة في كل استدعاء لأن SimpleDateFormat غير آمن بين الخيوط (الفواتير تُنشأ في الخلفية)
    fun format(t: Long): String = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(t))
    fun formatDay(t: Long): String = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(t))

    fun startOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** حدود عدد المنتجات حسب تفعيل المحل */
object Plans {
    const val FREE_LIMIT = 9

    fun isActive(shop: Shop, now: Long) = shop.tier > 0 && shop.expiresAt > now

    /**
     * محل مجاني واحد لكل حساب: أول محل أنشأه المستخدم (حتى 9 منتجات).
     * باقي المحلات تحتاج تفعيلًا من أول منتج، حتى لا يتهرب أحد بفتح محلات مجانية كثيرة.
     */
    var freeShopId by mutableStateOf<Long?>(null)

    fun isFreeShop(shop: Shop) = freeShopId == null || shop.id == freeShopId

    fun freeLimit(shop: Shop) = if (isFreeShop(shop)) FREE_LIMIT else 0

    fun limit(shop: Shop, now: Long): Int = if (!isActive(shop, now)) freeLimit(shop) else when (shop.tier) {
        1 -> 50
        2 -> 100
        else -> Int.MAX_VALUE
    }

    fun limitText(shop: Shop, now: Long): String {
        val l = limit(shop, now)
        return if (l == Int.MAX_VALUE) tr("بلا حد") else l.toString()
    }

    /**
     * المحل مقفل إذا تجاوز عدد منتجاته الحد المجاني وانتهى تفعيله (أو لم يُفعّل):
     * يُمنع البيع والشراء حتى التجديد، وتبقى التقارير والبيانات كاملة.
     */
    fun isLocked(shop: Shop, productCount: Int, now: Long) =
        productCount > freeLimit(shop) && !isActive(shop, now)

    /** أقل باقة تتسع لمنتج إضافي واحد على الأقل */
    fun neededTier(productCount: Int): Int = when {
        productCount + 1 <= 50 -> 1
        productCount + 1 <= 100 -> 2
        else -> 3
    }

    /** عدد الأيام الباقية إذا كان التفعيل سينتهي خلال أسبوع، وإلا null */
    fun daysLeftIfSoon(shop: Shop, now: Long): Int? {
        if (!isActive(shop, now)) return null
        val days = ((shop.expiresAt - now) / 86_400_000L).toInt()
        return if (days <= 7) days else null
    }

    fun label(shop: Shop, now: Long): String =
        if (isActive(shop, now)) tr("مفعّل حتى %1\$s", Dates.formatDay(shop.expiresAt))
        else if (shop.tier > 0) tr("انتهى التفعيل في %1\$s", Dates.formatDay(shop.expiresAt))
        else if (isFreeShop(shop)) tr("مجاني (أقل من 10 منتجات)")
        else tr("غير مفعّل (المحل المجاني هو أول محل لك فقط)")
}
