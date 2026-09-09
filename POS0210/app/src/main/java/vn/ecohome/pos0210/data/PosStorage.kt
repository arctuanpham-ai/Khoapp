package vn.ecohome.pos0210.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

object PosStorage {
    const val ROOT = "Download/POS0210/"
    const val CONFIG_PATH = "Download/POS0210/CONFIG/"
    const val DATA_PATH = "Download/POS0210/DATA/"
    const val ARCHIVE_PATH = "Download/POS0210/ARCHIVE/"

    private const val MARKER = ".pos0210"

    fun ensureFolders(context: Context): Result<Unit> = runCatching {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Cần Android 10 trở lên" }
        listOf(CONFIG_PATH, DATA_PATH, ARCHIVE_PATH).forEach { ensureMarker(context, it) }
    }

    fun find(context: Context, path: String, name: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?"
        val args = arrayOf(name, path)
        resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            selection,
            args,
            MediaStore.Downloads.DATE_MODIFIED + " DESC"
        )?.use { c ->
            if (c.moveToFirst()) {
                return Uri.withAppendedPath(collection, c.getLong(0).toString())
            }
        }
        return null
    }

    fun create(context: Context, path: String, name: String, mime: String = "application/octet-stream"): Uri {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Cần Android 10 trở lên" }
        return context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, path)
            }
        ) ?: error("Không tạo được $name")
    }

    fun delete(context: Context, uri: Uri?) {
        if (uri != null) runCatching { context.contentResolver.delete(uri, null, null) }
    }

    fun rename(context: Context, uri: Uri, newName: String): Uri {
        val updated = context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, newName) },
            null,
            null
        )
        require(updated > 0) { "Không đổi tên file backup" }
        return uri
    }

    private fun ensureMarker(context: Context, path: String) {
        if (find(context, path, MARKER) != null) return
        val uri = create(context, path, MARKER, "text/plain")
        context.contentResolver.openOutputStream(uri, "w").use { out ->
            requireNotNull(out)
            out.write("POS0210 folder marker".toByteArray())
        }
    }
}
