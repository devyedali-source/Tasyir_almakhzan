package com.elyedali.makhzan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * قاعدة البيانات المحلية. لا يوجد fallbackToDestructiveMigration عمدًا:
 * أي تغيير مستقبلي في الجداول يكون بترحيل (Migration) يحفظ بيانات التاجر.
 */
@Database(
    entities = [Shop::class, Product::class, Operation::class, OperationLine::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ShopDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "makhzan.db"
            ).build().also { instance = it }
        }
    }
}
