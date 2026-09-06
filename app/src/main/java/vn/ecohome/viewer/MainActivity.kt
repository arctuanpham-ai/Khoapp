package vn.ecohome.viewer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Choreographer
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Magnifier
import android.widget.TextView
import android.widget.Toast
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Skybox
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import kotlin.math.sqrt
import kotlin.math.roundToInt

class MainActivity : Activity() {
    companion object {
        private const val OPEN_MODEL = 1001
        init { Utils.init() }
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var modelViewer: ModelViewer
    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private lateinit var root: FrameLayout
    private lateinit var gestureDetector: GestureDetector

    private val choreographer by lazy { Choreographer.getInstance() }
    private val lightEntities = mutableListOf<Int>()
    private var skybox: Skybox? = null
    private var indirectLight: IndirectLight? = null
    private var lastUri: Uri? = null
    private var currentMode = "SHADED"
    private var magnifier: Magnifier? = null
    private var precisionHold = false
    private var holdX = 0f
    private var holdY = 0f
    private var dimMode = false
    private var dimPoint1: DoubleArray? = null
    private var dimPoint2: DoubleArray? = null
    private var sourceMaxExtentMeters = 1.0

    private val green = Color.rgb(55, 151, 91)
    private val darkGreen = Color.rgb(25, 77, 51)
    private val panel = Color.argb(248, 252, 253, 252)
    private val border = Color.rgb(214, 227, 218)

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            if (::modelViewer.isInitialized) modelViewer.render(frameTimeNanos)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(244, 248, 245)
        window.navigationBarColor = Color.rgb(248, 250, 248)
        buildUi()
        modelViewer = ModelViewer(surfaceView)
        installSmartTouchController()
        setupLighting()
        val restored = savedInstanceState?.getString("last_uri")?.let(Uri::parse)
        lastUri = restored ?: resolveIncomingUri(intent)
        lastUri?.let { loadModel(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveIncomingUri(intent)?.let { lastUri = it; loadModel(it) }
    }

    @Suppress("DEPRECATION")
    private fun resolveIncomingUri(intent: Intent?): Uri? {
        if (intent == null) return null
        intent.data?.let { return it }
        if (intent.action == Intent.ACTION_SEND) return intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        return null
    }

    private fun buildUi() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(232, 235, 232)) }
        surfaceView = SurfaceView(this)
        root.addView(surfaceView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
            background = solid(Color.argb(248, 250, 252, 250), 0f); elevation = dp(2).toFloat()
        }
        topBar.addView(topAction("‹") { finish() })
        titleView = TextView(this).apply {
            text = "ECOHOME Viewer"; textSize = if (landscape) 15f else 18f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(darkGreen); gravity = Gravity.CENTER_VERTICAL; maxLines = 1
        }
        topBar.addView(titleView, LinearLayout.LayoutParams(0, dp(56), 1f))
        topBar.addView(topAction("ⓘ") { showInfo() })
        topBar.addView(topAction("⋯") { showMore() })
        root.addView(topBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58), Gravity.TOP))

        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(5), dp(4), dp(5), dp(4))
            background = solid(panel, 17f, border); elevation = dp(5).toFloat()
        }
        listOf(
            Triple("⌂", "HOME") { fitModel() },
            Triple("◇", "VIEW") { showViewPanel() },
            Triple("⌇", "DIM") { showDimPanel() },
            Triple("▱", "TAGS") { showTagsPanel() },
            Triple("ⓘ", "INFO") { showInfo() }
        ).forEachIndexed { i, item ->
            bottomNav.addView(navItem(item.first, item.second, i == 1, item.third), LinearLayout.LayoutParams(0, dp(56), 1f).apply { if (i > 0) marginStart = dp(2) })
        }
        root.addView(bottomNav, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64), Gravity.BOTTOM).apply { setMargins(dp(10), 0, dp(10), dp(8)) })

        val cameraTools = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER; setPadding(dp(4), dp(4), dp(4), dp(4)); background = solid(panel, 17f, border); elevation = dp(5).toFloat()
        }
        listOf(
            Triple("◎", "Orbit") { setStatus("AUTO • 1 ngón Orbit") },
            Triple("✣", "Pan") { setStatus("AUTO • 2 ngón Pan") },
            Triple("⌕", "Zoom") { setStatus("AUTO • Pinch Zoom") },
            Triple("⌗", "Fit") { fitModel() },
            Triple("↻", "Reset") { resetView() }
        ).forEachIndexed { i, item ->
            val v = miniTool(item.first, item.second, i == 0, item.third)
            if (landscape) cameraTools.addView(v, LinearLayout.LayoutParams(dp(60), dp(52)).apply { if (i > 0) topMargin = dp(2) })
            else cameraTools.addView(v, LinearLayout.LayoutParams(0, dp(56), 1f).apply { if (i > 0) marginStart = dp(2) })
        }
        if (landscape) root.addView(cameraTools, FrameLayout.LayoutParams(dp(68), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(10); bottomMargin = dp(12) })
        else root.addView(cameraTools, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64), Gravity.BOTTOM).apply { setMargins(dp(12), 0, dp(12), dp(78)) })

        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(4), dp(4), dp(4), dp(4))
            background = solid(Color.argb(245, 252, 253, 252), 15f, border); elevation = dp(4).toFloat()
        }
        listOf(
            "⌂" to { fitModel() }, "◇" to { showViewPanel() }, "▦" to { showTagsPanel() },
            "⌗" to { fitModel() }, "☼" to { showLightingPanel() }, "⌇" to { showDimPanel() }
        ).forEach { item -> side.addView(sideIcon(item.first, item.second), LinearLayout.LayoutParams(dp(46), dp(44)).apply { bottomMargin = dp(2) }) }
        root.addView(side, FrameLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(10); bottomMargin = if (landscape) dp(10) else dp(78) })

        statusView = TextView(this).apply {
            text = "AUTO • 1 ngón Orbit • 2 ngón Pan • Pinch Zoom"; textSize = 11.5f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(50, 68, 56)); background = solid(Color.argb(239, 252, 253, 252), 20f, Color.rgb(226, 232, 228))
        }
        root.addView(statusView, FrameLayout.LayoutParams(if (landscape) dp(330) else ViewGroup.LayoutParams.MATCH_PARENT, dp(36), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            if (landscape) setMargins(0, 0, 0, dp(70)) else setMargins(dp(16), 0, dp(16), dp(148))
        })
        setContentView(root)
    }

    private fun installSmartTouchController() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!dimMode) pickEntity(e.x, e.y)
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                precisionHold = true; holdX = e.x; holdY = e.y
                showMagnifier(holdX, holdY)
                setStatus(if (dimMode) "DIM • giữ và rê để căn chính xác • nhả để chốt điểm" else "SELECT • giữ và rê để căn chính xác")
            }
        })

        surfaceView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            if (precisionHold) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> { holdX = event.x; holdY = event.y; showMagnifier(holdX, holdY); true }
                    MotionEvent.ACTION_UP -> {
                        hideMagnifier(); precisionHold = false
                        if (dimMode) pickDimPoint(holdX, holdY) else pickEntity(holdX, holdY)
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> { hideMagnifier(); precisionHold = false; true }
                    else -> true
                }
            } else {
                modelViewer.onTouch(surfaceView, event)
            }
        }
    }

    private fun showMagnifier(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (magnifier == null) magnifier = Magnifier.Builder(surfaceView).setSize(dp(150), dp(100)).setInitialZoom(2.8f).build()
        runCatching { magnifier?.show(x, y) }
    }

    private fun hideMagnifier() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { magnifier?.dismiss() } }

    private fun pickEntity(x: Float, y: Float) {
        val asset = modelViewer.asset ?: return
        modelViewer.view.pick(x.toInt().coerceAtLeast(0), (surfaceView.height - y.toInt()).coerceAtLeast(0), surfaceView.handler) { result ->
            if (result.renderable == 0) { setStatus("SELECT • Không trúng model"); return@pick }
            val raw = runCatching { asset.getName(result.renderable) }.getOrNull().orEmpty()
            val clean = cleanNodeName(raw)
            val tag = parseTag(raw)
            setStatus("SELECT • ${if (clean.isBlank()) "Chi tiết" else clean}${if (tag != null) " • Tag: $tag" else ""}")
        }
    }

    private fun pickDimPoint(x: Float, y: Float) {
        val asset = modelViewer.asset ?: return
        modelViewer.view.pick(x.toInt().coerceAtLeast(0), (surfaceView.height - y.toInt()).coerceAtLeast(0), surfaceView.handler) { result ->
            if (result.renderable == 0) { setStatus("DIM • Không trúng bề mặt, chọn lại"); return@pick }
            val world = unproject(result.fragCoords)
            if (world == null) { setStatus("DIM • Không xác định được tọa độ điểm"); return@pick }
            if (dimPoint1 == null) {
                dimPoint1 = world; dimPoint2 = null
                setStatus("DIM • Điểm 1 ✓ • ấn giữ để chọn điểm 2")
            } else {
                dimPoint2 = world
                val d = distance(dimPoint1!!, dimPoint2!!) * (sourceMaxExtentMeters / 2.0) * 1000.0
                setStatus("DIM • ${d.roundToInt()} mm")
                Toast.makeText(this, "Khoảng cách: ${d.roundToInt()} mm", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun unproject(frag: FloatArray): DoubleArray? {
        if (frag.size < 3 || surfaceView.width <= 0 || surfaceView.height <= 0) return null
        val ndc = doubleArrayOf(
            frag[0] / surfaceView.width.toDouble() * 2.0 - 1.0,
            frag[1] / surfaceView.height.toDouble() * 2.0 - 1.0,
            frag[2].toDouble() * 2.0 - 1.0,
            1.0
        )
        val proj = modelViewer.camera.getProjectionMatrix(null)
        val inv = invert4(proj) ?: return null
        val view = mul4(inv, ndc)
        if (kotlin.math.abs(view[3]) < 1e-9) return null
        for (i in 0..2) view[i] /= view[3]
        view[3] = 1.0
        val cam = modelViewer.camera.getModelMatrix(null as DoubleArray?)
        val world = mul4(cam, view)
        if (kotlin.math.abs(world[3]) > 1e-9) for (i in 0..2) world[i] /= world[3]
        return doubleArrayOf(world[0], world[1], world[2])
    }

    private fun distance(a: DoubleArray, b: DoubleArray): Double {
        val dx = a[0]-b[0]; val dy = a[1]-b[1]; val dz = a[2]-b[2]
        return sqrt(dx*dx + dy*dy + dz*dz)
    }

    private fun mul4(m: DoubleArray, v: DoubleArray): DoubleArray {
        val r = DoubleArray(4)
        for (row in 0..3) r[row] = m[row] * v[0] + m[4 + row] * v[1] + m[8 + row] * v[2] + m[12 + row] * v[3]
        return r
    }

    private fun invert4(m: DoubleArray): DoubleArray? {
        val a = Array(4) { DoubleArray(8) }
        for (r in 0..3) for (c in 0..3) a[r][c] = m[c*4+r]
        for (i in 0..3) a[i][i+4] = 1.0
        for (i in 0..3) {
            var pivot = i
            for (r in i+1..3) if (kotlin.math.abs(a[r][i]) > kotlin.math.abs(a[pivot][i])) pivot = r
            if (kotlin.math.abs(a[pivot][i]) < 1e-12) return null
            val tmp = a[i]; a[i] = a[pivot]; a[pivot] = tmp
            val div = a[i][i]; for (c in 0..7) a[i][c] /= div
            for (r in 0..3) if (r != i) {
                val f = a[r][i]; for (c in 0..7) a[r][c] -= f * a[i][c]
            }
        }
        val out = DoubleArray(16)
        for (r in 0..3) for (c in 0..3) out[c*4+r] = a[r][c+4]
        return out
    }

    private fun navItem(icon: String, label: String, selected: Boolean, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = solid(if (selected) green else Color.TRANSPARENT, 11f)
        addView(iconText(icon, 22f, if (selected) Color.WHITE else darkGreen), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(29)))
        addView(iconText(label, 10f, if (selected) Color.WHITE else Color.rgb(48, 64, 53)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(19)))
        setOnClickListener { action() }
    }

    private fun miniTool(icon: String, label: String, selected: Boolean, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = solid(if (selected) green else Color.rgb(253, 254, 253), 11f, if (selected) green else Color.rgb(232, 237, 233))
        addView(iconText(icon, 21f, if (selected) Color.WHITE else Color.BLACK), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(29)))
        addView(iconText(label, 10f, if (selected) Color.WHITE else Color.rgb(50, 57, 52)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(19)))
        setOnClickListener { action() }
    }

    private fun sideIcon(icon: String, action: () -> Unit): View = iconText(icon, 22f, darkGreen).apply { gravity = Gravity.CENTER; background = solid(Color.rgb(253, 254, 253), 10f, Color.rgb(232, 237, 233)); setOnClickListener { action() } }
    private fun topAction(text: String, action: () -> Unit): View = iconText(text, 24f, darkGreen).apply { gravity = Gravity.CENTER; setOnClickListener { action() } }.also { it.layoutParams = LinearLayout.LayoutParams(dp(44), dp(48)) }
    private fun iconText(value: String, size: Float, color: Int): TextView = TextView(this).apply { text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER; includeFontPadding = false }

    private fun setupLighting() {
        runCatching {
            modelViewer.scene.removeEntity(modelViewer.light)
            skybox = Skybox.Builder().color(0.90f, 0.92f, 0.91f, 1.0f).build(modelViewer.engine); modelViewer.scene.skybox = skybox
            val ambient = floatArrayOf(1.0f, 1.0f, 1.0f)
            indirectLight = IndirectLight.Builder().irradiance(1, ambient).radiance(1, ambient).intensity(26000f).build(modelViewer.engine); modelViewer.scene.indirectLight = indirectLight
            addLight(-0.55f, -1.0f, -0.75f, 42000f); addLight(0.75f, -0.35f, -0.35f, 18000f); addLight(-0.65f, 0.15f, 0.55f, 14000f)
            modelViewer.camera.setExposure(16.0f, 1.0f / 125.0f, 100.0f)
        }.onFailure { setStatus("Lighting fallback • ${it.message ?: ""}") }
    }

    private fun addLight(x: Float, y: Float, z: Float, intensity: Float) {
        val entity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL).color(1.0f, 0.99f, 0.97f).intensity(intensity).direction(x, y, z).castShadows(false).build(modelViewer.engine, entity)
        modelViewer.scene.addEntity(entity); lightEntities.add(entity)
    }

    private fun showViewPanel() {
        val options = arrayOf("Shaded + Edges", "Texture / Material", "Hidden Line", "Wireframe", "X-Ray", "Opacity")
        val selected = when (currentMode) { "SHADED" -> 0; "TEXTURE" -> 1; "HIDDEN" -> 2; "WIREFRAME" -> 3; "X-RAY" -> 4; else -> 5 }
        AlertDialog.Builder(this).setTitle("Chế độ hiển thị").setSingleChoiceItems(options, selected) { d, which ->
            currentMode = arrayOf("SHADED", "TEXTURE", "HIDDEN", "WIREFRAME", "X-RAY", "OPACITY")[which]
            setStatus("VIEW • ${options[which].uppercase()}")
            when (which) { 0,1 -> setBackground(.90f,.92f,.91f); 2,3 -> setBackground(.97f,.98f,.97f); 4 -> setBackground(.80f,.84f,.82f); 5 -> setBackground(.92f,.94f,.93f) }
            d.dismiss()
        }.setNegativeButton("Đóng", null).show()
    }

    private fun showDimPanel() {
        if (modelViewer.asset == null) { Toast.makeText(this, "Hãy mở model trước", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("DIM / MEASURE").setItems(arrayOf("Đo 2 điểm chính xác", "Xóa điểm đo", "Kích thước tổng model")) { _, which ->
            when (which) {
                0 -> { dimMode = true; dimPoint1 = null; dimPoint2 = null; setStatus("DIM • ấn giữ điểm 1 để bật kính lúp") }
                1 -> { dimMode = false; dimPoint1 = null; dimPoint2 = null; setStatus("DIM • Đã xóa") }
                2 -> showBoundingDimensions()
            }
        }.show()
    }

    private fun showBoundingDimensions() {
        val h = modelViewer.asset?.boundingBox?.halfExtent ?: return
        val x = (h[0] * 2000f).roundToInt(); val y = (h[1] * 2000f).roundToInt(); val z = (h[2] * 2000f).roundToInt()
        AlertDialog.Builder(this).setTitle("Kích thước tổng").setMessage("X: $x mm\nY: $y mm\nZ: $z mm").setPositiveButton("Đóng", null).show()
    }

    private fun parseTag(raw: String): String? {
        val p = "__ECO_TAG__"
        val q = "__ECO_NAME__"
        val i = raw.indexOf(p); if (i < 0) return null
        val start = i + p.length; val end = raw.indexOf(q, start)
        return if (end > start) raw.substring(start, end) else null
    }

    private fun cleanNodeName(raw: String): String {
        val q = "__ECO_NAME__"; val i = raw.indexOf(q)
        return if (i >= 0) raw.substring(i + q.length) else raw
    }

    private fun showTagsPanel() {
        val asset = modelViewer.asset ?: run { Toast.makeText(this, "Hãy mở model trước", Toast.LENGTH_SHORT).show(); return }
        val groups = linkedMapOf<String, MutableList<Int>>()
        for (e in asset.entities) {
            val raw = runCatching { asset.getName(e) }.getOrNull().orEmpty()
            val tag = parseTag(raw) ?: continue
            groups.getOrPut(tag) { mutableListOf() }.add(e)
        }
        if (groups.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Tags SketchUp").setMessage("File GLB này chưa có metadata Tags của SketchUp. Hãy xuất lại bằng ECOHOME Viewer Exporter v0.2.0 trở lên. App sẽ không dùng tên ID/node thay cho Tags nữa.").setPositiveButton("Đóng", null).show()
            return
        }
        val labels = groups.keys.toTypedArray(); val checked = BooleanArray(labels.size) { true }
        AlertDialog.Builder(this).setTitle("Tags SketchUp").setMultiChoiceItems(labels, checked) { _, which, isChecked ->
            val entities = groups[labels[which]].orEmpty()
            for (entity in entities) if (isChecked) modelViewer.scene.addEntity(entity) else modelViewer.scene.removeEntity(entity)
        }.setPositiveButton("Xong") { _, _ -> setStatus("TAGS • Đã cập nhật theo SketchUp Tags") }.show()
    }

    private fun showLightingPanel() {
        AlertDialog.Builder(this).setTitle("Tùy chọn hiển thị").setItems(arrayOf("Nền sáng", "Nền xám", "Nền tối", "Tăng ambient", "Giảm ambient")) { _, which ->
            when (which) { 0 -> setBackground(.94f,.95f,.94f); 1 -> setBackground(.76f,.79f,.77f); 2 -> setBackground(.28f,.30f,.29f); 3 -> indirectLight?.intensity = 40000f; 4 -> indirectLight?.intensity = 16000f }
            setStatus("DISPLAY • Đã cập nhật")
        }.show()
    }

    private fun showInfo() {
        val box = modelViewer.asset?.boundingBox?.halfExtent
        val dim = if (box != null) "\nKích thước: ${(box[0]*2000).roundToInt()} × ${(box[1]*2000).roundToInt()} × ${(box[2]*2000).roundToInt()} mm" else ""
        AlertDialog.Builder(this).setTitle("Thông tin mô hình").setMessage("${titleView.text}\n\nECOHOME Viewer v0.1.5\nARM64 • GLB\nAuto gesture: 1 ngón Orbit • 2 ngón Pan • Pinch Zoom$dim\n\nTap: chọn chi tiết\nDIM: ấn giữ + kính lúp precision pick\nTags: đọc metadata SketchUp từ ECOHOME Exporter").setPositiveButton("Đóng", null).show()
    }

    private fun showMore() {
        AlertDialog.Builder(this).setItems(arrayOf("Mở model", "Fit model", "Tùy chọn hiển thị", "Thông tin")) { _, which -> when (which) { 0 -> openDocument(); 1 -> fitModel(); 2 -> showLightingPanel(); 3 -> showInfo() } }.show()
    }

    private fun setBackground(r: Float, g: Float, b: Float) {
        runCatching {
            skybox?.let { modelViewer.scene.skybox = null; modelViewer.engine.destroySkybox(it) }
            skybox = Skybox.Builder().color(r, g, b, 1f).build(modelViewer.engine); modelViewer.scene.skybox = skybox
        }
    }

    private fun resetView() { dimMode = false; dimPoint1 = null; dimPoint2 = null; fitModel(); setStatus("AUTO • 1 ngón Orbit • 2 ngón Pan • Pinch Zoom") }

    private fun fitModel() {
        runCatching { modelViewer.transformToUnitCube(); setStatus("FIT • Model vừa khung nhìn") }
    }

    private fun openDocument() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("model/gltf-binary", "model/gltf+json", "application/octet-stream", "application/gltf-buffer"))
        }
        startActivityForResult(i, OPEN_MODEL)
    }

    @Deprecated("Kept for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OPEN_MODEL && resultCode == RESULT_OK) data?.data?.let { uri ->
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            lastUri = uri; loadModel(uri)
        }
    }

    private fun loadModel(uri: Uri) {
        try {
            val name = queryName(uri) ?: "Model.glb"
            setStatus("Đang mở $name…")
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Không đọc được file")
            modelViewer.destroyModel(); modelViewer.loadModelGlb(ByteBuffer.wrap(bytes)); titleView.text = name
            modelViewer.asset?.boundingBox?.halfExtent?.let { h -> sourceMaxExtentMeters = maxOf(h[0], h[1], h[2]).toDouble() * 2.0 }
            dimMode = false; dimPoint1 = null; dimPoint2 = null
            surfaceView.postDelayed({ fitModel() }, 120)
            setStatus("Đã mở • ${"%.1f".format(bytes.size / 1024f / 1024f)} MB")
        } catch (e: Exception) {
            setStatus("Không mở được model"); Toast.makeText(this, "Không mở được model: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return uri.lastPathSegment
    }

    private fun setStatus(text: String) { if (::statusView.isInitialized) statusView.text = text }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); lastUri?.let { outState.putString("last_uri", it.toString()) } }
    override fun onResume() { super.onResume(); choreographer.postFrameCallback(frameCallback) }
    override fun onPause() { choreographer.removeFrameCallback(frameCallback); super.onPause() }

    override fun onDestroy() {
        choreographer.removeFrameCallback(frameCallback); hideMagnifier()
        if (::modelViewer.isInitialized) {
            lightEntities.forEach { e -> runCatching { modelViewer.scene.removeEntity(e) }; runCatching { modelViewer.engine.destroyEntity(e) } }
            indirectLight?.let { runCatching { modelViewer.scene.indirectLight = null; modelViewer.engine.destroyIndirectLight(it) } }
            skybox?.let { runCatching { modelViewer.scene.skybox = null; modelViewer.engine.destroySkybox(it) } }
            modelViewer.destroyModel()
        }
        super.onDestroy()
    }

    private fun solid(fill: Int, radiusDp: Float, stroke: Int? = null): GradientDrawable = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radiusDp.toInt()).toFloat(); stroke?.let { setStroke(dp(1), it) } }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
