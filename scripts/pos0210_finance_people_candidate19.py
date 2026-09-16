from pathlib import Path

ROOT = Path('POS0210')

def replace_once(path, old, new):
    p = ROOT / path
    s = p.read_text()
    if old not in s:
        raise SystemExit(f'pattern not found in {path}: {old[:120]!r}')
    if s.count(old) != 1:
        raise SystemExit(f'pattern not unique in {path}: count={s.count(old)}')
    p.write_text(s.replace(old, new, 1))

# Version candidate19
replace_once('app/build.gradle.kts',
'''        versionCode = 79\n        versionName = "1.0.0-alpha52-candidate18"''',
'''        versionCode = 80\n        versionName = "1.0.0-alpha52-candidate19"''')

# Pure finance semantics: payroll UI follows the financial group; 0%-share people are valid payers.
p = ROOT / 'app/src/main/java/vn/ecohome/pos0210/FinanceSemantics.kt'
s = p.read_text()
append = '''\n\nfun isPayrollExpense(expenseCategory: String): Boolean = expenseCategory == ExpenseCategories.PAYROLL\n\nfun validProfitPeopleShares(shares: List<Int>): Boolean =\n    shares.isNotEmpty() && shares.all { it in 0..10000 } && shares.sum() == 10000\n'''
if 'fun isPayrollExpense(' not in s:
    p.write_text(s.rstrip() + append)

# Payer rename/merge operations update historical text references without touching amounts/categories.
replace_once('app/src/main/java/vn/ecohome/pos0210/data/PosDao.kt',
'''@Query("UPDATE ProfitPartnerEntity SET active=0") suspend fun deactivateProfitPartners()\n}''',
'''@Query("UPDATE ProfitPartnerEntity SET active=0") suspend fun deactivateProfitPartners()\n@Query("UPDATE PurchaseEntity SET paidByName=:newName WHERE lower(trim(paidByName))=lower(trim(:oldName))") suspend fun renamePurchasePayerName(oldName:String,newName:String):Int\n@Query("UPDATE FinancialMovementEntity SET counterpartyName=:newName WHERE lower(trim(counterpartyName))=lower(trim(:oldName))") suspend fun renameReimbursementCounterparty(oldName:String,newName:String):Int\n}''')

# Canonical people config can contain 0%-share payers. Renaming a person follows history by exact normalized old name.
vm_path = ROOT / 'app/src/main/java/vn/ecohome/pos0210/PosViewModel.kt'
vm = vm_path.read_text()
old = ''' fun saveProfitPartners(rows:List<ProfitPartnerEntity>){\n  val e=currentEmployee.value?:return;if(e.role!="ADMIN")return\n  if(rows.isEmpty()||rows.any{it.name.isBlank()||it.shareBasisPoints !in 0..10000}||rows.sumOf{it.shareBasisPoints}!=10000)return\n  viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO){db.withTransaction{dao.deactivateProfitPartners();dao.saveProfitPartners(rows)};audit("ACCOUNTING","PARTNERS","SAVE","count=${rows.size},totalBp=10000");autoBackup()}\n }'''
new = ''' fun saveProfitPartners(rows:List<ProfitPartnerEntity>){\n  val e=currentEmployee.value?:return;if(e.role!="ADMIN")return\n  if(rows.any{it.name.isBlank()}||rows.map{it.name.trim().lowercase()}.distinct().size!=rows.size||!validProfitPeopleShares(rows.map{it.shareBasisPoints}))return\n  viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO){\n   val previous=profitPartners.value.associateBy{it.id}\n   db.withTransaction{\n    rows.forEach{row->\n     val oldName=previous[row.id]?.name?.trim().orEmpty();val newName=row.name.trim()\n     if(oldName.isNotBlank()&&!oldName.equals(newName,true)){dao.renamePurchasePayerName(oldName,newName);dao.renameReimbursementCounterparty(oldName,newName)}\n    }\n    dao.deactivateProfitPartners();dao.saveProfitPartners(rows.map{it.copy(name=it.name.trim())})\n   }\n   audit("ACCOUNTING","PARTNERS","SAVE","count=${rows.size},totalBp=10000");autoBackup()\n  }\n }\n fun mergePayerAlias(oldName:String,newName:String){\n  val e=currentEmployee.value?:return;if(e.role!="ADMIN"||oldName.isBlank()||newName.isBlank()||oldName.trim().equals(newName.trim(),true))return\n  viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO){\n   val purchasesChanged=dao.renamePurchasePayerName(oldName.trim(),newName.trim());val reimbursementsChanged=dao.renameReimbursementCounterparty(oldName.trim(),newName.trim())\n   audit("ACCOUNTING","PAYER_ALIAS","MERGE","${oldName.trim()}->${newName.trim()},purchases=$purchasesChanged,reimbursements=$reimbursementsChanged");autoBackup()\n  }\n }'''
if old not in vm:
    raise SystemExit('saveProfitPartners block not found')
vm_path.write_text(vm.replace(old,new,1))

main_path = ROOT / 'app/src/main/java/vn/ecohome/pos0210/MainActivity.kt'
main = main_path.read_text()

# Add people configuration screen.
old = '        "LOYALTY_CONFIG" -> LoyaltyConfig(vm)\n        "EMP" -> Employees(vm)'
new = '        "LOYALTY_CONFIG" -> LoyaltyConfig(vm)\n        "FINANCE_PEOPLE" -> FinancePeopleManager(vm)\n        "EMP" -> Employees(vm)'
if old not in main: raise SystemExit('App screen insertion not found')
main = main.replace(old,new,1)

# Input form must not inherit the first legacy detail (salary); legacy detail becomes optional.
old = '''    var unit by remember { mutableStateOf("kg") }'''
new = '''    var unit by remember { mutableStateOf("lần") }'''
if old not in main: raise SystemExit('unit default not found')
main = main.replace(old,new,1)
old = '''    var selectedCategoryId by remember(purchaseCategories) {\n        mutableStateOf(purchaseCategories.firstOrNull()?.id ?: "pc_production")\n    }'''
new = '''    var selectedCategoryId by remember { mutableStateOf("") }'''
if old not in main: raise SystemExit('selectedCategoryId init not found')
main = main.replace(old,new,1)

old = '''                    ) {\n                        purchaseCategories.forEach { c ->'''
new = '''                    ) {\n                        FilterChip(\n                            selected = selectedCategoryId.isBlank(),\n                            onClick = { selectedCategoryId = "" },\n                            label = { Text("Không phân mục") }\n                        )\n                        purchaseCategories.forEach { c ->'''
if old not in main: raise SystemExit('detail category row not found')
main = main.replace(old,new,1)

# Canonical payer list + manual fallback.
old = '''                if(FinancialTransactionTypes.usesPurchaseDocument(transactionType)) OutlinedTextField(\n                    paidByName,\n                    { paidByName = it.take(60) },\n                    modifier = Modifier.fillMaxWidth(),\n                    label = { Text("Người chi / ứng tiền (không bắt buộc)") },\n                    supportingText = { Text("Người thực tế bỏ tiền, độc lập với người nhập và cổ đông") }\n                )'''
new = '''                if(FinancialTransactionTypes.usesPurchaseDocument(transactionType)) {\n                    Text("Người chi / ứng tiền", Modifier.padding(top=8.dp), fontWeight=FontWeight.Bold)\n                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(6.dp)) {\n                        FilterChip(paidByName.isBlank(), { paidByName = "" }, { Text("Chưa xác định") })\n                        profitPartners.forEach { person ->\n                            FilterChip(paidByName.equals(person.name,true), { paidByName = person.name }, { Text(person.name) })\n                        }\n                    }\n                    OutlinedTextField(\n                        paidByName,\n                        { paidByName = it.take(60) },\n                        modifier = Modifier.fillMaxWidth(),\n                        label = { Text("Tên người chi khác / chỉnh tay") },\n                        supportingText = { Text("Người thực tế bỏ tiền. Có thể cấu hình tên chuẩn và tỷ lệ chia trong Báo cáo tháng.") }\n                    )\n                }'''
if old not in main: raise SystemExit('payer field not found')
main = main.replace(old,new,1)

# Salary-specific labels follow the financial category, never the legacy row position.
main = main.replace('if (selectedCategory?.id == "pc_salary") "Người nhận lương / nhân sự" else "Mặt hàng / nội dung chi"', 'if (isPayrollExpense(expenseCategory)) "Người nhận lương / nhân sự" else "Mặt hàng / nội dung chi"')
main = main.replace('if (selectedCategory?.id == "pc_salary") "Số ngày công / SL" else "Khối lượng / SL"', 'if (isPayrollExpense(expenseCategory)) "Số ngày công / SL" else "Khối lượng / SL"')
main = main.replace('if (selectedCategory?.id == "pc_salary") "Đơn giá / ngày công" else "Đơn giá"', 'if (isPayrollExpense(expenseCategory)) "Đơn giá / ngày công" else "Đơn giá"')
main = main.replace('Text(selectedCategory?.name ?: "Chưa chọn phân mục", fontWeight = FontWeight.Bold)', 'Text(selectedCategory?.name ?: ExpenseCategories.label(expenseCategory), fontWeight = FontWeight.Bold)')

old = '''                        (!FinancialTransactionTypes.usesPurchaseDocument(transactionType)||(selectedCategoryId.isNotBlank()&&(qty?:0.0)>0)) &&'''
new = '''                        (!FinancialTransactionTypes.usesPurchaseDocument(transactionType)||(qty?:0.0)>0) &&\n                        (transactionType!=FinancialTransactionTypes.OPERATING_EXPENSE||expenseCategory!=ExpenseCategories.UNCLASSIFIED) &&'''
if old not in main: raise SystemExit('purchase enable condition not found')
main = main.replace(old,new,1)

# People manager UI: same master list is used for sharing and payer selection; 0% means payer-only.
marker = '@Composable\nfun MonthlyProfitReport(vm:PosViewModel){'
if marker not in main: raise SystemExit('monthly report marker not found')
people_ui = r'''private data class FinancePersonDraft(val id:String,val name:String,val shareText:String)

@Composable
fun FinancePeopleManager(vm:PosViewModel){
    val current by vm.currentEmployee.collectAsState()
    val partners by vm.profitPartners.collectAsState()
    if(current?.role!="ADMIN"){
        Column{Header("Người & tỷ lệ chia"){vm.screen.value="REPORT"};Text("Chỉ Admin được cấu hình.",Modifier.padding(20.dp),fontWeight=FontWeight.Bold)}
        return
    }
    var drafts by remember(partners){mutableStateOf(partners.map{FinancePersonDraft(it.id,it.name,(it.shareBasisPoints/100).toString())})}
    var alias by remember{mutableStateOf("")}
    var mergeTargetId by remember(partners){mutableStateOf(partners.firstOrNull()?.id)}
    var message by remember{mutableStateOf("")}
    val shares=drafts.map{((it.shareText.toIntOrNull()?:0).coerceIn(0,100))*100}
    val totalPercent=shares.sum()/100
    val valid=drafts.isNotEmpty()&&drafts.all{it.name.isNotBlank()}&&drafts.map{it.name.trim().lowercase()}.distinct().size==drafts.size&&validProfitPeopleShares(shares)
    Column{
        Header("Người & tỷ lệ chia"){vm.screen.value="REPORT"}
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)){
            item{
                Text("DANH SÁCH NGƯỜI",fontWeight=FontWeight.Black,fontSize=20.sp)
                Text("Một người có thể vừa được chia tiền vừa ứng tiền cho quán. Đặt 0% nếu người đó chỉ ứng tiền. Tổng tỷ lệ người chia phải bằng 100%.",fontSize=12.sp)
                Text("Tổng tỷ lệ hiện tại: $totalPercent%",Modifier.padding(vertical=8.dp),fontWeight=FontWeight.Bold,color=if(totalPercent==100)Color(0xFF41633A) else Color(0xFF9A4B3D))
            }
            itemsIndexed(drafts,key={_,d->d.id}){index,d->
                Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){
                    Column(Modifier.padding(12.dp)){
                        OutlinedTextField(d.name,{v->drafts=drafts.toMutableList().also{it[index]=d.copy(name=v.take(60))}},Modifier.fillMaxWidth(),label={Text("Tên người")},singleLine=true)
                        OutlinedTextField(d.shareText,{v->drafts=drafts.toMutableList().also{it[index]=d.copy(shareText=v.filter(Char::isDigit).take(3))}},Modifier.fillMaxWidth(),label={Text("% chia · 0 = chỉ ứng tiền")},singleLine=true)
                        TextButton(onClick={drafts=drafts.filterIndexed{i,_->i!=index}}){Text("BỎ KHỎI DANH SÁCH")}
                    }
                }
            }
            item{
                OutlinedButton(onClick={drafts=drafts+FinancePersonDraft(UUID.randomUUID().toString(),"","0")},Modifier.fillMaxWidth().padding(top=6.dp)){Text("＋ THÊM NGƯỜI")}
                Button(onClick={
                    vm.saveProfitPartners(drafts.mapIndexed{i,d->ProfitPartnerEntity(d.id,d.name.trim(),((d.shareText.toIntOrNull()?:0).coerceIn(0,100))*100,i,true)})
                    message="Đã lưu danh sách người và tỷ lệ chia."
                },enabled=valid,modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("LƯU CẤU HÌNH")}
                if(!valid)Text("Cần tên không trùng và tổng tỷ lệ chia đúng 100%.",Modifier.padding(top=6.dp),fontSize=12.sp,color=Color(0xFF9A4B3D))
                if(message.isNotBlank())Text(message,Modifier.padding(top=6.dp),fontWeight=FontWeight.Bold)
                HorizontalDivider(Modifier.padding(vertical=14.dp))
                Text("GỘP TÊN NGƯỜI CHI CŨ",fontWeight=FontWeight.Black)
                Text("Dùng khi lịch sử từng nhập tên khác. Chỉ đổi tên tham chiếu người chi/hoàn ứng; không đổi số tiền hay phân loại phiếu.",fontSize=11.sp)
                OutlinedTextField(alias,{alias=it.take(60)},Modifier.fillMaxWidth(),label={Text("Tên cũ cần gộp")})
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    partners.forEach{p->FilterChip(mergeTargetId==p.id,{mergeTargetId=p.id},{Text(p.name)})}
                }
                OutlinedButton(onClick={
                    val target=partners.firstOrNull{it.id==mergeTargetId}
                    if(target!=null&&alias.isNotBlank()){vm.mergePayerAlias(alias,target.name);message="Đã yêu cầu gộp ${alias.trim()} → ${target.name}";alias=""}
                },enabled=alias.isNotBlank()&&mergeTargetId!=null,modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("GỘP VÀO NGƯỜI ĐÃ CHỌN")}
            }
        }
    }
}

'''
main = main.replace(marker, people_ui + marker, 1)

# Monthly report always exposes the configuration entry point.
old = '''      item{Text("CHIA TIỀN",Modifier.padding(top=10.dp),fontWeight=FontWeight.Black,fontSize=18.sp);if(result.netCashResult<=0)Text("Tháng này không có số dư dương để chia.",Modifier.padding(8.dp),fontWeight=FontWeight.Bold) else if(shares.isEmpty())Text("Cần cấu hình tỷ lệ người chia đủ 100%.",Modifier.padding(8.dp),color=Color(0xFF9A4B3D),fontWeight=FontWeight.Bold)}'''
new = '''      item{Text("CHIA TIỀN",Modifier.padding(top=10.dp),fontWeight=FontWeight.Black,fontSize=18.sp);OutlinedButton({vm.screen.value="FINANCE_PEOPLE"},Modifier.fillMaxWidth().padding(vertical=6.dp)){Text("CẤU HÌNH NGƯỜI & TỶ LỆ CHIA")};if(result.netCashResult<=0)Text("Tháng này không có số dư dương để chia.",Modifier.padding(8.dp),fontWeight=FontWeight.Bold) else if(shares.isEmpty())Text("Cần cấu hình tỷ lệ người chia đủ 100%.",Modifier.padding(8.dp),color=Color(0xFF9A4B3D),fontWeight=FontWeight.Bold)}'''
if old not in main: raise SystemExit('monthly share block not found')
main = main.replace(old,new,1)

main_path.write_text(main)

print('candidate19 finance people/payroll patch applied')
