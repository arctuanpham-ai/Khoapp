package vn.ecohome.pos0210.printing

/**
 * Resolves the physical Bluetooth destination for each printed document.
 * Legacy installs keep using their single selected printer until a role is assigned.
 */
class PrinterRouting private constructor(
    private val kitchenMacValue: String,
    private val receiptMacValue: String
) {
    fun kitchenMac(): String = kitchenMacValue

    fun receiptMac(): String = receiptMacValue

    fun addressFor(jobType: PrintJobType): String = when (jobType) {
        PrintJobType.KITCHEN, PrintJobType.CANCEL -> kitchenMacValue
        PrintJobType.PAYMENT, PrintJobType.TEST -> receiptMacValue
    }

    companion object {
        fun fromSettings(
            legacyMac: String,
            kitchenMac: String = "",
            receiptMac: String = ""
        ): PrinterRouting = PrinterRouting(
            kitchenMac.trim().ifBlank { legacyMac.trim() },
            receiptMac.trim().ifBlank { legacyMac.trim() }
        )
    }
}
