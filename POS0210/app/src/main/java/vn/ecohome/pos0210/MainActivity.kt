package vn.ecohome.pos0210

import android.net.Uri
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
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
        Text("Tạm tính ${money(total)}", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { vm.addMore() },
                modifier = Modifier.weight(1f)
            ) { Text("＋ GỌI THÊM") }
            Button(
                onClick = { vm.screen.value = "PAY" },
                modifier = Modifier.weight(1f)
            ) { Text("THANH TOÁN") }
        }
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
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
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
        Button(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        ) { Text("＋ THÊM MÓN") }
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
                            Text(money(m.price))
                        }
                        TextButton(onClick = {
                            imageTarget = m
                            picker.launch(arrayOf("image/*"))
                        }) { Text(if (m.imageUri.isNullOrBlank()) "＋ ẢNH" else "ĐỔI ẢNH") }
                        Switch(checked = m.active, onCheckedChange = { vm.toggleMenu(m) })
                    }
                }
            }
        }
    }
    if (showAdd) {
        MenuAddDialog(categories, onDismiss = { showAdd = false }) { name, price, cat, imageUri ->
            vm.saveMenu(name, price, cat, imageUri)
            showAdd = false
        }
    }
}

@Composable
fun MenuAddDialog(
    categories: List<MenuCategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (String, Long, String, String?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var cat by remember(categories) { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var imageUri by remember { mutableStateOf<String?>(null) }
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
        title = { Text("Thêm món") },
        confirmButton = {
            Button(
                onClick = { onSave(name, priceText.toLongOrNull() ?: 0L, cat, imageUri) },
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
                }
            }
        }
    )
}

@Composable
fun Purchases(vm: PosViewModel) {
    val purchases by vm.purchases.collectAsState()
    val itemSales by vm.itemSales.collectAsState()
    val context = LocalContext.current
    var itemName by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("kg") }
    var unitPriceText by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var invoiceImage by remember { mutableStateOf<String?>(null) }
    var selectedPurchase by remember { mutableStateOf<PurchaseEntity?>(null) }
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
    val qty = qtyText.replace(',', '.').toDoubleOrNull()
    val unitPrice = unitPriceText.toLongOrNull()
    val total = if (qty != null && unitPrice != null) (qty * unitPrice).toLong() else 0L

    Column {
        Header("Nhập đầu vào") { vm.screen.value = "MANAGE" }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
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
                    label = { Text("Mặt hàng") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        qtyText,
                        { qtyText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Khối lượng / SL") }
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
                    label = { Text("Đơn giá") }
                )
                OutlinedTextField(
                    note,
                    { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ghi chú") }
                )
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("Thành tiền: ${money(total)}", Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { invoicePicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (invoiceImage == null) "＋ ẢNH HÓA ĐƠN" else "✓ ĐÃ CHỌN ẢNH HÓA ĐƠN")
                }
                Button(
                    onClick = {
                        val parser = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        val parsedAt = runCatching { parser.parse(dateText)?.time }.getOrNull()
                            ?: System.currentTimeMillis()
                        vm.addPurchaseDetailed(
                            name = itemName,
                            qty = qty ?: 0.0,
                            unit = unit.ifBlank { "lần" },
                            unitPrice = unitPrice ?: 0L,
                            note = note,
                            at = parsedAt,
                            supplierName = supplier,
                            imageUri = invoiceImage
                        )
                        message = "Đã tạo phiếu nhập · ${money(total)}"
                        itemName = ""
                        qtyText = ""
                        unitPriceText = ""
                        note = ""
                        invoiceImage = null
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    enabled = itemName.isNotBlank() && (qty ?: 0.0) > 0 && (unitPrice ?: 0L) > 0
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

    selectedPurchase?.let { p ->
        PurchaseDetailDialog(vm, p) { selectedPurchase = null }
    }
}

@Composable
fun PurchaseDetailDialog(vm: PosViewModel, p: PurchaseEntity, onDismiss: () -> Unit) {
    val items by vm.purchaseItems(p.id).collectAsState(initial = emptyList())
    val suppliers by vm.suppliers.collectAsState()
    val employees by vm.employees.collectAsState()
    val supplierName = suppliers.firstOrNull { it.id == p.supplierId }?.name ?: "Không ghi"
    val enteredBy = employees.firstOrNull { it.id == p.enteredBy }?.name ?: p.enteredBy

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("ĐÓNG") } },
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
    val bills by vm.bills.collectAsState()
    val payments by vm.payments.collectAsState()
    val purchases by vm.purchases.collectAsState()
    val itemSales by vm.itemSales.collectAsState()
    var section by remember { mutableStateOf("OVERVIEW") }
    var periodDays by remember { mutableStateOf(1) }
    var selectedBill by remember { mutableStateOf<BillEntity?>(null) }
    var selectedPurchase by remember { mutableStateOf<PurchaseEntity?>(null) }

    val cal = Calendar.getInstance().apply {
        timeInMillis = System.currentTimeMillis()
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (periodDays > 1) add(Calendar.DAY_OF_YEAR, -(periodDays - 1))
    }
    val from = cal.timeInMillis
    val filteredBills = bills.filter { (it.closedAt ?: 0L) >= from }
    val filteredPurchases = purchases.filter { it.purchasedAt >= from }
    val billIds = filteredBills.map { it.id }.toSet()
    val filteredPayments = payments.filter { it.billId in billIds }
    val revenue = filteredBills.sumOf { it.total }
    val purchaseTotal = filteredPurchases.sumOf { it.total }
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
                }
                LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                    item { MetricCard("Doanh thu", money(revenue)) }
                    item { MetricCard("Số bill", filteredBills.size.toString()) }
                    item { MetricCard("Bill trung bình", money(avgBill)) }
                    item { MetricCard("Tiền mặt", money(cash)) }
                    item { MetricCard("Chuyển khoản", money(transfer)) }
                    item { MetricCard("Tổng nhập hàng", money(purchaseTotal)) }
                    item { MetricCard("Chênh lệch thu - nhập", money(revenue - purchaseTotal)) }
                    item {
                        Text(
                            "Món khách chọn nhiều",
                            Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
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
                    item {
                        Text(
                            "Dữ liệu lấy trực tiếp từ bill đã thanh toán và phiếu nhập.",
                            Modifier.padding(8.dp),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            "BILLS" -> {
                if (filteredBills.isEmpty()) {
                    Text("Chưa có bill trong kỳ đã chọn", Modifier.padding(20.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                        items(filteredBills) { b ->
                            val p = payments.firstOrNull { it.billId == b.id }
                            Card(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedBill = b }
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(b.billNo, fontWeight = FontWeight.Bold)
                                    Text("${b.closedAt?.let { time(it) } ?: "--"} · ${money(b.total)}")
                                    Text(if (p?.method == "TRANSFER") "Chuyển khoản" else "Tiền mặt", fontSize = 12.sp)
                                    Text("Chạm để xem chi tiết bill", fontSize = 12.sp)
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
                        item {
                            Text("Tổng nhập: ${money(purchaseTotal)}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        items(filteredPurchases) { p ->
                            Card(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedPurchase = p }
                            ) {
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
    val payment = payments.firstOrNull { it.billId == bill.id }
    val tableName = tables.firstOrNull { it.id == session?.tableId }?.name ?: session?.tableId ?: "?"
    val cashier = employees.firstOrNull { it.id == payment?.cashierId }?.name ?: payment?.cashierId ?: "?"
    val methodText = if (payment?.method == "TRANSFER") "Chuyển khoản" else "Tiền mặt"

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("ĐÓNG") } },
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
