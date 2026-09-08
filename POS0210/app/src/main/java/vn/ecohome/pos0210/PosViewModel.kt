package vn.ecohome.pos0210
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import vn.ecohome.pos0210.data.*
import java.util.UUID
class PosViewModel(app:Application):AndroidViewModel(app){
 private val db=PosDatabase.get(app);private val repo=PosRepository(db);private val dao=db.dao()
 val areas=repo.areas().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val tables=repo.tables().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val categories=repo.categories().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val menu=repo.menuItems().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val employees=repo.employees().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val sessions=repo.openSessions().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val bills=repo.paidBills().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val suppliers=dao.suppliers().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val purchases=dao.purchases().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val payments=dao.payments().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val settings=dao.settings().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList());val printJobs=dao.printJobs().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val audits=dao.audits().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val itemSales=dao.paidItemSales().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val cart=MutableStateFlow<Map<String,Int>>(emptyMap());val currentTable=MutableStateFlow<DiningTableEntity?>(null);val currentSession=MutableStateFlow<TableSessionEntity?>(null);val currentEmployee=MutableStateFlow<EmployeeEntity?>(null);val authError=MutableStateFlow("");val screen=MutableStateFlow("LOGIN")
 init{viewModelScope.launch{seed()}}
 private suspend fun seed(){if(dao.areas().first().isNotEmpty())return;repo.saveArea(AreaEntity("inside","Trong nhà",0));repo.saveArea(AreaEntity("outside","Ngoài trời",1));(1..6).forEach{repo.saveTable(DiningTableEntity("t$it","inside","Bàn %02d".format(it),it))};(7..8).forEach{repo.saveTable(DiningTableEntity("t$it","outside","Bàn %02d".format(it),it))};listOf("Cà phê","Ăn sáng","Trà","Sinh tố","Khác").forEachIndexed{i,n->repo.saveCategory(MenuCategoryEntity("c$i",n,i))};listOf(MenuItemEntity("m1","c0","Đen đá",25000),MenuItemEntity("m2","c0","Nâu đá",30000),MenuItemEntity("m3","c0","Bạc xỉu",30000),MenuItemEntity("m4","c1","Bún gà",40000),MenuItemEntity("m5","c1","Đùi gà",55000),MenuItemEntity("m6","c1","Cánh gà",45000),MenuItemEntity("m7","c2","Trà mạn",25000),MenuItemEntity("m8","c2","Trà đào",35000)).forEach{repo.saveMenuItem(it)};repo.saveEmployee(EmployeeEntity("e0","Tuấn",true,"0210","ADMIN",true,true,true,true,true,true,true));repo.saveEmployee(EmployeeEntity("e1","Hương",true,"1992","STAFF",true,true,true,true,false,false,false));repo.saveEmployee(EmployeeEntity("e2","Nam",true,"2000","STAFF",false,false,true,true,false,false,false))}
 fun login(pin:String){viewModelScope.launch{val e=dao.employeeByPin(pin);if(e==null)authError.value="PIN không đúng" else{currentEmployee.value=e;authError.value="";screen.value="TABLES";audit("AUTH",e.id,"LOGIN")}}};fun logout(){val e=currentEmployee.value;viewModelScope.launch{if(e!=null)audit("AUTH",e.id,"LOGOUT")};currentEmployee.value=null;screen.value="LOGIN"}
 private suspend fun audit(type:String,id:String,action:String,payload:String=""){dao.audit(AuditEventEntity(UUID.randomUUID().toString(),type,id,action,currentEmployee.value?.id,"ANDROID",System.currentTimeMillis(),payload))}
 private fun autoBackup(){
  val tree=settings.value.firstOrNull{it.key=="autoback_tree_uri"}?.value.orEmpty()
  if(tree.isNotBlank()) DataBackup.autoBackup(getApplication(),tree)
 }
 fun add(i:MenuItemEntity){cart.value=cart.value.toMutableMap().apply{put(i.id,(get(i.id)?:0)+1)}};fun sub(i:MenuItemEntity){cart.value=cart.value.toMutableMap().apply{val q=get(i.id)?:0;if(q<=1)remove(i.id)else put(i.id,q-1)}}
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
 fun addTable(a:String){viewModelScope.launch{val n=tables.value.size+1;repo.saveTable(DiningTableEntity(UUID.randomUUID().toString(),a,"Bàn %02d".format(n),n));audit("TABLE",a,"CREATE")}}
 private fun canManageMenu():Boolean{val e=currentEmployee.value?:return false;return e.role=="ADMIN"||e.canManageMenu}
 fun saveMenu(name:String,price:Long,cat:String,imageUri:String?=null){if(!canManageMenu())return;viewModelScope.launch{val id=UUID.randomUUID().toString();repo.saveMenuItem(MenuItemEntity(id,cat,name,price,imageUri,menu.value.size+1));audit("MENU",id,"CREATE",name);autoBackup()}}
 fun setMenuImage(i:MenuItemEntity,uri:String?){if(!canManageMenu())return;viewModelScope.launch{repo.saveMenuItem(i.copy(imageUri=uri));audit("MENU",i.id,"IMAGE");autoBackup()}}
 fun toggleMenu(i:MenuItemEntity){if(!canManageMenu())return;viewModelScope.launch{dao.setMenuActive(i.id,!i.active);audit("MENU",i.id,"ACTIVE",(!i.active).toString());autoBackup()}}
 fun saveEmployee(name:String,pin:String,role:String,checkout:Boolean,purchase:Boolean,order:Boolean=true,kitchen:Boolean=true,report:Boolean=false,menu:Boolean=false,system:Boolean=false){if(currentEmployee.value?.role!="ADMIN")return;viewModelScope.launch{val id=UUID.randomUUID().toString();repo.saveEmployee(EmployeeEntity(id,name,true,pin,role,checkout,purchase,order,kitchen,report,menu,system));audit("EMPLOYEE",id,"CREATE",name);autoBackup()}}
 fun updateEmployee(e:EmployeeEntity){if(currentEmployee.value?.role!="ADMIN")return;viewModelScope.launch{repo.saveEmployee(e);audit("EMPLOYEE",e.id,"UPDATE");autoBackup()}};fun toggleEmployee(e:EmployeeEntity){if(currentEmployee.value?.role!="ADMIN"||e.id==currentEmployee.value?.id)return;viewModelScope.launch{dao.setEmployeeActive(e.id,!e.active);audit("EMPLOYEE",e.id,if(e.active)"DISABLE" else "ENABLE")}}
 fun saveSupplier(name:String){viewModelScope.launch{repo.saveSupplier(SupplierEntity(UUID.randomUUID().toString(),name))}};fun saveSetting(key:String,value:String){viewModelScope.launch{dao.saveSetting(AppSettingEntity(key,value));audit("SETTING",key,"SAVE",value)}};fun setting(key:String)=settings.value.firstOrNull{it.key==key}?.value?:""
 fun addPurchase(name:String,amount:Long,note:String,at:Long=System.currentTimeMillis(),imageUri:String?=null){addPurchaseDetailed(name,1.0,"lần",amount,note,at,"",imageUri)}
 fun addPurchaseDetailed(name:String,qty:Double,unit:String,unitPrice:Long,note:String,at:Long=System.currentTimeMillis(),supplierName:String="",imageUri:String?=null){
  val e=currentEmployee.value?:return
  if(!e.canPurchase&&e.role!="ADMIN")return
  if(name.isBlank()||qty<=0||unitPrice<=0)return
  viewModelScope.launch{
   val id=UUID.randomUUID().toString()
   val supplierId=if(supplierName.isBlank())null else UUID.randomUUID().toString().also{repo.saveSupplier(SupplierEntity(it,supplierName.trim()))}
   val amount=(qty*unitPrice).toLong()
   repo.savePurchase(
    PurchaseEntity(id,supplierId,e.id,at,amount,note,imageUri),
    listOf(PurchaseItemEntity(UUID.randomUUID().toString(),id,name.trim(),qty,unit.ifBlank{"lần"},unitPrice,amount))
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
   val its=lines.mapNotNull{(id,q)->menu.value.firstOrNull{it.id==id}?.let{OrderItemEntity("","",it.id,it.name,it.price,q)}}
   repo.createBatch(s.id,bs.size+1,e.id,its)
   cart.value=emptyMap()
   autoBackup()
   screen.value="SENT"
  }
 }
 fun markBatchSent(b:OrderBatchEntity){val e=currentEmployee.value?:return;if(!e.canSendKitchen&&e.role!="ADMIN")return;viewModelScope.launch{repo.queueKitchenPrint(b);dao.transitionBatch(b.id,"DRAFT","SENT",System.currentTimeMillis());audit("PRINT",b.id,"KITCHEN_CONFIRMED","operator=${e.name}")}}
 fun canCancelOrder():Boolean{val r=currentEmployee.value?.role?:return false;return r=="ADMIN"||r=="MANAGER"}
 fun cancelBatch(b:OrderBatchEntity,reason:String){val e=currentEmployee.value?:return;if(e.role!="ADMIN"&&e.role!="MANAGER")return;if(reason.isBlank())return;viewModelScope.launch{if(dao.cancelBatch(b.id)>0){audit("BATCH",b.id,"CANCELLED","reason=${reason.trim()},operator=${e.name}");autoBackup()}}}
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
 fun purchaseItems(id:String)=dao.purchaseItems(id)
 fun close(method:String,total:Long){val s=currentSession.value?:return;val e=currentEmployee.value?:return;if(!e.canCheckout&&e.role!="ADMIN")return;viewModelScope.launch{repo.closeAndPay(s,total,method,e.id,"0210-${System.currentTimeMillis().toString().takeLast(6)}");autoBackup();currentSession.value=null;currentTable.value=null;screen.value="TABLES"}}
}
