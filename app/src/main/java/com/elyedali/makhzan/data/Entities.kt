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
    /** الكمية بالألف (1000 = قطعة أو كيلو واحد) لتسمح بالبيع بالوزن مثل 1.5 كغ (الإصدار 6) */
    val qty: Long,
    val createdAt: Long,
    /** وحدة البيع: فارغة = قطعة، kg = كيلوغرام، l = لتر، m = متر (الإصدار 6) */
    @ColumnInfo(defaultValue = "") val unit: String = "",
)

object OpType {
    const val SALE = "SALE"
    const val PURCHASE = "PURCHASE"
    const val EXPENSE = "EXPENSE"
    /** تسديد زبون لجزء من دينه أو كله (يدخل الصندوق). المبلغ السالب = ردّ مال للزبون بعد إرجاع */
    const val PAYMENT = "PAYMENT"
    /** إرجاع سلع من بيع سابق (refId = البيع الأصلي) */
    const val RETURN = "RETURN"
    /** تسوية المخزون: تالف، منتهي، ضائع، أو فرق الجرد. لا تمس الصندوق */
    const val ADJUST = "ADJUST"
    /** دفع المحل لمورد جزءًا من دينه أو كله (يخرج من الصندوق) */
    const val SUPPLIER_PAYMENT = "SUPPLIER_PAYMENT"
    /** المخزون الموجود عند بدء استعمال التطبيق: يدخل المخزون بتكلفته دون أن يمسّ الصندوق */
    const val OPENING = "OPENING"
    /** إيداع مال في الصندوق (رصيد افتتاحي أو رأس مال يضعه صاحب المحل) — لا يُحسب ربحًا */
    const val DEPOSIT = "DEPOSIT"
    /** سحب صاحب المحل مالًا من الصندوق لنفسه — ليس مصروفًا ولا يُنقص الربح */
    const val WITHDRAW = "WITHDRAW"
    /** دين سابق كان قبل استعمال التطبيق: على زبون (customerId) أو للمورد على المحل (supplierId) */
    const val OLD_DEBT = "OLD_DEBT"
    /** إرجاع بضاعة إلى مورد: ينقص المخزون وينقص ما على المحل للمورد */
    const val SUPPLIER_RETURN = "SUPPLIER_RETURN"

    fun label(type: String) = when (type) {
        SALE -> tr("بيع")
        PURCHASE -> tr("شراء")
        EXPENSE -> tr("مصروف")
        PAYMENT -> tr("تسديد دَين")
        RETURN -> tr("مرتجع")
        ADJUST -> tr("تسوية مخزون")
        SUPPLIER_PAYMENT -> tr("دفع لمورد")
        OPENING -> tr("مخزون افتتاحي")
        DEPOSIT -> tr("إيداع في الصندوق")
        WITHDRAW -> tr("سحب من الصندوق")
        OLD_DEBT -> tr("دين سابق")
        SUPPLIER_RETURN -> tr("إرجاع للمورد")
        else -> type
    }
}

/** أثر كل عملية على الصندوق بلغة SQL (يطابق Operation.cashEffect) */
const val CASH_CASE =
    "CASE " +
        "WHEN type = 'SALE' AND customerId IS NOT NULL THEN 0 " +
        "WHEN type = 'RETURN' AND customerId IS NOT NULL THEN 0 " +
        "WHEN type = 'PURCHASE' AND supplierId IS NOT NULL THEN 0 " +
        "WHEN type IN ('ADJUST', 'OPENING', 'OLD_DEBT', 'SUPPLIER_RETURN') THEN 0 " +
        "WHEN type IN ('SALE', 'PAYMENT', 'DEPOSIT') THEN total " +
        "ELSE -total END"

/**
 * عملية في الصندوق: بيع نقدي (+)، تسديد دين (+)، شراء (−)، مصروف (−).
 * البيع بالدَّين (customerId غير فارغ) يُنقص المخزون ويُحسب في المبيعات والربح،
 * لكنه لا يدخل الصندوق حتى يسدده الزبون.
 */
@Entity(
    tableName = "operations",
    indices = [Index("shopId"), Index("time"), Index("customerId"), Index("workerId"), Index("refId"), Index("supplierId")],
)
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
    /** التخفيض على البيع (total = مجموع السطور − التخفيض) (الإصدار 6) */
    @ColumnInfo(defaultValue = "0") val discount: Long = 0,
    /** العملية المرتبطة: البيع الأصلي للمرتجع، أو المرتجع لردّ المال (الإصدار 6) */
    val refId: Long? = null,
    /** المورد في الشراء بالدَّين وفي الدفع له (الإصدار 6) */
    val supplierId: Long? = null,
)

// خارج الكيان حتى لا تعتبرها قاعدة البيانات أعمدة
val Operation.isCredit: Boolean get() = type == OpType.SALE && customerId != null

/** شراء بالدَّين من مورد (لا يخرج من الصندوق حتى يُدفع) */
val Operation.isCreditPurchase: Boolean get() = type == OpType.PURCHASE && supplierId != null

/** أثر العملية على الصندوق */
val Operation.cashEffect: Long
    get() = when {
        isCredit || isCreditPurchase -> 0L
        type in setOf(OpType.ADJUST, OpType.OPENING, OpType.OLD_DEBT, OpType.SUPPLIER_RETURN) -> 0L
        type == OpType.RETURN && customerId != null -> 0L
        type == OpType.SALE || type == OpType.PAYMENT || type == OpType.DEPOSIT -> total
        else -> -total
    }

/** وصف العملية للعرض */
fun Operation.title(): String = when {
    isCredit -> tr("بيع بالدَّين")
    isCreditPurchase -> tr("شراء بالدَّين")
    type == OpType.PAYMENT && total < 0 -> tr("ردّ مال لزبون")
    type == OpType.RETURN && customerId != null -> tr("مرتجع من دَين")
    type == OpType.OLD_DEBT && supplierId != null -> tr("دين سابق لمورد")
    type == OpType.OLD_DEBT -> tr("دين سابق على زبون")
    else -> OpType.label(type)
}

/** مورد يبيع للمحل بالدَّين (الإصدار 6) */
@Entity(tableName = "suppliers", indices = [Index("shopId")])
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val name: String,
    val phone: String,
    val createdAt: Long,
)

data class SupplierDebt(
    @Embedded val supplier: Supplier,
    val debt: Long,
)

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
    /** هل يُسمح له بالتخفيض وتغيير السعر عند البيع (الإصدار 6) */
    @ColumnInfo(defaultValue = "0") val canDiscount: Boolean = false,
)

data class WorkerSales(val workerId: Long?, val count: Long, val amount: Long)

data class ProductSales(
    val name: String,
    val qty: Long,
    val amount: Long,
    val profit: Long,
)

data class DaySales(val day: String, val amount: Long, val profit: Long)

/** مبلغ يومي واحد (لتركيب المبيعات اليومية) */
data class DayAmount(val day: String, val amount: Long)

/** كمية سبق إرجاعها من كل منتج في بيع معين */
data class ReturnedQty(val productId: Long, val qty: Long, val amount: Long)

data class NamedTotal(val name: String, val count: Long, val amount: Long)

data class ProductLastSale(
    @Embedded val product: Product,
    val lastSale: Long?,
)

data class CustomerDebt(
    @Embedded val customer: Customer,
    val debt: Long,
)

/**
 * سطر من عملية: هو أيضًا حركة المخزون للمنتج.
 * qty بالألف؛ وفي التسوية تكون سالبة للنقص وموجبة للزيادة.
 * amount = قيمة السطر بسعره (مقرّبة)، costAmount = قيمته بالتكلفة: يُخزَّنان لتكون كل الحسابات مطابقة تمامًا.
 */
@Entity(tableName = "operation_lines", indices = [Index("opId"), Index("productId")])
data class OperationLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val opId: Long,
    val productId: Long,
    val productName: String,
    val barcode: String,
    val qty: Long,
    /** سعر الوحدة في هذه العملية (سعر البيع في البيع، سعر الشراء في الشراء) */
    val unitPrice: Long,
    /** تكلفة الوحدة وقت العملية (لحساب الربح) */
    val unitCost: Long,
    @ColumnInfo(defaultValue = "0") val amount: Long = 0,
    @ColumnInfo(defaultValue = "0") val costAmount: Long = 0,
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
    val qty: Long,
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
    val qty: Long,
    val unit: String = "",
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
