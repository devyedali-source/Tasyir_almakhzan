package com.elyedali.makhzan.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * كل المبالغ مخزنة بوحدة صغرى (المبلغ × 100) في Long لضمان حساب دقيق بلا أخطاء كسور.
 */

/** المحل التجاري: لكل محل تفعيله الخاص (tier / expiresAt). */
@Entity(tableName = "shops")
data class Shop(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    /** 0 = مجاني، 1 = حتى 50 منتجًا، 2 = حتى 100، 3 = أكثر من 100 */
    val tier: Int = 0,
    /** تاريخ انتهاء التفعيل (0 = غير مفعل) */
    val expiresAt: Long = 0,
)

@Entity(
    tableName = "products",
    indices = [Index(value = ["shopId", "barcode"], unique = true), Index("shopId")]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val barcode: String,
    val name: String,
    val buyPrice: Long,
    val sellPrice: Long,
    val qty: Int,
    val createdAt: Long,
)

object OpType {
    const val SALE = "SALE"
    const val PURCHASE = "PURCHASE"
    const val EXPENSE = "EXPENSE"

    fun label(type: String) = when (type) {
        SALE -> "بيع"
        PURCHASE -> "شراء"
        EXPENSE -> "مصروف"
        else -> type
    }
}

/** عملية في الصندوق: بيع (+) أو شراء (−) أو مصروف (−). */
@Entity(tableName = "operations", indices = [Index("shopId"), Index("time")])
data class Operation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val type: String,
    val total: Long,
    val note: String,
    val time: Long,
)

/** سطر من عملية بيع أو شراء: هو أيضًا حركة المخزون للمنتج. */
@Entity(tableName = "operation_lines", indices = [Index("opId"), Index("productId")])
data class OperationLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val opId: Long,
    val productId: Long,
    val productName: String,
    val barcode: String,
    val qty: Int,
    /** سعر الوحدة في هذه العملية (سعر البيع في البيع، سعر الشراء في الشراء) */
    val unitPrice: Long,
    /** تكلفة الوحدة وقت العملية (لحساب الربح) */
    val unitCost: Long,
)

data class LineWithOp(
    @Embedded val line: OperationLine,
    val type: String,
    val time: Long,
)

/** عنصر في سلة البيع قبل التأكيد */
data class SaleItem(
    val productId: Long,
    val name: String,
    val barcode: String,
    val qty: Int,
    val unitPrice: Long,
    val unitCost: Long,
)

/** عنصر في سلة الشراء قبل التأكيد (productId = null إذا كان المنتج جديدًا) */
data class PurchaseItem(
    val productId: Long?,
    val barcode: String,
    val name: String,
    val buyPrice: Long,
    val sellPrice: Long,
    val qty: Int,
)
