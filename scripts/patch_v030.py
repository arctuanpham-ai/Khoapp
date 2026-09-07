from pathlib import Path
import re
p=Path('app/src/main/java/vn/ecohome/viewer/MainActivity.kt')
s=p.read_text()

# v0.3.0 state: deterministic snap callbacks, persistent tag visibility, reversible view materials.
anchor='    private var dimModeChip: TextView?=null\n'
extra='''    private var dimModeChip: TextView?=null
    private var snapGeneration=0L
    private var lastSnapRequestMs=0L
    private val tagVisible=mutableMapOf<String,Boolean>()
    private val viewOriginalMaterials=mutableMapOf<String,MaterialInstance>()
    private val viewTempMaterials=mutableListOf<MaterialInstance>()
'''
if 'private var snapGeneration=' not in s:
    if anchor in s:s=s.replace(anchor,extra,1)
    else:raise SystemExit('v030 state anchor missing')

# Clean DIM controller state machine. Magnifier appears ONLY on deliberate long press.
pat=r'    private fun installSmartTouchController\(\) \{.*?\n    \}\n\n    private fun handleNavigationTouch'
rep='''    private fun installSmartTouchController() {
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

    private fun handleNavigationTouch'''
s,n=re.subn(pat,rep,s,count=1,flags=re.S)
if n!=1:raise SystemExit('v030 touch controller replacement failed')

# Smooth magnetic snap: throttle GPU picks and discard stale callbacks.
pat=r'    private fun updateMagneticDimSnap\(fingerX:Float,fingerY:Float\)\{.*?\n    \}\n\n    private fun snapDimPointMagnetic'
rep='''    private fun updateMagneticDimSnap(fingerX:Float,fingerY:Float){
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
            val surface=if(result.renderable!=0)unproject(result.fragCoords) else null
            if(result.renderable==0||surface==null){
                magneticSnapLocked=false;magneticSnapScreenX=null;magneticSnapScreenY=null;magneticSnapWorld=null;magneticSnapLabel=""
                dimSnapView?.visibility=View.GONE;showMagnifier(fingerX,fingerY);return@pick
            }
            val candidate=snapDimPointMagnetic(result.renderable,fingerX,fingerY,surface)
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

    private fun snapDimPointMagnetic'''
s,n=re.subn(pat,rep,s,count=1,flags=re.S)
if n!=1:raise SystemExit('v030 magnetic snap replacement failed')

# Increase attraction radius slightly for finger operation while preserving release hysteresis.
s=s.replace('"Đỉnh"->dp(52).toDouble();"Trung điểm"->dp(42).toDouble();else->dp(32).toDouble()', '"Đỉnh"->dp(60).toDouble();"Trung điểm"->dp(50).toDouble();else->dp(38).toDouble()')

# Replace DIM commit logic completely: one clean dimension only; XYZ uses dominant axis, diagonal separate.
pat=r'    private fun pickDimPoint\(x: Float, y: Float\) \{.*?\n    \}\n\n    private fun snapDimPoint'
rep='''    private fun pickDimPoint(x: Float, y: Float) {
        modelViewer.asset?:return
        modelViewer.view.pick(x.toInt().coerceAtLeast(0),(surfaceView.height-y.toInt()).coerceAtLeast(0),surfaceView.handler){result->
            if(result.renderable==0){setStatus("DIM • Không trúng bề mặt • giữ và chọn lại");return@pick}
            val surface=unproject(result.fragCoords)
            if(surface==null){setStatus("DIM • Không giải được tọa độ 3D • chọn lại");return@pick}
            val snapped=snapDimPoint(result.renderable,x,y,surface)
            val world=snapped.first
            val screen=worldToScreen(world) ?: (x to y)
            hideMagnifier();dimSnapView?.visibility=View.GONE;clearMagneticDimSnap()
            if(dimPoint1==null){
                clearDimOverlay();dimPoint1=world;dimPoint2=null;dimScreen1=screen
                showDimMarker(screen.first,screen.second,"1")
                setStatus("DIM ${if(dimDiagonalMode)"CHÉO" else "XYZ"} • P1 ✓ • Orbit/Pan/Zoom tự do • giữ để bắt P2")
                return@pick
            }
            val a=dimPoint1!!;val b=world;val p1=dimScreen1 ?: (worldToScreen(a) ?: screen)
            val scale=(sourceMaxExtentMeters/2.0)*1000.0
            val dx=kotlin.math.abs(b[0]-a[0]);val dy=kotlin.math.abs(b[1]-a[1]);val dz=kotlin.math.abs(b[2]-a[2])
            clearDimOverlay()
            showDimMarker(p1.first,p1.second,"1");showDimMarker(screen.first,screen.second,"2")
            if(dimDiagonalMode){
                val mm=(distance(a,b)*scale).roundToInt();showDimLineAndLabel(p1.first,p1.second,screen.first,screen.second,"↗ $mm mm")
                setStatus("DIM CHÉO • $mm mm")
            }else{
                val axis:String;val mm:Int;val endWorld:DoubleArray
                if(dx>=dy&&dx>=dz){axis="X";mm=(dx*scale).roundToInt();endWorld=doubleArrayOf(b[0],a[1],a[2])}
                else if(dy>=dz){axis="Y";mm=(dy*scale).roundToInt();endWorld=doubleArrayOf(a[0],b[1],a[2])}
                else{axis="Z";mm=(dz*scale).roundToInt();endWorld=doubleArrayOf(a[0],a[1],b[2])}
                val ep=worldToScreen(endWorld) ?: screen
                showDimLineAndLabel(p1.first,p1.second,ep.first,ep.second,"$axis $mm mm")
                val wx=screen.first-ep.first;val wy=screen.second-ep.second
                if(wx*wx+wy*wy>dp(5)*dp(5))showWitnessLine(screen.first,screen.second,ep.first,ep.second)
                setStatus("DIM XYZ • $axis = $mm mm")
            }
            dimPoint1=null;dimPoint2=null;dimScreen1=null;dimMode=false;hideDimModeChip();precisionHold=false;hideMagnifier()
        }
    }

    private fun snapDimPoint'''
s,n=re.subn(pat,rep,s,count=1,flags=re.S)
if n!=1:raise SystemExit('v030 pickDimPoint replacement failed')

# DIM menu: no floating mode chip; mode is chosen explicitly.
pat=r'    private fun showDimPanel\(\) \{.*?\n    \}\n\n    private fun showBoundingDimensions'
rep='''    private fun showDimPanel() {
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

    private fun showBoundingDimensions'''
s,n=re.subn(pat,rep,s,count=1,flags=re.S)
if n!=1:raise SystemExit('v030 dim panel replacement failed')

# Tags: consume Exporter v0.3 __ECO_META__ JSON, persist visibility, and actually add/remove entities.
pat=r'    private fun showTagsPanel\(\) \{.*?\n    \}\n\n    private fun showLightingPanel'
rep='''    private fun showTagsPanel() {
        val asset=modelViewer.asset?:run{Toast.makeText(this,"Hãy mở model trước",Toast.LENGTH_SHORT).show();return}
        val groups=linkedMapOf<String,MutableList<Int>>()
        for(e in asset.entities){
            val raw=runCatching{asset.getName(e)}.getOrNull().orEmpty()
            val tag=parseEcoMetadata(raw)?.optString("tag")?.takeIf{it.isNotBlank()} ?: parseTag(raw) ?: continue
            groups.getOrPut(tag){mutableListOf()}.add(e)
        }
        if(groups.isEmpty()){
            AlertDialog.Builder(this).setTitle("Tags SketchUp").setMessage("Không tìm thấy Tag metadata trong GLB. Hãy xuất bằng ECOHOME Viewer Exporter v0.3.0.").setPositiveButton("Đóng",null).show();return
        }
        val names=groups.keys.sorted().toTypedArray();val labels=names.map{"$it  (${groups[it]?.size?:0})"}.toTypedArray()
        val checked=BooleanArray(names.size){i->tagVisible.getOrPut(names[i]){true}}
        AlertDialog.Builder(this).setTitle("Tags SketchUp • ${names.size} tags").setMultiChoiceItems(labels,checked){_,which,on->
            val tag=names[which];tagVisible[tag]=on
            groups[tag].orEmpty().forEach{entity->if(on)modelViewer.scene.addEntity(entity)else modelViewer.scene.removeEntity(entity)}
            setStatus("TAGS • $tag ${if(on)"ON" else "OFF"}")
        }.setPositiveButton("Xong",null).show()
    }

    private fun showLightingPanel'''
s,n=re.subn(pat,rep,s,count=1,flags=re.S)
if n!=1:raise SystemExit('v030 tags panel replacement failed')

# Functional display modes only. No fake Hidden Line/Wireframe menu entries.
pat=r'    private fun showViewPanel\(\) \{.*?\n    \}\n\n    private fun showDimPanel'
rep='''    private fun showViewPanel() {
        val options=arrayOf("Material / Texture","Shaded sáng","Technical contrast","X-Ray beta")
        val selected=when(currentMode){"TEXTURE"->0;"SHADED"->1;"TECH"->2;"X-RAY"->3;else->0}
        AlertDialog.Builder(this).setTitle("Chế độ hiển thị").setSingleChoiceItems(options,selected){d,which->
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

    private fun showDimPanel'''
s,n=re.subn(pat,rep,s,count=1,flags=re.S)
if n!=1:raise SystemExit('v030 view panel replacement failed')

# New model resets persistent UI state and never auto-jumps Extents.
s=s.replace('dimMode = false; dimPoint1 = null; dimPoint2 = null; clearDimOverlay()', 'dimMode = false; dimPoint1 = null; dimPoint2 = null; clearDimOverlay(); tagVisible.clear(); viewOriginalMaterials.clear(); viewTempMaterials.clear()',1)
s=s.replace('surfaceView.postDelayed({ fitModel() }, 120)','surfaceView.postDelayed({ if(modelViewer.asset!=null) setStatus("Model sẵn sàng • dùng Extents khi cần") },120)')

# Version label.
s=s.replace('ECOHOME Viewer v0.2.3','ECOHOME Viewer v0.3.0')
s=s.replace('ECOHOME Viewer v0.1.5','ECOHOME Viewer v0.3.0')
p.write_text(s)
print('v0.3.0 functional patch applied')
