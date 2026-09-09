package vn.ecohome.pos0210.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao interface PosDao{
@Query("SELECT * FROM AreaEntity WHERE active=1 ORDER BY sortOrder,name") fun areas():Flow<List<AreaEntity>>
@Query("SELECT * FROM DiningTableEntity WHERE active=1 ORDER BY sortOrder,name") fun tables():Flow<List<DiningTableEntity>>
@Query("SELECT * FROM MenuCategoryEntity WHERE active=1 ORDER BY sortOrder,name") fun categories():Flow<List<MenuCategoryEntity>>
@Query("SELECT * FROM MenuItemEntity ORDER BY sortOrder,name") fun menuItems():Flow<List<MenuItemEntity>>
@Query("SELECT * FROM EmployeeEntity ORDER BY name") fun employees():Flow<List<EmployeeEntity>>
@Query("SELECT * FROM SupplierEntity ORDER BY name") fun suppliers():Flow<List<SupplierEntity>>
@Query("SELECT * FROM TableSessionEntity WHERE status='OPEN'") fun openSessions():Flow<List<TableSessionEntity>>
@Query("SELECT * FROM TableSessionEntity WHERE id=:id LIMIT 1") fun sessionById(id:String):Flow<TableSessionEntity?>
@Query("SELECT * FROM TableSessionEntity WHERE tableId=:tableId AND status='OPEN' LIMIT 1") suspend fun openSessionForTable(tableId:String):TableSessionEntity?
@Query("SELECT * FROM OrderBatchEntity WHERE sessionId=:sessionId ORDER BY sequence") fun batches(sessionId:String):Flow<List<OrderBatchEntity>>
@Query("SELECT * FROM OrderItemEntity WHERE batchId=:batchId") fun batchItems(batchId:String):Flow<List<OrderItemEntity>>
@Query("SELECT COALESCE(SUM(qty*unitPriceSnapshot),0) FROM OrderItemEntity WHERE batchId IN (SELECT id FROM OrderBatchEntity WHERE sessionId=:sessionId AND status!='CANCELLED')") fun sessionTotal(sessionId:String):Flow<Long>
@Query("SELECT * FROM BillEntity WHERE status='PAID' ORDER BY closedAt DESC") fun paidBills():Flow<List<BillEntity>>
@Query("SELECT * FROM PaymentEntity ORDER BY paidAt DESC") fun payments():Flow<List<PaymentEntity>>
@Query("SELECT oi.itemNameSnapshot AS name, oi.qty AS qty, ob.sessionId AS sessionId FROM OrderItemEntity oi INNER JOIN OrderBatchEntity ob ON ob.id=oi.batchId INNER JOIN BillEntity b ON b.sessionId=ob.sessionId WHERE b.status='PAID' AND ob.status!='CANCELLED'") fun paidItemSales():Flow<List<ItemSaleRow>>
@Query("SELECT * FROM PurchaseEntity ORDER BY purchasedAt DESC") fun purchases():Flow<List<PurchaseEntity>>
@Query("SELECT * FROM PurchaseItemEntity WHERE purchaseId=:purchaseId") fun purchaseItems(purchaseId:String):Flow<List<PurchaseItemEntity>>
@Query("SELECT * FROM PrintJobEntity ORDER BY createdAt DESC") fun printJobs():Flow<List<PrintJobEntity>>
@Query("SELECT * FROM AuditEventEntity ORDER BY occurredAt DESC LIMIT 100") fun audits():Flow<List<AuditEventEntity>>
@Query("SELECT * FROM AppSettingEntity") fun settings():Flow<List<AppSettingEntity>>
@Query("SELECT * FROM AreaEntity ORDER BY sortOrder,name") suspend fun allAreasSnapshot():List<AreaEntity>
@Query("SELECT * FROM DiningTableEntity ORDER BY sortOrder,name") suspend fun allTablesSnapshot():List<DiningTableEntity>
@Query("SELECT * FROM MenuCategoryEntity ORDER BY sortOrder,name") suspend fun allCategoriesSnapshot():List<MenuCategoryEntity>
@Query("SELECT * FROM MenuItemEntity ORDER BY sortOrder,name") suspend fun allMenuSnapshot():List<MenuItemEntity>
@Query("SELECT * FROM EmployeeEntity ORDER BY name") suspend fun allEmployeesSnapshot():List<EmployeeEntity>
@Query("SELECT * FROM AppSettingEntity") suspend fun allSettingsSnapshot():List<AppSettingEntity>
@Query("SELECT * FROM EmployeeEntity WHERE pin=:pin AND active=1 LIMIT 1") suspend fun employeeByPin(pin:String):EmployeeEntity?
@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertSession(v:TableSessionEntity)
@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertBatch(v:OrderBatchEntity)
@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertItems(v:List<OrderItemEntity>)
@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertBill(v:BillEntity)
@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertPayment(v:PaymentEntity)
@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertPrintJob(v:PrintJobEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveArea(v:AreaEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveTable(v:DiningTableEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveCategory(v:MenuCategoryEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveMenuItem(v:MenuItemEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveEmployee(v:EmployeeEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveSupplier(v:SupplierEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveSetting(v:AppSettingEntity)
@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertPurchase(v:PurchaseEntity)
@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertPurchaseItems(v:List<PurchaseItemEntity>)
@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun audit(v:AuditEventEntity)
@Query("UPDATE MenuItemEntity SET active=:active WHERE id=:id") suspend fun setMenuActive(id:String,active:Boolean)
@Query("UPDATE MenuCategoryEntity SET active=:active WHERE id=:id") suspend fun setCategoryActive(id:String,active:Boolean)
@Query("UPDATE EmployeeEntity SET active=:active WHERE id=:id") suspend fun setEmployeeActive(id:String,active:Boolean)
@Query("UPDATE OrderBatchEntity SET status=:newStatus,sentAt=:sentAt WHERE id=:id AND status=:expected") suspend fun transitionBatch(id:String,expected:String,newStatus:String,sentAt:Long?):Int
@Query("UPDATE OrderBatchEntity SET status='CANCELLED' WHERE id=:id AND status IN ('DRAFT','SENT')") suspend fun cancelBatch(id:String):Int
@Query("UPDATE TableSessionEntity SET status='CLOSED',version=version+1 WHERE id=:id AND status='OPEN' AND version=:version") suspend fun closeSession(id:String,version:Long):Int
@Query("UPDATE PrintJobEntity SET status=:newStatus,claimedByDeviceId=:deviceId,attempts=attempts+1 WHERE id=:id AND status=:expected") suspend fun claimPrint(id:String,expected:String,newStatus:String,deviceId:String):Int
}