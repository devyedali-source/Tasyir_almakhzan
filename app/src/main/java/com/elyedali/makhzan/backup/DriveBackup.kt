package com.elyedali.makhzan.backup

import android.accounts.Account
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elyedali.makhzan.auth.Auth
import com.elyedali.makhzan.data.AppDatabase
import com.elyedali.makhzan.data.Prefs
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** نتيجة طلب الإذن بالوصول إلى Drive */
sealed interface DriveAccess {
    data class Granted(val token: String) : DriveAccess
    data class NeedsConsent(val intent: PendingIntent) : DriveAccess
    data class Failed(val message: String) : DriveAccess
}

/**
 * النسخ الاحتياطي على Google Drive الخاص بالمستخدم، في مجلد التطبيق المخفي (appDataFolder):
 * ملف واحد يحتوي قاعدة البيانات وشعارات المحلات. لا يُرسل شيء منه إلى خادمنا.
 */
object DriveBackup {
    private const val BACKUP_NAME = "makhzan_backup.zip"
    private val SCOPE = Scope("https://www.googleapis.com/auth/drive.appdata")

    private fun request(): AuthorizationRequest {
        val b = AuthorizationRequest.builder().setRequestedScopes(listOf(SCOPE))
        Auth.auth.currentUser?.email?.let { b.setAccount(Account(it, "com.google")) }
        return b.build()
    }

    suspend fun access(context: Context): DriveAccess = try {
        val r = Identity.getAuthorizationClient(context).authorize(request()).await()
        val pi = r.pendingIntent
        when {
            r.hasResolution() && pi != null -> DriveAccess.NeedsConsent(pi)
            r.accessToken != null -> DriveAccess.Granted(r.accessToken!!)
            else -> DriveAccess.Failed("no token")
        }
    } catch (e: Exception) {
        DriveAccess.Failed(e.message ?: e.javaClass.simpleName)
    }

    /** بعد عودة المستخدم من نافذة الموافقة */
    fun tokenFromConsent(activity: Activity, data: Intent?): String? = try {
        Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data).accessToken
    } catch (e: Exception) {
        null
    }

    // ---------------- Drive REST ----------------

    private fun open(url: String, token: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 60000
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun HttpURLConnection.readBody(): String {
        val ok = responseCode in 200..299
        val stream = if (ok) inputStream else errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (!ok) throw IllegalStateException("Drive HTTP $responseCode: ${text.take(200)}")
        return text
    }

    data class BackupInfo(val id: String, val modified: String)

    suspend fun findBackup(token: String): BackupInfo? = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode("name = '$BACKUP_NAME' and trashed = false", "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q" +
            "&fields=files(id,modifiedTime)&orderBy=modifiedTime%20desc"
        val json = JSONObject(open(url, token).readBody())
        val files = json.optJSONArray("files")
        if (files == null || files.length() == 0) null
        else files.getJSONObject(0).let { BackupInfo(it.getString("id"), it.optString("modifiedTime")) }
    }

    private fun upload(token: String, existingId: String?, zip: File) {
        if (existingId != null) {
            val c = open("https://www.googleapis.com/upload/drive/v3/files/$existingId?uploadType=media", token, "POST")
            c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            c.setRequestProperty("Content-Type", "application/zip")
            c.doOutput = true
            c.outputStream.use { out -> FileInputStream(zip).use { it.copyTo(out) } }
            c.readBody()
        } else {
            val boundary = "makhzan_${System.currentTimeMillis()}"
            val c = open("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", token, "POST")
            c.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            c.doOutput = true
            c.outputStream.use { out ->
                val meta = """{"name":"$BACKUP_NAME","parents":["appDataFolder"]}"""
                out.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n".toByteArray())
                out.write("--$boundary\r\nContent-Type: application/zip\r\n\r\n".toByteArray())
                FileInputStream(zip).use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            c.readBody()
        }
    }

    // ---------------- إنشاء النسخة واسترجاعها ----------------

    /** ينسخ ملفات القاعدة أثناء قفل الكتابة حتى تكون النسخة متناسقة */
    private fun createZip(context: Context): File {
        val db = AppDatabase.get(context)
        val dbFile = context.getDatabasePath(AppDatabase.FILE_NAME)
        val out = File(context.cacheDir, "backup_upload.zip")
        db.runInTransaction {
            ZipOutputStream(FileOutputStream(out)).use { zip ->
                fun add(f: File, name: String) {
                    if (!f.exists()) return
                    zip.putNextEntry(ZipEntry(name))
                    FileInputStream(f).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                add(dbFile, AppDatabase.FILE_NAME)
                add(File(dbFile.path + "-wal"), AppDatabase.FILE_NAME + "-wal")
                File(context.filesDir, "logos").listFiles()?.forEach { add(it, "logos/${it.name}") }
            }
        }
        return out
    }

    /** يرفع نسخة كاملة. يُرجع null عند النجاح أو سبب الفشل. */
    suspend fun backupNow(context: Context, token: String): String? = withContext(Dispatchers.IO) {
        try {
            val zip = createZip(context)
            val existing = findBackup(token)
            upload(token, existing?.id, zip)
            zip.delete()
            Prefs.setLastBackupAt(context, System.currentTimeMillis())
            null
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    /** ينزّل النسخة ويستبدل بها بيانات الهاتف. بعدها يجب إعادة تشغيل التطبيق. */
    suspend fun restore(context: Context, token: String, info: BackupInfo) = withContext(Dispatchers.IO) {
        val c = open("https://www.googleapis.com/drive/v3/files/${info.id}?alt=media", token)
        if (c.responseCode !in 200..299) throw IllegalStateException("Drive HTTP ${c.responseCode}")
        val tmp = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(c.inputStream).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val target = File(tmp, e.name)
                if (!target.canonicalPath.startsWith(tmp.canonicalPath + File.separator)) continue
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { zip.copyTo(it) }
            }
        }
        val newDb = File(tmp, AppDatabase.FILE_NAME)
        if (!newDb.exists()) throw IllegalStateException("النسخة الاحتياطية غير صالحة")

        AppDatabase.closeAndReset()
        val dbFile = context.getDatabasePath(AppDatabase.FILE_NAME)
        dbFile.parentFile?.mkdirs()
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
        newDb.copyTo(dbFile, overwrite = true)
        File(tmp, AppDatabase.FILE_NAME + "-wal").takeIf { it.exists() }?.copyTo(File(dbFile.path + "-wal"), overwrite = true)

        val logos = File(context.filesDir, "logos").apply { deleteRecursively(); mkdirs() }
        File(tmp, "logos").listFiles()?.forEach { it.copyTo(File(logos, it.name), overwrite = true) }
        tmp.deleteRecursively()
        Prefs.setLastBackupAt(context, System.currentTimeMillis())
    }

    fun restartApp(activity: Activity) {
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    /** يحذف بيانات الهاتف عند تسجيل الخروج (بعد التأكد من حفظها على Drive) */
    fun wipeLocal(context: Context) {
        AppDatabase.get(context).clearAllTables()
        File(context.filesDir, "logos").deleteRecursively()
        File(context.cacheDir, "invoices").deleteRecursively()
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Prefs.clearAll(context)
    }

    // ---------------- الجدولة في الخلفية ----------------

    const val WORK_NAME = "drive_backup"

    /** يُستدعى بعد كل تغيير في البيانات: ينتظر دقيقة ثم يرفع النسخة متى توفر الإنترنت */
    fun schedule(context: Context) {
        val uid = Auth.auth.currentUser?.uid ?: return
        Prefs.setDirtyAt(context, System.currentTimeMillis())
        if (!Prefs.restoreChecked(context, uid)) return
        val req = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(60, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 60, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, req)
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val uid = Auth.auth.currentUser?.uid ?: return Result.success()
        if (!Prefs.restoreChecked(ctx, uid)) return Result.success()
        val started = System.currentTimeMillis()
        return when (val a = DriveBackup.access(ctx)) {
            is DriveAccess.NeedsConsent -> {
                Prefs.setDriveNeedsConsent(ctx, true)
                Result.failure()
            }
            is DriveAccess.Failed -> if (runAttemptCount < 20) Result.retry() else Result.failure()
            is DriveAccess.Granted -> {
                Prefs.setDriveNeedsConsent(ctx, false)
                val err = DriveBackup.backupNow(ctx, a.token)
                when {
                    err != null -> if (runAttemptCount < 20) Result.retry() else Result.failure()
                    // تغيّرت البيانات أثناء الرفع: نعيد بعد قليل
                    Prefs.dirtyAt(ctx) > started -> Result.retry()
                    else -> Result.success()
                }
            }
        }
    }
}

