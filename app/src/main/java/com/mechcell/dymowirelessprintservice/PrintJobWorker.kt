package com.mechcell.dymowirelessprintservice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.printservice.PrintJob
import android.util.Log
import com.mechcell.dymowirelessprintservice.connection.DymoPrinterConnectionManager
import com.mechcell.dymowirelessprintservice.dymo.*
import com.mechcell.nomad.dymo_printing_service.utils.byteArrayToString
import com.mechcell.nomad.dymo_printing_service.utils.log
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Float.min
import java.net.ConnectException
import java.net.UnknownHostException
import kotlin.math.ceil

class PrintJobWorker(
    private val context: Context,
    private val printJob: PrintJob,
    private val connectionManager: DymoPrinterConnectionManager
) {

    private var progress = 0.0f

    private val documentFd = printJob.document.data

    private val documentInfo = printJob.document.info

    private val printerId = printJob.info.printerId

    private val printMediaSize = printJob.info.attributes.mediaSize


    fun execute() {
        printJob.start()
        Completable.create { emitter ->
            convertDocumentToBitmap()
                .subscribeOn(Schedulers.computation())
                .subscribe(
                    { indexedBitmap ->
                        printBitmap(indexedBitmap.first, indexedBitmap.second)
                            .subscribeOn(Schedulers.io())
                            .subscribe(
                                {
                                    addProgress(0.5f * 1 / documentInfo.pageCount)
                                    emitter.onComplete()
                                },
                                { e ->
                                    emitter.onError(e)
                                })
                    },
                    { e ->
                        e.printStackTrace()
                        emitter.onError(e)
                    })
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    printJob.setProgress(1.0f)
                    printJob.complete()
                    log("Print job complete.")
                }, { e ->
                    e.printStackTrace()
                    log("Error while printing: ${e.message}")
                    printJob.cancel()
                })

    }

    fun cancel() {
        printJob.cancel()
    }

    private fun convertDocumentToBitmap(): Observable<Pair<Int, Bitmap>> {
        return Observable.create { emitter ->
            try {
                if (documentFd != null) {
                    val inputStream = FileInputStream(documentFd.fileDescriptor)
                    val tempFile =
                        File.createTempFile("temp_${documentInfo.name}", ".pdf", context.cacheDir)
                    val output = FileOutputStream(tempFile)
                    output.write(inputStream.readBytes())
                    output.flush()
                    output.close()
                    inputStream.close()
                    val pdfRenderer = PdfRenderer(
                        ParcelFileDescriptor.open(
                            tempFile,
                            ParcelFileDescriptor.MODE_READ_ONLY
                        )
                    )
                    for (index in 0 until documentInfo.pageCount) {
                        val page = pdfRenderer.openPage(index)
                        val bitmap =
                            Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                        emitter.onNext(Pair(index, bitmap))
                        addProgress(0.5f * (index + 1) / documentInfo.pageCount)
                        page.close()
                    }
                    tempFile.delete()
                    pdfRenderer.close()
                    emitter.onComplete()
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
                emitter.onError(e)
            } catch (e: IOException) {
                e.printStackTrace()
                emitter.onError(e)
            }

        }
    }

    private fun printBitmap(index: Int, bitmap: Bitmap): Completable {
        var statusSubscription: Disposable? = null
        return Completable.create { emitter ->
            log("Starting printing page ${index + 1}")
            Completable.create { statusEmitter ->
                log("Sending first status command.")
                connectionManager.sendCommand(printerId, getPrinterStatus())
                statusSubscription = connectionManager.getConnection(printerId)?.startListening()
                    ?.subscribe(
                        { status ->
                            log("PrinterStatus $status")
                            statusEmitter.onComplete()
                        },
                        { e ->
                            Log.e("PrinterService", "Error waiting for first status")
                            if (statusSubscription != null && !statusSubscription!!.isDisposed) {
                                emitter.onError(e)
                            }
                        })
                if (statusSubscription == null) {
                    emitter.onError(Exception("Printer not found"))
                }

            }
                .subscribe {
                    try {
                        val matrix = Matrix()
                        matrix.setRotate(90.0f, bitmap.width / 2.0f, bitmap.height / 2.0f)
                        val printing = Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                            matrix,
                            false
                        )
                        statusSubscription?.dispose()
                        log("Printer ready for printing page ${index + 1}")
                        //debug image to fil
                        //*
                        val tempFile = File.createTempFile(
                            "temp_${documentInfo.name}",
                            ".png",
                            context.cacheDir
                        )
                        val output = FileOutputStream(tempFile)
                        printing.compress(Bitmap.CompressFormat.PNG, 100, output)
                        output.close()
                        // */

                        if (index < 1) {
                            val configuration = arrayOf(
                                0x1B, 0x73, 1, 0, 0, 0, //session preset is 1
                                0x1B, 0x43, 0x64,       //print density "normal"
                                0x1B, 0x42, 0x00,       //set dot tab to 0
                                0x1B, 0x68,             //print quality, 300x 300 dpi (text mode)
                                0x1B, 0x4D, 0, 0, 0, 0, 0, 0, 0, 0 //media type, standard
                            ).map { v -> v.toByte() }.toByteArray()
                            Log.d(
                                "PrinterService",
                                "configuration: ${byteArrayToString(configuration)}"
                            )
                            connectionManager.sendCommand(printerId, configuration)
                        }
                        //try to find label length
//                        sendLabelLengthConfiguration()

                        val labelIndexHeightWidth = ByteArray(16)
                        var cursor = 0
                        //label index command
                        Log.d(
                            "PrinterService",
                            "labelIndex: ${byteArrayToString(getLabelIndex(index + 1))}"
                        )
                        getLabelIndex(index + 1).forEach {
                            labelIndexHeightWidth[cursor++] = it
                        }


                        val adjustedWidth = ceil(printing.width / 8.0f).toInt() * 8
                        Log.d(
                            "PrinterService",
                            "height: ${printing.height}, original width: ${printing.width} adjusted: $adjustedWidth"
                        )

                        Log.d(
                            "PrinterService", "LabelDimensionsCommand: ${
                                byteArrayToString(
                                    getLabelDimensionsCommand(printing.height, adjustedWidth)
                                )
                            }"
                        )

                        getLabelDimensionsCommand(printing.height, adjustedWidth).forEach {
                            labelIndexHeightWidth[cursor++] = it
                        }

                        connectionManager.sendCommand(printerId, labelIndexHeightWidth)


                        val pixelsMap = ByteArray((adjustedWidth * printing.height) / 8)
                        val pixels = IntArray(adjustedWidth * printing.height)
                        printing.getPixels(
                            pixels,
                            0,
                            adjustedWidth,
                            0,
                            0,
                            printing.width,
                            printing.height
                        )

                        var bitString = ""
                        var bitIndex = 0
                        var mapIndex = 0
                        for (y in 0 until printing.height) {
                            for (x in 0 until adjustedWidth) {
                                val pixelVal = Color.blue(pixels[adjustedWidth * y + x])
                                bitString += if (pixelVal < 128) "1" else "0"
                                bitIndex++
                                if (bitIndex >= 8) {
                                    pixelsMap[mapIndex++] = bitString.toUByte(2).toByte()
                                    bitIndex = 0
                                    bitString = ""
                                }
                            }
                        }
                        Log.d(
                            "PrinterService",
                            "pixelMap: ${pixelsMap.size} first10: ${
                                byteArrayToString(
                                    pixelsMap.slice(IntRange(0, 10))
                                )
                            })"
                        )
                        connectionManager.sendCommand(printerId, pixelsMap)
                        Log.d("PrinterService", "send bitmap data complete")
                        if (index < documentInfo.pageCount - 1) {
                            connectionManager.sendCommand(printerId, shortFormFeedCommand())
                            Log.d(
                                "PrinterService", "shortFormFeed: ${
                                    byteArrayToString(
                                        shortFormFeedCommand()
                                    )
                                }"
                            )
                        } else {
                            val finishCommands = ByteArray(4)
                            cursor = 0
                            formFeedCommand().forEach {
                                finishCommands[cursor++] = it
                            }
                            finishSessionCommand().forEach {
                                finishCommands[cursor++] = it
                            }
                            connectionManager.sendCommand(printerId, finishCommands)
                            Log.d(
                                "PrinterService",
                                "formFeedCommand: ${byteArrayToString(finishCommands)}"
                            )
                        }
                        connectionManager.sendCommand(printerId, getPrinterStatus())
                        Log.d("PrinterService", "send final commands complete")
                        connectionManager.getConnection(printerId)?.close()
                        emitter.onComplete()
                    } catch (e: UnknownHostException) {
                        e.printStackTrace()
                        emitter.onError(e)
                    } catch (e: ConnectException) {
                        e.printStackTrace()
                        emitter.onError(e)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        emitter.onError(e)
                    }
                }
        }
    }

    private fun sendLabelLengthConfiguration() {
        val sizeLabel = printMediaSize?.getLabel(context.packageManager)
        if (!sizeLabel.isNullOrEmpty()) {
            try {
                val label =
                    DymoLabels::class.java.getDeclaredField(sizeLabel).apply { isAccessible = true }
                        .get(DymoLabels::class.java) as DymoLabels.Label?
                if (label != null) {
                    connectionManager.sendCommand(printerId, label.lengthCommand)
                    Log.d(
                        "PrinterService",
                        "Label found and lengthCommand: ${byteArrayToString(label.lengthCommand)}"
                    )
                } else {
                    //Pass maximum label length we can
                    connectionManager.sendCommand(printerId, getLabelLengthCommand())
                    connectionManager.sendCommand(
                        printerId,
                        byteArrayOf(0xFF.toByte(), 0xFF.toByte())
                    )
                }
            } catch (e: SecurityException) {
                // if a security manager, s, is present [and restricts the access to
                // the field]
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                // if the underlying field is inaccessible
                e.printStackTrace()
            } catch (e: NoSuchFieldException) {
                // if a field with the specified name is not found
                e.printStackTrace()
            } finally {
                //Pass maximum label length we can
                connectionManager.sendCommand(printerId, getLabelLengthCommand())
                connectionManager.sendCommand(printerId, byteArrayOf(0xFF.toByte(), 0xFF.toByte()))
            }
        } else {
            //Pass maximum label length we can
            connectionManager.sendCommand(printerId, getLabelLengthCommand())
            connectionManager.sendCommand(printerId, byteArrayOf(0xFF.toByte(), 0xFF.toByte()))
            byteArrayOf()
        }
    }

    private fun addProgress(part: Float) {
        progress += part
        progress = min(progress, 1.0f)
        Completable.create {
            printJob.setProgress(progress)
        }
            .subscribeOn(AndroidSchedulers.mainThread())
            .subscribe()
    }
}