from pathlib import Path
import argparse, re

ROOT=Path(__file__).resolve().parents[1]

def read(rel): return (ROOT/rel).read_text()
def write(rel,s):
    p=ROOT/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s)
def replace_once(rel,old,new):
    s=read(rel);n=s.count(old)
    if n!=1: raise SystemExit(f"{rel}: expected one match, got {n}: {old[:120]!r}")
    write(rel,s.replace(old,new,1))
def regex_once(rel,pattern,repl):
    s=read(rel);out,n=re.subn(pattern,repl,s,count=1,flags=re.S)
    if n!=1: raise SystemExit(f"{rel}: regex expected one match, got {n}: {pattern[:120]!r}")
    write(rel,out)

def write_tests():
    write('POS0210/app/src/test/java/vn/ecohome/pos0210/FinancialLedgerV2Test.kt',r'''package vn.ecohome.pos0210
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialLedgerV2Test {
 @Test fun cashResultUsesRevenueMinusCurrentExpenses() {
  val r=calculateFinancialPeriod(FinancialPeriodInput(100_000_000,70_000_000,0,9_000_000))
  assertEquals(30_000_000,r.operatingResultBeforeAdditionalInvestment)
  assertEquals(30_000_000,r.netCashResult)
 }
 @Test fun additionalInvestmentIsCurrentCashOutButSeparateFromOperations() {
  val r=calculateFinancialPeriod(FinancialPeriodInput(150_000_000,110_000_000,50_000_000,0))
  assertEquals(40_000_000,r.operatingResultBeforeAdditionalInvestment)
  assertEquals(-10_000_000,r.netCashResult)
 }
 @Test fun depreciationIsReferenceOnly() {
  val r=calculateFinancialPeriod(FinancialPeriodInput(100_000_000,70_000_000,0,1_000_000))
  assertEquals(30_000_000,r.netCashResult)
  assertEquals(1_000_000,r.depreciationReference)
 }
 @Test fun paybackUsesCumulativeNetCashAgainstInitialInvestment() {
  val r=calculateCashPayback(300_000_000,210_000_000)
  assertEquals(7000,r.paybackBasisPoints)
  assertEquals(90_000_000,r.remaining)
 }
 @Test fun reimbursementReducesDebtWithoutCreatingAnotherExpense() {
  val rows=calculatePayerLedger(listOf(PayerAdvance("A",5_000_000)),listOf(PayerReimbursement("A",2_000_000)))
  assertEquals(1,rows.size);assertEquals(5_000_000,rows[0].advanced);assertEquals(2_000_000,rows[0].reimbursed);assertEquals(3_000_000,rows[0].outstanding)
 }
 @Test fun costVarianceSeparatesPriceAndUsageMovement() {
  val v=calculateCostVariance(CostObservation(10.0,220_000),CostObservation(10.0,245_000))
  assertEquals(0.0,v.quantityChangePct,0.001);assertTrue(v.unitPriceChangePct>11.3);assertTrue(v.unitPriceChangePct<11.4)
 }
}
''')

def apply_patch():
    write('POS0210/app/src/main/java/vn/ecohome/pos0210/FinancialLedgerV2.kt',r'''package vn.ecohome.pos0210

data class FinancialPeriodInput(val revenue:Long,val operatingExpenses:Long,val additionalInvestment:Long=0,val depreciationReference:Long=0)
data class FinancialPeriodResult(val revenue:Long,val operatingExpenses:Long,val operatingResultBeforeAdditionalInvestment:Long,val additionalInvestment:Long,val netCashResult:Long,val depreciationReference:Long)
fun calculateFinancialPeriod(i:FinancialPeriodInput)=FinancialPeriodResult(i.revenue,i.operatingExpenses,i.revenue-i.operatingExpenses,i.additionalInvestment,i.revenue-i.operatingExpenses-i.additionalInvestment,i.depreciationReference)

data class CashPaybackResult(val initialInvestment:Long,val cumulativeNetCash:Long,val paybackBasisPoints:Int,val remaining:Long)
fun calculateCashPayback(initialInvestment:Long,cumulativeNetCash:Long):CashPaybackResult{
 require(initialInvestment>=0)
 val recovered=cumulativeNetCash.coerceAtLeast(0)
 val bp=if(initialInvestment==0L)10000 else ((recovered.coerceAtMost(initialInvestment)*10000L)/initialInvestment).toInt()
 return CashPaybackResult(initialInvestment,cumulativeNetCash,bp,(initialInvestment-recovered).coerceAtLeast(0))
}

data class PayerAdvance(val payer:String,val amount:Long)
data class PayerReimbursement(val payer:String,val amount:Long)
data class PayerLedgerLine(val payer:String,val advanced:Long,val reimbursed:Long,val outstanding:Long)
fun calculatePayerLedger(advances:List<PayerAdvance>,reimbursements:List<PayerReimbursement>):List<PayerLedgerLine>{
 val names=(advances.map{it.payer.trim()}.filter{it.isNotBlank()}+reimbursements.map{it.payer.trim()}.filter{it.isNotBlank()}).distinct()
 return names.map{name->
  val a=advances.filter{it.payer.trim()==name}.sumOf{it.amount};val r=reimbursements.filter{it.payer.trim()==name}.sumOf{it.amount}
  PayerLedgerLine(name,a,r,(a-r).coerceAtLeast(0))
 }.sortedByDescending{it.outstanding}
}

data class CostObservation(val quantity:Double,val unitPrice:Long){val total:Double get()=quantity*unitPrice}
data class CostVariance(val quantityChangePct:Double,val unitPriceChangePct:Double,val totalChangePct:Double)
private fun pct(old:Double,new:Double)=if(old==0.0)0.0 else (new-old)*100.0/old
fun calculateCostVariance(previous:CostObservation,current:CostObservation)=CostVariance(pct(previous.quantity,current.quantity),pct(previous.unitPrice.toDouble(),current.unitPrice.toDouble()),pct(previous.total,current.total))

fun allocatePositiveCash(result:Long,partners:List<ProfitShareInput>):List<PartnerProfit>{
 if(result<=0||partners.isEmpty()||partners.sumOf{it.shareBasisPoints}!=10000)return emptyList()
 var allocated=0L
 return partners.mapIndexed{i,p->val amount=if(i==partners.lastIndex)result-allocated else result*p.shareBasisPoints/10000L;allocated+=amount;PartnerProfit(p.id,p.name,p.shareBasisPoints,amount)}
}
''')

    ent='POS0210/app/src/main/java/vn/ecohome/pos0210/data/Entities.kt'
    replace_once(ent,'@Entity(indices=[Index("supplierId"),Index("status"),Index("expenseCategory")]) data class PurchaseEntity(@PrimaryKey val id:String,val supplierId:String?,val enteredBy:String,val purchasedAt:Long,val total:Long,val note:String="",val invoiceImageUri:String?=null,val status:String="ACTIVE",val expenseCategory:String="UNCLASSIFIED",val paidByName:String="")',
'''@Entity(indices=[Index(value=["code"],unique=true),Index("parentExpenseCategory"),Index("active")]) data class CostCodeEntity(@PrimaryKey val id:String,val code:String,val name:String,val parentExpenseCategory:String,val defaultUnit:String="lần",val defaultSupplier:String="",val referenceUnitPrice:Long?=null,val sortOrder:Int=0,val active:Boolean=true)
@Entity(indices=[Index("supplierId"),Index("status"),Index("expenseCategory"),Index("costCodeId")]) data class PurchaseEntity(@PrimaryKey val id:String,val supplierId:String?,val enteredBy:String,val purchasedAt:Long,val total:Long,val note:String="",val invoiceImageUri:String?=null,val status:String="ACTIVE",val expenseCategory:String="UNCLASSIFIED",val paidByName:String="",val costCodeId:String?=null,val updatedAt:Long?=null)''')
    replace_once(ent,'@Entity(indices=[Index("categoryId"),Index("purchaseDate"),Index("status")]) data class AssetEntity(@PrimaryKey val id:String,val name:String,val categoryId:String,val purchaseDate:Long,val purchasePrice:Long,val quantity:Int=1,val totalCost:Long,val supplier:String="",val usefulLifeMonths:Int,val depreciationMethod:String="STRAIGHT_LINE",val residualValue:Long=0,val estimatedLiquidationValue:Long=0,val status:String="ACTIVE",val disposalDate:Long?=null,val disposalPrice:Long?=null,val note:String="")',
'@Entity(indices=[Index("categoryId"),Index("purchaseDate"),Index("status")]) data class AssetEntity(@PrimaryKey val id:String,val name:String,val categoryId:String,val purchaseDate:Long,val purchasePrice:Long,val quantity:Int=1,val totalCost:Long,val supplier:String="",val usefulLifeMonths:Int,val depreciationMethod:String="STRAIGHT_LINE",val residualValue:Long=0,val estimatedLiquidationValue:Long=0,val status:String="ACTIVE",val disposalDate:Long?=null,val disposalPrice:Long?=null,val note:String="",val investmentClass:String="INITIAL")')
    replace_once(ent,'@Entity(indices=[Index("occurredAt"),Index("type"),Index("partnerId")]) data class FinancialMovementEntity(@PrimaryKey val id:String,val type:String,val amount:Long,val occurredAt:Long,val partnerId:String?=null,val method:String="CASH",val note:String="")',
'@Entity(indices=[Index("occurredAt"),Index("type"),Index("partnerId")]) data class FinancialMovementEntity(@PrimaryKey val id:String,val type:String,val amount:Long,val occurredAt:Long,val partnerId:String?=null,val method:String="CASH",val note:String="",val counterpartyName:String="")')

    db='POS0210/app/src/main/java/vn/ecohome/pos0210/data/PosDatabase.kt'
    replace_once(db,'OpeningCashAdjustmentEntity::class,CloudSyncStateEntity::class],version=18,exportSchema=false)','OpeningCashAdjustmentEntity::class,CloudSyncStateEntity::class,CostCodeEntity::class],version=19,exportSchema=false)')
    replace_once(db,'''  private val MIGRATION_17_18=object:Migration(17,18){
   override fun migrate(db:SupportSQLiteDatabase){
    db.execSQL("ALTER TABLE PurchaseEntity ADD COLUMN paidByName TEXT NOT NULL DEFAULT ''")
   }
  }
''','''  private val MIGRATION_17_18=object:Migration(17,18){
   override fun migrate(db:SupportSQLiteDatabase){
    db.execSQL("ALTER TABLE PurchaseEntity ADD COLUMN paidByName TEXT NOT NULL DEFAULT ''")
   }
  }
  private val MIGRATION_18_19=object:Migration(18,19){
   override fun migrate(db:SupportSQLiteDatabase){
    db.execSQL("ALTER TABLE PurchaseEntity ADD COLUMN costCodeId TEXT")
    db.execSQL("ALTER TABLE PurchaseEntity ADD COLUMN updatedAt INTEGER")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_PurchaseEntity_costCodeId ON PurchaseEntity(costCodeId)")
    db.execSQL("ALTER TABLE AssetEntity ADD COLUMN investmentClass TEXT NOT NULL DEFAULT 'INITIAL'")
    db.execSQL("ALTER TABLE FinancialMovementEntity ADD COLUMN counterpartyName TEXT NOT NULL DEFAULT ''")
    db.execSQL("CREATE TABLE IF NOT EXISTS CostCodeEntity (id TEXT NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, parentExpenseCategory TEXT NOT NULL, defaultUnit TEXT NOT NULL, defaultSupplier TEXT NOT NULL, referenceUnitPrice INTEGER, sortOrder INTEGER NOT NULL, active INTEGER NOT NULL, PRIMARY KEY(id))")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_CostCodeEntity_code ON CostCodeEntity(code)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_CostCodeEntity_parentExpenseCategory ON CostCodeEntity(parentExpenseCategory)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_CostCodeEntity_active ON CostCodeEntity(active)")
   }
  }
''')
    replace_once(db,'.addMigrations(MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6,MIGRATION_6_7,MIGRATION_7_8,MIGRATION_8_9,MIGRATION_9_10,MIGRATION_10_11,MIGRATION_11_12,MIGRATION_12_13,MIGRATION_13_14,MIGRATION_14_15,MIGRATION_15_16,MIGRATION_16_17,MIGRATION_17_18)',
'.addMigrations(MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6,MIGRATION_6_7,MIGRATION_7_8,MIGRATION_8_9,MIGRATION_9_10,MIGRATION_10_11,MIGRATION_11_12,MIGRATION_12_13,MIGRATION_13_14,MIGRATION_14_15,MIGRATION_15_16,MIGRATION_16_17,MIGRATION_17_18,MIGRATION_18_19)')

    dao='POS0210/app/src/main/java/vn/ecohome/pos0210/data/PosDao.kt'
    replace_once(dao,'@Query("SELECT * FROM PurchaseCategoryEntity WHERE active=1 ORDER BY sortOrder,name") fun purchaseCategories():Flow<List<PurchaseCategoryEntity>>',
'@Query("SELECT * FROM PurchaseCategoryEntity WHERE active=1 ORDER BY sortOrder,name") fun purchaseCategories():Flow<List<PurchaseCategoryEntity>>\n@Query("SELECT * FROM CostCodeEntity WHERE active=1 ORDER BY sortOrder,code") fun costCodes():Flow<List<CostCodeEntity>>\n@Query("SELECT * FROM CostCodeEntity ORDER BY sortOrder,code") suspend fun allCostCodesSnapshot():List<CostCodeEntity>\n@Query("SELECT * FROM PurchaseItemEntity") fun allPurchaseItems():Flow<List<PurchaseItemEntity>>\n@Query("SELECT * FROM PurchaseItemEntity WHERE purchaseId=:purchaseId") suspend fun purchaseItemsSnapshot(purchaseId:String):List<PurchaseItemEntity>')
    replace_once(dao,'@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun savePurchaseCategory(v:PurchaseCategoryEntity)',
'@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun savePurchaseCategory(v:PurchaseCategoryEntity)\n@Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveCostCode(v:CostCodeEntity)')
    replace_once(dao,'@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertPurchaseItems(v:List<PurchaseItemEntity>)',
'@Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertPurchaseItems(v:List<PurchaseItemEntity>)\n@Update suspend fun updatePurchase(v:PurchaseEntity):Int\n@Update suspend fun updatePurchaseItem(v:PurchaseItemEntity):Int')
    replace_once(dao,'@Query("UPDATE PurchaseCategoryEntity SET active=:active WHERE id=:id") suspend fun setPurchaseCategoryActive(id:String,active:Boolean)',
'@Query("UPDATE PurchaseCategoryEntity SET active=:active WHERE id=:id") suspend fun setPurchaseCategoryActive(id:String,active:Boolean)\n@Query("UPDATE CostCodeEntity SET active=:active WHERE id=:id") suspend fun setCostCodeActive(id:String,active:Boolean)')

    ma='POS0210/app/src/main/java/vn/ecohome/pos0210/MonthlyAccounting.kt'
    replace_once(ma,'    const val VARIABLE_EXPENSE="VARIABLE_EXPENSE"\n    const val CAPITAL_ASSET="CAPITAL_ASSET"',
'''    const val VARIABLE_EXPENSE="VARIABLE_EXPENSE"
    const val PAYROLL="PAYROLL"
    const val ELECTRICITY="ELECTRICITY"
    const val WATER="WATER"
    const val RENT="RENT"
    const val MARKETING="MARKETING"
    const val CONSUMABLES="CONSUMABLES"
    const val MAINTENANCE="MAINTENANCE"
    const val SERVICES="SERVICES"
    const val BANK_FEES="BANK_FEES"
    const val ADDITIONAL_INVESTMENT="ADDITIONAL_INVESTMENT"
    const val CAPITAL_ASSET="CAPITAL_ASSET"''')
    regex_once(ma,r'    val all=listOf\([^\n]+\)', '    val all=listOf(UNCLASSIFIED,INVENTORY_PURCHASE,PAYROLL,ELECTRICITY,WATER,RENT,MARKETING,CONSUMABLES,MAINTENANCE,SERVICES,BANK_FEES,FIXED_EXPENSE,VARIABLE_EXPENSE,OTHER_EXPENSE,ADDITIONAL_INVESTMENT,SETUP_COST,INITIAL_INVESTMENT_SUNK,CAPITAL_ASSET,WORKING_CAPITAL,OWNER_CONTRIBUTION,OWNER_WITHDRAWAL,PROFIT_WITHDRAWAL)')
    replace_once(ma,'        INVENTORY_PURCHASE->"Nguyên liệu / hàng hóa";FIXED_EXPENSE->"Chi phí cố định";VARIABLE_EXPENSE->"Chi phí biến đổi"',
'        INVENTORY_PURCHASE->"Nguyên liệu / hàng hóa";PAYROLL->"Lương / nhân sự";ELECTRICITY->"Điện";WATER->"Nước";RENT->"Thuê mặt bằng";MARKETING->"Marketing";CONSUMABLES->"Bao bì / vật tư tiêu hao";MAINTENANCE->"Sửa chữa / bảo trì";SERVICES->"Phần mềm / dịch vụ";BANK_FEES->"Phí ngân hàng / giao dịch";ADDITIONAL_INVESTMENT->"Đầu tư bổ sung";FIXED_EXPENSE->"Chi phí cố định";VARIABLE_EXPENSE->"Chi phí biến đổi"')
    replace_once(ma,'    const val ASSET_PURCHASE="ASSET_PURCHASE"\n    const val INITIAL_INVESTMENT="INITIAL_INVESTMENT"',
'    const val ASSET_PURCHASE="ASSET_PURCHASE"\n    const val ADDITIONAL_INVESTMENT="ADDITIONAL_INVESTMENT"\n    const val INITIAL_INVESTMENT="INITIAL_INVESTMENT"')
    replace_once(ma,'    val all=listOf(OPERATING_EXPENSE,INITIAL_INVESTMENT,ASSET_PURCHASE,CAPITAL_INJECTION,WORKING_CAPITAL,PROFIT_WITHDRAWAL,OWNER_WITHDRAWAL,CAPITAL_RECOVERY,OTHER_ADJUSTMENT)',
'    val all=listOf(OPERATING_EXPENSE,ADDITIONAL_INVESTMENT,INITIAL_INVESTMENT,ASSET_PURCHASE,CAPITAL_INJECTION,WORKING_CAPITAL,PROFIT_WITHDRAWAL,OWNER_WITHDRAWAL,CAPITAL_RECOVERY,OTHER_ADJUSTMENT)')
    replace_once(ma,'    fun label(v:String)=when(v){OPERATING_EXPENSE->"Chi phí vận hành";INITIAL_INVESTMENT->"Đầu tư ban đầu không thu hồi";ASSET_PURCHASE->"Mua tài sản";',
'    fun label(v:String)=when(v){OPERATING_EXPENSE->"Chi phí vận hành";ADDITIONAL_INVESTMENT->"Đầu tư bổ sung";INITIAL_INVESTMENT->"Đầu tư ban đầu không thu hồi";ASSET_PURCHASE->"Mua tài sản đầu tư ban đầu";')
    replace_once(ma,'    fun usesPurchaseDocument(v:String)=v in setOf(OPERATING_EXPENSE,INITIAL_INVESTMENT,ASSET_PURCHASE)',
'    fun usesPurchaseDocument(v:String)=v in setOf(OPERATING_EXPENSE,ADDITIONAL_INVESTMENT,INITIAL_INVESTMENT,ASSET_PURCHASE)')
    replace_once(ma,'    fun legacyExpenseCode(v:String,operatingCode:String)=when(v){INITIAL_INVESTMENT->ExpenseCategories.INITIAL_INVESTMENT_SUNK;ASSET_PURCHASE->ExpenseCategories.CAPITAL_ASSET;',
'    fun legacyExpenseCode(v:String,operatingCode:String)=when(v){ADDITIONAL_INVESTMENT->ExpenseCategories.ADDITIONAL_INVESTMENT;INITIAL_INVESTMENT->ExpenseCategories.INITIAL_INVESTMENT_SUNK;ASSET_PURCHASE->ExpenseCategories.CAPITAL_ASSET;')
    replace_once(ma,'    fun fromLegacy(code:String)=when(code){ExpenseCategories.SETUP_COST,ExpenseCategories.INITIAL_INVESTMENT_SUNK->INITIAL_INVESTMENT;ExpenseCategories.CAPITAL_ASSET->ASSET_PURCHASE;',
'    fun fromLegacy(code:String)=when(code){ExpenseCategories.ADDITIONAL_INVESTMENT->ADDITIONAL_INVESTMENT;ExpenseCategories.SETUP_COST,ExpenseCategories.INITIAL_INVESTMENT_SUNK->INITIAL_INVESTMENT;ExpenseCategories.CAPITAL_ASSET->ASSET_PURCHASE;')

    vm='POS0210/app/src/main/java/vn/ecohome/pos0210/PosViewModel.kt'
    replace_once(vm,'val purchases=dao.purchases().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val purchaseCosts=dao.purchaseCosts().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val purchaseCategories=dao.purchaseCategories().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());',
'val purchases=dao.purchases().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val purchaseCosts=dao.purchaseCosts().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val purchaseCategories=dao.purchaseCategories().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val costCodes=dao.costCodes().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val purchaseItemsAll=dao.allPurchaseItems().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());')
    replace_once(vm,'  seedAssetCategories()\n  if(dao.areas().first().isNotEmpty())return', '  seedAssetCategories()\n  seedCostCodes()\n  if(dao.areas().first().isNotEmpty())return')
    replace_once(vm,' private suspend fun seed(){if(dao.areas().first().isNotEmpty())return;', ''' private suspend fun seedCostCodes(){
  if(dao.costCodes().first().isNotEmpty())return
  listOf(
   CostCodeEntity("cc_electric","DV-DIEN","Điện",ExpenseCategories.ELECTRICITY,"kWh",sortOrder=0),
   CostCodeEntity("cc_water","DV-NUOC","Nước",ExpenseCategories.WATER,"m³",sortOrder=1),
   CostCodeEntity("cc_rent","DV-THUE","Thuê mặt bằng",ExpenseCategories.RENT,"tháng",sortOrder=2),
   CostCodeEntity("cc_payroll","NS-LUONG","Lương",ExpenseCategories.PAYROLL,"tháng",sortOrder=3),
   CostCodeEntity("cc_coffee","NL-CAFE-HAT","Cà phê hạt",ExpenseCategories.INVENTORY_PURCHASE,"kg",sortOrder=4)
  ).forEach{dao.saveCostCode(it)}
 }
 private suspend fun seed(){if(dao.areas().first().isNotEmpty())return;''')
    replace_once(vm,'fun addPurchaseDetailed(name:String,qty:Double,unit:String,unitPrice:Long,note:String,at:Long=System.currentTimeMillis(),supplierName:String="",imageUri:String?=null,categoryId:String="pc_production",expenseCategory:String="UNCLASSIFIED",asset:AssetEntity?=null,paidByName:String=""){',
'fun addPurchaseDetailed(name:String,qty:Double,unit:String,unitPrice:Long,note:String,at:Long=System.currentTimeMillis(),supplierName:String="",imageUri:String?=null,categoryId:String="pc_production",expenseCategory:String="UNCLASSIFIED",asset:AssetEntity?=null,paidByName:String="",costCodeId:String?=null){')
    replace_once(vm,'PurchaseEntity(id,supplierId,e.id,at,amount,note,managed,expenseCategory=expenseCategory,paidByName=paidByName.trim()),',
'PurchaseEntity(id,supplierId,e.id,at,amount,note,managed,expenseCategory=expenseCategory,paidByName=paidByName.trim(),costCodeId=costCodeId),')
    replace_once(vm,' fun updatePurchaseExpenseCategory(p:PurchaseEntity,category:String){', ''' fun addCostCode(code:String,name:String,parent:String,unit:String,supplier:String=""){
  val e=currentEmployee.value?:return;if(e.role!="ADMIN"||code.isBlank()||name.isBlank()||parent !in ExpenseCategories.all)return
  viewModelScope.launch(Dispatchers.IO){dao.saveCostCode(CostCodeEntity(UUID.randomUUID().toString(),code.trim().uppercase(),name.trim(),parent,unit.ifBlank{"lần"},supplier.trim(),sortOrder=costCodes.value.size));audit("COST_CODE",code.trim().uppercase(),"SAVE","name=${name.trim()}");autoBackup()}
 }
 fun updatePurchaseFinancial(p:PurchaseEntity,line:PurchaseItemEntity,category:String,detailCategoryId:String,costCodeId:String?,paidByName:String,supplierName:String,note:String,at:Long,qty:Double,unit:String,unitPrice:Long){
  val e=currentEmployee.value?:return;if(!e.canPurchase&&e.role!="ADMIN")return;if(category !in ExpenseCategories.all||qty<=0||unitPrice<=0)return
  viewModelScope.launch(Dispatchers.IO){
   val supplierId=if(supplierName.isBlank())null else p.supplierId?.takeIf{sid->suppliers.value.any{it.id==sid&&it.name==supplierName.trim()}}?:UUID.randomUUID().toString().also{repo.saveSupplier(SupplierEntity(it,supplierName.trim()))}
   val amount=(qty*unitPrice).toLong();val now=System.currentTimeMillis()
   db.withTransaction{dao.updatePurchase(p.copy(supplierId=supplierId,purchasedAt=at,total=amount,note=note.trim(),expenseCategory=category,paidByName=paidByName.trim(),costCodeId=costCodeId,updatedAt=now));dao.updatePurchaseItem(line.copy(categoryId=detailCategoryId,qty=qty,unit=unit.ifBlank{"lần"},unitPrice=unitPrice,amount=amount))}
   audit("PURCHASE",p.id,"UPDATE","amount=${p.total}->$amount,category=${p.expenseCategory}->$category,payer=${p.paidByName}->${paidByName.trim()},costCode=${p.costCodeId}->${costCodeId}");autoBackup()
  }
 }
 fun reimbursePayer(payer:String,amount:Long,method:String,note:String,at:Long=System.currentTimeMillis()){
  val e=currentEmployee.value?:return;if(e.role!="ADMIN"||payer.isBlank()||amount<=0)return
  viewModelScope.launch(Dispatchers.IO){val id=UUID.randomUUID().toString();dao.insertFinancialMovement(FinancialMovementEntity(id,"PAYER_REIMBURSEMENT",amount,at,method=method,note=note.trim(),counterpartyName=payer.trim()));audit("FINANCE",id,"PAYER_REIMBURSEMENT","payer=${payer.trim()},amount=$amount");autoBackup()}
 }
 fun updatePurchaseExpenseCategory(p:PurchaseEntity,category:String){''')

    ui='POS0210/app/src/main/java/vn/ecohome/pos0210/MainActivity.kt'
    replace_once(ui,'    val purchaseCategories by vm.purchaseCategories.collectAsState()\n    val assetCategories by vm.assetCategories.collectAsState()', '    val purchaseCategories by vm.purchaseCategories.collectAsState()\n    val costCodes by vm.costCodes.collectAsState()\n    val assetCategories by vm.assetCategories.collectAsState()')
    replace_once(ui,'    var paidByName by remember { mutableStateOf("") }\n    var note by remember { mutableStateOf("") }', '    var paidByName by remember { mutableStateOf("") }\n    var selectedCostCodeId by remember { mutableStateOf<String?>(null) }\n    var showCostCodeManager by remember { mutableStateOf(false) }\n    var note by remember { mutableStateOf("") }')
    replace_once(ui,'            OutlinedButton(onClick = { showCategoryManager = true }) { Text("DANH MỤC CHI TIẾT") }', '            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedButton(onClick = { showCategoryManager = true }) { Text("DANH MỤC") };OutlinedButton(onClick={showCostCodeManager=true}){Text("MÃ CHI PHÍ")}}')
    replace_once(ui,'                if(transactionType==FinancialTransactionTypes.ASSET_PURCHASE){', '''                if(FinancialTransactionTypes.usesPurchaseDocument(transactionType)){
                    Text("Mã chi phí theo dõi",Modifier.padding(top=8.dp),fontWeight=FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        FilterChip(selectedCostCodeId==null,{selectedCostCodeId=null},{Text("Không mã")})
                        costCodes.forEach{cc->FilterChip(selectedCostCodeId==cc.id,{selectedCostCodeId=cc.id;unit=cc.defaultUnit;if(itemName.isBlank())itemName=cc.name;if(supplier.isBlank())supplier=cc.defaultSupplier;expenseCategory=cc.parentExpenseCategory},{Text(cc.code)})}
                    }
                    selectedCostCodeId?.let{id->costCodes.firstOrNull{it.id==id}?.let{cc->Text("${cc.name} · ${ExpenseCategories.label(cc.parentExpenseCategory)} · ĐVT ${cc.defaultUnit}",fontSize=11.sp)}}
                }
                if(transactionType in setOf(FinancialTransactionTypes.ASSET_PURCHASE,FinancialTransactionTypes.ADDITIONAL_INVESTMENT)){''')
    replace_once(ui,'val asset=if(transactionType==FinancialTransactionTypes.ASSET_PURCHASE)selectedAssetCategory?.let{c->AssetEntity(UUID.randomUUID().toString(),itemName.trim(),c.id,parsedAt,unitPrice?:0,qty?.toInt()?.coerceAtLeast(1)?:1,total,supplier=supplier.trim(),usefulLifeMonths=usefulLifeText.toIntOrNull()?:c.defaultUsefulLifeMonths,residualValue=(residualText.toLongOrNull()?:0).coerceAtMost(total),estimatedLiquidationValue=liquidationText.toLongOrNull()?:0,note=note)} else null',
'val asset=if(transactionType in setOf(FinancialTransactionTypes.ASSET_PURCHASE,FinancialTransactionTypes.ADDITIONAL_INVESTMENT))selectedAssetCategory?.let{c->AssetEntity(UUID.randomUUID().toString(),itemName.trim(),c.id,parsedAt,unitPrice?:0,qty?.toInt()?.coerceAtLeast(1)?:1,total,supplier=supplier.trim(),usefulLifeMonths=usefulLifeText.toIntOrNull()?:c.defaultUsefulLifeMonths,residualValue=(residualText.toLongOrNull()?:0).coerceAtMost(total),estimatedLiquidationValue=liquidationText.toLongOrNull()?:0,note=note,investmentClass=if(transactionType==FinancialTransactionTypes.ADDITIONAL_INVESTMENT)"ADDITIONAL" else "INITIAL")} else null')
    replace_once(ui,'                            asset = asset,\n                            paidByName = paidByName\n                        )', '                            asset = asset,\n                            paidByName = paidByName,\n                            costCodeId = selectedCostCodeId\n                        )')
    replace_once(ui,'                        paidByName = ""\n                        invoiceImage = null', '                        paidByName = ""\n                        selectedCostCodeId = null\n                        invoiceImage = null')
    replace_once(ui,'                        (transactionType!=FinancialTransactionTypes.ASSET_PURCHASE||(selectedAssetCategory!=null&&(usefulLifeText.toIntOrNull()?:0)>0&&(residualText.toLongOrNull()?:0)<=total)) &&', '                        (transactionType !in setOf(FinancialTransactionTypes.ASSET_PURCHASE,FinancialTransactionTypes.ADDITIONAL_INVESTMENT)||(selectedAssetCategory!=null&&(usefulLifeText.toIntOrNull()?:0)>0&&(residualText.toLongOrNull()?:0)<=total)) &&')
    replace_once(ui,'    if (showCategoryManager) {\n        PurchaseCategoryManagerDialog(', '    if(showCostCodeManager){CostCodeManagerDialog(costCodes,{showCostCodeManager=false}){code,name,parent,unit0,supplier0->vm.addCostCode(code,name,parent,unit0,supplier0)}}\n\n    if (showCategoryManager) {\n        PurchaseCategoryManagerDialog(')

    new_detail=r'''@Composable
fun PurchaseDetailDialog(vm: PosViewModel, p: PurchaseEntity, onDismiss: () -> Unit) {
    val items by vm.purchaseItems(p.id).collectAsState(initial = emptyList())
    val suppliers by vm.suppliers.collectAsState();val employees by vm.employees.collectAsState();val current by vm.currentEmployee.collectAsState()
    val purchaseCategories by vm.purchaseCategories.collectAsState();val costCodes by vm.costCodes.collectAsState()
    var showDelete by remember { mutableStateOf(false) };var showEdit by remember{mutableStateOf(false)};var deleteReason by remember { mutableStateOf("") }
    val supplierName = suppliers.firstOrNull { it.id == p.supplierId }?.name ?: ""
    val enteredBy = employees.firstOrNull { it.id == p.enteredBy }?.name ?: p.enteredBy
    AlertDialog(onDismissRequest=onDismiss,confirmButton={Button(onClick=onDismiss){Text("ĐÓNG")}},dismissButton={if(current?.role=="ADMIN")TextButton(onClick={showDelete=true}){Text("XOÁ PHIẾU · ADMIN")}},title={Text("Phiếu nhập · ${time(p.purchasedAt)}")},text={LazyColumn{
        item{Text("Nhà cung cấp: ${supplierName.ifBlank{"Không ghi"}}");Text("Người nhập: $enteredBy");Text("Người chi / ứng tiền: ${p.paidByName.ifBlank{"Chưa xác định"}}",fontWeight=FontWeight.Bold);Text("Loại: ${ExpenseCategories.label(p.expenseCategory)}");Text("Mã chi phí: ${costCodes.firstOrNull{it.id==p.costCodeId}?.let{"${it.code} · ${it.name}"}?:"Không mã"}",fontSize=12.sp);if(p.updatedAt!=null)Text("Sửa gần nhất: ${time(p.updatedAt)}",fontSize=11.sp);if(current?.role=="ADMIN")Button({showEdit=true},Modifier.fillMaxWidth().padding(top=8.dp)){Text("CHỈNH SỬA PHIẾU")};if(p.note.isNotBlank())Text("Ghi chú: ${p.note}");Spacer(Modifier.height(8.dp))}
        items(items){line->Card(Modifier.fillMaxWidth().padding(vertical=3.dp)){Column(Modifier.padding(10.dp)){Text(line.name,fontWeight=FontWeight.Bold);Text("${line.qty} ${line.unit} × ${money(line.unitPrice)}");Text("= ${money(line.amount)}")}}}
        item{Text("TỔNG: ${money(p.total)}",Modifier.padding(vertical=10.dp),fontWeight=FontWeight.Black);if(!p.invoiceImageUri.isNullOrBlank()){Text("Ảnh hóa đơn",fontWeight=FontWeight.Bold);AsyncImage(model=p.invoiceImageUri,contentDescription="Ảnh hóa đơn",modifier=Modifier.fillMaxWidth().height(320.dp).padding(top=8.dp))}}
    }})
    if(showEdit&&items.isNotEmpty())PurchaseEditDialog(vm,p,items.first(),purchaseCategories,costCodes,supplierName,{showEdit=false}){showEdit=false;onDismiss()}
    if(showDelete)AlertDialog(onDismissRequest={showDelete=false},title={Text("XOÁ PHIẾU NHẬP")},text={Column{Text("Phiếu ${money(p.total)} sẽ bị loại khỏi báo cáo nhưng vẫn giữ audit.");OutlinedTextField(deleteReason,{deleteReason=it},Modifier.fillMaxWidth().padding(top=10.dp),label={Text("Lý do xoá bắt buộc")})}},confirmButton={Button(onClick={vm.deletePurchase(p,deleteReason);showDelete=false;onDismiss()},enabled=deleteReason.isNotBlank()){Text("XÁC NHẬN XOÁ")}},dismissButton={TextButton(onClick={showDelete=false}){Text("HỦY")}})
}
'''
    regex_once(ui,r'@Composable\nfun PurchaseDetailDialog\(vm: PosViewModel, p: PurchaseEntity, onDismiss: \(\) -> Unit\) \{.*?(?=\n@Composable\nfun VietQr)',new_detail.rstrip())

    new_monthly=r'''@Composable
fun MonthlyProfitReport(vm:PosViewModel){
    val bills by vm.bills.collectAsState();val payments by vm.payments.collectAsState();val purchases by vm.purchases.collectAsState();val partners by vm.profitPartners.collectAsState()
    val assets by vm.assets.collectAsState();val movements by vm.financialMovements.collectAsState();val costCodes by vm.costCodes.collectAsState();val allItems by vm.purchaseItemsAll.collectAsState()
    var monthOffset by remember{mutableIntStateOf(0)};var reimbursementPayer by remember{mutableStateOf<String?>(null)}
    val month=remember(monthOffset){Calendar.getInstance().apply{set(Calendar.DAY_OF_MONTH,1);set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0);add(Calendar.MONTH,monthOffset)}}
    val from=month.timeInMillis;val to=Calendar.getInstance().apply{timeInMillis=from;add(Calendar.MONTH,1)}.timeInMillis
    val monthBills=bills.filter{(it.closedAt?:Long.MIN_VALUE) in from until to};val billIds=monthBills.map{it.id}.toSet();val revenue=payments.filter{it.billId in billIds}.sumOf{it.amount}
    val monthPurchases=purchases.filter{it.purchasedAt in from until to}
    val initialCodes=setOf(ExpenseCategories.SETUP_COST,ExpenseCategories.INITIAL_INVESTMENT_SUNK,ExpenseCategories.CAPITAL_ASSET)
    val additional=monthPurchases.filter{it.expenseCategory==ExpenseCategories.ADDITIONAL_INVESTMENT}.sumOf{it.total}
    val operating=monthPurchases.filter{it.expenseCategory !in initialCodes&&it.expenseCategory!=ExpenseCategories.ADDITIONAL_INVESTMENT}.sumOf{it.total}
    val activeAssets=assets.filter{it.status=="ACTIVE"||it.status=="DAMAGED"||it.status=="TRANSFERRED"}
    fun assetValue(a:AssetEntity):AssetValue{val p=Calendar.getInstance().apply{timeInMillis=a.purchaseDate};val used=((month.get(Calendar.YEAR)-p.get(Calendar.YEAR))*12+month.get(Calendar.MONTH)-p.get(Calendar.MONTH)+1).coerceAtLeast(0);return calculateAssetValue(a.totalCost,a.residualValue,a.usefulLifeMonths,used)}
    val depreciation=activeAssets.filter{it.purchaseDate<to}.sumOf{assetValue(it).monthlyDepreciation}
    val result=calculateFinancialPeriod(FinancialPeriodInput(revenue,operating,additional,depreciation))
    val shares=allocatePositiveCash(result.netCashResult,partners.map{ProfitShareInput(it.id,it.name,it.shareBasisPoints)})
    val reimbursements=movements.filter{it.type=="PAYER_REIMBURSEMENT"&&it.counterpartyName.isNotBlank()}.map{PayerReimbursement(it.counterpartyName,it.amount)}
    val payerLedger=calculatePayerLedger(purchases.filter{it.paidByName.isNotBlank()}.map{PayerAdvance(it.paidByName,it.total)},reimbursements)
    val initialSunk=purchases.filter{it.expenseCategory in setOf(ExpenseCategories.SETUP_COST,ExpenseCategories.INITIAL_INVESTMENT_SUNK)}.sumOf{it.total};val initialAssets=assets.filter{it.investmentClass=="INITIAL"}.sumOf{it.totalCost};val initialInvestment=initialSunk+initialAssets
    val allBillIds=bills.map{it.id}.toSet();val allRevenue=payments.filter{it.billId in allBillIds}.sumOf{it.amount};val allOperating=purchases.filter{it.expenseCategory !in initialCodes}.sumOf{it.total};val cumulativeNet=allRevenue-allOperating;val payback=calculateCashPayback(initialInvestment,cumulativeNet)
    fun periodResult(start:Long,end:Long):FinancialPeriodResult{val ids=bills.filter{(it.closedAt?:Long.MIN_VALUE) in start until end}.map{it.id}.toSet();val rev=payments.filter{it.billId in ids}.sumOf{it.amount};val ps=purchases.filter{it.purchasedAt in start until end};val add=ps.filter{it.expenseCategory==ExpenseCategories.ADDITIONAL_INVESTMENT}.sumOf{it.total};val op=ps.filter{it.expenseCategory !in initialCodes&&it.expenseCategory!=ExpenseCategories.ADDITIONAL_INVESTMENT}.sumOf{it.total};return calculateFinancialPeriod(FinancialPeriodInput(rev,op,add,0))}
    val quarterStart=Calendar.getInstance().apply{timeInMillis=from;set(Calendar.MONTH,(get(Calendar.MONTH)/3)*3);set(Calendar.DAY_OF_MONTH,1)}.timeInMillis;val yearStart=Calendar.getInstance().apply{timeInMillis=from;set(Calendar.MONTH,0);set(Calendar.DAY_OF_MONTH,1)}.timeInMillis
    val quarter=periodResult(quarterStart,to);val year=periodResult(yearStart,to)
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)){
      item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){OutlinedButton({monthOffset--}){Text("‹")};Text(SimpleDateFormat("MM / yyyy",Locale.getDefault()).format(Date(from)),Modifier.weight(1f),textAlign=TextAlign.Center,fontWeight=FontWeight.Black,fontSize=20.sp);OutlinedButton({monthOffset++}){Text("›")}}}
      item{Text("KẾT QUẢ TIỀN THÁNG",Modifier.padding(top=12.dp,bottom=4.dp),fontWeight=FontWeight.Black,fontSize=18.sp);AccountingCard(listOf("Tiền thu bán hàng" to revenue,"Chi vận hành" to -operating,"DƯ TRƯỚC ĐẦU TƯ BỔ SUNG" to result.operatingResultBeforeAdditionalInvestment,"Đầu tư bổ sung" to -additional,"DÒNG TIỀN RÒNG THÁNG" to result.netCashResult,"Khấu hao tham khảo" to -depreciation));Text("Khấu hao chỉ để theo dõi tài sản, không trừ lần hai khỏi tiền chia.",Modifier.padding(8.dp),fontSize=12.sp,fontWeight=FontWeight.Bold)}
      item{Text("CHIA TIỀN",Modifier.padding(top=10.dp),fontWeight=FontWeight.Black,fontSize=18.sp);if(result.netCashResult<=0)Text("Tháng này không có số dư dương để chia.",Modifier.padding(8.dp),fontWeight=FontWeight.Bold) else if(shares.isEmpty())Text("Cần cấu hình tỷ lệ người chia đủ 100%.",Modifier.padding(8.dp),color=Color(0xFF9A4B3D),fontWeight=FontWeight.Bold)}
      items(shares){p->MetricCard("${p.name} · ${p.shareBasisPoints/100.0}%",money(p.amount))}
      item{Text("CÔNG NỢ NGƯỜI ỨNG",Modifier.padding(top=12.dp,bottom=4.dp),fontWeight=FontWeight.Black,fontSize=18.sp)}
      if(payerLedger.isEmpty())item{Text("Chưa có khoản người cá nhân ứng tiền.",Modifier.padding(8.dp))} else items(payerLedger){row->Card(Modifier.fillMaxWidth().padding(vertical=3.dp)){Column(Modifier.padding(12.dp)){Text(row.payer,fontWeight=FontWeight.Black);Text("Đã ứng ${money(row.advanced)} · Đã hoàn ${money(row.reimbursed)}");Text("Quán còn nợ ${money(row.outstanding)}",fontWeight=FontWeight.Bold);if(row.outstanding>0)OutlinedButton({reimbursementPayer=row.payer},Modifier.fillMaxWidth().padding(top=5.dp)){Text("GHI HOÀN ỨNG")}}}}
      item{Text("ĐẦU TƯ & HOÀN VỐN",Modifier.padding(top=12.dp,bottom=4.dp),fontWeight=FontWeight.Black,fontSize=18.sp);AccountingCard(listOf("Đầu tư ban đầu" to initialInvestment,"Dòng tiền kinh doanh tích lũy" to cumulativeNet,"Còn để hoàn vốn" to payback.remaining));Text("Tiến độ hoàn vốn: ${payback.paybackBasisPoints/100.0}%",Modifier.padding(8.dp),fontWeight=FontWeight.Bold)}
      item{Text("TỔNG KẾT QUÝ / NĂM",Modifier.padding(top=12.dp,bottom=4.dp),fontWeight=FontWeight.Black,fontSize=18.sp);AccountingCard(listOf("Dòng tiền quý hiện tại" to quarter.netCashResult,"Đầu tư bổ sung quý" to -quarter.additionalInvestment,"Dòng tiền năm hiện tại" to year.netCashResult,"Đầu tư bổ sung năm" to -year.additionalInvestment))}
      item{Text("BIẾN ĐỘNG MÃ CHI PHÍ",Modifier.padding(top=12.dp,bottom=4.dp),fontWeight=FontWeight.Black,fontSize=18.sp)}
      items(costCodes){cc->val rows=purchases.filter{it.costCodeId==cc.id}.sortedBy{it.purchasedAt};val latest=rows.lastOrNull();val prev=if(rows.size>1)rows[rows.lastIndex-1] else null;val latestItem=latest?.let{p->allItems.firstOrNull{it.purchaseId==p.id}};val prevItem=prev?.let{p->allItems.firstOrNull{it.purchaseId==p.id}};val monthSpend=monthPurchases.filter{it.costCodeId==cc.id}.sumOf{it.total};Card(Modifier.fillMaxWidth().padding(vertical=3.dp)){Column(Modifier.padding(12.dp)){Text("${cc.code} · ${cc.name}",fontWeight=FontWeight.Black);Text("Chi tháng: ${money(monthSpend)}");if(latestItem!=null)Text("Lần gần nhất: ${latestItem.qty} ${latestItem.unit} × ${money(latestItem.unitPrice)}",fontSize=12.sp);if(latestItem!=null&&prevItem!=null){val v=calculateCostVariance(CostObservation(prevItem.qty,prevItem.unitPrice),CostObservation(latestItem.qty,latestItem.unitPrice));Text("Đơn giá ${"%+.1f".format(v.unitPriceChangePct)}% · Sử dụng ${"%+.1f".format(v.quantityChangePct)}%",fontSize=12.sp,fontWeight=FontWeight.Bold)}}}}
    }
    reimbursementPayer?.let{payer->PayerReimbursementDialog(payer,{reimbursementPayer=null}){amount,method,note->vm.reimbursePayer(payer,amount,method,note);reimbursementPayer=null}}
}
'''
    regex_once(ui,r'@Composable\nfun MonthlyProfitReport\(vm:PosViewModel\)\{.*?(?=\n@Composable\nfun )',new_monthly.rstrip())
    replace_once(ui,'POS0210 v1.0.0-alpha52-candidate15 · versionCode 76','POS0210 v1.0.0-alpha52-candidate16 · versionCode 77')

    write('POS0210/app/src/main/java/vn/ecohome/pos0210/FinanceDialogs.kt',r'''package vn.ecohome.pos0210
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.ecohome.pos0210.data.*
import java.text.SimpleDateFormat
import java.util.*

@Composable fun CostCodeManagerDialog(codes:List<CostCodeEntity>,onDismiss:()->Unit,onAdd:(String,String,String,String,String)->Unit){
 var code by remember{mutableStateOf("")};var name by remember{mutableStateOf("")};var unit by remember{mutableStateOf("lần")};var supplier by remember{mutableStateOf("")};var parent by remember{mutableStateOf(ExpenseCategories.OTHER_EXPENSE)}
 AlertDialog(onDismissRequest=onDismiss,confirmButton={TextButton(onClick=onDismiss){Text("ĐÓNG")}},title={Text("Mã chi phí cố định")},text={Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())){
  OutlinedTextField(code,{code=it.uppercase().take(24)},Modifier.fillMaxWidth(),label={Text("Mã, ví dụ DV-DIEN")});OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("Tên chi phí")});OutlinedTextField(unit,{unit=it},Modifier.fillMaxWidth(),label={Text("Đơn vị mặc định")});OutlinedTextField(supplier,{supplier=it},Modifier.fillMaxWidth(),label={Text("Nhà cung cấp mặc định")})
  Text("Nhóm báo cáo",Modifier.padding(top=8.dp),fontWeight=FontWeight.Bold);Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf(ExpenseCategories.INVENTORY_PURCHASE,ExpenseCategories.PAYROLL,ExpenseCategories.ELECTRICITY,ExpenseCategories.WATER,ExpenseCategories.RENT,ExpenseCategories.MARKETING,ExpenseCategories.CONSUMABLES,ExpenseCategories.MAINTENANCE,ExpenseCategories.SERVICES,ExpenseCategories.BANK_FEES,ExpenseCategories.OTHER_EXPENSE,ExpenseCategories.ADDITIONAL_INVESTMENT).forEach{v->FilterChip(parent==v,{parent=v},{Text(ExpenseCategories.label(v))})}}
  Button({onAdd(code,name,parent,unit,supplier);code="";name=""},Modifier.fillMaxWidth().padding(top=8.dp),enabled=code.isNotBlank()&&name.isNotBlank()){Text("LƯU MÃ CHI PHÍ")};codes.forEach{Text("${it.code} · ${it.name} · ${it.defaultUnit}",Modifier.padding(top=6.dp),fontSize=androidx.compose.ui.unit.TextUnit.Unspecified)}
 }})
}

@Composable fun PurchaseEditDialog(vm:PosViewModel,p:PurchaseEntity,line:PurchaseItemEntity,categories:List<PurchaseCategoryEntity>,costCodes:List<CostCodeEntity>,supplierInitial:String,onDismiss:()->Unit,onSaved:()->Unit){
 var payer by remember{mutableStateOf(p.paidByName)};var supplier by remember{mutableStateOf(supplierInitial)};var note by remember{mutableStateOf(p.note)};var date by remember{mutableStateOf(SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(Date(p.purchasedAt)))};var qty by remember{mutableStateOf(line.qty.toString())};var unit by remember{mutableStateOf(line.unit)};var price by remember{mutableStateOf(line.unitPrice.toString())};var expense by remember{mutableStateOf(p.expenseCategory)};var detail by remember{mutableStateOf(line.categoryId)};var costCode by remember{mutableStateOf(p.costCodeId)}
 AlertDialog(onDismissRequest=onDismiss,confirmButton={Button(onClick={val at=runCatching{SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).parse(date)?.time}.getOrNull()?:p.purchasedAt;vm.updatePurchaseFinancial(p,line,expense,detail,costCode,payer,supplier,note,at,qty.replace(',','.').toDoubleOrNull()?:0.0,unit,price.toLongOrNull()?:0);onSaved()},enabled=(qty.replace(',','.').toDoubleOrNull()?:0.0)>0&&(price.toLongOrNull()?:0)>0){Text("LƯU SỬA")}},dismissButton={TextButton(onClick=onDismiss){Text("HỦY")}},title={Text("Chỉnh sửa phiếu")},text={Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())){
  OutlinedTextField(date,{date=it},Modifier.fillMaxWidth(),label={Text("Ngày giờ")});OutlinedTextField(payer,{payer=it},Modifier.fillMaxWidth(),label={Text("Người chi / ứng tiền")});OutlinedTextField(supplier,{supplier=it},Modifier.fillMaxWidth(),label={Text("Nhà cung cấp")});OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text("Ghi chú")})
  Text("Nhóm báo cáo",Modifier.padding(top=8.dp),fontWeight=FontWeight.Bold);Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){ExpenseCategories.all.forEach{v->FilterChip(expense==v,{expense=v},{Text(ExpenseCategories.label(v))})}}
  Text("Phân mục",Modifier.padding(top=8.dp),fontWeight=FontWeight.Bold);Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){categories.forEach{c->FilterChip(detail==c.id,{detail=c.id;unit=c.defaultUnit},{Text(c.name)})}}
  Text("Mã chi phí",Modifier.padding(top=8.dp),fontWeight=FontWeight.Bold);Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){FilterChip(costCode==null,{costCode=null},{Text("Không mã")});costCodes.forEach{cc->FilterChip(costCode==cc.id,{costCode=cc.id;expense=cc.parentExpenseCategory;unit=cc.defaultUnit},{Text(cc.code)})}}
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(qty,{qty=it.filter{c->c.isDigit()||c=='.'||c==','}},Modifier.weight(1f),label={Text("Số lượng")});OutlinedTextField(unit,{unit=it},Modifier.weight(1f),label={Text("ĐVT")})};OutlinedTextField(price,{price=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("Đơn giá")})
 }})
}

@Composable fun PayerReimbursementDialog(payer:String,onDismiss:()->Unit,onSave:(Long,String,String)->Unit){
 var amount by remember{mutableStateOf("")};var method by remember{mutableStateOf("CASH")};var note by remember{mutableStateOf("")}
 AlertDialog(onDismissRequest=onDismiss,confirmButton={Button(onClick={onSave(amount.toLongOrNull()?:0,method,note)},enabled=(amount.toLongOrNull()?:0)>0){Text("GHI HOÀN ỨNG")}},dismissButton={TextButton(onClick=onDismiss){Text("HỦY")}},title={Text("Hoàn ứng · $payer")},text={Column{OutlinedTextField(amount,{amount=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("Số tiền")});Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){FilterChip(method=="CASH",{method="CASH"},{Text("Tiền mặt")});FilterChip(method=="TRANSFER",{method="TRANSFER"},{Text("Chuyển khoản")})};OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text("Ghi chú")});Text("Hoàn ứng chỉ giảm công nợ, không tạo thêm chi phí.",Modifier.padding(top=6.dp),fontWeight=FontWeight.Bold)}})
}
''')

    cloud='POS0210/app/src/main/java/vn/ecohome/pos0210/cloud/FirebaseCloudSync.kt'
    replace_once(cloud,'"expenseCategory" to p.expenseCategory,"paidByName" to p.paidByName)', '"expenseCategory" to p.expenseCategory,"paidByName" to p.paidByName,"costCodeId" to p.costCodeId,"updatedAt" to p.updatedAt)')
    replace_once(cloud,'"disposalDate" to a.disposalDate,"disposalPrice" to a.disposalPrice)', '"disposalDate" to a.disposalDate,"disposalPrice" to a.disposalPrice,"investmentClass" to a.investmentClass)')
    replace_once(cloud,'"partnerId" to m.partnerId,"method" to m.method,"note" to m.note)', '"partnerId" to m.partnerId,"method" to m.method,"note" to m.note,"counterpartyName" to m.counterpartyName)')
    replace_once(cloud,'        writeMaps(fs,root.collection("monthlyAccounting")', '        writeMaps(fs,root.collection("costCodes"),dao.allCostCodesSnapshot().map{c0->c0.id to mapOf("id" to c0.id,"code" to c0.code,"name" to c0.name,"parentExpenseCategory" to c0.parentExpenseCategory,"defaultUnit" to c0.defaultUnit,"defaultSupplier" to c0.defaultSupplier,"referenceUnitPrice" to c0.referenceUnitPrice,"sortOrder" to c0.sortOrder,"active" to c0.active)})\n        writeMaps(fs,root.collection("monthlyAccounting")')

    replace_once('POS0210/app/build.gradle.kts','        versionCode = 76\n        versionName = "1.0.0-alpha52-candidate15"','        versionCode = 77\n        versionName = "1.0.0-alpha52-candidate16"')

if __name__=='__main__':
 ap=argparse.ArgumentParser();ap.add_argument('--write-tests',action='store_true');ap.add_argument('--apply',action='store_true');args=ap.parse_args()
 if args.write_tests:write_tests()
 if args.apply:apply_patch()
