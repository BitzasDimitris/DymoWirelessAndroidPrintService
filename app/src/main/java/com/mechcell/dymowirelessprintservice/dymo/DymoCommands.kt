package com.mechcell.dymowirelessprintservice.dymo

import com.mechcell.nomad.dymo_printing_service.utils.byteFromInt

private const val ESC = 0x1B.toByte()
private const val SYN = 0x16.toChar()
private const val ETB = 0x17.toChar()


fun getLabelIndex(index: Int): ByteArray {
    val bArray = ByteArray(4)
    bArray[0] = 0x1B.toByte()
    bArray[1] = 0x6E.toByte()
    bArray[2] = index.toByte()
    bArray[3] = 0.toByte()
    return bArray
}

fun getLabelLengthCommand(): ByteArray {
    val bArray = ByteArray(2)
    bArray[0] = 0x1B.toByte()
    bArray[1] = 0x4C.toByte()
    return bArray
}

fun getLabelDimensionsCommand(height: Int, width: Int): ByteArray {
    val bArray = ByteArray(12)
    bArray[0] = 0x1B.toByte()
    bArray[1] = 0x44.toByte()
    bArray[2] = 0x01.toByte()
    bArray[3] = 0x02.toByte()
    //height
    bArray[4] = byteFromInt(height, 0)
    bArray[5] = byteFromInt(height, 1)
    bArray[6] = byteFromInt(height, 2)
    bArray[7] = byteFromInt(height, 3)
    //width
    bArray[8] = byteFromInt(width, 0)
    bArray[9] = byteFromInt(width, 1)
    bArray[10] = byteFromInt(width, 2)
    bArray[11] = byteFromInt(width, 3)
    return bArray
}

//For final label print
fun formFeedCommand(): ByteArray {
    val bArray = ByteArray(2)
    bArray[0] = ESC
    bArray[1] = 0x45.toByte()
    return bArray
}

//For intermediate print
fun shortFormFeedCommand(): ByteArray {
    val bArray = ByteArray(2)
    bArray[0] = ESC
    bArray[1] = 0x47.toByte()
    return bArray
}

//Finish session command
fun finishSessionCommand(): ByteArray {
    val bArray = ByteArray(2)
    bArray[0] = ESC
    bArray[1] = 0x51.toByte()
    return bArray
}

fun getPrinterStatus(): ByteArray {
    val array = ByteArray(3)
    array[0] = 0x1b.toByte()
    array[1] = 0x41.toByte()
    array[2] = 0x01.toByte()
    return array
}

//fun resetPrinter(): String{
//    return ESC + "@"
//}
//
//fun restoreDefaultSettings(): String{
//    return ESC + "*"
//}
//
//fun skipLines(lines: Int): String{
//    return ESC + "f" + 1.toByte() + lines.toByte()
//}
//
//fun getPrinterModelFirmware(): String{
//    return ESC + "V"
//}
//
//fun transferPrintData(data: ByteArray): String{
//    return SYN + byteArrayToString(data)
//}
//
//fun transferCompressedPrintData(data: ByteArray): String{
//    return ETB + byteArrayToString(data)
//}
//
//fun setTextSpeedMode(): String{
//    return ESC + "h"
//}
//
//fun setBarCodeGraphicsMode(): String{
//    return ESC + "i"
//}
//
//fun setPrintDensity(printDensity: PrintDensity): String{
//    return ESC + when(printDensity){
//        PrintDensity.LIGHT -> "c"
//        PrintDensity.MEDIUM -> "d"
//        PrintDensity.NORMAL -> "e"
//        PrintDensity.DARK -> "g"
//    }
//}

enum class PrintDensity {
    LIGHT,
    MEDIUM,
    NORMAL,
    DARK
}
