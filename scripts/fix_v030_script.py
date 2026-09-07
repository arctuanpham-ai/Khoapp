from pathlib import Path
p=Path('scripts/patch_v030.py')
s=p.read_text()
old="pat=r'    private fun pickDimPoint\\(x: Float, y: Float\\) \\{.*?\\n    \\}\\n\\n    private fun snapDimPoint'"
new="pat=r'    private fun pickDimPoint\\([^\\)]*\\) \\{.*?\\n    \\}\\n\\n    private fun snapDimPoint'"
if old not in s:
    raise SystemExit('v030 signature pattern anchor missing')
s=s.replace(old,new,1)
p.write_text(s)
print('v0.3.0 DIM signature matcher repaired')
