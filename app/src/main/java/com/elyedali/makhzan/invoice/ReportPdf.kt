package com.elyedali.makhzan.invoice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import com.elyedali.makhzan.data.Customer
import com.elyedali.makhzan.data.CustomerDebt
import com.elyedali.makhzan.data.DaySales
import com.elyedali.makhzan.data.NamedTotal
import com.elyedali.makhzan.data.ProductLastSale
import com.elyedali.makhzan.data.Dates
import com.elyedali.makhzan.data.Money
import com.elyedali.makhzan.data.OpType
import com.elyedali.makhzan.data.Operation
import com.elyedali.makhzan.data.Product
import com.elyedali.makhzan.data.ProductSales
import com.elyedali.makhzan.data.TrustedClock
import com.elyedali.makhzan.data.isCredit
import com.elyedali.makhzan.i18n.I18n
import com.elyedali.makhzan.i18n.tr
import java.io.File
import java.io.FileOutputStream

/** ملخص فترة بين تاريخين */
data class PeriodSummary(
    val from: Long,
    val toExclusive: Long,
    val openingCash: Long,
    val closingCash: Long,
    val cashSales: Long,
    val creditSales: Long,
    val payments: Long,
    val purchases: Long,
    val expenses: Long,
    val profit: Long,
) {
    val netProfit: Long get() = profit - expenses
}

/**
 * تقارير PDF بلغة الواجهة: تقرير الفترة (الصندوق، المبيعات، أكثر المنتجات مبيعًا، العمليات)
 * وتقرير المخزون والوضع الراهن.
 */
object ReportPdf {
    private const val W = 595
    private const val H = 842
    private const val L = 36f
    private const val R = W - 36f
    private val ink = Color.rgb(33, 33, 33)
    private val green = Color.rgb(11, 110, 79)

    private class Writer(val doc: PdfDocument, val shopName: String, val title: String, val subtitle: String) {
        val rtl = I18n.isRtl
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f

        fun p(size: Float, bold: Boolean = false, color: Int = ink) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        fun fit(s: String, paint: TextPaint, w: Float) = TextUtils.ellipsize(s, paint, w, TextUtils.TruncateAt.END).toString()

        /** يكتب في عمود محدد بالترتيب المنطقي: البداية يمين في العربية ويسار في غيرها */
        fun cell(s: String, start: Float, width: Float, paint: TextPaint, alignEnd: Boolean = false) {
            val left = if (rtl) R - start - width else L + start
            val right = left + width
            val t = fit(s, paint, width - 4)
            val toRight = rtl != alignEnd
            paint.textAlign = if (toRight) Paint.Align.RIGHT else Paint.Align.LEFT
            c.drawText(t, if (toRight) right - 2 else left + 2, y, paint)
        }

        fun newPage() {
            if (pageNo > 0) doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page.canvas
            y = 44f
            if (pageNo == 1) {
                c.drawRect(0f, 0f, W.toFloat(), 92f, Paint().apply { color = green })
                cell(shopName, 0f, R - L, p(18f, true, Color.WHITE)); y += 24
                cell(title, 0f, R - L, p(13f, true, Color.WHITE)); y += 18
                cell(subtitle, 0f, R - L, p(10f, color = Color.WHITE))
                y = 118f
            }
            val foot = p(8f, color = Color.GRAY)
            foot.textAlign = Paint.Align.CENTER
            c.drawText("${Dates.format(TrustedClock.now())} — $pageNo", W / 2f, H - 18f, foot)
        }

        fun ensure(space: Float) { if (y + space > H - 40) newPage() }

        fun section(text: String) {
            ensure(40f)
            y += 10
            cell(text, 0f, R - L, p(12.5f, true, green))
            y += 6
            c.drawLine(L, y, R, y, Paint().apply { color = green; strokeWidth = 1f })
            y += 16
        }

        fun row(label: String, value: String, bold: Boolean = false) {
            ensure(18f)
            val pt = p(10.5f, bold)
            cell(label, 0f, (R - L) * 0.6f, pt)
            cell(value, (R - L) * 0.6f, (R - L) * 0.4f, p(10.5f, true), alignEnd = true)
            y += 18
        }

        /** جدول: widths نسب من العرض، header ثم الصفوف */
        fun table(headers: List<String>, widths: List<Float>, rows: List<List<String>>) {
            val total = R - L
            fun line(values: List<String>, paint: TextPaint, bg: Int?) {
                ensure(18f)
                if (bg != null) c.drawRect(L, y - 12, R, y + 5, Paint().apply { color = bg })
                var start = 0f
                values.forEachIndexed { i, v ->
                    val w = widths[i] * total
                    cell(v, start, w, paint, alignEnd = i > 0)
                    start += w
                }
                y += 18
            }
            line(headers, p(9.5f, true), Color.rgb(225, 240, 233))
            rows.forEachIndexed { i, r -> line(r, p(9.5f), if (i % 2 == 1) Color.rgb(247, 248, 247) else null) }
        }

        fun finish(file: File) {
            doc.finishPage(page)
            FileOutputStream(file).use { doc.writeTo(it) }
        }
    }

    private fun out(context: Context, name: String) =
        File(File(context.cacheDir, "invoices").apply { mkdirs() }, name)

    fun period(
        context: Context,
        shopName: String,
        s: PeriodSummary,
        top: List<ProductSales>,
        ops: List<Operation>,
        customers: Map<Long, Customer>,
        daily: List<DaySales>,
        expenses: List<NamedTotal>,
    ): File {
        val doc = PdfDocument()
        try {
            val lastDay = s.toExclusive - 1
            val w = Writer(doc, shopName, tr("تقرير الفترة"), tr("من %1\$s إلى %2\$s", Dates.formatDay(s.from), Dates.formatDay(lastDay)))
            w.newPage()
            w.section(tr("الصندوق"))
            w.row(tr("رصيد بداية الفترة"), Money.format(s.openingCash))
            w.row(tr("المبيعات النقدية"), "+" + Money.format(s.cashSales))
            w.row(tr("مقبوضات الديون"), "+" + Money.format(s.payments))
            w.row(tr("المشتريات"), "−" + Money.format(s.purchases))
            w.row(tr("المصاريف"), "−" + Money.format(s.expenses))
            w.row(tr("رصيد نهاية الفترة"), Money.format(s.closingCash), bold = true)
            w.section(tr("النتيجة"))
            w.row(tr("مجموع المبيعات (نقدًا وبالدَّين)"), Money.format(s.cashSales + s.creditSales))
            w.row(tr("منها بالدَّين"), Money.format(s.creditSales))
            w.row(tr("ربح المبيعات"), Money.format(s.profit))
            w.row(tr("الربح الصافي"), Money.format(s.netProfit), bold = true)

            if (top.isNotEmpty()) {
                w.section(tr("المبيعات حسب المنتج"))
                w.table(
                    listOf(tr("المنتج"), tr("الكمية"), tr("المبلغ"), tr("الربح")),
                    listOf(0.46f, 0.14f, 0.20f, 0.20f),
                    top.map { listOf(it.name, it.qty.toString(), Money.format(it.amount, false), Money.format(it.profit, false)) },
                )
            }
            if (daily.size > 1) {
                w.section(tr("المبيعات اليومية"))
                w.table(
                    listOf(tr("اليوم"), tr("المبيعات"), tr("الربح")),
                    listOf(0.40f, 0.30f, 0.30f),
                    daily.map { listOf(it.day, Money.format(it.amount, false), Money.format(it.profit, false)) },
                )
            }
            if (expenses.isNotEmpty()) {
                w.section(tr("المصاريف حسب البيان"))
                w.table(
                    listOf(tr("البيان"), tr("العدد"), tr("المبلغ")),
                    listOf(0.50f, 0.20f, 0.30f),
                    expenses.map { listOf(it.name, it.count.toString(), Money.format(it.amount, false)) },
                )
            }
            w.section(tr("العمليات"))
            if (ops.isEmpty()) w.row(tr("لا توجد عمليات في هذه الفترة"), "")
            else w.table(
                listOf(tr("التاريخ"), tr("العملية"), tr("المبلغ")),
                listOf(0.34f, 0.40f, 0.26f),
                ops.map { op ->
                    val who = op.customerId?.let { customers[it]?.name }
                    val kind = (if (op.isCredit) tr("بيع بالدَّين") else OpType.label(op.type)) +
                        (who?.let { " — $it" } ?: "") + (if (op.note.isNotBlank()) " — ${op.note}" else "")
                    listOf(Dates.format(op.time), kind, Money.format(op.total, false))
                },
            )
            val f = out(context, "report_period.pdf")
            w.finish(f)
            return f
        } finally {
            doc.close()
        }
    }

    /** تقرير الديون: كل الزبائن الذين عليهم مبالغ، الأكبر أولًا */
    fun debts(context: Context, shopName: String, list: List<CustomerDebt>): File {
        val doc = PdfDocument()
        try {
            val w = Writer(doc, shopName, tr("تقرير الديون"), Dates.format(TrustedClock.now()))
            w.newPage()
            val owing = list.filter { it.debt > 0 }
            w.section(tr("الملخص"))
            w.row(tr("مجموع الديون على الزبائن"), Money.format(owing.sumOf { it.debt }), bold = true)
            w.row(tr("عدد الزبائن المدينين"), owing.size.toString())
            w.section(tr("الزبائن"))
            if (owing.isEmpty()) w.row(tr("لا دَين"), "")
            else w.table(
                listOf(tr("اسم الزبون"), tr("الهاتف"), tr("الدَّين المتبقي")),
                listOf(0.44f, 0.26f, 0.30f),
                owing.map { listOf(it.customer.name, it.customer.phone, Money.format(it.debt, false)) },
            )
            val f = out(context, "report_debts.pdf")
            w.finish(f)
            return f
        } finally {
            doc.close()
        }
    }

    /** كشف حساب زبون: كل مشترياته بالدَّين وتسديداته مع الرصيد بعد كل عملية */
    fun customerStatement(context: Context, shopName: String, customer: Customer, ops: List<Operation>): File {
        val doc = PdfDocument()
        try {
            val w = Writer(doc, shopName, tr("كشف حساب: %1\$s", customer.name), Dates.format(TrustedClock.now()))
            w.newPage()
            var balance = 0L
            val rows = ops.map { op ->
                val isPayment = op.type == OpType.PAYMENT
                balance += if (isPayment) -op.total else op.total
                listOf(
                    Dates.format(op.time),
                    if (isPayment) tr("تسديد") else tr("شراء بالدَّين"),
                    (if (isPayment) "−" else "+") + Money.format(op.total, false),
                    Money.format(balance, false),
                )
            }
            w.section(tr("الملخص"))
            if (customer.phone.isNotBlank()) w.row(tr("الهاتف"), customer.phone)
            w.row(tr("الدَّين المتبقي"), Money.format(balance), bold = true)
            w.section(tr("السجل"))
            if (rows.isEmpty()) w.row(tr("لا توجد عمليات"), "")
            else w.table(
                listOf(tr("التاريخ"), tr("العملية"), tr("المبلغ"), tr("الرصيد")),
                listOf(0.30f, 0.28f, 0.21f, 0.21f),
                rows,
            )
            val f = out(context, "statement_${customer.id}.pdf")
            w.finish(f)
            return f
        } finally {
            doc.close()
        }
    }

    /** منتجات تحتاج إعادة تموين (5 قطع أو أقل) وسلع راكدة لم تُبع منذ 30 يومًا */
    fun restock(context: Context, shopName: String, list: List<ProductLastSale>): File {
        val doc = PdfDocument()
        try {
            val now = TrustedClock.now()
            val w = Writer(doc, shopName, tr("تقرير التموين والسلع الراكدة"), Dates.format(now))
            w.newPage()
            val low = list.filter { it.product.qty <= RESTOCK_LEVEL }
            val slowLimit = now - 30L * 86_400_000L
            val slow = list.filter { it.product.qty > 0 && (it.lastSale ?: 0L) < slowLimit && it.product.createdAt < slowLimit }
            w.section(tr("منتجات تحتاج إعادة تموين (%1\$s قطع أو أقل)", RESTOCK_LEVEL))
            if (low.isEmpty()) w.row(tr("لا شيء"), "")
            else w.table(
                listOf(tr("المنتج"), tr("الكمية"), tr("متوسط الشراء"), tr("آخر بيع")),
                listOf(0.42f, 0.14f, 0.20f, 0.24f),
                low.map {
                    listOf(it.product.name, it.product.qty.toString(), Money.format(it.product.buyPrice, false),
                        it.lastSale?.let { t -> Dates.formatDay(t) } ?: "—")
                },
            )
            w.section(tr("سلع راكدة: لم تُبع منذ 30 يومًا"))
            if (slow.isEmpty()) w.row(tr("لا شيء"), "")
            else w.table(
                listOf(tr("المنتج"), tr("الكمية"), tr("القيمة"), tr("آخر بيع")),
                listOf(0.42f, 0.14f, 0.20f, 0.24f),
                slow.map {
                    listOf(it.product.name, it.product.qty.toString(),
                        Money.format(it.product.qty.toLong() * it.product.buyPrice, false),
                        it.lastSale?.let { t -> Dates.formatDay(t) } ?: tr("لم يُبع"))
                },
            )
            if (slow.isNotEmpty()) {
                w.row(tr("مال مجمّد في السلع الراكدة"), Money.format(slow.sumOf { it.product.qty.toLong() * it.product.buyPrice }), bold = true)
            }
            val f = out(context, "report_restock.pdf")
            w.finish(f)
            return f
        } finally {
            doc.close()
        }
    }

    const val RESTOCK_LEVEL = 5

    fun stock(
        context: Context,
        shopName: String,
        products: List<Product>,
        cashBalance: Long,
        totalDebt: Long,
    ): File {
        val doc = PdfDocument()
        try {
            val w = Writer(doc, shopName, tr("تقرير المخزون والوضع الراهن"), Dates.format(TrustedClock.now()))
            w.newPage()
            val stockCost = products.sumOf { it.qty.toLong() * it.buyPrice }
            val stockSell = products.sumOf { it.qty.toLong() * it.sellPrice }
            w.section(tr("الوضع الراهن"))
            w.row(tr("رصيد الصندوق"), Money.format(cashBalance))
            w.row(tr("الديون على الزبائن"), Money.format(totalDebt))
            w.row(tr("قيمة المخزون (بسعر الشراء)"), Money.format(stockCost))
            w.row(tr("قيمة المخزون (بسعر البيع)"), Money.format(stockSell))
            w.row(tr("مجموع ما يملكه المحل"), Money.format(cashBalance + totalDebt + stockCost), bold = true)
            w.row(tr("عدد المنتجات"), products.size.toString())
            w.row(tr("منتجات نفدت"), products.count { it.qty <= 0 }.toString())

            w.section(tr("المخزون"))
            w.table(
                listOf(tr("المنتج"), tr("الكمية"), tr("متوسط الشراء"), tr("سعر البيع"), tr("القيمة")),
                listOf(0.36f, 0.12f, 0.17f, 0.17f, 0.18f),
                products.map {
                    listOf(
                        it.name, it.qty.toString(), Money.format(it.buyPrice, false),
                        Money.format(it.sellPrice, false), Money.format(it.qty.toLong() * it.buyPrice, false),
                    )
                },
            )
            val f = out(context, "report_stock.pdf")
            w.finish(f)
            return f
        } finally {
            doc.close()
        }
    }
}
