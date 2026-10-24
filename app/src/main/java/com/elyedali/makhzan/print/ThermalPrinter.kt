package com.elyedali.makhzan.print

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import com.elyedali.makhzan.data.Prefs
import com.elyedali.makhzan.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

data class PairedPrinter(val name: String, val address: String)

/**
 * الطباعة المباشرة على الطابعات الحرارية الصغيرة (58 أو 80 مم) عبر البلوتوث بأوامر ESC/POS.
 * الإيصال يُرسم صورةً ثم يُرسل نقطيًا: هكذا يُطبع العربي والشعار والطابع كما هي في كل الطابعات،
 * دون الحاجة لدعم الطابعة للخط العربي.
 * صلاحية BLUETOOTH_CONNECT يُتحقق منها قبل كل استعمال (hasPermission).
 */
@SuppressLint("MissingPermission")
object ThermalPrinter {
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /** عرض الطباعة بالنقاط: 384 لورق 58 مم، 576 لورق 80 مم */
    const val DOTS_58 = 384
    const val DOTS_80 = 576

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    val permission: String get() = Manifest.permission.BLUETOOTH_CONNECT

    private fun adapter(context: Context) =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun isAvailable(context: Context) = adapter(context) != null

    fun isEnabled(context: Context) = adapter(context)?.isEnabled == true

    /** الأجهزة المقترنة بالهاتف (يقرن التاجر الطابعة مرة واحدة من إعدادات البلوتوث في الهاتف) */
    @SuppressLint("MissingPermission")
    fun paired(context: Context): List<PairedPrinter> {
        if (!hasPermission(context)) return emptyList()
        val a = adapter(context) ?: return emptyList()
        if (!a.isEnabled) return emptyList()
        return runCatching {
            a.bondedDevices.map { PairedPrinter(it.name ?: it.address, it.address) }.sortedBy { it.name }
        }.getOrDefault(emptyList())
    }

    fun isConfigured(context: Context) = Prefs.printerAddress(context) != null

    /** يطبع كل صفحات ملف PDF على الطابعة المختارة في الإعدادات. يرمي IOException برسالة مفهومة عند الفشل. */
    suspend fun printPdf(context: Context, file: File) = withContext(Dispatchers.IO) {
        val address = Prefs.printerAddress(context) ?: throw IOException(tr("اختر طابعة البلوتوث أولًا من إعدادات المحل"))
        val dots = Prefs.printerDots(context)
        val data = ByteArrayOutputStream()
        data.write(byteArrayOf(0x1B, 0x40)) // تهيئة الطابعة
        for (bmp in render(file, dots)) {
            data.write(raster(bmp))
            bmp.recycle()
        }
        data.write(byteArrayOf(0x1B, 0x64, 4)) // تقديم الورق 4 أسطر
        data.write(byteArrayOf(0x1D, 0x56, 66, 0)) // قصّ الورق (تتجاهله الطابعات التي لا قاطع لها)
        send(context, address, data.toByteArray())
    }

    @SuppressLint("MissingPermission")
    private fun send(context: Context, address: String, bytes: ByteArray) {
        if (!hasPermission(context)) throw IOException(tr("اسمح للتطبيق باستعمال البلوتوث"))
        val a = adapter(context) ?: throw IOException(tr("هذا الهاتف لا يدعم البلوتوث"))
        if (!a.isEnabled) throw IOException(tr("شغّل البلوتوث في الهاتف"))
        val device = runCatching { a.getRemoteDevice(address) }.getOrNull()
            ?: throw IOException(tr("الطابعة غير موجودة. اخترها من جديد في الإعدادات"))
        runCatching { a.cancelDiscovery() }
        var socket: BluetoothSocket? = null
        try {
            val secure = device.createRfcommSocketToServiceRecord(SPP)
            val connected: BluetoothSocket = try {
                socket = secure
                secure.connect()
                secure
            } catch (e: IOException) {
                runCatching { secure.close() }
                // بعض الطابعات الرخيصة لا تقبل إلا الاتصال غير المؤمَّن
                val insecure = device.createInsecureRfcommSocketToServiceRecord(SPP)
                socket = insecure
                insecure.connect()
                insecure
            }
            val out = connected.outputStream
            var i = 0
            while (i < bytes.size) {
                val n = minOf(1024, bytes.size - i)
                out.write(bytes, i, n)
                out.flush()
                i += n
            }
            // مهلة قصيرة حتى تستلم الطابعة كل البيانات قبل قطع الاتصال
            Thread.sleep((bytes.size / 6L).coerceIn(800L, 6000L))
        } catch (e: IOException) {
            throw IOException(tr("تعذر الاتصال بالطابعة. تأكد أنها مشغّلة وقريبة ومقترنة بالهاتف"))
        } finally {
            runCatching { socket?.close() }
        }
    }

    /** يرسم صفحات PDF بعرض الطابعة */
    private fun render(file: File, dots: Int): List<Bitmap> {
        val result = mutableListOf<Bitmap>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { r ->
                for (i in 0 until r.pageCount) {
                    r.openPage(i).use { page ->
                        val h = (page.height.toLong() * dots / page.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(dots, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        result.add(bmp)
                    }
                }
            }
        }
        return result
    }

    /** يحوّل الصورة إلى أبيض وأسود ويرسلها بأمر GS v 0 على شرائح (حتى لا يمتلئ مخزن الطابعة) */
    private fun raster(bmp: Bitmap): ByteArray {
        val w = bmp.width
        val h = bmp.height
        val bytesPerRow = (w + 7) / 8
        val out = ByteArrayOutputStream()
        val row = IntArray(w)
        val band = 128
        var y0 = 0
        while (y0 < h) {
            val rows = minOf(band, h - y0)
            out.write(byteArrayOf(0x1D, 0x76, 0x30, 0, (bytesPerRow and 0xFF).toByte(), (bytesPerRow shr 8).toByte(),
                (rows and 0xFF).toByte(), (rows shr 8).toByte()))
            for (y in y0 until y0 + rows) {
                bmp.getPixels(row, 0, w, 0, y, w, 1)
                val line = ByteArray(bytesPerRow)
                for (x in 0 until w) {
                    val c = row[x]
                    val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                    if (lum < 160) line[x shr 3] = (line[x shr 3].toInt() or (0x80 shr (x and 7))).toByte()
                }
                out.write(line)
            }
            y0 += rows
        }
        return out.toByteArray()
    }
}
