package vn.ecohome.pos0210
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vn.ecohome.pos0210.data.*
import vn.ecohome.pos0210.printing.PrinterText
import vn.ecohome.pos0210.printing.BluetoothPrinter
import vn.ecohome.pos0210.printing.ReceiptRenderer
import java.util.UUID
class PosViewModel(app:Application):AndroidViewModel(app){
 private val db=PosDatabase.get(app);private val repo=PosRepository(db);private val dao=db.dao();private val masterMutex=Mutex()
 val areas=repo.areas().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val tables=repo.tables().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val waitingBatches=dao.waitingBatches().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val categories=repo.categories().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val menu=repo.menuItems().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val combos=dao.combos().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val employees=repo.employees().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val sessions=repo.openSessions().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val bills=repo.paidBills().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val suppliers=dao.suppliers().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val purchases=dao.purchases().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val purchaseCosts=dao.purchaseCosts().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val purchaseCategories=dao.purchaseCategories().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val payments=dao.payments().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val customers=dao.customers().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val customerItemStats=dao.customerItemStats().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val pricingRules=dao.pricingRules().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val billAdjustments=dao.billAdjustments().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val settings=dao.settings().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList());val printJobs=dao.printJobs().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val audits=dao.audits().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val itemSales=dao.paidItemSales().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val customerUpdateMessage=MutableStateFlow("");val cart=MutableStateFlow<Map<String,Int>>(emptyMap());val currentTable=MutableStateFlow<DiningTableEntity?>(null);val currentSession=MutableStateFlow<TableSessionEntity?>(null);val currentEmployee=MutableStateFlow<EmployeeEntity?>(null);val authError=MutableStateFlow("");val screen=MutableStateFlow("LOGIN");val printerPreview=MutableStateFlow("");val printerMessage=MutableStateFlow("")
 init{viewModelScope.launch{bootstrap()}}
 private suspend fun bootstrap(){
  if(dao.areas().first().isNotEmpty())return
  seed()
 }
 private suspend fun seed(){if(dao.areas().first().isNotEmpty())return;
 repo.savePurchaseCategory(PurchaseCategoryEntity("pc_salary","Lương","ngày công",0,true))
 repo.savePurchaseCategory(PurchaseCategoryEntity("pc_fixed","Vật tư cố định","cái",1,true))
 repo.savePurchaseCategory(PurchaseCategoryEntity("pc_production","Vật tư sản xuất","kg",2,true));repo.saveArea(AreaEntity("inside","Trong nhà",0));repo.saveArea(AreaEntity("outside","Ngoài trời",1));(1..6).forEach{repo.saveTable(DiningTableEntity("t$it","inside","Bàn %02d".format(it),it))};(7..8).forEach{repo.saveTable(DiningTableEntity("t$it","outside","Bàn %02d".format(it),it))};listOf("Cà phê","Ăn sáng","Trà","Sinh tố","Khác").forEachIndexed{i,n->repo.saveCategory(MenuCategoryEntity("c$i",n,i))};listOf(MenuItemEntity("m1","c0","Đen đá",25000),MenuItemEntity("m2","c0","Nâu đá",30000),MenuItemEntity("m3","c0","Bạc xỉu",30000),MenuItemEntity("m4","c1","Bún gà",40000),MenuItemEntity("m5","c1","Đùi gà",55000),MenuItemEntity("m6","c1","Cánh gà",45000),MenuItemEntity("m7","c2","Trà mạn",25000),MenuItemEntity("m8","c2","Trà đào",35000)).forEach{repo.saveMenuItem(it)};repo.saveEmployee(EmployeeEntity("e0","Tuấn",true,"0210","ADMIN",true,true,true,true,true,true,true));repo.saveEmployee(EmployeeEntity("e1","Hương",true,"1992","STAFF",true,true,true,true,false,false,false));repo.saveEmployee(EmployeeEntity("e2","Nam",true,"2000","STAFF",false,false,true,true,false,false,false))}
 fun login(pin:String){viewModelScope.launch{val e=dao.employeeByPin(pin);if(e==null)authError.value="PIN không đúng" else{currentEmployee.value=e;authError.value="";screen.value="TABLES";audit("AUTH",e.id,"LOGIN")}}};fun logout(){val e=currentEmployee.value;viewModelScope.launch{if(e!=null)audit("AUTH",e.id,"LOGOUT")};currentEmployee.value=null;screen.value="LOGIN"}
 private suspend fun audit(type:String,id:String,action:String,payload:String=""){dao.audit(AuditEventEntity(UUID.randomUUID().toString(),type,id,action,currentEmployee.value?.id,"ANDROID",System.currentTimeMillis(),payload))}
 private fun autoBackup(){
  val root=setting("storage_root_uri")
  if(root.isNotBlank()) DataBackup.backupLatest(getApplication(),root)
 }
 private suspend fun autoMasterConfig(){
  val root=dao.allSettingsSnapshot().firstOrNull{it.key=="storage_root_uri"}?.value.orEmpty()
  if(root.isBlank())return
  masterMutex.withLock {
   ConfigBackup.saveMaster(getApplication(),root)
  }
 }
 fun previewKitchen(){printerPreview.value="KITCHEN"}
 fun previewBill(){printerPreview.value="BILL"}
 fun previewCancel(){printerPreview.value="CANCEL"}
 fun clearPrinterPreview(){printerPreview.value=""}
 private fun printerMode()=setting("printer_mode").ifBlank{"TEST"}
 private fun printerMac()=setting("printer_mac")
 private fun printerName()=setting("printer_name").ifBlank{BluetoothPrinter.PROFILE_NAME}
 private fun qrUrl(amount:Long,info:String):String{
  val bank=setting("bank_name").trim().replace(" ","")
  val account=setting("bank_account").trim()
  if(bank.isBlank()||account.isBlank())return ""
  return "https://img.vietqr.io/image/${Uri.encode(bank)}-${Uri.encode(account)}-compact2.png?amount=$amount&addInfo=${Uri.encode(info.take(50))}&accountName=${Uri.encode(setting("bank_holder").trim())}"
 }
 fun testBluetoothPrint(){
  viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO){
   if(printerMode()!="BLUETOOTH"){printerMessage.value="Hãy chọn chế độ BLUETOOTH trước";return@launch}
   if(printerMac().isBlank()){printerMessage.value="Chưa chọn máy in Bluetooth";return@launch}
   printerMessage.value="Đang in thử..."
   val qr=qrUrl(135000,"0210 TEST").takeIf{it.isNotBlank()}?.let{BluetoothPrinter.downloadBitmap(it)}
   val result=BluetoothPrinter.printBitmap(getApplication(),printerMac(),ReceiptRenderer.sampleBill(qr))
   printerMessage.value=if(result.isSuccess)"IN THỬ THÀNH CÔNG · ${printerName()}" else "IN THỬ LỖI: ${result.exceptionOrNull()?.message}"
  }
 }
 fun add(i:MenuItemEntity){cart.value=cart.value.toMutableMap().apply{put(i.id,(get(i.id)?:0)+1)}};fun sub(i:MenuItemEntity){cart.value=cart.value.toMutableMap().apply{val q=get(i.id)?:0;if(q<=1)remove(i.id)else put(i.id,q-1)}};fun addCombo(i:ComboEntity){val k="combo:"+i.id;cart.value=cart.value.toMutableMap().apply{put(k,(get(k)?:0)+1)}};fun subCombo(i:ComboEntity){val k="combo:"+i.id;cart.value=cart.value.toMutableMap().apply{val q=get(k)?:0;if(q<=1)remove(k)else put(k,q-1)}}
 fun selectTable(t:DiningTableEntity){
  val e=currentEmployee.value?:return
  if(!e.canOrder&&e.role!="ADMIN")return
  viewModelScope.launch{
   currentTable.value=t
   val existing=dao.openSessionForTable(t.id)
   cart.value=emptyMap()
   if(existing!=null){
    currentSession.value=existing
    screen.value="SENT"
   }else{
    currentSession.value=null
    screen.value="ORDER"
   }
  }
 }
 fun addMore(){if(currentSession.value==null||currentTable.value==null)return;cart.value=emptyMap();screen.value="ORDER"}
 fun addTable(areaId:String,name:String=""){
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN"&&!e.canManageSystem)return
  viewModelScope.launch{
   val n=tables.value.size+1
   val id=UUID.randomUUID().toString()
   val tableName=name.trim().ifBlank{"Bàn %02d".format(n)}
   repo.saveTable(DiningTableEntity(id,areaId,tableName,n,true))
   audit("TABLE",id,"CREATE","name=$tableName,area=$areaId")
   autoBackup();autoMasterConfig()
  }
 }
 fun updateTable(t:DiningTableEntity,name:String,areaId:String){
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN"&&!e.canManageSystem)return
  viewModelScope.launch{
   repo.saveTable(t.copy(name=name.trim().ifBlank{t.name},areaId=areaId))
   audit("TABLE",t.id,"UPDATE","name=$name,area=$areaId")
   autoBackup();autoMasterConfig()
  }
 }
 fun hideTable(t:DiningTableEntity){
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN"&&!e.canManageSystem)return
  viewModelScope.launch{
   if(dao.openSessionForTable(t.id)!=null)return@launch
   repo.saveTable(t.copy(active=false))
   audit("TABLE",t.id,"HIDE",t.name)
   autoBackup();autoMasterConfig()
  }
 }
 fun canConfigureQr():Boolean{val r=currentEmployee.value?.role?:return false;return r=="ADMIN"||r=="MANAGER"}
 private fun canManageMenu():Boolean{val e=currentEmployee.value?:return false;return e.role=="ADMIN"||e.canManageMenu}
 fun saveMenu(name:String,price:Long,cat:String,imageUri:String?=null){if(!canManageMenu())return;viewModelScope.launch{val id=UUID.randomUUID().toString();repo.saveMenuItem(MenuItemEntity(id,cat,name,price,imageUri,menu.value.size+1));audit("MENU",id,"CREATE",name);autoBackup();autoMasterConfig()}}
 fun setMenuImage(i:MenuItemEntity,uri:String?){if(!canManageMenu())return;viewModelScope.launch{repo.saveMenuItem(i.copy(imageUri=uri));audit("MENU",i.id,"IMAGE");autoBackup();autoMasterConfig()}}
 fun toggleMenu(i:MenuItemEntity){if(!canManageMenu())return;viewModelScope.launch{dao.setMenuActive(i.id,!i.active);audit("MENU",i.id,"ACTIVE",(!i.active).toString());autoBackup();autoMasterConfig()}}
 fun deleteMenu(i:MenuItemEntity){if(!canManageMenu())return;viewModelScope.launch{dao.setMenuActive(i.id,false);audit("MENU",i.id,"DELETE_SOFT",i.name);autoBackup();autoMasterConfig()}}
 fun addCategory(name:String){if(!canManageMenu()||name.isBlank())return;viewModelScope.launch{val id=UUID.randomUUID().toString();repo.saveCategory(MenuCategoryEntity(id,name.trim(),categories.value.size+1,true));audit("CATEGORY",id,"CREATE",name.trim());autoBackup();autoMasterConfig()}}
 fun deleteCategory(c:MenuCategoryEntity){
  if(!canManageMenu())return
  viewModelScope.launch{
   if(menu.value.any{it.active&&it.categoryId==c.id})return@launch
   dao.setCategoryActive(c.id,false)
   audit("CATEGORY",c.id,"DELETE_SOFT",c.name)
   autoBackup();autoMasterConfig()
  }
 }
 fun saveEmployee(name:String,pin:String,role:String,checkout:Boolean,purchase:Boolean,order:Boolean=true,kitchen:Boolean=true,report:Boolean=false,menu:Boolean=false,system:Boolean=false){if(currentEmployee.value?.role!="ADMIN")return;viewModelScope.launch{val id=UUID.randomUUID().toString();repo.saveEmployee(EmployeeEntity(id,name,true,pin,role,checkout,purchase,order,kitchen,report,menu,system));audit("EMPLOYEE",id,"CREATE",name);autoBackup();autoMasterConfig()}}
 fun updateEmployee(e:EmployeeEntity){if(currentEmployee.value?.role!="ADMIN")return;viewModelScope.launch{repo.saveEmployee(e);audit("EMPLOYEE",e.id,"UPDATE");autoBackup();autoMasterConfig()}};fun toggleEmployee(e:EmployeeEntity){if(currentEmployee.value?.role!="ADMIN"||e.id==currentEmployee.value?.id)return;viewModelScope.launch{dao.setEmployeeActive(e.id,!e.active);audit("EMPLOYEE",e.id,if(e.active)"DISABLE" else "ENABLE")}}
 fun saveSupplier(name:String){viewModelScope.launch{repo.saveSupplier(SupplierEntity(UUID.randomUUID().toString(),name))}}
 fun addPurchaseCategory(name:String,defaultUnit:String){
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN"&&e.role!="MANAGER")return
  if(name.isBlank())return
  viewModelScope.launch{
   val id=UUID.randomUUID().toString()
   repo.savePurchaseCategory(PurchaseCategoryEntity(id,name.trim(),defaultUnit.trim().ifBlank{"lần"},purchaseCategories.value.size+1,true))
   audit("PURCHASE_CATEGORY",id,"CREATE",name.trim())
   autoBackup();autoMasterConfig()
  }
 }
 fun deletePurchaseCategory(c:PurchaseCategoryEntity){
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN"&&e.role!="MANAGER")return
  viewModelScope.launch{
   val used=purchases.value.any { p -> dao.purchaseItems(p.id).first().any { it.categoryId==c.id } }
   if(used)return@launch
   dao.setPurchaseCategoryActive(c.id,false)
   audit("PURCHASE_CATEGORY",c.id,"DELETE_SOFT",c.name)
   autoBackup();autoMasterConfig()
  }
 };
 fun saveCombo(name:String,price:Long,imageUri:String?,items:Map<String,Int>){
  val e=currentEmployee.value?:return
  if((e.role!="ADMIN"&&!e.canManageMenu)||name.isBlank()||price<=0||items.isEmpty())return
  viewModelScope.launch{
   val id=UUID.randomUUID().toString()
   dao.saveCombo(ComboEntity(id,name.trim(),price,imageUri,combos.value.size+1,true))
   items.filterValues{it>0}.forEach{(menuItemId,qty)->
    dao.saveComboItem(ComboItemEntity(UUID.randomUUID().toString(),id,menuItemId,qty))
   }
   audit("COMBO",id,"CREATE","name=${name.trim()},price=$price,items=${items.size}")
   autoBackup();autoMasterConfig()
  }
 }
 fun toggleCombo(combo:ComboEntity){
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN"&&!e.canManageMenu)return
  viewModelScope.launch{dao.setComboActive(combo.id,!combo.active);audit("COMBO",combo.id,"ACTIVE",(!combo.active).toString());autoBackup();autoMasterConfig()}
 }
 fun savePricingRule(name:String,code:String,kind:String,percent:Int,startAt:Long?,endAt:Long?,startMinute:Int?,endMinute:Int?,autoApply:Boolean){
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN"||name.isBlank()||percent<=0)return
  viewModelScope.launch{
   val id=UUID.randomUUID().toString()
   dao.savePricingRule(PricingRuleEntity(id,name.trim(),code.trim().uppercase(),kind,percent.coerceIn(1,100),startAt,endAt,startMinute,endMinute,autoApply,true))
   audit("PRICING",id,"CREATE","name=${name.trim()},kind=$kind,percent=$percent,code=${code.trim().uppercase()}")
   autoBackup();autoMasterConfig()
  }
 }
 fun togglePricingRule(rule:PricingRuleEntity){
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN")return
  viewModelScope.launch{dao.setPricingRuleActive(rule.id,!rule.active);audit("PRICING",rule.id,"ACTIVE",(!rule.active).toString());autoBackup();autoMasterConfig()}
 }
fun saveLoyaltyConfig(auto:Boolean,memberDiscount:Int,vipPoints:Int,vipDiscount:Int,vvipPoints:Int,vvipDiscount:Int){
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN")return
  viewModelScope.launch{
   val safeVip=vipPoints.coerceAtLeast(0)
   val safeVvip=vvipPoints.coerceAtLeast(safeVip)
   dao.saveSetting(AppSettingEntity("loyalty_auto_tier",auto.toString()))
   dao.saveSetting(AppSettingEntity("member_discount_percent",memberDiscount.coerceIn(0,100).toString()))
   dao.saveSetting(AppSettingEntity("vip_min_points",safeVip.toString()))
   dao.saveSetting(AppSettingEntity("vip_discount_percent",vipDiscount.coerceIn(0,100).toString()))
   dao.saveSetting(AppSettingEntity("vvip_min_points",safeVvip.toString()))
   dao.saveSetting(AppSettingEntity("vvip_discount_percent",vvipDiscount.coerceIn(0,100).toString()))
   if(auto){
    customers.value.filter{!it.tierManual}.forEach{cu->
     val tier=when{
      cu.points>=safeVvip -> "VVIP"
      cu.points>=safeVip -> "VIP"
      else -> "MEMBER"
     }
     if(tier!=cu.tier)dao.saveCustomer(cu.copy(tier=tier))
    }
   }
   audit("LOYALTY","CONFIG","SAVE","auto=$auto,vip=$safeVip,vvip=$safeVvip")
   autoBackup();autoMasterConfig()
  }
 }
fun saveSetting(key:String,value:String){viewModelScope.launch{dao.saveSetting(AppSettingEntity(key,value));audit("SETTING",key,"SAVE",value);if(key!="master_config_uri"&&key!="autoback_tree_uri"&&key!="storage_root_uri")autoMasterConfig()}};fun setting(key:String)=settings.value.firstOrNull{it.key==key}?.value?:""
 fun addPurchase(name:String,amount:Long,note:String,at:Long=System.currentTimeMillis(),imageUri:String?=null){addPurchaseDetailed(name,1.0,"lần",amount,note,at,"",imageUri)}
 fun addPurchaseDetailed(name:String,qty:Double,unit:String,unitPrice:Long,note:String,at:Long=System.currentTimeMillis(),supplierName:String="",imageUri:String?=null,categoryId:String="pc_production"){
  val e=currentEmployee.value?:return
  if(!e.canPurchase&&e.role!="ADMIN")return
  if(name.isBlank()||qty<=0||unitPrice<=0)return
  viewModelScope.launch{
   val id=UUID.randomUUID().toString()
   val supplierId=if(supplierName.isBlank())null else UUID.randomUUID().toString().also{repo.saveSupplier(SupplierEntity(it,supplierName.trim()))}
   val amount=(qty*unitPrice).toLong()
   repo.savePurchase(
    PurchaseEntity(id,supplierId,e.id,at,amount,note,imageUri),
    listOf(PurchaseItemEntity(UUID.randomUUID().toString(),id,categoryId,name.trim(),qty,unit.ifBlank{"lần"},unitPrice,amount))
   )
   audit("PURCHASE",id,"CREATE","${name.trim()}:$qty:$unit:$unitPrice:$amount");autoBackup()
  }
 }
 fun sendBatch(){
  val e=currentEmployee.value?:return
  val t=currentTable.value?:return
  if(!e.canSendKitchen&&e.role!="ADMIN")return
  val lines=cart.value
  if(lines.isEmpty())return
  viewModelScope.launch{
   val s=currentSession.value ?: repo.openSession(t.id,e.id).also{currentSession.value=it}
   val bs=dao.batches(s.id).first()
   val its=mutableListOf<OrderItemEntity>()
   lines.forEach{(id,q)->
    if(id.startsWith("combo:")){
     val comboId=id.removePrefix("combo:")
     val combo=combos.value.firstOrNull{it.id==comboId}
     if(combo!=null){
      val parts=dao.comboItems(comboId).first().mapNotNull{ci->
       menu.value.firstOrNull{it.id==ci.menuItemId}?.let{m->"${ci.qty}×${m.name}"}
      }
      its.add(OrderItemEntity("","","combo:"+combo.id,"COMBO · ${combo.name} [${parts.joinToString(" + ")}]",combo.price,q))
     }
    }else{
     menu.value.firstOrNull{it.id==id}?.let{its.add(OrderItemEntity("","",it.id,it.name,it.price,q))}
    }
   }
   repo.createBatch(s.id,bs.size+1,e.id,its)
   cart.value=emptyMap()
   autoBackup()
   screen.value="SENT"
  }
 }
 fun markBatchSent(b:OrderBatchEntity){
  val e=currentEmployee.value?:return
  if(!e.canSendKitchen&&e.role!="ADMIN")return
  viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO){
   if(printerMode()!="BLUETOOTH"){
    val job=repo.queueKitchenPrint(b)
    dao.markPrintSuccess(job.id,System.currentTimeMillis())
    dao.transitionBatch(b.id,"DRAFT","WAITING",System.currentTimeMillis())
    audit("PRINT",b.id,"KITCHEN_TEST_CONFIRMED","operator=${e.name}")
    printerMessage.value="TEST · Đã xác nhận phiếu bếp"
    return@launch
   }
   val mac=printerMac()
   if(mac.isBlank()){printerMessage.value="Chưa chọn máy in XP-N58H";return@launch}
   val table=currentTable.value?.name ?: "Bàn"
   val items=dao.batchItems(b.id).first().map{it.itemNameSnapshot to it.qty}
   val job=repo.queueKitchenPrint(b)
   printerMessage.value="Đang in Đơn #${b.sequence}..."
   val result=BluetoothPrinter.printBitmap(getApplication(),mac,ReceiptRenderer.kitchen(table,b.sequence,b.serviceNo,e.name,items))
   if(result.isSuccess){
    dao.markPrintSuccess(job.id,System.currentTimeMillis())
    dao.transitionBatch(b.id,"DRAFT","WAITING",System.currentTimeMillis())
    audit("PRINT",b.id,"KITCHEN_PRINTED","printer=${printerName()},operator=${e.name}")
    printerMessage.value="ĐÃ IN · Đơn #${b.sequence}"
   }else{
    dao.markPrintFailed(job.id,result.exceptionOrNull()?.message ?: "UNKNOWN")
    audit("PRINT",b.id,"KITCHEN_PRINT_FAILED","printer=${printerName()}")
    printerMessage.value="IN THẤT BẠI · ${result.exceptionOrNull()?.message ?: "Thử lại"}"
   }
  }
 }
 fun markDelivered(b:OrderBatchEntity){
  val e=currentEmployee.value?:return
  viewModelScope.launch{
   val now=System.currentTimeMillis()
   if(dao.markDelivered(b.id,now,e.id)>0){
    audit("BATCH",b.id,"DELIVERED","serviceNo=${b.serviceNo},by=${e.name}")
    autoBackup()
   }
  }
 }
 fun canCancelOrder():Boolean{val r=currentEmployee.value?.role?:return false;return r=="ADMIN"||r=="MANAGER"}
 fun cancelBatch(b:OrderBatchEntity,reason:String){
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN"&&e.role!="MANAGER")return
  if(reason.isBlank())return
  viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO){
   if(dao.cancelBatch(b.id)>0){
    audit("BATCH",b.id,"CANCELLED","reason=${reason.trim()},operator=${e.name}")
    autoBackup()
    if(printerMode()=="BLUETOOTH"&&printerMac().isNotBlank()){
     val table=currentTable.value?.name ?: "Bàn"
     val pr=BluetoothPrinter.printBitmap(getApplication(),printerMac(),ReceiptRenderer.cancel(table,b.sequence,e.name,reason.trim()))
     printerMessage.value=if(pr.isSuccess)"ĐÃ IN PHIẾU HỦY · Đơn #${b.sequence}" else "ĐÃ HỦY ĐƠN · In phiếu hủy lỗi: ${pr.exceptionOrNull()?.message}"
    }
   }
  }
 }
 fun releaseCancelledTable(){
  val s=currentSession.value?:return
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN"&&e.role!="MANAGER")return
  viewModelScope.launch{
   val bs=dao.batches(s.id).first()
   val remaining=dao.sessionTotal(s.id).first()
   if(bs.isNotEmpty()&&bs.all{it.status=="CANCELLED"}&&remaining==0L){
    repo.closeCancelledSession(s,e.id)
    currentSession.value=null
    currentTable.value=null
    cart.value=emptyMap()
    screen.value="TABLES"
    autoBackup()
   }
  }
 }
 fun batches(id:String)=dao.batches(id)
 fun items(id:String)=dao.batchItems(id)
 fun total(id:String)=dao.sessionTotal(id)
 fun session(id:String)=dao.sessionById(id)
 fun setCustomerTier(customer:CustomerEntity,tier:String){
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN"||tier !in listOf("MEMBER","VIP","VVIP"))return
  viewModelScope.launch{
   dao.saveCustomer(customer.copy(tier=tier,tierManual=true))
   audit("CUSTOMER",customer.id,"TIER_MANUAL","$tier")
   autoBackup()
  }
 }
 fun setCustomerTierAutomatic(customer:CustomerEntity){
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN")return
  viewModelScope.launch{
   val auto=setting("loyalty_auto_tier").ifBlank{"true"}.toBoolean()
   val tier=if(auto)autoTierFor(customer.points) else customer.tier
   dao.saveCustomer(customer.copy(tier=tier,tierManual=false))
   audit("CUSTOMER",customer.id,"TIER_AUTO","$tier")
   autoBackup()
  }
 }
 private fun autoTierFor(points:Int):String{
  val vip=setting("vip_min_points").toIntOrNull() ?: 200
  val vvip=setting("vvip_min_points").toIntOrNull() ?: 500
  return when{
   points>=vvip -> "VVIP"
   points>=vip -> "VIP"
   else -> "MEMBER"
  }
 }
 fun updateCustomerProfile(customer:CustomerEntity,name:String,phone:String,address:String){
  val e=currentEmployee.value?:return
  viewModelScope.launch{
   val normalized=phone.filter(Char::isDigit).take(15)
   if(normalized.length<9){customerUpdateMessage.value="Số điện thoại không hợp lệ";return@launch}
   val other=dao.customerByPhone(normalized)
   if(other!=null&&other.id!=customer.id){customerUpdateMessage.value="Số điện thoại đã thuộc khách khác";return@launch}
   dao.saveCustomer(customer.copy(name=name.trim(),phone=normalized,address=address.trim()))
   audit("CUSTOMER",customer.id,"PROFILE_UPDATE","name=${name.trim()},phone=$normalized")
   customerUpdateMessage.value="Đã cập nhật thông tin khách"
   autoBackup()
  }
 }
 fun clearCustomerUpdateMessage(){customerUpdateMessage.value=""}
 fun customerPoints(id:String)=dao.customerPoints(id)
 fun purchaseItems(id:String)=dao.purchaseItems(id)
 fun comboItems(id:String)=dao.comboItems(id)
 fun deleteBill(bill:BillEntity,reason:String){
  deleteBills(listOf(bill),reason)
 }
 fun deletePurchase(p:PurchaseEntity,reason:String){
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN"||reason.isBlank())return
  viewModelScope.launch{
   val changed=dao.softDeletePurchase(p.id)
   if(changed>0){
    audit("PURCHASE",p.id,"DELETE_SOFT","reason=${reason.trim()},total=${p.total},admin=${e.name}")
    autoBackup()
   }
  }
 }
 fun deleteBills(targets:List<BillEntity>,reason:String){
  val e=currentEmployee.value?:return
  if(e.role!="ADMIN"||reason.isBlank()||targets.isEmpty())return
  viewModelScope.launch{
   val ids=targets.map{it.id}
   val changed=dao.softDeleteBills(ids)
   if(changed>0){
    targets.forEach{ bill ->
     audit("BILL",bill.id,"DELETE_SOFT","reason=${reason.trim()},total=${bill.total},admin=${e.name}")
     bill.customerId?.let{customerId->
      val delta=dao.pointDeltaForBill(bill.id)
      val cu=dao.customerById(customerId)
      if(delta!=0){
       dao.insertCustomerPoint(CustomerPointTransactionEntity(UUID.randomUUID().toString(),customerId,bill.id,-delta,"HỦY/XÓA BILL ${bill.billNo}",System.currentTimeMillis(),e.id))
      }
      if(cu!=null){
       val newPoints=(cu.points-delta).coerceAtLeast(0)
       val newSpend=(cu.totalSpend-bill.total).coerceAtLeast(0)
       val newVisits=(cu.visitCount-1).coerceAtLeast(0)
       val autoTier=setting("loyalty_auto_tier").ifBlank{"true"}.toBoolean()
       val newTier=if(cu.tierManual||!autoTier)cu.tier else autoTierFor(newPoints)
       dao.saveCustomer(cu.copy(points=newPoints,totalSpend=newSpend,visitCount=newVisits,tier=newTier,lastVisitAt=System.currentTimeMillis()))
      }
     }
    }
    autoBackup()
   }
  }
 }
 fun close(method:String,preview:PricingPreview,customerPhone:String="",customerName:String=""){
  val session=currentSession.value?:return
  val employee=currentEmployee.value?:return
  val table=currentTable.value
  if(!employee.canCheckout&&employee.role!="ADMIN")return
  viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO){
   val normalizedPhone=customerPhone.filter(Char::isDigit).take(15)
   val customer=if(normalizedPhone.isBlank())null else{
    dao.customerByPhone(normalizedPhone) ?: CustomerEntity(UUID.randomUUID().toString(),normalizedPhone,customerName.trim(),"MEMBER",0,0,0,null,true,false).also{dao.saveCustomer(it)}
   }
   val bill=repo.closeAndPay(session,preview.subtotal,preview.total,method,employee.id,"0210-${System.currentTimeMillis().toString().takeLast(6)}",customer?.id)
   var pointsBefore=customer?.points ?: 0
   var pointsEarned=0
   var pointsAfter=pointsBefore
   var receiptTier:String?=customer?.tier
   customer?.let{cu->
    pointsEarned=(preview.total/10000L).toInt()
    pointsAfter=pointsBefore+pointsEarned
    val autoTier=setting("loyalty_auto_tier").ifBlank{"true"}.toBoolean()
    val newTier=if(cu.tierManual||!autoTier)cu.tier else autoTierFor(pointsAfter)
    dao.updateCustomerStats(cu.id,pointsEarned,preview.total,1,System.currentTimeMillis())
    if(newTier!=cu.tier)dao.saveCustomer(cu.copy(tier=newTier,points=pointsAfter,totalSpend=cu.totalSpend+preview.total,visitCount=cu.visitCount+1,lastVisitAt=System.currentTimeMillis()))
    receiptTier=newTier
    if(pointsEarned>0){
     dao.insertCustomerPoint(CustomerPointTransactionEntity(UUID.randomUUID().toString(),cu.id,bill.id,pointsEarned,"TÍCH ĐIỂM ${bill.billNo}",System.currentTimeMillis(),employee.id))
    }
    audit("CUSTOMER",cu.id,"VISIT","bill=${bill.billNo},spend=${preview.total},points=$pointsEarned,tier=$newTier")
   }
   val now=System.currentTimeMillis()
   val adjustments=mutableListOf<BillAdjustmentEntity>()
   preview.surchargeRules.forEach{rule->
    val amount=(preview.subtotal*rule.percent/100L)
    adjustments.add(BillAdjustmentEntity(UUID.randomUUID().toString(),bill.id,rule.id,rule.name,"SURCHARGE",rule.percent,amount,rule.code,now,employee.id))
   }
   preview.discountRule?.let{rule->
    adjustments.add(BillAdjustmentEntity(UUID.randomUUID().toString(),bill.id,rule.id,rule.name,"DISCOUNT",rule.percent,preview.discount,rule.code,now,employee.id))
   }
   if(adjustments.isNotEmpty())dao.insertBillAdjustments(adjustments)
   autoBackup()
   if(printerMode()=="BLUETOOTH"&&printerMac().isNotBlank()){
    val bs=dao.batches(session.id).first().filter{it.status!="CANCELLED"}
    val lines=mutableListOf<Triple<String,Int,Long>>()
    bs.forEach{b->dao.batchItems(b.id).first().forEach{it2->lines.add(Triple(it2.itemNameSnapshot,it2.qty,it2.unitPriceSnapshot))}}
    val info="0210 ${table?.name ?: bill.billNo}"
    val qr=qrUrl(preview.total,info).takeIf{it.isNotBlank()}?.let{BluetoothPrinter.downloadBitmap(it)}
    val period="${java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(java.util.Date(session.openedAt))}–${java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(java.util.Date(bill.closedAt ?: System.currentTimeMillis()))}"
    val receiptAdjustments=buildList {
     preview.surchargeRules.forEach{rule->add("PHỤ THU ${rule.name}  +${rule.percent}%")}
     preview.discountRule?.let{rule->
      add("ƯU ĐÃI ${rule.name}${if(rule.code.isNotBlank()) " · ${rule.code}" else ""}  -${rule.percent}%")
     }
    }
    val bmp=ReceiptRenderer.bill(
     table=table?.name ?: "Bàn",
     period=period,
     items=lines,
     subtotal=preview.subtotal,
     surcharge=preview.surcharge,
     discount=preview.discount,
     total=preview.total,
     adjustmentLines=receiptAdjustments,
     customerName=customer?.name?.ifBlank{"KHÁCH THÀNH VIÊN"} ?: "KHÁCH LẠ",
     customerTier=receiptTier,
     pointsBefore=pointsBefore,
     pointsEarned=pointsEarned,
     pointsAfter=pointsAfter,
     method=if(method=="CASH")"TIỀN MẶT" else "CHUYỂN KHOẢN",
     qr=qr
    )
    val job=PrintJobEntity(java.util.UUID.randomUUID().toString(),null,bill.id,"BILL",createdAt=System.currentTimeMillis())
    dao.insertPrintJob(job)
    val pr=BluetoothPrinter.printBitmap(getApplication(),printerMac(),bmp)
    if(pr.isSuccess){
     dao.markPrintSuccess(job.id,System.currentTimeMillis())
     audit("PRINT",bill.id,"BILL_PRINTED","printer=${printerName()}")
     printerMessage.value="ĐÃ IN BILL · ${bill.billNo}"
    }else{
     dao.markPrintFailed(job.id,pr.exceptionOrNull()?.message ?: "UNKNOWN")
     audit("PRINT",bill.id,"BILL_PRINT_FAILED","printer=${printerName()}")
     printerMessage.value="ĐÃ THANH TOÁN · IN BILL LỖI: ${pr.exceptionOrNull()?.message ?: "Thử in lại"}"
    }
   }
   currentSession.value=null
   currentTable.value=null
   screen.value="TABLES"
  }
 }
}
