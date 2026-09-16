package vn.ecohome.pos0210

import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceSemanticsTest {
    @Test fun salaryDetailMapsToPayroll() {
        assertEquals(ExpenseCategories.PAYROLL, expenseCategoryForLegacyDetail("pc_salary", ExpenseCategories.OTHER_EXPENSE))
    }

    @Test fun productionDetailMapsToInventoryPurchase() {
        assertEquals(ExpenseCategories.INVENTORY_PURCHASE, expenseCategoryForLegacyDetail("pc_production", ExpenseCategories.OTHER_EXPENSE))
    }

    @Test fun fixedDetailMapsToFixedExpense() {
        assertEquals(ExpenseCategories.FIXED_EXPENSE, expenseCategoryForLegacyDetail("pc_fixed", ExpenseCategories.OTHER_EXPENSE))
    }

    @Test fun customDetailDoesNotOverwriteChosenFinancialGroup() {
        assertEquals(ExpenseCategories.ELECTRICITY, expenseCategoryForLegacyDetail("custom", ExpenseCategories.ELECTRICITY))
    }
}
