from pathlib import Path
import argparse

ROOT = Path(__file__).resolve().parents[1]

def replace_once(rel, old, new):
    p = ROOT / rel
    s = p.read_text()
    n = s.count(old)
    if n != 1:
        raise SystemExit(f"{rel}: expected exactly one match, got {n}: {old[:120]!r}")
    p.write_text(s.replace(old, new, 1))

def write_test():
    p = ROOT / 'POS0210/app/src/test/java/vn/ecohome/pos0210/PurchasePayerTrackingTest.kt'
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text('''package vn.ecohome.pos0210
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import vn.ecohome.pos0210.data.PurchaseEntity

class PurchasePayerTrackingTest {
    @Test fun existingPurchaseDefaultsPayerToBlank() {
        val purchase = PurchaseEntity("p", null, "employee", 1L, 100L)
        val field = runCatching { PurchaseEntity::class.java.getDeclaredField("paidByName") }.getOrNull()
        assertNotNull("PurchaseEntity must store the actual payer independently from enteredBy", field)
        field!!.isAccessible = true
        assertEquals("", field.get(purchase))
    }
}
''')

def apply_patch():
    replace_once(
        'POS0210/app/src/main/java/vn/ecohome/pos0210/data/Entities.kt',
        '@Entity(indices=[Index("supplierId"),Index("status"),Index("expenseCategory")]) data class PurchaseEntity(@PrimaryKey val id:String,val supplierId:String?,val enteredBy:String,val purchasedAt:Long,val total:Long,val note:String="",val invoiceImageUri:String?=null,val status:String="ACTIVE",val expenseCategory:String="UNCLASSIFIED")',
        '@Entity(indices=[Index("supplierId"),Index("status"),Index("expenseCategory")]) data class PurchaseEntity(@PrimaryKey val id:String,val supplierId:String?,val enteredBy:String,val purchasedAt:Long,val total:Long,val note:String="",val invoiceImageUri:String?=null,val status:String="ACTIVE",val expenseCategory:String="UNCLASSIFIED",val paidByName:String="")'
    )
    replace_once(
        'POS0210/app/src/main/java/vn/ecohome/pos0210/data/Entities.kt',
        'data class PurchaseCostRow(val categoryId:String,val amount:Long,val purchasedAt:Long)',
        'data class PurchaseCostRow(val purchaseId:String,val categoryId:String,val amount:Long,val purchasedAt:Long)'
    )
    replace_once(
        'POS0210/app/src/main/java/vn/ecohome/pos0210/data/PosDao.kt',
        '@Query("SELECT pi.categoryId AS categoryId, pi.amount AS amount, p.purchasedAt AS purchasedAt FROM PurchaseItemEntity pi INNER JOIN PurchaseEntity p ON p.id=pi.purchaseId WHERE p.status=\'ACTIVE\'") fun purchaseCosts():Flow<List<PurchaseCostRow>>',
        '@Query("SELECT pi.purchaseId AS purchaseId, pi.categoryId AS categoryId, pi.amount AS amount, p.purchasedAt AS purchasedAt FROM PurchaseItemEntity pi INNER JOIN PurchaseEntity p ON p.id=pi.purchaseId WHERE p.status=\'ACTIVE\'") fun purchaseCosts():Flow<List<PurchaseCostRow>>'
    )
    replace_once(
        'POS0210/app/src/main/java/vn/ecohome/pos0210/data/PosDatabase.kt',
        'OpeningCashAdjustmentEntity::class,CloudSyncStateEntity::class],version=17,exportSchema=false)',
        'OpeningCashAdjustmentEntity::class,CloudSyncStateEntity::class],version=18,exportSchema=false)'
    )
    replace_once(
        'POS0210/app/src/main/java/vn/ecohome/pos0210/data/PosDatabase.kt',
        '''  private val MIGRATION_16_17=object:Migration(16,17){
   override fun migrate(db:SupportSQLiteDatabase){
    db.execSQL("CREATE TABLE IF NOT EXISTS CloudSyncStateEntity (id TEXT NOT NULL, enabled INTEGER NOT NULL, dirty INTEGER NOT NULL, lastAttemptAt INTEGER, lastSuccessAt INTEGER, lastError TEXT, syncedUid TEXT, PRIMARY KEY(id))")
    db.execSQL("INSERT OR IGNORE INTO CloudSyncStateEntity(id,enabled,dirty) VALUES('firebase',0,1)")
   }
  }
''',
        '''  private val MIGRATION_16_17=object:Migration(16,17){
   override fun migrate(db:SupportSQLiteDatabase){
    db.execSQL("CREATE TABLE IF NOT EXISTS CloudSyncStateEntity (id TEXT NOT NULL, enabled INTEGER NOT NULL, dirty INTEGER NOT NULL, lastAttemptAt INTEGER, lastSuccessAt INTEGER, lastError TEXT, syncedUid TEXT, PRIMARY KEY(id))")
    db.execSQL("INSERT OR IGNORE INTO CloudSyncStateEntity(id,enabled,dirty) VALUES('firebase',0,1)")
   }
  }
  private val MIGRATION_17_18=object:Migration(17,18){
   override fun migrate(db:SupportSQLiteDatabase){
    db.execSQL("ALTER TABLE PurchaseEntity ADD COLUMN paidByName TEXT NOT NULL DEFAULT ''")
   }
  }
'''
    )
    replace_once(
        'POS0210/app/src/main/java/vn/ecohome/pos0210/data/PosDatabase.kt',
        '.addMigrations(MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6,MIGRATION_6_7,MIGRATION_7_8,MIGRATION_8_9,MIGRATION_9_10,MIGRATION_10_11,MIGRATION_11_12,MIGRATION_12_13,MIGRATION_13_14,MIGRATION_14_15,MIGRATION_15_16,MIGRATION_16_17)',
        '.addMigrations(MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6,MIGRATION_6_7,MIGRATION_7_8,MIGRATION_8_9,MIGRATION_9_10,MIGRATION_10_11,MIGRATION_11_12,MIGRATION_12_13,MIGRATION_13_14,MIGRATION_14_15,MIGRATION_15_16,MIGRATION_16_17,MIGRATION_17_18)'
    )
    replace_once(
        'POS0210/app/src/main/java/vn/ecohome/pos0210/PosViewModel.kt',
        'fun addPurchaseDetailed(name:String,qty:Double,unit:String,unitPrice:Long,note:String,at:Long=System.currentTimeMillis(),supplierName:String="",imageUri:String?=null,categoryId:String="pc_production",expenseCategory:String="UNCLASSIFIED",asset:AssetEntity?=null){',
        'fun addPurchaseDetailed(name:String,qty:Double,unit:String,unitPrice:Long,note:String,at:Long=System.currentTimeMillis(),supplierName:String="",imageUri:String?=null,categoryId:String="pc_production",expenseCategory:String="UNCLASSIFIED",asset:AssetEntity?=null,paidByName:String=""){'
    )
    replace_once(
        'POS0210/app/src/main/java/vn/ecohome/pos0210/PosViewModel.kt',
        'PurchaseEntity(id,supplierId,e.id,at,amount,note,managed,expenseCategory=expenseCategory),',
        'PurchaseEntity(id,supplierId,e.id,at,amount,note,managed,expenseCategory=expenseCategory,paidByName=paidByName.trim()),'
    )
    replace_once(
        'POS0210/app/src/main/java/vn/ecohome/pos0210/PosViewModel.kt',
        'audit("PURCHASE",id,"CREATE","${name.trim()}:$qty:$unit:$unitPrice:$amount");autoBackup();autoBackupMedia()',
        'audit("PURCHASE",id,"CREATE","${name.trim()}:$qty:$unit:$unitPrice:$amount:payer=${paidByName.trim()}");autoBackup();autoBackupMedia()'
    )
    replace_once(
        'POS0210/app/src/main/java/vn/ecohome/pos0210/cloud/FirebaseCloudSync.kt',
        '"status" to p.status,"expenseCategory" to p.expenseCategory)',
        '"status" to p.status,"expenseCategory" to p.expenseCategory,"paidByName" to p.paidByName)'
    )
    main='POS0210/app/src/main/java/vn/ecohome/pos0210/MainActivity.kt'
    replace_once(main,
        '    var supplier by remember { mutableStateOf("") }\n    var note by remember { mutableStateOf("") }',
        '    var supplier by remember { mutableStateOf("") }\n    var paidByName by remember { mutableStateOf("") }\n    var note by remember { mutableStateOf("") }')
    replace_once(main,
        '''                OutlinedTextField(
                    itemName,
                    { itemName = it },''',
        '''                if(FinancialTransactionTypes.usesPurchaseDocument(transactionType)) OutlinedTextField(
                    paidByName,
                    { paidByName = it.take(60) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Người chi / ứng tiền (không bắt buộc)") },
                    supportingText = { Text("Người thực tế bỏ tiền, độc lập với người nhập và cổ đông") }
                )
                OutlinedTextField(
                    itemName,
                    { itemName = it },''')
    replace_once(main,
        '''                            expenseCategory = FinancialTransactionTypes.legacyExpenseCode(transactionType,expenseCategory),
                            asset = asset
                        )''',
        '''                            expenseCategory = FinancialTransactionTypes.legacyExpenseCode(transactionType,expenseCategory),
                            asset = asset,
                            paidByName = paidByName
                        )''')
    replace_once(main,
        '                        note = ""\n                        invoiceImage = null',
        '                        note = ""\n                        paidByName = ""\n                        invoiceImage = null')
    replace_once(main,
        '                        Text("${money(p.total)} · ${p.note.ifBlank { "Không ghi chú" }}")\n                        Text(if (!p.invoiceImageUri.isNullOrBlank()) "📷 Có ảnh hóa đơn · Chạm để xem" else "Chạm để xem chi tiết", fontSize = 12.sp)',
        '                        Text("${money(p.total)} · ${p.note.ifBlank { "Không ghi chú" }}")\n                        if(p.paidByName.isNotBlank()) Text("Người chi: ${p.paidByName}", fontSize = 12.sp, fontWeight = FontWeight.Bold)\n                        Text(if (!p.invoiceImageUri.isNullOrBlank()) "📷 Có ảnh hóa đơn · Chạm để xem" else "Chạm để xem chi tiết", fontSize = 12.sp)')
    replace_once(main,
        '                    Text("Người nhập: $enteredBy")\n                    Text("Loại giao dịch:',
        '                    Text("Người nhập: $enteredBy")\n                    Text("Người chi / ứng tiền: ${p.paidByName.ifBlank { "Chưa xác định" }}", fontWeight = FontWeight.Bold)\n                    Text("Loại giao dịch:')
    replace_once(main,
        '    var paymentFilter by remember { mutableStateOf("ALL") }\n    var historyDateText by remember { mutableStateOf("") }',
        '    var paymentFilter by remember { mutableStateOf("ALL") }\n    var payerFilter by remember { mutableStateOf("ALL") }\n    var historyDateText by remember { mutableStateOf("") }')
    replace_once(main,
        '    val filteredPurchases = if (from == null) purchases else purchases.filter { it.purchasedAt >= from }\n    val billIds = filteredBills.map { it.id }.toSet()',
        '''    val periodPurchases = if (from == null) purchases else purchases.filter { it.purchasedAt >= from }
    val payerNames = periodPurchases.map { it.paidByName.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    val hasUnknownPayer = periodPurchases.any { it.paidByName.isBlank() }
    val filteredPurchases = periodPurchases.filter { p ->
        when (payerFilter) {
            "ALL" -> true
            "UNKNOWN" -> p.paidByName.isBlank()
            else -> p.paidByName.trim() == payerFilter
        }
    }
    val filteredPurchaseIds = filteredPurchases.map { it.id }.toSet()
    val billIds = filteredBills.map { it.id }.toSet()''')
    replace_once(main,
        '    val categorizedCosts = purchaseCosts\n        .filter { from == null || it.purchasedAt >= from }',
        '    val categorizedCosts = purchaseCosts\n        .filter { it.purchaseId in filteredPurchaseIds }')
    replace_once(main,
        '''        when (section) {
            "MONTHLY" -> MonthlyProfitReport(vm)''',
        '''        if(section == "PURCHASES") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                FilterChip(payerFilter == "ALL", { payerFilter = "ALL" }, { Text("Tất cả người chi") })
                payerNames.forEach { name -> FilterChip(payerFilter == name, { payerFilter = name }, { Text(name) }) }
                if(hasUnknownPayer) FilterChip(payerFilter == "UNKNOWN", { payerFilter = "UNKNOWN" }, { Text("Chưa xác định") })
            }
        }
        when (section) {
            "MONTHLY" -> MonthlyProfitReport(vm)''')
    replace_once(main,
        '                        item { Text("Tổng chi phí đầu vào: ${money(purchaseTotal)}", fontSize = 22.sp, fontWeight = FontWeight.Bold) }',
        '''                        item { Text("Tổng chi phí đầu vào: ${money(purchaseTotal)}", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        item {
                            val summary = filteredPurchases.groupBy { it.paidByName.trim().ifBlank { "Chưa xác định" } }
                                .mapValues { (_, rows) -> rows.sumOf { it.total } }
                                .toList().sortedByDescending { it.second }
                            if(summary.isNotEmpty()) {
                                Text("Theo người chi / ứng tiền", Modifier.padding(top = 8.dp, bottom = 3.dp), fontWeight = FontWeight.Bold)
                                summary.forEach { (name, amount) -> Text("$name · ${money(amount)}", fontSize = 12.sp) }
                            }
                        }''')
    replace_once(main,
        '                                    Text(money(p.total))\n                                    Text(if (!p.invoiceImageUri.isNullOrBlank())',
        '                                    Text(money(p.total))\n                                    Text("Người chi: ${p.paidByName.ifBlank { "Chưa xác định" }}", fontSize = 12.sp, fontWeight = FontWeight.Bold)\n                                    Text(if (!p.invoiceImageUri.isNullOrBlank())')
    replace_once('POS0210/app/build.gradle.kts',
        '        versionCode = 75\n        versionName = "1.0.0-alpha52-candidate14"',
        '        versionCode = 76\n        versionName = "1.0.0-alpha52-candidate15"')
    replace_once(main,
        'POS0210 v1.0.0-alpha52-candidate14 · versionCode 75',
        'POS0210 v1.0.0-alpha52-candidate15 · versionCode 76')

if __name__ == '__main__':
    ap=argparse.ArgumentParser()
    ap.add_argument('--write-test', action='store_true')
    ap.add_argument('--apply', action='store_true')
    args=ap.parse_args()
    if args.write_test:
        write_test()
    if args.apply:
        apply_patch()
