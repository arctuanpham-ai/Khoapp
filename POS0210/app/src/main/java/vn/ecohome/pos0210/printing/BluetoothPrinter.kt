package vn.ecohome.pos0210.printing

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.UUID

data class PrinterDevice(val name:String,val address:String)

object BluetoothPrinter {
    const val PROFILE_NAME="Xprinter XP-N58H"
    const val PAPER_MM=58
    const val PRINT_WIDTH_PX=384
    const val PRINT_WIDTH_MM=48
    const val DPI=203
    private val SPP_UUID:UUID=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun hasPermission(context:Context):Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context,Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED

    fun pairedDevices(context:Context):List<PrinterDevice>{
        if(!hasPermission(context)) return emptyList()
        val adapter=(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return emptyList()
        return try {
            adapter.bondedDevices
                .map{PrinterDevice(it.name ?: "Bluetooth printer",it.address)}
                .sortedBy{it.name.lowercase()}
        } catch(_:SecurityException){ emptyList() }
    }

    fun printBitmap(context:Context,address:String,bitmap:Bitmap):Result<Unit> = runCatching {
        require(address.isNotBlank()){"Chưa chọn máy in Bluetooth"}
        require(hasPermission(context)){"Chưa cấp quyền Bluetooth"}
        val adapter=(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: error("Thiết bị không hỗ trợ Bluetooth")
        val device=adapter.getRemoteDevice(address)
        adapter.cancelDiscovery()
        device.createRfcommSocketToServiceRecord(SPP_UUID).use { socket ->
            socket.connect()
            socket.outputStream.use { out ->
                out.write(byteArrayOf(0x1B,0x40))
                out.write(rasterCommand(bitmap))
                out.write(byteArrayOf(0x0A,0x0A,0x0A))
                out.flush()
            }
        }
    }

    fun downloadBitmap(url:String):Bitmap? = runCatching {
        URL(url).openStream().use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun rasterCommand(src:Bitmap):ByteArray{
        val width=PRINT_WIDTH_PX
        val scaled=if(src.width==width) src else Bitmap.createScaledBitmap(src,width,(src.height*(width.toFloat()/src.width)).toInt(),true)
        val bytesPerRow=(width+7)/8
        val data=ByteArray(bytesPerRow*scaled.height)
        for(y in 0 until scaled.height){
            for(x in 0 until width){
                val c=scaled.getPixel(x,y)
                val gray=(Color.red(c)*30+Color.green(c)*59+Color.blue(c)*11)/100
                if(gray<160){
                    val i=y*bytesPerRow+x/8
                    data[i]=(data[i].toInt() or (0x80 shr (x%8))).toByte()
                }
            }
        }
        val h=scaled.height
        return ByteArrayOutputStream().apply{
            write(byteArrayOf(0x1D,0x76,0x30,0x00,(bytesPerRow and 0xFF).toByte(),((bytesPerRow shr 8) and 0xFF).toByte(),(h and 0xFF).toByte(),((h shr 8) and 0xFF).toByte()))
            write(data)
        }.toByteArray()
    }
}

object ReceiptRenderer {
    private const val W=384
    private const val PAD=18f

    private fun paint(size:Float,bold:Boolean=false,align:Paint.Align=Paint.Align.LEFT)=Paint(Paint.ANTI_ALIAS_FLAG).apply{
        color=Color.BLACK
        textSize=size
        typeface=if(bold) Typeface.create(Typeface.DEFAULT,Typeface.BOLD) else Typeface.create(Typeface.DEFAULT,Typeface.NORMAL)
        textAlign=align
    }

    private fun canvas(height:Int):Pair<Bitmap,Canvas>{
        val b=Bitmap.createBitmap(W,height,Bitmap.Config.ARGB_8888)
        val c=Canvas(b)
        c.drawColor(Color.WHITE)
        return b to c
    }

    fun sampleBill(qr:Bitmap?):Bitmap = bill(
        table="BÀN 02",
        period="08:32–09:25",
        items=listOf(Triple("Bún gà",2,40000L),Triple("Bạc xỉu",1,30000L),Triple("Đen đá",1,25000L)),
        total=135000L,
        method="TIỀN MẶT / CHUYỂN KHOẢN",
        qr=qr
    )

    fun bill(table:String,period:String,items:List<Triple<String,Int,Long>>,total:Long,method:String,qr:Bitmap?):Bitmap{
        val estimated=540+items.size*48+(if(qr!=null)250 else 0)
        val (b,c)=canvas(estimated)
        var y=48f
        c.drawText("0210",W/2f,y,paint(40f,true,Paint.Align.CENTER));y+=27
        c.drawText("BREAKFAST · COFFEE · DRINKS",W/2f,y,paint(14f,false,Paint.Align.CENTER));y+=32
        c.drawText("BILL THANH TOÁN",W/2f,y,paint(22f,true,Paint.Align.CENTER));y+=25
        c.drawText("${table.uppercase()}  ·  $period",W/2f,y,paint(15f,false,Paint.Align.CENTER));y+=22
        line(c,y);y+=28
        items.forEach{(name,qty,unitPrice)->
            y=item(c,y,"$qty × $name",money(unitPrice*qty))
        }
        line(c,y);y+=34
        c.drawText("TỔNG CỘNG",PAD,y,paint(24f,true))
        c.drawText(money(total),W-PAD,y,paint(24f,true,Paint.Align.RIGHT));y+=30
        c.drawText("Thanh toán: $method",PAD,y,paint(14f));y+=24
        line(c,y);y+=30
        if(qr!=null){
            c.drawText("QUÉT MÃ THANH TOÁN",W/2f,y,paint(17f,true,Paint.Align.CENTER));y+=14
            val q=Bitmap.createScaledBitmap(qr,210,210,true)
            c.drawBitmap(q,(W-210)/2f,y,null);y+=225
        }
        line(c,y);y+=30
        c.drawText("CẢM ƠN QUÝ KHÁCH!",W/2f,y,paint(17f,true,Paint.Align.CENTER));y+=23
        c.drawText("Good Food · Good Coffee · Brighter Day",W/2f,y,paint(13f,false,Paint.Align.CENTER))
        return crop(b,(y+28).toInt())
    }

    fun kitchen(table:String,sequence:Int,orderer:String,items:List<Pair<String,Int>>):Bitmap{
        val h=260+items.size*55
        val (b,c)=canvas(h)
        var y=45f
        c.drawText("0210",W/2f,y,paint(34f,true,Paint.Align.CENTER));y+=30
        c.drawText("PHIẾU LÀM HÀNG",W/2f,y,paint(23f,true,Paint.Align.CENTER));y+=28
        c.drawText("${table.uppercase()}  ·  ĐƠN #$sequence",W/2f,y,paint(16f,true,Paint.Align.CENTER));y+=22
        line(c,y);y+=32
        items.forEach{(name,qty)->
            c.drawText("$qty × $name",PAD,y,paint(21f,true));y+=42
        }
        line(c,y);y+=28
        c.drawText("Order: $orderer",PAD,y,paint(15f,true))
        return crop(b,(y+28).toInt())
    }

    fun cancel(table:String,sequence:Int,manager:String,reason:String):Bitmap{
        val (b,c)=canvas(340)
        var y=48f
        c.drawText("0210",W/2f,y,paint(34f,true,Paint.Align.CENTER));y+=34
        c.drawText("PHIẾU HỦY ĐƠN",W/2f,y,paint(24f,true,Paint.Align.CENTER));y+=30
        c.drawText("${table.uppercase()} · ĐƠN #$sequence",W/2f,y,paint(17f,true,Paint.Align.CENTER));y+=28
        line(c,y);y+=34
        c.drawText("LÝ DO:",PAD,y,paint(16f,true));y+=24
        y=wrap(c,reason,PAD,y,W-PAD*2,paint(17f),24f)
        y+=8
        line(c,y);y+=30
        c.drawText("Manager: $manager",PAD,y,paint(15f,true))
        return crop(b,(y+30).toInt())
    }

    private fun item(c:Canvas,y0:Float,name:String,price:String):Float{
        val y=y0
        c.drawText(name,PAD,y,paint(17f,true))
        c.drawText(price,W-PAD,y,paint(17f,true,Paint.Align.RIGHT))
        return y+34
    }

    private fun money(v:Long)="%,dđ".format(v).replace(',','.')
    private fun line(c:Canvas,y:Float){ c.drawLine(PAD,y,W-PAD,y,Paint().apply{color=Color.BLACK;strokeWidth=1f}) }

    private fun wrap(c:Canvas,text:String,x:Float,y0:Float,max:Float,p:Paint,step:Float):Float{
        var y=y0
        var line=""
        text.split(" ").forEach{word->
            val test=if(line.isBlank())word else "$line $word"
            if(p.measureText(test)>max){
                c.drawText(line,x,y,p);y+=step;line=word
            }else line=test
        }
        if(line.isNotBlank()){c.drawText(line,x,y,p);y+=step}
        return y
    }

    private fun crop(b:Bitmap,h:Int)=Bitmap.createBitmap(b,0,0,b.width,h.coerceAtMost(b.height))
}
