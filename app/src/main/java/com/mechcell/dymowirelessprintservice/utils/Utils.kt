package com.mechcell.nomad.dymo_printing_service.utils


import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.math.roundToInt

const val BUFFER = 8 * 1024

internal fun OutputStream.write(inputStream: InputStream) {
    inputStream.use { it.copyTo(this, bufferSize = BUFFER) }
}

internal fun OutputStream.write(file: File) {
    file.inputStream().use { it.copyTo(this, bufferSize = BUFFER) }
}

fun log(message: String, tag: String = "PrinterService") {
    Log.d(tag, "┌================================================================================")
    Log.d(tag, "│$message")
    Log.d(tag, "└================================================================================")
}

fun byteFromInt(input: Int, offset: Int = 0): Byte {
    return ((input ushr (offset * 8)) and 0xFFFF).toByte()
}

fun byteArrayToString(input: ByteArray): String {
    return byteArrayToString(input.toList())
}

fun byteArrayToString(input: List<Byte>): String {
    val sb = StringBuilder()
    input.forEach { byte -> sb.append(String.format("0x%02X ", byte)) }
    return sb.toString()
}

fun mmToMils(mm: Int): Int {
    return (mm * 39.3700787).roundToInt()
}

val scopeName = object {}.javaClass.enclosingMethod?.name ?: "unknownScope"


fun ByteBuffer.toTrimmedArray(): ByteArray {
    val bArray = ByteArray(this.capacity())
    for (index in 0..this.capacity()) {
        bArray[index] = this[index]
    }
    return bArray
}


