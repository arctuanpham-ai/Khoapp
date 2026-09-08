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
 val areas=repo.areas().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val tables=repo.tables().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val categories=repo.categories().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val menu=repo.menuItems().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val employees=repo.employees().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val sessions=repo.openSessions().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val bills=repo.paidBills().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val suppliers=dao.suppliers().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val purchases=dao.purchases().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());val payments=dao.payments().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val cart=MutableStateFlow<Map<String,Int>>(emptyMap());val currentTable=MutableStateFlow<DiningTableEntity?>(null);val currentSession=MutableStateFlow<TableSessionEntity?>(null);val screen=MutableStateFlow("TABLES")
 init{viewModelScope.launch{seed()}}
 private suspend fun seed(){if(dao.areas().first().isNotEmpty())return;repo.saveArea(AreaEntity("inside","Trong nhà",0));repo.saveArea(AreaEntity("outside","Ngoài trời",1));(1..6).forEach{repo.saveTable(DiningTableEntity("t$it","inside","Bàn %02d".format(it),it))};(7..8).forEach{repo.saveTable(DiningTableEntity("t$it","outside","Bàn %02d".format(it),it))};listOf("Cà phê","Ăn sáng","Trà","Sinh tố","Khác").forEachIndexed{i,n->repo.saveCategory(MenuCategoryEntity("c$i",n,i))};listOf(MenuItemEntity("m1","c0","Đen đá",25000),MenuItemEntity("m2","c0","Nâu đá",30000),MenuItemEntity("m3","c0","Bạc xỉu",30000),MenuItemEntity("m4","c1","Bún gà",40000),MenuItemEntity("m5","c1","Đùi gà",55000),MenuItemEntity("m6","c1","Cánh gà",45000),MenuItemEntity("m7","c2","Trà mạn",25000),MenuItemEntity("m8","c2","Trà đào",35000)).forEach{repo.saveMenuItem(it)};listOf("Tuấn","Hương","Nam").forEachIndexed{i,n->repo.saveEmployee(EmployeeEntity("e$i",n))}}
 fun add(i:MenuItemEntity){cart.value=cart.value.toMutableMap().apply{put(i.id,(get(i.id)?:0)+1)}};fun sub(i:MenuItemEntity){cart.value=cart.value.toMutableMap().apply{val q=get(i.id)?:0;if(q<=1)remove(i.id)else put(i.id,q-1)}}
 fun selectTable(t:DiningTableEntity,e:String){viewModelScope.launch{currentTable.value=t;currentSession.value=sessions.value.firstOrNull{it.tableId==t.id}?:repo.openSession(t.id,e);cart.value=emptyMap();screen.value="ORDER"}}
 fun addTable(a:String){viewModelScope.launch{val n=tables.value.size+1;repo.saveTable(DiningTableEntity(UUID.randomUUID().toString(),a,"Bàn %02d".format(n),n))}}
 fun saveMenu(name:String,price:Long,cat:String){viewModelScope.launch{repo.saveMenuItem(MenuItemEntity(UUID.randomUUID().toString(),cat,name,price,sortOrder=menu.value.size+1))}}
 fun toggleMenu(i:MenuItemEntity){viewModelScope.launch{dao.setMenuActive(i.id,!i.active)}}
 fun saveEmployee(name:String){viewModelScope.launch{repo.saveEmployee(EmployeeEntity(UUID.randomUUID().toString(),name))}}
 fun toggleEmployee(e:EmployeeEntity){viewModelScope.launch{dao.setEmployeeActive(e.id,!e.active)}}
 fun saveSupplier(name:String){viewModelScope.launch{repo.saveSupplier(SupplierEntity(UUID.randomUUID().toString(),name))}}
 fun addPurchase(name:String,amount:Long,supplier:String?){viewModelScope.launch{val id=UUID.randomUUID().toString();val emp=employees.value.firstOrNull()?.id?:"";repo.savePurchase(PurchaseEntity(id,supplier,emp,System.currentTimeMillis(),amount,name),listOf(PurchaseItemEntity(UUID.randomUUID().toString(),id,name,1.0,"lần",amount,amount)))}}
 fun sendBatch(e:String){val s=currentSession.value?:return;val lines=cart.value;if(lines.isEmpty())return;viewModelScope.launch{val bs=dao.batches(s.id).first();val its=lines.mapNotNull{(id,q)->menu.value.firstOrNull{it.id==id}?.let{OrderItemEntity("","",it.id,it.name,it.price,q)}};val b=repo.createBatch(s.id,bs.size+1,e,its);repo.queueKitchenPrint(b);dao.transitionBatch(b.id,"DRAFT","SENT",System.currentTimeMillis());cart.value=emptyMap();screen.value="SENT"}}
 fun batches(id:String)=dao.batches(id);fun items(id:String)=dao.batchItems(id);fun total(id:String)=dao.sessionTotal(id)
 fun close(method:String,cashier:String,total:Long){val s=currentSession.value?:return;viewModelScope.launch{repo.closeAndPay(s,total,method,cashier,"0210-${System.currentTimeMillis().toString().takeLast(6)}");currentSession.value=null;currentTable.value=null;screen.value="TABLES"}}
}