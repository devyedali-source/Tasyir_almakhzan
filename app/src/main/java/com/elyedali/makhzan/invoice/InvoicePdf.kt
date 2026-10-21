package com.elyedali.makhzan.invoice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import com.elyedali.makhzan.data.Dates
import com.elyedali.makhzan.data.Money
import com.elyedali.makhzan.data.ShopProfile
import java.io.File
import java.io.FileOutputStream

data class InvoiceLine(val name: String, val qty: Int, val unitPrice: Long)

data class InvoiceData(
    val number: Long,
    val time: Long,
    val lines: List<InvoiceLine>,
) {
    val total: Long get() = lines.sumOf { it.unitPrice * it.qty }
}

/** نصوص الفاتورة حسب اللغة: AR عربية، FR فرنسية، EN إنجليزية، BI مزدوجة (عربية + فرنسية) */
class Labels(val lang: String, customCurrency: String = "") {
    val rtl = lang == "AR" || lang == "BI"
    private fun t(ar: String, fr: String, en: String) = when (lang) {
        "FR" -> fr
        "EN" -> en
        "BI" -> "$ar / $fr"
        else -> ar
    }

    val title = t("فاتورة", "FACTURE", "INVOICE")
    val number = t("رقم الفاتورة", "N° Facture", "Invoice No.")
    val date = t("التاريخ", "Date", "Date")
    val product = t("المنتج", "Désignation", "Item")
    val qty = t("الكمية", "Qté", "Qty")
    val unit = t("سعر الوحدة", "P.U.", "Unit price")
    val amount = t("المبلغ", "Montant", "Amount")
    val total = t("المجموع الكلي", "Total", "Total")
    val tax = t("الرقم الضريبي", "NIF", "Tax ID")
    val phone = t("الهاتف", "Tél", "Phone")
    val address = t("العنوان", "Adresse", "Address")
    val email = t("التواصل", "Contact", "Contact")
    val thanks = t("شكرًا لزيارتكم", "Merci de votre visite", "Thank you for your visit")
    val currency = customCurrency.ifBlank { t("أوقية", "MRU", "MRU").let { if (lang == "BI") "أوقية MRU" else it } }
    val itemsCount = t("عدد القطع", "Nb articles", "Items")

    fun money(v: Long) = "${Money.format(v, withCurrency = false)} $currency"
    fun plain(v: Long) = Money.format(v, withCurrency = false)
}

/** ينشئ ملف PDF للفاتورة بأحد التصاميم الثلاثة */
object InvoicePdf {

    private const val A4_W = 595
    private const val A4_H = 842
    private const val RECEIPT_W = 227 // 80 مم

    private val ink = Color.rgb(33, 33, 33)
    private val stampBlue = Color.rgb(21, 67, 160)

    fun create(
        context: Context,
        shopName: String,
        profile: ShopProfile,
        data: InvoiceData,
        fileName: String,
    ): File {
        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val file = File(dir, fileName)
        val labels = Labels(profile.language, profile.currency)
        val logo = profile.logoPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
        val doc = PdfDocument()
        try {
            when (profile.design) {
                2 -> Receipt(doc, shopName, profile, labels, logo, data).draw()
                else -> A4(doc, shopName, profile, labels, logo, data, modern = profile.design == 1).draw()
            }
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return file
    }

    // ---------------- أدوات رسم مشتركة ----------------

    private fun paint(size: Float, bold: Boolean = false, color: Int = ink) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun fit(text: String, p: TextPaint, width: Float): String =
        TextUtils.ellipsize(text, p, width, TextUtils.TruncateAt.END).toString()

    /** يكتب النص من جهة البداية (اليمين في العربية، اليسار في الفرنسية) */
    private fun Canvas.textStart(s: String, left: Float, right: Float, y: Float, p: TextPaint, rtl: Boolean) {
        val f = fit(s, p, right - left)
        p.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        drawText(f, if (rtl) right else left, y, p)
    }

    private fun Canvas.textEnd(s: String, left: Float, right: Float, y: Float, p: TextPaint, rtl: Boolean) {
        val f = fit(s, p, right - left)
        p.textAlign = if (rtl) Paint.Align.LEFT else Paint.Align.RIGHT
        drawText(f, if (rtl) left else right, y, p)
    }

    private fun Canvas.textCenter(s: String, left: Float, right: Float, y: Float, p: TextPaint) {
        val f = fit(s, p, right - left)
        p.textAlign = Paint.Align.CENTER
        drawText(f, (left + right) / 2f, y, p)
    }

    private fun Canvas.drawLogo(logo: Bitmap, box: RectF) {
        val scale = minOf(box.width() / logo.width, box.height() / logo.height)
        val w = logo.width * scale
        val h = logo.height * scale
        val l = box.centerX() - w / 2
        val t = box.centerY() - h / 2
        drawBitmap(logo, null, RectF(l, t, l + w, t + h), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /** الطابع: دائرتان وبداخلهما اسم المحل والرقم الضريبي ورقم الهاتف، مائل قليلًا */
    private fun Canvas.drawStamp(cx: Float, cy: Float, r: Float, shopName: String, profile: ShopProfile, labels: Labels) {
        save()
        rotate(-12f, cx, cy)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = stampBlue
            alpha = 210
        }
        stroke.strokeWidth = 2.2f
        drawCircle(cx, cy, r, stroke)
        stroke.strokeWidth = 1f
        drawCircle(cx, cy, r - 5f, stroke)
        val inner = (r - 9f) * 1.6f
        val pName = paint(r * 0.22f, bold = true, color = stampBlue).apply { alpha = 220 }
        val pInfo = paint(r * 0.16f, color = stampBlue).apply { alpha = 220 }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = stampBlue; alpha = 200; strokeWidth = 0.8f }
        textCenter(shopName, cx - inner / 2, cx + inner / 2, cy - r * 0.18f, pName)
        drawLine(cx - inner / 2.2f, cy - r * 0.06f, cx + inner / 2.2f, cy - r * 0.06f, line)
        if (profile.taxNumber.isNotBlank()) {
            textCenter("${labels.tax}: ${profile.taxNumber}", cx - inner / 2, cx + inner / 2, cy + r * 0.2f, pInfo)
        }
        if (profile.phone.isNotBlank()) {
            textCenter("${labels.phone}: ${profile.phone}", cx - inner / 2, cx + inner / 2, cy + r * 0.45f, pInfo)
        }
        restore()
    }

    // ---------------- تصميم A4 (كلاسيكي / عصري) ----------------

    private class A4(
        val doc: PdfDocument,
        val shopName: String,
        val profile: ShopProfile,
        val labels: Labels,
        val logo: Bitmap?,
        val data: InvoiceData,
        val modern: Boolean,
    ) {
        val rtl = labels.rtl
        val L = 40f
        val R = A4_W - 40f
        val accent = if (modern) Color.rgb(11, 110, 79) else ink
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f

        // أعمدة الجدول بالترتيب المنطقي: المنتج، الكمية، سعر الوحدة، المبلغ
        val widths = floatArrayOf(0.46f, 0.14f, 0.20f, 0.20f)

        fun col(i: Int): Pair<Float, Float> {
            val total = R - L
            var s = 0f
            for (k in 0 until i) s += widths[k] * total
            val w = widths[i] * total
            return if (rtl) (R - s - w) to (R - s) else (L + s) to (L + s + w)
        }

        fun newPage() {
            if (pageNo > 0) doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, pageNo).create())
            c = page.canvas
            y = if (pageNo == 1) header() else 50f
            tableHeader()
        }

        fun header(): Float {
            val top = 36f
            val bandBottom = 150f
            val textColor = if (modern) Color.WHITE else ink
            if (modern) {
                c.drawRect(0f, 0f, A4_W.toFloat(), bandBottom, Paint().apply { color = accent })
            }
            // الشعار في جهة النهاية، والمعلومات في جهة البداية
            val logoSize = 90f
            val logoBox = if (rtl) RectF(L, top, L + logoSize, top + logoSize) else RectF(R - logoSize, top, R, top + logoSize)
            if (logo != null) {
                if (modern) {
                    c.drawRoundRect(
                        RectF(logoBox.left - 4, logoBox.top - 4, logoBox.right + 4, logoBox.bottom + 4),
                        8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                    )
                }
                c.drawLogo(logo, logoBox)
            }
            val infoL = if (rtl) L + logoSize + 16 else L
            val infoR = if (rtl) R else R - logoSize - 16
            var yy = top + 18
            c.textStart(shopName, infoL, infoR, yy, paint(20f, true, textColor), rtl)
            val pInfo = paint(10.5f, color = textColor)
            yy += 20
            if (profile.address.isNotBlank()) { c.textStart("${labels.address}: ${profile.address}", infoL, infoR, yy, pInfo, rtl); yy += 15 }
            if (profile.phone.isNotBlank()) { c.textStart("${labels.phone}: ${profile.phone}", infoL, infoR, yy, pInfo, rtl); yy += 15 }
            if (profile.email.isNotBlank()) { c.textStart("${labels.email}: ${profile.email}", infoL, infoR, yy, pInfo, rtl); yy += 15 }
            if (profile.taxNumber.isNotBlank()) { c.textStart("${labels.tax}: ${profile.taxNumber}", infoL, infoR, yy, pInfo, rtl); yy += 15 }

            var after = maxOf(yy, top + logoSize) + 12
            if (modern) after = maxOf(after, bandBottom) + 24
            else {
                c.drawLine(L, after, R, after, Paint().apply { color = ink; strokeWidth = 1.5f })
                after += 26
            }
            // العنوان ورقم الفاتورة والتاريخ
            c.textCenter(labels.title, L, R, after, paint(18f, true, accent))
            after += 22
            val pMeta = paint(10.5f)
            c.textStart("${labels.number}: ${data.number}", L, R, after, pMeta, rtl)
            c.textEnd("${labels.date}: ${Dates.format(data.time)}", L, R, after, pMeta, rtl)
            return after + 18
        }

        fun tableHeader() {
            val h = 24f
            val bg = Paint().apply { color = if (modern) Color.rgb(205, 239, 225) else Color.rgb(235, 235, 235) }
            c.drawRect(L, y, R, y + h, bg)
            val p = paint(10.5f, true)
            val names = listOf(labels.product, labels.qty, labels.unit, labels.amount)
            for (i in 0..3) {
                val (l, r) = col(i)
                if (i == 0) c.textStart(names[i], l + 6, r - 6, y + 16, p, rtl)
                else c.textCenter(names[i], l + 2, r - 2, y + 16, p)
            }
            if (!modern) c.drawRect(L, y, R, y + h, Paint().apply { style = Paint.Style.STROKE; color = ink })
            y += h
        }

        fun draw() {
            newPage()
            val rowH = 22f
            val p = paint(10.5f)
            val border = Paint().apply { style = Paint.Style.STROKE; color = Color.rgb(170, 170, 170); strokeWidth = 0.6f }
            val zebra = Paint().apply { color = Color.rgb(246, 248, 247) }
            data.lines.forEachIndexed { idx, line ->
                if (y + rowH > A4_H - 60) newPage()
                if (modern && idx % 2 == 1) c.drawRect(L, y, R, y + rowH, zebra)
                val values = listOf(line.name, line.qty.toString(), labels.plain(line.unitPrice), labels.plain(line.unitPrice * line.qty))
                for (i in 0..3) {
                    val (l, r) = col(i)
                    if (i == 0) c.textStart(values[i], l + 6, r - 6, y + 15, p, rtl)
                    else c.textCenter(values[i], l + 2, r - 2, y + 15, p)
                    if (!modern) c.drawRect(l, y, r, y + rowH, border)
                }
                if (modern) c.drawLine(L, y + rowH, R, y + rowH, border)
                y += rowH
            }
            // المجموع ثم الطابع: نحتاج حوالي 190 نقطة
            if (y + 190 > A4_H - 40) { doc.finishPage(page); pageNo++; page = doc.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, pageNo).create()); c = page.canvas; y = 50f }
            y += 12
            val boxW = 230f
            val box = if (rtl) RectF(L, y, L + boxW, y + 30) else RectF(R - boxW, y, R, y + 30)
            c.drawRect(box, Paint().apply { color = if (modern) accent else Color.rgb(235, 235, 235) })
            val tColor = if (modern) Color.WHITE else ink
            c.textStart(labels.total, box.left + 8, box.right - 8, y + 20, paint(12f, true, tColor), rtl)
            c.textEnd(labels.money(data.total), box.left + 8, box.right - 8, y + 20, paint(12f, true, tColor), rtl)
            val countL = if (rtl) L + boxW + 10 else L
            val countR = if (rtl) R else R - boxW - 10
            c.textStart("${labels.itemsCount}: ${data.lines.sumOf { it.qty }}", countL, countR, y + 20, paint(10f), rtl)
            y += 50
            if (profile.showStamp) {
                val r = 58f
                val cx = if (rtl) L + 90 else R - 90
                c.drawStamp(cx, y + r + 6, r, shopName, profile, labels)
            }
            c.textCenter(labels.thanks, L, R, A4_H - 30f, paint(10f, color = Color.rgb(100, 100, 100)))
            doc.finishPage(page)
        }
    }

    // ---------------- تصميم الإيصال 80 مم ----------------

    private class Receipt(
        val doc: PdfDocument,
        val shopName: String,
        val profile: ShopProfile,
        val labels: Labels,
        val logo: Bitmap?,
        val data: InvoiceData,
    ) {
        val rtl = labels.rtl
        val L = 10f
        val R = RECEIPT_W - 10f

        fun draw() {
            val infoLines = listOf(profile.address, profile.phone, profile.email, profile.taxNumber).count { it.isNotBlank() }
            val height = (20 + (if (logo != null) 70 else 0) + 26 + infoLines * 13 + 80 +
                data.lines.size * 30 + 70 + (if (profile.showStamp) 130 else 0) + 30)
            val page = doc.startPage(PdfDocument.PageInfo.Builder(RECEIPT_W, height, 1).create())
            val c = page.canvas
            var y = 14f
            if (logo != null) {
                c.drawLogo(logo, RectF(RECEIPT_W / 2f - 32, y, RECEIPT_W / 2f + 32, y + 64))
                y += 70
            }
            y += 14
            c.textCenter(shopName, L, R, y, paint(14f, true))
            val pSmall = paint(8.5f)
            y += 14
            if (profile.address.isNotBlank()) { c.textCenter(profile.address, L, R, y, pSmall); y += 13 }
            if (profile.phone.isNotBlank()) { c.textCenter("${labels.phone}: ${profile.phone}", L, R, y, pSmall); y += 13 }
            if (profile.email.isNotBlank()) { c.textCenter(profile.email, L, R, y, pSmall); y += 13 }
            if (profile.taxNumber.isNotBlank()) { c.textCenter("${labels.tax}: ${profile.taxNumber}", L, R, y, pSmall); y += 13 }
            val dash = Paint().apply { color = ink; strokeWidth = 0.7f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 2f), 0f) }
            y += 4
            c.drawLine(L, y, R, y, dash)
            y += 16
            c.textCenter(labels.title, L, R, y, paint(11f, true))
            y += 14
            c.textCenter("${labels.number}: ${data.number}", L, R, y, pSmall)
            y += 12
            c.textCenter(Dates.format(data.time), L, R, y, pSmall)
            y += 8
            c.drawLine(L, y, R, y, dash)
            y += 14
            val pItem = paint(9f, true)
            val pDet = paint(8.5f)
            for (line in data.lines) {
                c.textStart(line.name, L, R, y, pItem, rtl)
                y += 13
                c.textStart("${line.qty} × ${labels.plain(line.unitPrice)}", L, R, y, pDet, rtl)
                c.textEnd(labels.plain(line.unitPrice * line.qty), L, R, y, pDet, rtl)
                y += 17
            }
            c.drawLine(L, y - 6, R, y - 6, dash)
            y += 12
            c.textStart(labels.total, L, R, y, paint(11f, true), rtl)
            c.textEnd(labels.money(data.total), L, R, y, paint(11f, true), rtl)
            y += 22
            if (profile.showStamp) {
                val r = 52f
                c.drawStamp(RECEIPT_W / 2f, y + r + 4, r, shopName, profile, labels)
                y += 2 * r + 20
            }
            c.textCenter(labels.thanks, L, R, y + 6, paint(8.5f))
            doc.finishPage(page)
        }
    }
}
