package vn.ecohome.pos0210.cloud

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import vn.ecohome.pos0210.data.PosDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class CloudBackupInfo(val createdAt:Long,val bytes:Int,val chunks:Int)

/** A private, latest-only Room backup. Media stays local/SAF and is deliberately excluded. */
object FirestorePrivateBackup {
    private const val STORE_ID="0210"
    private const val CHUNK_SIZE=480_000
    private const val MAX_CHUNKS=200

    private fun root(fs:FirebaseFirestore,uid:String)=fs.collection("users").document(uid).collection("privateBackups").document(STORE_ID)
    private suspend fun <T> stage(name:String,block:suspend()->T):T =
        try{block()}catch(e:Throwable){throw IllegalStateException("$name: ${e.message}",e)}

    suspend fun upload(context:Context,fs:FirebaseFirestore,uid:String,now:Long):CloudBackupInfo {
        val staged=makeScrubbedCopy(context)
        try {
            val zipped=gzip(staged.readBytes())
            val encoded=Base64.encodeToString(zipped,Base64.NO_WRAP)
            val chunks=encoded.chunked(CHUNK_SIZE)
            require(chunks.size<=MAX_CHUNKS){"Cloud backup quá lớn; hãy lưu archive SAF và liên hệ hỗ trợ"}
            val privateRoot=root(fs,uid);val latest=privateRoot.collection("meta").document("latest");val old=stage("BACKUP_READ_META"){latest.get().await()}.getString("generation")
            val generation=now.toString();val chunkRoot=privateRoot.collection("generations").document(generation).collection("chunks")
            chunks.chunked(350).forEachIndexed { groupIndex,group ->
                val batch=fs.batch();group.forEachIndexed { offset,data ->
                    val index=groupIndex*350+offset
                    batch.set(chunkRoot.document(index.toString().padStart(4,'0')),mapOf("index" to index,"data" to data))
                };stage("BACKUP_WRITE_CHUNKS"){batch.commit().await()}
            }
            stage("BACKUP_WRITE_META"){latest.set(mapOf("generation" to generation,"createdAt" to now,"bytes" to zipped.size,"chunks" to chunks.size,"sha256" to sha256(zipped),"format" to "POS0210_ROOM_GZIP_V1")).await()}
            if(!old.isNullOrBlank()&&old!=generation) stage("BACKUP_DELETE_OLD"){deleteGeneration(fs,privateRoot,old)}
            return CloudBackupInfo(now,zipped.size,chunks.size)
        } finally { staged.delete() }
    }

    suspend fun latestInfo(context:Context):CloudBackupInfo? {
        val config=FirebaseCloudSync.config(context);require(config.valid){"Chưa cấu hình Firebase"}
        val app=FirebaseCloudSync.firebaseApp(context,config);val uid=com.google.firebase.auth.FirebaseAuth.getInstance(app).currentUser?.uid?:error("Chưa đăng nhập Firebase")
        val doc=root(FirebaseFirestore.getInstance(app),uid).collection("meta").document("latest").get().await()
        if(!doc.exists())return null
        return CloudBackupInfo(doc.getLong("createdAt")?:0,doc.getLong("bytes")?.toInt()?:0,doc.getLong("chunks")?.toInt()?:0)
    }

    suspend fun restoreLatest(context:Context):CloudBackupInfo {
        val config=FirebaseCloudSync.config(context);require(config.valid){"Chưa cấu hình Firebase"}
        val app=FirebaseCloudSync.firebaseApp(context,config);val uid=com.google.firebase.auth.FirebaseAuth.getInstance(app).currentUser?.uid?:error("Chưa đăng nhập Firebase")
        val fs=FirebaseFirestore.getInstance(app);val privateRoot=root(fs,uid);val manifest=privateRoot.collection("meta").document("latest").get().await()
        require(manifest.exists()){"Chưa có cloud backup để khôi phục"};require(manifest.getString("format")=="POS0210_ROOM_GZIP_V1"){"Cloud backup không tương thích"}
        val count=manifest.getLong("chunks")?.toInt()?:0;require(count in 1..MAX_CHUNKS){"Cloud backup lỗi số mảnh"}
        val generation=manifest.getString("generation")?:error("Cloud backup thiếu mã phiên")
        val parts=privateRoot.collection("generations").document(generation).collection("chunks").orderBy("index").get().await().documents
        require(parts.size==count){"Cloud backup thiếu dữ liệu"}
        val compressed=Base64.decode(parts.joinToString(""){it.getString("data").orEmpty()},Base64.NO_WRAP)
        require(sha256(compressed)==manifest.getString("sha256")){"Cloud backup không toàn vẹn"}
        val restored=gunzip(compressed);val staged=File(context.cacheDir,"pos0210-cloud-restore.db")
        staged.outputStream().use{it.write(restored)}
        try {
            SQLiteDatabase.openDatabase(staged.absolutePath,null,SQLiteDatabase.OPEN_READONLY).close()
            replaceDatabase(context,staged)
        } finally { staged.delete() }
        return CloudBackupInfo(manifest.getLong("createdAt")?:0,compressed.size,count)
    }

    private fun makeScrubbedCopy(context:Context):File {
        val room=PosDatabase.get(context);room.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        val source=context.getDatabasePath("pos0210.db");require(source.exists()){"Không tìm thấy dữ liệu POS"}
        val copy=File(context.cacheDir,"pos0210-cloud-upload.db");source.copyTo(copy,true)
        SQLiteDatabase.openDatabase(copy.absolutePath,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("DELETE FROM BankNotificationEventEntity")
            db.execSQL("DELETE FROM PrintJobEntity")
            db.execSQL("DELETE FROM AuditEventEntity")
            db.execSQL("DELETE FROM AppSettingEntity WHERE key IN ('autoback_tree_uri','master_config_uri','storage_root_uri','storage_write_enabled')")
            db.execSQL("UPDATE MenuItemEntity SET imageUri=NULL")
            db.execSQL("UPDATE ComboEntity SET imageUri=NULL")
            db.execSQL("UPDATE PurchaseEntity SET invoiceImageUri=NULL")
        }
        return copy
    }

    private suspend fun deleteGeneration(fs:FirebaseFirestore,root:com.google.firebase.firestore.DocumentReference,generation:String){
        val chunks=root.collection("generations").document(generation).collection("chunks").get().await().documents
        chunks.chunked(400).forEach { group -> val batch=fs.batch();group.forEach{batch.delete(it.reference)};batch.commit().await() }
        root.collection("generations").document(generation).delete().await()
    }
    private fun gzip(raw:ByteArray)=ByteArrayOutputStream().use { out -> GZIPOutputStream(out).use{it.write(raw)};out.toByteArray() }
    private fun gunzip(raw:ByteArray)=GZIPInputStream(ByteArrayInputStream(raw)).use{it.readBytes()}
    private fun sha256(raw:ByteArray)=MessageDigest.getInstance("SHA-256").digest(raw).joinToString(""){"%02x".format(it)}
    private fun replaceDatabase(context:Context,staged:File){
        val target=context.getDatabasePath("pos0210.db");val safety=File(target.parentFile,"pos0210-before-cloud-restore.db")
        PosDatabase.closeForRestore();if(target.exists())target.copyTo(safety,true)
        File(target.path+"-wal").delete();File(target.path+"-shm").delete();staged.copyTo(target,true)
    }
}
