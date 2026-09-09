package vn.ecohome.pos0210.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.util.UUID

object ManagedMedia {
    fun importImage(context: Context, uriString: String?, prefix: String): String? {
        if (uriString.isNullOrBlank()) return null
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") {
            val path = uri.path.orEmpty()
            if (path.contains("/managed_media/")) return uriString
        }

        val dir = File(context.filesDir, "managed_media").apply { mkdirs() }
        val ext = runCatching {
            context.contentResolver.getType(uri)
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        }.getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 2..5 }
            ?: "jpg"

        val out = File(dir, prefix + "_" + UUID.randomUUID().toString() + "." + ext)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Không đọc được ảnh nguồn" }
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(out).toString()
    }
}
