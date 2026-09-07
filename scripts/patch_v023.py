from pathlib import Path
import re
p=Path('app/src/main/java/vn/ecohome/viewer/MainActivity.kt'); s=p.read_text()

# Imports for ECOHOME Exporter v0.3 metadata.
imports='''import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
'''
if 'import org.json.JSONObject' not in s:
    pos=s.find('\nclass MainActivity')
    if pos<0: raise SystemExit('class import anchor missing')
    s=s[:pos]+"\n"+imports+s[pos:]

# v0.2.3 state.
anchor='    private var magneticSnapLocked=false\n'
extra='''    private var magneticSnapLocked=false
    private var dimDiagonalMode=false
    private var dimModeChip: TextView?=null
'''
if 'private var dimDiagonalMode=false' not in s:
    if anchor in s:s=s.replace(anchor,extra,1)
    else:raise SystemExit('state anchor missing')

# Zoom Window is replaced by two-finger focal zoom; remove dedicated Window tool if present.
s=re.sub(r'\s*Triple\("▣", "Window"\) \{[^\n]*\},\n','\n',s,count=1)

# Replace touch controller. After P1, normal camera navigation is available; long press enters precision DIM for P2.
pat=r'    private fun installSmartTouchController\(\) \{.*?\n    \}\n\n    private fun (?:updateMagneticDimSnap|showZoomWindowRect|showMagnifier)'
m=re.search(pat,s,flags=re.S)
if not m: raise SystemExit('touch controller anchor missing')
next_name=m.group(1)
controller='''    private fun installSmartTouchController() {
        surfaceView.setOnTouchListener { _, event ->
            if (!dimMode) return@setOnTouchListener handleNavigationTouch(event, true)

            when(event.actionMasked){
                MotionEvent.ACTION_DOWN -> {
                    touchDownX=event.x;touchDownY=event.y;holdX=event.x;holdY=event.y;longPressArmed=true
                    if(dimPoint1!=null) handleNavigationTouch(event,false)
                    surfaceView.postDelayed({
                        if(dimMode&&longPressArmed&&!precisionHold){
                            if(navGestureActive)runCatching{cameraManipulator.grabEnd()}
                            navGestureActive=false;navTwoFinger=false
                            precisionHold=true
                            updateMagneticDimSnap(holdX,holdY)
                            setStatus(if(dimPoint1==null)"DIM XYZ • hút điểm 1 • nhả để chốt" else "DIM XYZ • hút điểm 2 • nhả để chốt")
                        }
                    },300)
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if(dimPoint1!=null&&!precisionHold){longPressArmed=false;handleNavigationTouch(event,false)}
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    holdX=event.x;holdY=event.y
                    if(precisionHold){updateMagneticDimSnap(event.x,event.y)}
                    else if(dimPoint1!=null){
                        if(event.pointerCount==1){
                            val dx=event.x-touchDownX;val dy=event.y-touchDownY
                            if(dx*dx+dy*dy>dp(10)*dp(10))longPressArmed=false
                        }else longPressArmed=false
                        handleNavigationTouch(event,false)
                    }else{
                        val dx=event.x-touchDownX;val dy=event.y-touchDownY
                        if(dx*dx+dy*dy>dp(14)*dp(14))longPressArmed=false
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {if(dimPoint1!=null&&!precisionHold)handleNavigationTouch(event,false);true}
                MotionEvent.ACTION_UP -> {
                    longPressArmed=false
                    if(precisionHold){
                        val px=magneticSnapScreenX?:holdX;val py=magneticSnapScreenY?:holdY
                        hideMagnifier();precisionHold=false;pickDimPoint(px,py);clearMagneticDimSnap()
                    }else if(dimPoint1!=null){
                        handleNavigationTouch(event,false)
                        setStatus("DIM ${if(dimDiagonalMode)"CHÉO" else "XYZ"} • P1 ✓ • Orbit/Pan/Zoom tự do • giữ để bắt P2")
                    }else setStatus("DIM ${if(dimDiagonalMode)"CHÉO" else "XYZ"} • giữ trên bề mặt để bắt P1")
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressArmed=false;precisionHold=false;hideMagnifier();clearMagneticDimSnap()
                    if(navGestureActive)runCatching{cameraManipulator.grabEnd()};navGestureActive=false;navTwoFinger=false;true
                }
                else -> true
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

    private fun '''+next_name
s=s[:m.start()]+controller+s[m.end():]

# Add ECOHOME v0.3 metadata parser and inspector before current showEntityInfo.
anchor2='    private fun showEntityInfo(entity:Int,name:String,tag:String?){'
if anchor2 not in s: raise SystemExit('showEntityInfo anchor missing')
meta_helpers='''    private fun parseEcoMetadata(raw:String):JSONObject?{
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
                append("TẤM ĐANG CHỌN\\n");append("Instance: $inst\\n");if(def.isNotBlank())append("Definition: $def\\n")
                if(l.isFinite()&&w.isFinite()&&t.isFinite())append("Kích thước L×W×T: ${l.roundToInt()} × ${w.roundToInt()} × ${t.roundToInt()} mm\\n")
                if(x.isFinite()&&y.isFinite()&&z.isFinite())append("XYZ local: ${x.roundToInt()} × ${y.roundToInt()} × ${z.roundToInt()} mm\\n")
                if(tag.isNotBlank())append("Tag: $tag\\n");append("Vật liệu: ${mat.ifBlank{"Chưa gán"}}")
                val pid=meta.optLong("persistent_id",0L);if(pid!=0L)append("\\nPID: $pid")
                val prod=meta.optJSONObject("prod");if(prod!=null&&prod.length()>0){
                    val keys=prod.keys();var shown=0;while(keys.hasNext()&&shown<4){val k=keys.next();append("\\n${k.substringAfterLast('.')}: ${prod.opt(k)}");shown++}
                }
            }
        }else{
            "TẤM ĐANG CHỌN\\nInstance: $name\\nKích thước: cần GLB từ ECOHOME Exporter v0.3.0\\nTag: ${legacyTag?:"—"}\\nVật liệu: cần metadata Exporter"
        }
        val v=entityInfoView?:TextView(this).apply{textSize=12f;setTextColor(darkGreen);typeface=Typeface.DEFAULT_BOLD;setPadding(dp(12),dp(9),dp(12),dp(9));background=solid(Color.argb(245,252,255,252),14f,green);elevation=dp(8).toFloat()}.also{entityInfoView=it;root.addView(it,FrameLayout.LayoutParams(dp(310),ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.TOP or Gravity.END).apply{topMargin=dp(70);marginEnd=dp(12)})}
        v.text=text;v.visibility=View.VISIBLE;v.bringToFront()
    }

'''+anchor2
s=s.replace(anchor2,meta_helpers,1)
# Use metadata inspector after selection/highlight.
s=s.replace('showEntityInfo(result.renderable,clean,tag)','showEntityInfoV3(result.renderable,raw,clean,tag)',1)

# DIM mode chip: default XYZ, optional diagonal mode.
anchor3='    private fun clearDimOverlay() {'
if anchor3 not in s: raise SystemExit('clearDimOverlay anchor missing')
chip='''    private fun showDimModeChip(){
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

'''+anchor3
s=s.replace(anchor3,chip,1)

# Replace final distance branch with axis-constrained dimension; diagonal remains available via chip.
pat4=r'''            dimPoint2 = world\n\s*val d = distance\(dimPoint1!!, dimPoint2!!\) \* \(sourceMaxExtentMeters / 2\.0\) \* 1000\.0\n\s*val mm = d\.roundToInt\(\)\n\s*val p1 = dimScreen1\n\s*if \(p1 != null\) showDimLineAndLabel\(p1\.first, p1\.second, x, y, "\$mm mm"\)\n\s*showDimMarker\(x,y,"2"\);showDimSnap\(snapped,x,y\)\n\s*setStatus\("DIM • \$mm mm.*?\)'''
rep4='''            dimPoint2 = world
                val a=dimPoint1!!;val b=dimPoint2!!;val scale=(sourceMaxExtentMeters/2.0)*1000.0
                val dx=kotlin.math.abs(b[0]-a[0]);val dy=kotlin.math.abs(b[1]-a[1]);val dz=kotlin.math.abs(b[2]-a[2])
                val p1=dimScreen1
                if(dimDiagonalMode){
                    val mm=(distance(a,b)*scale).roundToInt();if(p1!=null)showDimLineAndLabel(p1.first,p1.second,x,y,"↗ $mm mm");setStatus("DIM CHÉO • $mm mm")
                }else{
                    val axis=if(dx>=dy&&dx>=dz)0 else if(dy>=dz)1 else 2;val axisName=when(axis){0->"X";1->"Y";else->"Z"}
                    val component=when(axis){0->dx;1->dy;else->dz};val mm=(component*scale).roundToInt()
                    val end=doubleArrayOf(a[0],a[1],a[2]);end[axis]=b[axis];val pe=worldToScreen(end)
                    if(p1!=null&&pe!=null)showDimLineAndLabel(p1.first,p1.second,pe.first,pe.second,"$axisName  $mm mm")
                    if(pe!=null)showWitnessLine(x,y,pe.first,pe.second)
                    setStatus("DIM XYZ • $axisName = $mm mm")
                }
                showDimMarker(x,y,"2");showDimSnap(snapped,x,y)'''
s,n=re.subn(pat4,rep4,s,count=1,flags=re.S)
if n!=1: raise SystemExit('axis DIM branch replacement failed')

# First-point status emphasizes navigation is unlocked after release.
s=s.replace('setStatus("DIM • Điểm 1 ✓${if(snapped.second.isNotBlank())" • ${snapped.second}" else ""} • chọn điểm 2")','setStatus("DIM ${if(dimDiagonalMode)"CHÉO" else "XYZ"} • P1 ✓ • có thể Orbit/Pan/Zoom • giữ để bắt P2")',1)

# Show/hide mode chip on DIM activation/reset. Be tolerant of generated wording.
s=s.replace('dimMode = true; dimPoint1 = null; dimPoint2 = null; clearDimOverlay(); setStatus("DIM • ấn giữ điểm 1 để bật kính lúp")','dimMode = true; dimPoint1 = null; dimPoint2 = null; dimDiagonalMode=false; clearDimOverlay(); showDimModeChip(); setStatus("DIM XYZ • giữ để bắt P1")',1)
s=s.replace('dimMode = false; dimPoint1 = null; dimPoint2 = null; clearDimOverlay(); setStatus("DIM • Đã xóa")','dimMode = false; dimPoint1 = null; dimPoint2 = null; clearDimOverlay(); hideDimModeChip(); setStatus("DIM • Đã xóa")',1)
s=s.replace('dimMode = false; dimPoint1 = null; dimPoint2 = null; clearDimOverlay()\n        fitModel()','dimMode = false; dimPoint1 = null; dimPoint2 = null; clearDimOverlay(); hideDimModeChip()\n        fitModel()',1)

# Avoid stale misleading unproject message once a measurement is already on screen.
s=s.replace('setStatus("DIM • Đã bắt bề mặt nhưng chưa giải được tọa độ 3D")','setStatus("DIM • Điểm này chưa giải được tọa độ 3D • thử giữ lại gần bề mặt")')

s=s.replace('ECOHOME Viewer v0.2.2','ECOHOME Viewer v0.2.3')
p.write_text(s)
print('v0.2.3 patch applied')
