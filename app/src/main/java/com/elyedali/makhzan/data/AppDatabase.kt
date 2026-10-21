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
    entities = [Shop::class, Product::class, Operation::class, OperationLine::class, ShopProfile::class],
    version = 3,
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

        const val FILE_NAME = "makhzan.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, FILE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }

        /** يغلق القاعدة قبل استبدال ملفها عند الاسترجاع من Drive */
        fun closeAndReset() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
