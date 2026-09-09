package vn.ecohome.pos0210.data
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
@Entity data class EmployeeEntity(@PrimaryKey val id:String,val name:String,val active:Boolean=true,val pin:String="0000",val role:String="STAFF",val canCheckout:Boolean=true,val canPurchase:Boolean=false,val canOrder:Boolean=true,val canSendKitchen:Boolean=true,val canViewReport:Boolean=false,val canManageMenu:Boolean=false,val canManageSystem:Boolean=false)
@Entity data class AreaEntity(@PrimaryKey val id:String,val name:String,val sortOrder:Int=0,val active:Boolean=true)
@Entity(indices=[Index("areaId")]) data class DiningTableEntity(@PrimaryKey val id:String,val areaId:String,val name:String,val sortOrder:Int=0,val active:Boolean=true)
@Entity data class MenuCategoryEntity(@PrimaryKey val id:String,val name:String,val sortOrder:Int=0,val active:Boolean=true)
@Entity(indices=[Index("categoryId")]) data class MenuItemEntity(@PrimaryKey val id:String,val categoryId:String,val name:String,val price:Long,val imageUri:String?=null,val sortOrder:Int=0,val active:Boolean=true)
@Entity(indices=[Index(value=["tableId","status"])]) data class TableSessionEntity(@PrimaryKey val id:String,val tableId:String,val openedAt:Long,val openedBy:String,val status:String="OPEN",val version:Long=1)
@Entity(indices=[Index("sessionId")]) data class OrderBatchEntity(@PrimaryKey val id:String,val sessionId:String,val sequence:Int,val ordererId:String,val createdAt:Long,val sentAt:Long?=null,val status:String="DRAFT")
@Entity(indices=[Index("batchId")]) data class OrderItemEntity(@PrimaryKey val id:String,val batchId:String,val menuItemId:String?,val itemNameSnapshot:String,val unitPriceSnapshot:Long,val qty:Int,val note:String="",val adjustmentOfItemId:String?=null)
@Entity(indices=[Index("sessionId")]) data class BillEntity(@PrimaryKey val id:String,val sessionId:String,val billNo:String,val openedAt:Long,val closedAt:Long?,val subtotal:Long,val total:Long,val status:String)
@Entity(indices=[Index(value=["billId"],unique=true)]) data class PaymentEntity(@PrimaryKey val id:String,val billId:String,val method:String,val amount:Long,val cashierId:String,val paidAt:Long,val reference:String?=null)
@Entity data class SupplierEntity(@PrimaryKey val id:String,val name:String,val phone:String="",val note:String="",val active:Boolean=true)
@Entity(indices=[Index("supplierId")]) data class PurchaseEntity(@PrimaryKey val id:String,val supplierId:String?,val enteredBy:String,val purchasedAt:Long,val total:Long,val note:String="",val invoiceImageUri:String?=null)
@Entity data class PurchaseCategoryEntity(@PrimaryKey val id:String,val name:String,val defaultUnit:String="lần",val sortOrder:Int=0,val active:Boolean=true)
@Entity(indices=[Index("purchaseId"),Index("categoryId")]) data class PurchaseItemEntity(@PrimaryKey val id:String,val purchaseId:String,val categoryId:String="pc_production",val name:String,val qty:Double,val unit:String,val unitPrice:Long,val amount:Long)
@Entity(indices=[Index("batchId")]) data class PrintJobEntity(@PrimaryKey val id:String,val batchId:String?,val billId:String?,val type:String,val status:String="PENDING",val claimedByDeviceId:String?=null,val attempts:Int=0,val createdAt:Long,val printedAt:Long?=null,val error:String?=null)
@Entity(indices=[Index("entityId")]) data class AuditEventEntity(@PrimaryKey val id:String,val entityType:String,val entityId:String,val action:String,val actorId:String?,val deviceId:String?,val occurredAt:Long,val payload:String="")
@Entity data class AppSettingEntity(@PrimaryKey val key:String,val value:String)

data class ItemSaleRow(val name:String,val qty:Int,val sessionId:String)
