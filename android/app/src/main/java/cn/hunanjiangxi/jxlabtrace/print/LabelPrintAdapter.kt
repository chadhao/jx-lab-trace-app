package cn.hunanjiangxi.jxlabtrace.print

import android.content.Context
import android.graphics.Bitmap
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PdfDocument
import java.io.FileOutputStream

/**
 * 把单张标签位图作为一页 PDF 交给系统打印服务（B3-a 系统打印出口）。
 * 页面按 [PrinterProfile.SIZE_MM] 毫米 1:1 映射，不缩放。
 */
internal class LabelPrintAdapter(
    @Suppress("unused") private val context: Context,
    private val jobName: String,
    private val bitmap: Bitmap,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: android.print.PrintAttributes?,
        newAttributes: android.print.PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        bundle: android.os.Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
        }
        // 72pt/inch；1 inch = 25.4mm
        val pageW = PrinterProfile.SIZE_MM * 72 / 25
        val pageH = PrinterProfile.SIZE_MM * 72 / 25
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
        val canvas = page.canvas
        canvas.drawBitmap(
            bitmap,
            null,
            android.graphics.RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat()),
            null,
        )
        doc.finishPage(page)
        try {
            FileOutputStream(destination.fileDescriptor).use { out -> doc.writeTo(out) }
            callback.onWriteFinished(arrayOf(PageRange(0, 0)))
        } finally {
            doc.close()
        }
    }
}
