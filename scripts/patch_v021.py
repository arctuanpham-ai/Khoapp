from pathlib import Path
import re
p=Path('app/src/main/java/vn/ecohome/viewer/MainActivity.kt'); s=p.read_text()

anchor='    private var entityInfoView: TextView?=null\n'
extra='''    private var entityInfoView: TextView?=null
    private var zoomWindowMode=false
    private var zoomWinStartX=0f
    private var zoomWinStartY=0f
    private var zoomWinRect: View?=null
    private var dimSnapView: TextView?=null
'''
if 'private var zoomWindowMode=false' not in s and anchor in s:s=s.replace(anchor,extra,1)

old='''            Triple("⌗", "Extents") { fitModel() },
            Triple("↻", "Reset") { resetView() }'''
old2='''            Triple("⌗", "Fit") { fitModel() },
            Triple("↻", "Reset") { resetView() }'''
new='''            Triple("⌗", "Extents") { fitModel() },
            Triple("▣", "Window") { zoomWindowMode=true; dimMode=false; setStatus("ZOOM WINDOW • kéo khung vùng muốn phóng") },
            Triple("↻", "Reset") { resetView() }'''
if '"Window"' not in s:
    if old in s:s=s.replace(old,new,1)
    elif old2 in s:s=s.replace(old2,new,1)

pat=r'    private fun installSmartTouchController\(\) \{.*?\n    \}\n\n    private fun showMagnifier'
m=re.search(pat,s,flags=re.S)
if m:
    block=m.group(0)
    if 'if (zoomWindowMode)' not in block:
        block=block.replace('''        surfaceView.setOnTouchListener { _, event ->
            if (dimMode) {''','''        surfaceView.setOnTouchListener { _, event ->
            if (zoomWindowMode) {
                when(event.actionMasked){
                    MotionEvent.ACTION_DOWN->{zoomWinStartX=event.x;zoomWinStartY=event.y;showZoomWindowRect(event.x,event.y,event.x,event.y);true}
                    MotionEvent.ACTION_MOVE->{showZoomWindowRect(zoomWinStartX,zoomWinStartY,event.x,event.y);true}
                    MotionEvent.ACTION_UP->{val x2=event.x;val y2=event.y;hideZoomWindowRect();zoomWindowMode=false;applyZoomWindow(zoomWinStartX,zoomWinStartY,x2,y2);true}
                    MotionEvent.ACTION_CANCEL->{hideZoomWindowRect();zoomWindowMode=false;true}
                    else->true
                }
            } else if (dimMode) {''',1)
    block=block.replace('navOneSyntheticX -= dx;navOneSyntheticY -= dy','navOneSyntheticX += dx;navOneSyntheticY += dy')
    s=s[:m.start()]+block+s[m.end():]

anchor2='    private fun showMagnifier(x: Float, y: Float) {'
if 'private fun showZoomWindowRect(' not in s and anchor2 in s:
    helpers='''    private fun showZoomWindowRect(x1:Float,y1:Float,x2:Float,y2:Float){
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

'''+anchor2
    s=s.replace(anchor2,helpers,1)

if 'private fun showDimSnap(' not in s:
    anchor3='    private fun clearDimOverlay() {'
    snapfun='''    private fun showDimSnap(snapped:Pair<DoubleArray,String>,sx:Float,sy:Float){
        if(snapped.second.isBlank()||snapped.second=="Mặt"){dimSnapView?.visibility=View.GONE;return}
        val v=dimSnapView?:TextView(this).apply{textSize=11f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;setPadding(dp(7),dp(3),dp(7),dp(3));background=solid(green,12f)}.also{dimSnapView=it;root.addView(it)}
        v.text=when(snapped.second){"Đỉnh"->"● ĐỈNH";"Trung điểm"->"◆ MID";"Tâm mặt"->"⊙ TÂM";else->snapped.second}
        val lp=FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(28));lp.leftMargin=(sx+dp(14)).roundToInt();lp.topMargin=(sy-dp(34)).roundToInt();v.layoutParams=lp;v.visibility=View.VISIBLE;v.bringToFront()
    }

'''+anchor3
    if anchor3 in s:s=s.replace(anchor3,snapfun,1)

s=s.replace('ECOHOME Viewer v0.2.0','ECOHOME Viewer v0.2.1')
p.write_text(s)
print('v0.2.1 tolerant patch applied')
