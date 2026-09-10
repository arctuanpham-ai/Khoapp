from pathlib import Path

root=Path('POS0210/app/src/main/java/vn/ecohome/pos0210')

# DAO: authoritative totals, unfulfilled state and transactional sequence allocation.
p=root/'data/PosDao.kt'
s=p.read_text()
needle='@Query("SELECT COUNT(*) FROM OrderBatchEntity WHERE sessionId=:sessionId AND status=\'WAITING\'") suspend fun waitingCountForSession(sessionId:String):Int\n'
insert=needle + '@Query("SELECT COUNT(*) FROM OrderBatchEntity WHERE sessionId=:sessionId AND status IN (\'DRAFT\',\'WAITING\')") suspend fun unfulfilledCountForSession(sessionId:String):Int\n@Query("SELECT COALESCE(MAX(sequence),0) FROM OrderBatchEntity WHERE sessionId=:sessionId") suspend fun maxBatchSequence(sessionId:String):Int\n'
assert needle in s
s=s.replace(needle,insert,1)
needle='@Query("SELECT COALESCE(SUM(qty*unitPriceSnapshot),0) FROM OrderItemEntity WHERE batchId IN (SELECT id FROM OrderBatchEntity WHERE sessionId=:sessionId AND status!=\'CANCELLED\')") fun sessionTotal(sessionId:String):Flow<Long>\n'
insert=needle + '@Query("SELECT COALESCE(SUM(qty*unitPriceSnapshot),0) FROM OrderItemEntity WHERE batchId IN (SELECT id FROM OrderBatchEntity WHERE sessionId=:sessionId AND status!=\'CANCELLED\')") suspend fun sessionTotalSnapshot(sessionId:String):Long\n'
assert needle in s
s=s.replace(needle,insert,1)
p.write_text(s)

# Repository: sequence allocation + kitchen finalize + payment source-of-truth checks.
p=root/'data/PosRepository.kt'
s=p.read_text()
old='''            val serviceNo=dao.maxServiceNoSince(cal.timeInMillis)+1
            b=OrderBatchEntity(UUID.randomUUID().toString(),sessionId,sequence,ordererId,now,serviceNo=serviceNo)'''
new='''            val serviceNo=dao.maxServiceNoSince(cal.timeInMillis)+1
            val nextSequence=dao.maxBatchSequence(sessionId)+1
            b=OrderBatchEntity(UUID.randomUUID().toString(),sessionId,nextSequence,ordererId,now,serviceNo=serviceNo)'''
assert old in s
s=s.replace(old,new,1)
old='''            if(dao.markPrintSuccess(jobId,printedAt)!=1) return@withTransaction false
            dao.transitionBatch(batchId,"DRAFT","WAITING",printedAt)
            true'''
new='''            if(dao.markPrintSuccess(jobId,printedAt)!=1) return@withTransaction false
            check(dao.transitionBatch(batchId,"DRAFT","WAITING",printedAt)==1) { "BATCH_STATE_CHANGED_DURING_PRINT" }
            true'''
assert old in s
s=s.replace(old,new,1)
old='''        return db.withTransaction {
            if(dao.waitingCountForSession(session.id)>0) error("PENDING_DELIVERY_NOT_CONFIRMED")'''
new='''        return db.withTransaction {
            if(dao.unfulfilledCountForSession(session.id)>0) error("PENDING_ORDER_NOT_COMPLETED")
            val liveSubtotal=dao.sessionTotalSnapshot(session.id)
            if(liveSubtotal!=preview.subtotal) error("ORDER_TOTAL_CHANGED")'''
assert old in s
s=s.replace(old,new,1)
p.write_text(s)

# ViewModel: precise payment errors; clear notes when qty reaches zero.
p=root/'PosViewModel.kt'
s=p.read_text()
s=s.replace('fun sub(i:MenuItemEntity){cart.value=cart.value.toMutableMap().apply{val q=get(i.id)?:0;if(q<=1)remove(i.id)else put(i.id,q-1)}}','fun sub(i:MenuItemEntity){cart.value=cart.value.toMutableMap().apply{val q=get(i.id)?:0;if(q<=1){remove(i.id);setCartNote(i.id,"")}else put(i.id,q-1)}}')
s=s.replace('fun subCombo(i:ComboEntity){val k="combo:"+i.id;cart.value=cart.value.toMutableMap().apply{val q=get(k)?:0;if(q<=1)remove(k)else put(k,q-1)}}','fun subCombo(i:ComboEntity){val k="combo:"+i.id;cart.value=cart.value.toMutableMap().apply{val q=get(k)?:0;if(q<=1){remove(k);setCartNote(k,"")}else put(k,q-1)}}')
old='''     "SESSION_ALREADY_CLOSED_OR_CHANGED" -> "BILL ĐÃ ĐƯỢC THANH TOÁN / BÀN ĐÃ ĐÓNG · Không ghi bill lần 2"
     "PENDING_DELIVERY_NOT_CONFIRMED" -> "CHƯA XÁC NHẬN GIAO ĐỦ · Không thể thanh toán"
     else -> "THANH TOÁN LỖI · ${err.message ?: "UNKNOWN"}"'''
new='''     "SESSION_ALREADY_CLOSED_OR_CHANGED" -> "BILL ĐÃ ĐƯỢC THANH TOÁN / BÀN ĐÃ ĐÓNG · Không ghi bill lần 2"
     "PENDING_ORDER_NOT_COMPLETED" -> "CÒN ĐƠN CHƯA HOÀN TẤT · Gửi bếp và xác nhận giao đủ trước khi thanh toán"
     "ORDER_TOTAL_CHANGED" -> "ĐƠN VỪA THAY ĐỔI · Quay lại kiểm tra món trước khi thanh toán"
     else -> "THANH TOÁN LỖI · ${err.message ?: "UNKNOWN"}"'''
assert old in s
s=s.replace(old,new,1)
p.write_text(s)

# Pay UI: include DRAFT as well as WAITING and block checkout visibly.
p=root/'MainActivity.kt'
s=p.read_text()
old='''    val waitingAll by vm.waitingBatches.collectAsState()
    val pendingDelivery = waitingAll.filter { it.sessionId == s.id }.sortedBy { it.serviceNo }'''
new='''    val sessionBatches by vm.batches(s.id).collectAsState(initial = emptyList())
    val pendingDelivery = sessionBatches.filter { it.status == "DRAFT" || it.status == "WAITING" }.sortedBy { it.serviceNo }'''
assert old in s
s=s.replace(old,new,1)
old='''                        Text("CHƯA XÁC NHẬN GIAO ĐỦ", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            "Không thể thanh toán cho đến khi xác nhận đã giao đủ đồ cho khách.",'''
new='''                        Text("CÒN ĐƠN CHƯA HOÀN TẤT", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            "Không thể thanh toán khi còn đơn chưa gửi bếp hoặc chưa xác nhận giao đủ.",'''
assert old in s
s=s.replace(old,new,1)
old='''                        Text(
                            pendingDelivery.joinToString(" · ") { "#${it.serviceNo.toString().padStart(3,'0')}" },'''
new='''                        Text(
                            pendingDelivery.joinToString(" · ") { batch ->
                                "#${batch.serviceNo.toString().padStart(3,'0')} ${if(batch.status=="DRAFT") "CHƯA GỬI" else "CHỜ GIAO"}"
                            },'''
assert old in s
s=s.replace(old,new,1)
# Confirm-all button should only be actionable for WAITING batches; DRAFT requires returning to Sent screen.
old='''                        Button(
                            onClick = { vm.confirmAllDelivered(s.id) },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        ) { Text("✓ XÁC NHẬN ĐÃ GIAO ĐỦ") }'''
new='''                        if (pendingDelivery.none { it.status == "DRAFT" }) {
                            Button(
                                onClick = { vm.confirmAllDelivered(s.id) },
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            ) { Text("✓ XÁC NHẬN ĐÃ GIAO ĐỦ") }
                        } else {
                            OutlinedButton(
                                onClick = { vm.screen.value = "SENT" },
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            ) { Text("QUAY LẠI GỬI BẾP") }
                        }'''
assert old in s
s=s.replace(old,new,1)
p.write_text(s)

# Operational health: catch duplicate payments, drifted subtotal, duplicate sequence, bad closed-session state and purchase totals.
p=root/'data/DatabaseHealth.kt'
s=p.read_text()
anchor='''        count(
            "Bill PAID có Payment method không hợp lệ",
            "SELECT COUNT(*) FROM PaymentEntity p INNER JOIN BillEntity b ON b.id=p.billId WHERE b.status='PAID' AND p.method NOT IN ('CASH','TRANSFER')"
        )
'''
extra=anchor+'''        count(
            "Bill PAID có nhiều hơn 1 Payment",
            "SELECT COUNT(*) FROM (SELECT b.id,COUNT(p.id) c FROM BillEntity b INNER JOIN PaymentEntity p ON p.billId=b.id WHERE b.status='PAID' GROUP BY b.id HAVING c>1)"
        )
        count(
            "Bill lệch subtotal so với món",
            "SELECT COUNT(*) FROM BillEntity b WHERE b.status='PAID' AND b.subtotal != COALESCE((SELECT SUM(oi.qty*oi.unitPriceSnapshot) FROM OrderBatchEntity ob INNER JOIN OrderItemEntity oi ON oi.batchId=ob.id WHERE ob.sessionId=b.sessionId AND ob.status!='CANCELLED'),0)"
        )
        count(
            "Session CLOSED còn batch DRAFT",
            "SELECT COUNT(*) FROM OrderBatchEntity ob INNER JOIN TableSessionEntity s ON s.id=ob.sessionId WHERE ob.status='DRAFT' AND s.status='CLOSED'"
        )
        count(
            "Trùng số thứ tự đơn trong cùng session",
            "SELECT COUNT(*) FROM (SELECT sessionId,sequence,COUNT(*) c FROM OrderBatchEntity GROUP BY sessionId,sequence HAVING c>1)"
        )
        count(
            "Kitchen PRINTED nhưng batch vẫn DRAFT",
            "SELECT COUNT(*) FROM PrintJobEntity j INNER JOIN OrderBatchEntity b ON b.id=j.batchId WHERE j.type='KITCHEN' AND j.status='PRINTED' AND b.status='DRAFT'"
        )
        count(
            "Phiếu nhập lệch tổng chi tiết",
            "SELECT COUNT(*) FROM PurchaseEntity p WHERE p.status='ACTIVE' AND p.total != COALESCE((SELECT SUM(pi.amount) FROM PurchaseItemEntity pi WHERE pi.purchaseId=p.id),0)"
        )
'''
assert anchor in s
s=s.replace(anchor,extra,1)
p.write_text(s)

# Backup restore: archive DB must restore media from the exact same timestamp, never silently use latest media.
p=root/'data/DataBackup.kt'
s=p.read_text()
if 'import android.provider.OpenableColumns' not in s:
    s=s.replace('import android.net.Uri\n','import android.net.Uri\nimport android.provider.OpenableColumns\n')
anchor='''    fun restoreMediaLatest(context: Context, rootTreeUriString: String): Result<Unit> = runCatching {
        val uri = findMediaLatest(context, rootTreeUriString) ?: return@runCatching
        val dir = File(context.filesDir, "managed_media").apply { mkdirs() }

        context.contentResolver.openInputStream(uri).use { raw ->
            requireNotNull(raw)
            ZipInputStream(raw).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val safeName = entry.name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
                    if (safeName.isNotBlank()) {
                        File(dir, safeName).outputStream().use { output -> zip.copyTo(output) }
                    }
                    zip.closeEntry()
                }
            }
        }
    }
'''
replacement='''    private fun restoreMediaFromUri(context: Context, uri: Uri) {
        val dir = File(context.filesDir, "managed_media").apply { mkdirs() }
        context.contentResolver.openInputStream(uri).use { raw ->
            requireNotNull(raw)
            ZipInputStream(raw).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val safeName = entry.name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
                    if (safeName.isNotBlank()) {
                        File(dir, safeName).outputStream().use { output -> zip.copyTo(output) }
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    fun restoreMediaLatest(context: Context, rootTreeUriString: String): Result<Unit> = runCatching {
        val uri = findMediaLatest(context, rootTreeUriString) ?: return@runCatching
        restoreMediaFromUri(context, uri)
    }
'''
assert anchor in s
s=s.replace(anchor,replacement,1)
old='''        restoreDatabase(context, uri).getOrThrow()
        restoreMediaLatest(context, rootTreeUriString).getOrThrow()
        val master = ConfigBackup.findMaster(context, rootTreeUriString)'''
new='''        restoreDatabase(context, uri).getOrThrow()
        val dbName=displayName(context,uri).orEmpty()
        val archiveStamp=Regex("POS0210_DATA_(\\d{8}_\\d{6})\\.db").matchEntire(dbName)?.groupValues?.get(1)
        if(archiveStamp!=null){
            val structure=SafPosStorage.ensureSelectedRoot(context,rootTreeUriString).getOrThrow()
            val media=SafPosStorage.findFile(context,structure.archive,"POS0210_MEDIA_${archiveStamp}.0210")
                ?: error("Thiếu MEDIA archive cùng mốc $archiveStamp; không dùng MEDIA_LATEST để tránh ghép sai dữ liệu")
            validateMediaArchive(context,media)
            restoreMediaFromUri(context,media)
        }else{
            restoreMediaLatest(context, rootTreeUriString).getOrThrow()
        }
        val master = ConfigBackup.findMaster(context, rootTreeUriString)'''
# Only replace in restoreDatabaseAndApplyMaster: use rfind by target block.
pos=s.rfind(old)
assert pos>=0
s=s[:pos]+s[pos:].replace(old,new,1)
p.write_text(s)

print('final hardening patch applied')
