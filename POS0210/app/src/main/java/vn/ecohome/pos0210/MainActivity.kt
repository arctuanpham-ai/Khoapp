package vn.ecohome.pos0210

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import vn.ecohome.pos0210.data.*
import java.text.SimpleDateFormat
import java.util.*

private val Cream = Color(0xFFF7F2E9)
private val Coffee = Color(0xFF65422F)
private val Tint = Color(0xFFE9E2EA)
private val Occupied = Color(0xFFE2C2A8)

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
        "EMP" -> Employees(vm)
        "PURCHASE" -> Purchases(vm)
        "VIETQR" -> VietQr(vm)
        "PRINTER" -> Printer(vm)
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
    Column {
        Header()
        Operator(vm)
        LazyColumn(Modifier.weight(1f).padding(12.dp)) {
            items(ts) { tb ->
                val open = ss.firstOrNull { it.tableId == tb.id }
                Card(
                    Modifier.fillMaxWidth().height(92.dp).padding(5.dp).clickable { vm.selectTable(tb) },
                    colors = CardDefaults.cardColors(containerColor = if (open == null) Tint else Occupied)
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(tb.name, fontWeight = FontWeight.Bold)
                        Text(if (open == null) "Trống" else "● ĐANG CÓ KHÁCH · ${time(open.openedAt)}")
                    }
                }
            }
        }
        Row(Modifier.padding(12.dp)) {
            Button({ vm.screen.value = "MANAGE" }, Modifier.weight(1f)) { Text("QUẢN LÝ") }
            Button({ vm.screen.value = "REPORT" }, Modifier.weight(1f)) { Text("BÁO CÁO") }
        }
    }
}

@Composable
fun Order(vm: PosViewModel, t: DiningTableEntity) {
    val ms by vm.menu.collectAsState()
    val cart by vm.cart.collectAsState()
    Column {
        Header(t.name) { vm.screen.value = "TABLES" }
        LazyColumn(Modifier.weight(1f).padding(12.dp)) {
            items(ms.filter { it.active }) { m ->
                val q = cart[m.id] ?: 0
                Card(Modifier.fillMaxWidth().padding(4.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!m.imageUri.isNullOrBlank()) {
                            AsyncImage(
                                model = m.imageUri,
                                contentDescription = m.name,
                                modifier = Modifier.size(58.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.Bold)
                            Text(money(m.price))
                        }
                        if (q > 0) Text("− $q", Modifier.clickable { vm.sub(m) }.padding(10.dp))
                        Text("＋", Modifier.clickable { vm.add(m) }.padding(10.dp))
                    }
                }
            }
        }
        Button(
            onClick = { vm.sendBatch() },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            enabled = cart.isNotEmpty()
        ) { Text("GỬI LÀM HÀNG") }
    }
}

@Composable
fun Sent(vm: PosViewModel, t: DiningTableEntity, s: TableSessionEntity) {
    val bs by vm.batches(s.id).collectAsState(initial = emptyList())
    val total by vm.total(s.id).collectAsState(initial = 0)
    var pv by remember { mutableStateOf<OrderBatchEntity?>(null) }
    Column {
        Header(t.name) { vm.screen.value = "TABLES" }
        LazyColumn(Modifier.weight(1f)) {
            items(bs) { b ->
                Card(Modifier.fillMaxWidth().padding(8.dp).clickable { pv = b }) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Đơn #${b.sequence}", fontWeight = FontWeight.Bold)
                        Text(b.status)
                    }
                }
            }
        }
        Text("Tạm tính ${money(total)}", Modifier.padding(16.dp))
        Button({ vm.screen.value = "PAY" }, Modifier.fillMaxWidth().padding(16.dp)) { Text("THANH TOÁN") }
    }
    pv?.let { b ->
        val its by vm.items(b.id).collectAsState(initial = emptyList())
        AlertDialog(
            onDismissRequest = { pv = null },
            confirmButton = {
                Button(onClick = { vm.markBatchSent(b); pv = null }) { Text("XÁC NHẬN GỬI") }
            },
            dismissButton = { TextButton(onClick = { pv = null }) { Text("ĐÓNG") } },
            title = { Text("PREVIEW PHIẾU BẾP") },
            text = {
                Column {
                    Text("0210 · ${t.name}")
                    its.forEach { Text("${it.qty} × ${it.itemNameSnapshot}") }
                }
            }
        )
    }
}

@Composable
fun Pay(vm: PosViewModel, t: DiningTableEntity, s: TableSessionEntity) {
    val total by vm.total(s.id).collectAsState(initial = 0)
    val e by vm.currentEmployee.collectAsState()
    val settings by vm.settings.collectAsState()
    fun setting(key: String) = settings.firstOrNull { it.key == key }?.value ?: ""
    var method by remember { mutableStateOf("CASH") }
    val qrInfo = "${setting("qr_prefix").ifBlank { "0210" }} ${t.name}"
    val qrUrl = if (setting("bank_name").isNotBlank() && setting("bank_account").isNotBlank()) {
        vietQrUrl(setting("bank_name"), setting("bank_account"), setting("bank_holder"), total, qrInfo)
    } else ""

    Column {
        Header("Thanh toán") { vm.screen.value = "SENT" }
        Column(Modifier.weight(1f).padding(18.dp)) {
            Text("TỔNG CỘNG")
            Text(money(total), fontSize = 38.sp, fontWeight = FontWeight.Black)
            Row {
                FilterChip(method == "CASH", { method = "CASH" }, { Text("TIỀN MẶT") })
                FilterChip(method == "TRANSFER", { method = "TRANSFER" }, { Text("CHUYỂN KHOẢN") })
            }
            Text("🔒 Thu tiền: ${e?.name}", Modifier.padding(vertical = 14.dp))
            if (method == "TRANSFER") {
                Card {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("VIETQR", fontWeight = FontWeight.Bold)
                        if (qrUrl.isBlank()) {
                            Text("Chưa cấu hình tài khoản VietQR")
                        } else {
                            AsyncImage(
                                model = qrUrl,
                                contentDescription = "Mã VietQR thanh toán",
                                modifier = Modifier.size(280.dp)
                            )
                            Text("${setting("bank_name")} · ${setting("bank_account")}")
                            Text("${money(total)} · $qrInfo")
                        }
                    }
                }
            }
        }
        Button(
            onClick = { vm.close(method, total) },
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            enabled = total > 0
        ) { Text("XÁC NHẬN THANH TOÁN") }
    }
}

@Composable
fun Manage(vm: PosViewModel) {
    val employee by vm.currentEmployee.collectAsState()
    Column {
        Header("Quản lý") { vm.screen.value = "TABLES" }
        Column(Modifier.padding(16.dp)) {
            if (employee?.role == "ADMIN" || employee?.canManageMenu == true) {
                Rowx("Quản lý menu", "Thêm món · ảnh · bật/tắt") { vm.screen.value = "MENU" }
            }
            if (employee?.role == "ADMIN") {
                Rowx("Nhân viên", "Thêm · khóa · phân quyền") { vm.screen.value = "EMP" }
            }
            Rowx("Nhập đầu vào", "Ngày giờ thủ công") { vm.screen.value = "PURCHASE" }
            Rowx("VietQR", "Lưu tài khoản · tạo QR") { vm.screen.value = "VIETQR" }
            Rowx("Máy in", "Cấu hình") { vm.screen.value = "PRINTER" }
            Rowx("Nhật ký", "Audit") { vm.screen.value = "SETTINGS" }
        }
    }
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
fun MenuManager(vm: PosViewModel) {
    val menu by vm.menu.collectAsState()
    val categories by vm.categories.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var imageTarget by remember { mutableStateOf<MenuItemEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageTarget?.let { vm.setMenuImage(it, uri?.toString()) }
        imageTarget = null
    }
    Column {
        Header("Quản lý menu") { vm.screen.value = "MANAGE" }
        Button(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        ) { Text("＋ THÊM MÓN") }
        LazyColumn {
            items(menu) { m ->
                Card(Modifier.fillMaxWidth().padding(6.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!m.imageUri.isNullOrBlank()) {
                            AsyncImage(model = m.imageUri, contentDescription = m.name, modifier = Modifier.size(54.dp))
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.Bold)
                            Text(money(m.price))
                        }
                        TextButton(onClick = { imageTarget = m; picker.launch("image/*") }) { Text("ẢNH") }
                        Switch(checked = m.active, onCheckedChange = { vm.toggleMenu(m) })
                    }
                }
            }
        }
    }
    if (showAdd) {
        MenuAddDialog(categories, onDismiss = { showAdd = false }) { name, price, cat ->
            vm.saveMenu(name, price, cat)
            showAdd = false
        }
    }
}

@Composable
fun MenuAddDialog(
    categories: List<MenuCategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (String, Long, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var cat by remember(categories) { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm món") },
        confirmButton = {
            Button(
                onClick = { onSave(name, priceText.toLongOrNull() ?: 0L, cat) },
                enabled = name.isNotBlank() && priceText.toLongOrNull() != null && cat.isNotBlank()
            ) { Text("LƯU MÓN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("HỦY") } },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Tên món") })
                OutlinedTextField(
                    priceText,
                    { priceText = it.filter(Char::isDigit) },
                    label = { Text("Giá bán") }
                )
                Text("Nhóm món", Modifier.padding(top = 10.dp))
                categories.forEach { c ->
                    FilterChip(
                        selected = cat == c.id,
                        onClick = { cat = c.id },
                        label = { Text(c.name) }
                    )
                }
            }
        }
    )
}

@Composable
fun Purchases(vm: PosViewModel) {
    var itemName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())) }
    Column {
        Header("Nhập đầu vào") { vm.screen.value = "MANAGE" }
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(dateText, { dateText = it }, label = { Text("Ngày giờ dd/MM/yyyy HH:mm") })
            OutlinedTextField(itemName, { itemName = it }, label = { Text("Mặt hàng") })
            OutlinedTextField(amountText, { amountText = it.filter(Char::isDigit) }, label = { Text("Tổng tiền") })
            Button(
                onClick = {
                    val parser = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    val parsedAt = runCatching { parser.parse(dateText)?.time }.getOrNull() ?: System.currentTimeMillis()
                    vm.addPurchase(itemName, amountText.toLongOrNull() ?: 0L, "", parsedAt)
                },
                enabled = itemName.isNotBlank() && amountText.isNotBlank()
            ) { Text("TẠO PHIẾU") }
        }
    }
}

@Composable
fun VietQr(vm: PosViewModel) {
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
    Column {
        Header("Máy in") { vm.screen.value = "MANAGE" }
        Text("Driver ESC/POS chưa kích hoạt", Modifier.padding(20.dp))
    }
}

@Composable
fun Report(vm: PosViewModel) {
    val bs by vm.bills.collectAsState()
    val revenue = bs.sumOf { it.total }
    Column {
        Header("Báo cáo") { vm.screen.value = "TABLES" }
        Text("Doanh thu ${money(revenue)}", Modifier.padding(20.dp), fontSize = 24.sp)
    }
}

@Composable
fun Settings(vm: PosViewModel) {
    val a by vm.audits.collectAsState()
    Column {
        Header("Nhật ký") { vm.screen.value = "MANAGE" }
        LazyColumn {
            items(a.take(30)) { event ->
                Text("${time(event.occurredAt)} · ${event.action}", Modifier.padding(8.dp))
            }
        }
    }
}
