package vn.ecohome.pos0210.data

import androidx.room.withTransaction
import java.util.UUID

class PosRepository(private val db:PosDatabase){
    private val dao=db.dao()
    fun areas()=dao.areas(); fun tables()=dao.tables(); fun categories()=dao.categories(); fun menuItems()=dao.menuItems(); fun employees()=dao.employees(); fun openSessions()=dao.openSessions(); fun paidBills()=dao.paidBills(); fun purchases()=dao.purchases()
    suspend fun saveArea(v:AreaEntity)=dao.saveArea(v)
    suspend fun saveTable(v:DiningTableEntity)=dao.saveTable(v)
    suspend fun saveCategory(v:MenuCategoryEntity)=dao.saveCategory(v)
    suspend fun saveMenuItem(v:MenuItemEntity)=dao.saveMenuItem(v)
    suspend fun saveEmployee(v:EmployeeEntity)=dao.saveEmployee(v)
    suspend fun saveSupplier(v:SupplierEntity)=dao.saveSupplier(v)
    suspend fun savePurchaseCategory(v:PurchaseCategoryEntity)=dao.savePurchaseCategory(v)

    suspend fun openSession(tableId:String,employeeId:String):TableSessionEntity {
        val now=System.currentTimeMillis(); val s=TableSessionEntity(UUID.randomUUID().toString(),tableId,now,employeeId)
        db.withTransaction { dao.insertSession(s); dao.audit(AuditEventEntity(UUID.randomUUID().toString(),"SESSION",s.id,"OPEN",employeeId,null,now,tableId)) }
        return s
    }

    suspend fun createBatch(sessionId:String,sequence:Int,ordererId:String,items:List<OrderItemEntity>):OrderBatchEntity {
        val now=System.currentTimeMillis(); val b=OrderBatchEntity(UUID.randomUUID().toString(),sessionId,sequence,ordererId,now)
        val fixed=items.map{it.copy(id=if(it.id.isBlank()) UUID.randomUUID().toString() else it.id,batchId=b.id)}
        db.withTransaction { dao.insertBatch(b); dao.insertItems(fixed); dao.audit(AuditEventEntity(UUID.randomUUID().toString(),"BATCH",b.id,"CREATE",ordererId,null,now,"items=${fixed.size}")) }
        return b
    }

    suspend fun queueKitchenPrint(batch:OrderBatchEntity):PrintJobEntity {
        val job=PrintJobEntity(UUID.randomUUID().toString(),batch.id,null,"KITCHEN",createdAt=System.currentTimeMillis())
        dao.insertPrintJob(job); return job
    }

    suspend fun claimPrint(jobId:String,deviceId:String)=dao.claimPrint(jobId,"PENDING","CLAIMED",deviceId)==1

    suspend fun closeAndPay(session:TableSessionEntity,subtotal:Long,total:Long,method:String,cashierId:String,billNo:String):BillEntity {
        val now=System.currentTimeMillis(); val bill=BillEntity(UUID.randomUUID().toString(),session.id,billNo,session.openedAt,now,subtotal,total,"PAID")
        db.withTransaction {
            if(dao.closeSession(session.id,session.version)!=1) error("SESSION_ALREADY_CLOSED_OR_CHANGED")
            dao.insertBill(bill)
            dao.insertPayment(PaymentEntity(UUID.randomUUID().toString(),bill.id,method,total,cashierId,now))
            dao.audit(AuditEventEntity(UUID.randomUUID().toString(),"BILL",bill.id,"PAID",cashierId,null,now,"method=$method,total=$total"))
        }
        return bill
    }

    suspend fun closeCancelledSession(session:TableSessionEntity,actorId:String) {
        val now=System.currentTimeMillis()
        db.withTransaction {
            if(dao.closeSession(session.id,session.version)!=1) error("SESSION_ALREADY_CLOSED_OR_CHANGED")
            dao.audit(
                AuditEventEntity(
                    UUID.randomUUID().toString(),
                    "SESSION",
                    session.id,
                    "CLOSED_CANCELLED",
                    actorId,
                    null,
                    now,
                    "ALL_ORDERS_CANCELLED"
                )
            )
        }
    }

    suspend fun savePurchase(p:PurchaseEntity,items:List<PurchaseItemEntity>){ db.withTransaction{dao.insertPurchase(p);dao.insertPurchaseItems(items)} }
}
