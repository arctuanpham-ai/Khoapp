package vn.ecohome.pos0210.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao interface PosDao{
@Query("SELECT * FROM AreaEntity WHERE active=1 ORDER BY sortOrder,name") fun areas():Flow<List<AreaEntity>>
@Query("SELECT * FROM DiningTableEntity WHERE active=1 ORDER BY sortOrder,name") fun tables():Flow<List<DiningTableEntity>>
@Query("SELECT * FROM MenuCategoryEntity WHERE active=1 ORDER BY sortOrder,name") fun categories():Flow<List<MenuCategoryEntity>>
@Query("SELECT * FROM MenuItemEntity ORDER BY sortOrder,name") fun menuItems():Flow<List<MenuItemEntity>>
@Query("SELECT * FROM ComboEntity ORDER BY sortOrder,name") fun combos():Flow<List<ComboEntity>>
@Query("SELECT * FROM ComboItemEntity WHERE comboId=:comboId") fun comboItems(comboId:String):Flow<List<ComboItemEntity>>
@Query("SELECT * FROM ComboItemEntity") suspend fun allComboItemsSnapshot():List<ComboItemEntity>
@Query("SELECT * FROM ComboEntity") suspend fun allCombosSnapshot():List<ComboEntity>
@Query("SELECT * FROM EmployeeEntity ORDER BY name") fun employees():Flow<List<EmployeeEntity>>
@Query("SELECT * FROM SupplierEntity ORDER BY name") fun suppliers():Flow<List<SupplierEntity>>
@Query("SELECT * FROM TableSessionEntity WHERE status='OPEN'") fun openSessions():Flow<List<TableSessionEntity>>
@Query("SELECT * FROM TableSessionEntity WHERE id=:id LIMIT 1") fun sessionById(id:String):Flow<TableSessionEntity?>
@Query("SELECT * FROM TableSessionEntity WHERE tableId=:tableId AND status='OPEN' LIMIT 1") suspend fun openSessionForTable(tableId:String):TableSessionEntity?
@Query("SELECT * FROM OrderBatchEntity WHERE sessionId=:sessionId ORDER BY sequence") fun batches(sessionId:String):Flow<List<OrderBatchEntity>>
@Query("SELECT * FROM OrderBatchEntity WHERE status='WAITING' ORDER BY serviceNo,createdAt") fun waitingBatches():Flow<List<OrderBatchEntity>>
@Query("SELECT COALESCE(MAX(serviceNo),0) FROM OrderBatchEntity WHERE createdAt>=:dayStart") suspend fun maxServiceNoSince(dayStart:Long):Int
@Query("SELECT * FROM OrderItemEntity WHERE batchId=:batchId") fun batchItems(batchId:String):Flow<List<OrderItemEntity>>
@Query("SELECT COALESCE(SUM(qty*unitPriceSnapshot),0) FROM OrderItemEntity WHERE batchId IN (SELECT id FROM OrderBatchEntity WHERE sessionId=:sessionId AND status!='CANCELLED')") fun sessionTotal(sessionId:String):Flow<Long>
@Query("SELECT * FROM BillEntity WHERE status='PAID' ORDER BY closedAt DESC") fun paidBills():Flow<List<BillEntity>>
@Query("SELECT * FROM PaymentEntity ORDER BY paidAt DESC") fun payments():Flow<List<PaymentEntity>>
@Query("SELECT * FROM CustomerEntity WHERE active=1 ORDER BY lastVisitAt DESC") fun customers():Flow<List<CustomerEntity>>
@Query("SELECT * FROM CustomerEntity WHERE phone=:phone AND active=1 LIMIT 1") suspend fun customerByPhone(phone:String):CustomerEntity?
@Query("SELECT * FROM CustomerEntity WHERE id=:id LIMIT 1") suspend fun customerById(id:String):CustomerEntity?
@Query("SELECT * FROM CustomerPointTransactionEntity WHERE customerId=:customerId ORDER BY createdAt DESC") fun customerPoints(customerId:String):Flow<List<CustomerPointTransactionEntity>>
@Query("SELECT COALESCE(SUM(delta),0) FROM CustomerPointTransactionEntity WHERE billId=:billId") suspend fun pointDeltaForBill(billId:String):Int
@Query("SELECT * FROM PricingRuleEntity ORDER BY name") fun pricingRules():Flow<List<PricingRuleEntity>>
@Query("SELECT * FROM PricingRuleEntity WHERE active=1") suspend fun activePricingRulesSnapshot():List<PricingRuleEntity>
@Query("SELECT * FROM BillAdjustmentEntity ORDER BY appliedAt DESC") fun billAdjustments():Flow<List<BillAdjustmentEntity>>
@Query("SELECT oi.itemNameSnapshot AS name, oi.qty AS qty, ob.sessionId AS sessionId FROM OrderItemEntity oi INNER JOIN OrderBatchEntity ob ON ob.id=oi.batchId INNER JOIN BillEntity b ON b.sessionId=ob.sessionId WHERE b.status='PAID' AND ob.status!='CANCELLED'") fun paidItemSales():Flow<List<ItemSaleRow>>
@Query("SELECT * FROM PurchaseEntity WHERE status='ACTIVE' ORDER BY purchasedAt DESC") fun purchases():Flow<List<PurchaseEntity>>
@Query("SELECT pi.categoryId AS categoryId, pi.amount AS amount, p.purchasedAt AS purchasedAt FROM PurchaseItemEntity pi INNER JOIN PurchaseEntity p ON p.id=pi.purchaseId WHERE p.status='ACTIVE'") fun purchaseCosts():Flow<List<PurchaseCostRow>>
@Query("SELECT * FROM PurchaseCategoryEntity WHERE active=1 ORDER BY sortOrder,name") fun purchaseCategories():Flow<List<PurchaseCategoryEntity>>
@Query("SELECT * FROM PurchaseCategoryEntity ORDER BY sortOrder,name") suspend fun allPurchaseCategoriesSnapshot():List<PurchaseCategoryEntity>
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
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveCustomer(v:CustomerEntity)
@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertCustomerPoint(v:CustomerPointTransactionEntity)
@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertPrintJob(v:PrintJobEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveArea(v:AreaEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveTable(v:DiningTableEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveCategory(v:MenuCategoryEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveMenuItem(v:MenuItemEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveCombo(v:ComboEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveComboItem(v:ComboItemEntity)
@Query("DELETE FROM ComboItemEntity WHERE comboId=:comboId") suspend fun deleteComboItems(comboId:String)
@Query("UPDATE ComboEntity SET active=:active WHERE id=:id") suspend fun setComboActive(id:String,active:Boolean)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveEmployee(v:EmployeeEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveSupplier(v:SupplierEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun savePurchaseCategory(v:PurchaseCategoryEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveSetting(v:AppSettingEntity)
@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun savePricingRule(v:PricingRuleEntity)
@Query("UPDATE PricingRuleEntity SET active=:active WHERE id=:id") suspend fun setPricingRuleActive(id:String,active:Boolean)
@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertBillAdjustments(v:List<BillAdjustmentEntity>)
@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertPurchase(v:PurchaseEntity)
@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertPurchaseItems(v:List<PurchaseItemEntity>)
@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun audit(v:AuditEventEntity)
@Query("UPDATE MenuItemEntity SET active=:active WHERE id=:id") suspend fun setMenuActive(id:String,active:Boolean)
@Query("UPDATE MenuCategoryEntity SET active=:active WHERE id=:id") suspend fun setCategoryActive(id:String,active:Boolean)
@Query("UPDATE PurchaseCategoryEntity SET active=:active WHERE id=:id") suspend fun setPurchaseCategoryActive(id:String,active:Boolean)
@Query("UPDATE AreaEntity SET active=0") suspend fun deactivateAllAreas()
@Query("UPDATE DiningTableEntity SET active=0") suspend fun deactivateAllTables()
@Query("UPDATE MenuCategoryEntity SET active=0") suspend fun deactivateAllMenuCategories()
@Query("UPDATE MenuItemEntity SET active=0") suspend fun deactivateAllMenuItems()
@Query("UPDATE ComboEntity SET active=0") suspend fun deactivateAllCombos()
@Query("UPDATE PricingRuleEntity SET active=0") suspend fun deactivateAllPricingRules()
@Query("UPDATE EmployeeEntity SET active=0") suspend fun deactivateAllEmployees()
@Query("UPDATE PurchaseCategoryEntity SET active=0") suspend fun deactivateAllPurchaseCategories()
@Query("DELETE FROM AppSettingEntity WHERE key NOT IN ('autoback_tree_uri','master_config_uri','storage_root_uri')") suspend fun clearConfigSettings()
@Query("UPDATE EmployeeEntity SET active=:active WHERE id=:id") suspend fun setEmployeeActive(id:String,active:Boolean)
@Query("UPDATE OrderBatchEntity SET status=:newStatus,sentAt=:sentAt WHERE id=:id AND status=:expected") suspend fun transitionBatch(id:String,expected:String,newStatus:String,sentAt:Long?):Int
@Query("UPDATE OrderBatchEntity SET status='DELIVERED',deliveredAt=:at,deliveredBy=:employeeId WHERE id=:id AND status='WAITING'") suspend fun markDelivered(id:String,at:Long,employeeId:String):Int
@Query("UPDATE OrderBatchEntity SET status='CANCELLED' WHERE id=:id AND status IN ('DRAFT','WAITING')") suspend fun cancelBatch(id:String):Int
@Query("UPDATE TableSessionEntity SET status='CLOSED',version=version+1 WHERE id=:id AND status='OPEN' AND version=:version") suspend fun closeSession(id:String,version:Long):Int
@Query("UPDATE PrintJobEntity SET status=:newStatus,claimedByDeviceId=:deviceId,attempts=attempts+1 WHERE id=:id AND status=:expected") suspend fun claimPrint(id:String,expected:String,newStatus:String,deviceId:String):Int
@Query("UPDATE PrintJobEntity SET status='PRINTED',printedAt=:printedAt,error=NULL WHERE id=:id") suspend fun markPrintSuccess(id:String,printedAt:Long)
@Query("UPDATE PrintJobEntity SET status='FAILED',error=:error WHERE id=:id") suspend fun markPrintFailed(id:String,error:String)
@Query("UPDATE BillEntity SET status='DELETED' WHERE id=:id AND status='PAID'") suspend fun softDeleteBill(id:String):Int
@Query("UPDATE BillEntity SET status='DELETED' WHERE id IN (:ids) AND status='PAID'") suspend fun softDeleteBills(ids:List<String>):Int
@Query("UPDATE CustomerEntity SET points=points+:pointsDelta,totalSpend=MAX(0,totalSpend+:spendDelta),visitCount=MAX(0,visitCount+:visitDelta),lastVisitAt=:lastVisitAt WHERE id=:customerId") suspend fun updateCustomerStats(customerId:String,pointsDelta:Int,spendDelta:Long,visitDelta:Int,lastVisitAt:Long?)
@Query("UPDATE PurchaseEntity SET status='DELETED' WHERE id=:id AND status='ACTIVE'") suspend fun softDeletePurchase(id:String):Int
}