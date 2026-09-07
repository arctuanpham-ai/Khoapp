package vn.ecohome.viewer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Skybox
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Manipulator
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import kotlin.math.sqrt
import kotlin.math.roundToInt

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

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
    private lateinit var cameraManipulator: Manipulator

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
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var longPressArmed = false
    private var dimScreen1: Pair<Float, Float>? = null
    private val dimMarkerViews = mutableListOf<View>()
    private var navGestureActive=false
    private var navTwoFinger=false
    private var navLastSpan=0f
    private var navLastMidX=0f
    private var navLastMidY=0f
    private var navSyntheticX=0f
    private var navSyntheticY=0f
    private var navOneLastX=0f
    private var navOneLastY=0f
    private var navOneSyntheticX=0f
    private var navOneSyntheticY=0f
    private var navDownX=0f
    private var navDownY=0f
    private var navDownTime=0L
    private var navMoved=false
    private var selectedEntity=0
    private val selectedOriginalMaterials=mutableListOf<MaterialInstance>()
    private val selectedHighlightMaterials=mutableListOf<MaterialInstance>()
    private var entityInfoView: TextView?=null
    private var zoomWindowMode=false
    private var zoomWinStartX=0f
    private var zoomWinStartY=0f
    private var zoomWinRect: View?=null
    private var dimSnapView: TextView?=null
    private var magneticSnapScreenX:Float?=null
    private var magneticSnapScreenY:Float?=null
    private var magneticSnapWorld:DoubleArray?=null
    private var magneticSnapLabel=""
    private var magneticSnapLocked=false
    private var dimDiagonalMode=false
    private var dimModeChip: TextView?=null
    private var snapGeneration=0L
    private var lastSnapRequestMs=0L
    private val tagVisible=mutableMapOf<String,Boolean>()
    private val viewOriginalMaterials=mutableMapOf<String,MaterialInstance>()
    private val viewTempMaterials=mutableListOf<MaterialInstance>()
    private var lastDimAxisName="X"
    private var completedDim: Triple<DoubleArray,DoubleArray,Boolean>? = null
    private var worldOverlay: View? = null
    private var lastSnapEntity = 0
    private var perspectiveCamera = true
    private var cameraFov = 45.0
    private var orthoHalfHeight = 1.5
    private val hiddenByTags = mutableSetOf<Int>()

    private fun applyCameraProjection() {
        if(surfaceView.width<=0 || surfaceView.height<=0)return
        val aspect=surfaceView.width.toDouble()/surfaceView.height
        if(perspectiveCamera) modelViewer.camera.setProjection(cameraFov,aspect,0.01,1000.0,com.google.android.filament.Camera.Fov.VERTICAL)
        else modelViewer.camera.setProjection(com.google.android.filament.Camera.Projection.ORTHO,-orthoHalfHeight*aspect,orthoHalfHeight*aspect,-orthoHalfHeight,orthoHalfHeight,0.01,1000.0)
    }

    private fun showCameraPanel() {
        val column=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(10),dp(20),dp(10))}
        val mode=android.widget.Switch(this).apply{text="Perspective (tắt = Parallel Projection)";isChecked=perspectiveCamera
            setOnCheckedChangeListener{_,checked->perspectiveCamera=checked;applyCameraProjection()}}
        column.addView(mode)
        val label=TextView(this).apply{text="Field of View dọc: ${cameraFov.roundToInt()}°"};column.addView(label)
        column.addView(android.widget.SeekBar(this).apply{max=110;progress=cameraFov.roundToInt()-10
            setOnSeekBarChangeListener(object:android.widget.SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(bar:android.widget.SeekBar?,value:Int,user:Boolean){cameraFov=(value+10).toDouble();label.text="Field of View dọc: ${cameraFov.roundToInt()}°";applyCameraProjection()}
                override fun onStartTrackingTouch(bar:android.widget.SeekBar?){}
                override fun onStopTrackingTouch(bar:android.widget.SeekBar?){}
            })})
        AlertDialog.Builder(this).setTitle("Camera").setView(column).setPositiveButton("Xong",null).show()
    }

    private fun installWorldOverlay() {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        worldOverlay=object:View(this){
            override fun onDraw(canvas:Canvas){
                fun point(p:DoubleArray,n:String){val v=worldToScreen(p)?:return
                    paint.color=green;paint.style=Paint.Style.FILL;canvas.drawCircle(v.first,v.second,dp(9).toFloat(),paint)
                    paint.color=Color.WHITE;paint.textSize=dp(11).toFloat();paint.textAlign=Paint.Align.CENTER;canvas.drawText(n,v.first,v.second+dp(4),paint)}
                val dim=completedDim
                if(dim==null){dimPoint1?.let{point(it,"1")};return}
                val a=dim.first;val b=dim.second;val diagonal=dim.third
                val axis=(0..2).maxByOrNull{kotlin.math.abs(b[it]-a[it])}?:0
                val end=if(diagonal)b else a.copyOf().also{it[axis]=b[axis]}
                val u=worldToScreen(a)?:return;val v=worldToScreen(end)?:return;val w=worldToScreen(b)?:return
                paint.color=green;paint.strokeWidth=dp(2).toFloat();canvas.drawLine(u.first,u.second,v.first,v.second,paint)
                paint.strokeWidth=dp(1).toFloat();canvas.drawLine(v.first,v.second,w.first,w.second,paint)
                point(a,"1");point(b,"2")
                val length=if(diagonal)distance(a,b) else kotlin.math.abs(b[axis]-a[axis])
                val mm=(length*sourceMaxExtentMeters*500.0).roundToInt()
                val text="${if(diagonal)"↗" else arrayOf("X","Y","Z")[axis]} $mm mm"
                val x=(u.first+v.first)/2;val y=(u.second+v.second)/2-dp(20)
                paint.textSize=dp(13).toFloat();paint.textAlign=Paint.Align.CENTER
                val half=paint.measureText(text)/2+dp(12);paint.color=Color.WHITE
                canvas.drawRoundRect(x-half,y-dp(19),x+half,y+dp(9),dp(10).toFloat(),dp(10).toFloat(),paint)
                paint.color=darkGreen;canvas.drawText(text,x,y,paint)
            }
        }.also{root.addView(it,1,FrameLayout.LayoutParams(-1,-1));it.isClickable=false}
    }

    private fun applyTagVisibility() {
        val asset=modelViewer.asset?:return;val tm=modelViewer.engine.transformManager;val rm=modelViewer.engine.renderableManager
        hiddenByTags.clear()
        for(entity in asset.entities){
            val ri=rm.getInstance(entity);if(ri==0)continue
            var current=entity;var visible=true;val visited=mutableSetOf<Int>()
            while(current!=0 && visited.add(current)){
                val raw=asset.getName(current).orEmpty()
                val tag=parseEcoMetadata(raw)?.optString("tag")?.takeIf{it.isNotBlank()}?:parseTag(raw)
                if(tag!=null && tagVisible[tag]==false)visible=false
                val ti=tm.getInstance(current);current=if(ti==0)0 else tm.getParent(ti)
            }
            rm.setLayerMask(ri,255,if(visible)255 else 0)
            if(!visible)hiddenByTags.add(entity)
        }
    }


    private val green = Color.rgb(55, 151, 91)
    private val darkGreen = Color.rgb(25, 77, 51)
    private val panel = Color.argb(248, 252, 253, 252)
    private val border = Color.rgb(214, 227, 218)

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            if (::modelViewer.isInitialized) {
                applyCameraProjection()
                modelViewer.render(frameTimeNanos)
                worldOverlay?.invalidate()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(244, 248, 245)
        window.navigationBarColor = Color.rgb(248, 250, 248)
        buildUi()
        cameraManipulator = Manipulator.Builder()
            .targetPosition(0.0f, 0.0f, -4.0f)
            .viewport(surfaceView.width.coerceAtLeast(1), surfaceView.height.coerceAtLeast(1))
            .zoomSpeed(0.018f)
            .orbitSpeed(0.004f, 0.004f)
            .build(Manipulator.Mode.ORBIT)
        modelViewer = ModelViewer(surfaceView, manipulator = cameraManipulator)
        installWorldOverlay()
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
            Triple("⛶", "Extents") { fitModel() },
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
        surfaceView.setOnTouchListener { _, event ->
            if(!dimMode) return@setOnTouchListener handleNavigationTouch(event,true)
            when(event.actionMasked){
                MotionEvent.ACTION_DOWN->{
                    touchDownX=event.x;touchDownY=event.y;holdX=event.x;holdY=event.y;longPressArmed=true
                    if(dimPoint1!=null)handleNavigationTouch(event,false)
                    surfaceView.postDelayed({
                        if(dimMode&&longPressArmed&&!precisionHold){
                            if(navGestureActive)runCatching{cameraManipulator.grabEnd()}
                            navGestureActive=false;navTwoFinger=false;precisionHold=true
                            snapGeneration++;updateMagneticDimSnap(holdX,holdY)
                            setStatus("DIM ${if(dimDiagonalMode)"CHÉO" else "XYZ"} • kính lúp + snap • nhả để chốt ${if(dimPoint1==null)"P1" else "P2"}")
                        }
                    },320)
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN->{
                    if(dimPoint1!=null&&!precisionHold){longPressArmed=false;handleNavigationTouch(event,false)}
                    true
                }
                MotionEvent.ACTION_MOVE->{
                    holdX=event.x;holdY=event.y
                    if(precisionHold){updateMagneticDimSnap(event.x,event.y)}
                    else if(dimPoint1!=null){
                        if(event.pointerCount>1)longPressArmed=false else {
                            val dx=event.x-touchDownX;val dy=event.y-touchDownY
                            if(dx*dx+dy*dy>dp(9)*dp(9))longPressArmed=false
                        }
                        handleNavigationTouch(event,false)
                    }else{
                        val dx=event.x-touchDownX;val dy=event.y-touchDownY
                        if(dx*dx+dy*dy>dp(12)*dp(12))longPressArmed=false
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_UP->{if(dimPoint1!=null&&!precisionHold)handleNavigationTouch(event,false);true}
                MotionEvent.ACTION_UP->{
                    longPressArmed=false
                    if(precisionHold){
                        val px=magneticSnapScreenX?:holdX;val py=magneticSnapScreenY?:holdY
                        hideMagnifier();precisionHold=false;dimSnapView?.visibility=View.GONE
                        snapGeneration++;pickDimPoint(px,py);clearMagneticDimSnap()
                    }else if(dimPoint1!=null){
                        handleNavigationTouch(event,false)
                        setStatus("DIM ${if(dimDiagonalMode)"CHÉO" else "XYZ"} • P1 ✓ • Orbit/Pan/Zoom tự do • giữ để bắt P2")
                    }else setStatus("DIM ${if(dimDiagonalMode)"CHÉO" else "XYZ"} • giữ trên bề mặt để bắt P1")
                    true
                }
                MotionEvent.ACTION_CANCEL->{
                    longPressArmed=false;precisionHold=false;snapGeneration++;hideMagnifier();dimSnapView?.visibility=View.GONE;clearMagneticDimSnap()
                    if(navGestureActive)runCatching{cameraManipulator.grabEnd()};navGestureActive=false;navTwoFinger=false;true
                }
                else->true
            }
        }
    }

    private fun handleNavigationTouch(event:MotionEvent,allowSelect:Boolean):Boolean{
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{
                navTwoFinger=false;navGestureActive=true;navMoved=false
                navDownX=event.x;navDownY=event.y;navDownTime=System.currentTimeMillis()
                navOneLastX=event.x;navOneLastY=event.y;navOneSyntheticX=event.x;navOneSyntheticY=event.y
                cameraManipulator.grabBegin(navOneSyntheticX.toInt(),navOneSyntheticY.toInt(),false);return true
            }
            MotionEvent.ACTION_POINTER_DOWN->{
                if(event.pointerCount>=2){
                    if(navGestureActive)runCatching{cameraManipulator.grabEnd()}
                    val dx=event.getX(1)-event.getX(0);val dy=event.getY(1)-event.getY(0);navLastSpan=sqrt(dx*dx+dy*dy).coerceAtLeast(1f)
                    navLastMidX=(event.getX(0)+event.getX(1))*0.5f;navLastMidY=(event.getY(0)+event.getY(1))*0.5f
                    navSyntheticX=navLastMidX;navSyntheticY=navLastMidY
                    cameraManipulator.grabBegin(navSyntheticX.toInt(),navSyntheticY.toInt(),true);navTwoFinger=true;navGestureActive=true;navMoved=true
                };return true
            }
            MotionEvent.ACTION_MOVE->{
                if(navTwoFinger&&event.pointerCount>=2){
                    val mx=(event.getX(0)+event.getX(1))*0.5f;val my=(event.getY(0)+event.getY(1))*0.5f
                    val dx=event.getX(1)-event.getX(0);val dy=event.getY(1)-event.getY(0);val span=sqrt(dx*dx+dy*dy).coerceAtLeast(1f);val ratio=span/navLastSpan.coerceAtLeast(1f)
                    if(kotlin.math.abs(ratio-1f)<0.022f){
                        navSyntheticX+=(mx-navLastMidX);navSyntheticY+=(my-navLastMidY);cameraManipulator.grabUpdate(navSyntheticX.toInt(),navSyntheticY.toInt());setStatus("PAN • 2 ngón kéo song song")
                    }else{
                        // Pinch midpoint is the focal zoom position requested for workshop navigation.
                        if(!perspectiveCamera)orthoHalfHeight=(orthoHalfHeight/ratio).coerceIn(0.001,1000.0)
                        val delta=(-(ratio-1f)*46f).coerceIn(-2.5f,2.5f);cameraManipulator.scroll(mx.toInt(),my.toInt(),delta)
                        navLastSpan=span;runCatching{cameraManipulator.grabEnd()};navSyntheticX=mx;navSyntheticY=my;cameraManipulator.grabBegin(mx.toInt(),my.toInt(),true)
                        setStatus("ZOOM • tâm = giữa 2 ngón")
                    }
                    navLastMidX=mx;navLastMidY=my
                }else if(event.pointerCount==1&&navGestureActive){
                    val dx=event.x-navOneLastX;val dy=event.y-navOneLastY
                    if(kotlin.math.abs(event.x-navDownX)>dp(5)||kotlin.math.abs(event.y-navDownY)>dp(5))navMoved=true
                    navOneSyntheticX+=dx;navOneSyntheticY+=dy;cameraManipulator.grabUpdate(navOneSyntheticX.toInt(),navOneSyntheticY.toInt())
                    navOneLastX=event.x;navOneLastY=event.y
                };return true
            }
            MotionEvent.ACTION_POINTER_UP->{if(navTwoFinger){runCatching{cameraManipulator.grabEnd()};navTwoFinger=false;navGestureActive=false};return true}
            MotionEvent.ACTION_UP->{
                if(navGestureActive)runCatching{cameraManipulator.grabEnd()}
                val wasTap=!navMoved&&System.currentTimeMillis()-navDownTime<450;navGestureActive=false;navTwoFinger=false
                if(allowSelect&&wasTap)pickEntity(event.x,event.y);return true
            }
            MotionEvent.ACTION_CANCEL->{if(navGestureActive)runCatching{cameraManipulator.grabEnd()};navGestureActive=false;navTwoFinger=false;return true}
        }
        return true
    }

    private fun showZoomWindowRect(x1:Float,y1:Float,x2:Float,y2:Float){
        val left=kotlin.math.min(x1,x2).roundToInt();val top=kotlin.math.min(y1,y2).roundToInt()
        val w=kotlin.math.max(dp(2),kotlin.math.abs(x2-x1).roundToInt());val h=kotlin.math.max(dp(2),kotlin.math.abs(y2-y1).roundToInt())
        val v=zoomWinRect?:View(this).apply{background=GradientDrawable().apply{setColor(Color.argb(32,55,151,91));setStroke(dp(2),green)}}.also{zoomWinRect=it;root.addView(it)}
        val lp=FrameLayout.LayoutParams(w,h);lp.leftMargin=left;lp.topMargin=top;v.layoutParams=lp;v.visibility=View.VISIBLE;v.bringToFront()
    }
    private fun hideZoomWindowRect(){zoomWinRect?.visibility=View.GONE}
    private fun applyZoomWindow(x1:Float,y1:Float,x2:Float,y2:Float){
        val w=kotlin.math.abs(x2-x1);val h=kotlin.math.abs(y2-y1);if(w<dp(30)||h<dp(30)){setStatus("ZOOM WINDOW • vùng quá nhỏ");return}
        val cx=(x1+x2)*0.5f;val cy=(y1+y2)*0.5f;val frac=kotlin.math.max(w/surfaceView.width.toFloat(),h/surfaceView.height.toFloat()).coerceIn(0.08f,0.95f)
        cameraManipulator.scroll(cx.toInt(),cy.toInt(),-((1f/frac)-1f).coerceIn(0.25f,5.5f));setStatus("ZOOM WINDOW • đã phóng vùng chọn")
    }

    private fun updateMagneticDimSnap(fingerX:Float,fingerY:Float){
        val now=System.currentTimeMillis()
        if(now-lastSnapRequestMs<34L){
            val sx=magneticSnapScreenX;val sy=magneticSnapScreenY
            if(magneticSnapLocked&&sx!=null&&sy!=null)showMagnifier(sx,sy) else showMagnifier(fingerX,fingerY)
            return
        }
        lastSnapRequestMs=now
        val request=++snapGeneration
        modelViewer.view.pick(fingerX.toInt().coerceAtLeast(0),(surfaceView.height-fingerY.toInt()).coerceAtLeast(0),surfaceView.handler){result->
            if(request!=snapGeneration||!precisionHold)return@pick
            if(result.renderable!=0 && result.renderable !in hiddenByTags)lastSnapEntity=result.renderable
            val entity=if(result.renderable!=0)result.renderable else lastSnapEntity
            val surface=if(result.renderable!=0)unproject(result.fragCoords) else magneticSnapWorld?:doubleArrayOf(0.0,0.0,0.0)
            if(entity==0||entity in hiddenByTags||surface==null){
                magneticSnapLocked=false;magneticSnapScreenX=null;magneticSnapScreenY=null;magneticSnapWorld=null;magneticSnapLabel=""
                dimSnapView?.visibility=View.GONE;showMagnifier(fingerX,fingerY);return@pick
            }
            val candidate=snapDimPointMagnetic(entity,fingerX,fingerY,surface)
            val screen=worldToScreen(candidate.first)
            if(candidate.second!="Mặt"&&screen!=null){
                magneticSnapLocked=true;magneticSnapWorld=candidate.first;magneticSnapLabel=candidate.second
                magneticSnapScreenX=screen.first;magneticSnapScreenY=screen.second
                showMagnifier(screen.first,screen.second);showMagneticSnapBadge(candidate.second,screen.first,screen.second)
            }else{
                magneticSnapLocked=false;magneticSnapScreenX=null;magneticSnapScreenY=null;magneticSnapWorld=null;magneticSnapLabel=""
                dimSnapView?.visibility=View.GONE;showMagnifier(fingerX,fingerY)
            }
        }
    }

    private fun snapDimPointMagnetic(entity:Int,sx:Float,sy:Float,fallback:DoubleArray):Pair<DoubleArray,String>{
        val rm=modelViewer.engine.renderableManager;val ri=rm.getInstance(entity);if(ri==0)return fallback to "Mặt"
        val tm=modelViewer.engine.transformManager;val ti=tm.getInstance(entity);if(ti==0)return fallback to "Mặt"
        val box=rm.getAxisAlignedBoundingBox(ri,null);val c=box.center;val h=box.halfExtent
        val mat=tm.getWorldTransform(ti,null as FloatArray?).map{it.toDouble()}.toDoubleArray()
        val corners=mutableListOf<DoubleArray>()
        for(ix in intArrayOf(-1,1))for(iy in intArrayOf(-1,1))for(iz in intArrayOf(-1,1))corners.add(transformPoint(mat,doubleArrayOf((c[0]+ix*h[0]).toDouble(),(c[1]+iy*h[1]).toDouble(),(c[2]+iz*h[2]).toDouble())))
        val candidates=mutableListOf<Pair<DoubleArray,String>>()
        corners.forEach{c0->candidates.add(c0 to "Đỉnh")}
        val edges=arrayOf(0 to 4,1 to 5,2 to 6,3 to 7,0 to 2,1 to 3,4 to 6,5 to 7,0 to 1,2 to 3,4 to 5,6 to 7)
        edges.forEach{(a,b)->candidates.add(mid3(corners[a],corners[b]) to "Trung điểm")}
        val faces=arrayOf(intArrayOf(0,1,2,3),intArrayOf(4,5,6,7),intArrayOf(0,1,4,5),intArrayOf(2,3,6,7),intArrayOf(0,2,4,6),intArrayOf(1,3,5,7))
        faces.forEach{ids->candidates.add(avg3(ids.map{corners[it]}) to "Tâm mặt")}
        edges.forEach{(a,b)->
            val u=worldToScreen(corners[a]);val v=worldToScreen(corners[b])
            if(u!=null&&v!=null){
                val dx=v.first-u.first;val dy=v.second-u.second;val len=dx*dx+dy*dy
                if(len>1f){
                    val t=(((sx-u.first)*dx+(sy-u.second)*dy)/len).toDouble().coerceIn(0.0,1.0)
                    val pv=mulMat4(modelViewer.camera.getProjectionMatrix(null as DoubleArray?),modelViewer.camera.getViewMatrix(null as DoubleArray?))
                    val wa=mul4(pv,corners[a]+doubleArrayOf(1.0))[3];val wb=mul4(pv,corners[b]+doubleArrayOf(1.0))[3]
                    val denom=(1-t)/wa+t/wb
                    if(kotlin.math.abs(denom)>1e-9){
                        val k=(t/wb)/denom
                        candidates.add(DoubleArray(3){i->corners[a][i]+k*(corners[b][i]-corners[a][i])} to "Cạnh hộp")
                    }
                }
            }
        }
        var best:Pair<DoubleArray,String>?=null;var bestD=Double.MAX_VALUE
        candidates.forEach{cand->
            val sp=worldToScreen(cand.first)?:return@forEach;val dx=sp.first-sx;val dy=sp.second-sy;val d=sqrt((dx*dx+dy*dy).toDouble())
            val acquire=when(cand.second){"Đỉnh"->dp(60).toDouble();"Trung điểm"->dp(50).toDouble();else->dp(38).toDouble()}
            val release=acquire+dp(18)
            val limit=if(magneticSnapLocked&&magneticSnapWorld?.let{distance(it,cand.first)<1e-6}==true)release else acquire
            val score=d+if(cand.second=="Cạnh hộp")dp(14) else 0
            if(d<limit&&score<bestD){bestD=score;best=cand}
        }
        return best ?: (fallback to "Mặt")
    }

    private fun showMagneticSnapBadge(label:String,x:Float,y:Float){
        val pair=magneticSnapWorld?.let{it to label}?:return
        showDimSnap(pair,x,y)
        setStatus("DIM • HÚT ${when(label){"Đỉnh"->"ĐỈNH";"Trung điểm"->"TRUNG ĐIỂM";"Tâm mặt"->"TÂM MẶT";else->label}}")
    }
    private fun clearMagneticDimSnap(){
        magneticSnapLocked=false;magneticSnapScreenX=null;magneticSnapScreenY=null;magneticSnapWorld=null;magneticSnapLabel=""
    }

    private fun showMagnifier(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (magnifier == null) {
            val targetOverlay = object : Drawable() {
                private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = green
                    strokeWidth = dp(2).toFloat()
                    style = Paint.Style.STROKE
                }
                override fun draw(canvas: Canvas) {
                    val cx=bounds.exactCenterX(); val cy=bounds.exactCenterY()
                    val arm=dp(16).toFloat(); val gap=dp(5).toFloat()
                    canvas.drawLine(cx-arm,cy,cx-gap,cy,p)
                    canvas.drawLine(cx+gap,cy,cx+arm,cy,p)
                    canvas.drawLine(cx,cy-arm,cx,cy-gap,p)
                    canvas.drawLine(cx,cy+gap,cx,cy+arm,p)
                    canvas.drawCircle(cx,cy,dp(4).toFloat(),p)
                }
                override fun setAlpha(alpha: Int) { p.alpha=alpha }
                override fun setColorFilter(colorFilter: ColorFilter?) { p.colorFilter=colorFilter }
                @Deprecated("Deprecated in Java")
                override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
            }
            magnifier = Magnifier.Builder(surfaceView)
                .setSize(dp(150), dp(100))
                .setInitialZoom(2.8f)
                .setOverlay(targetOverlay)
                .build()
        }
        runCatching { magnifier?.show(x, y) }
    }

    private fun hideMagnifier() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { magnifier?.dismiss() } }

    private fun showDimSnap(snapped:Pair<DoubleArray,String>,sx:Float,sy:Float){
        if(snapped.second.isBlank()||snapped.second=="Mặt"){dimSnapView?.visibility=View.GONE;return}
        val v=dimSnapView?:TextView(this).apply{textSize=11f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;setPadding(dp(7),dp(3),dp(7),dp(3));background=solid(green,12f)}.also{dimSnapView=it;root.addView(it)}
        v.text=when(snapped.second){"Đỉnh"->"● ĐỈNH";"Trung điểm"->"◆ MID";"Tâm mặt"->"⊙ TÂM";else->snapped.second}
        val lp=FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(28));lp.leftMargin=(sx+dp(14)).roundToInt();lp.topMargin=(sy-dp(34)).roundToInt();v.layoutParams=lp;v.visibility=View.VISIBLE;v.bringToFront()
    }

    private fun showDimModeChip(){
        val v=dimModeChip?:TextView(this).apply{
            textSize=11f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;setPadding(dp(10),dp(5),dp(10),dp(5));background=solid(green,14f)
            setOnClickListener{dimDiagonalMode=!dimDiagonalMode;text=if(dimDiagonalMode)"DIM CHÉO" else "DIM XYZ";setStatus(if(dimDiagonalMode)"DIM CHÉO • đo khoảng cách thực" else "DIM XYZ • tự gióng theo trục X/Y/Z")}
        }.also{dimModeChip=it;root.addView(it,FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(34),Gravity.TOP or Gravity.START).apply{leftMargin=dp(125);topMargin=dp(72)})}
        v.text=if(dimDiagonalMode)"DIM CHÉO" else "DIM XYZ";v.visibility=View.VISIBLE;v.bringToFront()
    }
    private fun hideDimModeChip(){dimModeChip?.visibility=View.GONE}

    private fun showWitnessLine(x1:Float,y1:Float,x2:Float,y2:Float){
        val dx=x2-x1;val dy=y2-y1;val len=sqrt(dx*dx+dy*dy).roundToInt().coerceAtLeast(1)
        val line=View(this).apply{setBackgroundColor(Color.argb(150,55,151,91));pivotX=0f;pivotY=dp(1).toFloat();rotation=Math.toDegrees(Math.atan2(dy.toDouble(),dx.toDouble())).toFloat();elevation=dp(5).toFloat()}
        root.addView(line,FrameLayout.LayoutParams(len,dp(1)).apply{leftMargin=x1.roundToInt();topMargin=y1.roundToInt()});dimMarkerViews.add(line)
    }

    private fun clearDimOverlay() {
        completedDim=null
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

    private fun pickEntity(x: Float, y: Float) {
        val asset=modelViewer.asset?:return
        modelViewer.view.pick(x.toInt().coerceAtLeast(0),(surfaceView.height-y.toInt()).coerceAtLeast(0),surfaceView.handler){result->
            if(result.renderable==0){clearSelection();setStatus("SELECT • Không trúng model");return@pick}
            val raw=runCatching{asset.getName(result.renderable)}.getOrNull().orEmpty()
            val clean=cleanNodeName(raw).ifBlank{"Chi tiết"}
            val tag=parseTag(raw)
            highlightEntity(result.renderable)
            showEntityInfoV3(result.renderable,raw,clean,tag)
            setStatus("SELECT • $clean${if(tag!=null)" • Tag: $tag" else ""}")
        }
    }

    private fun clearSelection(){
        if(selectedEntity!=0){
            val rm=modelViewer.engine.renderableManager;val ri=rm.getInstance(selectedEntity)
            if(ri!=0)selectedOriginalMaterials.forEachIndexed{i,m->runCatching{rm.setMaterialInstanceAt(ri,i,m)}}
        }
        selectedOriginalMaterials.clear();selectedHighlightMaterials.clear();selectedEntity=0
        entityInfoView?.visibility=View.GONE
    }

    private fun highlightEntity(entity:Int){
        clearSelection();selectedEntity=entity
        val rm=modelViewer.engine.renderableManager;val ri=rm.getInstance(entity);if(ri==0)return
        val count=rm.getPrimitiveCount(ri)
        for(i in 0 until count){
            val original=rm.getMaterialInstanceAt(ri,i);selectedOriginalMaterials.add(original)
            val hi=MaterialInstance.duplicate(original,"ECOHOME_SELECTED_${entity}_$i")
            runCatching{if(hi.material.hasParameter("baseColorFactor"))hi.setParameter("baseColorFactor",0.34f,1.0f,0.46f,1.0f)}
            selectedHighlightMaterials.add(hi);rm.setMaterialInstanceAt(ri,i,hi)
        }
    }

    private fun parseEcoMetadata(raw:String):JSONObject?{
        val a=raw.indexOf("__ECO_META__");val b=raw.indexOf("__ECO_NAME__")
        if(a<0||b<=a)return null
        return runCatching{
            var enc=raw.substring(a+"__ECO_META__".length,b)
            while(enc.length%4!=0)enc+="="
            val compressed=android.util.Base64.decode(enc,android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
            val inflater=Inflater();inflater.setInput(compressed);val out=ByteArrayOutputStream();val buf=ByteArray(4096)
            while(!inflater.finished()){val n=inflater.inflate(buf);if(n<=0){if(inflater.needsInput()||inflater.needsDictionary())break}else out.write(buf,0,n)}
            inflater.end();JSONObject(out.toString("UTF-8"))
        }.getOrNull()
    }

    private fun showEntityInfoV3(entity:Int,raw:String,name:String,legacyTag:String?){
        val meta=parseEcoMetadata(raw)
        val text=if(meta!=null){
            val inst=meta.optString("instance").ifBlank{name};val def=meta.optString("definition")
            val tag=meta.optString("tag").ifBlank{legacyTag?:""};val mat=meta.optString("material")
            val x=meta.optDouble("x_mm",Double.NaN);val y=meta.optDouble("y_mm",Double.NaN);val z=meta.optDouble("z_mm",Double.NaN)
            val l=meta.optDouble("l_mm",Double.NaN);val w=meta.optDouble("w_mm",Double.NaN);val t=meta.optDouble("t_mm",Double.NaN)
            buildString{
                append("TẤM ĐANG CHỌN\n");append("Instance: $inst\n");if(def.isNotBlank())append("Definition: $def\n")
                if(l.isFinite()&&w.isFinite()&&t.isFinite())append("Kích thước L×W×T: ${l.roundToInt()} × ${w.roundToInt()} × ${t.roundToInt()} mm\n")
                if(x.isFinite()&&y.isFinite()&&z.isFinite())append("XYZ local: ${x.roundToInt()} × ${y.roundToInt()} × ${z.roundToInt()} mm\n")
                if(tag.isNotBlank())append("Tag: $tag\n");append("Vật liệu: ${mat.ifBlank{"Chưa gán"}}")
                val pid=meta.optLong("persistent_id",0L);if(pid!=0L)append("\nPID: $pid")
                val prod=meta.optJSONObject("prod");if(prod!=null&&prod.length()>0){
                    val keys=prod.keys();var shown=0;while(keys.hasNext()&&shown<4){val k=keys.next();append("\n${k.substringAfterLast('.')}: ${prod.opt(k)}");shown++}
                }
            }
        }else{
            "TẤM ĐANG CHỌN\nInstance: $name\nKích thước: cần GLB từ ECOHOME Exporter v0.3.1\nTag: ${legacyTag?:"—"}\nVật liệu: cần metadata Exporter"
        }
        val v=entityInfoView?:TextView(this).apply{textSize=12f;setTextColor(darkGreen);typeface=Typeface.DEFAULT_BOLD;setPadding(dp(12),dp(9),dp(12),dp(9));background=solid(Color.argb(245,252,255,252),14f,green);elevation=dp(8).toFloat()}.also{entityInfoView=it;root.addView(it,FrameLayout.LayoutParams(dp(310),ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.TOP or Gravity.END).apply{topMargin=dp(70);marginEnd=dp(12)})}
        v.text=text;v.visibility=View.VISIBLE;v.bringToFront()
    }

    private fun showEntityInfo(entity:Int,name:String,tag:String?){
        val rm=modelViewer.engine.renderableManager
        val ri=rm.getInstance(entity)
        if(ri==0)return
        val box=rm.getAxisAlignedBoundingBox(ri,null)
        val h=box.halfExtent
        val dims=listOf(h[0]*2f,h[1]*2f,h[2]*2f).map{(it*1000f).roundToInt()}.sortedDescending()
        val mat=runCatching{if(rm.getPrimitiveCount(ri)>0)rm.getMaterialInstanceAt(ri,0).name else ""}.getOrDefault("")
        val text=buildString{
            append("TẤM ĐANG CHỌN\n")
            append("Instance: $name\n")
            append("Kích thước: ${dims.getOrElse(0){0}} × ${dims.getOrElse(1){0}} × ${dims.getOrElse(2){0}} mm\n")
            if(tag!=null)append("Tag: $tag\n")
            append("Vật liệu: ${mat.ifBlank{"GLB material"}}")
        }
        val v=entityInfoView?:TextView(this).apply{
            textSize=12f
            setTextColor(darkGreen)
            typeface=Typeface.DEFAULT_BOLD
            setPadding(dp(12),dp(9),dp(12),dp(9))
            background=solid(Color.argb(245,252,255,252),14f,green)
            elevation=dp(8).toFloat()
        }.also{
            entityInfoView=it
            root.addView(it,FrameLayout.LayoutParams(dp(285),ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.TOP or Gravity.END).apply{
                topMargin=dp(70);marginEnd=dp(12)
            })
        }
        v.text=text
        v.visibility=View.VISIBLE
        v.bringToFront()
    }

    private fun commitDimWorld(world:DoubleArray) {
        if(dimPoint1==null){clearDimOverlay();dimPoint1=world.copyOf();setStatus("DIM • P1 ✓ • di chuyển camera rồi giữ để bắt P2");return}
        completedDim=Triple(dimPoint1!!.copyOf(),world.copyOf(),dimDiagonalMode)
        dimPoint1=null;dimPoint2=null;dimMode=false;hideDimModeChip();hideMagnifier()
        setStatus("DIM • Đã neo hai điểm vào model 3D")
    }

    private fun pickDimPoint(x:Float,y:Float){
        val locked=magneticSnapWorld?.copyOf()
        if(locked!=null){commitDimWorld(locked);return}
        modelViewer.view.pick(x.toInt(),(surfaceView.height-y).toInt(),surfaceView.handler){result->
            if(result.renderable==0){setStatus("DIM • Chưa bắt được điểm");return@pick}
            val surface=unproject(result.fragCoords)?:return@pick
            commitDimWorld(surface)
        }
    }

    private fun unproject(frag: FloatArray): DoubleArray? {
        if(frag.size<3||surfaceView.width<=0||surfaceView.height<=0)return null
        val proj=modelViewer.camera.getProjectionMatrix(null as DoubleArray?)
        val view=modelViewer.camera.getViewMatrix(null as DoubleArray?)
        val inv=invert4(mulMat4(proj,view))?:return null
        val nx=frag[0].toDouble()/surfaceView.width*2.0-1.0
        val ny=frag[1].toDouble()/surfaceView.height*2.0-1.0
        for(z in doubleArrayOf(frag[2].toDouble()*2.0-1.0,frag[2].toDouble())){
            val q=mul4(inv,doubleArrayOf(nx,ny,z,1.0))
            if(kotlin.math.abs(q[3])>1e-9&&q.all{it.isFinite()})return doubleArrayOf(q[0]/q[3],q[1]/q[3],q[2]/q[3])
        }
        return null
    }
    private fun mulMat4(a:DoubleArray,b:DoubleArray):DoubleArray{val r=DoubleArray(16);for(c in 0..3)for(row in 0..3){var v=0.0;for(k in 0..3)v+=a[k*4+row]*b[c*4+k];r[c*4+row]=v};return r}

    private fun snapDimPoint(entity:Int,sx:Float,sy:Float,fallback:DoubleArray):Pair<DoubleArray,String>{
        val rm=modelViewer.engine.renderableManager;val ri=rm.getInstance(entity);if(ri==0)return fallback to ""
        val tm=modelViewer.engine.transformManager;val ti=tm.getInstance(entity);if(ti==0)return fallback to ""
        val box=rm.getAxisAlignedBoundingBox(ri,null);val c=box.center;val h=box.halfExtent
        val mat=tm.getWorldTransform(ti,null as FloatArray?).map{it.toDouble()}.toDoubleArray()
        val corners=mutableListOf<DoubleArray>()
        for(ix in intArrayOf(-1,1))for(iy in intArrayOf(-1,1))for(iz in intArrayOf(-1,1)){
            corners.add(transformPoint(mat,doubleArrayOf((c[0]+ix*h[0]).toDouble(),(c[1]+iy*h[1]).toDouble(),(c[2]+iz*h[2]).toDouble())))
        }
        val candidates=mutableListOf<Pair<DoubleArray,String>>()
        corners.forEach{c0->candidates.add(c0 to "Đỉnh")}
        val edges=arrayOf(0 to 4,1 to 5,2 to 6,3 to 7,0 to 2,1 to 3,4 to 6,5 to 7,0 to 1,2 to 3,4 to 5,6 to 7)
        edges.forEach{(a,b)->candidates.add(mid3(corners[a],corners[b]) to "Trung điểm")}
        val faces=arrayOf(intArrayOf(0,1,2,3),intArrayOf(4,5,6,7),intArrayOf(0,1,4,5),intArrayOf(2,3,6,7),intArrayOf(0,2,4,6),intArrayOf(1,3,5,7))
        faces.forEach{ids->candidates.add(avg3(ids.map{corners[it]}) to "Tâm mặt")}
        var best:Pair<DoubleArray,String>?=null;var bestD=Double.MAX_VALUE
        candidates.forEach{cand->
            val p=worldToScreen(cand.first)?:return@forEach
            val dx=p.first-sx;val dy=p.second-sy;val d=sqrt((dx*dx+dy*dy).toDouble())
            val limit=when(cand.second){"Đỉnh"->dp(30).toDouble();"Trung điểm"->dp(22).toDouble();else->dp(16).toDouble()}
            if(d<limit&&d<bestD){bestD=d;best=cand}
        }
        return best ?: (fallback to "Mặt")
    }

    private fun transformPoint(m:DoubleArray,p:DoubleArray):DoubleArray{
        val q=mul4(m,doubleArrayOf(p[0],p[1],p[2],1.0));val w=if(kotlin.math.abs(q[3])<1e-9)1.0 else q[3]
        return doubleArrayOf(q[0]/w,q[1]/w,q[2]/w)
    }
    private fun mid3(a:DoubleArray,b:DoubleArray)=doubleArrayOf((a[0]+b[0])/2.0,(a[1]+b[1])/2.0,(a[2]+b[2])/2.0)
    private fun avg3(v:List<DoubleArray>)=doubleArrayOf(v.map{it[0]}.average(),v.map{it[1]}.average(),v.map{it[2]}.average())
    private fun worldToScreen(p:DoubleArray):Pair<Float,Float>?{
        val proj=modelViewer.camera.getProjectionMatrix(null as DoubleArray?);val view=modelViewer.camera.getViewMatrix(null as DoubleArray?)
        val q=mul4(mulMat4(proj,view),doubleArrayOf(p[0],p[1],p[2],1.0));if(q[3]<=1e-9 || q[2]/q[3] < -1.0 || q[2]/q[3] > 1.0)return null
        val nx=q[0]/q[3];val ny=q[1]/q[3]
        return ((nx*0.5+0.5)*surfaceView.width).toFloat() to ((1.0-(ny*0.5+0.5))*surfaceView.height).toFloat()
    }

    private fun axisDistance(a:DoubleArray,b:DoubleArray):Double{
        val dx=kotlin.math.abs(b[0]-a[0]);val dy=kotlin.math.abs(b[1]-a[1]);val dz=kotlin.math.abs(b[2]-a[2])
        if(dimDiagonalMode){lastDimAxisName="↗";return sqrt(dx*dx+dy*dy+dz*dz)}
        return if(dx>=dy&&dx>=dz){lastDimAxisName="X";dx}else if(dy>=dz){lastDimAxisName="Y";dy}else{lastDimAxisName="Z";dz}
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
        val options=arrayOf("Material / Texture","Shaded sáng (ánh sáng)","Technical contrast (ánh sáng)","X-Ray beta","Camera • Perspective / Parallel / FOV")
        val selected=when(currentMode){"TEXTURE"->0;"SHADED"->1;"TECH"->2;"X-RAY"->3;else->0}
        AlertDialog.Builder(this).setTitle("Chế độ hiển thị").setSingleChoiceItems(options,selected){d,which->
            if(which==4){d.dismiss();showCameraPanel();return@setSingleChoiceItems}
            clearSelection();restoreViewMaterials()
            when(which){
                0->{currentMode="TEXTURE";indirectLight?.intensity=26000f;setBackground(.90f,.92f,.91f);modelViewer.camera.setExposure(16f,1f/125f,100f)}
                1->{currentMode="SHADED";indirectLight?.intensity=43000f;setBackground(.93f,.95f,.93f);modelViewer.camera.setExposure(16f,1f/100f,100f)}
                2->{currentMode="TECH";indirectLight?.intensity=15000f;setBackground(.98f,.98f,.97f);modelViewer.camera.setExposure(16f,1f/160f,100f)}
                3->{currentMode="X-RAY";indirectLight?.intensity=30000f;setBackground(.82f,.86f,.83f);applyXrayMaterials()}
            }
            setStatus("VIEW • ${options[which].uppercase()}");d.dismiss()
        }.setNegativeButton("Đóng",null).show()
    }

    private fun restoreViewMaterials(){
        val asset=modelViewer.asset?:return;val rm=modelViewer.engine.renderableManager
        for(e in asset.entities){val ri=rm.getInstance(e);if(ri==0)continue;for(i in 0 until rm.getPrimitiveCount(ri)){viewOriginalMaterials["$e:$i"]?.let{runCatching{rm.setMaterialInstanceAt(ri,i,it)}}}}
        viewTempMaterials.clear()
    }

    private fun applyXrayMaterials(){
        val asset=modelViewer.asset?:return;val rm=modelViewer.engine.renderableManager
        for(e in asset.entities){
            val ri=rm.getInstance(e);if(ri==0)continue
            for(i in 0 until rm.getPrimitiveCount(ri)){
                val key="$e:$i";val original=rm.getMaterialInstanceAt(ri,i);viewOriginalMaterials.putIfAbsent(key,original)
                val mi=MaterialInstance.duplicate(original,"ECOHOME_XRAY_${e}_$i")
                runCatching{if(mi.material.hasParameter("baseColorFactor"))mi.setParameter("baseColorFactor",0.62f,0.90f,0.72f,0.30f)}
                runCatching{if(mi.material.hasParameter("transmissionFactor"))mi.setParameter("transmissionFactor",0.72f)}
                runCatching{if(mi.material.hasParameter("roughnessFactor"))mi.setParameter("roughnessFactor",0.80f)}
                viewTempMaterials.add(mi);rm.setMaterialInstanceAt(ri,i,mi)
            }
        }
    }

    private fun showDimPanel() {
        if(modelViewer.asset==null){Toast.makeText(this,"Hãy mở model trước",Toast.LENGTH_SHORT).show();return}
        AlertDialog.Builder(this).setTitle("DIM / MEASURE").setItems(arrayOf("DIM theo trục X / Y / Z","DIM chéo","Xóa DIM","Kích thước tổng model")){_,which->
            when(which){
                0->{clearDimOverlay();dimDiagonalMode=false;dimMode=true;dimPoint1=null;dimPoint2=null;hideDimModeChip();setStatus("DIM XYZ • giữ P1 để bật kính lúp + snap")}
                1->{clearDimOverlay();dimDiagonalMode=true;dimMode=true;dimPoint1=null;dimPoint2=null;hideDimModeChip();setStatus("DIM CHÉO • giữ P1 để bật kính lúp + snap")}
                2->{dimMode=false;dimPoint1=null;dimPoint2=null;precisionHold=false;hideMagnifier();hideDimModeChip();clearDimOverlay();setStatus("DIM • Đã xóa")}
                3->showBoundingDimensions()
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
        val asset=modelViewer.asset?:run{Toast.makeText(this,"Hãy mở model trước",Toast.LENGTH_SHORT).show();return}
        val groups=linkedMapOf<String,MutableList<Int>>()
        for(e in asset.entities){
            val raw=runCatching{asset.getName(e)}.getOrNull().orEmpty()
            val tag=parseEcoMetadata(raw)?.optString("tag")?.takeIf{it.isNotBlank()} ?: parseTag(raw) ?: continue
            groups.getOrPut(tag){mutableListOf()}.add(e)
        }
        if(groups.isEmpty()){
            AlertDialog.Builder(this).setTitle("Tags SketchUp").setMessage("Không tìm thấy Tag metadata trong GLB. Hãy xuất bằng ECOHOME Viewer Exporter v0.3.1.").setPositiveButton("Đóng",null).show();return
        }
        val names=groups.keys.sorted().toTypedArray();val labels=names.map{"$it  (${groups[it]?.size?:0})"}.toTypedArray()
        val checked=BooleanArray(names.size){i->tagVisible.getOrPut(names[i]){true}}
        AlertDialog.Builder(this).setTitle("Tags SketchUp • ${names.size} tags").setMultiChoiceItems(labels,checked){_,which,on->
            val tag=names[which];tagVisible[tag]=on
            applyTagVisibility()
            setStatus("TAGS • $tag ${if(on)"ON" else "OFF"}")
        }.setPositiveButton("Xong",null).show()
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
        AlertDialog.Builder(this).setTitle("Thông tin mô hình").setMessage("${titleView.text}\n\nECOHOME Viewer v0.3.1\nARM64 • GLB\nAuto gesture: 1 ngón Orbit • 2 ngón Pan • Pinch Zoom$dim\n\nTap: chọn chi tiết\nDIM: ấn giữ + kính lúp precision pick\nTags: đọc metadata SketchUp từ ECOHOME Exporter").setPositiveButton("Đóng", null).show()
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

    private fun resetView() {
        dimMode = false; dimPoint1 = null; dimPoint2 = null; clearDimOverlay(); tagVisible.clear(); applyTagVisibility(); viewOriginalMaterials.clear(); viewTempMaterials.clear(); hideDimModeChip()
        fitModel()
        setStatus("AUTO • 1 ngón Orbit • 2 ngón Pan • Pinch Zoom")
    }

    private fun fitModel() {
        runCatching {
            modelViewer.cameraFocalLength = 28f
            modelViewer.transformToUnitCube()
            orthoHalfHeight=1.5
            cameraManipulator.jumpToBookmark(cameraManipulator.homeBookmark)
            setStatus("EXTENTS • Đã đưa toàn bộ model về khung nhìn")
        }.onFailure {
            setStatus("EXTENTS • Không reset được camera")
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
            setStatus("Đang mở $name…")
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Không đọc được file")
            tagVisible.clear();hiddenByTags.clear();lastSnapEntity=0;clearMagneticDimSnap()
            modelViewer.destroyModel(); modelViewer.loadModelGlb(ByteBuffer.wrap(bytes)); titleView.text = name
            modelViewer.asset?.boundingBox?.halfExtent?.let { h -> sourceMaxExtentMeters = maxOf(h[0], h[1], h[2]).toDouble() * 2.0 }
            dimMode = false; dimPoint1 = null; dimPoint2 = null; clearDimOverlay()
            surfaceView.postDelayed({ if(modelViewer.asset!=null) setStatus("Model sẵn sàng • dùng Extents khi cần") },120)
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

