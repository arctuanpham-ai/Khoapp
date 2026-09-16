from pathlib import Path

ROOT=Path('POS0210')

def replace(path, old, new, count=1):
    p=ROOT/path
    s=p.read_text()
    actual=s.count(old)
    if actual < count:
        raise SystemExit(f'anchor missing in {path}: expected >= {count}, got {actual}: {old[:120]!r}')
    s=s.replace(old,new,count)
    p.write_text(s)

# Candidate identity only.
replace('app/build.gradle.kts','versionCode = 81','versionCode = 82')
replace('app/build.gradle.kts','versionName = "1.0.0-alpha52-candidate20"','versionName = "1.0.0-alpha52-candidate21"')

# DAO: allow the purchase lifecycle to retire its matching asset record.
replace(
 'app/src/main/java/vn/ecohome/pos0210/data/PosDao.kt',
 '@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveAsset(v:AssetEntity)\n',
 '@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveAsset(v:AssetEntity)\n@Query("UPDATE AssetEntity SET status=:status WHERE id=:id") suspend fun setAssetStatus(id:String,status:String):Int\n'
)

# Repository: purchase soft-delete and linked asset retirement are one transaction.
old='''    suspend fun deletePurchaseAudited(purchase:PurchaseEntity,reason:String,actorId:String):Boolean =
        db.withTransaction {
            val changed=dao.softDeletePurchase(purchase.id)
            if(changed!=1) return@withTransaction false
            dao.audit(AuditEventEntity(
                UUID.randomUUID().toString(),"PURCHASE",purchase.id,"DELETE_SOFT",actorId,null,System.currentTimeMillis(),
                "reason=${reason.trim()},total=${purchase.total}"
            ))
            true
        }
'''
new='''    suspend fun deletePurchaseAudited(purchase:PurchaseEntity,reason:String,actorId:String,linkedAssetId:String?=null):Boolean =
        db.withTransaction {
            val changed=dao.softDeletePurchase(purchase.id)
            if(changed!=1) return@withTransaction false
            if(linkedAssetId!=null) dao.setAssetStatus(linkedAssetId,"DELETED")
            dao.audit(AuditEventEntity(
                UUID.randomUUID().toString(),"PURCHASE",purchase.id,"DELETE_SOFT",actorId,null,System.currentTimeMillis(),
                "reason=${reason.trim()},total=${purchase.total},asset=${linkedAssetId.orEmpty()}"
            ))
            true
        }
'''
replace('app/src/main/java/vn/ecohome/pos0210/data/PosRepository.kt',old,new)

# ViewModel: new assets get a deterministic purchase-derived id.
replace(
 'app/src/main/java/vn/ecohome/pos0210/PosViewModel.kt',
 '    listOf(PurchaseItemEntity(UUID.randomUUID().toString(),id,categoryId,name.trim(),qty,unit.ifBlank{"lần"},unitPrice,amount)),asset\n',
 '    listOf(PurchaseItemEntity(UUID.randomUUID().toString(),id,categoryId,name.trim(),qty,unit.ifBlank{"lần"},unitPrice,amount)),asset?.copy(id=assetIdForPurchase(id))\n'
)

# ViewModel: editing an asset-backed purchase updates/retire the same asset; no stale duplicate remains.
old=''' fun updatePurchaseFinancial(p:PurchaseEntity,line:PurchaseItemEntity,category:String,detailCategoryId:String,costCodeId:String?,paidByName:String,supplierName:String,note:String,at:Long,qty:Double,unit:String,unitPrice:Long){
  val e=currentEmployee.value?:return;if(!e.canPurchase&&e.role!="ADMIN")return;if(category !in ExpenseCategories.all||qty<=0||unitPrice<=0)return
  viewModelScope.launch(Dispatchers.IO){
   val supplierId=if(supplierName.isBlank())null else p.supplierId?.takeIf{sid->suppliers.value.any{it.id==sid&&it.name==supplierName.trim()}}?:UUID.randomUUID().toString().also{repo.saveSupplier(SupplierEntity(it,supplierName.trim()))}
   val amount=(qty*unitPrice).toLong();val now=System.currentTimeMillis()
   db.withTransaction{dao.updatePurchase(p.copy(supplierId=supplierId,purchasedAt=at,total=amount,note=note.trim(),expenseCategory=category,paidByName=paidByName.trim(),costCodeId=costCodeId,updatedAt=now));dao.updatePurchaseItem(line.copy(categoryId=detailCategoryId,qty=qty,unit=unit.ifBlank{"lần"},unitPrice=unitPrice,amount=amount))}
   audit("PURCHASE",p.id,"UPDATE","amount=${p.total}->$amount,category=${p.expenseCategory}->$category,payer=${p.paidByName}->${paidByName.trim()},costCode=${p.costCodeId}->${costCodeId}");autoBackup()
  }
 }
'''
new=''' fun updatePurchaseFinancial(p:PurchaseEntity,line:PurchaseItemEntity,category:String,detailCategoryId:String,costCodeId:String?,paidByName:String,supplierName:String,note:String,at:Long,qty:Double,unit:String,unitPrice:Long){
  val e=currentEmployee.value?:return;if(!e.canPurchase&&e.role!="ADMIN")return;if(category !in ExpenseCategories.all||qty<=0||unitPrice<=0)return
  viewModelScope.launch(Dispatchers.IO){
   val supplierId=if(supplierName.isBlank())null else p.supplierId?.takeIf{sid->suppliers.value.any{it.id==sid&&it.name==supplierName.trim()}}?:UUID.randomUUID().toString().also{repo.saveSupplier(SupplierEntity(it,supplierName.trim()))}
   val amount=(qty*unitPrice).toLong();val now=System.currentTimeMillis()
   val linkedAsset=assets.value.firstOrNull{assetMatchesPurchaseSignature(it,p,line.name)}
   db.withTransaction{
    dao.updatePurchase(p.copy(supplierId=supplierId,purchasedAt=at,total=amount,note=note.trim(),expenseCategory=category,paidByName=paidByName.trim(),costCodeId=costCodeId,updatedAt=now))
    dao.updatePurchaseItem(line.copy(categoryId=detailCategoryId,qty=qty,unit=unit.ifBlank{"lần"},unitPrice=unitPrice,amount=amount))
    linkedAsset?.let{a->
     val investmentClass=assetInvestmentClassForExpense(category)
     if(investmentClass==null) dao.setAssetStatus(a.id,"DELETED")
     else dao.saveAsset(a.copy(purchaseDate=at,purchasePrice=unitPrice,quantity=qty.toInt().coerceAtLeast(1),totalCost=amount,supplier=supplierName.trim(),note=note.trim(),investmentClass=investmentClass))
    }
   }
   audit("PURCHASE",p.id,"UPDATE","amount=${p.total}->$amount,category=${p.expenseCategory}->$category,payer=${p.paidByName}->${paidByName.trim()},costCode=${p.costCodeId}->${costCodeId}");autoBackup()
  }
 }
'''
replace('app/src/main/java/vn/ecohome/pos0210/PosViewModel.kt',old,new)

# ViewModel: deleting a purchase identifies its linked asset before the atomic repository delete.
old=''' fun deletePurchase(p:PurchaseEntity,reason:String){
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN"||reason.isBlank())return
  viewModelScope.launch{
   if(repo.deletePurchaseAudited(p,reason,e.id)){
    autoBackup()
   }
  }
 }
'''
new=''' fun deletePurchase(p:PurchaseEntity,reason:String){
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN"||reason.isBlank())return
  viewModelScope.launch{
   val line=dao.purchaseItemsSnapshot(p.id).firstOrNull()
   val linkedAssetId=line?.let{l->assets.value.firstOrNull{assetMatchesPurchaseSignature(it,p,l.name)}?.id}
   if(repo.deletePurchaseAudited(p,reason,e.id,linkedAssetId)){
    autoBackup()
   }
  }
 }
'''
replace('app/src/main/java/vn/ecohome/pos0210/PosViewModel.kt',old,new)

# Monthly report: only assets backed by an ACTIVE purchase contribute depreciation.
replace(
 'app/src/main/java/vn/ecohome/pos0210/MainActivity.kt',
 '    val activeAssets=assets.filter{it.status=="ACTIVE"||it.status=="DAMAGED"||it.status=="TRANSFERRED"}\n',
 '    val activeAssets=linkedAssetsForPurchases(assets,purchases,allItems).filter{it.status=="ACTIVE"||it.status=="DAMAGED"||it.status=="TRANSFERRED"}\n'
)

# Initial investment/payback uses the purchase ledger as the single cash source of truth.
old='''    val initialSunk=purchases.filter{it.expenseCategory in setOf(ExpenseCategories.SETUP_COST,ExpenseCategories.INITIAL_INVESTMENT_SUNK)}.sumOf{it.total};val initialAssets=assets.filter{it.investmentClass=="INITIAL"}.sumOf{it.totalCost};val initialInvestment=initialSunk+initialAssets
'''
new='''    val initialInvestment=initialInvestmentFromPurchases(purchases)
'''
replace('app/src/main/java/vn/ecohome/pos0210/MainActivity.kt',old,new)

# Keep visible build identity accurate while already touching this screen.
replace(
 'app/src/main/java/vn/ecohome/pos0210/MainActivity.kt',
 'POS0210 v1.0.0-alpha52-candidate18 · versionCode 79',
 'POS0210 v1.0.0-alpha52-candidate21 · versionCode 82'
)

print('candidate21 asset-purchase integrity patch applied')
