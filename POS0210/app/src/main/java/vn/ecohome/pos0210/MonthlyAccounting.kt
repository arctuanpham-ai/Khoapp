package vn.ecohome.pos0210

object ExpenseCategories {
    const val UNCLASSIFIED="UNCLASSIFIED"
    const val INVENTORY_PURCHASE="INVENTORY_PURCHASE"
    const val FIXED_EXPENSE="FIXED_EXPENSE"
    const val VARIABLE_EXPENSE="VARIABLE_EXPENSE"
    const val CAPITAL_ASSET="CAPITAL_ASSET"
    const val SETUP_COST="SETUP_COST"
    const val OWNER_CONTRIBUTION="OWNER_CONTRIBUTION"
    const val OWNER_WITHDRAWAL="OWNER_WITHDRAWAL"
    const val PROFIT_WITHDRAWAL="PROFIT_WITHDRAWAL"
    const val WORKING_CAPITAL="WORKING_CAPITAL"
    const val INITIAL_INVESTMENT_SUNK="INITIAL_INVESTMENT_SUNK"
    const val OTHER_EXPENSE="OTHER_EXPENSE"
    val all=listOf(UNCLASSIFIED,INVENTORY_PURCHASE,FIXED_EXPENSE,VARIABLE_EXPENSE,OTHER_EXPENSE,SETUP_COST,INITIAL_INVESTMENT_SUNK,CAPITAL_ASSET,WORKING_CAPITAL,OWNER_CONTRIBUTION,OWNER_WITHDRAWAL,PROFIT_WITHDRAWAL)
    fun label(value:String)=when(value){
        INVENTORY_PURCHASE->"Nguyên liệu / hàng hóa";FIXED_EXPENSE->"Chi phí cố định";VARIABLE_EXPENSE->"Chi phí biến đổi"
        CAPITAL_ASSET->"Tài sản có thể thu hồi";SETUP_COST,INITIAL_INVESTMENT_SUNK->"Đầu tư không thu hồi";OWNER_CONTRIBUTION->"Góp vốn đầu tư"
        WORKING_CAPITAL->"Vốn lưu động";OWNER_WITHDRAWAL->"Rút vốn";PROFIT_WITHDRAWAL->"Rút lợi nhuận";OTHER_EXPENSE->"Chi phí khác";else->"Chưa phân loại"
    }
}

data class ProfitShareInput(val id:String,val name:String,val shareBasisPoints:Int)
data class PartnerProfit(val id:String,val name:String,val shareBasisPoints:Int,val amount:Long)
data class MonthlyAccountingInput(
    val grossRevenue:Long,val discounts:Long=0,val refunds:Long=0,val revenueAdjustments:Long=0,val cogs:Long?,
    val fixedExpense:Long=0,val variableExpense:Long=0,val otherExpense:Long=0,val setupCost:Long=0,
    val inventoryPurchases:Long=0,val capitalAssets:Long=0,val ownerContribution:Long=0,val ownerWithdrawal:Long=0,
    val unclassified:Long=0,val openingCash:Long=0,val revenueReceived:Long=0,val reserveBasisPoints:Int=1000,
    val partners:List<ProfitShareInput> = emptyList(),val depreciationExpense:Long=0,val otherCashIn:Long=0,
    val workingCapitalContribution:Long=0,val profitWithdrawal:Long=0,val investmentCashOut:Long=0
)
data class MonthlyAccountingResult(
    val netRevenue:Long,val grossProfit:Long?,val operatingProfit:Long?,val reserve:Long?,val distributableProfit:Long?,
    val closingCash:Long,val partnerProfits:List<PartnerProfit>,val shareConfigurationValid:Boolean,
    val retainedProfit:Long?=reserve,val profitWithdrawn:Long=0
)

fun calculateMonthlyAccounting(i:MonthlyAccountingInput):MonthlyAccountingResult {
    require(i.reserveBasisPoints in 0..10_000)
    require(i.partners.all{it.shareBasisPoints in 0..10_000})
    val validShares=i.partners.isNotEmpty()&&i.partners.sumOf{it.shareBasisPoints}==10_000
    val net=i.grossRevenue-i.discounts-i.refunds+i.revenueAdjustments
    val gross=i.cogs?.let{net-it}
    val operating=gross?.let{it-i.fixedExpense-i.variableExpense-i.otherExpense-i.depreciationExpense}
    val reserve=operating?.takeIf{it>0}?.let{it*i.reserveBasisPoints/10_000L}?:operating?.let{0L}
    val distributable=operating?.let{it-(reserve?:0L)}
    var allocated=0L
    val shares=if(validShares&&distributable!=null) i.partners.mapIndexed{index,p->
        val amount=if(index==i.partners.lastIndex) distributable-allocated else distributable*p.shareBasisPoints/10_000L
        allocated+=amount;PartnerProfit(p.id,p.name,p.shareBasisPoints,amount)
    } else emptyList()
    val cashExpenses=i.fixedExpense+i.variableExpense+i.otherExpense+i.setupCost+i.unclassified
    val investmentOut=if(i.investmentCashOut>0)i.investmentCashOut else i.capitalAssets
    val closing=i.openingCash+i.revenueReceived+i.otherCashIn+i.ownerContribution+i.workingCapitalContribution-cashExpenses-i.inventoryPurchases-investmentOut-i.ownerWithdrawal-i.profitWithdrawal
    return MonthlyAccountingResult(net,gross,operating,reserve,distributable,closing,shares,validShares,reserve,i.profitWithdrawal)
}

data class AssetValue(val monthlyDepreciation:Long,val accumulatedDepreciation:Long,val bookValue:Long)
fun calculateAssetValue(totalCost:Long,residualValue:Long,usefulLifeMonths:Int,monthsUsed:Int):AssetValue{
    require(totalCost>=0&&residualValue in 0..totalCost&&usefulLifeMonths>0)
    val depreciable=totalCost-residualValue
    val monthly=depreciable/usefulLifeMonths
    val accumulated=(depreciable*monthsUsed.coerceIn(0,usefulLifeMonths)/usefulLifeMonths).coerceAtMost(depreciable)
    return AssetValue(monthly,accumulated,(totalCost-accumulated).coerceAtLeast(residualValue))
}

data class PaybackResult(val remainingToRecover:Long,val paybackBasisPoints:Int,val estimatedMonthsRemaining:Double?)
fun calculatePayback(initialInvestment:Long,recoveredCapital:Long,recentMonthlyProfits:List<Long>):PaybackResult{
    require(initialInvestment>=0&&recoveredCapital>=0)
    val remaining=(initialInvestment-recoveredCapital).coerceAtLeast(0)
    val percent=if(initialInvestment==0L)0 else ((recoveredCapital.coerceAtMost(initialInvestment)*10_000)/initialInvestment).toInt()
    val average=recentMonthlyProfits.takeLast(3).takeIf{it.isNotEmpty()}?.average()?:0.0
    return PaybackResult(remaining,percent,if(average>0)remaining/average else null)
}
