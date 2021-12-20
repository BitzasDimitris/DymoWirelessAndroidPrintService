package com.mechcell.dymowirelessprintservice.connection

import com.mechcell.dymowirelessprintservice.dymo.DymoPrinterStatus
import com.mechcell.dymowirelessprintservice.dymo.decodeByteArrayToStatus
import com.mechcell.dymowirelessprintservice.dymo.getPrinterStatus
import com.mechcell.nomad.dymo_printing_service.utils.log
import com.mechcell.nomad.dymo_printing_service.utils.optionalOnError
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class Connection(private var socket: Socket){

    init {
        socket.keepAlive
    }

    private var printerStatusRequestInterval: Disposable? = null

    private var printerStatusListenerObservable: Observable<DymoPrinterStatus>? = null

    val isConnected
        get() = socket.isConnected
    val isClosed
        get() = socket.isClosed

    fun startTracking(): Observable<DymoPrinterStatus> {
        printerStatusRequestInterval = Observable.interval(100, 5000, TimeUnit.MILLISECONDS)
            .subscribeOn(Schedulers.io())
            .subscribe { cycle ->
                log("Get status interval $cycle")
                sendCommand(getPrinterStatus())
            }
        return startListening()
    }

    fun startListening(): Observable<DymoPrinterStatus> {
        printerStatusListenerObservable = Observable.create { emitter ->
            val resultArray = ByteArray(32)
            try {
                if (socket.isClosed) {
                    socket = Socket(socket.inetAddress, socket.port)
                }
                while (socket.isConnected && !socket.isClosed) {
                    DataInputStream(socket.getInputStream()).readFully(resultArray, 0, 32)
                    decodeByteArrayToStatus(resultArray).blockingSubscribe(
                        {
                            log("Printer status = ${it.name}")
                            emitter.onNext(it)
                        },
                        { e ->
                            emitter.onError(e)
                        }
                    )
                }
                emitter.onComplete()
            } catch (e: UnknownHostException) {
                optionalOnError(emitter, CMUnknownHostException(socket, e))
            } catch (e: ConnectException) {
                optionalOnError(emitter, CMConnectException(socket, e))
            } catch (e: IOException) {
                e.printStackTrace()
//                optionalOnError(emitter, CMIOException(socket, e))
            }
        }
        return printerStatusListenerObservable!!
    }

    fun sendCommand(commandByteArray: ByteArray) {
        if (socket.isConnected && !socket.isClosed) {
            DataOutputStream(socket.getOutputStream()).apply {
                write(commandByteArray)
                flush()
            }
        } else {
            socket = Socket(socket.inetAddress, socket.port)
            sendCommand(commandByteArray)
        }

    }

    fun stopTracking() {
        printerStatusRequestInterval?.dispose()
    }

    fun close(){
        if(socket.isConnected)
            socket.close()
    }

}
