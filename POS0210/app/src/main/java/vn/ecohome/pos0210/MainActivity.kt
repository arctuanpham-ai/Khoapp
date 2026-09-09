package vn.ecohome.pos0210

import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.text.SimpleDateFormat
import java.util.*

private val Cream = Color(0xFFF6F0E6)
private val Coffee = Color(0xFF6E432D)
private val Tint = Color(0xFFF1ECE3)
private val Occupied = Color(0xFFE7C4AA)

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
        "TABLE_ADMIN" -> TableManager(vm)
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
    val areas by vm.areas.collectAsState()

    Column {
        Header()
        Operator(vm)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(true, {}, { Text("BÁN HÀNG") })
            FilterChip(false, { vm.screen.value = "REPORT" }, { Text("LỊCH SỬ") })
            FilterChip(false, { vm.screen.value = "REPORT" }, { Text("BÁO CÁO") })
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
                    Card(
                        Modifier.fillMaxWidth().height(cardHeight).clickable { vm.selectTable(tb) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (open == null) Tint else Occupied
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
                                if (cardHeight > 95.dp) {
                                    Text(time(open.openedAt), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ vm.screen.value = "MANAGE" }, Modifier.weight(1f)) { Text("QUẢN LÝ") }
            Button({ vm.screen.value = "REPORT" }, Modifier.weight(1f)) { Text("BÁO CÁO") }
        }
    }
}

@Composable
fun Order(vm: PosViewModel, t: DiningTableEntity) {
    val ms by vm.menu.collectAsState()
    val cats by vm.categories.collectAsState()
    val cart by vm.cart.collectAsState()
    var selectedCat by remember(cats) { mutableStateOf(cats.firstOrNull()?.id ?: "") }
    val visible = ms.filter { it.active && (selectedCat.isBlank() || it.categoryId == selectedCat) }
    val itemCount = cart.values.sum()
    val total = cart.entries.sumOf { (id, q) -> (ms.firstOrNull { it.id == id }?.price ?: 0L) * q }

    Column {
        Header(t.name) { vm.screen.value = "TABLES" }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            cats.take(5).forEach { c ->
                FilterChip(
                    selected = selectedCat == c.id,
                    onClick = { selectedCat = c.id },
                    label = { Text(c.name, fontSize = 11.sp) }
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            gridItems(visible, key = { it.id }) { m ->
                val q = cart[m.id] ?: 0
                Card(
                    Modifier.fillMaxWidth().clickable { vm.add(m) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFBF8F2))
                ) {
                    Column {
                        if (!m.imageUri.isNullOrBlank()) {
                            AsyncImage(
                                model = m.imageUri,
                                contentDescription = m.name,
                                modifier = Modifier.fillMaxWidth().height(92.dp)
                            )
                        } else {
                            Box(
                                Modifier.fillMaxWidth().height(70.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("0210", fontWeight = FontWeight.Black) }
                        }
                        Column(Modifier.padding(8.dp)) {
                            Text(m.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(money(m.price), fontSize = 12.sp)
                            if (q > 0) {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("−", Modifier.clickable { vm.sub(m) }.padding(5.dp), fontWeight = FontWeight.Bold)
                                    Text(q.toString(), fontWeight = FontWeight.Black)
                                    Text("+", Modifier.clickable { vm.add(m) }.padding(5.dp), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$itemCount món", fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(money(total), fontWeight = FontWeight.Black)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("XEM GIỎ HÀNG") }
            Button(
                onClick = { vm.sendBatch() },
                modifier = Modifier.weight(1f),
                enabled = cart.isNotEmpty()
            ) { Text("GỬI LÀM HÀNG") }
        }
    }
}

@Composable
fun Sent(vm: PosViewModel, t: DiningTableEntity, s: TableSessionEntity) {
    val bs by vm.batches(s.id).collectAsState(initial = emptyList())
    val total by vm.total(s.id).collectAsState(initial = 0)
    val current by vm.currentEmployee.collectAsState()
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
                                "SENT" -> "ĐÃ GỬI BẾP"
                                else -> b.status
                            }
                        )
                    }
                }
            }
        }
        Text("Tạm tính ${money(total)}", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
                    Button(onClick = { vm.markBatchSent(b); pv = null }) { Text("XÁC NHẬN GỬI") }
                } else {
                    Button(onClick = { pv = null }) { Text("ĐÓNG") }
                }
            },
            dismissButton = {},
            title = { Text("Đơn #${b.sequence} · ${b.status}") },
            text = {
                Column {
                    Text("0210 · ${t.name}")
                    its.forEach { Text("${it.qty} × ${it.itemNameSnapshot}") }
                    if (b.status != "CANCELLED" && (current?.role == "ADMIN" || current?.role == "MANAGER")) {
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
    val context = LocalContext.current
    var backupMessage by remember { mutableStateOf("") }

    val exportMasterConfig = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val result = ConfigBackup.exportConfig(context, uri)
            if (result.isSuccess) {
                vm.saveSetting("master_config_uri", uri.toString())
                ConfigBackup.saveMasterToDownloads(context)
            }
            backupMessage = if (result.isSuccess) "Đã lưu MASTER CONFIG .0210 + bản tự nhận trong Downloads" else "MASTER lỗi: ${result.exceptionOrNull()?.message}"
        }
    }

    val importMasterConfig = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val result = ConfigBackup.importConfig(context, uri)
            if (result.isSuccess) {
                ConfigBackup.saveMasterToDownloads(context)
                Toast.makeText(context, "Đã khôi phục MASTER CONFIG. Mở lại app.", Toast.LENGTH_LONG).show()
                android.os.Process.killProcess(android.os.Process.myPid())
            } else {
                backupMessage = "Restore MASTER lỗi: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val result = DataBackup.exportDatabase(context, uri)
            backupMessage = if (result.isSuccess) "Đã xuất file backup." else "Backup lỗi: ${result.exceptionOrNull()?.message}"
        }
    }
    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val result = DataBackup.restoreDatabase(context, uri)
            if (result.isSuccess) {
                Toast.makeText(context, "Đã khôi phục dữ liệu. Hãy mở lại app.", Toast.LENGTH_LONG).show()
                android.os.Process.killProcess(android.os.Process.myPid())
            } else {
                backupMessage = "Restore lỗi: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    val chooseAutoBackupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.saveSetting("autoback_tree_uri", uri.toString())
            val result = DataBackup.autoBackup(context, uri.toString())
            backupMessage = if (result.isSuccess) {
                "Autobackup đã bật · file POS0210_autoback_latest.db"
            } else {
                "Autobackup lỗi: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    Column {
        Header("Quản lý") { vm.screen.value = "TABLES" }
        Column(Modifier.padding(16.dp)) {
            if (employee?.role == "ADMIN" || employee?.canManageMenu == true) {
                Rowx("Quản lý menu", "Thêm món · ảnh · bật/tắt") { vm.screen.value = "MENU" }
            }
            if (employee?.role == "ADMIN") {
                Rowx("Nhân viên", "Thêm · khóa · phân quyền") { vm.screen.value = "EMP" }
            }
            if (employee?.role == "ADMIN" || employee?.canManageSystem == true) {
                Rowx("Bàn & khu vực", "Thêm · sửa · Trong nhà / Ngoài trời") { vm.screen.value = "TABLE_ADMIN" }
            }
            Rowx("Nhập đầu vào", "Ngày giờ thủ công") { vm.screen.value = "PURCHASE" }
            if (employee?.role == "ADMIN" || employee?.role == "MANAGER") {
                Rowx("VietQR", "Lưu tài khoản · tạo QR") { vm.screen.value = "VIETQR" }
            }
            Rowx("Máy in", "Cấu hình") { vm.screen.value = "PRINTER" }

            if (employee?.role == "ADMIN" || employee?.canManageSystem == true) {
                Card(Modifier.fillMaxWidth().padding(5.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("MASTER CONFIG", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("Menu · ảnh món · bàn · nhân viên · VietQR · cấu hình máy in", fontSize = 12.sp)
                        Text(
                            if (vm.setting("master_config_uri").isBlank()) "Chưa gắn file MASTER" else "MASTER tự cập nhật: ĐÃ BẬT",
                            Modifier.padding(vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { exportMasterConfig.launch("POS0210_MASTER.0210") },
                                modifier = Modifier.weight(1f)
                            ) { Text("XUẤT MASTER") }
                            Button(
                                onClick = { importMasterConfig.launch(arrayOf("*/*")) },
                                modifier = Modifier.weight(1f)
                            ) { Text("LOAD MASTER") }
                        }
                    }
                }

                Card(Modifier.fillMaxWidth().padding(5.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Sao lưu dữ liệu", fontWeight = FontWeight.Bold)
                        Text("Xuất/khôi phục dữ liệu và bật Autobackup tự động.", fontSize = 12.sp)
                        OutlinedButton(
                            onClick = { chooseAutoBackupFolder.launch(null) },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        ) { Text("CHỌN THƯ MỤC AUTOBACKUP") }
                        val autoFolder = vm.setting("autoback_tree_uri")
                        Text(
                            if (autoFolder.isBlank()) "Autobackup: CHƯA BẬT" else "Autobackup: ĐÃ BẬT",
                            Modifier.padding(top = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                    exportBackup.launch("POS0210_backup_$stamp.db")
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("BACKUP") }
                            Button(
                                onClick = { restoreBackup.launch(arrayOf("application/octet-stream", "*/*")) },
                                modifier = Modifier.weight(1f)
                            ) { Text("RESTORE") }
                        }
                        if (backupMessage.isNotBlank()) Text(backupMessage, Modifier.padding(top = 8.dp), fontSize = 12.sp)
                    }
                }
            }
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
fun MenuManager(vm: PosViewModel) {
    val menu by vm.menu.collectAsState()
    val categories by vm.categories.collectAsState()
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }
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
            items(menu.filter { it.active }) { m ->
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
                        TextButton(onClick = { deleteTarget = m }) { Text("XOÁ") }
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
    Column {
        Header("Máy in") { vm.screen.value = "MANAGE" }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("MÁY IN BILL NHIỆT K80 · ESC/POS", fontWeight = FontWeight.Black)
            Text("Chế độ hiện tại: TEST / PREVIEW · chưa cần máy in thật", fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton({ vm.previewKitchen() }, Modifier.weight(1f)) { Text("PHIẾU BẾP", fontSize = 11.sp) }
                OutlinedButton({ vm.previewBill() }, Modifier.weight(1f)) { Text("BILL", fontSize = 11.sp) }
                OutlinedButton({ vm.previewCancel() }, Modifier.weight(1f)) { Text("PHIẾU HỦY", fontSize = 11.sp) }
            }
            if (preview.isNotBlank()) {
                Card(
                    Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp),
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
                        Text("Test trước khi mua máy", fontWeight = FontWeight.Bold)
                        Text("Preview kiểm tra nội dung, QR và bố cục. Khi có máy K80 Bluetooth ESC/POS sẽ dùng cùng dữ liệu này để in thật.")
                    }
                }
            }
        }
    }
}

@Composable
fun SimplePrintPreview(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("0210", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text("BREAKFAST · COFFEE · DRINKS", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Text(title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        Text(body.substringAfter("--------------------------------").trim(), fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
fun BillPrintPreview(vm: PosViewModel) {
    val settings by vm.settings.collectAsState()
    fun setting(key: String) = settings.firstOrNull { it.key == key }?.value ?: ""
    val amount = 135000L
    val qrUrl = if (setting("bank_name").isNotBlank() && setting("bank_account").isNotBlank()) {
        vietQrUrl(setting("bank_name"), setting("bank_account"), setting("bank_holder"), amount, "0210 BAN 02")
    } else ""

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("0210", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("BREAKFAST · COFFEE · DRINKS", fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Text("BILL THANH TOÁN", fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text("BÀN 02  ·  08:32–09:25", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        HorizontalDivider(Modifier.padding(vertical = 10.dp))

        PrintLine("2 × Bún gà", "80.000đ", bold = false)
        PrintLine("1 × Bạc xỉu", "30.000đ", bold = false)
        PrintLine("1 × Đen đá", "25.000đ", bold = false)

        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        PrintLine("TỔNG CỘNG", "135.000đ", bold = true, large = true)
        Text("Thanh toán: TIỀN MẶT / CHUYỂN KHOẢN", Modifier.fillMaxWidth(), fontSize = 11.sp)

        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Text("QUÉT MÃ THANH TOÁN", fontSize = 13.sp, fontWeight = FontWeight.Black)
        if (qrUrl.isNotBlank()) {
            AsyncImage(
                model = qrUrl,
                contentDescription = "VietQR trên bill",
                modifier = Modifier.size(180.dp).padding(top = 6.dp)
            )
            Text("${setting("bank_name")} · ${setting("bank_account")}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("Nội dung: 0210 BAN 02", fontSize = 10.sp)
        } else {
            Box(
                Modifier.size(150.dp).padding(10.dp),
                contentAlignment = Alignment.Center
            ) { Text("CHƯA CẤU HÌNH VIETQR", textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }

        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Text("CẢM ƠN QUÝ KHÁCH!", fontWeight = FontWeight.Black, fontSize = 13.sp)
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
            fontSize = if (large) 17.sp else 13.sp
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
