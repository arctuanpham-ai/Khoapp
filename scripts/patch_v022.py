from pathlib import Path
import re
p=Path('app/src/main/java/vn/ecohome/viewer/MainActivity.kt'); s=p.read_text()

# Magnetic DIM state.
anchor='    private var dimSnapView: TextView?=null\n'
extra='''    private var dimSnapView: TextView?=null
    private var magneticSnapScreenX:Float?=null
    private var magneticSnapScreenY:Float?=null
    private var magneticSnapWorld:DoubleArray?=null
    private var magneticSnapLabel=""
    private var magneticSnapLocked=false
'''
if anchor in s:s=s.replace(anchor,extra,1)
else:raise SystemExit('magnetic state anchor missing')

# During DIM movement continuously probe snap candidates and move the crosshair to the snapped screen coordinate.
s=s.replace('''MotionEvent.ACTION_MOVE -> {holdX=event.x;holdY=event.y;if(precisionHold)showMagnifier(holdX,holdY);true}''','''MotionEvent.ACTION_MOVE -> {
                        holdX=event.x;holdY=event.y
                        if(precisionHold) updateMagneticDimSnap(event.x,event.y)
                        true
                    }''',1)
s=s.replace('''if(dimMode&&longPressArmed&&!precisionHold){precisionHold=true;showMagnifier(holdX,holdY);setStatus("DIM • rê tâm ngắm xanh • nhả để chốt")}''','''if(dimMode&&longPressArmed&&!precisionHold){precisionHold=true;updateMagneticDimSnap(holdX,holdY);setStatus("DIM • rê gần điểm • con trỏ sẽ tự hút") }''',1)
# Release uses snapped screen point when locked, so the actual View.pick and DIM coordinate agree with the visual target.
s=s.replace('''if(precisionHold){hideMagnifier();precisionHold=false;pickDimPoint(holdX,holdY)}else pickDimPoint(event.x,event.y);true''','''if(precisionHold){
                            val px=magneticSnapScreenX?:holdX;val py=magneticSnapScreenY?:holdY
                            hideMagnifier();precisionHold=false;pickDimPoint(px,py);clearMagneticDimSnap()
                        }else pickDimPoint(event.x,event.y);true''',1)
s=s.replace('''MotionEvent.ACTION_CANCEL -> {longPressArmed=false;precisionHold=false;hideMagnifier();true}''','''MotionEvent.ACTION_CANCEL -> {longPressArmed=false;precisionHold=false;hideMagnifier();clearMagneticDimSnap();true}''',1)

# Insert realtime pick + snap visual. Uses current board snapDimPoint; attraction radius is larger than final lock radius.
anchor2='    private fun showMagnifier(x: Float, y: Float) {'
helpers='''    private fun updateMagneticDimSnap(fingerX:Float,fingerY:Float){
        modelViewer.view.pick(fingerX.toInt().coerceAtLeast(0),(surfaceView.height-fingerY.toInt()).coerceAtLeast(0),surfaceView.handler){result->
            val surface=if(result.renderable!=0)unproject(result.fragCoords) else null
            if(result.renderable==0||surface==null){
                magneticSnapLocked=false;magneticSnapScreenX=null;magneticSnapScreenY=null;magneticSnapWorld=null;magneticSnapLabel=""
                showMagnifier(fingerX,fingerY);return@pick
            }
            val candidate=snapDimPointMagnetic(result.renderable,fingerX,fingerY,surface)
            val screen=worldToScreen(candidate.first)
            val canLock=candidate.second!="Mặt"&&screen!=null
            if(canLock){
                magneticSnapLocked=true;magneticSnapWorld=candidate.first;magneticSnapLabel=candidate.second
                magneticSnapScreenX=screen!!.first;magneticSnapScreenY=screen.second
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
        var best:Pair<DoubleArray,String>?=null;var bestD=Double.MAX_VALUE
        candidates.forEach{cand->
            val sp=worldToScreen(cand.first)?:return@forEach;val dx=sp.first-sx;val dy=sp.second-sy;val d=sqrt((dx*dx+dy*dy).toDouble())
            val acquire=when(cand.second){"Đỉnh"->dp(52).toDouble();"Trung điểm"->dp(42).toDouble();else->dp(32).toDouble()}
            val release=acquire+dp(18)
            val limit=if(magneticSnapLocked&&cand.second==magneticSnapLabel)release else acquire
            if(d<limit&&d<bestD){bestD=d;best=cand}
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

'''+anchor2
if anchor2 in s:s=s.replace(anchor2,helpers,1)
else:raise SystemExit('showMagnifier anchor missing')

s=s.replace('ECOHOME Viewer v0.2.1','ECOHOME Viewer v0.2.2')
p.write_text(s)
print('v0.2.2 magnetic DIM patch applied')
