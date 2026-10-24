package com.elyedali.makhzan

import android.app.Application
import com.elyedali.makhzan.data.TrustedClock
import androidx.room.InvalidationTracker
import com.elyedali.makhzan.backup.DriveBackup
import com.elyedali.makhzan.billing.PlayBilling
import com.google.firebase.auth.FirebaseAuth
import com.elyedali.makhzan.data.AppDatabase
import com.elyedali.makhzan.i18n.I18n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class MakhzanApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        I18n.init(this)
        // ساعة موثوقة: تمنع إطالة التفعيل بتغيير تاريخ الهاتف (كما في دفتر المعلم)
        TrustedClock.init(this)
        appScope.launch { TrustedClock.syncWithServer() }
        PlayBilling.init(this)
        // مشتريات دُفعت ولم يُكتب تفعيلها بعد (انقطاع الاتصال): تُعالج عند كل دخول
        FirebaseAuth.getInstance().addAuthStateListener { if (it.currentUser != null) PlayBilling.processPending() }
        val db = AppDatabase.get(this)

        // كل تغيير في البيانات يطلب نسخة احتياطية في الخلفية على Drive
        db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(
            arrayOf("shops", "products", "operations", "operation_lines", "shop_profile", "customers", "workers")
        ) {
            override fun onInvalidated(tables: Set<String>) {
                DriveBackup.schedule(this@MakhzanApp)
            }
        })

        // معرّف ثابت لكل محل قديم لم يأخذ معرّفًا بعد
        appScope.launch {
            val dao = db.dao()
            dao.shopsWithoutRemoteId().forEach { dao.setRemoteId(it.id, UUID.randomUUID().toString()) }
        }
    }
}
