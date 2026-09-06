package vn.ecohome.viewer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Skybox
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
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

    private val choreographer by lazy { Choreographer.getInstance() }
    private val lightEntities = mutableListOf<Int>()
    private var skybox: Skybox? = null
    private var indirectLight: IndirectLight? = null
    private var lastUri: Uri? = null
    private var currentMode = "SHADED"
    private var interactionMode = "ORBIT"
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var rootTransformAfterFit: FloatArray? = null
    private var panX = 0f
    private var panY = 0f

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
        installTouchController()
        setupLighting()
        val restored = savedInstanceState?.getString("last_uri")?.let(Uri::parse)
        lastUri = restored ?: resolveIncomingUri(intent)
        lastUri?.let { loadModel(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveIncomingUri(intent)?.let {
            lastUri = it
            loadModel(it)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveIncomingUri(intent: Intent?): Uri? {
        if (intent == null) return null
        intent.data?.let { return it }
        if (intent.action == Intent.ACTION_SEND) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
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
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
            background = solid(Color.argb(248, 250, 252, 250), 0f)
            elevation = dp(2).toFloat()
        }
        topBar.addView(topAction("‹") { finish() })
        titleView = TextView(this).apply {
            text = "ECOHOME Viewer"
            textSize = if (landscape) 15f else 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(darkGreen)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
        }
        topBar.addView(titleView, LinearLayout.LayoutParams(0, dp(56), 1f))
        topBar.addView(topAction("ⓘ") { showInfo() })
        topBar.addView(topAction("⋯") { showMore() })
        root.addView(topBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58), Gravity.TOP))

        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(4), dp(5), dp(4))
            background = solid(panel, 17f, border)
            elevation = dp(5).toFloat()
        }
        listOf(
            Triple("⌂", "HOME") { fitModel() },
            Triple("◇", "VIEW") { showViewPanel() },
            Triple("⌇", "DIM") { showDimPanel() },
            Triple("▱", "TAGS") { showTagsPanel() },
            Triple("ⓘ", "INFO") { showInfo() }
        ).forEachIndexed { i, item ->
            bottomNav.addView(navItem(item.first, item.second, i == 1, item.third), LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                if (i > 0) marginStart = dp(2)
            })
        }
        root.addView(bottomNav, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64), Gravity.BOTTOM).apply {
            setMargins(dp(10), 0, dp(10), dp(8))
        })

        val cameraTools = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = solid(panel, 17f, border)
            elevation = dp(5).toFloat()
        }
        listOf(
            Triple("◎", "Orbit") { setInteraction("ORBIT") },
            Triple("✣", "Pan") { setInteraction("PAN") },
            Triple("⌕", "Zoom") { setInteraction("ZOOM") },
            Triple("⌗", "Fit") { fitModel() },
            Triple("↻", "Reset") { resetView() }
        ).forEachIndexed { i, item ->
            val v = miniTool(item.first, item.second, i == 0, item.third)
            if (landscape) cameraTools.addView(v, LinearLayout.LayoutParams(dp(60), dp(52)).apply { if (i > 0) topMargin = dp(2) })
            else cameraTools.addView(v, LinearLayout.LayoutParams(0, dp(56), 1f).apply { if (i > 0) marginStart = dp(2) })
        }
        if (landscape) {
            root.addView(cameraTools, FrameLayout.LayoutParams(dp(68), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = dp(10); bottomMargin = dp(12)
            })
        } else {
            root.addView(cameraTools, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64), Gravity.BOTTOM).apply {
                setMargins(dp(12), 0, dp(12), dp(78))
            })
        }

        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = solid(Color.argb(245, 252, 253, 252), 15f, border)
            elevation = dp(4).toFloat()
        }
        listOf(
            "⌂" to { fitModel() },
            "◇" to { showViewPanel() },
            "▦" to { showTagsPanel() },
            "⌗" to { fitModel() },
            "☼" to { showLightingPanel() },
            "⌇" to { showDimPanel() }
        ).forEach { item -> side.addView(sideIcon(item.first, item.second), LinearLayout.LayoutParams(dp(46), dp(44)).apply { bottomMargin = dp(2) }) }
        root.addView(side, FrameLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL).apply {
            marginStart = dp(10); bottomMargin = if (landscape) dp(10) else dp(78)
        })

        statusView = TextView(this).apply {
            text = "VIEW • SHADED + EDGES"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(50, 68, 56))
            background = solid(Color.argb(239, 252, 253, 252), 20f, Color.rgb(226, 232, 228))
        }
        root.addView(statusView, FrameLayout.LayoutParams(if (landscape) dp(260) else ViewGroup.LayoutParams.MATCH_PARENT, dp(36), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            if (landscape) setMargins(0, 0, 0, dp(70)) else setMargins(dp(16), 0, dp(16), dp(148))
        })

        setContentView(root)
    }

    private fun installTouchController() {
        surfaceView.setOnTouchListener { _, event ->
            if (interactionMode != "PAN") return@setOnTouchListener modelViewer.onTouch(surfaceView, event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastTouchX = event.x; lastTouchY = event.y; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    lastTouchX = event.x; lastTouchY = event.y
                    panModel(dx, dy)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun panModel(dxPixels: Float, dyPixels: Float) {
        val asset = modelViewer.asset ?: return
        val base = rootTransformAfterFit ?: return
        panX += dxPixels / surfaceView.width.coerceAtLeast(1) * 2.4f
        panY -= dyPixels / surfaceView.height.coerceAtLeast(1) * 2.4f
        val m = base.clone()
        m[12] = base[12] + panX
        m[13] = base[13] + panY
        val tm = modelViewer.engine.transformManager
        tm.setTransform(tm.getInstance(asset.root), m)
        setStatus("PAN • 1 ngón di chuyển")
    }

    private fun setInteraction(mode: String) {
        interactionMode = mode
        setStatus(when (mode) {
            "PAN" -> "PAN • kéo 1 ngón để di chuyển model"
            "ZOOM" -> "ZOOM • chụm 2 ngón để phóng / thu"
            else -> "ORBIT • kéo 1 ngón để xoay"
        })
    }

    private fun navItem(icon: String, label: String, selected: Boolean, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        background = solid(if (selected) green else Color.TRANSPARENT, 11f)
        addView(iconText(icon, 22f, if (selected) Color.WHITE else darkGreen), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(29)))
        addView(iconText(label, 10f, if (selected) Color.WHITE else Color.rgb(48, 64, 53)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(19)))
        setOnClickListener { action() }
    }

    private fun miniTool(icon: String, label: String, selected: Boolean, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        background = solid(if (selected) green else Color.rgb(253, 254, 253), 11f, if (selected) green else Color.rgb(232, 237, 233))
        addView(iconText(icon, 21f, if (selected) Color.WHITE else Color.BLACK), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(29)))
        addView(iconText(label, 10f, if (selected) Color.WHITE else Color.rgb(50, 57, 52)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(19)))
        setOnClickListener { action() }
    }

    private fun sideIcon(icon: String, action: () -> Unit): View = iconText(icon, 22f, darkGreen).apply {
        gravity = Gravity.CENTER
        background = solid(Color.rgb(253, 254, 253), 10f, Color.rgb(232, 237, 233))
        setOnClickListener { action() }
    }

    private fun topAction(text: String, action: () -> Unit): View = iconText(text, 24f, darkGreen).apply {
        gravity = Gravity.CENTER; setOnClickListener { action() }
    }.also { it.layoutParams = LinearLayout.LayoutParams(dp(44), dp(48)) }

    private fun iconText(value: String, size: Float, color: Int): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER; includeFontPadding = false
    }

    private fun setupLighting() {
        runCatching {
            modelViewer.scene.removeEntity(modelViewer.light)
            skybox = Skybox.Builder().color(0.90f, 0.92f, 0.91f, 1.0f).build(modelViewer.engine)
            modelViewer.scene.skybox = skybox
            val ambient = floatArrayOf(1.0f, 1.0f, 1.0f)
            indirectLight = IndirectLight.Builder().irradiance(1, ambient).radiance(1, ambient).intensity(26000f).build(modelViewer.engine)
            modelViewer.scene.indirectLight = indirectLight
            addLight(-0.55f, -1.0f, -0.75f, 42000f)
            addLight(0.75f, -0.35f, -0.35f, 18000f)
            addLight(-0.65f, 0.15f, 0.55f, 14000f)
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
            if (which == 0 || which == 2 || which == 3) Toast.makeText(this, "Edges thật cần dữ liệu cạnh từ ECOHOME Exporter; GLB hiện tại chỉ có mesh.", Toast.LENGTH_LONG).show()
            d.dismiss()
        }.setNegativeButton("Đóng", null).show()
    }

    private fun showDimPanel() {
        val asset = modelViewer.asset ?: run { Toast.makeText(this, "Hãy mở model trước", Toast.LENGTH_SHORT).show(); return }
        val h = asset.boundingBox.halfExtent
        val x = (h[0] * 2f * 1000f).roundToInt(); val y = (h[1] * 2f * 1000f).roundToInt(); val z = (h[2] * 2f * 1000f).roundToInt()
        AlertDialog.Builder(this).setTitle("Công cụ đo kích thước (Dim)").setItems(arrayOf("Kích thước tổng: X $x mm • Y $y mm • Z $z mm", "Đo 2 điểm", "Đo liên tục", "Góc", "Diện tích", "Xóa đo")) { _, which ->
            if (which == 0) setStatus("DIM • X $x  Y $y  Z $z mm") else Toast.makeText(this, "Đã chọn công cụ đo", Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun showTagsPanel() {
        val asset = modelViewer.asset ?: run { Toast.makeText(this, "Hãy mở model trước", Toast.LENGTH_SHORT).show(); return }
        val named = asset.entities.mapNotNull { e ->
            val name = runCatching { asset.getName(e) }.getOrNull()
            if (name.isNullOrBlank()) null else e to name
        }.distinctBy { it.second }.take(40)
        if (named.isEmpty()) { Toast.makeText(this, "GLB này chưa có tên node/tag. ECOHOME Exporter sẽ bổ sung metadata.", Toast.LENGTH_LONG).show(); return }
        val labels = named.map { it.second }.toTypedArray(); val checked = BooleanArray(labels.size) { true }
        AlertDialog.Builder(this).setTitle("Quản lý Tags / Layers").setMultiChoiceItems(labels, checked) { _, which, isChecked ->
            val entity = named[which].first
            if (isChecked) modelViewer.scene.addEntity(entity) else modelViewer.scene.removeEntity(entity)
        }.setPositiveButton("Xong") { _, _ -> setStatus("TAGS • Đã cập nhật hiển thị") }.show()
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
        AlertDialog.Builder(this).setTitle("Thông tin mô hình").setMessage("${titleView.text}\n\nECOHOME Viewer v0.1.4\nARM64 • GLB\nAuto rotate Portrait / Landscape$dim\n\nOrbit • Pan 1 ngón • Zoom • View • Dim • Tags").setPositiveButton("Đóng", null).show()
    }

    private fun showMore() {
        AlertDialog.Builder(this).setItems(arrayOf("Mở model", "Fit model", "Tùy chọn hiển thị", "Thông tin")) { _, which ->
            when (which) { 0 -> openDocument(); 1 -> fitModel(); 2 -> showLightingPanel(); 3 -> showInfo() }
        }.show()
    }

    private fun setBackground(r: Float, g: Float, b: Float) {
        runCatching {
            skybox?.let { modelViewer.scene.skybox = null; modelViewer.engine.destroySkybox(it) }
            skybox = Skybox.Builder().color(r, g, b, 1f).build(modelViewer.engine); modelViewer.scene.skybox = skybox
        }
    }

    private fun resetView() {
        panX = 0f; panY = 0f
        runCatching { modelViewer.resetToDefaultState() }
        fitModel(); setInteraction("ORBIT")
    }

    private fun fitModel() {
        runCatching {
            modelViewer.transformToUnitCube()
            val asset = modelViewer.asset ?: return@runCatching
            val tm = modelViewer.engine.transformManager
            val arr = FloatArray(16); tm.getTransform(tm.getInstance(asset.root), arr)
            rootTransformAfterFit = arr; panX = 0f; panY = 0f
            setStatus("FIT • Model vừa khung nhìn")
        }
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
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext != "glb") Toast.makeText(this, "ECOHOME Viewer hiện tối ưu cho .glb", Toast.LENGTH_LONG).show()
            setStatus("Đang mở $name…")
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Không đọc được file")
            modelViewer.destroyModel(); modelViewer.loadModelGlb(ByteBuffer.wrap(bytes)); titleView.text = name
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
        choreographer.removeFrameCallback(frameCallback)
        if (::modelViewer.isInitialized) {
            lightEntities.forEach { e -> runCatching { modelViewer.scene.removeEntity(e) }; runCatching { modelViewer.engine.destroyEntity(e) } }
            indirectLight?.let { runCatching { modelViewer.scene.indirectLight = null; modelViewer.engine.destroyIndirectLight(it) } }
            skybox?.let { runCatching { modelViewer.scene.skybox = null; modelViewer.engine.destroySkybox(it) } }
            modelViewer.destroyModel()
        }
        super.onDestroy()
    }

    private fun solid(fill: Int, radiusDp: Float, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radiusDp.toInt()).toFloat(); stroke?.let { setStroke(dp(1), it) }
    }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
