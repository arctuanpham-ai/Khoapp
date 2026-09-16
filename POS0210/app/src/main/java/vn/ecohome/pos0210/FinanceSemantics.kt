package vn.ecohome.pos0210

/** Maps the original input-detail buckets to the newer financial reporting groups.
 * The detail bucket is secondary metadata; this helper only supplies the obvious
 * financial category when the user explicitly selects one of the three built-ins.
 */
fun expenseCategoryForLegacyDetail(categoryId: String, current: String): String = when (categoryId) {
    "pc_salary" -> ExpenseCategories.PAYROLL
    "pc_production" -> ExpenseCategories.INVENTORY_PURCHASE
    "pc_fixed" -> ExpenseCategories.FIXED_EXPENSE
    else -> current
}
