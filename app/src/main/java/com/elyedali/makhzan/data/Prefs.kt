package com.elyedali.makhzan.data

import android.content.Context

/** إعدادات صغيرة محفوظة في الهاتف (حالة النسخ الاحتياطي) */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("makhzan_prefs", Context.MODE_PRIVATE)

    /** تم التحقق من Drive بعد تسجيل الدخول (لا نرفع نسخة قبل ذلك حتى لا نكتب فوق نسخة قديمة أهم) */
    fun restoreChecked(c: Context, uid: String) = sp(c).getBoolean("restore_checked_$uid", false)
    fun setRestoreChecked(c: Context, uid: String, v: Boolean) =
        sp(c).edit().putBoolean("restore_checked_$uid", v).commit()

    fun lastBackupAt(c: Context) = sp(c).getLong("last_backup_at", 0L)
    fun setLastBackupAt(c: Context, t: Long) = sp(c).edit().putLong("last_backup_at", t).commit()

    fun dirtyAt(c: Context) = sp(c).getLong("dirty_at", 0L)
    fun setDirtyAt(c: Context, t: Long) = sp(c).edit().putLong("dirty_at", t).commit()

    /** يحتاج المستخدم إلى إعطاء الإذن بالحفظ على Drive */
    fun driveNeedsConsent(c: Context) = sp(c).getBoolean("drive_needs_consent", false)
    fun setDriveNeedsConsent(c: Context, v: Boolean) = sp(c).edit().putBoolean("drive_needs_consent", v).commit()

    fun registered(c: Context, uid: String) = sp(c).getBoolean("registered_$uid", false)
    fun setRegistered(c: Context, uid: String) = sp(c).edit().putBoolean("registered_$uid", true).commit()

    // ---------- وضع البائع ----------
    /** رمز صاحب المحل مشفّرًا (يحمي الخروج من وضع البائع) */
    fun ownerPinHash(c: Context): String? = sp(c).getString("owner_pin_hash", null)
    fun setOwnerPinHash(c: Context, v: String?) = sp(c).edit().putString("owner_pin_hash", v).commit()

    /** المحل المسلَّم للعمال على هذا الهاتف (null = وضع صاحب المحل) */
    fun workerModeShop(c: Context): Long? = sp(c).getLong("worker_mode_shop", -1L).takeIf { it > 0 }
    fun setWorkerModeShop(c: Context, shopId: Long?) =
        sp(c).edit().putLong("worker_mode_shop", shopId ?: -1L).commit()

    /** بداية الوردية الحالية لكل عامل (لحساب ما سيسلّمه) */
    fun shiftStart(c: Context, workerId: Long): Long = sp(c).getLong("shift_start_$workerId", 0L)
    fun setShiftStart(c: Context, workerId: Long, t: Long) = sp(c).edit().putLong("shift_start_$workerId", t).commit()

    fun language(c: Context): String? = sp(c).getString("ui_language", null)
    fun setLanguage(c: Context, v: String) = sp(c).edit().putString("ui_language", v).commit()

    /** يحذف كل شيء ما عدا لغة الواجهة */
    fun clearAll(c: Context) {
        val lang = language(c)
        sp(c).edit().clear().commit()
        if (lang != null) setLanguage(c, lang)
    }
}
