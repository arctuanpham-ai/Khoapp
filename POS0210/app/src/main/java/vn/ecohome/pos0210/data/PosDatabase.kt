package vn.ecohome.pos0210.data
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
@Database(entities=[EmployeeEntity::class,AreaEntity::class,DiningTableEntity::class,MenuCategoryEntity::class,ComboEntity::class,ComboItemEntity::class,MenuItemEntity::class,TableSessionEntity::class,OrderBatchEntity::class,OrderItemEntity::class,BillEntity::class,PaymentEntity::class,PricingRuleEntity::class,BillAdjustmentEntity::class,SupplierEntity::class,PurchaseEntity::class,PurchaseCategoryEntity::class,PurchaseItemEntity::class,PrintJobEntity::class,AuditEventEntity::class,AppSettingEntity::class],version=6,exportSchema=false)
abstract class PosDatabase:RoomDatabase(){
 abstract fun dao():PosDao
 companion object{
  @Volatile private var instance:PosDatabase?=null
  private val MIGRATION_3_4=object:Migration(3,4){
   override fun migrate(db:SupportSQLiteDatabase){
    db.execSQL("CREATE TABLE IF NOT EXISTS PurchaseCategoryEntity (id TEXT NOT NULL, name TEXT NOT NULL, defaultUnit TEXT NOT NULL, sortOrder INTEGER NOT NULL, active INTEGER NOT NULL, PRIMARY KEY(id))")
    db.execSQL("INSERT OR IGNORE INTO PurchaseCategoryEntity(id,name,defaultUnit,sortOrder,active) VALUES('pc_salary','Lương','ngày công',0,1)")
    db.execSQL("INSERT OR IGNORE INTO PurchaseCategoryEntity(id,name,defaultUnit,sortOrder,active) VALUES('pc_fixed','Vật tư cố định','cái',1,1)")
    db.execSQL("INSERT OR IGNORE INTO PurchaseCategoryEntity(id,name,defaultUnit,sortOrder,active) VALUES('pc_production','Vật tư sản xuất','kg',2,1)")
    db.execSQL("ALTER TABLE PurchaseItemEntity ADD COLUMN categoryId TEXT NOT NULL DEFAULT 'pc_production'")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_PurchaseItemEntity_categoryId ON PurchaseItemEntity(categoryId)")
   }
  }
  private val MIGRATION_4_5=object:Migration(4,5){
   override fun migrate(db:SupportSQLiteDatabase){
    db.execSQL("ALTER TABLE PurchaseEntity ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_PurchaseEntity_status ON PurchaseEntity(status)")
   }
  }
  private val MIGRATION_5_6=object:Migration(5,6){
   override fun migrate(db:SupportSQLiteDatabase){
    db.execSQL("CREATE TABLE IF NOT EXISTS ComboEntity (id TEXT NOT NULL, name TEXT NOT NULL, price INTEGER NOT NULL, imageUri TEXT, sortOrder INTEGER NOT NULL, active INTEGER NOT NULL, PRIMARY KEY(id))")
    db.execSQL("CREATE TABLE IF NOT EXISTS ComboItemEntity (id TEXT NOT NULL, comboId TEXT NOT NULL, menuItemId TEXT NOT NULL, qty INTEGER NOT NULL, PRIMARY KEY(id))")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_ComboItemEntity_comboId ON ComboItemEntity(comboId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_ComboItemEntity_menuItemId ON ComboItemEntity(menuItemId)")
    db.execSQL("CREATE TABLE IF NOT EXISTS PricingRuleEntity (id TEXT NOT NULL, name TEXT NOT NULL, code TEXT NOT NULL, kind TEXT NOT NULL, percent INTEGER NOT NULL, startAt INTEGER, endAt INTEGER, startMinute INTEGER, endMinute INTEGER, autoApply INTEGER NOT NULL, active INTEGER NOT NULL, PRIMARY KEY(id))")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_PricingRuleEntity_code ON PricingRuleEntity(code)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_PricingRuleEntity_active ON PricingRuleEntity(active)")
    db.execSQL("CREATE TABLE IF NOT EXISTS BillAdjustmentEntity (id TEXT NOT NULL, billId TEXT NOT NULL, ruleId TEXT, name TEXT NOT NULL, kind TEXT NOT NULL, percent INTEGER NOT NULL, amount INTEGER NOT NULL, code TEXT NOT NULL, appliedAt INTEGER NOT NULL, actorId TEXT, PRIMARY KEY(id))")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_BillAdjustmentEntity_billId ON BillAdjustmentEntity(billId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_BillAdjustmentEntity_ruleId ON BillAdjustmentEntity(ruleId)")
   }
  }
  fun get(context:Context):PosDatabase=instance?:synchronized(this){
   instance?:Room.databaseBuilder(context.applicationContext,PosDatabase::class.java,"pos0210.db")
    .addMigrations(MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6)
    .build().also{instance=it}
  }
  fun closeForRestore(){synchronized(this){instance?.close();instance=null}}
 }
}
