package com.elyedali.makhzan.data

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
    const val CURRENCY = "أوقية"

    fun format(minor: Long, withCurrency: Boolean = true): String {
        val a = abs(minor)
        val whole = a / 100
        val frac = a % 100
        val num = if (frac == 0L) String.format(Locale.US, "%,d", whole)
        else String.format(Locale.US, "%,d.%02d", whole, frac)
        val sign = if (minor < 0) "-" else ""
        return if (withCurrency) "$sign$num $CURRENCY" else "$sign$num"
    }

    /** يحول النص المكتوب إلى مبلغ بالوحدة الصغرى، أو null إذا لم يكن صالحًا */
    fun parse(text: String): Long? {
        val t = text.trim().westernDigits()
        if (t.isEmpty()) return null
        val bd = t.toBigDecimalOrNull() ?: return null
        if (bd.signum() < 0) return null
        return try {
            bd.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    fun toInput(minor: Long): String =
        BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString()
}

object Qty {
    fun parse(text: String): Int? = text.trim().westernDigits().toIntOrNull()?.takeIf { it > 0 }
}

object Dates {
    private val full = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
    private val day = SimpleDateFormat("yyyy/MM/dd", Locale.US)

    fun format(t: Long): String = full.format(Date(t))
    fun formatDay(t: Long): String = day.format(Date(t))

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

    fun limit(shop: Shop, now: Long): Int = if (!isActive(shop, now)) FREE_LIMIT else when (shop.tier) {
        1 -> 50
        2 -> 100
        else -> Int.MAX_VALUE
    }

    fun limitText(shop: Shop, now: Long): String {
        val l = limit(shop, now)
        return if (l == Int.MAX_VALUE) "بلا حد" else l.toString()
    }

    fun label(shop: Shop, now: Long): String =
        if (isActive(shop, now)) "مفعّل حتى ${Dates.formatDay(shop.expiresAt)}"
        else "مجاني (أقل من 10 منتجات)"
}
