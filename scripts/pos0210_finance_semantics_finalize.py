from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = root / "POS0210/app/src/main/java/vn/ecohome/pos0210/MainActivity.kt"
s = main.read_text()
old = 'POS0210 v1.0.0-alpha52-candidate16 · versionCode 77'
new = 'POS0210 v1.0.0-alpha52-candidate17 · versionCode 78'
count = s.count(old)
if count not in (0, 1):
    raise SystemExit(f"unexpected candidate label count: {count}")
if count == 1:
    s = s.replace(old, new, 1)
main.write_text(s)
