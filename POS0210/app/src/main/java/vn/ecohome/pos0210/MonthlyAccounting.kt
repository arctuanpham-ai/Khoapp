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
    const val OTHER_EXPENSE="OTHER_EXPENSE"
    val all=listOf(UNCLASSIFIED,INVENTORY_PURCHASE,FIXED_EXPENSE,VARIABLE_EXPENSE,CAPITAL_ASSET,SETUP_COST,OWNER_CONTRIBUTION,OWNER_WITHDRAWAL,OTHER_EXPENSE)
    fun label(value:String)=when(value){
        INVENTORY_PURCHASE->"Nguyên liệu / hàng hóa";FIXED_EXPENSE->"Chi phí cố định";VARIABLE_EXPENSE->"Chi phí biến đổi"
        CAPITAL_ASSET->"Đầu tư tài sản";SETUP_COST->"Chi phí setup";OWNER_CONTRIBUTION->"Góp vốn"
        OWNER_WITHDRAWAL->"Rút vốn / Rút tiền chủ";OTHER_EXPENSE->"Chi phí khác";else->"Chưa phân loại"
    }
}

data class ProfitShareInput(val id:String,val name:String,val shareBasisPoints:Int)
data class PartnerProfit(val id:String,val name:String,val shareBasisPoints:Int,val amount:Long)
data class MonthlyAccountingInput(
    val grossRevenue:Long,val discounts:Long=0,val refunds:Long=0,val revenueAdjustments:Long=0,val cogs:Long?,
    val fixedExpense:Long=0,val variableExpense:Long=0,val otherExpense:Long=0,val setupCost:Long=0,
    val inventoryPurchases:Long=0,val capitalAssets:Long=0,val ownerContribution:Long=0,val ownerWithdrawal:Long=0,
    val unclassified:Long=0,val openingCash:Long=0,val revenueReceived:Long=0,val reserveBasisPoints:Int=1000,
    val partners:List<ProfitShareInput> = emptyList()
)
data class MonthlyAccountingResult(
    val netRevenue:Long,val grossProfit:Long?,val operatingProfit:Long?,val reserve:Long?,val distributableProfit:Long?,
    val closingCash:Long,val partnerProfits:List<PartnerProfit>,val shareConfigurationValid:Boolean
)

fun calculateMonthlyAccounting(i:MonthlyAccountingInput):MonthlyAccountingResult {
    require(i.reserveBasisPoints in 0..10_000)
    require(i.partners.all{it.shareBasisPoints in 0..10_000})
    val validShares=i.partners.isNotEmpty()&&i.partners.sumOf{it.shareBasisPoints}==10_000
    val net=i.grossRevenue-i.discounts-i.refunds+i.revenueAdjustments
    val gross=i.cogs?.let{net-it}
    val operating=gross?.let{it-i.fixedExpense-i.variableExpense-i.otherExpense}
    val reserve=operating?.takeIf{it>0}?.let{it*i.reserveBasisPoints/10_000L}?:operating?.let{0L}
    val distributable=operating?.let{it-(reserve?:0L)}
    val shares=if(validShares&&distributable!=null) i.partners.map{PartnerProfit(it.id,it.name,it.shareBasisPoints,distributable*it.shareBasisPoints/10_000L)} else emptyList()
    val cashExpenses=i.fixedExpense+i.variableExpense+i.otherExpense+i.setupCost+i.unclassified
    val closing=i.openingCash+i.revenueReceived+i.ownerContribution-cashExpenses-i.inventoryPurchases-i.capitalAssets-i.ownerWithdrawal
    return MonthlyAccountingResult(net,gross,operating,reserve,distributable,closing,shares,validShares)
}
