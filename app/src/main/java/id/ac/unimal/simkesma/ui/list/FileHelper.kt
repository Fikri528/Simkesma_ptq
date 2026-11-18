package id.ac.unimal.simkesma.ui.list

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import id.ac.unimal.simkesma.R
import id.ac.unimal.simkesma.data.Repo
import id.ac.unimal.simkesma.util.UiExt
import java.io.File

object FileHelper {
    const val MAX_BYTES = 700_000

    /** Membuka berkas langsung di aplikasi (tanpa download). */
    fun open(ctx: Context, id: String) {
        resolveFile(id) { f ->
            if (f == null) { UiExt.toast(ctx, R.string.err_file_missing); return@resolveFile }
            try {
                val bytes = Base64.decode(f.third, Base64.DEFAULT)
                val tempFile = File(ctx.cacheDir, f.first.ifBlank { "berkas.pdf" })
                tempFile.writeBytes(bytes)
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", tempFile)
                val type = f.second.ifBlank { "application/octet-stream" }
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                UiExt.toast(ctx, R.string.err_file_read)
            }
        }
    }

    /** Mengunduh berkas ke folder Download. */
    fun download(ctx: Context, id: String) {
        resolveFile(id) { f ->
            if (f == null) { UiExt.toast(ctx, R.string.err_file_missing); return@resolveFile }
            try {
                val bytes = Base64.decode(f.third, Base64.DEFAULT)
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, f.first)
                    put(MediaStore.Downloads.MIME_TYPE, f.second)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SIMKESMA")
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException()
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                UiExt.toast(ctx, ctx.getString(R.string.file_saved, f.first))
            } catch (e: Exception) {
                UiExt.toast(ctx, R.string.err_file_save)
            }
        }
    }

    private fun resolveFile(id: String, cb: (Triple<String, String, String>?) -> Unit) {
        Repo.loadFile(id) { f ->
            if (f != null) { cb(f); return@loadFile }
            var found: Triple<String, String, String>? = null
            for ((_, items) in Repo.memoryCache) {
                val item = items.firstOrNull { it.id == id }
                if (item != null) {
                    val b64 = item.str("lampiran").ifBlank { item.str("berkas").ifBlank { item.str("foto") } }
                    val name = item.str("lampiranNama").ifBlank { item.str("berkasNama").ifBlank { item.str("namaLengkap") + ".jpg" } }
                    val mime = item.str("lampiranMime").ifBlank { item.str("berkasMime").ifBlank { "application/octet-stream" } }
                    if (b64.isNotBlank()) {
                        found = Triple(name, mime, b64)
                        break
                    }
                }
            }
            cb(found)
        }
    }
}
