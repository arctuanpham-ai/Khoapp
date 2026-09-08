package vn.ecohome.pos0210.data
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
@Database(entities=[EmployeeEntity::class,AreaEntity::class,DiningTableEntity::class,MenuCategoryEntity::class,MenuItemEntity::class,TableSessionEntity::class,OrderBatchEntity::class,OrderItemEntity::class,BillEntity::class,PaymentEntity::class,SupplierEntity::class,PurchaseEntity::class,PurchaseItemEntity::class,PrintJobEntity::class,AuditEventEntity::class,AppSettingEntity::class],version=3,exportSchema=false)
abstract class PosDatabase:RoomDatabase(){
 abstract fun dao():PosDao
 companion object{
  @Volatile private var instance:PosDatabase?=null
  fun get(context:Context):PosDatabase=instance?:synchronized(this){
   instance?:Room.databaseBuilder(context.applicationContext,PosDatabase::class.java,"pos0210.db").build().also{instance=it}
  }
  fun closeForRestore(){synchronized(this){instance?.close();instance=null}}
 }
}
