package vn.ecohome.pos0210

import org.junit.Assert.*
import org.junit.Test

class MonthlyAccountingTest {
 @Test fun acceptanceCaseSeparatesProfitFromCashFlow(){
  val r=calculateMonthlyAccounting(MonthlyAccountingInput(
   grossRevenue=100_000_000,cogs=30_000_000,fixedExpense=10_000_000,variableExpense=5_000_000,otherExpense=2_000_000,
   capitalAssets=20_000_000,ownerContribution=15_000_000,ownerWithdrawal=5_000_000,revenueReceived=100_000_000,
   reserveBasisPoints=1000,partners=listOf(ProfitShareInput("a","Partner A",5000),ProfitShareInput("b","Partner B",5000))))
  assertEquals(70_000_000L,r.grossProfit);assertEquals(53_000_000L,r.operatingProfit)
  assertEquals(5_300_000L,r.reserve);assertEquals(47_700_000L,r.distributableProfit)
  assertEquals(listOf(23_850_000L,23_850_000L),r.partnerProfits.map{it.amount})
  assertEquals(73_000_000L,r.closingCash)
 }
 @Test fun purchasesAreNotAssumedToBeCogs(){
  val r=calculateMonthlyAccounting(MonthlyAccountingInput(grossRevenue=10_000,cogs=null,inventoryPurchases=7_000,revenueReceived=10_000))
  assertNull(r.grossProfit);assertNull(r.operatingProfit);assertEquals(3_000L,r.closingCash)
 }
 @Test fun sharesMustTotalExactlyOneHundredPercent(){
  val r=calculateMonthlyAccounting(MonthlyAccountingInput(grossRevenue=100,cogs=0,partners=listOf(ProfitShareInput("a","A",4000))))
  assertFalse(r.shareConfigurationValid);assertTrue(r.partnerProfits.isEmpty())
 }
 @Test fun capitalAndOwnerMovementsNeverChangeOperatingProfit(){
  val base=MonthlyAccountingInput(grossRevenue=1000,cogs=300,fixedExpense=100)
  val moved=base.copy(capitalAssets=9999,ownerContribution=8888,ownerWithdrawal=7777)
  assertEquals(calculateMonthlyAccounting(base).operatingProfit,calculateMonthlyAccounting(moved).operatingProfit)
 }
 @Test fun roundingRemainderIsAssignedWithoutLosingVnd(){
  val r=calculateMonthlyAccounting(MonthlyAccountingInput(grossRevenue=101,cogs=0,reserveBasisPoints=0,partners=listOf(ProfitShareInput("a","A",3333),ProfitShareInput("b","B",3333),ProfitShareInput("c","C",3334))))
  assertEquals(101L,r.partnerProfits.sumOf{it.amount})
 }
 @Test fun straightLineDepreciationDoesNotReduceCash(){
  val asset=calculateAssetValue(30_000_000,0,60,12)
  assertEquals(500_000L,asset.monthlyDepreciation);assertEquals(6_000_000L,asset.accumulatedDepreciation);assertEquals(24_000_000L,asset.bookValue)
  val r=calculateMonthlyAccounting(MonthlyAccountingInput(grossRevenue=20_000_000,cogs=0,depreciationExpense=500_000,revenueReceived=20_000_000))
  assertEquals(19_500_000L,r.operatingProfit);assertEquals(20_000_000L,r.closingCash)
 }
 @Test fun profitWithdrawalReducesCashButNeverProfit(){
  val r=calculateMonthlyAccounting(MonthlyAccountingInput(grossRevenue=20_000_000,cogs=0,reserveBasisPoints=1000,revenueReceived=20_000_000,profitWithdrawal=4_000_000,partners=listOf(ProfitShareInput("a","A",5000),ProfitShareInput("b","B",5000))))
  assertEquals(20_000_000L,r.operatingProfit);assertEquals(18_000_000L,r.distributableProfit);assertEquals(9_000_000L,r.partnerProfits.first().amount);assertEquals(16_000_000L,r.closingCash)
 }
 @Test fun capitalContributionChangesCashOnly(){
  val r=calculateMonthlyAccounting(MonthlyAccountingInput(grossRevenue=0,cogs=0,ownerContribution=10_000_000))
  assertEquals(0L,r.operatingProfit);assertEquals(10_000_000L,r.closingCash)
 }
 @Test fun assetPurchaseAndSunkSetupAreInvestmentCashOutNotOperatingExpense(){
  val r=calculateMonthlyAccounting(MonthlyAccountingInput(grossRevenue=0,cogs=0,openingCash=40_000_000,capitalAssets=20_000_000,setupCost=15_000_000,investmentCashOut=35_000_000))
  assertEquals(0L,r.operatingProfit);assertEquals(5_000_000L,r.closingCash)
 }
 @Test fun paybackUsesRecentProfitAndNeverProducesNegativeEstimate(){
  val p=calculatePayback(100_000_000,31_000_000,listOf(8_000_000,10_000_000,12_000_000))
  assertEquals(69_000_000L,p.remainingToRecover);assertEquals(3100,p.paybackBasisPoints);assertEquals(6.9,p.estimatedMonthsRemaining!!,0.001)
  assertNull(calculatePayback(100,0,listOf(-10L,0L)).estimatedMonthsRemaining)
 }
 @Test fun legacyTransactionClassificationRemainsReadable(){
  assertEquals(FinancialTransactionTypes.OPERATING_EXPENSE,FinancialTransactionTypes.fromLegacy(ExpenseCategories.FIXED_EXPENSE))
  assertEquals(FinancialTransactionTypes.ASSET_PURCHASE,FinancialTransactionTypes.fromLegacy(ExpenseCategories.CAPITAL_ASSET))
  assertEquals(FinancialTransactionTypes.CAPITAL_INJECTION,FinancialTransactionTypes.fromLegacy(ExpenseCategories.OWNER_CONTRIBUTION))
  assertEquals(FinancialTransactionTypes.PROFIT_WITHDRAWAL,FinancialTransactionTypes.fromLegacy(ExpenseCategories.PROFIT_WITHDRAWAL))
 }
}
