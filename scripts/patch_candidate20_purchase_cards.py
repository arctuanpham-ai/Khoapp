from pathlib import Path

root = Path('POS0210')
main = root / 'app/src/main/java/vn/ecohome/pos0210/MainActivity.kt'
s = main.read_text()

recent_old = '''            items(purchases.take(20)) { p ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedPurchase = p }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(time(p.purchasedAt), fontWeight = FontWeight.Bold)
                        Text("${money(p.total)} · ${p.note.ifBlank { "Không ghi chú" }}")
                        if(p.paidByName.isNotBlank()) Text("Người chi: ${p.paidByName}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(if (!p.invoiceImageUri.isNullOrBlank()) "📷 Có ảnh hóa đơn · Chạm để xem" else "Chạm để xem chi tiết", fontSize = 12.sp)
                    }
                }
            }
'''
recent_new = '''            items(purchases.take(20)) { p ->
                val purchaseLines by vm.purchaseItems(p.id).collectAsState(initial = emptyList())
                val card = purchaseCardPresentation(purchaseLines.firstOrNull()?.name.orEmpty(), p.note)
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedPurchase = p }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(time(p.purchasedAt), fontWeight = FontWeight.Bold)
                        Text("${money(p.total)} · ${card.itemName.ifBlank { "Chưa có nội dung" }}")
                        if (card.note.isNotBlank()) Text("Ghi chú: ${card.note}", fontSize = 12.sp)
                        if(p.paidByName.isNotBlank()) Text("Người chi: ${p.paidByName}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(if (!p.invoiceImageUri.isNullOrBlank()) "📷 Có ảnh hóa đơn · Chạm để xem" else "Chạm để xem chi tiết", fontSize = 12.sp)
                    }
                }
            }
'''
if s.count(recent_old) != 1:
    raise SystemExit(f'recent purchase card anchor count={s.count(recent_old)}')
s = s.replace(recent_old, recent_new, 1)

report_old = '''                        items(filteredPurchases) { p ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedPurchase = p }) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(time(p.purchasedAt), fontWeight = FontWeight.Bold)
                                    Text(money(p.total))
                                    Text("Người chi: ${p.paidByName.ifBlank { "Chưa xác định" }}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(if (!p.invoiceImageUri.isNullOrBlank()) "📷 Có ảnh hóa đơn · Chạm để xem" else "Chạm để xem chi tiết", fontSize = 12.sp)
                                }
                            }
                        }
'''
report_new = '''                        items(filteredPurchases) { p ->
                            val purchaseLines by vm.purchaseItems(p.id).collectAsState(initial = emptyList())
                            val card = purchaseCardPresentation(purchaseLines.firstOrNull()?.name.orEmpty(), p.note)
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedPurchase = p }) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(time(p.purchasedAt), fontWeight = FontWeight.Bold)
                                    Text("${money(p.total)} · ${card.itemName.ifBlank { "Chưa có nội dung" }}")
                                    if (card.note.isNotBlank()) Text("Ghi chú: ${card.note}", fontSize = 12.sp)
                                    Text("Người chi: ${p.paidByName.ifBlank { "Chưa xác định" }}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(if (!p.invoiceImageUri.isNullOrBlank()) "📷 Có ảnh hóa đơn · Chạm để xem" else "Chạm để xem chi tiết", fontSize = 12.sp)
                                }
                            }
                        }
'''
if s.count(report_old) != 1:
    raise SystemExit(f'report purchase card anchor count={s.count(report_old)}')
s = s.replace(report_old, report_new, 1)

s = s.replace('Text("POS0210 v1.0.0-alpha52-candidate18 · versionCode 79"', 'Text("POS0210 v1.0.0-alpha52-candidate20 · versionCode 81"', 1)
main.write_text(s)

presentation = root / 'app/src/main/java/vn/ecohome/pos0210/PurchaseCardPresentation.kt'
presentation.write_text('''package vn.ecohome.pos0210\n\ndata class PurchaseCardPresentation(val itemName: String, val note: String)\n\nfun purchaseCardPresentation(itemName: String, note: String) =\n    PurchaseCardPresentation(itemName.trim(), note.trim())\n''')

build = root / 'app/build.gradle.kts'
b = build.read_text()
b = b.replace('versionCode = 80', 'versionCode = 81', 1)
b = b.replace('versionName = "1.0.0-alpha52-candidate19"', 'versionName = "1.0.0-alpha52-candidate20"', 1)
build.write_text(b)
