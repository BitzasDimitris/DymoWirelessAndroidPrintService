package com.mechcell.dymowirelessprintservice.connection

import android.print.PrinterId
import com.mechcell.dymowirelessprintservice.dymo.DymoPrinterStatus
import com.mechcell.dymowirelessprintservice.model.NetworkPrinterInfo
import com.mechcell.nomad.dymo_printing_service.utils.log
import com.mechcell.nomad.dymo_printing_service.utils.optionalOnError
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.IOException
import java.net.ConnectException
import java.net.Socket
import java.net.UnknownHostException

class DymoPrinterConnectionManager() {

    private var connectionMap = HashMap<PrinterId, Connection>()

    private var selectedPrinter: Connection? = null


    fun selectPrinter(printerId: PrinterId) {
        selectedPrinter = connectionMap[printerId]
    }

    fun connectTo(networkPrinter: NetworkPrinterInfo): Single<ConnectionStatus> {
        return Single.create { emitter ->
            if (connectionMap.containsKey(networkPrinter.info.id) && connectionMap[networkPrinter.info.id]?.isConnected == true) {
                emitter.onSuccess(ConnectionStatus.CONNECTED)
            } else {
                try {
                    val socket = Socket(networkPrinter.ip, networkPrinter.port)
                    connectionMap[networkPrinter.info.id] = Connection(socket)
                    emitter.onSuccess(ConnectionStatus.CONNECTED)
                } catch (e: UnknownHostException) {
                    optionalOnError(emitter, e)
                } catch (e: ConnectException) {
                    optionalOnError(emitter, e)
                } catch (e: IOException) {
                    optionalOnError(emitter, e)
                }
            }
        }
    }

    fun getStatusInterval(printer: NetworkPrinterInfo): Observable<DymoPrinterStatus> {
        val printerId = printer.info.id
        return if (connectionMap.containsKey(printerId) && connectionMap[printerId]?.isConnected == true) {
            connectionMap[printerId]!!.startTracking()
        } else {
            Observable.just(DymoPrinterStatus.UNAVAILABLE)
        }
    }

    fun stopStatusInterval(printer: NetworkPrinterInfo) {
        val printerId = printer.info.id
        if (connectionMap.containsKey(printerId) && connectionMap[printerId]?.isConnected == true) {
            connectionMap[printerId]!!.stopTracking()
        }
    }


    //must be on io schedulers
    fun sendCommand(printer: NetworkPrinterInfo, commandByteArray: ByteArray) {
        val printerId = printer.info.id
        if (connectionMap.containsKey(printerId) && connectionMap[printerId]?.isConnected == true) {
            connectionMap[printerId]!!.sendCommand(commandByteArray)
        }
    }

    fun sendCommand(printerId: PrinterId?, commandByteArray: ByteArray) {
        if (printerId != null) {
            if (connectionMap.containsKey(printerId) && connectionMap[printerId]?.isConnected == true) {
                connectionMap[printerId]!!.sendCommand(commandByteArray)
            }
        } else if (selectedPrinter != null) {
            selectedPrinter?.sendCommand(commandByteArray)
        } else {
            log("Error no printer is available")
        }

    }

    fun checkIfPrintersExists(printers: List<NetworkPrinterInfo>): Observable<Pair<Boolean, PrinterId>> {
        return Observable.create { emitter ->
            repeat(printers.size) { index ->
                val printer = printers[index]
                try {
                    Socket(printer.ip, printer.port).use { socket ->
                        emitter.onNext(Pair(socket.isConnected, printer.info.id))
                        emitter.onNext(Pair(socket.isConnected, printer.info.id))
                    }
                } catch (e: UnknownHostException) {
                    emitter.onNext(Pair(false, printer.info.id))
                } catch (e: ConnectException) {
                    emitter.onNext(Pair(false, printer.info.id))
                } catch (e: IOException) {
                    emitter.onNext(Pair(false, printer.info.id))
                }
            }
            emitter.onComplete()
        }

    }

    fun checkIfSavedPrinterExists(
        printerId: PrinterId,
        printerIp: String,
        printerPort: Int
    ): Single<Boolean> {
        return Single.create { emitter ->
            try {
                val socket = Socket(printerIp, printerPort)
                val connection = Connection(socket)
                connectionMap[printerId] = connection
                selectedPrinter = connection
                emitter.onSuccess(socket.isConnected)
            } catch (e: UnknownHostException) {
                emitter.onError(e)
            } catch (e: ConnectException) {
                emitter.onError(e)
            } catch (e: IOException) {
                emitter.onError(e)
            }
        }
    }

    fun tear() {
        connectionMap.values.forEach { connection ->
            if (!connection.isClosed) {
                Completable.create {
                    connection.close()
                }
                    .subscribeOn(Schedulers.io())
                    .subscribe()
            }
        }
    }

    fun getConnection(printerId: PrinterId?): Connection? {
        if (printerId != null && connectionMap.containsKey(printerId)) {
            return connectionMap[printerId]
        }
        return null
    }
}