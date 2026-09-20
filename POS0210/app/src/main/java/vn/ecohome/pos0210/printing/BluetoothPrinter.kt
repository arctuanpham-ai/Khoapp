package vn.ecohome.pos0210.printing

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.OutputStream
import java.util.UUID

data class PrinterDevice(val name:String,val address:String)

object BluetoothPrinter {
    const val PROFILE_NAME="Xprinter XP-NB8H"
    const val PAPER_MM=58
    const val DPI=203
    private const val TAG="POS0210_PRINT"
    private const val MAX_JOB_BYTES=768*1024
    private val PRINT_LOCK=Any()
    private val SPP_UUID:UUID=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun hasPermission(context:Context):Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context,Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED

    // Guarded immediately below and SecurityException remains a runtime fallback.
    @SuppressLint("MissingPermission")
    fun pairedDevices(context:Context):List<PrinterDevice>{
        if(!hasPermission(context)) return emptyList()
        val adapter=(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return emptyList()
        return try {
            adapter.bondedDevices
                .map{PrinterDevice(it.name ?: "Bluetooth printer",it.address)}
                .sortedBy{it.name.lowercase()}
        } catch(_:SecurityException){ emptyList() }
    }

    fun printBitmap(context:Context,address:String,bitmap:Bitmap,profile:PrinterProfile=PrinterProfile.MM58,jobType:PrintJobType=PrintJobType.TEST):Result<Unit> =
        synchronized(PRINT_LOCK){printBitmapLocked(context,address,bitmap,profile,jobType)}

    // Permission is checked before this method and SecurityException is still
    // translated below in case Android revokes it between check and use.
    @SuppressLint("MissingPermission")
    private fun printBitmapLocked(context:Context,address:String,bitmap:Bitmap,profile:PrinterProfile,jobType:PrintJobType):Result<Unit> {
        if(!hasPermission(context))return permissionRevokedFailure()
        return runCatching {
        require(address.isNotBlank()){"Chưa chọn máy in Bluetooth"}
        val adapter=(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: error("Thiết bị không hỗ trợ Bluetooth")
        val device=adapter.getRemoteDevice(address)
        require(profile.supportsRaster){"Profile không hỗ trợ raster"}
        val raster=EscPosRaster.encode(bitmap,profile).toMutableList().apply{addAll(EscPosRaster.blank(profile,profile.transport.trailingBlankDots))}
        val textBytes=0
        val rasterBytes=raster.sumOf{it.size}
        val totalBytes=EscPosTransport.estimatedBytes(raster,profile.transport)
        require(totalBytes in 1..MAX_JOB_BYTES){"PRINT_JOB_SIZE_INVALID:$totalBytes"}
        val policy=profile.transport
        Log.i(TAG,"PRINT START printer=${device.name ?: "unknown"} mac=$address profile=${profile.label} dots=${profile.printableWidthDots} type=$jobType bitmap=${bitmap.width}x${bitmap.height} stripes=${raster.size} textBytes=$textBytes rasterBytes=$rasterBytes totalBytes=$totalBytes qrMode=raster pacing=${policy.delayPerStripeMs}ms/${policy.burstBytes}B/${policy.delayPerBurstMs}ms blankDots=${policy.trailingBlankDots} feed=${policy.trailingFeedLines} drain=${policy.postJobDrainMs}ms cutter=${policy.hasAutoCutter}")
        adapter.cancelDiscovery()
        try{
            device.createRfcommSocketToServiceRecord(SPP_UUID).use { socket ->
                socket.connect()
                socket.outputStream.use { out ->
                    val stats=EscPosTransport.write(out,raster,policy)
                    Log.i(TAG,"transport stripes=${stats.stripeCount} bursts=${stats.burstCount} sent=${stats.totalBytesSent}")
                }
            }
            Log.i(TAG,"PRINT SUCCESS printer=${device.name ?: "unknown"} mac=$address profile=${profile.label} type=$jobType totalBytes=$totalBytes")
        }catch(t:Throwable){
            val transport=t as? PrintTransportException
            Log.e(TAG,"PRINT FAILURE printer=${device.name ?: "unknown"} mac=$address profile=${profile.label} type=$jobType stripe=${transport?.stripeIndex ?: -1} sent=${transport?.bytesSent ?: 0} totalBytes=$totalBytes",t)
            throw t
        }
        Unit
    }.recoverCatching { error ->
        if(error is SecurityException)throw IllegalStateException("BLUETOOTH_PERMISSION_REVOKED",error)
        throw error
    }
    }

    internal fun permissionRevokedFailure():Result<Unit> =
        Result.failure(IllegalStateException("BLUETOOTH_PERMISSION_REVOKED"))
}

data class PrintTransportStats(val stripeCount:Int,val burstCount:Int,val totalBytesSent:Int)
class PrintTransportException(val stripeIndex:Int,val bytesSent:Int,cause:Throwable):Exception("TRANSPORT_FAILED stripe=$stripeIndex sent=$bytesSent",cause)

object EscPosTransport{
    private val RESET=byteArrayOf(0x1B,0x40,0x1B,0x61,0x00,0x1B,0x32,0x1D,0x4C,0x00,0x00,0x1B,0x21,0x00)
    internal fun trailingCommand(policy:PrinterTransportProfile):ByteArray = if(policy.hasAutoCutter){
        byteArrayOf(0x1B,0x64,policy.trailingFeedLines.toByte(),0x1D,0x56,0x00)
    }else byteArrayOf(0x1B,0x64,policy.trailingFeedLines.toByte())
    fun estimatedBytes(commands:List<ByteArray>,policy:PrinterTransportProfile)=RESET.size+commands.sumOf{it.size}+trailingCommand(policy).size

    fun write(out:OutputStream,commands:List<ByteArray>,policy:PrinterTransportProfile,sleep:(Long)->Unit={Thread.sleep(it)}):PrintTransportStats{
        require(commands.isNotEmpty()){ "RASTER_EMPTY" }
        var sent=0;var burstBytes=0;var bursts=0;var stripeIndex=-1
        try{
            out.write(RESET);out.flush();sent+=RESET.size
            commands.forEachIndexed{index,command->
                stripeIndex=index
                require(command.size<=policy.burstBytes){"STRIPE_EXCEEDS_BURST:${command.size}"}
                out.write(command);out.flush();sent+=command.size;burstBytes+=command.size
                if(policy.delayPerStripeMs>0)sleep(policy.delayPerStripeMs)
                if(burstBytes>=policy.burstBytes&&index<commands.lastIndex){
                    bursts++;burstBytes=0
                    if(policy.delayPerBurstMs>0)sleep(policy.delayPerBurstMs)
                }
            }
            val trailing=trailingCommand(policy)
            out.write(trailing);out.flush();sent+=trailing.size
