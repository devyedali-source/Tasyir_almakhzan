package com.elyedali.makhzan.ui

import android.content.Context
import com.elyedali.makhzan.data.TrustedClock
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.elyedali.makhzan.data.ShopDao
import com.elyedali.makhzan.data.ShopProfile
import com.elyedali.makhzan.invoice.InvoiceData
import com.elyedali.makhzan.invoice.InvoiceLine
import com.elyedali.makhzan.invoice.InvoicePdf
import com.elyedali.makhzan.print.ThermalPrinter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import com.elyedali.makhzan.i18n.tr

/**
 * عرض الفاتورة مع المشاركة والطباعة.
 * opId = null يعني فاتورة تجريبية لمعاينة التصميم من الإعدادات.
 */
@Composable
fun InvoiceScreen(dao: ShopDao, shopId: Long, opId: Long?, onBack: () -> Unit) {
    val context = LocalContext.current
    var file by remember { mutableStateOf<File?>(null) }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var invoice by remember { mutableStateOf<InvoiceData?>(null) }
    var printing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hasPrinter = remember { ThermalPrinter.isConfigured(context) }
    val btPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun printBluetooth() {
        val data = invoice ?: return
        if (!ThermalPrinter.hasPermission(context)) { btPermission.launch(ThermalPrinter.permission); return }
        printing = true
        scope.launch {
            try {
                printReceipt(context, dao, shopId, data)
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: tr("تعذر الطباعة"))
            } finally {
                printing = false
            }
        }
    }

    LaunchedEffect(shopId, opId) {
        try {
            val shop = dao.shopNow(shopId) ?: run { error = tr("العملية غير موجودة (ربما أُلغيت)"); return@LaunchedEffect }
            val profile = dao.profileNow(shopId) ?: ShopProfile(shopId = shopId)
            val data = if (opId == null) sampleInvoice() else loadInvoice(dao, opId)
                ?: run { error = tr("العملية غير موجودة (ربما أُلغيت)"); return@LaunchedEffect }
            val name = if (opId == null) "invoice_preview.pdf" else "invoice_${opId}.pdf"
            val f = withContext(Dispatchers.IO) { InvoicePdf.create(context, shop.name, profile, data, name) }
            pages = withContext(Dispatchers.IO) { renderPages(f) }
            invoice = data
            file = f
        } catch (e: Exception) {
            error = tr("تعذر إنشاء الفاتورة: %1\$s", e.message ?: "")
        }
    }

    Scaffold(
        topBar = { AppTopBar(if (opId == null) tr("معاينة تصميم الفاتورة") else tr("فاتورة رقم %1\$s", opId), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val f = file
            if (f != null) {
                Surface(tonalElevation = 6.dp, shadowElevation = 6.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(onClick = { sharePdf(context, f) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Text(tr("  مشاركة"))
                        }
                        // طابعة بلوتوث مختارة: طباعة مباشرة بضغطة؛ وإلا فطباعة أندرويد العادية
                        if (hasPrinter && opId != null) {
                            OutlinedButton(enabled = !printing, onClick = ::printBluetooth, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Print, contentDescription = null)
                                Text("  " + tr("إيصال"))
                            }
                        }
                        OutlinedButton(onClick = { printPdf(context, f) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Print, contentDescription = null)
                            Text(tr("  طباعة"))
                        }
                    }
                }
            }
        },
    ) { pad ->
        Box(
            Modifier.padding(pad).fillMaxSize().background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                error != null -> Text(error!!)
                pages.isEmpty() -> CircularProgressIndicator()
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(pages) { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(), contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().background(Color.White),
                        )
                    }
                }
            }
        }
    }
}

/** بيانات فاتورة بيع من قاعدة البيانات (null إذا أُلغيت العملية) */
internal suspend fun loadInvoice(dao: ShopDao, opId: Long): InvoiceData? {
    val op = dao.operationNow(opId) ?: return null
    return InvoiceData(
        number = op.id, time = op.time,
        lines = dao.lines(op.id).map {
            InvoiceLine(it.productName, it.qty, it.unitPrice, it.amount, dao.productNow(it.productId)?.unit ?: "")
        },
        creditCustomer = op.customerId?.let { dao.customerNow(it)?.name },
        discount = op.discount,
    )
}

internal fun sampleInvoice() = InvoiceData(
    number = 1,
    time = TrustedClock.now(),
    lines = listOf(
        InvoiceLine(tr("حليب مجفف 400غ"), 2000, 15000, 30000),
        InvoiceLine(tr("أرز"), 1500, 4000, 6000, "kg"),
        InvoiceLine(tr("شاي أخضر"), 3000, 2000, 6000),
    ),
)

internal fun renderPages(file: File): List<Bitmap> {
    val result = mutableListOf<Bitmap>()
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val scale = 2
                    val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    result.add(bmp)
                }
            }
        }
    }
    return result
}

internal fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, tr("مشاركة")))
}

internal fun printPdf(context: Context, file: File) {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
            val info = PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?, destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?, callback: WriteResultCallback,
        ) {
            try {
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
    manager.print(file.nameWithoutExtension, adapter, null)
}


/** عرض أي ملف PDF (التقارير) مع المشاركة والطباعة */
@Composable
fun PdfViewScreen(path: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val file = remember(path) { File(path) }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(path) {
        try {
            pages = withContext(Dispatchers.IO) { renderPages(file) }
        } catch (e: Exception) {
            error = e.message ?: ""
        }
    }
    Scaffold(
        topBar = { AppTopBar(title, onBack) },
        bottomBar = {
            Surface(tonalElevation = 6.dp, shadowElevation = 6.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = { sharePdf(context, file) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Text("  " + tr("مشاركة"))
                    }
                    OutlinedButton(onClick = { printPdf(context, file) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Print, contentDescription = null)
                        Text("  " + tr("طباعة"))
                    }
                }
            }
        },
    ) { pad ->
        Box(
            Modifier.padding(pad).fillMaxSize().background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                error != null -> Text(error!!)
                pages.isEmpty() -> CircularProgressIndicator()
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(pages) { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(), contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().background(Color.White),
                        )
                    }
                }
            }
        }
    }
}
