package vn.ecohome.pos0210.printing

fun main() {
    val routing = PrinterRouting.fromSettings(
        legacyMac = "LEGACY",
        kitchenMac = "KITCHEN",
        receiptMac = "RECEIPT"
    )
    assertEquals("KITCHEN", routing.kitchenMac(), "kitchen ticket")
    assertEquals("RECEIPT", routing.receiptMac(), "payment bill")

    val legacyOnly = PrinterRouting.fromSettings(legacyMac = "LEGACY")
    assertEquals("LEGACY", legacyOnly.kitchenMac(), "legacy kitchen fallback")
    assertEquals("LEGACY", legacyOnly.receiptMac(), "legacy receipt fallback")
}

private fun assertEquals(expected: String, actual: String, label: String) {
    check(expected == actual) { "$label: expected $expected, got $actual" }
}
