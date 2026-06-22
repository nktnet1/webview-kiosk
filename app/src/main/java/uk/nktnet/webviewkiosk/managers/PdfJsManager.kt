package uk.nktnet.webviewkiosk.managers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.config.Constants.PDF_JS_ASSETS_DIR
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object PdfJsManager {
    private val assetUrls = mapOf(
        "https://cdn.jsdelivr.net/npm/pdfjs-dist/legacy/build/pdf.mjs" to "pdf.mjs",
        "https://cdn.jsdelivr.net/npm/pdfjs-dist/legacy/build/pdf.worker.mjs" to "pdf.worker.mjs"
    )

    private fun getTargetDirectory(context: Context): File {
        return File(context.filesDir, PDF_JS_ASSETS_DIR)
    }

    suspend fun downloadAssets(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetDir = getTargetDirectory(context)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            for ((url, fileName) in assetUrls) {
                val outputFile = File(targetDir, fileName)

                outputFile.parentFile?.let { parent ->
                    if (!parent.exists()) {
                        parent.mkdirs()
                    }
                }

                if (!outputFile.exists()) {
                    URL(url).openStream().use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            ToastManager.show(context, "Download complete")
            true
        } catch (e: Exception) {
            Log.e(javaClass.simpleName, "Failed to download PDF.js", e)
            ToastManager.show(context, "Download failed: ${e.message}")
            false
        }
    }

    fun areAssetsReady(context: Context): Boolean {
        val targetDir = getTargetDirectory(context)
        return targetDir.exists() && assetUrls.values.all {
            File(targetDir, it).exists()
        }
    }

    fun clearAssets(context: Context) {
        try {
            getTargetDirectory(context).deleteRecursively()
            ToastManager.show(context, "PDF.js asset deleted.")
        } catch (e: Exception) {
            ToastManager.show(context, "PDF.js asset deletion failed: ${e.message}")
        }
    }
}
