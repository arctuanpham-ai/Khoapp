from pathlib import Path
import re
p=Path('app/src/main/java/vn/ecohome/viewer/MainActivity.kt'); s=p.read_text()

# Imports for material highlight.
if 'import com.google.android.filament.MaterialInstance\n' not in s:
    s=s.replace('import com.google.android.filament.LightManager\n','import com.google.android.filament.LightManager\nimport com.google.android.filament.MaterialInstance\n')

# Navigation + selection state.
anchor='    private var navSyntheticY=0f\n'
extra='''    private var navSyntheticY=0f
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
'''
if anchor in s:
    s=s.replace(anchor,extra,1)
else:
    raise SystemExit('state anchor missing')

# Replace touch controller: invert orbit, reverse pan from v0.1.9, retain pinch separation.
pat=r'    private fun installSmartTouchController\(\) \{.*?\n    \}\n\n    private fun showMagnifier'
rep='''    private fun installSmartTouchController() {
        surfaceView.setOnTouchListener { _, event ->
            if (dimMode) {
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownX=event.x; touchDownY=event.y; holdX=event.x; holdY=event.y; longPressArmed=true
                        surfaceView.postDelayed({ if(dimMode&&longPressArmed&&!precisionHold){precisionHold=true;showMagnifier(holdX,holdY);setStatus("DIM • rê tâm ngắm xanh • nhả để chốt")}},300)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {holdX=event.x;holdY=event.y;if(precisionHold)showMagnifier(holdX,holdY);true}
                    MotionEvent.ACTION_UP -> {longPressArmed=false;if(precisionHold){hideMagnifier();precisionHold=false;pickDimPoint(holdX,holdY)}else pickDimPoint(event.x,event.y);true}
                    MotionEvent.ACTION_CANCEL -> {longPressArmed=false;precisionHold=false;hideMagnifier();true}
                    else -> true
                }
            } else {
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        navTwoFinger=false;navGestureActive=true;navMoved=false
                        navDownX=event.x;navDownY=event.y;navDownTime=System.currentTimeMillis()
                        navOneLastX=event.x;navOneLastY=event.y
                        navOneSyntheticX=event.x;navOneSyntheticY=event.y
                        cameraManipulator.grabBegin(navOneSyntheticX.toInt(),navOneSyntheticY.toInt(),false)
                        true
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if(event.pointerCount>=2){
                            if(navGestureActive)cameraManipulator.grabEnd()
                            val dx=event.getX(1)-event.getX(0);val dy=event.getY(1)-event.getY(0)
                            navLastSpan=sqrt(dx*dx+dy*dy)
                            navLastMidX=(event.getX(0)+event.getX(1))*0.5f;navLastMidY=(event.getY(0)+event.getY(1))*0.5f
                            navSyntheticX=navLastMidX;navSyntheticY=navLastMidY
                            cameraManipulator.grabBegin(navSyntheticX.toInt(),navSyntheticY.toInt(),true)
                            navTwoFinger=true;navGestureActive=true;navMoved=true
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if(navTwoFinger&&event.pointerCount>=2){
                            val mx=(event.getX(0)+event.getX(1))*0.5f;val my=(event.getY(0)+event.getY(1))*0.5f
                            val dx=event.getX(1)-event.getX(0);val dy=event.getY(1)-event.getY(0)
                            val span=sqrt(dx*dx+dy*dy).coerceAtLeast(1f);val ratio=span/navLastSpan.coerceAtLeast(1f)
                            if(kotlin.math.abs(ratio-1f)<0.025f){
                                // v0.2.0: pan sign corrected again after device test.
                                navSyntheticX += (mx-navLastMidX);navSyntheticY += (my-navLastMidY)
                                cameraManipulator.grabUpdate(navSyntheticX.toInt(),navSyntheticY.toInt())
                                setStatus("PAN • 2 ngón kéo song song")
                            }else{
                                val delta=(-(ratio-1f)*42f).coerceIn(-2.2f,2.2f)
                                cameraManipulator.scroll(mx.toInt(),my.toInt(),delta)
                                navLastSpan=span;cameraManipulator.grabEnd();navSyntheticX=mx;navSyntheticY=my
                                cameraManipulator.grabBegin(navSyntheticX.toInt(),navSyntheticY.toInt(),true)
                                setStatus("ZOOM • dang ra = vào gần • khép lại = ra xa")
                            }
                            navLastMidX=mx;navLastMidY=my
                        }else if(event.pointerCount==1&&navGestureActive){
                            val dx=event.x-navOneLastX;val dy=event.y-navOneLastY
                            if(kotlin.math.abs(event.x-navDownX)>dp(5)||kotlin.math.abs(event.y-navDownY)>dp(5))navMoved=true
                            // Orbit direction inverted to follow the requested SketchUp Viewer behavior.
                            navOneSyntheticX -= dx;navOneSyntheticY -= dy
                            cameraManipulator.grabUpdate(navOneSyntheticX.toInt(),navOneSyntheticY.toInt())
                            navOneLastX=event.x;navOneLastY=event.y
                        }
                        true
                    }
                    MotionEvent.ACTION_POINTER_UP -> {if(navTwoFinger){cameraManipulator.grabEnd();navTwoFinger=false;navGestureActive=false};true}
                    MotionEvent.ACTION_UP -> {
                        if(navGestureActive)cameraManipulator.grabEnd()
                        val wasTap=!navMoved && System.currentTimeMillis()-navDownTime<450
                        navGestureActive=false;navTwoFinger=false
                        if(wasTap)pickEntity(event.x,event.y)
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {if(navGestureActive)cameraManipulator.grabEnd();navGestureActive=false;navTwoFinger=false;true}
                    else -> true
                }
            }
        }
    }

    private fun showMagnifier'''
s,n=re.subn(pat,rep,s,count=1,flags=re.S)
if n!=1: raise SystemExit('touch controller patch failed')

# Replace entity pick with real highlight + inspector.
pat2=r'    private fun pickEntity\(x: Float, y: Float\) \{.*?\n    \}\n\n    private fun pickDimPoint'
rep2='''    private fun pickEntity(x: Float, y: Float) {
        val asset=modelViewer.asset?:return
        modelViewer.view.pick(x.toInt().coerceAtLeast(0),(surfaceView.height-y.toInt()).coerceAtLeast(0),surfaceView.handler){result->
            if(result.renderable==0){clearSelection();setStatus("SELECT • Không trúng model");return@pick}
            val raw=runCatching{asset.getName(result.renderable)}.getOrNull().orEmpty()
            val clean=cleanNodeName(raw).ifBlank{"Chi tiết"}
            val tag=parseTag(raw)
            highlightEntity(result.renderable)
            showEntityInfo(result.renderable,clean,tag)
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
            val hi=MaterialInstance.duplicate(original,"ECOHOME_SELECTED_$entity_$i")
            runCatching{if(hi.material.hasParameter("baseColorFactor"))hi.setParameter("baseColorFactor",0.34f,1.0f,0.46f,1.0f)}
            selectedHighlightMaterials.add(hi);rm.setMaterialInstanceAt(ri,i,hi)
        }
    }

    private fun showEntityInfo(entity:Int,name:String,tag:String?){
        val rm=modelViewer.engine.renderableManager;val ri=rm.getInstance(entity);if(ri==0)return
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
            textSize=12f;setTextColor(darkGreen);typeface=Typeface.DEFAULT_BOLD
            setPadding(dp(12),dp(9),dp(12),dp(9));background=solid(Color.argb(245,252,255,252),14f,green);elevation=dp(8).toFloat()
        }.also{entityInfoView=it;root.addView(it,FrameLayout.LayoutParams(dp(285),ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.TOP or Gravity.END).apply{topMargin=dp(70);marginEnd=dp(12)})}
        v.text=text;v.visibility=View.VISIBLE;v.bringToFront()
    }

    private fun pickDimPoint'''
s,n=re.subn(pat2,rep2,s,count=1,flags=re.S)
if n!=1: raise SystemExit('pickEntity patch failed')

# DIM snapping using picked board AABB: corners, edge midpoints, face centers.
old='''            val world = unproject(result.fragCoords)
            if (world == null) {
                setStatus("DIM • Đã bắt bề mặt nhưng chưa giải được tọa độ 3D")
                return@pick
            }
            if (dimPoint1 == null) {'''
new='''            val surfaceWorld=unproject(result.fragCoords)
            if(surfaceWorld==null){setStatus("DIM • Đã bắt bề mặt nhưng chưa giải được tọa độ 3D");return@pick}
            val snapped=snapDimPoint(result.renderable,x,y,surfaceWorld)
            val world=snapped.first
            if (dimPoint1 == null) {'''
if old not in s: raise SystemExit('dim world anchor missing')
s=s.replace(old,new,1)
s=s.replace('setStatus("DIM • Điểm 1 ✓ • ấn giữ để chọn điểm 2")','setStatus("DIM • Điểm 1 ✓${if(snapped.second.isNotBlank())" • ${snapped.second}" else ""} • chọn điểm 2")',1)
s=s.replace('setStatus("DIM • $mm mm")','setStatus("DIM • $mm mm${if(snapped.second.isNotBlank())" • ${snapped.second}" else ""}")',1)

# Insert snap helpers before distance().
anchor2='    private fun distance(a: DoubleArray, b: DoubleArray): Double {'
helpers='''    private fun snapDimPoint(entity:Int,sx:Float,sy:Float,fallback:DoubleArray):Pair<DoubleArray,String>{
        val rm=modelViewer.engine.renderableManager;val ri=rm.getInstance(entity);if(ri==0)return fallback to ""
        val tm=modelViewer.engine.transformManager;val ti=tm.getInstance(entity);if(ti==0)return fallback to ""
        val box=rm.getAxisAlignedBoundingBox(ri,null);val c=box.center;val h=box.halfExtent
        val mat=tm.getWorldTransform(ti,null as FloatArray?).map{it.toDouble()}.toDoubleArray()
        val corners=mutableListOf<DoubleArray>()
        for(ix in intArrayOf(-1,1))for(iy in intArrayOf(-1,1))for(iz in intArrayOf(-1,1)){
            corners.add(transformPoint(mat,doubleArrayOf(c[0]+ix*h[0],c[1]+iy*h[1],c[2]+iz*h[2])))
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
        val q=mul4(mulMat4(proj,view),doubleArrayOf(p[0],p[1],p[2],1.0));if(kotlin.math.abs(q[3])<1e-9)return null
        val nx=q[0]/q[3];val ny=q[1]/q[3]
        return ((nx*0.5+0.5)*surfaceView.width).toFloat() to ((1.0-(ny*0.5+0.5))*surfaceView.height).toFloat()
    }

'''+anchor2
if anchor2 not in s: raise SystemExit('distance anchor missing')
s=s.replace(anchor2,helpers,1)

s=s.replace('ECOHOME Viewer v0.1.9','ECOHOME Viewer v0.2.0')
p.write_text(s)
print('v0.2.0 patch applied')
