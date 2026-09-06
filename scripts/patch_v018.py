from pathlib import Path
import re
p=Path('app/src/main/java/vn/ecohome/viewer/MainActivity.kt'); s=p.read_text()
s=s.replace('.zoomSpeed(0.006f)', '.zoomSpeed(0.018f)')
anchor='    private val dimMarkerViews = mutableListOf<View>()\n'
extra='''    private val dimMarkerViews = mutableListOf<View>()
    private var navGestureActive=false
    private var navTwoFinger=false
    private var navLastSpan=0f
'''
if anchor in s:s=s.replace(anchor,extra,1)
pat=r'    private fun installSmartTouchController\(\) \{.*?\n    \}\n\n    private fun showMagnifier'
rep='''    private fun installSmartTouchController() {
        surfaceView.setOnTouchListener { _, event ->
            if (dimMode) {
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { touchDownX=event.x; touchDownY=event.y; holdX=event.x; holdY=event.y; longPressArmed=true; surfaceView.postDelayed({ if(dimMode&&longPressArmed&&!precisionHold){precisionHold=true;showMagnifier(holdX,holdY);setStatus("DIM • rê tâm ngắm • nhả để chốt")}},300); true }
                    MotionEvent.ACTION_MOVE -> {holdX=event.x;holdY=event.y;if(precisionHold)showMagnifier(holdX,holdY);true}
                    MotionEvent.ACTION_UP -> {longPressArmed=false;if(precisionHold){hideMagnifier();precisionHold=false;pickDimPoint(holdX,holdY)}else pickDimPoint(event.x,event.y);true}
                    MotionEvent.ACTION_CANCEL -> {longPressArmed=false;precisionHold=false;hideMagnifier();true}
                    else -> true
                }
            } else {
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {navTwoFinger=false;navGestureActive=true;cameraManipulator.grabBegin(event.x.toInt(),event.y.toInt(),false);true}
                    MotionEvent.ACTION_POINTER_DOWN -> {if(event.pointerCount>=2){if(navGestureActive)cameraManipulator.grabEnd();val dx=event.getX(1)-event.getX(0);val dy=event.getY(1)-event.getY(0);navLastSpan=sqrt(dx*dx+dy*dy);val mx=(event.getX(0)+event.getX(1))*0.5f;val my=(event.getY(0)+event.getY(1))*0.5f;cameraManipulator.grabBegin(mx.toInt(),my.toInt(),true);navTwoFinger=true;navGestureActive=true};true}
                    MotionEvent.ACTION_MOVE -> {if(navTwoFinger&&event.pointerCount>=2){val mx=(event.getX(0)+event.getX(1))*0.5f;val my=(event.getY(0)+event.getY(1))*0.5f;val dx=event.getX(1)-event.getX(0);val dy=event.getY(1)-event.getY(0);val span=sqrt(dx*dx+dy*dy).coerceAtLeast(1f);val ratio=span/navLastSpan.coerceAtLeast(1f);if(kotlin.math.abs(ratio-1f)<0.025f){cameraManipulator.grabUpdate(mx.toInt(),my.toInt());setStatus("PAN • kéo 2 ngón song song")}else{val delta=((ratio-1f)*42f).coerceIn(-2.2f,2.2f);cameraManipulator.scroll(mx.toInt(),my.toInt(),delta);navLastSpan=span;cameraManipulator.grabEnd();cameraManipulator.grabBegin(mx.toInt(),my.toInt(),true);setStatus("ZOOM • pinch")}}else if(event.pointerCount==1&&navGestureActive)cameraManipulator.grabUpdate(event.x.toInt(),event.y.toInt());true}
                    MotionEvent.ACTION_POINTER_UP -> {if(navTwoFinger){cameraManipulator.grabEnd();navTwoFinger=false;navGestureActive=false};true}
                    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {if(navGestureActive)cameraManipulator.grabEnd();navGestureActive=false;navTwoFinger=false;true}
                    else -> true
                }
            }
        }
    }

    private fun showMagnifier'''
s,n=re.subn(pat,rep,s,count=1,flags=re.S)
if n!=1:raise SystemExit('touch patch failed')
pat2=r'    private fun unproject\(frag: FloatArray\): DoubleArray\? \{.*?\n    \}\n\n    private fun distance'
rep2='''    private fun unproject(frag: FloatArray): DoubleArray? {
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

    private fun distance'''
s,n=re.subn(pat2,rep2,s,count=1,flags=re.S)
if n!=1:raise SystemExit('unproject patch failed')
s=s.replace('ECOHOME Viewer v0.1.7','ECOHOME Viewer v0.1.8')
p.write_text(s);print('v0.1.8 patch applied')
