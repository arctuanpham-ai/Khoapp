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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import vn.ecohome.pos0210.data.*
import vn.ecohome.pos0210.printing.PrinterText
import vn.ecohome.pos0210.printing.BluetoothPrinter
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

private fun vietQrUrl(bank: String, account: String, holder: String, amount: Long, info: String): String {
    val bankId = bank.trim().replace(" ", "")
    return "https://img.vietqr.io/image/${Uri.encode(bankId)}-${Uri.encode(account.trim())}-compact2.png" +
        "?amount=$amount&addInfo=${Uri.encode(info.take(50))}&accountName=${Uri.encode(holder.trim())}"
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
    val areas by vm.areas.collectAsState()
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

        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val count = ts.size.coerceAtLeast(1)
            val columns = when {
                count <= 4 -> 2
                count <= 9 -> 3
                count <= 16 -> 4
                else -> 5
            }
            val rows = ((count + columns - 1) / columns).coerceAtLeast(1)
            val rawHeight = (maxHeight - 8.dp * (rows - 1).toFloat()) / rows.toFloat()
            val cardHeight = rawHeight.coerceIn(76.dp, 150.dp)

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gridItems(ts, key = { it.id }) { tb ->
                    val open = ss.firstOrNull { it.tableId == tb.id }
                    val areaName = areas.firstOrNull { it.id == tb.areaId }?.name ?: tb.areaId
                    val waitingForTable = open?.let { s -> waiting.filter { it.sessionId == s.id } } ?: emptyList()
                    val nextService = waitingForTable.minWithOrNull(compareBy<OrderBatchEntity> { it.serviceNo }.thenBy { it.createdAt })
                    val priorityRank = nextService?.let { waitingRankById[it.id] }
                    Card(
                        Modifier.fillMaxWidth().height(cardHeight).clickable { vm.selectTable(tb) },
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                priorityRank == 0 -> WaitingPriority1
                                priorityRank == 1 -> WaitingPriority2
                                nextService != null -> WaitingDelivery
                                open == null -> Tint
                                else -> Occupied
                            }
                        )
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                tb.name,
                                fontWeight = FontWeight.Black,
                                fontSize = if (columns >= 4) 16.sp else 19.sp
                            )
                            Text(areaName, fontSize = if (columns >= 4) 10.sp else 11.sp)
                            Spacer(Modifier.height(4.dp))
                            if (open == null) {
                                Text("Trống", fontSize = if (columns >= 4) 12.sp else 14.sp)
                            } else {
                                Text(
                                    "● CÓ KHÁCH",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (columns >= 4) 11.sp else 13.sp
                                )
                                if (nextService != null) {
                                    Text(
                                        when (priorityRank) {
                                            0 -> "#${nextService.serviceNo.toString().padStart(3,'0')} · ƯU TIÊN 1"
                                            1 -> "#${nextService.serviceNo.toString().padStart(3,'0')} · ƯU TIÊN 2"
                                            else -> "#${nextService.serviceNo.toString().padStart(3,'0')} · CHỜ GIAO"
                                        },
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp
                                    )
                                } else if (cardHeight > 95.dp) {
                                    Text(time(open.openedAt), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

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
                    Card(
                        Modifier.fillMaxWidth().clickable { vm.addCombo(combo) },
                        colors = CardDefaults.cardColors(containerColor = if(note.isNotBlank()) Color(0xFFFFF0D8) else Color(0xFFFBF8F2))
                    ) {
                        Column {
                            if (!combo.imageUri.isNullOrBlank()) {
                                AsyncImage(model = combo.imageUri, contentDescription = combo.name, modifier = Modifier.fillMaxWidth().height(92.dp))
                            } else {
                                Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) { Text("COMBO", fontWeight = FontWeight.Black) }
                            }
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
    val qrInfo = "${setting("qr_prefix").ifBlank { "0210" }} ${t.name}"
    val qrUrl = if (setting("bank_name").isNotBlank() && setting("bank_account").isNotBlank()) {
        vietQrUrl(setting("bank_name"), setting("bank_account"), setting("bank_holder"), preview.total, qrInfo)
    } else ""

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
                        if (qrUrl.isBlank()) Text("Chưa cấu hình tài khoản VietQR") else {
                            AsyncImage(model = qrUrl, contentDescription = "Mã VietQR thanh toán", modifier = Modifier.size(280.dp))
                            Text("${setting("bank_name")} · ${setting("bank_account")}")
                            Text("${money(preview.total)} · $qrInfo")
                        }
                    }
                }
            }
        }
        Button(
            onClick = { vm.close(method, preview, customerPhone, customerName) },
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            enabled = preview.total > 0 && pendingDelivery.isEmpty()
        ) {
            Text(if (pendingDelivery.isEmpty()) "XÁC NHẬN THANH TOÁN" else "CHƯA GIAO ĐỦ · CHƯA THỂ THANH TOÁN")
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
                Rowx("Nhập đầu vào", "Lương · vật tư cố định · vật tư sản xuất") { vm.screen.value = "PURCHASE" }
            }
            if (employee?.role == "ADMIN" || employee?.role == "MANAGER") {
                Rowx("VietQR", "Lưu tài khoản · tạo QR") { vm.screen.value = "VIETQR" }
            }
            Rowx("Máy in", "XP-N58H · Bluetooth · ESC/POS") { vm.screen.value = "PRINTER" }
            if (employee?.role == "ADMIN" || employee?.canManageSystem == true) {
                Rowx("Dữ liệu & Backup", "MASTER · Autobackup · Backup/Restore") { vm.screen.value = "BACKUP" }
            }
            if (employee?.role == "ADMIN") {
                Rowx("Kiểm tra dữ liệu", "Đối soát Payment · Bill · Customer · điểm · trạng thái bàn") { vm.screen.value = "HEALTH" }
                Rowx("Nhật ký hệ thống", "Audit thao tác · người thực hiện · thời điểm · dữ liệu thay đổi") { vm.screen.value = "SETTINGS" }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("POS0210 v1.0.0-alpha51 · versionCode 61", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Tương thích Android 8.0 (API 26) trở lên · Thiết bị hiện tại: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", fontSize = 11.sp)
            if (Build.VERSION.SDK_INT < 26) Text("Thiết bị không được hỗ trợ. Cần Android 8.0 trở lên.", color = Color.Red, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
fun BackupCenter(vm: PosViewModel) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    var refreshTick by remember { mutableStateOf(0) }
    var rootUri by remember { mutableStateOf(vm.setting("storage_root_uri")) }
    val savedRootUri = vm.setting("storage_root_uri")
    LaunchedEffect(savedRootUri) {
        if (savedRootUri.isNotBlank()) rootUri = savedRootUri
    }

    val chooseRoot = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val result = SafPosStorage.ensureSelectedRoot(context, uri.toString())
            if (result.isSuccess) {
                rootUri = uri.toString()
                vm.saveSetting("storage_root_uri", uri.toString())
                refreshTick++
                message = "Đã gắn thư mục POS0210. Nếu chưa có file, bấm GHI MASTER và BACKUP NGAY để tạo từ dữ liệu hiện tại."
            } else {
                message = "Không gắn được thư mục: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    val importMaster = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val result = ConfigBackup.importConfig(context, uri)
            if (result.isSuccess) {
                if (rootUri.isNotBlank()) {
                    ConfigBackup.copyMaster(context, rootUri, uri)
                }
                Toast.makeText(context, "Đã LOAD MASTER. App sẽ mở lại.", Toast.LENGTH_LONG).show()
                android.os.Process.killProcess(android.os.Process.myPid())
            } else {
                message = "LOAD MASTER lỗi: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val result = if (rootUri.isNotBlank()) {
                DataBackup.restoreDatabaseAndApplyMaster(context, uri, rootUri)
            } else {
                DataBackup.restoreDatabase(context, uri)
            }
            if (result.isSuccess) {
                Toast.makeText(
                    context,
                    if (rootUri.isNotBlank()) "Đã RESTORE DATA + áp lại MASTER. App sẽ mở lại." else "Đã RESTORE DATA. Chưa có MASTER root để áp lại.",
                    Toast.LENGTH_LONG
                ).show()
                android.os.Process.killProcess(android.os.Process.myPid())
            } else {
                message = "RESTORE DATA lỗi: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    val masterFound = remember(refreshTick, rootUri) {
        rootUri.isNotBlank() && ConfigBackup.findMaster(context, rootUri) != null
    }
    val dataFound = remember(refreshTick, rootUri) {
        rootUri.isNotBlank() && DataBackup.findLatest(context, rootUri) != null
    }
    val mediaFound = remember(refreshTick, rootUri) {
        rootUri.isNotBlank() && DataBackup.findMediaLatest(context, rootUri) != null
    }

    Column {
        Header("Dữ liệu & Backup") { vm.screen.value = "MANAGE" }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())
        ) {
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("NƠI LƯU POS0210", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text(
                        if (rootUri.isBlank()) "CHƯA GẮN THƯ MỤC" else "ĐÃ GẮN THƯ MỤC",
                        Modifier.padding(vertical = 6.dp),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Android không cho chọn trực tiếp thư mục gốc Download. App sẽ tự tạo trước:\n" +
                        "Download/POS0210/CONFIG\nDownload/POS0210/DATA\nDownload/POS0210/ARCHIVE\n" +
                        "Sau đó hãy mở Download → POS0210 và chọn chính thư mục POS0210.",
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = {
                            val prep = PosStorage.ensureFolders(context)
                            if (prep.isSuccess) {
                                message = "Đã tạo/kiểm tra cây Download/POS0210. Hãy chọn chính thư mục POS0210."
                                chooseRoot.launch(null)
                            } else {
                                message = "Không tạo được cây POS0210: ${prep.exceptionOrNull()?.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text(if (rootUri.isBlank()) "TẠO & GẮN THƯ MỤC POS0210" else "ĐỔI / GẮN LẠI POS0210") }
                }
            }

            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("MASTER CONFIG", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("Menu · ảnh món · bàn · nhân viên/PIN · VietQR · máy in · phân mục", fontSize = 13.sp)
                    Text(
                        "POS0210/CONFIG/POS0210_MASTER.0210\nTrạng thái: ${if (masterFound) "ĐÃ TÌM THẤY" else if (rootUri.isNotBlank()) "CHƯA CÓ FILE · BẤM GHI MASTER" else "CHƯA GẮN THƯ MỤC"}",
                        Modifier.padding(vertical = 8.dp),
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = {
                            if (rootUri.isBlank()) {
                                message = "Hãy chọn nơi lưu POS0210 trước."
                            } else {
                                val result = ConfigBackup.saveMaster(context, rootUri)
                                refreshTick++
                                message = if (result.isSuccess) "Đã ghi đè đúng MASTER chuẩn." else "GHI MASTER lỗi: ${result.exceptionOrNull()?.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = rootUri.isNotBlank()
                    ) { Text("GHI MASTER NGAY") }

                    if (masterFound) {
                        OutlinedButton(
                            onClick = {
                                val uri = ConfigBackup.findMaster(context, rootUri)
                                if (uri != null) {
                                    val result = ConfigBackup.importConfig(context, uri)
                                    if (result.isSuccess) {
                                        vm.saveSetting("storage_root_uri", rootUri)
                                        Toast.makeText(context, "Đã LOAD MASTER chuẩn. App sẽ mở lại.", Toast.LENGTH_LONG).show()
                                        android.os.Process.killProcess(android.os.Process.myPid())
                                    } else {
                                        message = "LOAD MASTER lỗi: ${result.exceptionOrNull()?.message}"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("LOAD MASTER CHUẨN") }
                    }

                    OutlinedButton(
                        onClick = { importMaster.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text("LOAD MASTER TỪ FILE KHÁC") }
                }
            }

            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("DATA VẬN HÀNH", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("Bill · order · thanh toán · nhập hàng · lịch sử · audit", fontSize = 13.sp)
                    Text(
                        "POS0210/DATA/POS0210_DATA_LATEST.db\n" +
                        "POS0210/DATA/POS0210_MEDIA_LATEST.0210\n" +
                        "DATA: ${if (dataFound) "ĐÃ CÓ" else "CHƯA CÓ"} · MEDIA: ${if (mediaFound) "ĐÃ CÓ" else "CHƯA CÓ"}",
                        Modifier.padding(vertical = 8.dp),
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = {
                            if (rootUri.isBlank()) {
                                message = "Hãy chọn nơi lưu POS0210 trước."
                            } else {
                                val result = DataBackup.backupLatest(context, rootUri)
                                refreshTick++
                                message = if (result.isSuccess) "BACKUP DATA + MEDIA thành công." else "BACKUP lỗi: ${result.exceptionOrNull()?.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = rootUri.isNotBlank()
                    ) { Text("BACKUP NGAY → DATA_LATEST") }

                    OutlinedButton(
                        onClick = {
                            if (rootUri.isBlank()) {
                                message = "Hãy chọn nơi lưu POS0210 trước."
                            } else {
                                val result = DataBackup.archiveSnapshot(context, rootUri)
                                refreshTick++
                                message = if (result.isSuccess) "Đã tạo snapshot trong ARCHIVE." else "ARCHIVE lỗi: ${result.exceptionOrNull()?.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        enabled = rootUri.isNotBlank()
                    ) { Text("TẠO SNAPSHOT ARCHIVE") }

                    if (dataFound) {
                        Button(
                            onClick = {
                                val result = DataBackup.restoreLatest(context, rootUri)
                                if (result.isSuccess) {
                                    Toast.makeText(context, "Đã RESTORE DATA + MEDIA + MASTER. App sẽ mở lại.", Toast.LENGTH_LONG).show()
                                    android.os.Process.killProcess(android.os.Process.myPid())
                                } else {
                                    message = "RESTORE DATA_LATEST lỗi: ${result.exceptionOrNull()?.message}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("KHÔI PHỤC TOÀN BỘ TỪ POS0210") }
                    }

                    OutlinedButton(
                        onClick = { restoreBackup.launch(arrayOf("application/octet-stream", "*/*")) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text("RESTORE DATA TỪ FILE KHÁC") }
                }
            }

            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("HƯỚNG DẪN LƯU TRỮ & KHÔI PHỤC", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(
                        "CẤU TRÚC ĐÚNG:\n" +
                        "Download/POS0210/CONFIG/POS0210_MASTER.0210\n" +
                        "Download/POS0210/DATA/POS0210_DATA_LATEST.db\n" +
                        "Download/POS0210/DATA/POS0210_MEDIA_LATEST.0210\n" +
                        "Download/POS0210/ARCHIVE/...\n\n" +
                        "LƯU TRỮ:\n" +
                        "• MASTER: bấm GHI MASTER NGAY khi thay menu, bàn, nhân viên/PIN, phân quyền, VietQR, máy in.\n" +
                        "• DATA_LATEST: app tự cập nhật sau các thao tác vận hành quan trọng.\n" +
                        "• MEDIA_LATEST: giữ ảnh món/combo/hóa đơn; cập nhật khi có thay đổi ảnh hoặc BACKUP NGAY.\n" +
                        "• ARCHIVE: tạo cả snapshot DB và media cùng mốc thời gian.\n\n" +
                        "CHUYỂN SANG MÁY KHÁC:\n" +
                        "1. Copy nguyên thư mục POS0210 vào Download của máy mới.\n" +
                        "2. Cài POS0210 và vào Dữ liệu & Backup.\n" +
                        "3. Gắn đúng thư mục Download/POS0210.\n" +
                        "4. Bấm KHÔI PHỤC TOÀN BỘ TỪ POS0210.\n" +
                        "5. App sẽ khôi phục DATA_LATEST + MEDIA_LATEST + MASTER, sau đó mở lại.\n\n" +
                        "LƯU Ý:\n" +
                        "• Sau khi cài lại app phải gắn lại thư mục vì Android có thể mất quyền SAF của app cũ.\n" +
                        "• Nên copy cả thư mục POS0210, không copy riêng từng file.",
                        Modifier.padding(top = 8.dp),
                        fontSize = 13.sp
                    )
                }
            }

            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("QUY TẮC AN TOÀN", fontWeight = FontWeight.Black)
                    Text(
                        "• App không tự tạo MASTER/DATA khi chưa gắn thư mục.\n" +
                        "• Restore DATA luôn áp MASTER lại để PIN/quyền/menu không bị snapshot DB cũ ghi đè.\n" +
                        "• Trước khi chuyển máy nên bấm GHI MASTER NGAY và BACKUP NGAY để chắc chắn lấy dữ liệu mới nhất.",
                        Modifier.padding(top = 8.dp)
                    )
                }
            }

            if (message.isNotBlank()) {
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(message, Modifier.padding(14.dp), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
fun TableManager(vm: PosViewModel) {
    val tables by vm.tables.collectAsState()
    val areas by vm.areas.collectAsState()
    val sessions by vm.sessions.collectAsState()
    var editing by remember { mutableStateOf<DiningTableEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column {
        Header("Bàn & khu vực") { vm.screen.value = "MANAGE" }
        Button(
            onClick = { adding = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        ) { Text("＋ THÊM BÀN") }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(tables) { t ->
                val areaName = areas.firstOrNull { it.id == t.areaId }?.name ?: t.areaId
                val occupied = sessions.any { it.tableId == t.id }
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editing = t }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontWeight = FontWeight.Bold)
                            Text(areaName, fontSize = 12.sp)
                        }
                        if (occupied) Text("ĐANG CÓ KHÁCH", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        else Text("SỬA", fontSize = 11.sp)
                    }
                }
            }
        }
    }

    if (adding) {
        TableEditorDialog(
            initial = null,
            areas = areas,
            onDismiss = { adding = false },
            onSave = { name, areaId ->
                vm.addTable(areaId, name)
                adding = false
            },
            onHide = null
        )
    }

    editing?.let { t ->
        val occupied = sessions.any { it.tableId == t.id }
        TableEditorDialog(
            initial = t,
            areas = areas,
            onDismiss = { editing = null },
            onSave = { name, areaId ->
                vm.updateTable(t, name, areaId)
                editing = null
            },
            onHide = if (occupied) null else {
                {
                    vm.hideTable(t)
                    editing = null
                }
            }
        )
    }
}

@Composable
fun TableEditorDialog(
    initial: DiningTableEntity?,
    areas: List<AreaEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onHide: (() -> Unit)?
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var areaId by remember(initial?.id, areas) {
        mutableStateOf(initial?.areaId ?: areas.firstOrNull()?.id.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Thêm bàn" else "Sửa bàn") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tên bàn") },
                    placeholder = { Text("Để trống sẽ tự đặt Bàn xx") }
                )
                Spacer(Modifier.height(10.dp))
                Text("Khu vực", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    areas.forEach { a ->
                        FilterChip(
                            selected = areaId == a.id,
                            onClick = { areaId = a.id },
                            label = { Text(a.name) }
                        )
                    }
                }
                if (initial != null && onHide == null) {
                    Text(
                        "Bàn đang có khách nên chưa thể ẩn.",
                        Modifier.padding(top = 10.dp),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, areaId) },
                enabled = areaId.isNotBlank()
            ) { Text("LƯU") }
        },
        dismissButton = {
            Row {
                if (onHide != null) {
                    TextButton(onClick = onHide) { Text("ẨN BÀN") }
                }
                TextButton(onClick = onDismiss) { Text("HỦY") }
            }
        }
    )
}

@Composable
fun Rowx(t: String, s: String, go: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(5.dp).clickable { go() }) {
        Column(Modifier.padding(16.dp)) {
            Text(t, fontWeight = FontWeight.Bold)
            Text(s, fontSize = 12.sp)
        }
    }
}

@Composable
fun Employees(vm: PosViewModel) {
    val es by vm.employees.collectAsState()
    val current by vm.currentEmployee.collectAsState()
    var editing by remember { mutableStateOf<EmployeeEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column {
        Header("Nhân viên") { vm.screen.value = "MANAGE" }
        if (current?.role == "ADMIN") {
            Button(
                onClick = { adding = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            ) { Text("＋ THÊM NHÂN VIÊN") }
        }
        LazyColumn {
            items(es) { e ->
                Card(Modifier.fillMaxWidth().padding(6.dp).clickable { if (current?.role == "ADMIN") editing = e }) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${e.name} · ${e.role}", fontWeight = FontWeight.Bold)
                        Text("Order ${e.canOrder} · Bếp ${e.canSendKitchen} · Thu ${e.canCheckout} · Nhập ${e.canPurchase}")
                        Text("Chạm để sửa quyền", fontSize = 12.sp)
                    }
                }
            }
        }
    }
    if (adding) {
        EmployeeEditor(null, onDismiss = { adding = false }) { name, pin, role, order, kitchen, checkout, purchase, report, menu, system ->
            vm.saveEmployee(name, pin, role, checkout, purchase, order, kitchen, report, menu, system)
            adding = false
        }
    }
    editing?.let { e ->
        EmployeeEditor(e, onDismiss = { editing = null }) { name, pin, role, order, kitchen, checkout, purchase, report, menu, system ->
            vm.updateEmployee(
                e.copy(
                    name = name,
                    pin = pin,
                    role = role,
                    canOrder = order,
                    canSendKitchen = kitchen,
                    canCheckout = checkout,
                    canPurchase = purchase,
                    canViewReport = report,
                    canManageMenu = menu,
                    canManageSystem = system
                )
            )
            editing = null
        }
    }
}

@Composable
fun EmployeeEditor(
    initial: EmployeeEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean, Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var pin by remember(initial?.id) { mutableStateOf(initial?.pin ?: "") }
    var role by remember(initial?.id) { mutableStateOf(initial?.role ?: "STAFF") }
    var order by remember(initial?.id) { mutableStateOf(initial?.canOrder ?: true) }
    var kitchen by remember(initial?.id) { mutableStateOf(initial?.canSendKitchen ?: true) }
    var checkout by remember(initial?.id) { mutableStateOf(initial?.canCheckout ?: false) }
    var purchase by remember(initial?.id) { mutableStateOf(initial?.canPurchase ?: false) }
    var report by remember(initial?.id) { mutableStateOf(initial?.canViewReport ?: false) }
    var menu by remember(initial?.id) { mutableStateOf(initial?.canManageMenu ?: false) }
    var system by remember(initial?.id) { mutableStateOf(initial?.canManageSystem ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Thêm nhân viên" else "Sửa nhân viên") },
        confirmButton = {
            Button(
                onClick = { onSave(name, pin, role, order, kitchen, checkout, purchase, report, menu, system) },
                enabled = name.isNotBlank() && pin.length == 4
            ) { Text("LƯU") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("HỦY") } },
        text = {
            LazyColumn {
                item {
                    OutlinedTextField(name, { name = it }, label = { Text("Tên nhân viên") })
                    OutlinedTextField(
                        pin,
                        { pin = it.filter(Char::isDigit).take(4) },
                        label = { Text("PIN 4 số") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Row {
                        FilterChip(role == "STAFF", { role = "STAFF" }, { Text("STAFF") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(role == "MANAGER", { role = "MANAGER" }, { Text("MANAGER") })
                    }
                    PermissionSwitch("Order món", order) { order = it }
                    PermissionSwitch("Gửi bếp", kitchen) { kitchen = it }
                    PermissionSwitch("Thanh toán", checkout) { checkout = it }
                    PermissionSwitch("Nhập đầu vào", purchase) { purchase = it }
                    PermissionSwitch("Xem báo cáo", report) { report = it }
                    PermissionSwitch("Quản lý menu", menu) { menu = it }
                    PermissionSwitch("Cấu hình hệ thống", system) { system = it }
                }
            }
        }
    )
}

@Composable
fun PermissionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ComboManager(vm: PosViewModel) {
    val combos by vm.combos.collectAsState()
    val menu by vm.menu.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    Column {
        Header("Combo") { vm.screen.value = "MANAGE" }
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text("＋ TẠO COMBO")
        }
        if (combos.isEmpty()) {
            Text("Chưa có combo.", Modifier.padding(20.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(combos) { combo ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!combo.imageUri.isNullOrBlank()) {
                                AsyncImage(model = combo.imageUri, contentDescription = combo.name, modifier = Modifier.size(62.dp))
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(combo.name, fontWeight = FontWeight.Bold)
                                Text(money(combo.price))
                                ComboComponentNames(vm, combo.id, menu)
                            }
                            Switch(checked = combo.active, onCheckedChange = { vm.toggleCombo(combo) })
                        }
                    }
                }
            }
        }
    }
    if (showAdd) {
        ComboAddDialog(menu.filter { it.active }, onDismiss = { showAdd = false }) { name, price, imageUri, selected ->
            vm.saveCombo(name, price, imageUri, selected)
            showAdd = false
        }
    }
}

@Composable
fun ComboComponentNames(vm: PosViewModel, comboId: String, menu: List<MenuItemEntity>) {
    val parts by vm.comboItems(comboId).collectAsState(initial = emptyList())
    val text = parts.mapNotNull { ci -> menu.firstOrNull { it.id == ci.menuItemId }?.let { "${ci.qty}×${it.name}" } }.joinToString(" + ")
    if (text.isNotBlank()) Text(text, fontSize = 11.sp)
}

@Composable
fun ComboAddDialog(
    menu: List<MenuItemEntity>,
    onDismiss: () -> Unit,
    onSave: (String, Long, String?, Map<String, Int>) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateMapOf<String, Int>() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            imageUri = uri.toString()
        }
    }
    val normalTotal = selected.entries.sumOf { (id, q) -> (menu.firstOrNull { it.id == id }?.price ?: 0L) * q }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo combo") },
        confirmButton = {
            Button(
                onClick = { onSave(name, priceText.toLongOrNull() ?: 0L, imageUri, selected.toMap()) },
                enabled = name.isNotBlank() && (priceText.toLongOrNull() ?: 0L) > 0 && selected.isNotEmpty()
            ) { Text("LƯU COMBO") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("HỦY") } },
        text = {
            LazyColumn {
                item {
                    OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Tên combo") })
                    OutlinedTextField(priceText, { priceText = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Giá combo") })
                    OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(if (imageUri == null) "＋ ẢNH COMBO" else "✓ ĐÃ CHỌN ẢNH")
                    }
                    Text("Giá lẻ các món đã chọn: ${money(normalTotal)}", fontWeight = FontWeight.Bold)
                    val comboPrice = priceText.toLongOrNull() ?: 0L
                    if (comboPrice > 0 && normalTotal > comboPrice) Text("Tiết kiệm: ${money(normalTotal - comboPrice)}", fontSize = 12.sp)
                    Text("Chọn món trong combo", Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold)
                }
                items(menu) { item ->
                    val q = selected[item.id] ?: 0
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Text(money(item.price), fontSize = 11.sp)
                        }
                        if (q > 0) {
                            TextButton(onClick = { if (q <= 1) selected.remove(item.id) else selected[item.id] = q - 1 }) { Text("−") }
                            Text(q.toString(), fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { selected[item.id] = q + 1 }) { Text("+") }
                    }
                }
            }
        }
    )
}

@Composable
fun PricingManager(vm: PosViewModel) {
    val rules by vm.pricingRules.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    Column {
        Header("Ưu đãi & điều chỉnh giá") { vm.screen.value = "MANAGE" }
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text("＋ TẠO CHƯƠNG TRÌNH")
        }
        Text("Giảm giá không cộng dồn: hệ thống chỉ áp dụng 1 mức giảm có giá trị lớn nhất.", Modifier.padding(horizontal = 16.dp), fontSize = 12.sp)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(rules) { rule ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.name, fontWeight = FontWeight.Bold)
                            Text("${if (rule.kind == "DISCOUNT") "Giảm" else "Phụ thu"} ${rule.percent}%${if (rule.code.isNotBlank()) " · Mã ${rule.code}" else ""}")
                            val mode = if (rule.autoApply) "Tự động" else "Theo mã"
                            Text(mode, fontSize = 11.sp)
                        }
                        Switch(checked = rule.active, onCheckedChange = { vm.togglePricingRule(rule) })
                    }
                }
            }
        }
    }
    if (showAdd) {
        PricingRuleDialog(onDismiss = { showAdd = false }) { name, code, kind, percent, startAt, endAt, startMin, endMin, autoApply ->
            vm.savePricingRule(name, code, kind, percent, startAt, endAt, startMin, endMin, autoApply)
            showAdd = false
        }
    }
}

@Composable
fun PricingRuleDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Long?, Long?, Int?, Int?, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("DISCOUNT") }
    var percentText by remember { mutableStateOf("") }
    var startText by remember { mutableStateOf("") }
    var endText by remember { mutableStateOf("") }
    var startHour by remember { mutableStateOf("") }
    var endHour by remember { mutableStateOf("") }
    var autoApply by remember { mutableStateOf(true) }
    fun parseDate(text: String): Long? = if (text.isBlank()) null else runCatching {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply { isLenient = false }.parse(text)?.time
    }.getOrNull()
    fun parseMinute(text: String): Int? {
        if (text.isBlank()) return null
        val parts = text.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chương trình giá") },
        confirmButton = {
            Button(
                onClick = { onSave(name, code, kind, percentText.toIntOrNull() ?: 0, parseDate(startText), parseDate(endText), parseMinute(startHour), parseMinute(endHour), autoApply) },
                enabled = name.isNotBlank() && (percentText.toIntOrNull() ?: 0) in 1..100 && (autoApply || code.isNotBlank())
            ) { Text("LƯU") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("HỦY") } },
        text = {
            LazyColumn {
                item {
                    OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Tên chương trình") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(kind == "DISCOUNT", { kind = "DISCOUNT" }, { Text("GIẢM") })
                        FilterChip(kind == "SURCHARGE", { kind = "SURCHARGE" }, { Text("TĂNG / PHỤ THU") })
                    }
                    OutlinedTextField(percentText, { percentText = it.filter(Char::isDigit).take(3) }, modifier = Modifier.fillMaxWidth(), label = { Text("% điều chỉnh") })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Tự động áp dụng", Modifier.weight(1f))
                        Switch(checked = autoApply, onCheckedChange = { autoApply = it })
                    }
                    if (!autoApply) {
                        OutlinedTextField(code, { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' }.take(30) }, modifier = Modifier.fillMaxWidth(), label = { Text("Mã ưu đãi") })
                    } else {
                        OutlinedTextField(code, { code = it.uppercase().take(30) }, modifier = Modifier.fillMaxWidth(), label = { Text("Mã tham chiếu (không bắt buộc)") })
                    }
                    Text("Thời hạn chung (để trống nếu không giới hạn)", Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
                    OutlinedTextField(startText, { startText = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Từ dd/MM/yyyy HH:mm") })
                    OutlinedTextField(endText, { endText = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Đến dd/MM/yyyy HH:mm") })
                    Text("Khung giờ lặp hàng ngày (để trống nếu cả ngày)", Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(startHour, { startHour = it.take(5) }, modifier = Modifier.weight(1f), label = { Text("Từ HH:mm") })
                        OutlinedTextField(endHour, { endHour = it.take(5) }, modifier = Modifier.weight(1f), label = { Text("Đến HH:mm") })
                    }
                }
            }
        }
    )
}

@Composable
fun MenuManager(vm: PosViewModel) {
    val menu by vm.menu.collectAsState()
    val categories by vm.categories.collectAsState()
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<MenuItemEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<MenuItemEntity?>(null) }
    var imageTarget by remember { mutableStateOf<MenuItemEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            imageTarget?.let { vm.setMenuImage(it, uri.toString()) }
        }
        imageTarget = null
    }
    Column {
        Header("Quản lý menu") { vm.screen.value = "MANAGE" }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { showAdd = true }, modifier = Modifier.weight(1f)) { Text("＋ THÊM MÓN") }
            OutlinedButton(onClick = { showCategoryManager = true }, modifier = Modifier.weight(1f)) { Text("NHÓM MÓN") }
        }
        LazyColumn {
            items(menu) { m ->
                Card(Modifier.fillMaxWidth().padding(6.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!m.imageUri.isNullOrBlank()) {
                            AsyncImage(
                                model = m.imageUri,
                                contentDescription = m.name,
                                modifier = Modifier.size(58.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                        } else {
                            Surface(
                                modifier = Modifier.size(58.dp),
                                color = Tint,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text("ẢNH", fontSize = 11.sp) }
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.Bold)
                            Text("${m.productCode} · ${categories.firstOrNull { it.id == m.categoryId }?.name ?: "Khác"}", fontSize = 12.sp)
                            Text(money(m.price))
                            if (m.description.isNotBlank()) Text(m.description, fontSize = 12.sp, maxLines = 2)
                            if (!m.active) Text("TẠM NGƯNG BÁN", color = Color(0xFF9A3412), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { editTarget = m }) { Text("SỬA") }
                        TextButton(onClick = {
                            imageTarget = m
                            picker.launch(arrayOf("image/*"))
                        }) { Text(if (m.imageUri.isNullOrBlank()) "＋ ẢNH" else "ĐỔI ẢNH") }
                        TextButton(onClick = { if (m.active) deleteTarget = m else vm.toggleMenu(m) }) { Text(if(m.active) "TẠM NGƯNG" else "BẬT BÁN") }
                    }
                }
            }
        }
    }
    if (showAdd) {
        MenuAddDialog(categories, null, onDismiss = { showAdd = false }) { name, price, cat, description, imageUri, active ->
            vm.saveMenu(name, price, cat, description, imageUri, active)
            showAdd = false
        }
    }
    editTarget?.let { original ->
        MenuAddDialog(categories, original, onDismiss = { editTarget = null }) { name, price, cat, description, imageUri, active ->
            vm.updateMenu(original, name, price, cat, description, imageUri, active)
            editTarget = null
        }
    }
    if (showCategoryManager) {
        CategoryManagerDialog(
            categories = categories,
            menu = menu,
            onDismiss = { showCategoryManager = false },
            onAdd = { vm.addCategory(it) },
            onDelete = { vm.deleteCategory(it) }
        )
    }
    deleteTarget?.let { m ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Xoá món") },
            text = { Text("Xoá ${m.name} khỏi menu bán? Lịch sử bill cũ vẫn được giữ.") },
            confirmButton = {
                Button(onClick = { vm.deleteMenu(m); deleteTarget = null }) { Text("XOÁ MÓN") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("HỦY") } }
        )
    }
}

@Composable
fun CategoryManagerDialog(
    categories: List<MenuCategoryEntity>,
    menu: List<MenuItemEntity>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (MenuCategoryEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nhóm món") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("ĐÓNG") } },
        text = {
            LazyColumn {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tên nhóm mới") }
                    )
                    Button(
                        onClick = { onAdd(name); name = "" },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) { Text("＋ THÊM NHÓM") }
                }
                items(categories) { c ->
                    val hasActiveItems = menu.any { it.active && it.categoryId == c.id }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(c.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = { onDelete(c) },
                            enabled = !hasActiveItems
                        ) { Text("XOÁ") }
                    }
                    if (hasActiveItems) {
                        Text("Còn món trong nhóm này", fontSize = 11.sp)
                    }
                }
            }
        }
    )
}

@Composable
fun MenuAddDialog(
    categories: List<MenuCategoryEntity>,
    initial: MenuItemEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, Long, String, String, String?, Boolean) -> Unit
) {
    val context = LocalContext.current
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var priceText by remember(initial?.id) { mutableStateOf(initial?.price?.toString() ?: "") }
    var cat by remember(categories, initial?.id) { mutableStateOf(initial?.categoryId ?: categories.firstOrNull()?.id ?: "") }
    var description by remember(initial?.id) { mutableStateOf(initial?.description ?: "") }
    var imageUri by remember(initial?.id) { mutableStateOf(initial?.imageUri) }
    var active by remember(initial?.id) { mutableStateOf(initial?.active ?: true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            imageUri = uri.toString()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Thêm món" else "Sửa món · ${initial.productCode}") },
        confirmButton = {
            Button(
                onClick = { onSave(name, priceText.toLongOrNull() ?: 0L, cat, description, imageUri, active) },
                enabled = name.isNotBlank() && priceText.toLongOrNull() != null && cat.isNotBlank()
            ) { Text("LƯU MÓN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("HỦY") } },
        text = {
            LazyColumn {
                item {
                    OutlinedTextField(name, { name = it }, label = { Text("Tên món") })
                    OutlinedTextField(
                        priceText,
                        { priceText = it.filter(Char::isDigit) },
                        label = { Text("Giá bán") }
                    )
                    OutlinedTextField(
                        description,
                        { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Mô tả / ghi chú món") },
                        minLines = 2,
                        maxLines = 4
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }) {
                        Text(if (imageUri == null) "＋ THÊM ẢNH MINH HOẠ" else "✓ ĐÃ CHỌN ẢNH · ĐỔI ẢNH")
                    }
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Ảnh món mới",
                            modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp)
                        )
                    }
                    Text("Nhóm món", Modifier.padding(top = 10.dp))
                    categories.forEach { c ->
                        FilterChip(
                            selected = cat == c.id,
                            onClick = { cat = c.id },
                            label = { Text(c.name) }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Đang bán", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }
            }
        }
    )
}

@Composable
fun Purchases(vm: PosViewModel) {
    val currentGuard by vm.currentEmployee.collectAsState()
    if (currentGuard?.role != "ADMIN" && currentGuard?.canPurchase != true) {
        Column {
            Header("Nhập đầu vào") { vm.screen.value = "MANAGE" }
            Text("Bạn không có quyền nhập đầu vào.", Modifier.padding(20.dp), fontWeight = FontWeight.Bold)
        }
        return
    }
    val purchases by vm.purchases.collectAsState()
    val purchaseCategories by vm.purchaseCategories.collectAsState()
    val context = LocalContext.current
    var itemName by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("kg") }
    var unitPriceText by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var invoiceImage by remember { mutableStateOf<String?>(null) }
    var selectedPurchase by remember { mutableStateOf<PurchaseEntity?>(null) }
    var selectedCategoryId by remember(purchaseCategories) {
        mutableStateOf(purchaseCategories.firstOrNull()?.id ?: "pc_production")
    }
    var showCategoryManager by remember { mutableStateOf(false) }
    var dateText by remember {
        mutableStateOf(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()))
    }
    var message by remember { mutableStateOf("") }

    val invoicePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            invoiceImage = uri.toString()
        }
    }

    val selectedCategory = purchaseCategories.firstOrNull { it.id == selectedCategoryId }
    val qty = qtyText.replace(',', '.').toDoubleOrNull()
    val unitPrice = unitPriceText.toLongOrNull()
    val total = if (qty != null && unitPrice != null) (qty * unitPrice).toLong() else 0L

    Column {
        Header("Nhập đầu vào") { vm.screen.value = "MANAGE" }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Phân mục", Modifier.weight(1f), fontWeight = FontWeight.Black, fontSize = 18.sp)
            OutlinedButton(onClick = { showCategoryManager = true }) { Text("QUẢN LÝ PHÂN MỤC") }
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                if (purchaseCategories.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        purchaseCategories.forEach { c ->
                            FilterChip(
                                selected = selectedCategoryId == c.id,
                                onClick = {
                                    selectedCategoryId = c.id
                                    unit = c.defaultUnit
                                },
                                label = { Text(c.name) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    dateText,
                    { dateText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ngày giờ dd/MM/yyyy HH:mm") }
                )
                OutlinedTextField(
                    supplier,
                    { supplier = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nhà cung cấp (không bắt buộc)") }
                )
                OutlinedTextField(
                    itemName,
                    { itemName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (selectedCategory?.id == "pc_salary") "Nội dung / nhân sự" else "Mặt hàng / nội dung chi") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        qtyText,
                        { qtyText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (selectedCategory?.id == "pc_salary") "Số ngày công / SL" else "Khối lượng / SL") }
                    )
                    OutlinedTextField(
                        unit,
                        { unit = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Đơn vị") }
                    )
                }
                OutlinedTextField(
                    unitPriceText,
                    { unitPriceText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (selectedCategory?.id == "pc_salary") "Đơn giá / ngày công" else "Đơn giá") }
                )
                OutlinedTextField(
                    note,
                    { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ghi chú") }
                )
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(selectedCategory?.name ?: "Chưa chọn phân mục", fontWeight = FontWeight.Bold)
                        Text("Thành tiền: ${money(total)}", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                }
                OutlinedButton(
                    onClick = { invoicePicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (invoiceImage == null) "＋ ẢNH HÓA ĐƠN (TÙY CHỌN)" else "✓ ĐÃ CHỌN ẢNH HÓA ĐƠN")
                }
                Button(
                    onClick = {
                        val parser = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        val parsedAt = runCatching { parser.parse(dateText)?.time }.getOrNull()
                            ?: System.currentTimeMillis()
                        vm.addPurchaseDetailed(
                            name = itemName,
                            qty = qty ?: 0.0,
                            unit = unit.ifBlank { selectedCategory?.defaultUnit ?: "lần" },
                            unitPrice = unitPrice ?: 0L,
                            note = note,
                            at = parsedAt,
                            supplierName = supplier,
                            imageUri = invoiceImage,
                            categoryId = selectedCategoryId
                        )
                        message = "Đã tạo phiếu nhập · ${selectedCategory?.name ?: ""} · ${money(total)}"
                        itemName = ""
                        qtyText = ""
                        unitPriceText = ""
                        note = ""
                        invoiceImage = null
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    enabled = selectedCategoryId.isNotBlank() && itemName.isNotBlank() && (qty ?: 0.0) > 0 && (unitPrice ?: 0L) > 0
                ) { Text("TẠO PHIẾU NHẬP") }

                if (message.isNotBlank()) {
                    Text(message, Modifier.padding(vertical = 6.dp), fontWeight = FontWeight.Bold)
                }
                Text("Phiếu nhập gần đây", Modifier.padding(top = 14.dp, bottom = 6.dp), fontWeight = FontWeight.Bold)
            }

            items(purchases.take(20)) { p ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedPurchase = p }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(time(p.purchasedAt), fontWeight = FontWeight.Bold)
                        Text("${money(p.total)} · ${p.note.ifBlank { "Không ghi chú" }}")
                        Text(if (!p.invoiceImageUri.isNullOrBlank()) "📷 Có ảnh hóa đơn · Chạm để xem" else "Chạm để xem chi tiết", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showCategoryManager) {
        PurchaseCategoryManagerDialog(
            categories = purchaseCategories,
            onDismiss = { showCategoryManager = false },
            onAdd = { name, defaultUnit -> vm.addPurchaseCategory(name, defaultUnit) },
            onDelete = { vm.deletePurchaseCategory(it) }
        )
    }

    selectedPurchase?.let { p ->
        PurchaseDetailDialog(vm, p) { selectedPurchase = null }
    }
}

@Composable
fun PurchaseCategoryManagerDialog(
    categories: List<PurchaseCategoryEntity>,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
    onDelete: (PurchaseCategoryEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var defaultUnit by remember { mutableStateOf("lần") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Phân mục nhập đầu vào") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("ĐÓNG") } },
        text = {
            LazyColumn {
                item {
                    OutlinedTextField(
                        name,
                        { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tên phân mục mới") }
                    )
                    OutlinedTextField(
                        defaultUnit,
                        { defaultUnit = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Đơn vị mặc định") }
                    )
                    Button(
                        onClick = {
                            onAdd(name, defaultUnit)
                            name = ""
                            defaultUnit = "lần"
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) { Text("＋ THÊM PHÂN MỤC") }
                }
                items(categories) { c ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Bold)
                            Text("ĐVT mặc định: ${c.defaultUnit}", fontSize = 11.sp)
                        }
                        TextButton(onClick = { onDelete(c) }) { Text("XOÁ") }
                    }
                }
            }
        }
    )
}

@Composable
fun PurchaseDetailDialog(vm: PosViewModel, p: PurchaseEntity, onDismiss: () -> Unit) {
    val items by vm.purchaseItems(p.id).collectAsState(initial = emptyList())
    val suppliers by vm.suppliers.collectAsState()
    val employees by vm.employees.collectAsState()
    val current by vm.currentEmployee.collectAsState()
    var showDelete by remember { mutableStateOf(false) }
    var deleteReason by remember { mutableStateOf("") }

    val supplierName = suppliers.firstOrNull { it.id == p.supplierId }?.name ?: "Không ghi"
    val enteredBy = employees.firstOrNull { it.id == p.enteredBy }?.name ?: p.enteredBy

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("ĐÓNG") } },
        dismissButton = {
            if (current?.role == "ADMIN") {
                TextButton(onClick = { showDelete = true }) { Text("XOÁ PHIẾU · ADMIN") }
            }
        },
        title = { Text("Phiếu nhập · ${time(p.purchasedAt)}") },
        text = {
            LazyColumn {
                item {
                    Text("Nhà cung cấp: $supplierName")
                    Text("Người nhập: $enteredBy")
                    if (p.note.isNotBlank()) Text("Ghi chú: ${p.note}")
                    Spacer(Modifier.height(8.dp))
                }
                items(items) { line ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(line.name, fontWeight = FontWeight.Bold)
                            Text("${line.qty} ${line.unit} × ${money(line.unitPrice)}")
                            Text("= ${money(line.amount)}")
                        }
                    }
                }
                item {
                    Text("TỔNG: ${money(p.total)}", Modifier.padding(vertical = 10.dp), fontWeight = FontWeight.Black)
                    if (!p.invoiceImageUri.isNullOrBlank()) {
                        Text("Ảnh hóa đơn", fontWeight = FontWeight.Bold)
                        AsyncImage(
                            model = p.invoiceImageUri,
                            contentDescription = "Ảnh hóa đơn",
                            modifier = Modifier.fillMaxWidth().height(320.dp).padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    )

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("XOÁ PHIẾU NHẬP") },
            text = {
                Column {
                    Text("Phiếu ${money(p.total)} sẽ bị loại khỏi chi phí đầu vào và báo cáo, nhưng vẫn giữ dấu vết audit.")
                    OutlinedTextField(
                        value = deleteReason,
                        onValueChange = { deleteReason = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        label = { Text("Lý do xoá bắt buộc") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deletePurchase(p, deleteReason)
                        showDelete = false
                        onDismiss()
                    },
                    enabled = deleteReason.isNotBlank()
                ) { Text("XÁC NHẬN XOÁ") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("HỦY") }
            }
        )
    }
}

@Composable
fun VietQr(vm: PosViewModel) {
    val current by vm.currentEmployee.collectAsState()
    if (current?.role != "ADMIN" && current?.role != "MANAGER") {
        Column {
            Header("VietQR") { vm.screen.value = "MANAGE" }
            Text("Tài khoản Staff chỉ được sử dụng QR đã cài đặt khi thanh toán.", Modifier.padding(20.dp))
        }
        return
    }
    val sets by vm.settings.collectAsState()
    val valueFor: (String) -> String = { key -> sets.firstOrNull { it.key == key }?.value ?: "" }
    var bank by remember(sets) { mutableStateOf(valueFor("bank_name")) }
    var acc by remember(sets) { mutableStateOf(valueFor("bank_account")) }
    var holder by remember(sets) { mutableStateOf(valueFor("bank_holder")) }
    var saved by remember { mutableStateOf(false) }
    Column {
        Header("VietQR") { vm.screen.value = "MANAGE" }
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(bank, { bank = it }, label = { Text("Ngân hàng / BANK_ID") })
            OutlinedTextField(acc, { acc = it.filter(Char::isDigit) }, label = { Text("Số tài khoản") })
            OutlinedTextField(holder, { holder = it }, label = { Text("Chủ tài khoản") })
            Button(onClick = {
                vm.saveSetting("bank_name", bank)
                vm.saveSetting("bank_account", acc)
                vm.saveSetting("bank_holder", holder)
                vm.saveSetting("qr_prefix", "0210")
                saved = true
            }) { Text("LƯU") }
            if (saved) Text("Đã lưu cấu hình VietQR", Modifier.padding(top = 8.dp))
            if (bank.isNotBlank() && acc.isNotBlank()) {
                Text("QR mẫu 1.000đ", Modifier.padding(top = 14.dp))
                AsyncImage(
                    model = vietQrUrl(bank, acc, holder, 1000, "0210 TEST"),
                    contentDescription = "QR mẫu VietQR",
                    modifier = Modifier.size(240.dp)
                )
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
                Text("XPRINTER XP‑N58H", fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text("58mm · vùng in 48mm · 203dpi · 384 dots · Bluetooth ESC/POS", fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
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
                            ) { Text("TEST IN XP‑N58H") }
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
                            Text("XP‑N58H · 58mm · bitmap 384px. In bitmap giúp giữ font tiếng Việt, chữ Bold và VietQR ổn định.")
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
    val qrUrl = if (setting("bank_name").isNotBlank() && setting("bank_account").isNotBlank()) {
        vietQrUrl(setting("bank_name"), setting("bank_account"), setting("bank_holder"), amount, "0210 BAN 02")
    } else ""

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
        if (qrUrl.isNotBlank()) {
            AsyncImage(
                model = qrUrl,
                contentDescription = "VietQR trên bill",
                modifier = Modifier.fillMaxWidth(0.78f).aspectRatio(1f).padding(top = 4.dp)
            )
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
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(section == "OVERVIEW", { section = "OVERVIEW" }, { Text("TỔNG QUAN") })
            FilterChip(section == "BILLS", { section = "BILLS" }, { Text("LỊCH SỬ BILL") })
            FilterChip(section == "PURCHASES", { section = "PURCHASES" }, { Text("NHẬP HÀNG") })
        }

        when (section) {
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
