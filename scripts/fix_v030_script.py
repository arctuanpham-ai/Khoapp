from pathlib import Path
p=Path('scripts/patch_v030.py')
s=p.read_text()
old="pat=r'    private fun pickDimPoint\\(x: Float, y: Float\\) \\{.*?\\n    \\}\\n\\n    private fun snapDimPoint'"
if old in s:
    s=s.replace(old,"pat=r'    private fun pickDimPoint\\([^\\)]*\\) \\{.*?\\n    \\}\\n\\n    private fun unproject'",1)
else:
    old2="pat=r'    private fun pickDimPoint\\([^\\)]*\\) \\{.*?\\n    \\}\\n\\n    private fun snapDimPoint'"
    if old2 not in s: raise SystemExit('v030 DIM pattern anchor missing')
    s=s.replace(old2,"pat=r'    private fun pickDimPoint\\([^\\)]*\\) \\{.*?\\n    \\}\\n\\n    private fun unproject'",1)
old_tail="    private fun snapDimPoint'''\ns,n=re.subn(pat,rep,s,count=1,flags=re.S)"
new_tail="    private fun unproject'''\ns,n=re.subn(pat,rep,s,count=1,flags=re.S)"
if old_tail not in s: raise SystemExit('v030 DIM replacement tail anchor missing')
s=s.replace(old_tail,new_tail,1)
p.write_text(s)
print('v0.3.0 DIM function boundary matcher repaired')
