from pathlib import Path

ROOT=Path('POS0210')

def rep(path, old, new, count=1):
    p=ROOT/path
    s=p.read_text()
    n=s.count(old)
    if n < count:
        raise SystemExit(f'anchor missing in {path}: {old[:140]!r} (found {n})')
    p.write_text(s.replace(old,new,count))

rep('app/build.gradle.kts','versionCode = 81','versionCode = 82')
rep('app/build.gradle.kts','versionName = "1.0.0-alpha52-candidate20"','versionName = "1.0.0-alpha52-candidate21"')

# Asset lifecycle DAO.
rep('app/src/main/java/vn/ecohome/pos0210/data/PosDao.kt',
    '@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveAsset(v:AssetEntity)\n',
    '@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveAsset(v:AssetEntity)\n@Query("UPDATE AssetEntity SET status=:status WHERE id=:id") suspend fun setAssetStatus(id:String,status:String):Int\n')

# Purchase delete + asset retirement stay atomic.
rep('app/src/main/java/vn/ecohome/pos0210/data/PosRepository.kt',
    'suspend fun deletePurchaseAudited(purchase:PurchaseEntity,reason:String,actorId:String):Boolean =',
    'suspend fun deletePurchaseAudited(purchase:PurchaseEntity,reason:String,actorId:String,linkedAssetId:String?=null):Boolean =')
rep('app/src/main/java/vn/ecohome/pos0210/data/PosRepository.kt',
    '            if(changed!=1) return@withTransaction false\n            dao.audit(AuditEventEntity(\n                UUID.randomUUID().toString(),"PURCHASE",purchase.id,"DELETE_SOFT",actorId,null,System.currentTimeMillis(),',
    '            if(changed!=1) return@withTransaction false\n            if(linkedAssetId!=null) dao.setAssetStatus(linkedAssetId,"DELETED")\n            dao.audit(AuditEventEntity(\n                UUID.randomUUID().toString(),"PURCHASE",purchase.id,"DELETE_SOFT",actorId,null,System.currentTimeMillis(),')

# New asset ids are deterministic from the purchase id.
rep('app/src/main/java/vn/ecohome/pos0210/PosViewModel.kt',
    '    listOf(PurchaseItemEntity(UUID.randomUUID().toString(),id,categoryId,name.trim(),qty,unit.ifBlank{"lần"},unitPrice,amount)),asset\n',
    '    listOf(PurchaseItemEntity(UUID.randomUUID().toString(),id,categoryId,name.trim(),qty,unit.ifBlank{"lần"},unitPrice,amount)),asset?.copy(id=assetIdForPurchase(id))\n')

# Editing a purchase updates or retires its linked asset before the old signature disappears.
rep('app/src/main/java/vn/ecohome/pos0210/PosViewModel.kt',
    '   val amount=(qty*unitPrice).toLong();val now=System.currentTimeMillis()\n   db.withTransaction{dao.updatePurchase(p.copy(supplierId=supplierId,purchasedAt=at,total=amount,note=note.trim(),expenseCategory=category,paidByName=paidByName.trim(),costCodeId=costCodeId,updatedAt=now));dao.updatePurchaseItem(line.copy(categoryId=detailCategoryId,qty=qty,unit=unit.ifBlank{"lần"},unitPrice=unitPrice,amount=amount))}\n',
    '''   val amount=(qty*unitPrice).toLong();val now=System.currentTimeMillis()\n   val linkedAsset=assets.value.firstOrNull{assetMatchesPurchaseSignature(it,p,line.name)}\n   db.withTransaction{\n    dao.updatePurchase(p.copy(supplierId=supplierId,purchasedAt=at,total=amount,note=note.trim(),expenseCategory=category,paidByName=paidByName.trim(),costCodeId=costCodeId,updatedAt=now))\n    dao.updatePurchaseItem(line.copy(categoryId=detailCategoryId,qty=qty,unit=unit.ifBlank{"lần"},unitPrice=unitPrice,amount=amount))\n    linkedAsset?.let{a->\n     val investmentClass=assetInvestmentClassForExpense(category)\n     if(investmentClass==null) dao.setAssetStatus(a.id,"DELETED") else dao.saveAsset(a.copy(purchaseDate=at,purchasePrice=unitPrice,quantity=qty.toInt().coerceAtLeast(1),totalCost=amount,supplier=supplierName.trim(),note=note.trim(),investmentClass=investmentClass))\n    }\n   }\n''')

# Deleting a purchase resolves its asset while the old purchase signature is still available.
rep('app/src/main/java/vn/ecohome/pos0210/PosViewModel.kt',
    '  viewModelScope.launch{\n   if(repo.deletePurchaseAudited(p,reason,e.id)){\n',
    '  viewModelScope.launch{\n   val line=dao.purchaseItemsSnapshot(p.id).firstOrNull()\n   val linkedAssetId=line?.let{l->assets.value.firstOrNull{assetMatchesPurchaseSignature(it,p,l.name)}?.id}\n   if(repo.deletePurchaseAudited(p,reason,e.id,linkedAssetId)){\n')

# Reports use active purchase-backed assets only for depreciation; orphan assets cannot inflate finance metrics.
rep('app/src/main/java/vn/ecohome/pos0210/MainActivity.kt',
    '    val activeAssets=assets.filter{it.status=="ACTIVE"||it.status=="DAMAGED"||it.status=="TRANSFERRED"}\n',
    '    val activeAssets=linkedAssetsForPurchases(assets,purchases,allItems).filter{it.status=="ACTIVE"||it.status=="DAMAGED"||it.status=="TRANSFERRED"}\n')
rep('app/src/main/java/vn/ecohome/pos0210/MainActivity.kt',
    '    val initialSunk=purchases.filter{it.expenseCategory in setOf(ExpenseCategories.SETUP_COST,ExpenseCategories.INITIAL_INVESTMENT_SUNK)}.sumOf{it.total};val initialAssets=assets.filter{it.investmentClass=="INITIAL"}.sumOf{it.totalCost};val initialInvestment=initialSunk+initialAssets\n',
    '    val initialInvestment=initialInvestmentFromPurchases(purchases)\n')
rep('app/src/main/java/vn/ecohome/pos0210/MainActivity.kt',
    'POS0210 v1.0.0-alpha52-candidate20 · versionCode 81',
    'POS0210 v1.0.0-alpha52-candidate21 · versionCode 82')

print('candidate21 asset-purchase integrity patch applied')
