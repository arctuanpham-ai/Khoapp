package vn.ecohome.viewer

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Choreographer
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
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
    private var lightEntity: Int? = null
    private var skybox: Skybox? = null
    private var lastUri: Uri? = null
    private var viewModeIndex = 0
    private val viewModes = arrayOf("TEXTURE", "SHADED", "HIDDEN", "X-RAY")

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            if (::modelViewer.isInitialized) modelViewer.render(frameTimeNanos)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(232, 241, 233)
        window.navigationBarColor = Color.rgb(238, 243, 239)
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
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(226, 231, 227)) }

        surfaceView = SurfaceView(this)
        root.addView(surfaceView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(8), dp(6))
            background = rounded(Color.argb(245, 241, 247, 242), 0f)
            elevation = dp(3).toFloat()
        }

        titleView = TextView(this).apply {
            text = "ECOHOME VIEWER"
            textSize = if (landscape) 14f else 16f
            setTextColor(Color.rgb(35, 83, 54))
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
        }
        topBar.addView(titleView, LinearLayout.LayoutParams(0, dp(48), 1f))
        topBar.addView(toolButton("MỞ") { openDocument() })
        topBar.addView(toolButton("FIT") { fitModel() })

        root.addView(topBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(60), Gravity.TOP
        ))

        statusView = TextView(this).apply {
            text = "Sẵn sàng • 1 ngón Orbit • 2 ngón Pan • Chụm Zoom"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(54, 73, 60))
            background = rounded(Color.argb(232, 247, 250, 247), 14f)
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }

        val statusLp = FrameLayout.LayoutParams(
            if (landscape) dp(330) else ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            if (landscape) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.BOTTOM
        ).apply {
            if (landscape) setMargins(dp(76), 0, dp(76), dp(8))
            else setMargins(dp(10), 0, dp(10), dp(72))
        }
        root.addView(statusView, statusLp)

        if (landscape) {
            val tools = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(6), dp(5), dp(6))
                background = rounded(Color.argb(238, 246, 249, 246), 18f)
                elevation = dp(4).toFloat()
            }
            addViewerTools(tools, compact = true)
            root.addView(tools, FrameLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = dp(8)
            })
        } else {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(5), dp(6), dp(5))
            }
            addViewerTools(row, compact = false)
            val scroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                background = rounded(Color.argb(242, 246, 249, 246), 18f)
                elevation = dp(5).toFloat()
                addView(row)
            }
            root.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62), Gravity.BOTTOM).apply {
                setMargins(dp(8), 0, dp(8), dp(8))
            })
        }

        setContentView(root)
    }

    private fun addViewerTools(parent: LinearLayout, compact: Boolean) {
        val names = listOf("HOME", "VIEW", "DIM", "TAGS", "INFO")
        names.forEach { name ->
            val b = toolButton(name) {
                when (name) {
                    "HOME" -> fitModel()
                    "VIEW" -> cycleViewMode()
                    "DIM" -> featureNotice("DIM / MEASURE", "Công cụ Dim đã có vị trí trên UI. Bản metadata tiếp theo sẽ đo và bắt điểm chi tiết.")
                    "TAGS" -> featureNotice("TAGS", "Tags đã có vị trí trên UI. Exporter metadata tiếp theo sẽ đồng bộ Tag từ SketchUp.")
                    "INFO" -> featureNotice("MODEL INFO", "ECOHOME Viewer v0.1.2 • GLB ARM64 • Auto rotate")
                }
            }
            if (compact) {
                parent.addView(b, LinearLayout.LayoutParams(dp(62), dp(46)).apply { setMargins(0, dp(2), 0, dp(2)) })
            } else {
                parent.addView(b, LinearLayout.LayoutParams(dp(76), dp(48)).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
        }
    }

    private fun toolButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 11f
        setTextColor(Color.rgb(34, 77, 49))
        isAllCaps = false
        background = rounded(Color.rgb(239, 247, 241), 12f, Color.rgb(166, 202, 176))
        setPadding(dp(6), 0, dp(6), 0)
        setOnClickListener { action() }
    }

    private fun setupLighting() {
        runCatching {
            skybox = Skybox.Builder()
                .color(0.82f, 0.84f, 0.83f, 1.0f)
                .build(modelViewer.engine)
            modelViewer.scene.skybox = skybox

            val entity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1.0f, 0.97f, 0.92f)
                .intensity(110000.0f)
                .direction(-0.6f, -1.0f, -0.8f)
                .castShadows(true)
                .build(modelViewer.engine, entity)
            modelViewer.scene.addEntity(entity)
            lightEntity = entity
        }.onFailure {
            statusView.text = "Viewer hoạt động • Lighting fallback"
        }
    }

    private fun cycleViewMode() {
        viewModeIndex = (viewModeIndex + 1) % viewModes.size
        val mode = viewModes[viewModeIndex]
        statusView.text = "VIEW: $mode"
        when (mode) {
            "TEXTURE" -> setBackground(0.82f, 0.84f, 0.83f)
            "SHADED" -> setBackground(0.72f, 0.76f, 0.73f)
            "HIDDEN" -> setBackground(0.93f, 0.94f, 0.93f)
            "X-RAY" -> setBackground(0.55f, 0.60f, 0.57f)
        }
        Toast.makeText(this, "$mode • Material override nâng cao sẽ bổ sung sau", Toast.LENGTH_SHORT).show()
    }

    private fun setBackground(r: Float, g: Float, b: Float) {
        runCatching {
            skybox?.let {
                modelViewer.scene.skybox = null
                modelViewer.engine.destroySkybox(it)
            }
            skybox = Skybox.Builder().color(r, g, b, 1.0f).build(modelViewer.engine)
            modelViewer.scene.skybox = skybox
        }
    }

    private fun featureNotice(title: String, message: String) {
        statusView.text = title
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun fitModel() {
        runCatching {
            modelViewer.transformToUnitCube()
            statusView.text = "FIT • Model đã đưa về khung nhìn"
        }
    }

    private fun openDocument() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "model/gltf-binary",
                "model/gltf+json",
                "application/octet-stream"
            ))
        }
        startActivityForResult(i, OPEN_MODEL)
    }

    @Deprecated("Deprecated in Android framework; kept for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OPEN_MODEL && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                lastUri = uri
                loadModel(uri)
            }
        }
    }

    private fun loadModel(uri: Uri) {
        try {
            val name = queryName(uri) ?: "Model"
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext != "glb") Toast.makeText(this, "Hiện ưu tiên file .glb", Toast.LENGTH_LONG).show()
            statusView.text = "Đang mở $name…"
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Không đọc được file")
            modelViewer.destroyModel()
            modelViewer.loadModelGlb(ByteBuffer.wrap(bytes))
            modelViewer.transformToUnitCube()
            titleView.text = name
            statusView.text = "Đã mở • ${bytes.size / 1024 / 1024.0} MB • Orbit / Pan / Zoom"
        } catch (e: Exception) {
            statusView.text = "Không mở được model"
            Toast.makeText(this, "Không mở được model: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastUri?.let { outState.putString("last_uri", it.toString()) }
    }

    override fun onResume() {
        super.onResume()
        choreographer.postFrameCallback(frameCallback)
    }

    override fun onPause() {
        choreographer.removeFrameCallback(frameCallback)
        super.onPause()
    }

    override fun onDestroy() {
        choreographer.removeFrameCallback(frameCallback)
        if (::modelViewer.isInitialized) {
            lightEntity?.let { entity ->
                runCatching { modelViewer.scene.removeEntity(entity) }
                runCatching { modelViewer.engine.destroyEntity(entity) }
            }
            skybox?.let { runCatching { modelViewer.engine.destroySkybox(it) } }
            modelViewer.destroyModel()
        }
        super.onDestroy()
    }

    private fun rounded(fill: Int, radiusDp: Float, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
