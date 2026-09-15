Warning: truncated output (original token count: 47829)
Total output lines: 3558

package vn.ecohome.pos0210

import android.net.Uri
import android.Manifest
import android.os.Build
import android.provider.Settings
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import vn.ecohome.pos0210.data.*
import vn.ecohome.pos0210.printing.PrinterText
import vn.ecohome.pos0210.printing.BluetoothPrinter
import vn.ecohome.pos0210.payment.VietQrOffline
import vn.ecohome.pos0210.banknotification.NotificationAccess
import vn.ecohome.pos0210.banknotification.BankPaymentAnnouncer
import java.text.SimpleDateFormat
import java.util.*

private val Cream = Color(0xFFF6F0E6)
private val Coffee = Color(0xFF6E432D)
private val Tint = Color(0xFFF1ECE3)
private val Occupied = Color(0xFFE7C4AA)
private val WaitingDelivery = Color(0xFFF2B777)
private val WaitingPriority1 = Color(0xFFE86A33)
private val WaitingPriority2 = Color(0xFFF3C15F)

private fun money(v: Long) = "%,dđ".format(v).replace(',', '.')
private fun time(v: Long) = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(v))

@Composable
private fun OfflineVietQrImage(
    bank: String,
    account: String,
    holder: String,
    amount: Long,
    info: String,
    modifier: Modifier
) {
    val result = remember(bank, account, holder, amount, info) {
        VietQrOffline.bitmap(bank, account, holder, amount, info)
    }
    result.fold(
        onSuccess = { bitmap -> Image(bitmap.asImageBitmap(), "Mã VietQR thanh toán offline", modifier) },
        onFailure = { error -> Text("KHÔNG TẠO ĐƯỢC VIETQR\n${error.message}", modifier, color = Color.Red, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) }
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Coffee, background = Cream)) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = Cream) { App() }
            }
        }
    }
}

@Composable
fun App(vm: PosViewModel = viewModel()) {
    val sc by vm.screen.collectAsState()
    val t by vm.currentTable.collectAsState()
    val s by vm.currentSession.collectAsState()
    when (sc) {
        "LOGIN" -> Login(vm)
        "TABLES" -> Tables(vm)
        "ORDER" -> t?.let { Order(vm, it) }
        "SENT" -> if (t != null && s != null) Sent(vm, t!!, s!!)
        "PAY" -> if (t != null && s != null) Pay(vm, t!!, s!!)
        "REPORT" -> Report(vm)
        "MANAGE" -> Manage(vm)
        "MENU" -> MenuManager(vm)
        "COMBO" -> ComboManager(vm)
        "PRICING" -> PricingManager(vm)
        "DELIVERY" -> DeliveryQueue(vm)
        "CUSTOMERS" -> Customers(vm)
        "LOYALTY_CONFIG" -> LoyaltyConfig(vm)
        "EMP" -> Employees(vm)
        "PURCHASE" -> Purchases(vm)
        "VIETQR" -> VietQr(vm)
        "PRINTER" -> Printer(vm)
        "TABLE_ADMIN" -> TableManager(vm)
        "BACKUP" -> BackupCenter(vm)
        "HEALTH" -> DataHealth(vm)
        "SETTINGS" -> Settings(vm)
        "BANK_PAYMENT_SETTINGS" -> BankPaymentSettings(vm)
        "BANK_NOTIFICATION_TEST" -> BankNotificationTest(vm)
    }
}

@Composable
fun Header(title: String = "", back: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back == null) {
            Column {
                Text("0210", fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text("BREAKFAST · COFFEE · DRINKS", fontSize = 9.sp)
            }
        } else {
            Text("‹", Modifier.clickable { back() }.padding(6.dp), fontSize = 36.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Login(vm: PosViewModel) {
    var pin by remember { mutableStateOf("") }
    val err by vm.authError.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("0210", fontSize = 48.sp, fontWeight = FontWeight.Black)
        Text("PIN NHÂN VIÊN", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(4) },
            label = { Text("PIN 4 số") },
            visualTransformation = PasswordVisualTransformation()
        )
        if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = { vm.login(pin) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            enabled = pin.length == 4
        ) { Text("ĐĂNG NHẬP") }
    }
}

@Composable
fun Operator(vm: PosViewModel) {
    val e by vm.currentEmployee.collectAsState()
    AssistChip(onClick = { vm.logout() }, label = { Text("🔒 ${e?.name} · Đổi người") })
}

@Composable
fun Tables(vm: PosViewModel) {
    val ts by vm.tables.collectAsState()
    val ss by vm.sessions.collectAsState()
    val waiting by vm.waitingBatches.collectAsState()
    val serviceTimings by vm.tableServiceTimings.collectAsState()
    val areas by vm.areas.collectAsState()
    var timerNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            kotlinx.coroutines.delay((60_000L - now % 60_000L).coerceAtLeast(1_000L))
            timerNow = System.currentTimeMillis()
        }
    }
    val current by vm.currentEmployee.collectAsState()
    val canReport = current?.role == "ADMIN" || current?.canViewReport == true
    val waitingOrdered = waiting.sortedWith(compareBy<OrderBatchEntity> { it.serviceNo }.thenBy { it.createdAt })
    val waitingRankById = waitingOrdered.mapIndexed { index, batch -> batch.id to index }.toMap()

    Column {
        Header()
        Operator(vm)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(true, {}, { Text("BÁN HÀNG") })
            if (canReport) {
                FilterChip(false, { vm.screen.value = "REPORT" }, { Text("LỊCH SỬ") })
                FilterChip(false, { vm.screen.value = "REPORT" }, { Text("BÁO CÁO") })
            }
        }

        val tableCards = ts.map { tb ->
                    val open = ss.firstOrNull { it.tableId == tb.id }
                    val areaName = areas.firstOrNull { it.id == tb.areaId }?.name ?: tb.areaId
                    val waitingForTable = open?.let { s -> waiting.filter { it.sessionId == s.id } } ?: emptyList()
                    val nextService = waitingForTable.minWithOrNull(compareBy<OrderBatchEntity> { it.serviceNo }.thenBy { it.createdAt })
                    val priorityRank = nextService?.let { waitingRankById[it.id] }
                    val serviceTimer = open?.let { session ->
                        serviceTimings.firstOrNull { it.sessionId == session.id }?.let { timing ->
                            serviceTimerPresentation(timing.firstOrderAt, timing.lastOrderSentAt, timing.sentBatchCount, timing.waitingBatchCount, timerNow)
                        }
                    }
                    TableCardUi(tb.id,tb.name,areaName,open!=null,nextService?.serviceNo,priorityRank,serviceTimer)
        }
        ResponsiveTableGrid(tableCards,Modifier.weight(1f).fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp)){id->ts.firstOrNull{it.id==id}?.let(vm::selectTable)}

        if (waiting.isNotEmpty()) {
            Button(
                onClick = { vm.screen.value = "DELIVERY" },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            ) { Text("CHỜ GIAO · ${waiting.size} ĐƠN") }
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ vm.screen.value = "MANAGE" }, Modifier.weight(1f)) { Text("QUẢN LÝ") }
            if (canReport) {
                Button({ vm.screen.value = "REPORT" }, Modifier.weight(1f)) { Text("BÁO CÁO") }
            }
        }
    }
}

@Composable
fun Order(vm: PosViewModel, t: DiningTableEntity) {
    val ms by vm.menu.collectAsState()
    val combos by vm.combos.collectAsState()
    val cats by vm.categories.collectAsState()
    val cart by vm.cart.collectAsState()
    val cartNotes by vm.cartNotes.collectAsState()
    var showCart by remember { mutableStateOf(false) }
    var noteTarget by remember { mutableStateOf<Pair<String,String>?>(null) }
    var selectedCat by remember(cats) { mutableStateOf(cats.firstOrNull()?.id ?: "") }
    val visible = ms.filter { it.active && (selectedCat.isBlank() || it.categoryId == selectedCat) }
    val activeCombos = combos.filter { it.active }
    val itemCount = cart.values.sum()
    val total = cart.entries.sumOf { (id, q) ->
        if (id.startsWith("combo:")) {
            val comboId = id.removePrefix("combo:")
            (combos.firstOrNull { it.id == comboId }?.price ?: 0L) * q
        } else {
            (ms.firstOrNull { it.id == id }?.price ?: 0L) * q
        }
    }

    Column {
        Header(t.name) { vm.screen.value = "TABLES" }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            cats.forEach { cat ->
                FilterChip(selectedCat == cat.id, { selectedCat = cat.id }, { Text(cat.name, fontSize = 11.sp) })
            }
            FilterChip(selectedCat == "__COMBO__", { selectedCat = "__COMBO__" }, { Text("COMBO", fontSize = 11.sp) })
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selectedCat == "__COMBO__") {
                gridItems(activeCombos, key = { "combo:" + it.id }) { combo ->
                    val key = "combo:" + combo.id
                    val q = cart[key] ?: 0
                    val note = cartNotes[key].orEmpty()
                    val parts by vm.comboItems(combo.id).collectAsState(initial = emptyList())
                    Card(
                        Modifier.fillMaxWidth().clickable { vm.addCombo(combo) },
                        colors = CardDefaults.cardColors(containerColor = if(note.isNotBlank()) Color(0xFFFFF0D8) else Color(0xFFFBF8F2))
                    ) {
                        Column {
                            ComboArtwork(combo, parts, ms, Modifier.fillMaxWidth().height(92.dp))
                            Column(Modifier.padding(8.dp)) {
                                Text(combo.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(money(combo.price), fontSize = 12.sp)
                                if (q > 0) {
                                    Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("−", Modifier.clickable { vm.subCombo(combo) }.padding(5.dp), fontWeight = FontWeight.Bold)
                                        Text(q.toString(), fontWeight = FontWeight.Black)
                                        Text("+", Modifier.clickable { vm.addCombo(combo) }.padding(5.dp), fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        if(note.isBlank()) "+ Ghi chú" else "📝 " + note.take(24),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clickable { noteTarget = key to combo.name },
                                        fontSize = 10.sp,
                                        fontWeight = if(note.isBlank()) FontWeight.Normal else FontWeight.Bold,
                                        color = Coffee
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                gridItems(visible, key = { it.id }) { m ->
                    val q = cart[m.id] ?: 0
                    val note = cartNotes[m.id].orEmpty()
                    Card(
                        Modifier.fillMaxWidth().clickable { vm.add(m) },
                        colors = CardDefaults.cardColors(containerColor = if(note.isNotBlank()) Color(0xFFFFF0D8) else Color(0xFFFBF8F2))
                    ) {
                        Column {
                            if (!m.imageUri.isNullOrBlank()) {
                                AsyncImage(model = m.imageUri, contentDescription = m.name, modifier = Modifier.fillMaxWidth().height(92.dp))
                            } else {
                                Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) { Text("0210", fontWeight = FontWeight.Black) }
                            }
                            Column(Modifier.padding(8.dp)) {
                                Text(m.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(money(m.price), fontSize = 12.sp)
                                if (q > 0) {
                                    Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("−", Modifier.clickable { vm.sub(m) }.padding(5.dp), fontWeight = FontWeight.Bold)
                                        Text(q.toString(), fontWeight = FontWeight.Black)
                                        Text("+", Modifier.clickable { vm.add(m) }.padding(5.dp), fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        if(note.isBlank()) "+ Ghi chú" else "📝 " + note.take(24),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clickable { noteTarget = m.id to m.name },
                                        fontSize = 10.sp,
                                        fontWeight = if(note.isBlank()) FontWeight.Normal else FontWeight.Bold,
                                        color = Coffee
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$itemCount món", fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(money(total), fontWeight = FontWeight.Black)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showCart = true }, modifier = Modifier.weight(1f)) { Text("XEM GIỎ HÀNG") }
            Button(onClick = { vm.sendBatch() }, modifier = Modifier.weight(1f), enabled = cart.isNotEmpty()) { Text("GỬI LÀM HÀNG") }
        }
    }
    if (showCart) OrderCartNotesDialog(vm) { showCart = false }
    noteTarget?.let { (key,name) ->
        var noteText by remember(key) { mutableStateOf(cartNotes[key].orEmpty()) }
        AlertDialog(
            onDismissRequest = { noteTarget = null },
            title = { Text("Ghi chú · $name") },
            text = {
                Column {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ví dụ: không hành, ít đá...") },
                        minLines = 2
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Không hành","Ít đá","Không đá","Ít ngọt","Không cay").forEach { quick ->
                            AssistChip(onClick = { noteText = quick }, label = { Text(quick, fontSize = 10.sp) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { vm.setCartNote(key,noteText); noteTarget = null }) { Text("LƯU GHI CHÚ") }
            },
            dismissButton = {
                if(cartNotes[key].orEmpty().isNotBlank()) {
                    TextButton(onClick = { vm.setCartNote(key,""); noteTarget = null }) { Text("XÓA GHI CHÚ") }
                }
            }
        )
    }
}

@Composable
fun Sent(vm: PosViewModel, t: DiningTableEntity, s: TableSessionEntity) {
    val bs by vm.batches(s.id).collectAsState(initial = emptyList())
    val total by vm.total(s.id).collectAsState(initial = 0)
    val current by vm.currentEmployee.collectAsState()
    val printerMessage by vm.printerMessage.collectAsState()
    val printerMode = vm.setting("printer_mode").ifBlank { "TEST" }
    var pv by remember { mutableStateOf<OrderBatchEntity?>(null) }
    var cancelTarget by remember { mutableStateOf<OrderBatchEntity?>(null) }
    var cancelReason by remember { mutableStateOf("") }

    Column {
        Header(t.name) { vm.screen.value = "TABLES" }
        LazyColumn(Modifier.weight(1f)) {
            items(bs) { b ->
                Card(
                    Modifier.fillMaxWidth().padding(8.dp).clickable { pv = b },
                    colors = CardDefaults.cardColors(
                        containerColor = if (b.status == "CANCELLED") Color(0xFFF1DDDA) else Tint
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Đơn #${b.sequence}", fontWeight = FontWeight.Bold)
                        Text(
                            when (b.status) {
                                "CANCELLED" -> "ĐÃ HỦY"
                                "WAITING" -> "CHỜ GIAO · #${b.serviceNo.toString().padStart(3,'0')}"
                                "DELIVERED" -> "ĐÃ GIAO ĐỦ · #${b.serviceNo.toString().padStart(3,'0')}"
                                else -> b.status
                            }
                        )
                    }
                }
            }
        }
        Text("Tạm tính ${money(total)}", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (printerMessage.isNotBlank()) {
            Text(printerMessage, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        val allCancelled = bs.isNotEmpty() && bs.all { it.status == "CANCELLED" }
        val canRelease = allCancelled && total == 0L && (current?.role == "ADMIN" || current?.role == "MANAGER")

        if (canRelease) {
            Button(
                onClick = { vm.releaseCancelledTable() },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) { Text("TRẢ BÀN") }
        } else {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { vm.addMore() }, modifier = Modifier.weight(1f)) {
                    Text("＋ GỌI THÊM")
                }
                Button(onClick = { vm.screen.value = "PAY" }, modifier = Modifier.weight(1f), enabled = total > 0) {
                    Text("THANH TOÁN")
                }
            }
        }
    }

    pv?.let { b ->
        val its by vm.items(b.id).collectAsState(initial = emptyList())
        AlertDialog(
            onDismissRequest = { pv = null },
            confirmButton = {
                if (b.status == "DRAFT") {
                    Button(onClick = { vm.markBatchSent(b); pv = null }) {
                        Text(if (printerMode == "BLUETOOTH") "IN & GỬI BẾP" else "XÁC NHẬN GỬI (TEST)")
                    }
                } else {
                    Button(onClick = { pv = null }) { Text("ĐÓNG") }
                }
            },
            dismissButton = {},
            title = { Text("Đơn #${b.sequence} · ${b.status}") },
            text = {
                Column {
                    Text("0210 · ${t.name}")
                    Text("STT phục vụ: #${b.serviceNo.toString().padStart(3,'0')}", fontWeight = FontWeight.Black)
                    its.forEach { item ->
                        Text("${item.qty} × ${item.itemNameSnapshot}")
                        if(item.note.isNotBlank()) Text("↳ ${item.note}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (b.status == "WAITING") {
                        Button(
                            onClick = { vm.markDelivered(b); pv = null },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) { Text("✓ ĐÃ GIAO ĐỦ") }
                    }
                    if (b.status != "CANCELLED" && b.status != "DELIVERED" && (current?.role == "ADMIN" || current?.role == "MANAGER")) {
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { cancelTarget = b; cancelReason = ""; pv = null },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("HỦY ĐƠN · MANAGER") }
                    }
                }
            }
        )
    }

    cancelTarget?.let { b ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("Hủy Đơn #${b.sequence}") },
            text = {
                Column {
                    Text("Chỉ hủy khi bếp chưa làm. Đơn vẫn được lưu trong lịch sử.")
                    OutlinedTextField(
                        cancelReason,
                        { cancelReason = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        label = { Text("Lý do hủy bắt buộc") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.cancelBatch(b, cancelReason)
                        cancelTarget = null
                        cancelReason = ""
                    },
                    enabled = cancelReason.isNotBlank()
                ) { Text("XÁC NHẬN HỦY") }
            },
            dismissButton = { TextButton(onClick = { cancelTarget = null }) { Text("KHÔNG HỦY") } }
        )
    }
}

private fun pricingRuleInTime(rule: PricingRuleEntity, now: Long): Boolean {
    if (!rule.active) return false
    if (rule.startAt != null && now < rule.startAt) return false
    if (rule.endAt != null && now > rule.endAt) return false
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val start = rule.startMinute
    val end = rule.endMinute
    if (start != null && end != null) {
        val ok = if (start <= end) minute in start..end else minute >= start || minute <= end
        if (!ok) return false
    }
    return true
}

private fun settingValue(settings: List<AppSettingEntity>, key: String, fallback: String): String =
    settings.firstOrNull { it.key == key }?.value?.ifBlank { fallback } ?: fallback

private fun effectiveCustomerTier(customer: CustomerEntity?, settings: List<AppSettingEntity>, newMember: Boolean = false): String? {
    if (customer == null) return if (newMember) "MEMBER" else null
    if (customer.tierManual) return customer.tier
    val auto = settingValue(settings, "loyalty_auto_tier", "true").toBoolean()
    if (!auto) return customer.tier
    val vip = settingValue(settings, "vip_min_points", "200").toIntOrNull() ?: 200
    val vvip = settingValue(settings, "vvip_min_points", "500").toIntOrNull() ?: 500
    return when {
        customer.points >= vvip -> "VVIP"
        customer.points >= vip -> "VIP"
        else -> "MEMBER"
    }
}

private fun tierDiscountRule(tier: String?, settings: List<AppSettingEntity>): PricingRuleEntity? {
    if (tier == null) return null
    val key = when (tier) {
        "VVIP" -> "vvip_discount_percent"
        "VIP" -> "vip_discount_percent"
        else -> "member_discount_percent"
    }
    val pct = settingValue(settings, key, "0").toIntOrNull()?.coerceIn(0,100) ?: 0
    if (pct <= 0) return null
    return PricingRuleEntity(
        id = "LOYALTY_$tier",
        name = "ƯU ĐÃI HẠNG $tier",
        kind = "DISCOUNT",
        percent = pct,
        autoApply = true,
        active = true
    )
}

private fun calculatePricing(subtotal: Long, rules: List<PricingRuleEntity>, enteredCode: String, now: Long = System.currentTimeMillis()): PricingPreview {
    val code = enteredCode.trim().uppercase()
    val eligible = rules.filter { pricingRuleInTime(it, now) }
    val codeMatches = if (code.isBlank()) emptyList() else eligible.filter { it.code.isNotBlank() && it.code.equals(code, true) }
    val surchargeRules = eligible.filter { it.kind == "SURCHARGE" && (it.autoApply || it in codeMatches) }
    val surcharge = surchargeRules.sumOf { subtotal * it.percent / 100L }
    val afterSurcharge = subtotal + surcharge
    val discountCandidates = eligible.filter { it.kind == "DISCOUNT" && (it.autoApply || it in codeMatches) }
    val bestDiscount = discountCandidates.maxByOrNull { afterSurcharge * it.percent / 100L }
    val discount = bestDiscount?.let { afterSurcharge * it.percent / 100L } ?: 0L
    val total = (afterSurcharge - discount).coerceAtLeast(0L)
    val message = when {
        code.isNotBlank() && codeMatches.isEmpty() -> "Mã không hợp lệ hoặc đã hết thời gian áp dụng."
        code.isNotBlank() && bestDiscount != null && bestDiscount !in codeMatches -> "Mã hợp lệ nhưng hệ thống đang áp dụng ưu đãi lớn hơn: ${bestDiscount.name}."
        code.isNotBlank() && bestDiscount != null -> "Đã áp dụng ưu đãi tốt nhất: ${bestDiscount.name}."
        else -> ""
    }
    return PricingPreview(subtotal, surcharge, discount, total, surchargeRules, bestDiscount, message)
}

@Composable
fun Pay(vm: PosViewModel, t: DiningTableEntity, s: TableSessionEntity) {
    val subtotal by vm.total(s.id).collectAsState(initial = 0)
    val rules by vm.pricingRules.collectAsState()
    val e by vm.currentEmployee.collectAsState()
    val settings by vm.settings.collectAsState()
    val sessionBatches by vm.batches(s.id).collectAsState(initial = emptyList())
    val pendingDelivery = sessionBatches.filter { it.status == "DRAFT" || it.status == "WAITING" }.sortedBy { it.serviceNo }
    fun setting(key: String) = settings.firstOrNull { it.key == key }?.value ?: ""
    var method by remember { mutableStateOf("CASH") }
    var codeText by remember { mutableStateOf("") }
    var appliedCode by remember { mutableStateOf("") }
    val customers by vm.customers.collectAsState()
    var customerPhone by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    val normalizedPhone = customerPhone.filter(Char::isDigit)
    val matchedCustomer = customers.firstOrNull { it.phone == normalizedPhone }
    val effectiveTier = effectiveCustomerTier(
        matchedCustomer,
        settings,
        newMember = matchedCustomer == null && normalizedPhone.length >= 9
    )
    val loyaltyRule = tierDiscountRule(effectiveTier, settings)
    val preview = calculatePricing(subtotal, rules + listOfNotNull(loyaltyRule), appliedCode)
    val paymentSession by vm.paymentSession(s.id).collectAsState(initial = null)
    val bankEvents by vm.recentBankNotifications.collectAsState()
    LaunchedEffect(method, preview.total, s.id) { if(method=="TRANSFER")vm.openPaymentSession(s,t,preview.total) }
    val validPaymentSession=paymentSession?.takeIf{it.expectedAmount==preview.total}
    val shortTable=t.name.filter(Char::isLetterOrDigit).takeLast(3).uppercase()
    val qrInfo = "${setting("qr_prefix").ifBlank { "0210" }} $shortTable ${validPaymentSession?.paymentCode.orEmpty()}".trim()
    val qrConfigured = setting("bank_name").isNotBlank() && setting("bank_account").isNotBlank()
    val ambiguousEvent=bankEvents.firstOrNull{it.matchStatus=="AMBIGUOUS"&&it.amount==preview.total&&it.receivedAt>=s.openedAt}

    Column {
        Header("Thanh toán") { vm.screen.value = "SENT" }
        Column(Modifier.weight(1f).padding(18.dp).verticalScroll(rememberScrollState())) {
            if (pendingDelivery.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = WaitingPriority2)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("CÒN ĐƠN CHƯA HOÀN TẤT", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            "Không thể thanh toán khi còn đơn chưa gửi bếp hoặc chưa xác nhận giao đủ.",
                            Modifier.padding(top = 4.dp),
                            fontSize = 12.sp
                        )
                        Text(
                            pendingDelivery.joinToString(" · ") { batch ->
                                "#${batch.serviceNo.toString().padStart(3,'0')} ${if(batch.status=="DRAFT") "CHƯA GỬI" else "CHỜ GIAO"}"
                            },
                            Modifier.padding(top = 8.dp),
                            fontWeight = FontWeight.Black
                        )
                        if (pendingDelivery.none { it.status == "DRAFT" }) {
                            Button(
                                onClick = { vm.confirmAllDelivered(s.id) },
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            ) { Text("✓ XÁC NHẬN ĐÃ GIAO ĐỦ") }
                        } else {
                            OutlinedButton(
                                onClick = { vm.screen.value = "SENT" },
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            ) { Text("QUAY LẠI GỬI BẾP") }
                        }
                    }
                }
            }
            Text("TẠM TÍNH")
            Text(money(preview.subtotal), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (preview.surcharge > 0) Text("Phụ thu: +${money(preview.surcharge)}", fontWeight = FontWeight.Bold)
            preview.surchargeRules.forEach { Text("• ${it.name} +${it.percent}%", fontSize = 12.sp) }
            if (preview.discount > 0) {
                Text("Ưu đãi: -${money(preview.discount)}", fontWeight = FontWeight.Bold)
                preview.discountRule?.let { Text("• ${it.name} -${it.percent}%", fontSize = 12.sp) }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text("THÀNH TIỀN")
            Text(money(preview.total), fontSize = 38.sp, fontWeight = FontWeight.Black)

            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = codeText,
                    onValueChange = { codeText = it.uppercase().take(30) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Mã ưu đãi") },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { appliedCode = codeText.trim().uppercase() }) { Text("ÁP DỤNG") }
            }
            if (appliedCode.isNotBlank()) {
                TextButton(onClick = { appliedCode = ""; codeText = "" }) { Text("BỎ MÃ") }
            }
            if (preview.message.isNotBlank()) Text(preview.message, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Các ưu đãi giảm giá không cộng dồn; hệ thống chỉ chọn mức giảm lớn nhất.", fontSize = 11.sp)

            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text("KHÁCH HÀNG / TÍCH ĐIỂM", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = customerPhone,
                onValueChange = { customerPhone = it.filter(Char::isDigit).take(15) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Số điện thoại khách · không bắt buộc") },
                singleLine = true
            )
            if (matchedCustomer != null) {
                Text("${effectiveTier ?: matchedCustomer.tier} · ${matchedCustomer.points} điểm · ${matchedCustomer.visitCount} lần ghé", fontWeight = FontWeight.Bold)
                if (matchedCustomer.tierManual) Text("Hạng đặc biệt do Admin gán", fontSize = 11.sp)
                if (matchedCustomer.name.isNotBlank()) Text(matchedCustomer.name)
                loyaltyRule?.let { Text("Ưu đãi hạng: -${it.percent}%", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            } else if (normalizedPhone.length >= 9) {
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tên khách mới · tùy chọn") }
                )
                Text("Khách mới · sau thanh toán sẽ tạo Member", fontSize = 11.sp)
            }
            Text("Tích điểm: 10.000đ thực trả = 1 điểm", fontSize = 11.sp)

            Row {
                FilterChip(method == "CASH", { method = "CASH" }, { Text("TIỀN MẶT") })
                Spacer(Modifier.width(8.dp))
                FilterChip(method == "TRANSFER", { method = "TRANSFER" }, { Text("CHUYỂN KHOẢN") })
            }
            Text("🔒 Thu tiền: ${e?.name}", Modifier.padding(vertical = 14.dp))
            if (method == "TRANSFER") {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("VIETQR", fontWeight = FontWeight.Bold)
                        if (!qrConfigured) Text("Chưa cấu hình tài khoản VietQR") else if(validPaymentSession==null) Text("Đang tạo mã thanh toán riêng cho bill…") else {
                            OfflineVietQrImage(setting("bank_name"), setting("bank_account"), setting("bank_holder"), preview.total, qrInfo, Modifier.size(280.dp))
                            Text("${setting("bank_name")} · ${setting("bank_account")}")
                            Text("${money(preview.total)} · $qrInfo")
                        }
                    }
                }
                if(validPaymentSession?.status=="PAYMENT_DETECTED"){
                    Card(Modifier.fillMaxWidth().padding(top=10.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFDCE8D8))){
                        Column(Modifier.padding(14.dp)){
                            Text("✓ ĐÃ PHÁT HIỆN THANH TOÁN",fontWeight=FontWeight.Black)
                            Text("Đã nhận: ${money(validPaymentSession.detectedAmount?:preview.total)}")
                            Text("Ngân hàng: ${validPaymentSession.detectedBank.orEmpty()}",fontSize=12.sp)
                            Text("Nhân viên vẫn phải xác nhận để đóng bill.",fontSize=11.sp)
                        }
                    }
                }else if(ambiguousEvent!=null){
                    Text("Đã phát hiện giao dịch cùng số tiền nhưng chưa xác định được bill.",Modifier.padding(top=8.dp),color=Color(0xFF9A5B28),fontSize=12.sp,fontWeight=FontWeight.Bold)
                }else Text("Đang chờ thông báo ngân hàng · vẫn có thể xác nhận thủ công",Modifier.padding(top=8.dp),fontSize=11.sp)
            }
        }
        Button(
            onClick = { vm.close(method, preview, customerPhone, customerName) },
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            enabled = preview.total > 0 && pendingDelivery.isEmpty()
        ) {
            Text(if (pendingDelivery.isNotEmpty()) "CHƯA GIAO ĐỦ · CHƯA THỂ THANH TOÁN" else if(method=="TRANSFER"&&validPaymentSession?.status=="PAYMENT_DETECTED") "XÁC NHẬN ĐÃ THANH TOÁN" else "XÁC NHẬN THANH TOÁN")
        }
    }
}

@Composable
fun DeliveryQueue(vm: PosViewModel) {
    val waiting by vm.waitingBatches.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val tables by vm.tables.collectAsState()
    Column {
        Header("Đơn chờ giao") { vm.screen.value = "TABLES" }
        if (waiting.isEmpty()) {
            Text("Không còn đơn chờ giao.", Modifier.padding(20.dp))
        } else {
            val ordered = waiting.sortedWith(compareBy<OrderBatchEntity> { it.serviceNo }.thenBy { it.createdAt })
            LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                itemsIndexed(ordered, key = { _, b -> b.id }) { index, b ->
                    val session = sessions.firstOrNull { it.id == b.sessionId }
                    val table = session?.let { s -> tables.firstOrNull { it.id == s.tableId } }
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (index) {
                                0 -> WaitingPriority1
                                1 -> WaitingPriority2
                                else -> WaitingDelivery
                            }
                        )
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "#${b.serviceNo.toString().padStart(3,'0')} · ${table?.name ?: "Bàn"}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    when (index) {
                                        0 -> "ƯU TIÊN GIAO TRƯỚC"
                                        1 -> "ƯU TIÊN KẾ TIẾP"
                                        else -> "ĐANG CHỜ GIAO"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Đơn #${b.sequence} · ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(b.createdAt))}")
                            }
                            Button(onClick = { vm.markDelivered(b) }) { Text("ĐÃ GIAO ĐỦ") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Customers(vm: PosViewModel) {
    val customers by vm.customers.collectAsState()
    val itemStats by vm.customerItemStats.collectAsState()
    val bills by vm.bills.collectAsState()
    val current by vm.currentEmployee.collectAsState()
    val updateMessage by vm.customerUpdateMessage.collectAsState()
    var selected by remember { mutableStateOf<CustomerEntity?>(null) }
    var editTarget by remember { mutableStateOf<CustomerEntity?>(null) }
    var query by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("LIST") }

    val normalizedQuery = query.trim().lowercase()
    val visibleCustomers = customers.filter { c ->
        normalizedQuery.isBlank() || c.phone.contains(normalizedQuery.filter(Char::isDigit)) || c.name.lowercase().contains(normalizedQuery)
    }

    fun aggregatedRows(customerIds: Set<String>, combos: Boolean): List<Pair<String, Int>> {
        return itemStats
            .filter { it.customerId in customerIds }
            .filter { if (combos) it.name.startsWith("COMBO ·") else !it.name.startsWith("COMBO ·") }
            .groupBy { it.name }
            .map { (name, rows) -> name to rows.sumOf { it.qty } }
            .sortedByDescending { it.second }
    }

    Column {
        Header("Khách hàng") { vm.screen.value = "MANAGE" }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(section == "LIST", { section = "LIST" }, { Text("DANH SÁCH") })
            FilterChip(section == "ANALYTICS", { section = "ANALYTICS" }, { Text("PHÂN TÍCH THÀNH VIÊN") })
        }

        if (section == "LIST") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(50) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                leadingIcon = { Text("🔍") },
                label = { Text("Tìm số điện thoại hoặc tên khách") },
                singleLine = true
            )
            Text(
                "${visibleCustomers.size} / ${customers.size} khách · Member / VIP / VVIP",
                Modifier.padding(horizontal = 16.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (visibleCustomers.isEmpty()) {
                Text("Không tìm thấy khách phù hợp.", Modifier.padding(20.dp))
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                    items(visibleCustomers, key = { it.id }) { c ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selected = c }) {
                            Column(Modifier.padding(14.dp)) {
                                Text("${c.phone} · ${c.tier}", fontWeight = FontWeight.Black)
                                if (c.name.isNotBlank()) Text(c.name)
                                if (c.address.isNotBlank()) Text(c.address, fontSize = 11.sp)
                                Text("${c.points} điểm · ${c.visitCount} lần ghé · ${money(c.totalSpend)}")
                                c.lastVisitAt?.let { Text("Gần nhất: ${time(it)}", fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                item {
                    Text("PHÂN TÍCH KHÁCH THÀNH VIÊN", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("Tỷ lệ món tính theo tổng số phần đã gọi trong các bill còn hiệu lực.", fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                }
                items(listOf("MEMBER","VIP","VVIP")) { tier ->
                    val tierCustomers = customers.filter { it.tier == tier }
                    val ids = tierCustomers.map { it.id }.toSet()
                    val foodRows = aggregatedRows(ids, false)
                    val comboRows = aggregatedRows(ids, true)
                    val totalQty = foodRows.sumOf { it.second }
                    val tierBills = bills.filter { it.customerId in ids }
                    val avgBill = if (tierBills.isEmpty()) 0L else tierBills.sumOf { it.total } / tierBills.size
                    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("$tier · ${tierCustomers.size} khách", fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text("Bill TB: ${money(avgBill)} · ${tierBills.size} bill", fontSize = 12.sp)
                            if (foodRows.isEmpty()) {
                                Text("Chưa đủ dữ liệu món.", Modifier.padding(top = 8.dp), fontSize = 12.sp)
                            } else {
                                Text("Top món", Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
                                foodRows.take(5).forEach { row ->
                                    val pct = if (totalQty == 0) 0 else ((row.second * 100.0 / totalQty) + 0.5).toInt()
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                        Text(row.first, Modifier.weight(1f), fontSize = 12.sp)
                                        Text("${row.second} phần · $pct%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            comboRows.firstOrNull()?.let { combo ->
                                Text("Combo hay gọi: ${combo.first} · ${combo.second} lần", Modifier.padding(top = 7.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { c ->
        val rows = itemStats.filter { it.customerId == c.id }
        val foodRows = rows
            .filter { !it.name.startsWith("COMBO ·") }
            .groupBy { it.name }
            .map { (name, rs) -> name to rs.sumOf { it.qty } }
            .sortedByDescending { it.second }
        val comboRows = rows
            .filter { it.name.startsWith("COMBO ·") }
            .groupBy { it.name }
            .map { (name, rs) -> name to rs.sumOf { it.qty } }
            .sortedByDescending { it.second }
        val totalFoodQty = foodRows.sumOf { it.second }
        val customerBills = bills.filter { it.customerId == c.id }
        val avgBill = if (customerBills.isEmpty()) 0L else customerBills.sumOf { it.total } / customerBills.size

        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(if (c.name.isBlank()) c.phone else "${c.name} · ${c.phone}") },
            text = {
                LazyColumn {
                    item {
                        Text("Hạng: ${c.tier}", fontWeight = FontWeight.Black)
                        if (c.name.isNotBlank()) Text("Tên: ${c.name}")
                        Text("SĐT: ${c.phone}")
                        if (c.address.isNotBlank()) Text("Địa chỉ: ${c.address}")
                        OutlinedButton(
                            onClick = { editTarget = c },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) { Text("CHỈNH SỬA THÔNG TIN") }
                        Text("Tổng chi tiêu: ${money(c.totalSpend)}")
                        Text("Điểm: ${c.points}")
                        Text("Lượt ghé: ${c.visitCount}")
                        Text("Bill trung bình: ${money(avgBill)}")
                        c.lastVisitAt?.let { Text("Lần ghé gần nhất: ${time(it)}") }
                        foodRows.firstOrNull()?.let { Text("Món gọi nhiều nhất: ${it.first}", Modifier.padding(top = 6.dp), fontWeight = FontWeight.Bold) }
                        comboRows.firstOrNull()?.let { Text("Combo hay gọi nhất: ${it.first}", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(10.dp))
                        Text("MÓN THƯỜNG GỌI", fontWeight = FontWeight.Black)
                    }
                    if (foodRows.isEmpty()) {
                        item { Text("Chưa có đủ lịch sử món.", Modifier.padding(vertical = 6.dp)) }
                    } else {
                        items(foodRows.take(8)) { row ->
                            val pct = if (totalFoodQty == 0) 0 else ((row.second * 100.0 / totalFoodQty) + 0.5).toInt()
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(row.first, Modifier.weight(1f), fontSize = 12.sp)
                                Text("${row.second} phần · $pct%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    item {
                        Text("HẠNG KHÁCH", Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold)
                        Text(if (c.tierManual) "Hạng đặc biệt do Admin gán" else "Đang tự động theo điểm", fontSize = 11.sp)
                        if (current?.role == "ADMIN") {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("MEMBER","VIP","VVIP").forEach { tier ->
                                    FilterChip(c.tier == tier, { vm.setCustomerTier(c,tier); selected = c.copy(tier=tier,tierManual=true) }, { Text(tier) })
                                }
                            }
                            if (c.tierManual) {
                                OutlinedButton(
                                    onClick = { vm.setCustomerTierAutomatic(c); selected = null },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) { Text("CHUYỂN VỀ TỰ ĐỘNG THEO ĐIỂM") }
                            }
                        } else {
                            Text("Chỉ Admin được thay đổi hạng khách.", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { selected = null }) { Text("ĐÓNG") } }
        )
    }

    editTarget?.let { target ->
        CustomerEditDialog(
            vm = vm,
            customer = target,
            onDismiss = { editTarget = null },
            onSaved = { updated -> selected = updated; editTarget = null }
        )
    }
}
@Composable
fun CustomerEditDialog(vm: PosViewModel, customer: CustomerEntity, onDismiss: () -> Unit, onSaved: (CustomerEntity) -> Unit) {
    var name by remember(customer.id) { mutableStateOf(customer.name) }
    var phone by remember(customer.id) { mutableStateOf(customer.phone) }
    var address by remember(customer.id) { mutableStateOf(customer.address) }
    val message by vm.customerUpdateMessage.collectAsState()
    AlertDialog(
        onDismissRequest = { vm.clearCustomerUpdateMessage(); onDismiss() },
        title = { Text("Cập nhật khách hàng") },
        text = {
            Column {
                OutlinedTextField(name, { name = it.take(50) }, modifier = Modifier.fillMaxWidth(), label = { Text("Tên khách") })
                OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(15) }, modifier = Modifier.fillMaxWidth(), label = { Text("Số điện thoại") })
                OutlinedTextField(address, { address = it.take(150) }, modifier = Modifier.fillMaxWidth(), label = { Text("Địa chỉ · nếu có") }, minLines = 2)
                if (message.isNotBlank()) Text(message, Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.updateCustomerProfile(customer,name,phone,address)
                onSaved(customer.copy(name=name.trim(),phone=phone.filter(Char::isDigit),address=address.trim()))
            }, enabled = phone.filter(Char::isDigit).length >= 9) { Text("LƯU") }
        },
        dismissButton = { TextButton(onClick = { vm.clearCustomerUpdateMessage(); onDismiss() }) { Text("HỦY") } }
    )
}

@Composable
fun LoyaltyConfig(vm: PosViewModel) {
    val settings by vm.settings.collectAsState()
    fun current(key: String, fallback: String) = settingValue(settings,key,fallback)
    var autoTier by remember(settings) { mutableStateOf(current("loyalty_auto_tier","true").toBoolean()) }
    var memberDiscount by remember(settings) { mutableStateOf(current("member_discount_percent","0")) }
    var vipPoints by remember(settings) { mutableStateOf(current("vip_min_points","200")) }
    var vipDiscount by remember(settings) { mutableStateOf(current("vip_discount_percent","5")) }
    var vvipPoints by remember(settings) { mutableStateOf(current("vvip_min_points","500")) }
    var vvipDiscount by remember(settings) { mutableStateOf(current("vvip_discount_percent","10")) }
    var saved by remember { mutableStateOf(false) }

    Column {
        Header("Cấu hình hạng thành viên") { vm.screen.value = "MANAGE" }
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("TỰ ĐỘNG NÂNG HẠNG", fontWeight = FontWeight.Black)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Theo điểm tích lũy", Modifier.weight(1f))
                        Switch(checked = autoTier, onCheckedChange = { autoTier = it })
                    }
                    Text("Hạng Admin gán thủ công cho khách đặc biệt sẽ không bị hệ thống tự thay đổi.", fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            TierConfigCard("MEMBER", "0", memberDiscount, { memberDiscount = it }, null, null)
            Spacer(Modifier.height(8.dp))
            TierConfigCard("VIP", vipPoints, vipDiscount, { vipDiscount = it }, vipPoints) { vipPoints = it }
            Spacer(Modifier.height(8.dp))
            TierConfigCard("VVIP", vvipPoints, vvipDiscount, { vvipDiscount = it }, vvipPoints) { vvipPoints = it }
            Button(
                onClick = {
                    vm.saveLoyaltyConfig(
                        autoTier,
                        memberDiscount.toIntOrNull() ?: 0,
                        vipPoints.toIntOrNull() ?: 200,
                        vipDiscount.toIntOrNull() ?: 0,
                        vvipPoints.toIntOrNull() ?: 500,
                        vvipDiscount.toIntOrNull() ?: 0
                    )
                    saved = true
                },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            ) { Text("LƯU CẤU HÌNH") }
            if (saved) Text("Đã lưu cấu hình hạng thành viên.", Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
            Text(
                "Quy tắc: ưu đãi theo hạng tham gia cùng mã giảm/Happy Hour; hệ thống vẫn chỉ chọn 1 mức giảm lớn nhất.",
                Modifier.padding(top = 12.dp),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun TierConfigCard(
    tier: String,
    defaultPoints: String,
    discount: String,
    onDiscount: (String) -> Unit,
    points: String?,
    onPoints: ((String) -> Unit)?
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(tier, fontSize = 20.sp, fontWeight = FontWeight.Black)
            if (points != null && onPoints != null) {
                OutlinedTextField(
                    value = points,
                    onValueChange = { onPoints(it.filter(Char::isDigit).take(7)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tự lên hạng khi đạt số điểm") },
                    singleLine = true
                )
            } else {
                Text("Hạng mặc định khi khách đăng ký số điện thoại", fontSize = 12.sp)
            }
            OutlinedTextField(
                value = discount,
                onValueChange = { onDiscount(it.filter(Char::isDigit).take(3)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("% ưu đãi theo hạng") },
                singleLine = true
            )
        }
    }
}

@Composable
fun Manage(vm: PosViewModel) {
    val employee by vm.currentEmployee.collectAsState()
    Column {
        Header("Quản lý") { vm.screen.value = "TABLES" }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())
        ) {
            if (employee?.role == "ADMIN" || employee?.canManageMenu == true) {
                Rowx("Quản lý menu", "Thêm món · ảnh · nhóm món") { vm.screen.value = "MENU" }
                Rowx("Combo", "Tạo combo từ các món đang có · giá combo · bật/tắt") { vm.screen.value = "COMBO" }
            }
            if (employee?.role == "ADMIN") {
                Rowx("Ưu đãi & điều chỉnh giá", "Mã giảm giá · Happy Hour · phụ thu ngày lễ · thời hạn") { vm.screen.value = "PRICING" }
            }
            Rowx("Khách hàng", "Tra cứu · cập nhật tên/SĐT/địa chỉ · Member/VIP/VVIP") { vm.screen.value = "CUSTOMERS" }
            if (employee?.role == "ADMIN") {
                Rowx("Cấu hình hạng thành viên", "Ngưỡng điểm · tự nâng hạng · % ưu đãi Member/VIP/VVIP") { vm.screen.value = "LOYALTY_CONFIG" }
                Rowx("Nhân viên", "Thêm · khóa · phân quyền") { vm.screen.value = "EMP" }
            }
            if (employee?.role == "ADMIN" || employee?.canManageSystem == true) {
                Rowx("Bàn & khu vực", "Thêm · sửa · Trong nhà / Ngoài trời") { vm.screen.value = "TABLE_ADMIN" }
            }
            if (employee?.role == "ADMIN" || employee?.canPurchase == true) {
                Rowx("Nhập đầu vào", "Lương · vật tư cố định · v…17829 tokens truncated…("bank_name", bank)
                vm.saveSetting("bank_account", acc)
                vm.saveSetting("bank_holder", holder)
                vm.saveSetting("qr_prefix", "0210")
                saved = true
            }) { Text("LƯU") }
            if (saved) Text("Đã lưu cấu hình VietQR", Modifier.padding(top = 8.dp))
            if (bank.isNotBlank() && acc.isNotBlank()) {
                Text("QR mẫu 1.000đ", Modifier.padding(top = 14.dp))
                OfflineVietQrImage(bank, acc, holder, 1000, "0210 TEST", Modifier.size(240.dp))
            }
        }
    }
}

@Composable
fun Printer(vm: PosViewModel) {
    val preview by vm.printerPreview.collectAsState()
    val settings by vm.settings.collectAsState()
    val message by vm.printerMessage.collectAsState()
    val context = LocalContext.current
    var permissionTick by remember { mutableStateOf(0) }
    val mode = settings.firstOrNull { it.key == "printer_mode" }?.value ?: "TEST"
    val selectedMac = settings.firstOrNull { it.key == "printer_mac" }?.value ?: ""
    val selectedName = settings.firstOrNull { it.key == "printer_name" }?.value ?: ""
    val paperMm = settings.firstOrNull { it.key == "printer_paper_mm" }?.value ?: "58"
    val hasPermission = remember(permissionTick) { BluetoothPrinter.hasPermission(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionTick++ }

    val paired = remember(permissionTick, selectedMac, mode) {
        if (BluetoothPrinter.hasPermission(context)) BluetoothPrinter.pairedDevices(context) else emptyList()
    }

    Column {
        Header("Máy in") { vm.screen.value = "MANAGE" }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Text("MÁY IN BLUETOOTH ESC/POS", fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text(if(paperMm=="80") "80mm · 576 dots · Bluetooth ESC/POS" else "58mm · vùng in 48mm · 203dpi · 384 dots · Bluetooth ESC/POS", fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Text("KHỔ GIẤY",fontWeight=FontWeight.Bold,fontSize=12.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    FilterChip(selected=paperMm=="58",onClick={vm.saveSetting("printer_paper_mm","58")},label={Text("58 mm")})
                    FilterChip(selected=paperMm=="80",onClick={vm.saveSetting("printer_paper_mm","80")},label={Text("80 mm")})
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == "TEST",
                        onClick = { vm.saveSetting("printer_mode","TEST") },
                        label = { Text("TEST / PREVIEW") }
                    )
                    FilterChip(
                        selected = mode == "BLUETOOTH",
                        onClick = { vm.saveSetting("printer_mode","BLUETOOTH") },
                        label = { Text("BLUETOOTH") }
                    )
                }

                if (mode == "BLUETOOTH") {
                    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Máy đang chọn", fontWeight = FontWeight.Bold)
                            Text(if (selectedMac.isBlank()) "CHƯA CHỌN" else "${selectedName.ifBlank { "Bluetooth printer" }} · $selectedMac")
                            Spacer(Modifier.height(8.dp))

                            if (!hasPermission) {
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.BLUETOOTH_CONNECT,
                                                    Manifest.permission.BLUETOOTH_SCAN
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("CẤP QUYỀN BLUETOOTH") }
                            } else {
                                if (paired.isEmpty()) {
                                    Text("Chưa thấy máy đã ghép đôi.", fontSize = 12.sp)
                                } else {
                                    paired.forEach { d ->
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .clickable {
                                                    vm.saveSetting("printer_mac",d.address)
                                                    vm.saveSetting("printer_name",d.name)
                                                }
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = selectedMac == d.address,
                                                onClick = {
                                                    vm.saveSetting("printer_mac",d.address)
                                                    vm.saveSetting("printer_name",d.name)
                                                }
                                            )
                                            Column {
                                                Text(d.name, fontWeight = FontWeight.Bold)
                                                Text(d.address, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("MỞ CÀI ĐẶT BLUETOOTH") }

                            Button(
                                onClick = { vm.testBluetoothPrint() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = hasPermission && selectedMac.isNotBlank()
                            ) { Text("TEST IN") }
                        }
                    }
                }

                if (message.isNotBlank()) {
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(message, Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                    }
                }

                Text("XEM TRƯỚC PHIẾU", fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton({ vm.previewKitchen() }, Modifier.weight(1f)) { Text("PHIẾU BẾP", fontSize = 11.sp) }
                    OutlinedButton({ vm.previewBill() }, Modifier.weight(1f)) { Text("BILL", fontSize = 11.sp) }
                    OutlinedButton({ vm.previewCancel() }, Modifier.weight(1f)) { Text("PHIẾU HỦY", fontSize = 11.sp) }
                }

                if (preview.isNotBlank()) {
                    Card(
                        Modifier.fillMaxWidth().heightIn(min = 430.dp).padding(top = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        when (preview) {
                            "BILL" -> BillPrintPreview(vm)
                            "KITCHEN" -> SimplePrintPreview(
                                title = "PHIẾU LÀM HÀNG",
                                body = PrinterText.kitchenSample()
                            )
                            "CANCEL" -> SimplePrintPreview(
                                title = "PHIẾU HỦY",
                                body = PrinterText.cancelSample()
                            )
                        }
                    }
                } else {
                    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Profile chuẩn 0210", fontWeight = FontWeight.Bold)
                            Text("58mm: 384 dots · 80mm: 576 dots. Bitmap Unicode và VietQR được mã hóa raster ESC/POS theo từng dải an toàn.")
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun SimplePrintPreview(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("0210", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text("BREAKFAST · COFFEE · DRINKS", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Text(title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        Text(body.substringAfter("--------------------------------").trim(), fontSize = 18.sp, lineHeight = 26.sp)
    }
}

@Composable
fun BillPrintPreview(vm: PosViewModel) {
    val settings by vm.settings.collectAsState()
    fun setting(key: String) = settings.firstOrNull { it.key == key }?.value ?: ""
    val subtotal = 135000L
    val discount = 13500L
    val amount = subtotal - discount
    val qrConfigured = setting("bank_name").isNotBlank() && setting("bank_account").isNotBlank()

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("0210", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("BREAKFAST · COFFEE · DRINKS", fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Text("BILL THANH TOÁN", fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text("BÀN 02  ·  08:32–09:25", fontSize = 17.sp, fontWeight = FontWeight.Medium)
        HorizontalDivider(Modifier.padding(vertical = 9.dp))

        PrintLine("2 × Bún gà", "80.000đ", bold = false)
        PrintLine("1 × Bạc xỉu", "30.000đ", bold = false)
        PrintLine("1 × Đen đá", "25.000đ", bold = false)

        HorizontalDivider(Modifier.padding(vertical = 9.dp))
        PrintLine("TẠM TÍNH", money(subtotal), bold = true)
        PrintLine("ƯU ĐÃI HẠNG VIP · -10%", "-${money(discount)}", bold = true)
        HorizontalDivider(Modifier.padding(vertical = 9.dp))
        PrintLine("THÀNH TIỀN", money(amount), bold = true, large = true)
        HorizontalDivider(Modifier.padding(vertical = 9.dp))
        Text("KHÁCH: ANH NAM", Modifier.fillMaxWidth(), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("HẠNG: VIP", Modifier.fillMaxWidth(), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("ĐIỂM: 188 + 12 = 200", Modifier.fillMaxWidth(), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Thanh toán: CHUYỂN KHOẢN", Modifier.fillMaxWidth(), fontSize = 17.sp)

        HorizontalDivider(Modifier.padding(vertical = 9.dp))
        Text("QUÉT MÃ THANH TOÁN", fontSize = 20.sp, fontWeight = FontWeight.Black)
        if (qrConfigured) {
            OfflineVietQrImage(setting("bank_name"), setting("bank_account"), setting("bank_holder"), amount, "0210 BAN 02", Modifier.fillMaxWidth(0.78f).aspectRatio(1f).padding(top = 4.dp))
            Text("SỐ TIỀN: ${money(amount)}", fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text("${setting("bank_name")} · ${setting("bank_account")}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Nội dung: 0210 BAN 02", fontSize = 14.sp)
        } else {
            Box(
                Modifier.fillMaxWidth(0.78f).aspectRatio(1f).padding(10.dp),
                contentAlignment = Alignment.Center
            ) { Text("CHƯA CẤU HÌNH VIETQR", textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }

        HorizontalDivider(Modifier.padding(vertical = 9.dp))
        Text("CẢM ƠN QUÝ KHÁCH!", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text("Good Food · Good Coffee · Brighter Day", fontSize = 10.sp)
    }
}

@Composable
fun PrintLine(label: String, value: String, bold: Boolean, large: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            Modifier.weight(1f),
            fontWeight = if (bold) FontWeight.Black else FontWeight.Normal,
            fontSize = if (large) 26.sp else 18.sp
        )
        Text(
            value,
            fontWeight = if (bold) FontWeight.Black else FontWeight.Medium,
            fontSize = if (large) 17.sp else 13.sp
        )
    }
}

@Composable
fun Report(vm: PosViewModel) {
    val currentGuard by vm.currentEmployee.collectAsState()
    if (currentGuard?.role != "ADMIN" && currentGuard?.canViewReport != true) {
        Column {
            Header("Báo cáo") { vm.screen.value = "TABLES" }
            Text("Bạn không có quyền xem báo cáo.", Modifier.padding(20.dp), fontWeight = FontWeight.Bold)
        }
        return
    }
    val bills by vm.bills.collectAsState()
    val payments by vm.payments.collectAsState()
    val purchases by vm.purchases.collectAsState()
    val purchaseCosts by vm.purchaseCosts.collectAsState()
    val purchaseCategories by vm.purchaseCategories.collectAsState()
    val itemSales by vm.itemSales.collectAsState()
    val current by vm.currentEmployee.collectAsState()
    var section by remember { mutableStateOf("OVERVIEW") }
    var periodDays by remember { mutableStateOf(1) }
    var selectedBill by remember { mutableStateOf<BillEntity?>(null) }
    var selectedPurchase by remember { mutableStateOf<PurchaseEntity?>(null) }
    var paymentFilter by remember { mutableStateOf("ALL") }
    var historyDateText by remember { mutableStateOf("") }
    var selectedBillIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDelete by remember { mutableStateOf(false) }
    var bulkDeleteReason by remember { mutableStateOf("") }

    fun periodStart(days: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (days > 1) add(Calendar.DAY_OF_YEAR, -(days - 1))
        }.timeInMillis
    }

    val from = if (periodDays == 0) null else periodStart(periodDays)
    val filteredBills = if (from == null) bills else bills.filter { (it.closedAt ?: 0L) >= from }
    val filteredPurchases = if (from == null) purchases else purchases.filter { it.purchasedAt >= from }
    val billIds = filteredBills.map { it.id }.toSet()
    val filteredPayments = payments.filter { it.billId in billIds }
    val revenue = filteredBills.sumOf { it.total }
    val purchaseTotal = filteredPurchases.sumOf { it.total }
    val categorizedCosts = purchaseCosts
        .filter { from == null || it.purchasedAt >= from }
        .groupBy { it.categoryId }
        .map { (categoryId, rows) ->
            val categoryName = purchaseCategories.firstOrNull { it.id == categoryId }?.name ?: "Phân mục khác"
            Triple(categoryId, categoryName, rows.sumOf { it.amount })
        }
        .sortedByDescending { it.third }
    val categorizedCostTotal = categorizedCosts.sumOf { it.third }
    val cash = filteredPayments.filter { it.method == "CASH" }.sumOf { it.amount }
    val transfer = filteredPayments.filter { it.method == "TRANSFER" }.sumOf { it.amount }
    val avgBill = if (filteredBills.isEmpty()) 0L else revenue / filteredBills.size
    val sessionIds = filteredBills.map { it.sessionId }.toSet()
    val salesInPeriod = itemSales.filter { it.sessionId in sessionIds }
    val totalItemQty = salesInPeriod.sumOf { it.qty }
    val favoriteRows = salesInPeriod
        .groupBy { it.name }
        .map { (name, rows) -> name to rows.sumOf { it.qty } }
        .sortedByDescending { it.second }
        .take(8)

    val hourlyBillCounts = (5..23).associateWith { hour ->
        filteredBills.count { bill ->
            Calendar.getInstance().apply { timeInMillis = bill.openedAt }
                .get(Calendar.HOUR_OF_DAY) == hour
        }
    }
    val peakHour = hourlyBillCounts.maxByOrNull { it.value }?.takeIf { it.value > 0 }

    val exactDayRange = runCatching {
        if (historyDateText.isBlank()) null else {
            val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { isLenient = false }
            val start = fmt.parse(historyDateText)?.time ?: return@runCatching null
            start until (start + 24L * 60L * 60L * 1000L)
        }
    }.getOrNull()
    val historyTimeBills = if (exactDayRange != null) {
        bills.filter { (it.closedAt ?: 0L) in exactDayRange }
    } else filteredBills
    val historyBills = historyTimeBills.filter { bill ->
        val method = payments.firstOrNull { it.billId == bill.id }?.method
        when (paymentFilter) {
            "CASH" -> method == "CASH"
            "TRANSFER" -> method == "TRANSFER"
            else -> true
        }
    }
    val selectedBills = bills.filter { it.id in selectedBillIds }
    val selectedTotal = selectedBills.sumOf { it.total }

    Column {
        Header("Báo cáo") { vm.screen.value = "TABLES" }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(section == "OVERVIEW", { section = "OVERVIEW" }, { Text("TỔNG QUAN") })
            FilterChip(section == "BILLS", { section = "BILLS" }, { Text("LỊCH SỬ BILL") })
            FilterChip(section == "PURCHASES", { section = "PURCHASES" }, { Text("NHẬP HÀNG") })
            FilterChip(section == "MONTHLY", { section = "MONTHLY" }, { Text("BÁO CÁO THÁNG") })
        }

        when (section) {
            "MONTHLY" -> MonthlyProfitReport(vm)
            "OVERVIEW" -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(periodDays == 1, { periodDays = 1 }, { Text("Hôm nay") })
                    FilterChip(periodDays == 7, { periodDays = 7 }, { Text("7 ngày") })
                    FilterChip(periodDays == 30, { periodDays = 30 }, { Text("30 ngày") })
                    FilterChip(periodDays == 0, { periodDays = 0 }, { Text("Tất cả") })
                }
                LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                    item { MetricCard("Doanh thu", money(revenue)) }
                    item { MetricCard("Số bill", filteredBills.size.toString()) }
                    item { MetricCard("Bill trung bình", money(avgBill)) }
                    item { MetricCard("Tiền mặt", money(cash)) }
                    item { MetricCard("Chuyển khoản", money(transfer)) }
                    item { MetricCard("Tổng chi phí đầu vào", money(purchaseTotal)) }
                    item { MetricCard("Chênh lệch thu - chi đầu vào", money(revenue - purchaseTotal)) }
                    item {
                        PeakHoursChart(
                            hourlyCounts = hourlyBillCounts,
                            peakHour = peakHour,
                            periodDays = periodDays
                        )
                    }
                    item {
                        Text(
                            "Chi phí đầu vào theo phân mục",
                            Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    if (categorizedCosts.isEmpty()) {
                        item { Text("Chưa có chi phí đầu vào trong kỳ.", Modifier.padding(8.dp)) }
                    } else {
                        items(categorizedCosts) { row ->
                            val pct = if (categorizedCostTotal == 0L) 0
                            else ((row.third * 100.0 / categorizedCostTotal) + 0.5).toInt()
                            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(row.second, fontWeight = FontWeight.Bold)
                                        Text("$pct% tổng chi phí", fontSize = 11.sp)
                                    }
                                    Text(money(row.third), fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                    item {
                        Text("Món khách chọn nhiều", Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    if (favoriteRows.isEmpty()) {
                        item { Text("Chưa đủ dữ liệu món trong kỳ.", Modifier.padding(8.dp)) }
                    } else {
                        items(favoriteRows) { row ->
                            val pct = if (totalItemQty == 0) 0 else ((row.second * 100.0 / totalItemQty) + 0.5).toInt()
                            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.first, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    Text("${row.second} phần · $pct%")
                                }
                            }
                        }
                    }
                }
            }
            "BILLS" -> {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        FilterChip(periodDays == 1 && historyDateText.isBlank(), { periodDays = 1; historyDateText = ""; selectedBillIds = emptySet() }, { Text("Hôm nay") })
                        FilterChip(periodDays == 7 && historyDateText.isBlank(), { periodDays = 7; historyDateText = ""; selectedBillIds = emptySet() }, { Text("7 ngày") })
                        FilterChip(periodDays == 30 && historyDateText.isBlank(), { periodDays = 30; historyDateText = ""; selectedBillIds = emptySet() }, { Text("30 ngày") })
                        FilterChip(periodDays == 0 && historyDateText.isBlank(), { periodDays = 0; historyDateText = ""; selectedBillIds = emptySet() }, { Text("Tất cả") })
                    }
                    OutlinedTextField(
                        value = historyDateText,
                        onValueChange = { historyDateText = it.take(10); selectedBillIds = emptySet() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                        label = { Text("Lọc đúng ngày dd/MM/yyyy") },
                        singleLine = true
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        FilterChip(paymentFilter == "ALL", { paymentFilter = "ALL"; selectedBillIds = emptySet() }, { Text("TẤT CẢ") })
                        FilterChip(paymentFilter == "CASH", { paymentFilter = "CASH"; selectedBillIds = emptySet() }, { Text("TIỀN MẶT") })
                        FilterChip(paymentFilter == "TRANSFER", { paymentFilter = "TRANSFER"; selectedBillIds = emptySet() }, { Text("CHUYỂN KHOẢN") })
                    }
                    if (current?.role == "ADMIN" && historyBills.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val ids = historyBills.map { it.id }.toSet()
                                    selectedBillIds = if (ids.all { it in selectedBillIds }) selectedBillIds - ids else selectedBillIds + ids
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(if (historyBills.all { it.id in selectedBillIds }) "BỎ CHỌN TẤT CẢ" else "CHỌN TẤT CẢ") }
                            Button(
                                onClick = { showBulkDelete = true },
                                enabled = selectedBillIds.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) { Text("XÓA ${selectedBillIds.size} BILL") }
                        }
                        if (selectedBillIds.isNotEmpty()) {
                            Text("Đã chọn ${selectedBillIds.size} bill · ${money(selectedTotal)}", Modifier.padding(horizontal = 16.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                    if (historyBills.isEmpty()) {
                        Text("Không có bill theo bộ lọc", Modifier.padding(20.dp))
                    } else {
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                            items(historyBills, key = { it.id }) { bill ->
                                val p = payments.firstOrNull { it.billId == bill.id }
                                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (current?.role == "ADMIN") {
                                            Checkbox(
                                                checked = bill.id in selectedBillIds,
                                                onCheckedChange = { checked ->
                                                    selectedBillIds = if (checked) selectedBillIds + bill.id else selectedBillIds - bill.id
                                                }
                                            )
                                        }
                                        Column(
                                            Modifier.weight(1f).clickable { selectedBill = bill }.padding(4.dp)
                                        ) {
                                            Text(bill.billNo, fontWeight = FontWeight.Bold)
                                            Text("${bill.closedAt?.let { time(it) } ?: "--"} · ${money(bill.total)}")
                                            Text(if (p?.method == "TRANSFER") "Chuyển khoản" else "Tiền mặt", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "PURCHASES" -> {
                if (filteredPurchases.isEmpty()) {
                    Text("Chưa có phiếu nhập trong kỳ đã chọn", Modifier.padding(20.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                        item { Text("Tổng chi phí đầu vào: ${money(purchaseTotal)}", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        if (categorizedCosts.isNotEmpty()) {
                            item {
                                Text(
                                    "Theo phân mục",
                                    Modifier.padding(top = 12.dp, bottom = 4.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            }
                            items(categorizedCosts) { row ->
                                val pct = if (categorizedCostTotal == 0L) 0
                                else ((row.third * 100.0 / categorizedCostTotal) + 0.5).toInt()
                                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(row.second, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                        Text("${money(row.third)} · $pct%")
                                    }
                                }
                            }
                            item {
                                Text(
                                    "Phiếu nhập trong kỳ",
                                    Modifier.padding(top = 14.dp, bottom = 4.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        items(filteredPurchases) { p ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedPurchase = p }) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(time(p.purchasedAt), fontWeight = FontWeight.Bold)
                                    Text(money(p.total))
                                    Text(if (!p.invoiceImageUri.isNullOrBlank()) "📷 Có ảnh hóa đơn · Chạm để xem" else "Chạm để xem chi tiết", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedBill?.let { b ->
        BillDetailDialog(vm, b) { selectedBill = null }
    }
    selectedPurchase?.let { p ->
        PurchaseDetailDialog(vm, p) { selectedPurchase = null }
    }

    if (showBulkDelete) {
        AlertDialog(
            onDismissRequest = { showBulkDelete = false },
            title = { Text("XÓA ${selectedBills.size} BILL") },
            text = {
                Column {
                    Text("Tổng giá trị sẽ loại khỏi doanh thu: ${money(selectedTotal)}", fontWeight = FontWeight.Black)
                    Text("Bill sẽ chuyển sang DELETED, không còn tính doanh thu/tiền mặt/chuyển khoản/thống kê món.")
                    OutlinedTextField(
                        value = bulkDeleteReason,
                        onValueChange = { bulkDeleteReason = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        label = { Text("Lý do xóa bắt buộc") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteBills(selectedBills, bulkDeleteReason)
                        selectedBillIds = emptySet()
                        bulkDeleteReason = ""
                        showBulkDelete = false
                    },
                    enabled = selectedBills.isNotEmpty() && bulkDeleteReason.isNotBlank()
                ) { Text("XÁC NHẬN XÓA") }
            },
            dismissButton = { TextButton(onClick = { showBulkDelete = false }) { Text("HỦY") } }
        )
    }
}

@Composable
fun MonthlyProfitReport(vm:PosViewModel){
    val bills by vm.bills.collectAsState();val payments by vm.payments.collectAsState();val adjustments by vm.billAdjustments.collectAsState()
    val purchases by vm.purchases.collectAsState();val configs by vm.monthlyAccounting.collectAsState();val partners by vm.profitPartners.collectAsState()
    var monthOffset by remember{mutableIntStateOf(0)};var expenseFilter by remember{mutableStateOf("ALL")}
    val month=remember(monthOffset){Calendar.getInstance().apply{set(Calendar.DAY_OF_MONTH,1);set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0);add(Calendar.MONTH,monthOffset)}}
    val from=month.timeInMillis;val to=Calendar.getInstance().apply{timeInMillis=from;add(Calendar.MONTH,1)}.timeInMillis
    val monthKey="%04d-%02d".format(month.get(Calendar.YEAR),month.get(Calendar.MONTH)+1)
    val monthBills=bills.filter{(it.closedAt?:Long.MIN_VALUE) in from until to};val billIds=monthBills.map{it.id}.toSet()
    val monthAdjustments=adjustments.filter{it.billId in billIds};val monthPurchases=purchases.filter{it.purchasedAt in from until to}
    val config=configs.firstOrNull{it.monthKey==monthKey}
    var cogsText by remember(monthKey,config){mutableStateOf(config?.cogs?.toString().orEmpty())};var source by remember(monthKey,config){mutableStateOf(config?.cogsSource?:"UNAVAILABLE")}
    var reserveText by remember(monthKey,config){mutableStateOf(((config?.reserveBasisPoints?:1000)/100).toString())};var openingCashText by remember(monthKey,config){mutableStateOf((config?.openingCash?:0).toString())}
    fun sum(category:String)=monthPurchases.filter{it.expenseCategory==category}.sumOf{it.total}
    val grossRevenue=monthBills.sumOf{it.subtotal};val discounts=monthAdjustments.filter{it.kind=="DISCOUNT"}.sumOf{it.amount};val surcharges=monthAdjustments.filter{it.kind=="SURCHARGE"}.sumOf{it.amount}
    val fixed=sum(ExpenseCategories.FIXED_EXPENSE);val variableExpense=sum(ExpenseCategories.VARIABLE_EXPENSE);val other=sum(ExpenseCategories.OTHER_EXPENSE)
    val inventory=sum(ExpenseCategories.INVENTORY_PURCHASE);val capital=sum(ExpenseCategories.CAPITAL_ASSET);val setup=sum(ExpenseCategories.SETUP_COST)
    val contribution=sum(ExpenseCategories.OWNER_CONTRIBUTION);val withdrawal=sum(ExpenseCategories.OWNER_WITHDRAWAL);val unclassified=sum(ExpenseCategories.UNCLASSIFIED)
    val received=payments.filter{it.billId in billIds}.sumOf{it.amount};val reserveBp=(reserveText.toIntOrNull()?:10).coerceIn(0,100)*100
    val result=calculateMonthlyAccounting(MonthlyAccountingInput(grossRevenue,discounts,0,surcharges,cogsText.toLongOrNull(),fixed,variableExpense,other,setup,inventory,capital,contribution,withdrawal,unclassified,openingCashText.toLongOrNull()?:0,received,reserveBp,partners.map{ProfitShareInput(it.id,it.name,it.shareBasisPoints)}))
    var showPartners by remember{mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)){
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){OutlinedButton({monthOffset--}){Text("‹")};Text(SimpleDateFormat("MM / yyyy",Locale.getDefault()).format(Date(from)),Modifier.weight(1f),textAlign=TextAlign.Center,fontWeight=FontWeight.Black,fontSize=20.sp);OutlinedButton({monthOffset++}){Text("›")}}}
        item{Card(Modifier.fillMaxWidth().padding(vertical=5.dp)){Column(Modifier.padding(14.dp)){
            Text("NGUỒN GIÁ VỐN",fontWeight=FontWeight.Black);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(source=="INVENTORY",{source="INVENTORY"},{Text("Theo kiểm kê")});FilterChip(source=="RECIPE",{source="RECIPE"},{Text("Theo recipe")});FilterChip(source=="UNAVAILABLE",{source="UNAVAILABLE";cogsText=""},{Text("Chưa có")})}
            OutlinedTextField(cogsText,{cogsText=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("Giá vốn tháng (VND)")},enabled=source!="UNAVAILABLE")
            if(source=="UNAVAILABLE")Text("Chưa đủ dữ liệu giá vốn; hệ thống không lấy tiền nhập hàng thay cho COGS.",color=Color(0xFF9A4B3D),fontWeight=FontWeight.Bold)
            OutlinedTextField(reserveText,{reserveText=it.filter(Char::isDigit).take(3)},Modifier.fillMaxWidth(),label={Text("Trích quỹ dự phòng (%)")})
            OutlinedTextField(openingCashText,{openingCashText=it.filter{c->c.isDigit()||c=='-'}},Modifier.fillMaxWidth(),label={Text("Tiền đầu kỳ")})
            Button({vm.saveMonthlyAccounting(MonthlyAccountingEntity(monthKey,if(source=="UNAVAILABLE")null else cogsText.toLongOrNull(),source,openingCashText.toLongOrNull()?:0,reserveBp))},Modifier.fillMaxWidth(),enabled=source=="UNAVAILABLE"||cogsText.toLongOrNull()!=null){Text("LƯU THÔNG SỐ THÁNG")}
        }}}
        item{Text("KẾT QUẢ KINH DOANH",Modifier.padding(top=12.dp,bottom=4.dp),fontWeight=FontWeight.Black,fontSize=18.sp)}
        item{AccountingCard(listOf("Doanh thu gộp" to grossRevenue,"Giảm giá" to -discounts,"Điều chỉnh doanh thu" to surcharges,"Doanh thu thuần" to result.netRevenue,"Giá vốn" to result.grossProfit?.let{-(result.netRevenue-it)},"Lãi gộp" to result.grossProfit,"Chi phí cố định" to -fixed,"Chi phí biến đổi" to -variableExpense,"Chi phí khác" to -other,"LỢI NHUẬN KINH DOANH" to result.operatingProfit))}
        item{Text("Chi phí setup trong kỳ: ${money(setup)} · không tự trừ vào lợi nhuận hoạt động.",Modifier.padding(8.dp),fontWeight=FontWeight.Bold)}
        if(unclassified>0)item{Text("Còn ${money(unclassified)} chưa phân loại; chưa đưa vào P&L.",Modifier.padding(8.dp),color=Color(0xFF9A4B3D),fontWeight=FontWeight.Bold)}
        item{Text("PHÂN PHỐI LỢI NHUẬN",Modifier.padding(top=12.dp,bottom=4.dp),fontWeight=FontWeight.Black,fontSize=18.sp)}
        item{AccountingCard(listOf("Lợi nhuận kinh doanh" to result.operatingProfit,"Trích quỹ dự phòng" to result.reserve?.let{-it},"LỢI NHUẬN ĐƯỢC CHIA" to result.distributableProfit))}
        items(result.partnerProfits){p->MetricCard("${p.name} · ${p.shareBasisPoints/100}%",money(p.amount))}
        item{Button({showPartners=true},Modifier.fillMaxWidth()){Text("CẤU HÌNH NGƯỜI CHIA LỢI")};if(!result.shareConfigurationValid)Text("Tổng tỷ lệ người nhận phải đúng 100%.",color=Color(0xFF9A4B3D),fontWeight=FontWeight.Bold)}
        item{Text("DÒNG TIỀN",Modifier.padding(top=12.dp,bottom=4.dp),fontWeight=FontWeight.Black,fontSize=18.sp);AccountingCard(listOf("Tiền đầu kỳ" to (openingCashText.toLongOrNull()?:0),"Doanh thu đã thu" to received,"Góp vốn" to contribution,"Tiền nhập hàng" to -inventory,"Chi tiền hoạt động/setup/chưa PL" to -(fixed+variableExpense+other+setup+unclassified),"Mua tài sản" to -capital,"Rút vốn" to -withdrawal,"TIỀN CUỐI KỲ" to result.closingCash));Text("Lợi nhuận và tiền còn lại là hai chỉ tiêu độc lập.",Modifier.padding(8.dp),fontWeight=FontWeight.Bold)}
        item{Text("VỐN & ĐẦU TƯ",Modifier.padding(top=12.dp,bottom=4.dp),fontWeight=FontWeight.Black,fontSize=18.sp);AccountingCard(listOf("Vốn góp tháng" to contribution,"Rút vốn tháng" to withdrawal,"Đầu tư tài sản tháng" to capital,"Chi phí setup tháng" to setup,"Vốn góp toàn kỳ" to purchases.filter{it.expenseCategory==ExpenseCategories.OWNER_CONTRIBUTION}.sumOf{it.total},"Đầu tư tài sản toàn kỳ" to purchases.filter{it.expenseCategory==ExpenseCategories.CAPITAL_ASSET}.sumOf{it.total}))}
        item{Text("LỌC GIAO DỊCH CHI",Modifier.padding(top=12.dp),fontWeight=FontWeight.Black);Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){FilterChip(expenseFilter=="ALL",{expenseFilter="ALL"},{Text("Tất cả")});ExpenseCategories.all.forEach{v->FilterChip(expenseFilter==v,{expenseFilter=v},{Text(ExpenseCategories.label(v))})}}}
        items(monthPurchases.filter{expenseFilter=="ALL"||it.expenseCategory==expenseFilter}){p->Card(Modifier.fillMaxWidth().padding(vertical=3.dp)){Column(Modifier.padding(10.dp)){Text(ExpenseCategories.label(p.expenseCategory),fontWeight=FontWeight.Bold);Text("${time(p.purchasedAt)} · ${money(p.total)}")}}}
    }
    if(showPartners)ProfitPartnerDialog(partners,{showPartners=false}){vm.saveProfitPartners(it);showPartners=false}
}

@Composable fun AccountingCard(rows:List<Pair<String,Long?>>){Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){Column(Modifier.padding(14.dp)){rows.forEach{(label,value)->Row(Modifier.fillMaxWidth().padding(vertical=3.dp)){Text(label,Modifier.weight(1f),fontWeight=if(label.uppercase()==label)FontWeight.Black else FontWeight.Normal);Text(value?.let{money(it)}?:"CHƯA CÓ GIÁ VỐN",fontWeight=FontWeight.Bold)}}}}}

@Composable fun ProfitPartnerDialog(current:List<ProfitPartnerEntity>,onDismiss:()->Unit,onSave:(List<ProfitPartnerEntity>)->Unit){
    val rows=remember(current){mutableStateListOf<Pair<String,String>>().apply{addAll(current.map{it.name to (it.shareBasisPoints/100.0).toString().removeSuffix(".0")});if(isEmpty())add("" to "")}}
    val total=rows.sumOf{((it.second.replace(',','.').toDoubleOrNull()?:0.0)*100).toInt()}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Tỷ lệ chia lợi nhuận")},text={LazyColumn{itemsIndexed(rows){index,row->Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(row.first,{rows[index]=it to row.second},Modifier.weight(1f),label={Text("Người nhận")});OutlinedTextField(row.second,{rows[index]=row.first to it.filter{c->c.isDigit()||c=='.'||c==','}},Modifier.width(100.dp),label={Text("%")})}};item{OutlinedButton({rows.add("" to "")},Modifier.fillMaxWidth()){Text("＋ THÊM NGƯỜI")};Text("Tổng: ${total/100.0}%",color=if(total==10000)Coffee else Color(0xFF9A4B3D),fontWeight=FontWeight.Bold)}}},confirmButton={Button({onSave(rows.mapIndexed{i,r->ProfitPartnerEntity(UUID.randomUUID().toString(),r.first.trim(),((r.second.replace(',','.').toDoubleOrNull()?:0.0)*100).toInt(),i,true)})},enabled=total==10000&&rows.all{it.first.isNotBlank()}){Text("LƯU 100%")}},dismissButton={TextButton(onClick=onDismiss){Text("HỦY")}})
}

@Composable
fun PeakHoursChart(
    hourlyCounts: Map<Int, Int>,
    peakHour: Map.Entry<Int, Int>?,
    periodDays: Int
) {
    val maxCount = (hourlyCounts.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val periodLabel = when (periodDays) {
        1 -> "Hôm nay"
        7 -> "7 ngày"
        30 -> "30 ngày"
        0 -> "Tất cả"
        else -> "$periodDays ngày"
    }

    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("GIỜ CAO ĐIỂM", fontWeight = FontWeight.Black, fontSize = 18.sp)
            if (peakHour == null) {
                Text("Chưa đủ dữ liệu bill trong kỳ $periodLabel.", Modifier.padding(top = 6.dp))
            } else {
                Text(
                    "Cao điểm: %02d:00–%02d:00 · %d bill".format(
                        peakHour.key,
                        (peakHour.key + 1) % 24,
                        peakHour.value
                    ),
                    Modifier.padding(top = 4.dp),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Biểu đồ theo giờ mở bàn · $periodLabel",
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                hourlyCounts.forEach { (hour, count) ->
                    val ratio = count.toFloat() / maxCount.toFloat()
                    val barHeight = if (count == 0) 2.dp else (18f + 92f * ratio).dp
                    Column(
                        Modifier.width(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            count.toString(),
                            fontSize = 10.sp,
                            fontWeight = if (count == maxCount && count > 0) FontWeight.Black else FontWeight.Normal
                        )
                        Surface(
                            modifier = Modifier.width(24.dp).height(barHeight),
                            color = if (count == maxCount && count > 0) Coffee else Tint,
                            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                        ) {}
                        Text("%02d".format(hour), fontSize = 10.sp)
                    }
                }
            }
            Text(
                "Trục ngang: giờ · Trục đứng: số bill",
                Modifier.padding(top = 8.dp),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun MetricCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Text(value, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
    }
}

@Composable
fun BillDetailDialog(vm: PosViewModel, bill: BillEntity, onDismiss: () -> Unit) {
    val session by vm.session(bill.sessionId).collectAsState(initial = null)
    val batches by vm.batches(bill.sessionId).collectAsState(initial = emptyList())
    val tables by vm.tables.collectAsState()
    val employees by vm.employees.collectAsState()
    val payments by vm.payments.collectAsState()
    val current by vm.currentEmployee.collectAsState()
    var showDelete by remember { mutableStateOf(false) }
    var deleteReason by remember { mutableStateOf("") }

    val payment = payments.firstOrNull { it.billId == bill.id }
    val tableName = tables.firstOrNull { it.id == session?.tableId }?.name ?: session?.tableId ?: "?"
    val cashier = employees.firstOrNull { it.id == payment?.cashierId }?.name ?: payment?.cashierId ?: "?"
    val methodText = if (payment?.method == "TRANSFER") "Chuyển khoản" else "Tiền mặt"

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("ĐÓNG") } },
        dismissButton = {
            if (current?.role == "ADMIN") {
                TextButton(onClick = { showDelete = true }) { Text("XÓA BILL · ADMIN") }
            }
        },
        title = { Text("$tableName · ${bill.billNo}") },
        text = {
            LazyColumn {
                item {
                    Text("Mở: ${time(bill.openedAt)}")
                    Text("Đóng: ${bill.closedAt?.let { time(it) } ?: "--"}")
                    Text("Thanh toán: $methodText")
                    Text("Thu tiền: $cashier")
                    Text("Tổng bill: ${money(bill.total)}", fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                }
                items(batches) { batch ->
                    BillBatchDetail(vm, batch, employees)
                }
            }
        }
    )

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("XÓA BILL ${bill.billNo}") },
            text = {
                Column {
                    Text("Bill sẽ bị loại khỏi lịch sử và báo cáo doanh thu nhưng vẫn giữ dấu vết audit.")
                    OutlinedTextField(
                        value = deleteReason,
                        onValueChange = { deleteReason = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        label = { Text("Lý do xóa bắt buộc") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteBill(bill, deleteReason)
                        showDelete = false
                        onDismiss()
                    },
                    enabled = deleteReason.isNotBlank()
                ) { Text("XÁC NHẬN XÓA BILL") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("HỦY") }
            }
        )
    }
}

@Composable
fun BillBatchDetail(vm: PosViewModel, batch: OrderBatchEntity, employees: List<EmployeeEntity>) {
    val lines by vm.items(batch.id).collectAsState(initial = emptyList())
    val orderer = employees.firstOrNull { it.id == batch.ordererId }?.name ?: batch.ordererId
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Đơn #${batch.sequence} · $orderer · ${time(batch.createdAt)}",
                fontWeight = FontWeight.Bold
            )
            lines.forEach { line ->
                Text("${line.qty} × ${line.itemNameSnapshot} · ${money(line.unitPriceSnapshot * line.qty)}")
                if (line.note.isNotBlank()) Text("  Ghi chú: ${line.note}", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun DataHealth(vm: PosViewModel) {
    val current by vm.currentEmployee.collectAsState()
    val issues by vm.healthIssues.collectAsState()
    val message by vm.healthMessage.collectAsState()
    if (current?.role != "ADMIN") {
        Column {
            Header("Kiểm tra dữ liệu") { vm.screen.value = "MANAGE" }
            Text("Chỉ Admin được chạy kiểm tra dữ liệu.", Modifier.padding(20.dp), fontWeight = FontWeight.Bold)
        }
        return
    }
    Column {
        Header("Kiểm tra dữ liệu") { vm.screen.value = "MANAGE" }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("ĐỐI SOÁT HỆ THỐNG", fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(
                "Kiểm tra Payment ↔ Bill ↔ Session ↔ Customer ↔ Điểm. Không tự sửa dữ liệu.",
                Modifier.padding(top = 4.dp),
                fontSize = 12.sp
            )
            Button(
                onClick = { vm.runHealthCheck() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) { Text("CHẠY KIỂM TRA") }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(message, fontWeight = FontWeight.Black)
                    if (issues.isEmpty() && message.startsWith("PASS")) {
                        Text("Không phát hiện lệch dữ liệu lõi.", Modifier.padding(top = 6.dp))
                    } else {
                        issues.forEach { issue ->
                            Text("• $issue", Modifier.padding(top = 5.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (issues.any { it.startsWith("Session CLOSED còn đơn WAITING") }) {
                            Button(
                                onClick = { vm.reconcileLegacyWaiting() },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                            ) { Text("XỬ LÝ DỮ LIỆU CŨ") }
                            Text(
                                "Chỉ đối soát đơn WAITING thuộc session đã đóng; không sửa bill, payment, doanh thu hay điểm.",
                                Modifier.padding(top = 6.dp),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
            Text(
                "Nếu có cảnh báo, cần xác định nguyên nhân trước khi sửa hoặc chạy thử thật.",
                Modifier.padding(top = 12.dp),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun Settings(vm: PosViewModel) {
    val a by vm.audits.collectAsState()
    val employees by vm.employees.collectAsState()
    val current by vm.currentEmployee.collectAsState()
    if (current?.role != "ADMIN") {
        Column {
            Header("Nhật ký hệ thống") { vm.screen.value = "MANAGE" }
            Text("Chỉ Admin được xem audit log.", Modifier.padding(20.dp), fontWeight = FontWeight.Bold)
        }
        return
    }
    Column {
        Header("Nhật ký hệ thống") { vm.screen.value = "MANAGE" }
        Text("Các thao tác gần nhất · không sửa/xóa từ giao diện", Modifier.padding(horizontal = 16.dp), fontSize = 11.sp)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(a) { event ->
                val actor = employees.firstOrNull { it.id == event.actorId }?.name ?: event.actorId ?: "SYSTEM"
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${time(event.occurredAt)} · ${event.action}", fontWeight = FontWeight.Black)
                        Text("Người thực hiện: $actor", fontSize = 12.sp)
                        Text("${event.entityType} · ${event.entityId}", fontSize = 11.sp)
                        if (event.payload.isNotBlank()) {
                            Text(event.payload, Modifier.padding(top = 3.dp), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BankPaymentSettings(vm:PosViewModel){
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val settings by vm.settings.collectAsState()
    fun enabled(key:String,default:Boolean)=settings.firstOrNull{it.key==key}?.value?.toBooleanStrictOrNull()?:default
    var permissionGranted by remember{mutableStateOf(NotificationAccess.isGranted(context))}
    DisposableEffect(owner){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)permissionGranted=NotificationAccess.isGranted(context)}
        owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}
    }
    Column{
        Header("Thanh toán chuyển khoản"){vm.screen.value="MANAGE"}
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())){
            Text("THEO DÕI THÔNG BÁO NGÂN HÀNG",fontWeight=FontWeight.Black)
            SettingSwitch("Theo dõi thông báo ngân hàng",enabled("bank_notification_enabled",false)){vm.saveSetting("bank_notification_enabled",it.toString())}
            Card(Modifier.fillMaxWidth().padding(vertical=8.dp)){
                Column(Modifier.padding(14.dp)){
                    Text("Quyền đọc thông báo: ${if(permissionGranted)"Đã cấp" else "Chưa cấp"}",fontWeight=FontWeight.Bold)
                    if(!permissionGranted)Text("Chưa cấp quyền đọc thông báo ngân hàng. POS vẫn thanh toán thủ công bình thường.",fontSize=12.sp)
                    Button(onClick={context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))},modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text(if(permissionGranted)"MỞ CÀI ĐẶT QUYỀN" else "CẤP QUYỀN ĐỌC THÔNG BÁO")}
                }
            }
            Text("Ngân hàng hỗ trợ: Vietcombank · VietinBank",fontSize=12.sp)
            Text("Chỉ đọc notification từ package ngân hàng trong whitelist; không đọc SMS, Zalo hoặc ứng dụng khác.",fontSize=11.sp)
            SettingSwitch("Rung khi nhận tiền",enabled("bank_notification_vibrate",true)){vm.saveSetting("bank_notification_vibrate",it.toString())}
            SettingSwitch("Đọc số tiền bằng loa",enabled("bank_notification_tts",true)){vm.saveSetting("bank_notification_tts",it.toString())}
            HorizontalDivider(Modifier.padding(vertical=12.dp))
            Text("Chế độ xác nhận",fontWeight=FontWeight.Bold)
            Text("Phát hiện + nhân viên xác nhận (mặc định)\nKhông tự động đóng bill.",fontSize=12.sp)
            OutlinedButton(onClick={vm.screen.value="BANK_NOTIFICATION_TEST"},modifier=Modifier.fillMaxWidth().padding(top=14.dp)){Text("TEST THÔNG BÁO NGÂN HÀNG")}
        }
    }
}

@Composable private fun SettingSwitch(label:String,checked:Boolean,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(checked=checked,onCheckedChange=onChange)}
}

@Composable
fun BankNotificationTest(vm:PosViewModel){
    val events by vm.recentBankNotifications.collectAsState()
    val event=events.firstOrNull()
    Column{
        Header("Test thông báo ngân hàng"){vm.screen.value="BANK_PAYMENT_SETTINGS"}
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())){
            if(event==null)Text("Chưa có notification ngân hàng trong log test.") else Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(14.dp)){
                    Text("${event.bank?:"UNKNOWN"} · ${event.parserResult}",fontWeight=FontWeight.Black)
                    Text("Title: ${event.title}",fontSize=12.sp)
                    Text("Body: ${event.body}",fontSize=11.sp)
                    Text("Amount: ${event.amount?.let(::money)?:"—"}")
                    Text("Account: ${event.account?:"—"}",fontSize=12.sp)
                    Text("Transaction time: ${event.transactionTime?.let(::time)?:"—"}",fontSize=12.sp)
                    Text("Direction: ${event.direction?:"—"} · Match: ${event.matchStatus}",fontSize=12.sp)
                    Text("Content: ${event.content?:"—"}",fontSize=11.sp)
                }
            }
            Button(onClick={BankPaymentAnnouncer.announce(LocalContext.current,127000,"Bàn 05",false,true)},modifier=Modifier.fillMaxWidth().padding(top=12.dp)){Text("TEST TTS")}
            OutlinedButton(onClick={vm.clearBankNotificationLog()},modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("XÓA LOG TEST")}
            Text("Log chỉ lưu cục bộ tối đa 20 notification ngân hàng gần nhất và có thể xóa tại đây.",Modifier.padding(top=10.dp),fontSize=11.sp)
        }
    }
}
