package vn.ecohome.viewer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Choreographer
import android.view.Gravity
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
    private val choreographer by lazy { Choreographer.getInstance() }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            if (::modelViewer.isInitialized) modelViewer.render(frameTimeNanos)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        modelViewer = ModelViewer(surfaceView)
        surfaceView.setOnTouchListener(modelViewer)
        intent?.data?.let { loadModel(it) }
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(242, 245, 242)) }
        surfaceView = SurfaceView(this)
        root.addView(surfaceView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.argb(235, 231, 241, 232))
        }

        titleView = TextView(this).apply {
            text = "ECOHOME Viewer"
            textSize = 17f
            setTextColor(Color.rgb(31, 62, 38))
        }
        topBar.addView(titleView, LinearLayout.LayoutParams(0, dp(48), 1f))

        topBar.addView(Button(this).apply {
            text = "MỞ MODEL"
            setOnClickListener { openDocument() }
        })

        topBar.addView(Button(this).apply {
            text = "FIT"
            setOnClickListener { runCatching { modelViewer.transformToUnitCube() } }
        })

        root.addView(topBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ))

        root.addView(TextView(this).apply {
            text = "1 ngón: xoay   •   2 ngón: pan   •   chụm: zoom"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setBackgroundColor(Color.argb(210, 255, 255, 255))
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ))

        setContentView(root)
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
                loadModel(uri)
            }
        }
    }

    private fun loadModel(uri: Uri) {
        try {
            val name = queryName(uri) ?: "Model"
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext != "glb") {
                Toast.makeText(this, "V1 ưu tiên file .glb", Toast.LENGTH_LONG).show()
            }
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Không đọc được file")
            modelViewer.destroyModel()
            modelViewer.loadModelGlb(ByteBuffer.wrap(bytes))
            modelViewer.transformToUnitCube()
            titleView.text = name
        } catch (e: Exception) {
            Toast.makeText(this, "Không mở được model: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment
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
        if (::modelViewer.isInitialized) modelViewer.destroyModel()
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
