from pathlib import Path
import re

p = Path('app/src/main/java/vn/ecohome/viewer/MainActivity.kt')
s = p.read_text()

# Import Manipulator.
if 'import com.google.android.filament.utils.Manipulator\n' not in s:
    s = s.replace('import com.google.android.filament.utils.ModelViewer\n', 'import com.google.android.filament.utils.ModelViewer\nimport com.google.android.filament.utils.Manipulator\n')

# Activity state.
anchor = '    private lateinit var gestureDetector: GestureDetector\n'
extra = '''    private lateinit var gestureDetector: GestureDetector
    private lateinit var cameraManipulator: Manipulator
'''
if anchor in s:
    s = s.replace(anchor, extra, 1)

anchor = '    private var sourceMaxExtentMeters = 1.0\n'
extra = '''    private var sourceMaxExtentMeters = 1.0
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var longPressArmed = false
    private var dimScreen1: Pair<Float, Float>? = null
    private val dimMarkerViews = mutableListOf<View>()
'''
if anchor in s:
    s = s.replace(anchor, extra, 1)

# Build a tuned Manipulator and pass it into ModelViewer.
old = '''        buildUi()
        modelViewer = ModelViewer(surfaceView)
        installSmartTouchController()'''
new = '''        buildUi()
        cameraManipulator = Manipulator.Builder()
            .targetPosition(0.0f, 0.0f, -4.0f)
            .viewport(surfaceView.width.coerceAtLeast(1), surfaceView.height.coerceAtLeast(1))
            .zoomSpeed(0.006f)
            .orbitSpeed(0.004f, 0.004f)
            .build(Manipulator.Mode.ORBIT)
        modelViewer = ModelViewer(surfaceView, manipulator = cameraManipulator)
        installSmartTouchController()'''
if old not in s:
    raise SystemExit('onCreate modelViewer anchor missing')
s = s.replace(old, new, 1)

# Camera strip: explicit Extents.
s = s.replace('Triple("⌗", "Fit") { fitModel() }', 'Triple("⛶", "Extents") { fitModel() }', 1)

# Replace touch controller: Filament handles normal gestures, DIM owns touch only when active.
pattern = r'    private fun installSmartTouchController\(\) \{.*?\n    \}\n\n    private fun showMagnifier'
replacement = '''    private fun installSmartTouchController() {
        surfaceView.setOnTouchListener { _, event ->
            if (!dimMode) {
                // Tuned Filament manipulator: 1 finger orbit, 2 fingers pan, pinch zoom.
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
                    }, 320)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    holdX = event.x; holdY = event.y
                    val dx = holdX - touchDownX; val dy = holdY - touchDownY
                    if (!precisionHold && dx * dx + dy * dy > dp(14) * dp(14)) longPressArmed = false
                    if (precisionHold) showMagnifier(holdX, holdY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressArmed = false
                    if (precisionHold) {
                        hideMagnifier(); precisionHold = false
                        pickDimPoint(holdX, holdY)
                    } else {
                        setStatus("DIM • ấn giữ trên bề mặt để chọn điểm")
                    }
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

# DIM overlay helpers.
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

    private fun showDimLineAndLabel(x1: Float, y1: Float, x2: Float, y2: Float, label: String) {
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

        val bubble = TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(darkGreen)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = solid(Color.WHITE, 14f, border)
            elevation = dp(8).toFloat()
        }
        val w = dp(110); val h = dp(32)
        root.addView(bubble, FrameLayout.LayoutParams(w, h).apply {
            leftMargin = ((x1 + x2) / 2f - w / 2f).roundToInt()
            topMargin = ((y1 + y2) / 2f - h - dp(8)).roundToInt()
        })
        dimMarkerViews.add(bubble)
    }
'''
if anchor2 not in s:
    raise SystemExit('hideMagnifier anchor missing')
s = s.replace(anchor2, helper, 1)

# Replace DIM callback branch. Marker is shown immediately on a successful surface pick.
old_dim = '''            val world = unproject(result.fragCoords)
            if (world == null) { setStatus("DIM • Không xác định được tọa độ điểm"); return@pick }
            if (dimPoint1 == null) {
                dimPoint1 = world; dimPoint2 = null
                setStatus("DIM • Điểm 1 ✓ • ấn giữ để chọn điểm 2")
            } else {
                dimPoint2 = world
                val d = distance(dimPoint1!!, dimPoint2!!) * (sourceMaxExtentMeters / 2.0) * 1000.0
                setStatus("DIM • ${d.roundToInt()} mm")
                Toast.makeText(this, "Khoảng cách: ${d.roundToInt()} mm", Toast.LENGTH_LONG).show()
            }'''
new_dim = '''            val world = unproject(result.fragCoords)
            if (world == null) {
                setStatus("DIM • Đã bắt bề mặt nhưng chưa giải được tọa độ 3D")
                return@pick
            }
            if (dimPoint1 == null) {
                clearDimOverlay()
                dimPoint1 = world; dimPoint2 = null
                dimScreen1 = x to y
                showDimMarker(x, y, "1")
                setStatus("DIM • Điểm 1 ✓ • ấn giữ để chọn điểm 2")
            } else {
                dimPoint2 = world
                val d = distance(dimPoint1!!, dimPoint2!!) * (sourceMaxExtentMeters / 2.0) * 1000.0
                val mm = d.roundToInt()
                val p1 = dimScreen1
                if (p1 != null) showDimLineAndLabel(p1.first, p1.second, x, y, "$mm mm")
                showDimMarker(x, y, "2")
                setStatus("DIM • $mm mm")
            }'''
if old_dim not in s:
    raise SystemExit('DIM branch anchor missing')
s = s.replace(old_dim, new_dim, 1)

# DIM state cleanup.
s = s.replace('0 -> { dimMode = true; dimPoint1 = null; dimPoint2 = null; setStatus("DIM • ấn giữ điểm 1 để bật kính lúp") }', '0 -> { dimMode = true; dimPoint1 = null; dimPoint2 = null; clearDimOverlay(); setStatus("DIM • ấn giữ điểm 1 để bật kính lúp") }')
s = s.replace('1 -> { dimMode = false; dimPoint1 = null; dimPoint2 = null; setStatus("DIM • Đã xóa") }', '1 -> { dimMode = false; dimPoint1 = null; dimPoint2 = null; clearDimOverlay(); setStatus("DIM • Đã xóa") }')

# Hard Zoom Extents: reset manipulator home bookmark + unit-cube transform + lens.
old_fit = '''    private fun resetView() { dimMode = false; dimPoint1 = null; dimPoint2 = null; fitModel(); setStatus("AUTO • 1 ngón Orbit • 2 ngón Pan • Pinch Zoom") }

    private fun fitModel() {
        runCatching { modelViewer.transformToUnitCube(); setStatus("FIT • Model vừa khung nhìn") }
    }'''
new_fit = '''    private fun resetView() {
        dimMode = false; dimPoint1 = null; dimPoint2 = null; clearDimOverlay()
        fitModel()
        setStatus("AUTO • 1 ngón Orbit • 2 ngón Pan • Pinch Zoom")
    }

    private fun fitModel() {
        runCatching {
            modelViewer.cameraFocalLength = 28f
            modelViewer.transformToUnitCube()
            cameraManipulator.jumpToBookmark(cameraManipulator.homeBookmark)
            setStatus("EXTENTS • Đã đưa toàn bộ model về khung nhìn")
        }.onFailure {
            setStatus("EXTENTS • Không reset được camera")
        }
    }'''
if old_fit not in s:
    raise SystemExit('fitModel anchor missing')
s = s.replace(old_fit, new_fit, 1)

s = s.replace('dimMode = false; dimPoint1 = null; dimPoint2 = null\n            surfaceView.postDelayed', 'dimMode = false; dimPoint1 = null; dimPoint2 = null; clearDimOverlay()\n            surfaceView.postDelayed')
s = s.replace('ECOHOME Viewer v0.1.5', 'ECOHOME Viewer v0.1.7')

p.write_text(s)
print('v0.1.7 patch applied')
