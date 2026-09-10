package uk.nktnet.webviewkiosk.managers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.Constants.PDF_JS_ASSETS_DIR
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL

object PdfJsManager {
    private const val PDF_JS_VERSION = "6.3.289"
    private const val VERSION_FILE = ".version"

    private val assetUrls = mapOf(
        "https://cdn.jsdelivr.net/npm/pdfjs-dist@$PDF_JS_VERSION/legacy/build/pdf.mjs" to "pdf.mjs",
        "https://cdn.jsdelivr.net/npm/pdfjs-dist@$PDF_JS_VERSION/legacy/build/pdf.worker.mjs" to "pdf.worker.mjs"
    )

    private fun getTargetDirectory(context: Context): File {
        return File(context.filesDir, PDF_JS_ASSETS_DIR)
    }

    private fun isCurrentVersion(targetDir: File): Boolean {
        return runCatching {
            File(targetDir, VERSION_FILE).readText().trim() == PDF_JS_VERSION
        }.getOrDefault(false)
    }

    private fun downloadAsset(url: String, outputFile: File) {
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.part")
        tempFile.delete()

        try {
            URL(url).openStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!tempFile.isFile || tempFile.length() == 0L) {
                throw IOException("Downloaded ${outputFile.name} is empty")
            }

            if (!tempFile.renameTo(outputFile)) {
                throw IOException("Failed to finalise ${outputFile.name}")
            }
        } finally {
            tempFile.delete()
        }
    }

    suspend fun downloadAssets(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetDir = getTargetDirectory(context)

            if (!isCurrentVersion(targetDir)) {
                if (targetDir.exists() && !targetDir.deleteRecursively()) {
                    throw IOException("Failed to clear old PDF.js assets")
                }
            }

            if (!targetDir.exists() && !targetDir.mkdirs()) {
                throw IOException("Failed to create PDF.js asset directory")
            }

            for ((url, fileName) in assetUrls) {
                val outputFile = File(targetDir, fileName)

                if (!outputFile.isFile || outputFile.length() == 0L) {
                    if (outputFile.exists() && !outputFile.delete()) {
                        throw IOException("Failed to replace ${outputFile.name}")
                    }
                    downloadAsset(url, outputFile)
                }
            }

            File(targetDir, VERSION_FILE).writeText(PDF_JS_VERSION)
            ToastManager.show(context, "Download complete")
            true
        } catch (e: Exception) {
            Log.e(Constants.APP_SCHEME, "Failed to download PDF.js", e)
            ToastManager.show(context, "Download failed: ${e.message}")
            false
        }
    }

    fun areAssetsReady(context: Context): Boolean {
        val targetDir = getTargetDirectory(context)
        return (
            targetDir.exists()
                && isCurrentVersion(targetDir)
                && assetUrls.values.all {
                    File(targetDir, it).let { file ->
                        file.isFile && file.length() > 0L
                    }
                }
        )
    }

    fun clearAssets(context: Context) {
        try {
            val targetDir = getTargetDirectory(context)
            if (targetDir.exists() && !targetDir.deleteRecursively()) {
                throw IOException("Failed to delete PDF.js assets")
            }
            ToastManager.show(context, "PDF.js asset deleted.")
        } catch (e: Exception) {
            ToastManager.show(context, "PDF.js asset deletion failed: ${e.message}")
        }
    }
}
