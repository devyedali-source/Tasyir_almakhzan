package com.elyedali.makhzan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * قاعدة البيانات المحلية. لا يوجد fallbackToDestructiveMigration عمدًا:
 * أي تغيير مستقبلي في الجداول يكون بترحيل (Migration) يحفظ بيانات التاجر.
 */
@Database(
    entities = [Shop::class, Product::class, Operation::class, OperationLine::class, ShopProfile::class, Customer::class, Worker::class, Supplier::class],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ShopDao

    companion object {
        /** الإصدار 2: إضافة جدول معلومات المحل والفواتير دون المساس بأي بيانات موجودة */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shop_profile` (`shopId` INTEGER NOT NULL, " +
                        "`address` TEXT NOT NULL, `phone` TEXT NOT NULL, `email` TEXT NOT NULL, " +
                        "`taxNumber` TEXT NOT NULL, `logoPath` TEXT, `design` INTEGER NOT NULL, " +
                        "`language` TEXT NOT NULL, `showStamp` INTEGER NOT NULL, PRIMARY KEY(`shopId`))"
                )
            }
        }

        /** الإصدار 3: معرّف المحل على الخادم + عملة المحل */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `shops` ADD COLUMN `remoteId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `shop_profile` ADD COLUMN `currency` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** الإصدار 4: الزبائن والبيع بالدَّين، دون المساس بأي بيانات موجودة */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `customers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`shopId` INTEGER NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_shopId` ON `customers` (`shopId`)")
                db.execSQL("ALTER TABLE `operations` ADD COLUMN `customerId` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_customerId` ON `operations` (`customerId`)")
            }
        }

        /** الإصدار 5: العمال (وضع البائع) ونسبة كل عملية إلى من سجّلها */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`shopId` INTEGER NOT NULL, `name` TEXT NOT NULL, `pinHash` TEXT NOT NULL, " +
                        "`canCredit` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workers_shopId` ON `workers` (`shopId`)")
                db.execSQL("ALTER TABLE `operations` ADD COLUMN `workerId` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_workerId` ON `operations` (`workerId`)")
            }
        }

        /**
         * الإصدار 6: البيع بالوزن (الكميات بالألف + وحدة المنتج)، قيمة كل سطر محفوظة،
         * التخفيض، المرتجعات، الموردون والشراء بالدَّين، وصلاحية التخفيض للعامل.
         * الكميات القديمة (أعداد صحيحة) تُضرب في 1000 فتبقى كما هي تمامًا.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `products` ADD COLUMN `unit` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `products` SET `qty` = `qty` * 1000")
                db.execSQL("ALTER TABLE `operation_lines` ADD COLUMN `amount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `operation_lines` ADD COLUMN `costAmount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `operation_lines` SET `amount` = `unitPrice` * `qty`, `costAmount` = `unitCost` * `qty`")
                db.execSQL("UPDATE `operation_lines` SET `qty` = `qty` * 1000")
                db.execSQL("ALTER TABLE `operations` ADD COLUMN `discount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `operations` ADD COLUMN `refId` INTEGER")
                db.execSQL("ALTER TABLE `operations` ADD COLUMN `supplierId` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_refId` ON `operations` (`refId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_supplierId` ON `operations` (`supplierId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `suppliers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`shopId` INTEGER NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_suppliers_shopId` ON `suppliers` (`shopId`)")
                db.execSQL("ALTER TABLE `workers` ADD COLUMN `canDiscount` INTEGER NOT NULL DEFAULT 0")
            }
        }

        const val FILE_NAME = "makhzan.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, FILE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
        }

        /** يغلق القاعدة قبل استبدال ملفها عند الاسترجاع من Drive */
        fun closeAndReset() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
