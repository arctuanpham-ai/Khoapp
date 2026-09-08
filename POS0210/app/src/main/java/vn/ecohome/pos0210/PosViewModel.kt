package vn.ecohome.pos0210

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import vn.ecohome.pos0210.data.*
import java.util.UUID

data class CartLine(val item:MenuItemEntity,val qty:Int)

class PosViewModel(app:Application):AndroidViewModel(app){
    private val db=PosDatabase.get(app); private val repo=PosRepository(db); private val dao=db.dao()
    val areas=repo.areas().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val tables=repo.tables().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val categories=repo.categories().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val menu=repo.menuItems().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val employees=repo.employees().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val sessions=repo.openSessions().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val bills=repo.paidBills().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val cart=MutableStateFlow<Map<String,Int>>(emptyMap())
    val currentTable=MutableStateFlow<DiningTableEntity?>(null)
    val currentSession=MutableStateFlow<TableSessionEntity?>(null)
    val screen=MutableStateFlow("TABLES")
    init{viewModelScope.launch{seed()}}
    private suspend fun seed(){
        if(areas.value.isNotEmpty())return
        val inside=AreaEntity("inside","Trong nhà",0);val outside=AreaEntity("outside","Ngoài trời",1)
        repo.saveArea(inside);repo.saveArea(outside)
        (1..6).forEach{repo.saveTable(DiningTableEntity("t$it","inside","Bàn %02d".format(it),it))};(7..8).forEach{repo.saveTable(DiningTableEntity("t$it","outside","Bàn %02d".format(it),it))}
        listOf("Cà phê","Ăn sáng","Trà","Sinh tố","Khác").forEachIndexed{i,n->repo.saveCategory(MenuCategoryEntity("c$i",n,i))}
        listOf(MenuItemEntity("m1","c0","Đen đá",25000),MenuItemEntity("m2","c0","Nâu đá",30000),MenuItemEntity("m3","c0","Bạc xỉu",30000),MenuItemEntity("m4","c1","Bún gà",40000),MenuItemEntity("m5","c1","Đùi gà",55000),MenuItemEntity("m6","c1","Cánh gà",45000),MenuItemEntity("m7","c2","Trà mạn",25000),MenuItemEntity("m8","c2","Trà đào",35000)).forEach{repo.saveMenuItem(it)}
        listOf("Tuấn","Hương","Nam").forEachIndexed{i,n->repo.saveEmployee(EmployeeEntity("e$i",n))}
    }
    fun add(item:MenuItemEntity){cart.value=cart.value.toMutableMap().apply{put(item.id,(get(item.id)?:0)+1)}}
    fun sub(item:MenuItemEntity){cart.value=cart.value.toMutableMap().apply{val q=get(item.id)?:0;if(q<=1)remove(item.id)else put(item.id,q-1)}}
    fun selectTable(t:DiningTableEntity,employeeId:String){viewModelScope.launch{currentTable.value=t;val existing=sessions.value.firstOrNull{it.tableId==t.id};currentSession.value=existing?:repo.openSession(t.id,employeeId);cart.value=emptyMap();screen.value="ORDER"}}
    fun addTable(areaId:String){viewModelScope.launch{val n=tables.value.size+1;repo.saveTable(DiningTableEntity(UUID.randomUUID().toString(),areaId,"Bàn %02d".format(n),n))}}
    fun sendBatch(employeeId:String){val s=currentSession.value?:return;val lines=cart.value;if(lines.isEmpty())return;viewModelScope.launch{val existing=dao.batches(s.id).first();val items=lines.mapNotNull{(id,q)->menu.value.firstOrNull{it.id==id}?.let{OrderItemEntity("","",it.id,it.name,it.price,q)}};val b=repo.createBatch(s.id,existing.size+1,employeeId,items);repo.queueKitchenPrint(b);dao.transitionBatch(b.id,"DRAFT","SENT",System.currentTimeMillis());cart.value=emptyMap();screen.value="SENT"}}
    fun batches(sessionId:String)=dao.batches(sessionId)
    fun items(batchId:String)=dao.batchItems(batchId)
    fun close(method:String,cashierId:String,total:Long){val s=currentSession.value?:return;viewModelScope.launch{repo.closeAndPay(s,total,method,cashierId,"0210-${System.currentTimeMillis().toString().takeLast(6)}");currentSession.value=null;currentTable.value=null;screen.value="TABLES"}}
}