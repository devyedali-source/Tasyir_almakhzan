package com.elyedali.makhzan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.elyedali.makhzan.i18n.tr
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ShopDao {

    // ---------- المحلات ----------
    @Query("SELECT * FROM shops ORDER BY createdAt")
    abstract fun shops(): Flow<List<Shop>>

    @Query("SELECT * FROM shops WHERE id = :id")
    abstract fun shop(id: Long): Flow<Shop?>

    @Query("SELECT * FROM shops WHERE id = :id")
    abstract suspend fun shopNow(id: Long): Shop?

    @Insert
    abstract suspend fun insertShop(shop: Shop): Long

    @Query("SELECT MIN(id) FROM shops WHERE remoteId NOT LIKE 'demo-%'")
    abstract fun firstShopId(): Flow<Long?>

    @Query("SELECT * FROM shops WHERE remoteId = :remoteId LIMIT 1")
    abstract suspend fun shopByRemoteId(remoteId: String): Shop?

    /** عدد المحلات المفعّلة الآن (لإخفاء الإعلانات عمّن فعّل أي خدمة) */
    @Query("SELECT COUNT(*) FROM shops WHERE tier > 0 AND expiresAt > :now")
    abstract fun activeShopCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM shops")
    abstract suspend fun shopCount(): Int

    @Query("SELECT * FROM shops WHERE remoteId = ''")
    abstract suspend fun shopsWithoutRemoteId(): List<Shop>

    @Query("UPDATE shops SET remoteId = :remoteId WHERE id = :id")
    abstract suspend fun setRemoteId(id: Long, remoteId: String)

    @Query("UPDATE shops SET tier = :tier, expiresAt = :expiresAt WHERE id = :id")
    abstract suspend fun updateActivation(id: Long, tier: Int, expiresAt: Long)

    @Query("UPDATE shops SET name = :name WHERE id = :id")
    abstract suspend fun updateShopName(id: Long, name: String)

    // ---------- معلومات المحل وإعدادات الفاتورة ----------
    @Query("SELECT * FROM shop_profile WHERE shopId = :shopId")
    abstract suspend fun profileNow(shopId: Long): ShopProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveProfile(profile: ShopProfile)

    // ---------- المنتجات ----------
    @Query("SELECT * FROM products WHERE shopId = :shopId ORDER BY name")
    abstract fun products(shopId: Long): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    abstract fun product(id: Long): Flow<Product?>

    @Query("SELECT * FROM products WHERE id = :id")
    abstract suspend fun productNow(id: Long): Product?

    @Query("SELECT * FROM products WHERE shopId = :shopId AND barcode = :barcode LIMIT 1")
    abstract suspend fun findByBarcode(shopId: Long, barcode: String): Product?

    @Query("SELECT COUNT(*) FROM products WHERE shopId = :shopId")
    abstract suspend fun productCount(shopId: Long): Int

    @Query("SELECT COUNT(*) FROM products WHERE shopId = :shopId")
    abstract fun productCountFlow(shopId: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(qty * buyPrice), 0) / 1000 FROM products WHERE shopId = :shopId")
    abstract fun stockValue(shopId: Long): Flow<Long>

    @Insert
    abstract suspend fun insertProduct(product: Product): Long

    @Query("UPDATE products SET qty = qty + :delta WHERE id = :id")
    abstract suspend fun addQty(id: Long, delta: Long)

    @Query("UPDATE products SET buyPrice = :cost WHERE id = :id")
    abstract suspend fun setCost(id: Long, cost: Long)

    @Query("UPDATE products SET buyPrice = :buy, sellPrice = :sell WHERE id = :id")
    abstract suspend fun updatePrices(id: Long, buy: Long, sell: Long)

    /** حذف منتج كميته صفر (مثلًا سُجّل خطأً). سجل حركاته القديمة يبقى باسمه. */
    @Query("DELETE FROM products WHERE id = :id AND qty = 0")
    abstract suspend fun deleteEmptyProduct(id: Long): Int

    @Query("UPDATE products SET name = :name, sellPrice = :sell, unit = :unit WHERE id = :id")
    abstract suspend fun updateProductInfo(id: Long, name: String, sell: Long, unit: String)

    /** منتجات وصلت إلى حد إعادة التموين أو نفدت (للتنبيه في الصفحة الرئيسية) */
    @Query("SELECT COUNT(*) FROM products WHERE shopId = :shopId AND qty <= :level")
    abstract fun lowStockCount(shopId: Long, level: Long): Flow<Int>

    // ---------- العمليات (الصندوق) ----------
    @Insert
    abstract suspend fun insertOperation(op: Operation): Long

    @Insert
    abstract suspend fun insertLine(line: OperationLine): Long

    @Query("SELECT * FROM operations WHERE shopId = :shopId AND time >= :from ORDER BY time DESC")
    abstract fun operations(shopId: Long, from: Long): Flow<List<Operation>>

    @Query("SELECT * FROM operations WHERE id = :id")
    abstract suspend fun operationNow(id: Long): Operation?

    @Query("SELECT * FROM operation_lines WHERE opId = :opId")
    abstract suspend fun lines(opId: Long): List<OperationLine>

    /** رصيد الصندوق: البيع النقدي والتسديد يدخلان، الشراء والمصروف يخرجان، والبيع بالدَّين لا يُحسب */
    @Query("SELECT COALESCE(SUM($CASH_CASE), 0) FROM operations WHERE shopId = :shopId")
    abstract fun balance(shopId: Long): Flow<Long>

    // ---------- التقارير بين تاريخين (from مشمول، to غير مشمول) ----------
    @Query("SELECT COALESCE(SUM(total), 0) FROM operations WHERE shopId = :shopId AND type = :type AND time >= :from AND time < :to")
    abstract suspend fun sumTypeRange(shopId: Long, type: String, from: Long, to: Long): Long

    @Query("SELECT COALESCE(SUM(total), 0) FROM operations WHERE shopId = :shopId AND type = 'SALE' AND customerId IS NOT NULL AND time >= :from AND time < :to")
    abstract suspend fun creditSalesRange(shopId: Long, from: Long, to: Long): Long

    @Query("SELECT COALESCE(SUM(total), 0) FROM operations WHERE shopId = :shopId AND type = 'RETURN' AND customerId IS NOT NULL AND time >= :from AND time < :to")
    abstract suspend fun creditReturnsRange(shopId: Long, from: Long, to: Long): Long

    @Query("SELECT COALESCE(SUM(total), 0) FROM operations WHERE shopId = :shopId AND type = 'PURCHASE' AND supplierId IS NOT NULL AND time >= :from AND time < :to")
    abstract suspend fun creditPurchasesRange(shopId: Long, from: Long, to: Long): Long

    @Query("SELECT COALESCE(SUM(discount), 0) FROM operations WHERE shopId = :shopId AND type = 'SALE' AND time >= :from AND time < :to")
    abstract suspend fun discountsRange(shopId: Long, from: Long, to: Long): Long

    /** ربح المبيعات = (سعر البيع − التكلفة) للسطور − التخفيضات − ربح ما أُرجع */
    @Query(PROFIT_SQL)
    abstract suspend fun profitRange(shopId: Long, from: Long, to: Long): Long

    /** رصيد الصندوق قبل لحظة معينة (لحساب رصيد بداية الفترة ونهايتها) */
    @Query("SELECT COALESCE(SUM($CASH_CASE), 0) FROM operations WHERE shopId = :shopId AND time < :before")
    abstract suspend fun balanceBefore(shopId: Long, before: Long): Long

    /** المبيعات الصافية لكل منتج (البيع ناقص المرتجع)، قبل التخفيض الإجمالي على الفاتورة */
    @Query(
        "SELECT l.productName AS name, " +
            "SUM(CASE WHEN o.type = 'SALE' THEN l.qty ELSE -l.qty END) AS qty, " +
            "SUM(CASE WHEN o.type = 'SALE' THEN l.amount ELSE -l.amount END) AS amount, " +
            "SUM(CASE WHEN o.type = 'SALE' THEN l.amount - l.costAmount ELSE l.costAmount - l.amount END) AS profit " +
            "FROM operation_lines l JOIN operations o ON o.id = l.opId " +
            "WHERE o.shopId = :shopId AND o.type IN ('SALE', 'RETURN') AND o.time >= :from AND o.time < :to " +
            "GROUP BY l.productId HAVING qty != 0 OR amount != 0 ORDER BY amount DESC"
    )
    abstract suspend fun productSalesRange(shopId: Long, from: Long, to: Long): List<ProductSales>

    /** خسائر التسوية لكل منتج (تالف، ضائع، فرق جرد) بسعر التكلفة */
    @Query(
        "SELECT l.productName AS name, SUM(l.qty) AS qty, SUM(-l.costAmount) AS amount, 0 AS profit " +
            "FROM operation_lines l JOIN operations o ON o.id = l.opId " +
            "WHERE o.shopId = :shopId AND o.type = 'ADJUST' AND o.time >= :from AND o.time < :to " +
            "GROUP BY l.productId ORDER BY amount DESC"
    )
    abstract suspend fun adjustmentsRange(shopId: Long, from: Long, to: Long): List<ProductSales>

    @Query("SELECT * FROM operations WHERE shopId = :shopId AND time >= :from AND time < :to ORDER BY time")
    abstract suspend fun operationsRange(shopId: Long, from: Long, to: Long): List<Operation>

    @Query("SELECT * FROM products WHERE shopId = :shopId ORDER BY name")
    abstract suspend fun productsNow(shopId: Long): List<Product>

    @Query(
        "SELECT strftime('%Y/%m/%d', time / 1000, 'unixepoch', 'localtime') AS day, " +
            "SUM(CASE WHEN type = 'SALE' THEN total ELSE -total END) AS amount FROM operations " +
            "WHERE shopId = :shopId AND type IN ('SALE', 'RETURN') AND time >= :from AND time < :to GROUP BY day"
    )
    abstract suspend fun dailyNetSales(shopId: Long, from: Long, to: Long): List<DayAmount>

    @Query(
        "SELECT strftime('%Y/%m/%d', o.time / 1000, 'unixepoch', 'localtime') AS day, " +
            "SUM(CASE WHEN o.type = 'SALE' THEN l.amount - l.costAmount ELSE l.costAmount - l.amount END) AS amount " +
            "FROM operation_lines l JOIN operations o ON o.id = l.opId " +
            "WHERE o.shopId = :shopId AND o.type IN ('SALE', 'RETURN') AND o.time >= :from AND o.time < :to GROUP BY day"
    )
    abstract suspend fun dailyGrossProfit(shopId: Long, from: Long, to: Long): List<DayAmount>

    @Query(
        "SELECT strftime('%Y/%m/%d', time / 1000, 'unixepoch', 'localtime') AS day, SUM(discount) AS amount " +
            "FROM operations WHERE shopId = :shopId AND type = 'SALE' AND time >= :from AND time < :to GROUP BY day"
    )
    abstract suspend fun dailyDiscounts(shopId: Long, from: Long, to: Long): List<DayAmount>

    /** المبيعات الصافية والربح لكل يوم (بتوقيت الهاتف) */
    open suspend fun dailySalesRange(shopId: Long, from: Long, to: Long): List<DaySales> {
        val sales = dailyNetSales(shopId, from, to).associate { it.day to it.amount }
        val gross = dailyGrossProfit(shopId, from, to).associate { it.day to it.amount }
        val disc = dailyDiscounts(shopId, from, to).associate { it.day to it.amount }
        return (sales.keys + gross.keys).sorted().map { d ->
            DaySales(d, sales[d] ?: 0L, (gross[d] ?: 0L) - (disc[d] ?: 0L))
        }
    }

    @Query(
        "SELECT note AS name, COUNT(*) AS count, SUM(total) AS amount FROM operations " +
            "WHERE shopId = :shopId AND type = 'EXPENSE' AND time >= :from AND time < :to " +
            "GROUP BY note ORDER BY amount DESC"
    )
    abstract suspend fun expensesByNote(shopId: Long, from: Long, to: Long): List<NamedTotal>

    /** آخر تاريخ بيع لكل منتج (لكشف السلع الراكدة) */
    @Query(
        "SELECT p.*, (SELECT MAX(o.time) FROM operation_lines l JOIN operations o ON o.id = l.opId " +
            "WHERE l.productId = p.id AND o.type = 'SALE') AS lastSale " +
            "FROM products p WHERE p.shopId = :shopId ORDER BY p.qty, p.name"
    )
    abstract suspend fun productsWithLastSale(shopId: Long): List<ProductLastSale>

    @Query("SELECT * FROM operations WHERE customerId = :customerId ORDER BY time")
    abstract suspend fun customerOperationsNow(customerId: Long): List<Operation>

    @Query("SELECT * FROM customers WHERE shopId = :shopId")
    abstract suspend fun customersNow(shopId: Long): List<Customer>

    // ---------- العمال (وضع البائع) ----------
    @Query("SELECT * FROM workers WHERE shopId = :shopId ORDER BY name")
    abstract fun workers(shopId: Long): Flow<List<Worker>>

    @Query("SELECT * FROM workers WHERE shopId = :shopId")
    abstract suspend fun workersNow(shopId: Long): List<Worker>

    @Query("SELECT * FROM workers WHERE id = :id")
    abstract suspend fun workerNow(id: Long): Worker?

    @Insert
    abstract suspend fun insertWorker(worker: Worker): Long

    @Query("UPDATE workers SET canCredit = :canCredit WHERE id = :id")
    abstract suspend fun setWorkerCredit(id: Long, canCredit: Boolean)

    @Query("UPDATE workers SET canDiscount = :canDiscount WHERE id = :id")
    abstract suspend fun setWorkerDiscount(id: Long, canDiscount: Boolean)

    @Query("UPDATE workers SET pinHash = :pinHash WHERE id = :id")
    abstract suspend fun setWorkerPin(id: Long, pinHash: String)

    @Query("DELETE FROM workers WHERE id = :id")
    abstract suspend fun deleteWorker(id: Long)

    @Query("SELECT COUNT(*) FROM operations WHERE workerId = :workerId AND type = 'SALE' AND time >= :from")
    abstract fun workerSaleCount(workerId: Long, from: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(total), 0) FROM operations WHERE workerId = :workerId AND type = 'SALE' AND customerId IS NULL AND time >= :from")
    abstract fun workerCashSales(workerId: Long, from: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(total), 0) FROM operations WHERE workerId = :workerId AND type = 'SALE' AND customerId IS NOT NULL AND time >= :from")
    abstract fun workerCreditSales(workerId: Long, from: Long): Flow<Long>

    @Query(
        "SELECT workerId, COUNT(*) AS count, SUM(total) AS amount FROM operations " +
            "WHERE shopId = :shopId AND type = 'SALE' AND time >= :from AND time < :to GROUP BY workerId ORDER BY amount DESC"
    )
    abstract suspend fun salesByWorker(shopId: Long, from: Long, to: Long): List<WorkerSales>

    // ---------- الزبائن والديون ----------
    @Insert
    abstract suspend fun insertCustomer(customer: Customer): Long

    @Query(
        "SELECT c.*, " +
            "COALESCE((SELECT SUM(o.total) FROM operations o WHERE o.customerId = c.id AND o.type = 'SALE'), 0) - " +
            "COALESCE((SELECT SUM(o.total) FROM operations o WHERE o.customerId = c.id AND o.type IN ('PAYMENT', 'RETURN')), 0) AS debt " +
            "FROM customers c WHERE c.shopId = :shopId ORDER BY debt DESC, c.name"
    )
    abstract fun customersWithDebt(shopId: Long): Flow<List<CustomerDebt>>

    @Query("SELECT * FROM customers WHERE id = :id")
    abstract fun customer(id: Long): Flow<Customer?>

    @Query("SELECT * FROM customers WHERE id = :id")
    abstract suspend fun customerNow(id: Long): Customer?

    @Query(
        "SELECT COALESCE((SELECT SUM(total) FROM operations WHERE customerId = :customerId AND type = 'SALE'), 0) - " +
            "COALESCE((SELECT SUM(total) FROM operations WHERE customerId = :customerId AND type IN ('PAYMENT', 'RETURN')), 0)"
    )
    abstract fun customerDebt(customerId: Long): Flow<Long>

    @Query(
        "SELECT COALESCE((SELECT SUM(total) FROM operations WHERE customerId = :customerId AND type = 'SALE'), 0) - " +
            "COALESCE((SELECT SUM(total) FROM operations WHERE customerId = :customerId AND type IN ('PAYMENT', 'RETURN')), 0)"
    )
    abstract suspend fun customerDebtNow(customerId: Long): Long

    @Query(
        "SELECT COALESCE((SELECT SUM(total) FROM operations WHERE shopId = :shopId AND customerId IS NOT NULL AND type = 'SALE'), 0) - " +
            "COALESCE((SELECT SUM(total) FROM operations WHERE shopId = :shopId AND customerId IS NOT NULL AND type IN ('PAYMENT', 'RETURN')), 0)"
    )
    abstract fun totalDebt(shopId: Long): Flow<Long>

    @Query("SELECT * FROM operations WHERE customerId = :customerId ORDER BY time DESC")
    abstract fun customerOperations(customerId: Long): Flow<List<Operation>>

    @Query("UPDATE customers SET name = :name, phone = :phone WHERE id = :id")
    abstract suspend fun updateCustomer(id: Long, name: String, phone: String)

    /** تسديد زبون: لا يُقبل مبلغ أكبر من دينه */
    @Transaction
    open suspend fun recordPayment(shopId: Long, customerId: Long, amount: Long, time: Long): Long {
        require(amount > 0)
        val debt = customerDebtNow(customerId)
        if (amount > debt) throw IllegalStateException(tr("المبلغ أكبر من الدَّين المتبقي"))
        return insertOperation(
            Operation(shopId = shopId, type = OpType.PAYMENT, total = amount, note = "", time = time, customerId = customerId)
        )
    }

    @Query("SELECT COALESCE(SUM(total), 0) FROM operations WHERE shopId = :shopId AND type = :type AND time >= :from")
    abstract fun sumType(shopId: Long, type: String, from: Long): Flow<Long>

    @Query(PROFIT_SQL)
    abstract fun profitFlow(shopId: Long, from: Long, to: Long): Flow<Long>

    fun profit(shopId: Long, from: Long): Flow<Long> = profitFlow(shopId, from, Long.MAX_VALUE)

    /** المبيعات الصافية: البيع ناقص المرتجعات */
    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'SALE' THEN total ELSE -total END), 0) FROM operations WHERE shopId = :shopId AND type IN ('SALE', 'RETURN') AND time >= :from")
    abstract fun netSales(shopId: Long, from: Long): Flow<Long>

    // ---------- حركة المخزون لمنتج ----------
    @Query(
        "SELECT operation_lines.*, operations.type AS type, operations.time AS time " +
            "FROM operation_lines JOIN operations ON operations.id = operation_lines.opId " +
            "WHERE operation_lines.productId = :productId ORDER BY operations.time DESC"
    )
    abstract fun movements(productId: Long): Flow<List<LineWithOp>>


    // ---------- الموردون ----------
    @Insert
    abstract suspend fun insertSupplier(supplier: Supplier): Long

    @Query("UPDATE suppliers SET name = :name, phone = :phone WHERE id = :id")
    abstract suspend fun updateSupplier(id: Long, name: String, phone: String)

    @Query("SELECT * FROM suppliers WHERE id = :id")
    abstract fun supplier(id: Long): Flow<Supplier?>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    abstract suspend fun supplierNow(id: Long): Supplier?

    @Query("SELECT * FROM suppliers WHERE shopId = :shopId")
    abstract suspend fun suppliersNow(shopId: Long): List<Supplier>

    @Query(
        "SELECT s.*, " +
            "COALESCE((SELECT SUM(o.total) FROM operations o WHERE o.supplierId = s.id AND o.type = 'PURCHASE'), 0) - " +
            "COALESCE((SELECT SUM(o.total) FROM operations o WHERE o.supplierId = s.id AND o.type = 'SUPPLIER_PAYMENT'), 0) AS debt " +
            "FROM suppliers s WHERE s.shopId = :shopId ORDER BY debt DESC, s.name"
    )
    abstract fun suppliersWithDebt(shopId: Long): Flow<List<SupplierDebt>>

    @Query(
        "SELECT COALESCE((SELECT SUM(total) FROM operations WHERE supplierId = :supplierId AND type = 'PURCHASE'), 0) - " +
            "COALESCE((SELECT SUM(total) FROM operations WHERE supplierId = :supplierId AND type = 'SUPPLIER_PAYMENT'), 0)"
    )
    abstract fun supplierDebt(supplierId: Long): Flow<Long>

    @Query(
        "SELECT COALESCE((SELECT SUM(total) FROM operations WHERE supplierId = :supplierId AND type = 'PURCHASE'), 0) - " +
            "COALESCE((SELECT SUM(total) FROM operations WHERE supplierId = :supplierId AND type = 'SUPPLIER_PAYMENT'), 0)"
    )
    abstract suspend fun supplierDebtNow(supplierId: Long): Long

    /** ما على المحل لكل الموردين */
    @Query(
        "SELECT COALESCE((SELECT SUM(total) FROM operations WHERE shopId = :shopId AND supplierId IS NOT NULL AND type = 'PURCHASE'), 0) - " +
            "COALESCE((SELECT SUM(total) FROM operations WHERE shopId = :shopId AND type = 'SUPPLIER_PAYMENT'), 0)"
    )
    abstract fun totalSupplierDebt(shopId: Long): Flow<Long>

    @Query("SELECT * FROM operations WHERE supplierId = :supplierId ORDER BY time DESC")
    abstract fun supplierOperations(supplierId: Long): Flow<List<Operation>>

    /** دفع المحل لمورد: لا يُقبل مبلغ أكبر مما عليه */
    @Transaction
    open suspend fun recordSupplierPayment(shopId: Long, supplierId: Long, amount: Long, time: Long): Long {
        require(amount > 0)
        if (amount > supplierDebtNow(supplierId)) throw IllegalStateException(tr("المبلغ أكبر من الدَّين المتبقي"))
        return insertOperation(
            Operation(shopId = shopId, type = OpType.SUPPLIER_PAYMENT, total = amount, note = "", time = time, supplierId = supplierId)
        )
    }

    // ---------- المرتجعات ----------
    @Query(
        "SELECT l.productId AS productId, SUM(l.qty) AS qty, SUM(l.amount) AS amount FROM operation_lines l " +
            "JOIN operations o ON o.id = l.opId WHERE o.type = 'RETURN' AND o.refId = :saleId GROUP BY l.productId"
    )
    abstract suspend fun returnedFor(saleId: Long): List<ReturnedQty>

    @Query("SELECT COUNT(*) FROM operations WHERE refId = :opId AND type = 'RETURN'")
    abstract suspend fun returnCount(opId: Long): Int

    @Query("SELECT * FROM operations WHERE refId = :opId AND type = 'PAYMENT' LIMIT 1")
    abstract suspend fun refundPaymentFor(opId: Long): Operation?

    // ---------- عمليات كاملة داخل معاملة واحدة ----------

    /**
     * يسجل البيع: ينقص المخزون ويدخل المبلغ في الصندوق (أو على الزبون إذا كان بالدَّين).
     * يرفض البيع إذا كانت الكمية غير كافية أو كان التخفيض أكبر من المجموع.
     */
    @Transaction
    open suspend fun recordSale(
        shopId: Long, items: List<SaleItem>, time: Long, customerId: Long? = null, workerId: Long? = null,
        discount: Long = 0,
    ): Long {
        require(items.isNotEmpty())
        for (item in items) {
            require(item.qty > 0 && item.unitPrice >= 0)
            val p = productNow(item.productId)
                ?: throw IllegalStateException(tr("منتج غير موجود: %1\$s", item.name))
            if (p.qty < item.qty) {
                throw IllegalStateException(tr("الكمية المتوفرة من «%1\$s» هي %2\$s فقط", p.name, Qty.format(p.qty, p.unit)))
            }
        }
        val gross = items.sumOf { Qty.amount(it.unitPrice, it.qty) }
        if (discount < 0 || discount > gross) throw IllegalStateException(tr("التخفيض أكبر من المجموع"))
        val opId = insertOperation(
            Operation(
                shopId = shopId, type = OpType.SALE, total = gross - discount, note = "", time = time,
                customerId = customerId, workerId = workerId, discount = discount,
            )
        )
        for (item in items) {
            insertLine(
                OperationLine(
                    opId = opId, productId = item.productId, productName = item.name,
                    barcode = item.barcode, qty = item.qty,
                    unitPrice = item.unitPrice, unitCost = item.unitCost,
                    amount = Qty.amount(item.unitPrice, item.qty), costAmount = Qty.amount(item.unitCost, item.qty),
                )
            )
            addQty(item.productId, -item.qty)
        }
        return opId
    }

    /**
     * يسجل الشراء: يضيف المنتجات الجديدة ويزيد المخزون.
     * نقدًا يخرج المبلغ من الصندوق؛ وبالدَّين (supplierId) يُسجَّل على المحل للمورد.
     */
    @Transaction
    open suspend fun recordPurchase(shopId: Long, items: List<PurchaseItem>, time: Long, supplierId: Long? = null): Long {
        require(items.isNotEmpty())
        val total = items.sumOf { Qty.amount(it.buyPrice, it.qty) }
        val opId = insertOperation(
            Operation(shopId = shopId, type = OpType.PURCHASE, total = total, note = "", time = time, supplierId = supplierId)
        )
        for (item in items) {
            require(item.qty > 0)
            val pid = item.productId
                ?: findByBarcode(shopId, item.barcode)?.id
                ?: insertProduct(
                    Product(
                        shopId = shopId, barcode = item.barcode, name = item.name,
                        buyPrice = item.buyPrice, sellPrice = item.sellPrice, qty = 0, createdAt = time, unit = item.unit,
                    )
                )
            // تكلفة الشراء = المتوسط المرجّح بين المخزون القديم والكمية الجديدة (حساب دقيق للربح)
            val before = productNow(pid)
            val avgCost = if (before == null || before.qty <= 0) item.buyPrice
            else Qty.weightedCost(before.buyPrice, before.qty, item.buyPrice, item.qty)
            addQty(pid, item.qty)
            updatePrices(pid, avgCost, item.sellPrice)
            val amount = Qty.amount(item.buyPrice, item.qty)
            insertLine(
                OperationLine(
                    opId = opId, productId = pid, productName = item.name,
                    barcode = item.barcode, qty = item.qty,
                    unitPrice = item.buyPrice, unitCost = item.buyPrice, amount = amount, costAmount = amount,
                )
            )
        }
        return opId
    }

    /**
     * إرجاع سلع من بيع سابق: تعود الكمية إلى المخزون بتكلفتها الأصلية،
     * ويُردّ للزبون ثمنها (مع حصتها من التخفيض). في البيع بالدَّين يُنقص الدَّين أولًا،
     * وإن كان الزبون قد سدّد يُردّ له الباقي نقدًا.
     * returns: productId → الكمية المرجعة (بالألف).
     */
    @Transaction
    open suspend fun recordReturn(saleId: Long, returns: Map<Long, Long>, time: Long, workerId: Long? = null): Long {
        val sale = operationNow(saleId) ?: throw IllegalStateException(tr("العملية غير موجودة (ربما أُلغيت)"))
        require(sale.type == OpType.SALE)
        val wanted = returns.filterValues { it > 0 }
        if (wanted.isEmpty()) throw IllegalStateException(tr("اختر كمية لإرجاعها"))
        val saleLines = lines(saleId).associateBy { it.productId }
        val already = returnedFor(saleId).associateBy { it.productId }
        val grossSale = sale.total + sale.discount
        val refundedBefore = already.values.sumOf { it.amount }

        data class R(val line: OperationLine, val qty: Long, val amount: Long, val cost: Long)
        val rs = wanted.map { (pid, q) ->
            val l = saleLines[pid] ?: throw IllegalStateException(tr("منتج غير موجود: %1\$s", pid))
            val left = l.qty - (already[pid]?.qty ?: 0L)
            if (q > left) throw IllegalStateException(tr("الكمية القابلة للإرجاع من «%1\$s» هي %2\$s", l.productName, Qty.plain(left)))
            // ثمن السلعة المرجعة بعد حصتها من التخفيض
            val full = if (q == l.qty) l.amount else Qty.amount(l.unitPrice, q)
            R(l, q, Qty.share(full, sale.total, grossSale), if (q == l.qty) l.costAmount else Qty.amount(l.unitCost, q))
        }
        // لا يتجاوز مجموع ما يُردّ مبلغ البيع نفسه (بسبب التقريب)
        var refund = rs.sumOf { it.amount }
        val maxLeft = (sale.total - refundedBefore).coerceAtLeast(0)
        val excess = refund - maxLeft
        val fixed = if (excess > 0) {
            refund = maxLeft
            var e = excess
            rs.map { r -> val cut = minOf(e, r.amount); e -= cut; r.copy(amount = r.amount - cut) }
        } else rs

        val retId = insertOperation(
            Operation(
                shopId = sale.shopId, type = OpType.RETURN, total = refund, note = "", time = time,
                customerId = sale.customerId, workerId = workerId, refId = saleId,
            )
        )
        for (r in fixed) {
            val p = productNow(r.line.productId)
                ?: throw IllegalStateException(tr("المنتج «%1\$s» حُذف من المخزون", r.line.productName))
            setCost(p.id, if (p.qty <= 0) r.line.unitCost else Qty.weightedCost(p.buyPrice, p.qty, r.line.unitCost, r.qty))
            addQty(p.id, r.qty)
            insertLine(
                OperationLine(
                    opId = retId, productId = p.id, productName = r.line.productName, barcode = r.line.barcode,
                    qty = r.qty, unitPrice = r.line.unitPrice, unitCost = r.line.unitCost,
                    amount = r.amount, costAmount = r.cost,
                )
            )
        }
        // زبون بالدَّين سبق أن سدّد: ما زاد على دينه يُردّ له نقدًا
        val cid = sale.customerId
        if (cid != null) {
            val debt = customerDebtNow(cid)
            if (debt < 0) {
                insertOperation(
                    Operation(
                        shopId = sale.shopId, type = OpType.PAYMENT, total = debt, note = "", time = time,
                        customerId = cid, workerId = workerId, refId = retId,
                    )
                )
            }
        }
        return retId
    }

    /**
     * تسوية المخزون (تالف، منتهي الصلاحية، ضائع، أو فرق الجرد): لا تمس الصندوق،
     * وقيمة النقص بالتكلفة تُحسب خسارة. deltas: productId → الفرق بالألف (سالب = نقص).
     */
    @Transaction
    open suspend fun recordAdjustment(shopId: Long, deltas: Map<Long, Long>, note: String, time: Long): Long? {
        val d = deltas.filterValues { it != 0L }
        if (d.isEmpty()) return null
        val prods = d.keys.associateWith {
            productNow(it) ?: throw IllegalStateException(tr("منتج غير موجود: %1\$s", it))
        }
        for ((pid, delta) in d) {
            val p = prods.getValue(pid)
            if (p.qty + delta < 0) throw IllegalStateException(tr("الكمية المتوفرة من «%1\$s» هي %2\$s فقط", p.name, Qty.format(p.qty, p.unit)))
        }
        val lossValue = -d.entries.sumOf { (pid, delta) -> Qty.amount(prods.getValue(pid).buyPrice, delta) }
        val opId = insertOperation(Operation(shopId = shopId, type = OpType.ADJUST, total = lossValue, note = note, time = time))
        for ((pid, delta) in d) {
            val p = prods.getValue(pid)
            val v = Qty.amount(p.buyPrice, delta)
            insertLine(
                OperationLine(
                    opId = opId, productId = pid, productName = p.name, barcode = p.barcode, qty = delta,
                    unitPrice = p.buyPrice, unitCost = p.buyPrice, amount = v, costAmount = v,
                )
            )
            addQty(pid, delta)
        }
        return opId
    }

    @Query("DELETE FROM operation_lines WHERE opId = :opId")
    abstract suspend fun deleteLines(opId: Long)

    @Query("DELETE FROM operations WHERE id = :id")
    abstract suspend fun deleteOperation(id: Long)

    /** ينقص كمية عادت إلى المخزون (عند إلغاء شراء أو مرتجع) ويعيد متوسط التكلفة كما كان */
    private suspend fun takeBack(l: OperationLine, error: String) {
        val p = productNow(l.productId)
        if (p == null || p.qty < l.qty) throw IllegalStateException(error)
        val remaining = p.qty - l.qty
        if (remaining > 0) {
            val restored = Qty.weightedCost(p.buyPrice, p.qty, l.unitCost, -l.qty).coerceAtLeast(0)
            setCost(p.id, restored)
        }
        addQty(l.productId, -l.qty)
    }

    /**
     * إلغاء عملية خاطئة: يعيد المخزون والصندوق والديون كما كانت قبلها.
     * يُرفض الإلغاء إذا كانت عمليات لاحقة تعتمد عليها.
     */
    @Transaction
    open suspend fun cancelOperation(opId: Long) {
        val op = operationNow(opId) ?: return
        if (op.type == OpType.SALE && returnCount(opId) > 0) {
            throw IllegalStateException(tr("ألغِ أولًا المرتجعات المرتبطة بهذا البيع"))
        }
        if (op.type == OpType.PAYMENT && op.refId != null) {
            throw IllegalStateException(tr("هذا المبلغ مرتبط بمرتجع: ألغِ المرتجع نفسه"))
        }
        // لا يُلغى بيع بالدَّين إذا كان ذلك سيجعل ما سدّده الزبون أكبر من دينه
        if (op.isCredit && op.customerId != null && customerDebtNow(op.customerId) < op.total) {
            throw IllegalStateException(tr("ألغِ أولًا تسديدات هذا الزبون، فقد سدّد جزءًا من هذا البيع"))
        }
        if (op.isCreditPurchase && op.supplierId != null && supplierDebtNow(op.supplierId) < op.total) {
            throw IllegalStateException(tr("ألغِ أولًا ما دفعته لهذا المورد، فقد دفعت جزءًا من هذا الشراء"))
        }
        val opLines = lines(opId)
        when (op.type) {
            OpType.SALE -> opLines.forEach { l ->
                // تعود السلعة بتكلفتها وقت البيع، فيبقى متوسط التكلفة صحيحًا
                productNow(l.productId)?.let { p ->
                    setCost(p.id, if (p.qty <= 0) l.unitCost else Qty.weightedCost(p.buyPrice, p.qty, l.unitCost, l.qty))
                    addQty(p.id, l.qty)
                }
            }
            OpType.PURCHASE -> {
                // التحقق أولًا لكل السطور ثم التنفيذ (المعاملة تلغي كل شيء عند أي خطأ)
                for (l in opLines) {
                    takeBack(l, tr("لا يمكن إلغاء هذا الشراء: بيع جزء من «%1\$s» بعده", l.productName))
                }
            }
            OpType.RETURN -> {
                for (l in opLines) {
                    takeBack(l, tr("لا يمكن إلغاء هذا المرتجع: بيع جزء من «%1\$s» بعده", l.productName))
                }
                refundPaymentFor(opId)?.let { deleteOperation(it.id) }
            }
            OpType.ADJUST -> {
                for (l in opLines) {
                    val p = productNow(l.productId)
                    if (p == null || p.qty - l.qty < 0) {
                        throw IllegalStateException(tr("لا يمكن إلغاء هذه التسوية: بيع جزء من «%1\$s» بعدها", l.productName))
                    }
                    addQty(l.productId, -l.qty)
                }
            }
        }
        deleteLines(opId)
        deleteOperation(opId)
    }

    // ---------- حذف محل كامل (المحل التجريبي فقط) ----------
    @Query("DELETE FROM operation_lines WHERE opId IN (SELECT id FROM operations WHERE shopId = :shopId)")
    abstract suspend fun deleteShopLines(shopId: Long)

    @Query("DELETE FROM operations WHERE shopId = :shopId")
    abstract suspend fun deleteShopOperations(shopId: Long)

    @Query("DELETE FROM products WHERE shopId = :shopId")
    abstract suspend fun deleteShopProducts(shopId: Long)

    @Query("DELETE FROM customers WHERE shopId = :shopId")
    abstract suspend fun deleteShopCustomers(shopId: Long)

    @Query("DELETE FROM suppliers WHERE shopId = :shopId")
    abstract suspend fun deleteShopSuppliers(shopId: Long)

    @Query("DELETE FROM workers WHERE shopId = :shopId")
    abstract suspend fun deleteShopWorkers(shopId: Long)

    @Query("DELETE FROM shop_profile WHERE shopId = :shopId")
    abstract suspend fun deleteShopProfile(shopId: Long)

    @Query("DELETE FROM shops WHERE id = :shopId")
    abstract suspend fun deleteShopRow(shopId: Long)

    @Query("SELECT * FROM shops WHERE remoteId LIKE 'demo-%' LIMIT 1")
    abstract fun demoShop(): Flow<Shop?>

    @Transaction
    open suspend fun deleteShopCompletely(shopId: Long) {
        deleteShopLines(shopId)
        deleteShopOperations(shopId)
        deleteShopProducts(shopId)
        deleteShopCustomers(shopId)
        deleteShopSuppliers(shopId)
        deleteShopWorkers(shopId)
        deleteShopProfile(shopId)
        deleteShopRow(shopId)
    }
}

/** ربح المبيعات بين تاريخين: ربح السطور − التخفيضات − ربح المرتجعات */
private const val PROFIT_SQL =
    "SELECT COALESCE((SELECT SUM(CASE WHEN o.type = 'SALE' THEN l.amount - l.costAmount ELSE l.costAmount - l.amount END) " +
        "FROM operation_lines l JOIN operations o ON o.id = l.opId " +
        "WHERE o.shopId = :shopId AND o.type IN ('SALE', 'RETURN') AND o.time >= :from AND o.time < :to), 0) - " +
        "COALESCE((SELECT SUM(discount) FROM operations WHERE shopId = :shopId AND type = 'SALE' AND time >= :from AND time < :to), 0)"
