package com.elyedali.makhzan.data

import com.elyedali.makhzan.i18n.tr
import com.elyedali.makhzan.security.Pins
import kotlin.random.Random

/**
 * محل تجريبي ببيانات افتراضية لشهر كامل (خاص بمدير التطبيق):
 * منتجات بالقطعة وبالوزن، مشتريات نقدًا وبالدَّين، مبيعات يومية، ديون وتسديدات،
 * مصاريف، مرتجع، تالف، مورّدون وعامل — ليرى المدير كل شاشات التطبيق وتقاريره ممتلئة.
 * كل العمليات تمر بنفس دوال التطبيق الحقيقية، فتبقى الأرقام متطابقة.
 */
object DemoData {
    /** المحل التجريبي يُعرف بمعرّفه على الخادم (لا يُسجَّل على الخادم أبدًا) */
    const val PREFIX = "demo-"

    fun isDemo(shop: Shop) = shop.remoteId.startsWith(PREFIX)

    private data class P(val name: String, val buy: Long, val sell: Long, val unit: String = "", val stock: Long)

    // الأسعار بالأوقية (تُضرب في 100)، والمخزون بالوحدة
    private fun products() = listOf(
        P(tr("أرز"), 38, 45, "kg", 200),
        P(tr("سكر"), 36, 42, "kg", 150),
        P(tr("زيت طبخ"), 75, 90, "l", 80),
        P(tr("دقيق القمح"), 28, 35, "kg", 120),
        P(tr("شاي أخضر 250غ"), 55, 70, stock = 60),
        P(tr("حليب مجفف 400غ"), 150, 180, stock = 40),
        P(tr("معكرونة 500غ"), 22, 30, stock = 90),
        P(tr("طماطم مركزة"), 12, 17, stock = 100),
        P(tr("صابون غسيل"), 18, 25, stock = 70),
        P(tr("ماء معدني 1.5ل"), 15, 20, stock = 120),
        P(tr("مشروب غازي"), 25, 35, stock = 96),
        P(tr("بسكويت"), 8, 12, stock = 150),
        P(tr("علبة سردين"), 30, 40, stock = 60),
        P(tr("بيض (طبق 30)"), 140, 165, stock = 20),
        P(tr("خبز"), 8, 10, stock = 60),
        P(tr("بصل"), 15, 22, "kg", 50),
        P(tr("بطاطس"), 18, 25, "kg", 60),
        P(tr("فحم"), 20, 30, "kg", 40),
        P(tr("رصيد هاتف 100"), 97, 100, stock = 30),
        P(tr("مناديل ورقية"), 20, 28, stock = 8),
        P(tr("معجون أسنان"), 35, 50, stock = 4),
        P(tr("بطارية"), 25, 40, stock = 3),
    )

    /** ينشئ المحل التجريبي ويعيد معرّفه */
    suspend fun create(dao: ShopDao): Long {
        val rnd = Random(2026)
        val now = TrustedClock.now()
        val day = 86_400_000L
        val start = now - 30 * day

        val shopId = dao.insertShop(
            Shop(
                name = tr("محل تجريبي"), createdAt = start,
                // مفعّل بلا حد طوال الوقت (محل محلي للمدير فقط، لا يُرسل إلى الخادم)
                tier = 3, expiresAt = now + 3650 * day,
                remoteId = PREFIX + java.util.UUID.randomUUID().toString(),
            )
        )
        dao.saveProfile(
            ShopProfile(
                shopId = shopId, address = tr("نواكشوط - تفرغ زينة"), phone = "22 00 00 00",
                taxNumber = "00000000", design = 2,
            )
        )

        // الموردون والزبائن والعامل
        val sup1 = dao.insertSupplier(Supplier(shopId = shopId, name = tr("تاجر الجملة سيدي"), phone = "22 11 11 11", createdAt = start))
        val sup2 = dao.insertSupplier(Supplier(shopId = shopId, name = tr("شركة المشروبات"), phone = "22 22 22 22", createdAt = start))
        val customers = listOf(tr("محمد الأمين"), tr("فاطمة"), tr("الشيخ أحمد"), tr("مريم")).mapIndexed { i, n ->
            dao.insertCustomer(Customer(shopId = shopId, name = n, phone = "36 00 00 0$i", createdAt = start))
        }
        val worker = dao.insertWorker(
            Worker(shopId = shopId, name = tr("العامل أحمد"), pinHash = Pins.hash("1234"), canCredit = true, createdAt = start)
        )

        // البضاعة الأولى بالدَّين من الموردين، فيبدأ الصندوق من الصفر ويكبر بالمبيعات
        val list = products()
        val items = list.mapIndexed { i, p ->
            PurchaseItem(null, "DEMO-%03d".format(i + 1), p.name, p.buy * 100, p.sell * 100, p.stock * Qty.ONE, p.unit)
        }
        val drinks = setOf(9, 10)
        dao.recordPurchase(shopId, items.filterIndexed { i, _ -> i !in drinks }, start + 2 * 3_600_000L, sup1)
        dao.recordPurchase(shopId, items.filterIndexed { i, _ -> i in drinks }, start + 4 * 3_600_000L, sup2)

        val initial = dao.productsNow(shopId)
        var lastCreditSale: Long? = null
        for (d in 1..29) {
            val dayStart = start + d * day
            // من 4 إلى 9 مبيعات يوميًا بين الثامنة صباحًا والعاشرة ليلًا
            repeat(rnd.nextInt(4, 10)) {
                val time = dayStart + 8 * 3_600_000L + rnd.nextLong(14 * 3_600_000L)
                val fresh = dao.productsNow(shopId).filter { it.qty >= 2 * Qty.ONE }
                if (fresh.isEmpty()) return@repeat
                val picked = fresh.shuffled(rnd).take(rnd.nextInt(1, 4))
                val sale = picked.map { p ->
                    val q = if (Qty.isWeighed(p.unit)) listOf(500L, 1000L, 1500L, 2000L).random(rnd)
                    else Qty.ONE * rnd.nextInt(1, 3)
                    SaleItem(p.id, p.name, p.barcode, minOf(q, p.qty), p.sellPrice, p.buyPrice)
                }
                val credit = rnd.nextInt(10) == 0
                val customer = if (credit) customers.random(rnd) else null
                val byWorker = rnd.nextInt(3) == 0
                val gross = sale.sumOf { Qty.amount(it.unitPrice, it.qty) }
                // تخفيض بسيط أحيانًا (تقريب المبلغ)
                val discount = if (!credit && rnd.nextInt(8) == 0) gross % 1000 else 0L
                val id = dao.recordSale(shopId, sale, time, customer, if (byWorker) worker else null, discount)
                if (credit) lastCreditSale = id
            }
            // مصروف كل أسبوع
            if (d % 7 == 0) {
                dao.insertOperation(Operation(shopId = shopId, type = OpType.EXPENSE, total = 50_000, note = tr("كهرباء"), time = dayStart + 20 * 3_600_000L))
                dao.insertOperation(Operation(shopId = shopId, type = OpType.EXPENSE, total = 20_000, note = tr("نقل البضاعة"), time = dayStart + 21 * 3_600_000L))
            }
            // تسديدات من الزبائن
            if (d % 6 == 0) {
                for (c in customers) {
                    val debt = dao.customerDebtNow(c)
                    if (debt > 0) dao.recordPayment(shopId, c, (debt / 2).coerceAtLeast(1), dayStart + 19 * 3_600_000L)
                }
            }
            // دفعة للمورد في منتصف الشهر
            if (d == 15) {
                val debt = dao.supplierDebtNow(sup1)
                if (debt > 0) dao.recordSupplierPayment(shopId, sup1, debt / 2, dayStart + 18 * 3_600_000L)
            }
            // إعادة تموين بعض السلع في اليوم العاشر
            if (d == 10) {
                val restock = dao.productsNow(shopId).filter { it.qty < 20 * Qty.ONE && it.barcode != "DEMO-021" && it.barcode != "DEMO-022" }
                if (restock.isNotEmpty()) {
                    dao.recordPurchase(
                        shopId,
                        restock.map { PurchaseItem(it.id, it.barcode, it.name, it.buyPrice, it.sellPrice, 30 * Qty.ONE, it.unit) },
                        dayStart + 9 * 3_600_000L,
                    )
                }
            }
        }
        // كراء الشهر
        dao.insertOperation(Operation(shopId = shopId, type = OpType.EXPENSE, total = 300_000, note = tr("كراء المحل"), time = start + 5 * day))

        // مرتجع: إرجاع جزء من آخر بيع بالدَّين
        lastCreditSale?.let { id ->
            val l = dao.lines(id).firstOrNull()
            if (l != null) {
                val q = if (l.qty > Qty.ONE) Qty.ONE else l.qty
                runCatching { dao.recordReturn(id, mapOf(l.productId to q), now - 2 * day) }
            }
        }
        // تالف
        initial.firstOrNull { it.barcode == "DEMO-014" }?.let { egg ->
            val p = dao.productNow(egg.id)
            if (p != null && p.qty >= Qty.ONE) dao.recordAdjustment(shopId, mapOf(p.id to -Qty.ONE), tr("تالف"), now - day)
        }
        return shopId
    }

    /** يحذف المحل التجريبي بكل بياناته */
    suspend fun delete(dao: ShopDao, shopId: Long) {
        val shop = dao.shopNow(shopId) ?: return
        if (!isDemo(shop)) return
        dao.deleteShopCompletely(shopId)
    }
}
