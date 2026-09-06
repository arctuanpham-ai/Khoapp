from pathlib import Path
import re
p=Path('app/src/main/java/vn/ecohome/viewer/MainActivity.kt'); s=p.read_text()

# State for zoom-window and visible DIM snap marker.
anchor='    private var entityInfoView: TextView?=null\n'
extra='''    private var entityInfoView: TextView?=null
    private var zoomWindowMode=false
    private var zoomWinStartX=0f
    private var zoomWinStartY=0f
    private var zoomWinRect: View?=null
    private var dimSnapView: TextView?=null
'''
if anchor in s: s=s.replace(anchor,extra,1)
else: raise SystemExit('state anchor missing')

# Add Zoom Window tool next to Extents/Reset without changing existing navigation semantics.
old='''            Triple("⌗", "Extents") { fitModel() },
            Triple("↻", "Reset") { resetView() }'''
if old not in s:
    old='''            Triple("⌗", "Fit") { fitModel() },
            Triple("↻", "Reset") { resetView() }'''
new='''            Triple("⌗", "Extents") { fitModel() },
            Triple("▣", "Window") { zoomWindowMode=true; dimMode=false; setStatus("ZOOM WINDOW • kéo khung vùng muốn phóng") },
            Triple("↻", "Reset") { resetView() }'''
if old in s: s=s.replace(old,new,1)
else: raise SystemExit('camera tools anchor missing')

# Touch controller: add zoom-window branch and correct orbit sign once more.
pat=r'    private fun installSmartTouchController\(\) \{.*?\n    \}\n\n    private fun showMagnifier'
m=re.search(pat,s,flags=re.S)
if not m: raise SystemExit('touch controller not found')
block=m.group(0)
# Insert window branch before dimMode branch.
block=block.replace('''        surfaceView.setOnTouchListener { _, event ->
            if (dimMode) {''','''        surfaceView.setOnTouchListener { _, event ->
            if (zoomWindowMode) {
                when(event.actionMasked){
                    MotionEvent.ACTION_DOWN->{zoomWinStartX=event.x;zoomWinStartY=event.y;showZoomWindowRect(event.x,event.y,event.x,event.y);true}
                    MotionEvent.ACTION_MOVE->{showZoomWindowRect(zoomWinStartX,zoomWinStartY,event.x,event.y);true}
                    MotionEvent.ACTION_UP->{
                        val x2=event.x;val y2=event.y;hideZoomWindowRect();zoomWindowMode=false
                        applyZoomWindow(zoomWinStartX,zoomWinStartY,x2,y2);true
                    }
                    MotionEvent.ACTION_CANCEL->{hideZoomWindowRect();zoomWindowMode=false;true}
                    else->true
                }
            } else if (dimMode) {''',1)
# v0.2.0 had synthetic -= delta. Device feedback says orbit still inverted, so restore direct sign.
block=block.replace('''                            // Orbit direction inverted to follow the requested SketchUp Viewer behavior.
                            navOneSyntheticX -= dx;navOneSyntheticY -= dy''','''                            // v0.2.1 device correction: orbit follows finger drag direction.
                            navOneSyntheticX += dx;navOneSyntheticY += dy''',1)
s=s[:m.start()]+block+s[m.end():]

# Add zoom-window helpers before showMagnifier.
anchor2='    private fun showMagnifier(x: Float, y: Float) {'
helpers='''    private fun showZoomWindowRect(x1:Float,y1:Float,x2:Float,y2:Float){
        val left=kotlin.math.min(x1,x2).roundToInt();val top=kotlin.math.min(y1,y2).roundToInt()
        val w=kotlin.math.max(dp(2),kotlin.math.abs(x2-x1).roundToInt());val h=kotlin.math.max(dp(2),kotlin.math.abs(y2-y1).roundToInt())
        val v=zoomWinRect?:View(this).apply{background=GradientDrawable().apply{setColor(Color.argb(32,55,151,91));setStroke(dp(2),green)}}.also{zoomWinRect=it;root.addView(it)}
        val lp=FrameLayout.LayoutParams(w,h);lp.leftMargin=left;lp.topMargin=top;v.layoutParams=lp;v.visibility=View.VISIBLE;v.bringToFront()
    }
    private fun hideZoomWindowRect(){zoomWinRect?.visibility=View.GONE}
    private fun applyZoomWindow(x1:Float,y1:Float,x2:Float,y2:Float){
        val w=kotlin.math.abs(x2-x1);val h=kotlin.math.abs(y2-y1)
        if(w<dp(30)||h<dp(30)){setStatus("ZOOM WINDOW • vùng quá nhỏ");return}
        val cx=(x1+x2)*0.5f;val cy=(y1+y2)*0.5f
        val frac=kotlin.math.max(w/surfaceView.width.toFloat(),h/surfaceView.height.toFloat()).coerceIn(0.08f,0.95f)
        val strength=((1f/frac)-1f).coerceIn(0.25f,5.5f)
        cameraManipulator.scroll(cx.toInt(),cy.toInt(),-strength)
        setStatus("ZOOM WINDOW • đã phóng vùng chọn")
    }

'''+anchor2
if anchor2 in s: s=s.replace(anchor2,helpers,1)
else: raise SystemExit('showMagnifier anchor missing')

# Make DIM snap visible after release: move the on-screen point marker to snapped candidate and show label.
# Find calls that show dim markers after point assignment and supplement them.
s=s.replace('showDimMarker(x,y,"1")','showDimMarker(x,y,"1");showDimSnap(snapped,x,y)',1)
s=s.replace('showDimMarker(x,y,"2")','showDimMarker(x,y,"2");showDimSnap(snapped,x,y)',1)

# If exact showDimMarker strings differ, insert snap feedback near status updates.
if 'private fun showDimSnap(' not in s:
    anchor3='    private fun clearDimOverlay() {'
    snapfun='''    private fun showDimSnap(snapped:Pair<DoubleArray,String>,sx:Float,sy:Float){
        if(snapped.second.isBlank()||snapped.second=="Mặt"){dimSnapView?.visibility=View.GONE;return}
        val v=dimSnapView?:TextView(this).apply{
            textSize=11f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD
            setPadding(dp(7),dp(3),dp(7),dp(3));background=solid(green,12f)
        }.also{dimSnapView=it;root.addView(it)}
        v.text=when(snapped.second){"Đỉnh"->"● ĐỈNH";"Trung điểm"->"◆ MID";"Tâm mặt"->"⊙ TÂM";else->snapped.second}
        val lp=FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(28));lp.leftMargin=(sx+dp(14)).roundToInt();lp.topMargin=(sy-dp(34)).roundToInt();v.layoutParams=lp;v.visibility=View.VISIBLE;v.bringToFront()
    }

'''+anchor3
    if anchor3 in s: s=s.replace(anchor3,snapfun,1)
    else: raise SystemExit('clearDimOverlay anchor missing')

# Clear snap feedback when dimension overlays are reset.
s=s.replace('''    private fun clearDimOverlay() {
''','''    private fun clearDimOverlay() {
        dimSnapView?.visibility=View.GONE
''',1)

# Prevent surprise Extents jumps by only using fitModel from explicit controls; remove any fitModel call immediately after generic load if present.
s=s.replace('''            fitModel()
            setStatus("Đã mở model''','''            setStatus("Đã mở model''',1)

s=s.replace('ECOHOME Viewer v0.2.0','ECOHOME Viewer v0.2.1')
p.write_text(s)
print('v0.2.1 patch applied')
