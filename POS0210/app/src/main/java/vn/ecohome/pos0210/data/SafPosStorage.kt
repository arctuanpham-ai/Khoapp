package vn.ecohome.pos0210.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

object SafPosStorage {
    const val ROOT_NAME = "POS0210"
    const val CONFIG_NAME = "CONFIG"
    const val DATA_NAME = "DATA"
    const val ARCHIVE_NAME = "ARCHIVE"

    fun ensureStructure(context: Context, parentTreeUriString: String): Result<Structure> = runCatching {
        require(parentTreeUriString.isNotBlank()) { "Chưa chọn nơi lưu POS0210" }
        val tree = Uri.parse(parentTreeUriString)
        val root = findChild(context, tree, ROOT_NAME)
            ?: createDir(context, tree, ROOT_NAME)
        val config = findChild(context, root, CONFIG_NAME) ?: createDir(context, root, CONFIG_NAME)
        val data = findChild(context, root, DATA_NAME) ?: createDir(context, root, DATA_NAME)
        val archive = findChild(context, root, ARCHIVE_NAME) ?: createDir(context, root, ARCHIVE_NAME)
        Structure(root, config, data, archive)
    }

    fun findFile(context: Context, folderUri: Uri, name: String): Uri? =
        children(context, folderUri).firstOrNull { it.second == name }?.first

    fun createFile(context: Context, folderUri: Uri, name: String, mime: String = "application/octet-stream"): Uri =
        DocumentsContract.createDocument(context.contentResolver, folderUri, mime, name)
            ?: error("Không tạo được file $name")

    fun overwrite(context: Context, uri: Uri, writer: (java.io.OutputStream) -> Unit) {
        context.contentResolver.openOutputStream(uri, "wt").use { out ->
            requireNotNull(out) { "Không mở được file để ghi đè" }
            writer(out)
        }
    }

    fun delete(context: Context, uri: Uri?) {
        if (uri != null) DocumentsContract.deleteDocument(context.contentResolver, uri)
    }

    private fun findChild(context: Context, parent: Uri, name: String): Uri? =
        children(context, parent).firstOrNull { it.second == name && it.third }?.first

    private fun createDir(context: Context, parent: Uri, name: String): Uri =
        DocumentsContract.createDocument(
            context.contentResolver,
            parent,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name
        ) ?: error("Không tạo được thư mục $name")

    private fun children(context: Context, parent: Uri): List<Triple<Uri, String, Boolean>> {
        val resolver = context.contentResolver
        val documentId = DocumentsContract.getDocumentId(parent)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, documentId)
        val out = mutableListOf<Triple<Uri, String, Boolean>>()
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )?.use { c ->
            val idI = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameI = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeI = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (c.moveToNext()) {
                val id = c.getString(idI)
                val uri = DocumentsContract.buildDocumentUriUsingTree(parent, id)
                out += Triple(uri, c.getString(nameI), c.getString(mimeI) == DocumentsContract.Document.MIME_TYPE_DIR)
            }
        }
        return out
    }

    data class Structure(
        val root: Uri,
        val config: Uri,
        val data: Uri,
        val archive: Uri
    )
}
