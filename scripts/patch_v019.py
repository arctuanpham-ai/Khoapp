from pathlib import Path
import re
p=Path('app/src/main/java/vn/ecohome/viewer/MainActivity.kt'); s=p.read_text()

# Imports for magnifier overlay crosshair.
if 'import android.graphics.Canvas\n' not in s:
    s=s.replace('import android.graphics.Color\n','import android.graphics.Canvas\nimport android.graphics.Color\nimport android.graphics.ColorFilter\nimport android.graphics.Paint\nimport android.graphics.PixelFormat\n')
if 'import android.graphics.drawable.Drawable\n' not in s:
    s=s.replace('import android.graphics.drawable.GradientDrawable\n','import android.graphics.drawable.Drawable\nimport android.graphics.drawable.GradientDrawable\n')

# Add synthetic pan state used to reverse two-finger translation cleanly.
anchor='    private var navLastSpan=0f\n'
extra='''    private var navLastSpan=0f
    private var navLastMidX=0f
    private var navLastMidY=0f
    private var navSyntheticX=0f
    private var navSyntheticY=0f
'''
if anchor in s:
    s=s.replace(anchor,extra,1)

# Replace the v0.1.8 touch controller so Pan and Zoom directions match finger motion.
pat=r'    private fun installSmartTouchController\(\) \{.*?\n    \}\n\n    private fun showMagnifier'
rep='''    private fun installSmartTouchController() {
        surfaceView.setOnTouchListener { _, event ->
            if (dimMode) {
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownX=event.x; touchDownY=event.y; holdX=event.x; holdY=event.y; longPressArmed=true
                        surfaceView.postDelayed({
                            if(dimMode&&longPressArmed&&!precisionHold){
                                precisionHold=true; showMagnifier(holdX,holdY)
                                setStatus("DIM • rê tâm ngắm xanh • nhả để chốt")
                            }
                        },300)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        holdX=event.x; holdY=event.y
                        if(precisionHold) showMagnifier(holdX,holdY)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        longPressArmed=false
                        if(precisionHold){hideMagnifier();precisionHold=false;pickDimPoint(holdX,holdY)}
                        else pickDimPoint(event.x,event.y)
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {longPressArmed=false;precisionHold=false;hideMagnifier();true}
                    else -> true
                }
            } else {
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        navTwoFinger=false; navGestureActive=true
                        cameraManipulator.grabBegin(event.x.toInt(),event.y.toInt(),false)
                        true
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if(event.pointerCount>=2){
                            if(navGestureActive) cameraManipulator.grabEnd()
                            val dx=event.getX(1)-event.getX(0); val dy=event.getY(1)-event.getY(0)
                            navLastSpan=sqrt(dx*dx+dy*dy)
                            navLastMidX=(event.getX(0)+event.getX(1))*0.5f
                            navLastMidY=(event.getY(0)+event.getY(1))*0.5f
                            navSyntheticX=navLastMidX; navSyntheticY=navLastMidY
                            cameraManipulator.grabBegin(navSyntheticX.toInt(),navSyntheticY.toInt(),true)
                            navTwoFinger=true; navGestureActive=true
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if(navTwoFinger&&event.pointerCount>=2){
                            val mx=(event.getX(0)+event.getX(1))*0.5f
                            val my=(event.getY(0)+event.getY(1))*0.5f
                            val dx=event.getX(1)-event.getX(0); val dy=event.getY(1)-event.getY(0)
                            val span=sqrt(dx*dx+dy*dy).coerceAtLeast(1f)
                            val ratio=span/navLastSpan.coerceAtLeast(1f)
                            if(kotlin.math.abs(ratio-1f)<0.025f){
                                // Reverse manipulator translation so the model follows the two fingers naturally.
                                navSyntheticX -= (mx-navLastMidX)
                                navSyntheticY -= (my-navLastMidY)
                                cameraManipulator.grabUpdate(navSyntheticX.toInt(),navSyntheticY.toInt())
                                setStatus("PAN • kéo 2 ngón theo hướng muốn dịch")
                            } else {
                                // Reverse v0.1.8 zoom direction: fingers apart = zoom in, fingers together = zoom out.
                                val delta=(-(ratio-1f)*42f).coerceIn(-2.2f,2.2f)
                                cameraManipulator.scroll(mx.toInt(),my.toInt(),delta)
                                navLastSpan=span
                                cameraManipulator.grabEnd()
                                navSyntheticX=mx; navSyntheticY=my
                                cameraManipulator.grabBegin(navSyntheticX.toInt(),navSyntheticY.toInt(),true)
                                setStatus("ZOOM • dang ra = vào gần • khép lại = ra xa")
                            }
                            navLastMidX=mx; navLastMidY=my
                        } else if(event.pointerCount==1&&navGestureActive){
                            cameraManipulator.grabUpdate(event.x.toInt(),event.y.toInt())
                        }
                        true
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        if(navTwoFinger){cameraManipulator.grabEnd();navTwoFinger=false;navGestureActive=false}
                        true
                    }
                    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
                        if(navGestureActive)cameraManipulator.grabEnd()
                        navGestureActive=false;navTwoFinger=false;true
                    }
                    else -> true
                }
            }
        }
    }

    private fun showMagnifier'''
s,n=re.subn(pat,rep,s,count=1,flags=re.S)
if n!=1: raise SystemExit('touch controller patch failed')

# Magnifier now has a green target/crosshair overlay centered inside the loupe.
pat2=r'    private fun showMagnifier\(x: Float, y: Float\) \{.*?\n    \}\n\n    private fun hideMagnifier'
rep2='''    private fun showMagnifier(x: Float, y: Float) {
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

    private fun hideMagnifier'''
s,n=re.subn(pat2,rep2,s,count=1,flags=re.S)
if n!=1: raise SystemExit('magnifier patch failed')

s=s.replace('ECOHOME Viewer v0.1.8','ECOHOME Viewer v0.1.9')
p.write_text(s)
print('v0.1.9 patch applied')
