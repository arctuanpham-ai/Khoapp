from pathlib import Path
p=Path('scripts/patch_v023.py')
s=p.read_text()
s=s.replace('private fun (?:updateMagneticDimSnap|showZoomWindowRect|showMagnifier)', 'private fun (updateMagneticDimSnap|showZoomWindowRect|showMagnifier)')
p.write_text(s)
print('v0.2.3 patch script capture group fixed')
