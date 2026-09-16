from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = root / "POS0210/app/src/main/java/vn/ecohome/pos0210/MainActivity.kt"
build = root / "POS0210/app/build.gradle.kts"
s = main.read_text()

replacements = [
(
'''listOf(ExpenseCategories.INVENTORY_PURCHASE,ExpenseCategories.PAYROLL,ExpenseCategories.ELECTRICITY,ExpenseCategories.WATER,ExpenseCategories.RENT,ExpenseCategories.MARKETING,ExpenseCategories.CONSUMABLES,ExpenseCategories.MAINTENANCE,ExpenseCategories.SERVICES,ExpenseCategories.BANK_FEES,ExpenseCategories.FIXED_EXPENSE,ExpenseCategories.VARIABLE_EXPENSE,ExpenseCategories.OTHER_EXPENSE).forEach{value->FilterChip(expenseCategory==value,{expenseCategory=value},{Text(ExpenseCategories.label(value))})}''',
'''listOf(ExpenseCategories.INVENTORY_PURCHASE,ExpenseCategories.PAYROLL,ExpenseCategories.ELECTRICITY,ExpenseCategories.WATER,ExpenseCategories.RENT,ExpenseCategories.MARKETING,ExpenseCategories.CONSUMABLES,ExpenseCategories.MAINTENANCE,ExpenseCategories.SERVICES,ExpenseCategories.BANK_FEES,ExpenseCategories.OTHER_EXPENSE).forEach{value->FilterChip(expenseCategory==value,{expenseCategory=value},{Text(ExpenseCategories.label(value))})}'''
),
(
'''    val purchaseTotal = filteredPurchases.sumOf { it.total }
    val categorizedCosts = filteredPurchases
        .groupBy { it.expenseCategory }
        .map { (expenseCategory, rows) ->
            Triple(expenseCategory, ExpenseCategories.label(expenseCategory), rows.sumOf { it.total })
        }
        .sortedByDescending { it.third }
    val categorizedCostTotal = categorizedCosts.sumOf { it.third }''',
'''    val cashOutPurchases = filteredPurchases.filter { financialReportBucket(it.expenseCategory) != FinancialReportBucket.CAPITAL_FLOW }
    val purchaseTotal = cashOutPurchases.sumOf { it.total }
    val operatingPurchases = cashOutPurchases.filter { financialReportBucket(it.expenseCategory) == FinancialReportBucket.OPERATING }
    val categorizedCosts = operatingPurchases
        .groupBy { canonicalFinancialReportCategory(it.expenseCategory) }
        .map { (expenseCategory, rows) ->
            Triple(expenseCategory, ExpenseCategories.label(expenseCategory), rows.sumOf { it.total })
        }
        .sortedByDescending { it.third }
    val categorizedCostTotal = categorizedCosts.sumOf { it.third }
    val investmentRows = listOf(
        Triple(FinancialReportBucket.ADDITIONAL_INVESTMENT, "Đầu tư bổ sung", cashOutPurchases.filter { financialReportBucket(it.expenseCategory) == FinancialReportBucket.ADDITIONAL_INVESTMENT }.sumOf { it.total }),
        Triple(FinancialReportBucket.INITIAL_ASSET, "Tài sản đầu tư ban đầu", cashOutPurchases.filter { financialReportBucket(it.expenseCategory) == FinancialReportBucket.INITIAL_ASSET }.sumOf { it.total }),
        Triple(FinancialReportBucket.INITIAL_SUNK, "Đầu tư ban đầu không thu hồi", cashOutPurchases.filter { financialReportBucket(it.expenseCategory) == FinancialReportBucket.INITIAL_SUNK }.sumOf { it.total })
    ).filter { it.third > 0L }'''
),
('''MetricCard("Tổng chi phí đầu vào", money(purchaseTotal))''','''MetricCard("Tổng tiền chi trong kỳ", money(purchaseTotal))'''),
('''MetricCard("Chênh lệch thu - chi đầu vào", money(revenue - purchaseTotal))''','''MetricCard("Chênh lệch thu - tổng tiền chi", money(revenue - purchaseTotal))'''),
('''"Chi phí theo nhóm tài chính"''','''"Chi phí vận hành theo nhóm"'''),
('''Text("Tổng chi phí đầu vào: ${money(purchaseTotal)}", fontSize = 22.sp, fontWeight = FontWeight.Bold)''','''Text("Tổng tiền chi trong kỳ: ${money(purchaseTotal)}", fontSize = 22.sp, fontWeight = FontWeight.Bold)'''),
('''"Theo phân mục"''','''"Chi phí vận hành theo nhóm"'''),
]

for old, new in replacements:
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one MainActivity match, got {count}: {old[:100]!r}")
    s = s.replace(old, new, 1)

# Add a dedicated investment block to OVERVIEW before popular items.
overview_marker = '''                    item {
                        Text("Món khách chọn nhiều", Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }'''
overview_insert = '''                    if (investmentRows.isNotEmpty()) {
                        item {
                            Text("Đầu tư trong kỳ", Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        items(investmentRows) { row ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.second, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    Text(money(row.third), fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
''' + overview_marker
if s.count(overview_marker) != 1:
    raise SystemExit("overview marker mismatch")
s = s.replace(overview_marker, overview_insert, 1)

# Add the same dedicated investment block in the PURCHASES tab before receipt cards.
purchases_marker = '''                        items(filteredPurchases) { p ->'''
purchases_insert = '''                        if (investmentRows.isNotEmpty()) {
                            item {
                                Text("Đầu tư trong kỳ", Modifier.padding(top = 12.dp, bottom = 4.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            }
                            items(investmentRows) { row ->
                                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(row.second, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                        Text(money(row.third))
                                    }
                                }
                            }
                        }
                        item {
                            Text("Phiếu nhập trong kỳ", Modifier.padding(top = 14.dp, bottom = 4.dp), fontWeight = FontWeight.Bold)
                        }
''' + purchases_marker
if s.count(purchases_marker) != 1:
    raise SystemExit("purchases marker mismatch")
s = s.replace(purchases_marker, purchases_insert, 1)

# Remove the old receipt heading nested inside the operating-cost conditional to avoid duplicate headings.
old_nested = '''                            item {
                                Text(
                                    "Phiếu nhập trong kỳ",
                                    Modifier.padding(top = 14.dp, bottom = 4.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
'''
if s.count(old_nested) != 1:
    raise SystemExit("old nested receipt heading mismatch")
s = s.replace(old_nested, "", 1)

# Visible version text in Manage.
s = s.replace('POS0210 v1.0.0-alpha52-candidate17 · versionCode 78', 'POS0210 v1.0.0-alpha52-candidate18 · versionCode 79')
main.write_text(s)

b = build.read_text()
for old, new in [
    ('versionCode = 78', 'versionCode = 79'),
    ('versionName = "1.0.0-alpha52-candidate17"', 'versionName = "1.0.0-alpha52-candidate18"'),
]:
    if b.count(old) != 1:
        raise SystemExit(f"build identity mismatch: {old}")
    b = b.replace(old, new, 1)
build.write_text(b)
