from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = root / "POS0210/app/src/main/java/vn/ecohome/pos0210/MainActivity.kt"
build = root / "POS0210/app/build.gradle.kts"

s = main.read_text()

replacements = [
(
'''listOf(ExpenseCategories.INVENTORY_PURCHASE,ExpenseCategories.FIXED_EXPENSE,ExpenseCategories.VARIABLE_EXPENSE,ExpenseCategories.OTHER_EXPENSE).forEach{value->FilterChip(expenseCategory==value,{expenseCategory=value},{Text(ExpenseCategories.label(value))})}''',
'''listOf(ExpenseCategories.INVENTORY_PURCHASE,ExpenseCategories.PAYROLL,ExpenseCategories.ELECTRICITY,ExpenseCategories.WATER,ExpenseCategories.RENT,ExpenseCategories.MARKETING,ExpenseCategories.CONSUMABLES,ExpenseCategories.MAINTENANCE,ExpenseCategories.SERVICES,ExpenseCategories.BANK_FEES,ExpenseCategories.FIXED_EXPENSE,ExpenseCategories.VARIABLE_EXPENSE,ExpenseCategories.OTHER_EXPENSE).forEach{value->FilterChip(expenseCategory==value,{expenseCategory=value},{Text(ExpenseCategories.label(value))})}'''
),
(
'''selectedCategoryId = c.id
                                    unit = c.defaultUnit''',
'''selectedCategoryId = c.id
                                    unit = c.defaultUnit
                                    expenseCategory = expenseCategoryForLegacyDetail(c.id, expenseCategory)'''
),
(
'''label = { Text(if(FinancialTransactionTypes.usesPurchaseDocument(transactionType)) if (selectedCategory?.id == "pc_salary") "Nội dung / nhân sự" else "Mặt hàng / nội dung chi" else "Nội dung giao dịch") }''',
'''label = { Text(if(FinancialTransactionTypes.usesPurchaseDocument(transactionType)) if (selectedCategory?.id == "pc_salary") "Người nhận lương / nhân sự" else "Mặt hàng / nội dung chi" else "Nội dung giao dịch") }'''
),
(
'''val categorizedCosts = purchaseCosts
        .filter { it.purchaseId in filteredPurchaseIds }
        .groupBy { it.categoryId }
        .map { (categoryId, rows) ->
            val categoryName = purchaseCategories.firstOrNull { it.id == categoryId }?.name ?: "Phân mục khác"
            Triple(categoryId, categoryName, rows.sumOf { it.amount })
        }
        .sortedByDescending { it.third }''',
'''val categorizedCosts = filteredPurchases
        .groupBy { it.expenseCategory }
        .map { (expenseCategory, rows) ->
            Triple(expenseCategory, ExpenseCategories.label(expenseCategory), rows.sumOf { it.total })
        }
        .sortedByDescending { it.third }'''
),
(
'''"Chi phí đầu vào theo phân mục"''',
'''"Chi phí theo nhóm tài chính"'''
),
]

for old, new in replacements:
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one MainActivity match, got {count}: {old[:90]!r}")
    s = s.replace(old, new, 1)

main.write_text(s)

b = build.read_text()
for old, new in [
    ('versionCode = 77', 'versionCode = 78'),
    ('versionName = "1.0.0-alpha52-candidate16"', 'versionName = "1.0.0-alpha52-candidate17"'),
]:
    count = b.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one build.gradle match, got {count}: {old}")
    b = b.replace(old, new, 1)
build.write_text(b)
