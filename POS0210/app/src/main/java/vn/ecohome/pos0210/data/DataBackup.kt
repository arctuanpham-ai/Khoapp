package vn.ecohome.pos0210.data

import android.content.Context
import android.net.Uri
import java.io.File

object DataBackup {
    private const val DB_NAME = "pos0210.db"

    fun exportDatabase(context: Context, uri: Uri): Result<Unit> = runCatching {
        val db = PosDatabase.get(context)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
        val source = context.getDatabasePath(DB_NAME)
        require(source.exists()) { "Không tìm thấy database" }
        context.contentResolver.openOutputStream(uri, "w").use { out ->
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
