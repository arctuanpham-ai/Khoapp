package vn.ecohome.pos0210.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataBackup {
    private const val DB_NAME = "pos0210.db"
    private const val LATEST_NAME = "POS0210_DATA_LATEST.db"
    private const val TEMP_NAME = "POS0210_DATA_TEMP.db"

    private fun checkpoint(context: Context): File {
        val db = PosDatabase.get(context)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
        val source = context.getDatabasePath(DB_NAME)
        require(source.exists()) { "Không tìm thấy database" }
        return source
    }

    private fun validateSqlite(file: File) {
        val header = ByteArray(16)
        file.inputStream().use { input -> require(input.read(header) == 16) }
        require(String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) {
            "File DATA không hợp lệ"
        }
    }

    private fun validateSqlite(context: Context, uri: Uri) {
        val temp = File(context.cacheDir, "pos0210-validate.tmp")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        validateSqlite(temp)
        temp.delete()
    }

    fun ensureFolders(context: Context): Result<Unit> = PosStorage.ensureFolders(context)

    fun findLatest(context: Context): Uri? = PosStorage.find(context, PosStorage.DATA_PATH, LATEST_NAME)

    fun exportDatabase(context: Context, uri: Uri): Result<Unit> = runCatching {
        val source = checkpoint(context)
        context.contentResolver.openOutputStream(uri, "w").use { out ->
            requireNotNull(out)
            source.inputStream().use { input -> input.copyTo(out) }
        }
    }

    fun backupLatest(context: Context): Result<Uri> = runCatching {
        PosStorage.ensureFolders(context).getOrThrow()
        PosStorage.delete(context, PosStorage.find(context, PosStorage.DATA_PATH, TEMP_NAME))
        val tempUri = PosStorage.create(context, PosStorage.DATA_PATH, TEMP_NAME)
        val source = checkpoint(context)
        context.contentResolver.openOutputStream(tempUri, "w").use { out ->
            requireNotNull(out)
            source.inputStream().use { input -> input.copyTo(out) }
        }
        validateSqlite(context, tempUri)
        PosStorage.delete(context, PosStorage.find(context, PosStorage.DATA_PATH, LATEST_NAME))
        PosStorage.rename(context, tempUri, LATEST_NAME)
    }

    fun archiveSnapshot(context: Context): Result<Uri> = runCatching {
        PosStorage.ensureFolders(context).getOrThrow()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val name = "POS0210_DATA_$stamp.db"
        val target = PosStorage.create(context, PosStorage.ARCHIVE_PATH, name)
        exportDatabase(context, target).getOrThrow()
        validateSqlite(context, target)
        target
    }

    fun restoreLatest(context: Context): Result<Unit> = runCatching {
        val uri = findLatest(context) ?: error("Không tìm thấy POS0210_DATA_LATEST.db")
        restoreDatabase(context, uri).getOrThrow()
    }

    fun autoBackup(context: Context, ignored: String = ""): Result<Unit> =
        backupLatest(context).map { Unit }

    fun restoreDatabase(context: Context, uri: Uri): Result<Unit> = runCatching {
        val temp = File(context.cacheDir, "pos0210-restore.tmp")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        validateSqlite(temp)

        PosDatabase.closeForRestore()
        val target = context.getDatabasePath(DB_NAME)
        target.parentFile?.mkdirs()
        temp.copyTo(target, overwrite = true)
        File(target.path + "-wal").delete()
        File(target.path + "-shm").delete()
        temp.delete()
    }
}
