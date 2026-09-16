from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "POS0210/app/src/main/java/vn/ecohome/pos0210/FinanceDialogs.kt"
s = p.read_text()
marker = "@Composable fun AccountingCard("
if marker not in s:
    s += r'''

@Composable
fun AccountingCard(rows: List<Pair<String, Long?>>) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(14.dp)) {
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        label,
                        Modifier.weight(1f),
                        fontWeight = if (label.uppercase() == label) FontWeight.Black else FontWeight.Normal
                    )
                    Text(value?.let { money(it) } ?: "—", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
'''
p.write_text(s)
