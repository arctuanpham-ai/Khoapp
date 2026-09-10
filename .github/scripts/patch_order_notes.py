from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        print(f"SKIP {label}: pattern not found or already applied")
        return text
    print(f"PATCH {label}")
    return text.replace(old, new, 1)


# PosViewModel.kt
vm = Path("POS0210/app/src/main/java/vn/ecohome/pos0210/PosViewModel.kt")
s = vm.read_text()

s = replace_once(
    s,
    'val customerUpdateMessage=MutableStateFlow("");val cart=MutableStateFlow<Map<String,Int>>(emptyMap());',
    'val customerUpdateMessage=MutableStateFlow("");val cartNotes=MutableStateFlow<Map<String,String>>(emptyMap());val cart=MutableStateFlow<Map<String,Int>>(emptyMap());',
    "cartNotes state",
)

marker = ' fun add(i:MenuItemEntity){cart.value=cart.value.toMutableMap().apply{put(i.id,(get(i.id)?:0)+1)}}'
if 'fun setCartNote(' not in s:
    s = replace_once(
        s,
        marker,
        ' fun setCartNote(key:String,note:String){cartNotes.value=cartNotes.value.toMutableMap().apply{if(note.isBlank())remove(key) else put(key,note.take(120))}}\n' + marker,
        "setCartNote",
    )

s = replace_once(
    s,
    'cart.value=emptyMap()\n   if(existing!=null)',
    'cart.value=emptyMap();cartNotes.value=emptyMap()\n   if(existing!=null)',
    "clear notes on select table",
)

s = replace_once(
    s,
    'fun addMore(){val e=currentEmployee.value?:return;if(!e.canOrder&&e.role!="ADMIN")return;if(currentSession.value==null||currentTable.value==null)return;cart.value=emptyMap();screen.value="ORDER"}',
    'fun addMore(){val e=currentEmployee.value?:return;if(!e.canOrder&&e.role!="ADMIN")return;if(currentSession.value==null||currentTable.value==null)return;cart.value=emptyMap();cartNotes.value=emptyMap();screen.value="ORDER"}',
    "clear notes on addMore",
)

s = replace_once(
    s,
    'its.add(OrderItemEntity("","","combo:"+combo.id,"COMBO · ${combo.name} [${parts.joinToString(" + ")}]",combo.price,q))',
    'its.add(OrderItemEntity("","","combo:"+combo.id,"COMBO · ${combo.name} [${parts.joinToString(" + ")}]",combo.price,q,(cartNotes.value[id] ?: "").trim()))',
    "combo note",
)

s = replace_once(
    s,
    'menu.value.firstOrNull{it.id==id}?.let{its.add(OrderItemEntity("","",it.id,it.name,it.price,q))}',
    'menu.value.firstOrNull{it.id==id}?.let{its.add(OrderItemEntity("","",it.id,it.name,it.price,q,(cartNotes.value[id] ?: "").trim()))}',
    "menu note",
)

s = replace_once(
    s,
    'repo.createBatch(s.id,bs.size+1,e.id,its)\n   cart.value=emptyMap()',
    'repo.createBatch(s.id,bs.size+1,e.id,its)\n   cart.value=emptyMap();cartNotes.value=emptyMap()',
    "clear notes after batch",
)

s = replace_once(
    s,
    'val items=dao.batchItems(b.id).first().map{it.itemNameSnapshot to it.qty}',
    'val items=dao.batchItems(b.id).first().map{Triple(it.itemNameSnapshot,it.qty,it.note)}',
    "kitchen print note mapping",
)

vm.write_text(s)


# MainActivity.kt
main = Path("POS0210/app/src/main/java/vn/ecohome/pos0210/MainActivity.kt")
s = main.read_text()

s = replace_once(
    s,
    '    val cart by vm.cart.collectAsState()\n    var selectedCat',
    '    val cart by vm.cart.collectAsState()\n    var showCart by remember { mutableStateOf(false) }\n    var selectedCat',
    "show cart state",
)

s = replace_once(
    s,
    'OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("XEM GIỎ HÀNG") }',
    'OutlinedButton(onClick = { showCart = true }, modifier = Modifier.weight(1f)) { Text("XEM GIỎ HÀNG") }',
    "open cart note dialog",
)

old_order_end = '''        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showCart = true }, modifier = Modifier.weight(1f)) { Text("XEM GIỎ HÀNG") }
            Button(onClick = { vm.sendBatch() }, modifier = Modifier.weight(1f), enabled = cart.isNotEmpty()) { Text("GỬI LÀM HÀNG") }
        }
    }
}

@Composable
fun Sent'''
new_order_end = '''        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showCart = true }, modifier = Modifier.weight(1f)) { Text("XEM GIỎ HÀNG") }
            Button(onClick = { vm.sendBatch() }, modifier = Modifier.weight(1f), enabled = cart.isNotEmpty()) { Text("GỬI LÀM HÀNG") }
        }
    }
    if (showCart) OrderCartNotesDialog(vm) { showCart = false }
}

@Composable
fun Sent'''
s = replace_once(s, old_order_end, new_order_end, "render cart note dialog")

s = replace_once(
    s,
    'its.forEach { Text("${it.qty} × ${it.itemNameSnapshot}") }',
    '''its.forEach { item ->
                        Text("${item.qty} × ${item.itemNameSnapshot}")
                        if(item.note.isNotBlank()) Text("↳ ${item.note}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }''',
    "show note in sent detail",
)

main.write_text(s)


# BluetoothPrinter.kt / ReceiptRenderer
printer = Path("POS0210/app/src/main/java/vn/ecohome/pos0210/printing/BluetoothPrinter.kt")
s = printer.read_text()

s = replace_once(
    s,
    'fun kitchen(table:String,sequence:Int,serviceNo:Int,orderer:String,items:List<Pair<String,Int>>):Bitmap{',
    'fun kitchen(table:String,sequence:Int,serviceNo:Int,orderer:String,items:List<Triple<String,Int,String>>):Bitmap{',
    "kitchen renderer item type",
)

s = replace_once(
    s,
    'val h=300+items.size*55',
    'val h=300+items.sumOf{if(it.third.isBlank())55 else 86}',
    "kitchen dynamic height",
)

s = replace_once(
    s,
    '''        items.forEach{(name,qty)->
            c.drawText("$qty × $name",PAD,y,paint(24f,true));y+=42
        }''',
    '''        items.forEach{(name,qty,note)->
            c.drawText("$qty × $name",PAD,y,paint(24f,true));y+=30
            if(note.isNotBlank()){
                y=wrap(c,"GHI CHÚ: ${note.trim()}",PAD+10f,y,W-PAD*2-10f,paint(18f,true),21f)
                y+=8
            }else y+=12
        }''',
    "kitchen note rendering",
)

printer.write_text(s)


# Version bump
gradle = Path("POS0210/app/build.gradle.kts")
g = gradle.read_text()
g = g.replace('versionCode = 53', 'versionCode = 54', 1)
g = g.replace('versionName = "1.0.0-alpha44-hardening"', 'versionName = "1.0.0-alpha45-order-notes"', 1)
gradle.write_text(g)

print("Order note patch complete")
