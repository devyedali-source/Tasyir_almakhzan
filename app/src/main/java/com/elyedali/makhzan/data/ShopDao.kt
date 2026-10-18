package com.elyedali.makhzan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT COALESCE(SUM(qty * buyPrice), 0) FROM products WHERE shopId = :shopId")
    abstract fun stockValue(shopId: Long): Flow<Long>

    @Insert
    abstract suspend fun insertProduct(product: Product): Long

    @Query("UPDATE products SET qty = qty + :delta WHERE id = :id")
    abstract suspend fun addQty(id: Long, delta: Int)

    @Query("UPDATE products SET buyPrice = :buy, sellPrice = :sell WHERE id = :id")
    abstract suspend fun updatePrices(id: Long, buy: Long, sell: Long)

    @Query("UPDATE products SET name = :name, sellPrice = :sell WHERE id = :id")
    abstract suspend fun updateNameAndSellPrice(id: Long, name: String, sell: Long)

    // ---------- العمليات (الصندوق) ----------
    @Insert
    abstract suspend fun insertOperation(op: Operation): Long

    @Insert
    abstract suspend fun insertLine(line: OperationLine): Long

    @Query("SELECT * FROM operations WHERE shopId = :shopId AND time >= :from ORDER BY time DESC")
    abstract fun operations(shopId: Long, from: Long): Flow<List<Operation>>

    @Query("SELECT * FROM operation_lines WHERE opId = :opId")
    abstract suspend fun lines(opId: Long): List<OperationLine>

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 'SALE' THEN total ELSE -total END), 0) " +
            "FROM operations WHERE shopId = :shopId"
    )
    abstract fun balance(shopId: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(total), 0) FROM operations WHERE shopId = :shopId AND type = :type AND time >= :from")
    abstract fun sumType(shopId: Long, type: String, from: Long): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM((l.unitPrice - l.unitCost) * l.qty), 0) FROM operation_lines l " +
            "JOIN operations o ON o.id = l.opId " +
            "WHERE o.shopId = :shopId AND o.type = 'SALE' AND o.time >= :from"
    )
    abstract fun profit(shopId: Long, from: Long): Flow<Long>

    // ---------- حركة المخزون لمنتج ----------
    @Query(
        "SELECT operation_lines.*, operations.type AS type, operations.time AS time " +
            "FROM operation_lines JOIN operations ON operations.id = operation_lines.opId " +
            "WHERE operation_lines.productId = :productId ORDER BY operations.time DESC"
    )
    abstract fun movements(productId: Long): Flow<List<LineWithOp>>

    // ---------- عمليات كاملة داخل معاملة واحدة ----------

    /** يسجل البيع: ينقص المخزون ويدخل المبلغ في الصندوق. يرفض البيع إذا كانت الكمية غير كافية. */
    @Transaction
    open suspend fun recordSale(shopId: Long, items: List<SaleItem>, time: Long): Long {
        require(items.isNotEmpty())
        for (item in items) {
            val p = productNow(item.productId)
                ?: throw IllegalStateException("منتج غير موجود: ${item.name}")
            if (p.qty < item.qty) {
                throw IllegalStateException("الكمية المتوفرة من «${p.name}» هي ${p.qty} فقط")
            }
        }
        val total = items.sumOf { it.unitPrice * it.qty }
        val opId = insertOperation(
            Operation(shopId = shopId, type = OpType.SALE, total = total, note = "", time = time)
        )
        for (item in items) {
            insertLine(
                OperationLine(
                    opId = opId, productId = item.productId, productName = item.name,
                    barcode = item.barcode, qty = item.qty,
                    unitPrice = item.unitPrice, unitCost = item.unitCost,
                )
            )
            addQty(item.productId, -item.qty)
        }
        return opId
    }

    /** يسجل الشراء: يضيف المنتجات الجديدة، يزيد المخزون ويخرج المبلغ من الصندوق. */
    @Transaction
    open suspend fun recordPurchase(shopId: Long, items: List<PurchaseItem>, time: Long): Long {
        require(items.isNotEmpty())
        val total = items.sumOf { it.buyPrice * it.qty }
        val opId = insertOperation(
            Operation(shopId = shopId, type = OpType.PURCHASE, total = total, note = "", time = time)
        )
        for (item in items) {
            val pid = item.productId
                ?: findByBarcode(shopId, item.barcode)?.id
                ?: insertProduct(
                    Product(
                        shopId = shopId, barcode = item.barcode, name = item.name,
                        buyPrice = item.buyPrice, sellPrice = item.sellPrice, qty = 0, createdAt = time,
                    )
                )
            addQty(pid, item.qty)
            updatePrices(pid, item.buyPrice, item.sellPrice)
            insertLine(
                OperationLine(
                    opId = opId, productId = pid, productName = item.name,
                    barcode = item.barcode, qty = item.qty,
                    unitPrice = item.buyPrice, unitCost = item.buyPrice,
                )
            )
        }
        return opId
    }
}
