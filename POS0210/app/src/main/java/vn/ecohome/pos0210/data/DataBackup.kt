package vn.ecohome.pos0210.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

object DataBackup {
    private const val DB_NAME = "pos0210.db"
    private const val AUTO_NAME = "POS0210_autoback_latest.db"

    private fun checkpoint(context: Context): File {
        val db = PosDatabase.get(context)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
        val source = context.getDatabasePath(DB_NAME)
        require(source.exists()) { "Không tìm thấy database" }
        return source
    }

    fun exportDatabase(context: Context, uri: Uri): Result<Unit> = runCatching {
        val source = checkpoint(context)
        context.contentResolver.openOutputStream(uri, "w").use { out ->
            requireNotNull(out)
            source.inputStream().use { input -> input.copyTo(out) }
        }
    }

    fun autoBackup(context: Context, treeUriString: String): Result<Unit> = runCatching {
        require(treeUriString.isNotBlank()) { "Chưa chọn thư mục Autobackup" }
        val resolver = context.contentResolver
        val treeUri = Uri.parse(treeUriString)
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)

        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == AUTO_NAME) {
                    val oldUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex))
                    runCatching { DocumentsContract.deleteDocument(resolver, oldUri) }
                    break
                }
            }
        }

        val target = DocumentsContract.createDocument(
            resolver,
            parent,
            "application/octet-stream",
            AUTO_NAME
        ) ?: error("Không tạo được file Autobackup")

        val source = checkpoint(context)
        resolver.openOutputStream(target, "w").use { out ->
            requireNotNull(out)
            source.inputStream().use { input -> input.copyTo(out) }
        }
    }

    fun restoreDatabase(context: Context, uri: Uri): Result<Unit> = runCatching {
        val temp = File(context.cacheDir, "pos0210-restore.tmp")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        val header = ByteArray(16)
        temp.inputStream().use { input -> require(input.read(header) == 16) }
        require(String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) {
            "File backup không hợp lệ"
        }

        PosDatabase.closeForRestore()
        val target = context.getDatabasePath(DB_NAME)
        target.parentFile?.mkdirs()
        temp.copyTo(target, overwrite = true)
        File(target.path + "-wal").delete()
        File(target.path + "-shm").delete()
        temp.delete()
    }
}
