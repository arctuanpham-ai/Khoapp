package vn.ecohome.pos0210.printing

enum class PaperSize(val settingValue:String){ MM58("58"),MM80("80") }

data class PrinterProfile(
    val paperSize:PaperSize,
    val paperWidthMm:Int,
    val printableWidthDots:Int,
    val charsPerLine:Int,
    val supportsRaster:Boolean=true,
    val supportsQrNative:Boolean=false,
    val rasterStripeHeight:Int=96
){
    val label:String get()="${paperWidthMm}mm"
    companion object{
        val MM58=PrinterProfile(PaperSize.MM58,58,384,32)
        val MM80=PrinterProfile(PaperSize.MM80,80,576,48)
        fun fromSetting(value:String?)=if(value==PaperSize.MM80.settingValue) MM80 else MM58
    }
}

enum class PrintJobType{ KITCHEN,PAYMENT,TEST,CANCEL }
