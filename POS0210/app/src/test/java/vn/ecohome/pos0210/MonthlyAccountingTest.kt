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
}
