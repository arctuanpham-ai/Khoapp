from pathlib import Path
import re

p = Path('app/src/main/java/vn/ecohome/viewer/MainActivity.kt')
s = p.read_text()

anchor = '    private var sourceMaxExtentMeters = 1.0\n'
extra = (
    '    private var sourceMaxExtentMeters = 1.0\n'
    '    private var touchDownX = 0f\n'
    '    private var touchDownY = 0f\n'
    '    private var longPressArmed = false\n'
    '    private var dimScreen1: Pair<Float, Float>? = null\n'
    '    private val dimMarkerViews = mutableListOf<View>()\n'
)
if anchor in s:
    s = s.replace(anchor, extra, 1)

s = s.replace('Triple("⌗", "Fit") { fitModel() }', 'Triple("⛶", "Extents") { fitModel() }', 1)

pattern = r'    private fun installSmartTouchController\(\) \{.*?\n    \}\n\n    private fun showMagnifier'
replacement = '''    private fun installSmartTouchController() {
        surfaceView.setOnTouchListener { _, event ->
            if (!dimMode) {
                return@setOnTouchListener modelViewer.onTouch(surfaceView, event)
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x; touchDownY = event.y
                    holdX = event.x; holdY = event.y
                    longPressArmed = true
                    surfaceView.postDelayed({
                        if (dimMode && longPressArmed && !precisionHold) {
                            precisionHold = true
                            showMagnifier(holdX, holdY)
                            setStatus("DIM • rê ngón tay để căn điểm • nhả để chốt")
                        }
                    }, 360)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    holdX = event.x; holdY = event.y
                    val dx = holdX - touchDownX; val dy = holdY - touchDownY
                    if (!precisionHold && dx * dx + dy * dy > dp(10) * dp(10)) longPressArmed = false
                    if (precisionHold) showMagnifier(holdX, holdY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressArmed = false
                    if (precisionHold) {
                        hideMagnifier(); precisionHold = false
                        pickDimPoint(holdX, holdY)
                    } else setStatus("DIM • ấn giữ trên bề mặt để chọn điểm")
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressArmed = false; precisionHold = false; hideMagnifier(); true
                }
                else -> true
            }
        }
    }

    private fun showMagnifier'''
s, n = re.subn(pattern, replacement, s, count=1, flags=re.S)
if n != 1:
    raise SystemExit(f'controller patch count={n}')

anchor2 = '    private fun hideMagnifier() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { magnifier?.dismiss() } }\n'
helper = '''    private fun hideMagnifier() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { magnifier?.dismiss() } }

    private fun clearDimOverlay() {
        dimMarkerViews.forEach { runCatching { root.removeView(it) } }
        dimMarkerViews.clear()
        dimScreen1 = null
    }

    private fun showDimMarker(x: Float, y: Float, label: String) {
        val marker = TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = solid(green, 18f, Color.WHITE)
            elevation = dp(8).toFloat()
        }
        val size = dp(34)
        root.addView(marker, FrameLayout.LayoutParams(size, size).apply {
            leftMargin = (x - size / 2f).roundToInt()
            topMargin = (y - size / 2f).roundToInt()
        })
        dimMarkerViews.add(marker)
    }

    private fun showDimLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        val dx = x2 - x1; val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy).roundToInt().coerceAtLeast(1)
        val line = View(this).apply {
            setBackgroundColor(green)
            pivotX = 0f; pivotY = dp(1).toFloat()
            rotation = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
            elevation = dp(6).toFloat()
        }
        root.addView(line, FrameLayout.LayoutParams(len, dp(2)).apply {
            leftMargin = x1.roundToInt(); topMargin = y1.roundToInt()
        })
        dimMarkerViews.add(line)
    }
'''
if anchor2 not in s:
    raise SystemExit('hideMagnifier anchor missing')
s = s.replace(anchor2, helper, 1)

old1 = '''            if (dimPoint1 == null) {
                dimPoint1 = world; dimPoint2 = null
                setStatus("DIM • Điểm 1 ✓ • ấn giữ để chọn điểm 2")
            } else {
                dimPoint2 = world
                val d = distance(dimPoint1!!, dimPoint2!!) * (sourceMaxExtentMeters / 2.0) * 1000.0
                setStatus("DIM • ${d.roundToInt()} mm")
                Toast.makeText(this, "Khoảng cách: ${d.roundToInt()} mm", Toast.LENGTH_LONG).show()
            }'''
new1 = '''            if (dimPoint1 == null) {
                clearDimOverlay()
                dimPoint1 = world; dimPoint2 = null
                dimScreen1 = x to y
                showDimMarker(x, y, "1")
                setStatus("DIM • Điểm 1 ✓ • ấn giữ để chọn điểm 2")
            } else {
                dimPoint2 = world
                val d = distance(dimPoint1!!, dimPoint2!!) * (sourceMaxExtentMeters / 2.0) * 1000.0
                val p1 = dimScreen1
                if (p1 != null) showDimLine(p1.first, p1.second, x, y)
                showDimMarker(x, y, "2")
                setStatus("DIM • ${d.roundToInt()} mm")
                Toast.makeText(this, "Khoảng cách: ${d.roundToInt()} mm", Toast.LENGTH_LONG).show()
            }'''
if old1 not in s:
    raise SystemExit('DIM branch anchor missing')
s = s.replace(old1, new1, 1)

s = s.replace('0 -> { dimMode = true; dimPoint1 = null; dimPoint2 = null; setStatus("DIM • ấn giữ điểm 1 để bật kính lúp") }', '0 -> { dimMode = true; dimPoint1 = null; dimPoint2 = null; clearDimOverlay(); setStatus("DIM • ấn giữ điểm 1 để bật kính lúp") }')
s = s.replace('1 -> { dimMode = false; dimPoint1 = null; dimPoint2 = null; setStatus("DIM • Đã xóa") }', '1 -> { dimMode = false; dimPoint1 = null; dimPoint2 = null; clearDimOverlay(); setStatus("DIM • Đã xóa") }')
s = s.replace('private fun resetView() { dimMode = false; dimPoint1 = null; dimPoint2 = null; fitModel();', 'private fun resetView() { dimMode = false; dimPoint1 = null; dimPoint2 = null; clearDimOverlay(); fitModel();')
s = s.replace('dimMode = false; dimPoint1 = null; dimPoint2 = null\n            surfaceView.postDelayed', 'dimMode = false; dimPoint1 = null; dimPoint2 = null; clearDimOverlay()\n            surfaceView.postDelayed')
s = s.replace('ECOHOME Viewer v0.1.5', 'ECOHOME Viewer v0.1.6')

p.write_text(s)
print('v0.1.6 patch applied')
