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
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.Skybox
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer

class MainActivity : Activity() {
    companion object {
        private const val OPEN_MODEL = 1001
        init { Utils.init() }
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var modelViewer: ModelViewer
    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private val choreographer by lazy { Choreographer.getInstance() }
    private val lightEntities = mutableListOf<Int>()
    private var skybox: Skybox? = null
    private var lastUri: Uri? = null
    private var currentMode = "SHADED"

    private val green = Color.rgb(55, 139, 84)
    private val darkGreen = Color.rgb(27, 73, 48)
    private val paleGreen = Color.rgb(239, 247, 241)
    private val panel = Color.argb(246, 250, 252, 250)
    private val border = Color.rgb(205, 221, 210)

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            if (::modelViewer.isInitialized) modelViewer.render(frameTimeNanos)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(238, 245, 239)
        window.navigationBarColor = Color.rgb(248, 250, 248)
        buildUi()
        modelViewer = ModelViewer(surfaceView)
        surfaceView.setOnTouchListener(modelViewer)
        setupLighting()
        val restored = savedInstanceState?.getString("last_uri")?.let(Uri::parse)
        lastUri = restored ?: intent?.data
        lastUri?.let { loadModel(it) }
    }

    private fun buildUi() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(222, 226, 223)) }
        surfaceView = SurfaceView(this)
        root.addView(surfaceView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(4), dp(10), dp(4))
            background = solid(Color.argb(242, 247, 250, 247), 0f)
            elevation = dp(2).toFloat()
        }
        topBar.addView(iconText("‹", 28f, darkGreen), LinearLayout.LayoutParams(dp(34), dp(54)))
        titleView = TextView(this).apply {
            text = "ECOHOME Viewer"
            textSize = if (landscape) 15f else 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(darkGreen)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
        }
        topBar.addView(titleView, LinearLayout.LayoutParams(0, dp(54), 1f))
        topBar.addView(topAction("ⓘ") { showInfo() })
        topBar.addView(topAction("⋯") { showMore() })
        root.addView(topBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62), Gravity.TOP))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = solid(panel, 18f, border)
            elevation = dp(5).toFloat()
        }
        listOf(
            Triple("⌂", "HOME") { fitModel() },
            Triple("◇", "VIEW") { showViewPanel() },
            Triple("⌇", "DIM") { showDimPanel() },
            Triple("▱", "TAGS") { showTagsPanel() },
            Triple("ⓘ", "INFO") { showInfo() }
        ).forEachIndexed { index, item ->
            nav.addView(navItem(item.first, item.second, index == 1, item.third), LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                if (index > 0) marginStart = dp(3)
            })
        }
        root.addView(nav, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68), Gravity.BOTTOM).apply {
            setMargins(dp(10), 0, dp(10), dp(9))
        })

        val tools = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = solid(panel, 18f, border)
            elevation = dp(5).toFloat()
        }
        listOf(
            Triple("◎", "Orbit") { setStatus("ORBIT • 1 ngón xoay model") },
            Triple("✣", "Pan") { setStatus("PAN • 2 ngón di chuyển") },
            Triple("⌕", "Zoom") { setStatus("ZOOM • Chụm 2 ngón") },
            Triple("⌗", "Fit") { fitModel() },
            Triple("↻", "Reset") { fitModel() }
        ).forEachIndexed { index, item ->
            val v = miniTool(item.first, item.second, index == 0, item.third)
            if (landscape) tools.addView(v, LinearLayout.LayoutParams(dp(62), dp(54)).apply { if (index > 0) topMargin = dp(2) })
            else tools.addView(v, LinearLayout.LayoutParams(0, dp(58), 1f).apply { if (index > 0) marginStart = dp(2) })
        }
        if (landscape) {
            root.addView(tools, FrameLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = dp(10); bottomMargin = dp(45)
            })
        } else {
            root.addView(tools, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68), Gravity.BOTTOM).apply {
                setMargins(dp(12), 0, dp(12), dp(82))
            })
        }

        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            background = solid(Color.argb(238, 250, 252, 250), 16f, border)
            elevation = dp(4).toFloat()
        }
        listOf(
            Triple("⌂", "") { fitModel() },
            Triple("◇", "") { showViewPanel() },
            Triple("▦", "") { showTagsPanel() },
            Triple("⌗", "") { fitModel() },
            Triple("☼", "") { showLightingPanel() },
            Triple("⌇", "") { showDimPanel() }
        ).forEach { item -> side.addView(sideIcon(item.first, item.third), LinearLayout.LayoutParams(dp(48), dp(46)).apply { bottomMargin = dp(2) }) }
        root.addView(side, FrameLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL).apply {
            marginStart = dp(9); bottomMargin = if (landscape) dp(22) else dp(62)
        })

        statusView = TextView(this).apply {
            text = "VIEW: SHADED"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(50, 68, 56))
            background = solid(Color.argb(235, 252, 253, 252), 20f, Color.rgb(224, 230, 225))
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        root.addView(statusView, FrameLayout.LayoutParams(if (landscape) dp(250) else ViewGroup.LayoutParams.MATCH_PARENT, dp(38), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            if (landscape) setMargins(0, 0, 0, dp(82)) else setMargins(dp(16), 0, dp(16), dp(156))
        })
        setContentView(root)
    }

    private fun navItem(icon: String, label: String, selected: Boolean, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = solid(if (selected) green else Color.TRANSPARENT, 12f)
        addView(iconText(icon, 23f, if (selected) Color.WHITE else darkGreen), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)))
        addView(iconText(label, 10f, if (selected) Color.WHITE else Color.rgb(45, 62, 51)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)))
        setOnClickListener { action() }
    }

    private fun miniTool(icon: String, label: String, selected: Boolean, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = solid(if (selected) green else Color.rgb(252, 253, 252), 12f, if (selected) green else Color.rgb(231, 235, 232))
        addView(iconText(icon, 22f, if (selected) Color.WHITE else Color.BLACK), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(31)))
        addView(iconText(label, 10f, if (selected) Color.WHITE else Color.rgb(48, 56, 50)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)))
        setOnClickListener { action() }
    }

    private fun sideIcon(icon: String, action: () -> Unit): View = iconText(icon, 23f, darkGreen).apply {
        gravity = Gravity.CENTER
        background = solid(Color.rgb(253, 254, 253), 11f, Color.rgb(232, 236, 233))
        setOnClickListener { action() }
    }

    private fun topAction(text: String, action: () -> Unit): View = iconText(text, 24f, darkGreen).apply {
        gravity = Gravity.CENTER
        setOnClickListener { action() }
    }.also { it.layoutParams = LinearLayout.LayoutParams(dp(46), dp(48)) }

    private fun iconText(textValue: String, size: Float, color: Int): TextView = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        includeFontPadding = false
    }

    private fun setupLighting() {
        runCatching {
            skybox = Skybox.Builder().color(0.86f, 0.88f, 0.87f, 1.0f).build(modelViewer.engine)
            modelViewer.scene.skybox = skybox
            addLight(-0.5f, -1.0f, -0.7f, 65000f)
            addLight(0.8f, -0.3f, -0.4f, 35000f)
            addLight(-0.8f, -0.1f, 0.5f, 30000f)
            addLight(0.0f, 0.8f, 0.5f, 22000f)
            addLight(0.0f, -0.2f, 1.0f, 24000f)
            modelViewer.camera.setExposure(16.0f, 1.0f / 125.0f, 100.0f)
        }.onFailure { setStatus("Lighting fallback") }
    }

    private fun addLight(x: Float, y: Float, z: Float, intensity: Float) {
        val entity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 0.99f, 0.97f)
            .intensity(intensity)
            .direction(x, y, z)
            .castShadows(false)
            .build(modelViewer.engine, entity)
        modelViewer.scene.addEntity(entity)
        lightEntities.add(entity)
    }

    private fun showViewPanel() {
        val options = arrayOf("Shaded + Edges", "Texture / Material", "Hidden Line", "Wireframe", "X-Ray", "Opacity")
        AlertDialog.Builder(this)
            .setTitle("Chế độ hiển thị")
            .setSingleChoiceItems(options, when (currentMode) { "SHADED" -> 0; "TEXTURE" -> 1; "HIDDEN" -> 2; "WIREFRAME" -> 3; "X-RAY" -> 4; else -> 5 }) { d, which ->
                currentMode = arrayOf("SHADED", "TEXTURE", "HIDDEN", "WIREFRAME", "X-RAY", "OPACITY")[which]
                setStatus("VIEW: ${options[which].uppercase()}")
                when (which) {
                    0 -> setBackground(0.86f, 0.88f, 0.87f)
                    1 -> setBackground(0.82f, 0.85f, 0.83f)
                    2 -> setBackground(0.96f, 0.97f, 0.96f)
                    3 -> setBackground(0.90f, 0.92f, 0.91f)
                    4 -> setBackground(0.72f, 0.77f, 0.74f)
                    5 -> setBackground(0.88f, 0.91f, 0.89f)
                }
                d.dismiss()
            }.setNegativeButton("Đóng", null).show()
    }

    private fun showDimPanel() {
        AlertDialog.Builder(this).setTitle("DIM / MEASURE")
            .setItems(arrayOf("Đo 2 điểm", "Đo liên tục", "Kích thước tổng", "Góc", "Diện tích", "Xóa đo")) { _, which ->
                val names = arrayOf("ĐO 2 ĐIỂM", "ĐO LIÊN TỤC", "KÍCH THƯỚC TỔNG", "GÓC", "DIỆN TÍCH", "XÓA ĐO")
                setStatus("DIM: ${names[which]}")
                Toast.makeText(this, "Đã chọn ${names[which]}", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun showTagsPanel() {
        val tags = arrayOf("Tất cả", "Thân tủ", "Cánh tủ", "Ngăn kéo", "Hậu tủ", "Phụ kiện")
        val checked = BooleanArray(tags.size) { true }
        AlertDialog.Builder(this).setTitle("Quản lý Tags / Layers")
            .setMultiChoiceItems(tags, checked) { _, _, _ -> }
            .setPositiveButton("Xong") { _, _ -> setStatus("TAGS • Đã cập nhật hiển thị") }
            .show()
    }

    private fun showLightingPanel() {
        AlertDialog.Builder(this).setTitle("Hiển thị")
            .setItems(arrayOf("Nền sáng", "Nền xám", "Nền tối", "Ánh sáng studio", "Bóng đổ mềm")) { _, which ->
                when (which) { 0 -> setBackground(.92f,.93f,.92f); 1 -> setBackground(.72f,.74f,.73f); 2 -> setBackground(.25f,.27f,.26f) }
                setStatus("DISPLAY • ${arrayOf("NỀN SÁNG","NỀN XÁM","NỀN TỐI","STUDIO","BÓNG MỀM")[which]}")
            }.show()
    }

    private fun showInfo() {
        AlertDialog.Builder(this).setTitle("Thông tin mô hình")
            .setMessage("${titleView.text}\n\nECOHOME Viewer v0.1.3\nARM64 • GLB\nAuto rotate Portrait / Landscape\n\nView • Dim • Tags • Info")
            .setPositiveButton("Đóng", null).show()
    }

    private fun showMore() {
        AlertDialog.Builder(this).setItems(arrayOf("Mở model", "FIT model", "View mode", "Ánh sáng", "Thông tin")) { _, which ->
            when (which) { 0 -> openDocument(); 1 -> fitModel(); 2 -> showViewPanel(); 3 -> showLightingPanel(); 4 -> showInfo() }
        }.show()
    }

    private fun setBackground(r: Float, g: Float, b: Float) {
        runCatching {
            skybox?.let { modelViewer.scene.skybox = null; modelViewer.engine.destroySkybox(it) }
            skybox = Skybox.Builder().color(r, g, b, 1f).build(modelViewer.engine)
            modelViewer.scene.skybox = skybox
        }
    }

    private fun setStatus(text: String) { if (::statusView.isInitialized) statusView.text = text }
    private fun fitModel() { runCatching { modelViewer.transformToUnitCube(); setStatus("FIT • Model vừa khung nhìn") } }

    private fun openDocument() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("model/gltf-binary", "model/gltf+json", "application/octet-stream"))
        }
        startActivityForResult(i, OPEN_MODEL)
    }

    @Deprecated("Deprecated in Android framework; kept for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OPEN_MODEL && resultCode == RESULT_OK) data?.data?.let { uri ->
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            lastUri = uri; loadModel(uri)
        }
    }

    private fun loadModel(uri: Uri) {
        try {
            val name = queryName(uri) ?: "Model"
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Không đọc được file")
            modelViewer.destroyModel(); modelViewer.loadModelGlb(ByteBuffer.wrap(bytes)); modelViewer.transformToUnitCube()
            titleView.text = name; setStatus("VIEW: SHADED • ${String.format("%.1f", bytes.size / 1048576.0)} MB")
        } catch (e: Exception) { setStatus("Không mở được model"); Toast.makeText(this, "Không mở được model: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return uri.lastPathSegment
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); lastUri?.let { outState.putString("last_uri", it.toString()) } }
    override fun onResume() { super.onResume(); choreographer.postFrameCallback(frameCallback) }
    override fun onPause() { choreographer.removeFrameCallback(frameCallback); super.onPause() }
    override fun onDestroy() {
        choreographer.removeFrameCallback(frameCallback)
        if (::modelViewer.isInitialized) {
            lightEntities.forEach { e -> runCatching { modelViewer.scene.removeEntity(e) }; runCatching { modelViewer.engine.destroyEntity(e) } }
            skybox?.let { runCatching { modelViewer.engine.destroySkybox(it) } }
            modelViewer.destroyModel()
        }
        super.onDestroy()
    }

    private fun solid(fill: Int, radiusDp: Float, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radiusDp.toInt()).toFloat(); stroke?.let { setStroke(dp(1), it) }
    }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
