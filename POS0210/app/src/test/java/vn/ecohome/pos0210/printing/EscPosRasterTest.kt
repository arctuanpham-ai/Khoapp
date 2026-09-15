package vn.ecohome.pos0210.printing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosRasterTest{
    @Test fun profilesExposeIndependentPrintableWidths(){
        assertEquals(384,PrinterProfile.MM58.printableWidthDots)
        assertEquals(576,PrinterProfile.MM80.printableWidthDots)
        assertEquals(PrinterProfile.MM58,PrinterProfile.fromSetting(null))
        assertEquals(PrinterProfile.MM80,PrinterProfile.fromSetting("80"))
    }

    @Test fun stripeHasCompleteGsV0HeaderAndPackedPixels(){
        val command=EscPosRaster.encodeStripe(16,2){x,y->(y==0&&x==0)||(y==1&&x==15)}
        assertArrayEquals(byteArrayOf(0x1D,0x76,0x30,0x00,0x02,0x00,0x02,0x00),command.copyOfRange(0,8))
        assertArrayEquals(byteArrayOf(0x80.toByte(),0x00,0x00,0x01),command.copyOfRange(8,12))
    }

    @Test fun stripePayloadIsBoundedForSmallPrinterBuffers(){
        listOf(PrinterProfile.MM58,PrinterProfile.MM80).forEach{profile->
            val command=EscPosRaster.encodeStripe(profile.printableWidthDots,profile.rasterStripeHeight){_,_->false}
            assertTrue(command.size<8*1024)
            assertEquals(8+(profile.printableWidthDots/8)*profile.rasterStripeHeight,command.size)
        }
    }
}
