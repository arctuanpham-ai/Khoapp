package vn.ecohome.pos0210.data

import android.content.Context

object DatabaseHealth {
    fun validate(context: Context): Result<Unit> = runCatching {
        val db = PosDatabase.get(context).openHelper.writableDatabase

        db.query("PRAGMA integrity_check").use { cursor ->
            require(cursor.moveToFirst()) { "Không đọc được integrity_check" }
            require(cursor.getString(0).equals("ok", ignoreCase = true)) {
                "SQLite integrity_check thất bại: ${cursor.getString(0)}"
            }
        }

        val orphanChecks = listOf(
            "Payment không có Bill" to
                "SELECT COUNT(*) FROM PaymentEntity p LEFT JOIN BillEntity b ON b.id=p.billId WHERE b.id IS NULL",
            "Bill customer không tồn tại" to
                "SELECT COUNT(*) FROM BillEntity b LEFT JOIN CustomerEntity c ON c.id=b.customerId WHERE b.customerId IS NOT NULL AND c.id IS NULL",
            "Order item không có batch" to
                "SELECT COUNT(*) FROM OrderItemEntity oi LEFT JOIN OrderBatchEntity ob ON ob.id=oi.batchId WHERE ob.id IS NULL",
            "Order batch không có session" to
                "SELECT COUNT(*) FROM OrderBatchEntity ob LEFT JOIN TableSessionEntity s ON s.id=ob.sessionId WHERE s.id IS NULL",
            "Bill không có session" to
                "SELECT COUNT(*) FROM BillEntity b LEFT JOIN TableSessionEntity s ON s.id=b.sessionId WHERE s.id IS NULL",
            "Điểm customer không có customer" to
                "SELECT COUNT(*) FROM CustomerPointTransactionEntity p LEFT JOIN CustomerEntity c ON c.id=p.customerId WHERE c.id IS NULL",
            "Bill adjustment không có bill" to
                "SELECT COUNT(*) FROM BillAdjustmentEntity a LEFT JOIN BillEntity b ON b.id=a.billId WHERE b.id IS NULL",
            "Kitchen PrintJob không có batch" to
                "SELECT COUNT(*) FROM PrintJobEntity j LEFT JOIN OrderBatchEntity b ON b.id=j.batchId WHERE j.type='KITCHEN' AND j.batchId IS NOT NULL AND b.id IS NULL"
        )

        orphanChecks.forEach { (label, sql) ->
            db.query(sql).use { cursor ->
                require(cursor.moveToFirst()) { "Không kiểm tra được: $label" }
                val count = cursor.getLong(0)
                require(count == 0L) { "$label: $count bản ghi" }
            }
        }

        db.query("SELECT COUNT(*) FROM PaymentEntity p INNER JOIN BillEntity b ON b.id=p.billId WHERE p.amount!=b.total").use { cursor ->
            require(cursor.moveToFirst())
            val count = cursor.getLong(0)
            require(count == 0L) { "Payment amount lệch Bill total: $count bill" }
        }

        db.query("SELECT COUNT(*) FROM CustomerEntity WHERE points<0 OR totalSpend<0 OR visitCount<0").use { cursor ->
            require(cursor.moveToFirst())
            val count = cursor.getLong(0)
            require(count == 0L) { "Customer có số liệu âm: $count hồ sơ" }
        }
    }
}
