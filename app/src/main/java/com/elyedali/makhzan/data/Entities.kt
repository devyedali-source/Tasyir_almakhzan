package com.elyedali.makhzan.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.elyedali.makhzan.i18n.tr

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
    /** معرّف ثابت للمحل على الخادم (يُستعمل في التفعيل، ولا يتغير بعد الاسترجاع من Drive) */
    @ColumnInfo(defaultValue = "") val remoteId: String = "",
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
    /** تسديد زبون لجزء من دينه أو كله (يدخل الصندوق) */
    const val PAYMENT = "PAYMENT"

    fun label(type: String) = when (type) {
        SALE -> tr("بيع")
        PURCHASE -> tr("شراء")
        EXPENSE -> tr("مصروف")
        PAYMENT -> tr("تسديد دَين")
        else -> type
    }
}

/**
 * عملية في الصندوق: بيع نقدي (+)، تسديد دين (+)، شراء (−)، مصروف (−).
 * البيع بالدَّين (customerId غير فارغ) يُنقص المخزون ويُحسب في المبيعات والربح،
 * لكنه لا يدخل الصندوق حتى يسدده الزبون.
 */
@Entity(tableName = "operations", indices = [Index("shopId"), Index("time"), Index("customerId"), Index("workerId")])
data class Operation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val type: String,
    val total: Long,
    val note: String,
    val time: Long,
    /** الزبون في البيع بالدَّين وفي التسديد (أُضيف في الإصدار 4) */
    val customerId: Long? = null,
    /** العامل الذي سجّل العملية؛ null = صاحب المحل (أُضيف في الإصدار 5) */
    val workerId: Long? = null,
)

// خارج الكيان حتى لا تعتبرها قاعدة البيانات أعمدة
val Operation.isCredit: Boolean get() = type == OpType.SALE && customerId != null

/** أثر العملية على الصندوق */
val Operation.cashEffect: Long
    get() = when {
        isCredit -> 0L
        type == OpType.SALE || type == OpType.PAYMENT -> total
        else -> -total
    }

/** زبون يشتري بالدَّين (الإصدار 4) */
@Entity(tableName = "customers", indices = [Index("shopId")])
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val name: String,
    val phone: String,
    val createdAt: Long,
)

/** عامل (بائع) في المحل: يبيع برمزه الخاص ولا يرى أرقام صاحب المحل (الإصدار 5) */
@Entity(tableName = "workers", indices = [Index("shopId")])
data class Worker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val name: String,
    /** الرمز السري مشفّرًا (لا يُحفظ الرمز نفسه أبدًا) */
    val pinHash: String,
    /** هل يُسمح له بالبيع بالدَّين */
    val canCredit: Boolean,
    val createdAt: Long,
)

data class WorkerSales(val workerId: Long?, val count: Long, val amount: Long)

data class ProductSales(
    val name: String,
    val qty: Long,
    val amount: Long,
    val profit: Long,
)

data class DaySales(val day: String, val amount: Long, val profit: Long)

data class NamedTotal(val name: String, val count: Long, val amount: Long)

data class ProductLastSale(
    @Embedded val product: Product,
    val lastSale: Long?,
)

data class CustomerDebt(
    @Embedded val customer: Customer,
    val debt: Long,
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

/** معلومات المحل التي تظهر على الفواتير وإعدادات الفاتورة (جدول أضيف في الإصدار 2 من القاعدة) */
@Entity(tableName = "shop_profile")
data class ShopProfile(
    @PrimaryKey val shopId: Long,
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val taxNumber: String = "",
    val logoPath: String? = null,
    /** 0 = كلاسيكي A4، 1 = عصري A4، 2 = إيصال 80 مم */
    val design: Int = 0,
    /** AR / FR / BI (مزدوجة) */
    val language: String = "AR",
    val showStamp: Boolean = true,
    /** عملة المحل: فارغة = الأوقية (الافتراضي)، ويمكن للمستخدم كتابة عملة أخرى */
    @ColumnInfo(defaultValue = "") val currency: String = "",
)
