package vn.ecohome.pos0210.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao interface PosDao {
 @Query("SELECT * FROM AreaEntity WHERE active=1 ORDER BY sortOrder,name") fun areas():Flow<List<AreaEntity>>
 @Query("SELECT * FROM DiningTableEntity WHERE active=1 ORDER BY sortOrder,name") fun tables():Flow<List<DiningTableEntity>>
 @Query("SELECT * FROM MenuCategoryEntity WHERE active=1 ORDER BY sortOrder,name") fun categories():Flow<List<MenuCategoryEntity>>
 @Query("SELECT * FROM MenuItemEntity WHERE active=1 ORDER BY sortOrder,name") fun menuItems():Flow<List<MenuItemEntity>>
 @Query("SELECT * FROM EmployeeEntity WHERE active=1 ORDER BY name") fun employees():Flow<List<EmployeeEntity>>
 @Query("SELECT * FROM TableSessionEntity WHERE status='OPEN'") fun openSessions():Flow<List<TableSessionEntity>>
 @Query("SELECT * FROM OrderBatchEntity WHERE sessionId=:sessionId ORDER BY sequence") fun batches(sessionId:String):Flow<List<OrderBatchEntity>>
 @Query("SELECT * FROM OrderItemEntity WHERE batchId=:batchId") fun batchItems(batchId:String):Flow<List<OrderItemEntity>>
 @Query("SELECT COALESCE(SUM(i.unitPriceSnapshot*i.qty),0) FROM OrderItemEntity i INNER JOIN OrderBatchEntity b ON b.id=i.batchId WHERE b.sessionId=:sessionId") fun sessionTotal(sessionId:String):Flow<Long>
 @Query("SELECT * FROM BillEntity WHERE status='PAID' ORDER BY closedAt DESC") fun paidBills():Flow<List<BillEntity>>
 @Query("SELECT * FROM PurchaseEntity ORDER BY purchasedAt DESC") fun purchases():Flow<List<PurchaseEntity>>
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
 @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertPurchase(v:PurchaseEntity)
 @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertPurchaseItems(v:List<PurchaseItemEntity>)
 @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun audit(v:AuditEventEntity)
 @Query("UPDATE OrderBatchEntity SET status=:newStatus, sentAt=:sentAt WHERE id=:id AND status=:expected") suspend fun transitionBatch(id:String,expected:String,newStatus:String,sentAt:Long?):Int
 @Query("UPDATE TableSessionEntity SET status='CLOSED', version=version+1 WHERE id=:id AND status='OPEN' AND version=:version") suspend fun closeSession(id:String,version:Long):Int
 @Query("UPDATE PrintJobEntity SET status=:newStatus, claimedByDeviceId=:deviceId, attempts=attempts+1 WHERE id=:id AND status=:expected") suspend fun claimPrint(id:String,expected:String,newStatus:String,deviceId:String):Int
}