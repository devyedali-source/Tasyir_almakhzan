package com.elyedali.makhzan.ui

import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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

    LaunchedEffect(shopId, opId) {
        try {
            val shop = dao.shopNow(shopId) ?: return@LaunchedEffect
            val profile = dao.profileNow(shopId) ?: ShopProfile(shopId = shopId)
            val data = if (opId == null) sampleInvoice() else {
                val op = dao.operationNow(opId) ?: return@LaunchedEffect
                InvoiceData(
                    number = op.id, time = op.time,
                    lines = dao.lines(op.id).map { InvoiceLine(it.productName, it.qty, it.unitPrice) },
                )
            }
            val name = if (opId == null) "invoice_preview.pdf" else "invoice_${opId}.pdf"
            val f = withContext(Dispatchers.IO) { InvoicePdf.create(context, shop.name, profile, data, name) }
            pages = withContext(Dispatchers.IO) { renderPages(f) }
            file = f
        } catch (e: Exception) {
            error = "تعذر إنشاء الفاتورة: ${e.message ?: ""}"
        }
    }

    Scaffold(
        topBar = { AppTopBar(if (opId == null) "معاينة تصميم الفاتورة" else "فاتورة رقم $opId", onBack) },
        bottomBar = {
            val f = file
            if (f != null) {
                Surface(tonalElevation = 6.dp, shadowElevation = 6.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(onClick = { share(context, f) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Text("  مشاركة")
                        }
                        OutlinedButton(onClick = { print(context, f) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Print, contentDescription = null)
                            Text("  طباعة")
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

private fun sampleInvoice() = InvoiceData(
    number = 1,
    time = System.currentTimeMillis(),
    lines = listOf(
        InvoiceLine("حليب مجفف 400غ", 2, 15000),
        InvoiceLine("سكر 1 كغ", 1, 4500),
        InvoiceLine("شاي أخضر", 3, 2000),
    ),
)

private fun renderPages(file: File): List<Bitmap> {
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

private fun share(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة الفاتورة"))
}

private fun print(context: Context, file: File) {
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
