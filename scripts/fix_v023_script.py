from pathlib import Path
p=Path('scripts/patch_v023.py')
s=p.read_text()
s=s.replace('private fun (?:updateMagneticDimSnap|showZoomWindowRect|showMagnifier)', 'private fun (updateMagneticDimSnap|showZoomWindowRect|showMagnifier)')
old="if n!=1: raise SystemExit('axis DIM branch replacement failed')"
lines=[
"if n!=1:",
"    old_d='val d = distance(dimPoint1!!, dimPoint2!!) * (sourceMaxExtentMeters / 2.0) * 1000.0'",
"    new_d='val d = axisDistance(dimPoint1!!, dimPoint2!!) * (sourceMaxExtentMeters / 2.0) * 1000.0'",
"    if old_d not in s: raise SystemExit('axis DIM fallback distance anchor missing')",
"    s=s.replace(old_d,new_d,1)",
"    if 'private var lastDimAxisName=' not in s:",
"        s=s.replace('    private var dimModeChip: TextView?=null\\n','    private var dimModeChip: TextView?=null\\n    private var lastDimAxisName=\\\"X\\\"\\n',1)",
"    dist_anchor='    private fun distance(a: DoubleArray, b: DoubleArray): Double {'",
"    helper='    private fun axisDistance(a:DoubleArray,b:DoubleArray):Double{\\n        val dx=kotlin.math.abs(b[0]-a[0]);val dy=kotlin.math.abs(b[1]-a[1]);val dz=kotlin.math.abs(b[2]-a[2])\\n        if(dimDiagonalMode){lastDimAxisName=\\\"↗\\\";return sqrt(dx*dx+dy*dy+dz*dz)}\\n        return if(dx>=dy&&dx>=dz){lastDimAxisName=\\\"X\\\";dx}else if(dy>=dz){lastDimAxisName=\\\"Y\\\";dy}else{lastDimAxisName=\\\"Z\\\";dz}\\n    }\\n\\n'+dist_anchor",
"    if dist_anchor not in s: raise SystemExit('axisDistance anchor missing')",
"    s=s.replace(dist_anchor,helper,1)",
"    s=s.replace('showDimLineAndLabel(p1.first, p1.second, x, y, \\\"$mm mm\\\")','showDimLineAndLabel(p1.first, p1.second, x, y, \\\"${lastDimAxisName} $mm mm\\\")',1)",
"    s=s.replace('setStatus(\\\"DIM • $mm mm','setStatus(\\\"DIM • ${lastDimAxisName} $mm mm',1)",
]
new='\n'.join(lines)
s=s.replace(old,new)
p.write_text(s)
print('v0.2.3 patch script repaired with axis fallback')
