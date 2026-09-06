from pathlib import Path
import re

p=Path('app/src/main/java/vn/ecohome/viewer/MainActivity.kt')
s=p.read_text()

# Kotlin interpolation: $entity_ is parsed as a different identifier.
s=s.replace('"ECOHOME_SELECTED_$entity_$i"','"ECOHOME_SELECTED_${entity}_$i"')

# Rebuild inspector function using escaped Kotlin newlines.
pat=r'    private fun showEntityInfo\(entity:Int,name:String,tag:String\?\)\{.*?\n    \}\n\n    private fun pickDimPoint'
rep='''    private fun showEntityInfo(entity:Int,name:String,tag:String?){
        val rm=modelViewer.engine.renderableManager
        val ri=rm.getInstance(entity)
        if(ri==0)return
        val box=rm.getAxisAlignedBoundingBox(ri,null)
        val h=box.halfExtent
        val dims=listOf(h[0]*2f,h[1]*2f,h[2]*2f).map{(it*1000f).roundToInt()}.sortedDescending()
        val mat=runCatching{if(rm.getPrimitiveCount(ri)>0)rm.getMaterialInstanceAt(ri,0).name else ""}.getOrDefault("")
        val text=buildString{
            append("TẤM ĐANG CHỌN\\n")
            append("Instance: $name\\n")
            append("Kích thước: ${dims.getOrElse(0){0}} × ${dims.getOrElse(1){0}} × ${dims.getOrElse(2){0}} mm\\n")
            if(tag!=null)append("Tag: $tag\\n")
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

    private fun pickDimPoint'''
s,n=re.subn(pat,lambda m:rep,s,count=1,flags=re.S)
if n!=1: raise SystemExit('showEntityInfo repair failed')

# AABB is Float; snap transform point helper expects Double coordinates.
s=s.replace(
    'doubleArrayOf(c[0]+ix*h[0],c[1]+iy*h[1],c[2]+iz*h[2])',
    'doubleArrayOf((c[0]+ix*h[0]).toDouble(),(c[1]+iy*h[1]).toDouble(),(c[2]+iz*h[2]).toDouble())'
)

p.write_text(s)
print('v0.2.0 Kotlin repairs applied')
