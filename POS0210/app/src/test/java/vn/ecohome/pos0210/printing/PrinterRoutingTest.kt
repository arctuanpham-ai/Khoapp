package vn.ecohome.pos0210.printing

import org.junit.Assert.assertEquals
import org.junit.Test

class PrinterRoutingTest {
    @Test fun kitchenJobUsesItsDedicatedPrinterInsteadOfReceiptPrinter() {
        val routing = PrinterRouting.fromSettings(
            legacyMac = "LEGACY",
            kitchenMac = "KITCHEN",
            receiptMac = "RECEIPT"
        )

        assertEquals("KITCHEN", routing.addressFor(PrintJobType.KITCHEN))
        assertEquals("KITCHEN", routing.addressFor(PrintJobType.CANCEL))
    }

    @Test fun receiptAndTestJobsUseTheirDedicatedReceiptPrinter() {
        val routing = PrinterRouting.fromSettings(
            legacyMac = "LEGACY",
            kitchenMac = "KITCHEN",
            receiptMac = "RECEIPT"
        )

        assertEquals("RECEIPT", routing.addressFor(PrintJobType.PAYMENT))
        assertEquals("RECEIPT", routing.addressFor(PrintJobType.TEST))
    }

    @Test fun legacySinglePrinterRemainsUsableUntilEachRoleIsAssigned() {
        val routing = PrinterRouting.fromSettings(legacyMac = "LEGACY")

        assertEquals("LEGACY", routing.addressFor(PrintJobType.KITCHEN))
        assertEquals("LEGACY", routing.addressFor(PrintJobType.PAYMENT))
    }
}
