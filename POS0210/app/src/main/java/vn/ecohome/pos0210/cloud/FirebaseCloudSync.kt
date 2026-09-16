package vn.ecohome.pos0210.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import vn.ecohome.pos0210.data.CloudSyncStateEntity
import vn.ecohome.pos0210.data.PosDatabase
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class FirebaseConfig(val projectId:String,val applicationId:String,val apiKey:String){
    val valid:Boolean get()=projectId.isNotBlank()&&applicationId.isNotBlank()&&apiKey.isNotBlank()
}
data class CloudDashboard(val openTables:Int=0,val revenueToday:Long=0,val paidBillsToday:Int=0,val lastUpdatedAt:Long=0,val openTableNames:List<String> = emptyList(),val online:Boolean=false,val error:String?=null)
object CloudSyncPolicy{
    val safeSettingKeys=setOf("bank_name","bank_account","bank_holder","qr_prefix","printer_paper_mm","loyalty_auto_tier","member_discount_percent","vip_min_points","vip_discount_percent","vvip_min_points","vvip_discount_percent")
    fun shouldUploadSetting(key:String)=key in safeSettingKeys
}

object FirebaseCloudSync {
    private const val APP_NAME="pos0210-cloud"
    private const val WORK_NAME="pos0210-firebase-periodic"

    suspend fun config(context:Context):FirebaseConfig{
        val settings=PosDatabase.get(context).dao().allSettingsSnapshot().associate{it.key to it.value}
        return FirebaseConfig(settings["firebase_project_id"].orEmpty(),settings["firebase_application_id"].orEmpty(),settings["firebase_api_key"].orEmpty())
    }
    private fun app(context:Context,c:FirebaseConfig):FirebaseApp{
        FirebaseApp.getApps(context).firstOrNull{it.name==APP_NAME}?.let{return it}
        return FirebaseApp.initializeApp(context,FirebaseOptions.Builder().setProjectId(c.projectId).setApplicationId(c.applicationId).setApiKey(c.apiKey).build(),APP_NAME)
            ?: error("Không thể khởi tạo Firebase")
    }
    suspend fun signIn(context:Context,email:String,password:String):String{
        val c=config(context);require(c.valid){"Chưa cấu hình Firebase"}
        return FirebaseAuth.getInstance(app(context,c)).signInWithEmailAndPassword(email.trim(),password).await().user?.uid?:error("Firebase không trả UID")
    }
    fun signOut(context:Context){runCatching{FirebaseApp.getApps(context).firstOrNull{it.name==APP_NAME}?.let{FirebaseAuth.getInstance(it).signOut()}}}
    fun reset(context:Context){runCatching{FirebaseApp.getApps(context).firstOrNull{it.name==APP_NAME}?.delete()}}
    fun currentUid(context:Context):String?=runCatching{FirebaseApp.getApps(context).firstOrNull{it.name==APP_NAME}?.let{FirebaseAuth.getInstance(it).currentUser?.uid}}.getOrNull()

    suspend fun syncNow(context:Context):Result<Unit> = runCatching{
        val db=PosDatabase.get(context);val dao=db.dao();val old=dao.cloudSyncStateSnapshot()?:CloudSyncStateEntity()
        dao.saveCloudSyncState(old.copy(lastAttemptAt=System.currentTimeMillis(),lastError=null))
        val c=config(context);require(c.valid){"Chưa cấu hình Firebase"};val firebaseApp=app(context,c);val uid=FirebaseAuth.getInstance(firebaseApp).currentUser?.uid?:error("Chưa đăng nhập Firebase")
        val fs=FirebaseFirestore.getInstance(firebaseApp);val root=fs.collection("users").document(uid).collection("stores").document("0210")
        val tables=dao.cloudTablesSnapshot();val sessions=dao.cloudSessionsSnapshot();val bills=dao.cloudBillsSnapshot();val payments=dao.cloudPaymentsSnapshot()
        val today=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis
        val paidToday=bills.filter{it.status=="PAID"&&(it.closedAt?:0)>=today};val open=sessions.filter{it.status=="OPEN"};val now=System.currentTimeMillis()
        root.set(mapOf("name" to "0210","updatedAt" to now,"schemaVersion" to 1)).await()
        val openByTable=open.associateBy{it.tableId}
        root.collection("dashboard").document("current").set(mapOf("openTables" to open.size,"openTableNames" to tables.filter{openByTable.containsKey(it.id)}.map{it.name},"revenueToday" to paidToday.sumOf{it.total},"paidBillsToday" to paidToday.size,"lastUpdatedAt" to now)).await()
        writeMaps(fs,root.collection("tableStatus"),tables.map{t->t.id to mapOf("id" to t.id,"name" to t.name,"areaId" to t.areaId,"active" to t.active,"occupied" to openByTable.containsKey(t.id),"openedAt" to openByTable[t.id]?.openedAt,"updatedAt" to now)})
        writeMaps(fs,root.collection("menu"),dao.allMenuSnapshot().map{m->m.id to mapOf("id" to m.id,"categoryId" to m.categoryId,"name" to m.name,"price" to m.price,"sortOrder" to m.sortOrder,"active" to m.active,"productCode" to m.productCode,"description" to m.description)})
        writeMaps(fs,root.collection("areas"),dao.allAreasSnapshot().map{a->a.id to mapOf("id" to a.id,"name" to a.name,"sortOrder" to a.sortOrder,"active" to a.active)})
        writeMaps(fs,root.collection("menuCategories"),dao.allCategoriesSnapshot().map{c0->c0.id to mapOf("id" to c0.id,"name" to c0.name,"sortOrder" to c0.sortOrder,"active" to c0.active)})
        writeMaps(fs,root.collection("sessions"),sessions.map{s->s.id to mapOf("id" to s.id,"tableId" to s.tableId,"openedAt" to s.openedAt,"openedBy" to s.openedBy,"status" to s.status,"version" to s.version)})
        writeMaps(fs,root.collection("orderBatches"),dao.cloudOrderBatchesSnapshot().map{b->b.id to mapOf("id" to b.id,"sessionId" to b.sessionId,"sequence" to b.sequence,"ordererId" to b.ordererId,"createdAt" to b.createdAt,"sentAt" to b.sentAt,"status" to b.status,"serviceNo" to b.serviceNo,"deliveredAt" to b.deliveredAt,"deliveredBy" to b.deliveredBy)})
        writeMaps(fs,root.collection("orderItems"),dao.cloudOrderItemsSnapshot().map{i->i.id to mapOf("id" to i.id,"batchId" to i.batchId,"menuItemId" to i.menuItemId,"itemName" to i.itemNameSnapshot,"unitPrice" to i.unitPriceSnapshot,"qty" to i.qty,"note" to i.note,"adjustmentOfItemId" to i.adjustmentOfItemId)})
        writeMaps(fs,root.collection("bills"),bills.map{b->b.id to mapOf("id" to b.id,"sessionId" to b.sessionId,"billNo" to b.billNo,"openedAt" to b.openedAt,"closedAt" to b.closedAt,"subtotal" to b.subtotal,"total" to b.total,"status" to b.status)})
        writeMaps(fs,root.collection("payments"),payments.map{p->p.id to mapOf("id" to p.id,"billId" to p.billId,"method" to p.method,"amount" to p.amount,"cashierId" to p.cashierId,"paidAt" to p.paidAt,"reference" to p.reference)})
        writeMaps(fs,root.collection("purchases"),dao.cloudPurchasesSnapshot().map{p->p.id to mapOf("id" to p.id,"purchasedAt" to p.purchasedAt,"total" to p.total,"note" to p.note,"status" to p.status,"expenseCategory" to p.expenseCategory)})
        writeMaps(fs,root.collection("monthlyAccounting"),dao.cloudAccountingSnapshot().map{a->a.monthKey to mapOf("monthKey" to a.monthKey,"cogs" to a.cogs,"openingCash" to a.openingCash,"closingCash" to a.closingCashSnapshot,"operatingProfit" to a.operatingProfitSnapshot,"distributableProfit" to a.distributableProfitSnapshot)})
        writeMaps(fs,root.collection("assets"),dao.cloudAssetsSnapshot().map{a->a.id to mapOf("id" to a.id,"name" to a.name,"categoryId" to a.categoryId,"purchaseDate" to a.purchaseDate,"totalCost" to a.totalCost,"usefulLifeMonths" to a.usefulLifeMonths,"residualValue" to a.residualValue,"estimatedLiquidationValue" to a.estimatedLiquidationValue,"status" to a.status,"disposalDate" to a.disposalDate,"disposalPrice" to a.disposalPrice)})
        writeMaps(fs,root.collection("financialMovements"),dao.cloudMovementsSnapshot().map{m->m.id to mapOf("id" to m.id,"type" to m.type,"amount" to m.amount,"occurredAt" to m.occurredAt,"partnerId" to m.partnerId,"method" to m.method,"note" to m.note)})
        val settings=dao.allSettingsSnapshot().filter{CloudSyncPolicy.shouldUploadSetting(it.key)}.associate{it.key to it.value};root.collection("config").document("safe").set(settings+mapOf("updatedAt" to now)).await()
        dao.saveCloudSyncState(old.copy(enabled=true,dirty=false,lastAttemptAt=now,lastSuccessAt=now,lastError=null,syncedUid=uid))
    }.onFailure{e->
        val dao=PosDatabase.get(context).dao();val old=dao.cloudSyncStateSnapshot()?:CloudSyncStateEntity();dao.saveCloudSyncState(old.copy(lastAttemptAt=System.currentTimeMillis(),lastError=e.message?.take(300)))
    }

    suspend fun syncDashboardNow(context:Context):Result<Unit> = runCatching{
        val db=PosDatabase.get(context);val dao=db.dao();val c=config(context);require(c.valid){"Chưa cấu hình Firebase"};val firebaseApp=app(context,c);val uid=FirebaseAuth.getInstance(firebaseApp).currentUser?.uid?:error("Chưa đăng nhập Firebase")
        val fs=FirebaseFirestore.getInstance(firebaseApp);val root=fs.collection("users").document(uid).collection("stores").document("0210")
        val tables=dao.cloudTablesSnapshot();val sessions=dao.cloudSessionsSnapshot();val bills=dao.cloudBillsSnapshot();val open=sessions.filter{it.status=="OPEN"};val openByTable=open.associateBy{it.tableId}
        val today=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis;val paidToday=bills.filter{it.status=="PAID"&&(it.closedAt?:0)>=today};val now=System.currentTimeMillis()
        root.collection("dashboard").document("current").set(mapOf("openTables" to open.size,"openTableNames" to tables.filter{openByTable.containsKey(it.id)}.map{it.name},"revenueToday" to paidToday.sumOf{it.total},"paidBillsToday" to paidToday.size,"lastUpdatedAt" to now)).await()
        writeMaps(fs,root.collection("tableStatus"),tables.map{t->t.id to mapOf("id" to t.id,"name" to t.name,"areaId" to t.areaId,"active" to t.active,"occupied" to openByTable.containsKey(t.id),"openedAt" to openByTable[t.id]?.openedAt,"updatedAt" to now)})
    }

    private suspend fun writeMaps(fs:FirebaseFirestore,collection:com.google.firebase.firestore.CollectionReference,rows:List<Pair<String,Map<String,Any?>>>){
        rows.chunked(400).forEach{chunk->val batch=fs.batch();chunk.forEach{(id,data)->batch.set(collection.document(id),data)};batch.commit().await()}
    }
    fun dashboard(context:Context):Flow<CloudDashboard> = callbackFlow{
        val c=config(context);if(!c.valid){trySend(CloudDashboard(error="Chưa cấu hình Firebase"));close();return@callbackFlow}
        val firebaseApp=app(context,c);val uid=FirebaseAuth.getInstance(firebaseApp).currentUser?.uid
        if(uid==null){trySend(CloudDashboard(error="Chưa đăng nhập Firebase"));close();return@callbackFlow}
        val registration=FirebaseFirestore.getInstance(firebaseApp).collection("users").document(uid).collection("stores").document("0210").collection("dashboard").document("current")
            .addSnapshotListener{doc,error->if(error!=null)trySend(CloudDashboard(error=error.message)) else trySend(CloudDashboard(doc?.getLong("openTables")?.toInt()?:0,doc?.getLong("revenueToday")?:0,doc?.getLong("paidBillsToday")?.toInt()?:0,doc?.getLong("lastUpdatedAt")?:0,(doc?.get("openTableNames") as? List<*>)?.mapNotNull{it as? String}.orEmpty(),true))}
        awaitClose{registration.remove()}
    }
    fun schedule(context:Context){
        val request=PeriodicWorkRequestBuilder<FirebaseSyncWorker>(24,TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME,ExistingPeriodicWorkPolicy.UPDATE,request)
    }
    fun enqueueImmediate(context:Context){
        val request=OneTimeWorkRequestBuilder<FirebaseRealtimeWorker>().setInitialDelay(3,TimeUnit.SECONDS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("pos0210-firebase-immediate",ExistingWorkPolicy.REPLACE,request)
    }
    fun enqueueBackup(context:Context){
        val request=OneTimeWorkRequestBuilder<FirebaseSyncWorker>().setInitialDelay(5,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("pos0210-firebase-backup",ExistingWorkPolicy.REPLACE,request)
    }
}

class FirebaseSyncWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result{
        val state=PosDatabase.get(applicationContext).dao().cloudSyncStateSnapshot()
        if(state?.enabled!=true)return Result.success()
        return if(FirebaseCloudSync.syncNow(applicationContext).isSuccess)Result.success() else Result.retry()
    }
}
class FirebaseRealtimeWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result{
        val state=PosDatabase.get(applicationContext).dao().cloudSyncStateSnapshot()
        if(state?.enabled!=true)return Result.success()
        return if(FirebaseCloudSync.syncDashboardNow(applicationContext).isSuccess)Result.success() else Result.retry()
    }
}
