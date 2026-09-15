package vn.ecohome.pos0210.printing

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

data class PrinterDevice(val name:String,val address:String)

object BluetoothPrinter {
    const val PROFILE_NAME="Xprinter XP-NB8H"
    const val PAPER_MM=58
    const val DPI=203
    private const val TAG="POS0210_PRINT"
    private const val MAX_JOB_BYTES=768*1024
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

    fun printBitmap(context:Context,address:String,bitmap:Bitmap,profile:PrinterProfile=PrinterProfile.MM58,jobType:PrintJobType=PrintJobType.TEST):Result<Unit> = runCatching {
        require(address.isNotBlank()){"Chưa chọn máy in Bluetooth"}
        require(hasPermission(context)){"Chưa cấp quyền Bluetooth"}
        val adapter=(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: error("Thiết bị không hỗ trợ Bluetooth")
        val device=adapter.getRemoteDevice(address)
        require(profile.supportsRaster){"Profile không hỗ trợ raster"}
        val raster=EscPosRaster.encode(bitmap,profile)
        val textBytes=0
        val rasterBytes=raster.sumOf{it.size}
        val totalBytes=2+rasterBytes+3
        require(totalBytes in 1..MAX_JOB_BYTES){"PRINT_JOB_SIZE_INVALID:$totalBytes"}
        Log.i(TAG,"start printer=${device.name ?: "unknown"} mac=$address profile=${profile.label} dots=${profile.printableWidthDots} type=$jobType textBytes=$textBytes rasterBytes=$rasterBytes totalBytes=$totalBytes qrMode=raster")
        adapter.cancelDiscovery()
        try{
            device.createRfcommSocketToServiceRecord(SPP_UUID).use { socket ->
                socket.connect()
                socket.outputStream.use { out ->
                    out.write(byteArrayOf(0x1B,0x40))
                    // Each stripe is a complete ESC/POS command. Small printer
                    // buffers never receive a single receipt-height binary frame.
                    raster.forEach{command->out.write(command)}
                    out.write(byteArrayOf(0x0A,0x0A,0x0A))
                    out.flush()
                }
            }
            Log.i(TAG,"success printer=${device.name ?: "unknown"} mac=$address profile=${profile.label} type=$jobType totalBytes=$totalBytes")
        }catch(t:Throwable){
            Log.e(TAG,"failure printer=${device.name ?: "unknown"} mac=$address profile=${profile.label} type=$jobType totalBytes=$totalBytes",t)
            throw t
        }
    }
}

object EscPosRaster{
    fun encode(src:Bitmap,profile:PrinterProfile):List<ByteArray>{
        require(src.width>0&&src.height>0){"Bitmap rỗng"}
        val width=profile.printableWidthDots
        val targetHeight=(src.height.toLong()*width/src.width).toInt()
        require(targetHeight in 1..10000){"RASTER_HEIGHT_INVALID:$targetHeight"}
        val scaled=if(src.width==width) src else Bitmap.createScaledBitmap(src,width,targetHeight,true)
        val commands=mutableListOf<ByteArray>()
        var top=0
        while(top<scaled.height){
            val height=minOf(profile.rasterStripeHeight,scaled.height-top)
            commands+=encodeStripe(width,height){x,y->
                val c=scaled.getPixel(x,top+y)
                (Color.red(c)*30+Color.green(c)*59+Color.blue(c)*11)/100<160
            }
            top+=height
        }
        if(scaled!==src) scaled.recycle()
        return commands
    }

    internal fun encodeStripe(width:Int,height:Int,isBlack:(Int,Int)->Boolean):ByteArray{
        require(width>0&&height>0)
        val bytesPerRow=(width+7)/8
        val command=ByteArray(8+bytesPerRow*height)
        command[0]=0x1D;command[1]=0x76;command[2]=0x30;command[3]=0x00
        command[4]=(bytesPerRow and 0xFF).toByte();command[5]=((bytesPerRow shr 8) and 0xFF).toByte()
        command[6]=(height and 0xFF).toByte();command[7]=((height shr 8) and 0xFF).toByte()
        for(y in 0 until height)for(x in 0 until width)if(isBlack(x,y)){
            val i=8+y*bytesPerRow+x/8
            command[i]=(command[i].toInt() or (0x80 shr (x%8))).toByte()
        }
        return command
    }
}

object ReceiptRenderer {
    private var W=384
    private var PAD=18f

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

    @Synchronized
    fun sampleBill(qr:Bitmap?,profile:PrinterProfile=PrinterProfile.MM58):Bitmap = bill(
        table="BÀN 02",
        period="08:32–09:25",
        items=listOf(Triple("Bún gà",2,40000L),Triple("Bạc xỉu",1,30000L),Triple("Đen đá",1,25000L)),
        subtotal=135000L,
        surcharge=0L,
        discount=13500L,
        total=121500L,
        adjustmentLines=listOf("ƯU ĐÃI HẠNG VIP  -10%"),
        customerName="ANH NAM",
        customerTier="VIP",
        pointsBefore=188,
        pointsEarned=12,
        pointsAfter=200,
        method="CHUYỂN KHOẢN",
        qr=qr,profile=profile
    )

    @Synchronized
    fun bill(
        table:String,
        period:String,
        items:List<Triple<String,Int,Long>>,
        subtotal:Long,
        surcharge:Long=0L,
        discount:Long=0L,
        total:Long,
        adjustmentLines:List<String> = emptyList(),
        customerName:String="KHÁCH LẠ",
        customerTier:String?=null,
        pointsBefore:Int=0,
        pointsEarned:Int=0,
        pointsAfter:Int=0,
        method:String,
        qr:Bitmap?,
        profile:PrinterProfile=PrinterProfile.MM58
    ):Bitmap{
        dimensions(profile)
        val adjustmentHeight = (if(surcharge>0) 34 else 0) + (if(discount>0) 34 else 0) + adjustmentLines.size*24
        val customerHeight=if(customerTier!=null)92 else 48
        val estimated=700+items.size*100+adjustmentHeight+customerHeight+(if(qr!=null)360 else 0)
        val (b,c)=canvas(estimated)
        var y=48f
        c.drawText("0210",W/2f,y,paint(40f,true,Paint.Align.CENTER));y+=27
        c.drawText("BREAKFAST · COFFEE · DRINKS",W/2f,y,paint(18f,false,Paint.Align.CENTER));y+=32
        c.drawText("BILL THANH TOÁN",W/2f,y,paint(28f,true,Paint.Align.CENTER));y+=25
        c.drawText("${table.uppercase()}  ·  $period",W/2f,y,paint(18f,false,Paint.Align.CENTER));y+=22
        line(c,y);y+=28
        items.forEach{(name,qty,unitPrice)->
            y=item(c,y,"$qty × $name",money(unitPrice*qty))
        }
        line(c,y);y+=30
        c.drawText("TẠM TÍNH",PAD,y,paint(20f,true))
        c.drawText(money(subtotal),W-PAD,y,paint(19f,true,Paint.Align.RIGHT));y+=30
        if(surcharge>0){
            c.drawText("PHỤ THU",PAD,y,paint(19f,true))
            c.drawText("+${money(surcharge)}",W-PAD,y,paint(18f,true,Paint.Align.RIGHT));y+=28
        }
        if(discount>0){
            c.drawText("ƯU ĐÃI",PAD,y,paint(19f,true))
            c.drawText("-${money(discount)}",W-PAD,y,paint(18f,true,Paint.Align.RIGHT));y+=28
        }
        adjustmentLines.forEach{lineText->
            y=wrap(c,lineText,PAD,y,W-PAD*2,paint(15f,true),20f)
        }
        line(c,y);y+=35
        c.drawText("THÀNH TIỀN",PAD,y,paint(30f,true))
        c.drawText(money(total),W-PAD,y,paint(25f,true,Paint.Align.RIGHT));y+=31
        line(c,y);y+=27
        c.drawText("KHÁCH: ${customerName.uppercase()}",PAD,y,paint(17f,true));y+=22
        if(customerTier!=null){
            c.drawText("HẠNG: $customerTier",PAD,y,paint(17f,true));y+=22
            c.drawText("ĐIỂM: $pointsBefore + $pointsEarned = $pointsAfter",PAD,y,paint(16f,true));y+=24
        }
        c.drawText("Thanh toán: $method",PAD,y,paint(18f));y+=24
        line(c,y);y+=28
        if(qr!=null){
            c.drawText("QUÉT MÃ THANH TOÁN",W/2f,y,paint(22f,true,Paint.Align.CENTER));y+=14
            val qrSize=292
            val q=Bitmap.createScaledBitmap(qr,qrSize,qrSize,true)
            c.drawBitmap(q,(W-qrSize)/2f,y,null);y+=qrSize+14
            c.drawText("SỐ TIỀN: ${money(total)}",W/2f,y,paint(17f,true,Paint.Align.CENTER));y+=23
        }
        line(c,y);y+=30
        c.drawText("CẢM ƠN QUÝ KHÁCH!",W/2f,y,paint(17f,true,Paint.Align.CENTER));y+=23
        c.drawText("Good Food · Good Coffee · Brighter Day",W/2f,y,paint(18f,false,Paint.Align.CENTER))
        return crop(b,(y+28).toInt())
    }

    @Synchronized
    fun kitchen(table:String,sequence:Int,serviceNo:Int,orderer:String,items:List<Triple<String,Int,String>>,profile:PrinterProfile=PrinterProfile.MM58):Bitmap{
        dimensions(profile)
        val h=420+items.size*170
        val (b,c)=canvas(h)
        var y=45f
        c.drawText("0210",W/2f,y,paint(38f,true,Paint.Align.CENTER));y+=30
        c.drawText("PHIẾU LÀM HÀNG",W/2f,y,paint(30f,true,Paint.Align.CENTER));y+=28
        c.drawText("THỨ TỰ RA ĐƠN #${serviceNo.toString().padStart(3,'0')}",W/2f,y,paint(24f,true,Paint.Align.CENTER));y+=26
        c.drawText("${table.uppercase()}  ·  ĐƠN #$sequence",W/2f,y,paint(18f,true,Paint.Align.CENTER));y+=22
        line(c,y);y+=32
        items.forEach{(name,qty,note)->
            y=wrap(c,"$qty × $name",PAD,y,W-PAD*2,paint(24f,true),28f)
            if(note.isNotBlank()){
                y=wrap(c,"GHI CHÚ: ${note.trim()}",PAD+10f,y,W-PAD*2-10f,paint(18f,true),21f)
                y+=8
            }else y+=12
        }
        line(c,y);y+=28
        c.drawText("Order: $orderer",PAD,y,paint(18f,true))
        return crop(b,(y+28).toInt())
    }

    @Synchronized
    fun cancel(table:String,sequence:Int,manager:String,reason:String,profile:PrinterProfile=PrinterProfile.MM58):Bitmap{
        dimensions(profile)
        val (b,c)=canvas(340)
        var y=48f
        c.drawText("0210",W/2f,y,paint(34f,true,Paint.Align.CENTER));y+=34
        c.drawText("PHIẾU HỦY ĐƠN",W/2f,y,paint(30f,true,Paint.Align.CENTER));y+=30
        c.drawText("${table.uppercase()} · ĐƠN #$sequence",W/2f,y,paint(17f,true,Paint.Align.CENTER));y+=28
        line(c,y);y+=34
        c.drawText("LÝ DO:",PAD,y,paint(20f,true));y+=24
        y=wrap(c,reason,PAD,y,W-PAD*2,paint(20f),24f)
        y+=8
        line(c,y);y+=30
        c.drawText("Manager: $manager",PAD,y,paint(15f,true))
        return crop(b,(y+30).toInt())
    }

    private fun item(c:Canvas,y0:Float,name:String,price:String):Float{
        val namePaint=paint(22f,true)
        val pricePaint=paint(17f,true,Paint.Align.RIGHT)
        val priceWidth=pricePaint.measureText(price)
        val available=W-PAD*3-priceWidth
        return if(namePaint.measureText(name)<=available){
            c.drawText(name,PAD,y0,namePaint);c.drawText(price,W-PAD,y0,pricePaint);y0+34
        }else{
            val next=wrap(c,name,PAD,y0,W-PAD*2,namePaint,27f)
            c.drawText(price,W-PAD,next,pricePaint);next+30
        }
    }

    private fun money(v:Long)="%,dđ".format(v).replace(',','.')
    private fun line(c:Canvas,y:Float){ c.drawLine(PAD,y,W-PAD,y,Paint().apply{color=Color.BLACK;strokeWidth=1f}) }

    private fun wrap(c:Canvas,text:String,x:Float,y0:Float,max:Float,p:Paint,step:Float):Float{
        var y=y0
        var line=""
        text.split(" ").forEach{word->
            val test=if(line.isBlank())word else "$line $word"
            if(p.measureText(test)>max){
                if(line.isNotBlank()){c.drawText(line,x,y,p);y+=step}
                line=word
            }else line=test
        }
        if(line.isNotBlank()){c.drawText(line,x,y,p);y+=step}
        return y
    }

    private fun dimensions(profile:PrinterProfile){W=profile.printableWidthDots;PAD=if(profile.paperSize==PaperSize.MM58)18f else 24f}

    private fun crop(b:Bitmap,h:Int)=Bitmap.createBitmap(b,0,0,b.width,h.coerceAtMost(b.height))
}
