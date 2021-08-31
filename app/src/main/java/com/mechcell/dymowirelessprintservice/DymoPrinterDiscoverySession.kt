package com.mechcell.dymowirelessprintservice

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintService.EXTRA_CAN_SELECT_PRINTER
import android.printservice.PrintService.PRINT_SERVICE
import android.printservice.PrinterDiscoverySession
import com.google.gson.Gson
import com.mechcell.dymowirelessprintservice.connection.DymoPrinterConnectionManager
import com.mechcell.dymowirelessprintservice.dymo.DymoLabels
import com.mechcell.dymowirelessprintservice.dymo.DymoPrinterStatus
import com.mechcell.dymowirelessprintservice.model.NetworkPrinterInfo
import com.mechcell.dymowirelessprintservice.ui.AddPrinterActivity
import com.mechcell.dymowirelessprintservice.ui.PrinterInfoActivity
import com.mechcell.nomad.dymo_printing_service.DiscoveryFailedException
import com.mechcell.nomad.dymo_printing_service.utils.*
import com.pixplicity.easyprefs.library.Prefs
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

class DymoPrinterDiscoverySession(
    private val service: DymoPrintService,
    private val nsdManager: NsdManager,
    private val connectionManager: DymoPrinterConnectionManager,
    private val timeout: Long
) : PrinterDiscoverySession() {

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when (intent.action) {
                ACTION_REQUEST_PRINTERS -> {
                    val responseIntent = Intent().apply {
                        action = ACTION_PROVIDE_PRINTERS
                        putExtra(PRINTERS_EXTRA, printersMap)
                    }
                    service.sendBroadcast(responseIntent)
                }
                ACTION_SELECT_PRINTER -> {
                    val printerId = intent.extras?.get(SELECTED_PRINTER_EXTRA) as PrinterId?
                    if (printerId != null) {
                        connectionManager.selectPrinter(printerId)
                    }
                }
                PRINT_SERVICE -> {
                    log("Print Service $intent")
                }
            }
        }
    }


    private val serviceType = "_pdl-datastream._tcp"

    private var discoveryListener: NsdDiscoveryListener? = null


    private var printerStatusObserver: Disposable? = null

    private val printersMap = HashMap<PrinterId, NetworkPrinterInfo>()

    override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
//        priorityList.forEach{ printerId ->
//            //TODO check if printer is valid
//        }

        val discoveryObservable = Observable.create<NetworkPrinterInfo> { emitter ->
            discoveryListener =
                NsdDiscoveryListener(nsdManager, service, emitter, timeout)
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }
        discoveryObservable!!
            .distinct()
            .toList()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ printers ->
                log("Found printers $printers")
                printers.map { printer -> Pair(printer.info.id, printer) }
                    .forEach { pair -> printersMap[pair.first] = pair.second }
                printers.firstOrNull()?.let {
                    storePrinterToPrefs(it)
                }
                addPrinters(printers.map { printer -> printer.info }.toList())
            }, {})
    }

    override fun onStopPrinterDiscovery() {
        discoveryListener?.stopDiscovery()
    }

    override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {
        val printersToRemove = ArrayList<PrinterId>()
        val printers = printerIds.mapNotNull { id -> printersMap[id] }.toList()
        connectionManager.checkIfPrintersExists(printers)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete {
                removePrinters(printersToRemove)
            }
            .subscribe(
                { printerExistsPair ->
                    if (!printerExistsPair.first) {
                        printersMap.remove(printerExistsPair.second)
                        printersToRemove.add(printerExistsPair.second)
                    }
                },
                { e ->
                    e.printStackTrace()
                    log("Error on printer validation: ${e.message}")
                }
            )
    }

    override fun onStartPrinterStateTracking(printerId: PrinterId) {
        if (printersMap.contains(printerId)) {
            val printer = printersMap[printerId]!!
            log("onStartPrinterStateTracking printer found: ${printer.name}")
            if (printer.name.contains("Dymo", true)) {
                updatePrinterCapabilities(printer)
                storePrinterToPrefs(printer)
                connectionManager.connectTo(printer)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { status ->
                            log("Connected to ${printer.name} @ ${printer.ip}:${printer.port}| status: $status")

                        },
                        { error ->
                            error.printStackTrace()
                            log("ERROR: ${error.message}")
                        }
                    )
                printerStatusObserver?.dispose()
                printerStatusObserver = connectionManager.getStatusInterval(printer)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { status ->
                            printer.info = PrinterInfo.Builder(printer.info).setStatus(
                                when (status) {
                                    DymoPrinterStatus.READY -> PrinterInfo.STATUS_IDLE
                                    DymoPrinterStatus.OUT_OF_PAPER,
                                    DymoPrinterStatus.JAMMED,
                                    DymoPrinterStatus.UNAVAILABLE,
                                    null -> PrinterInfo.STATUS_UNAVAILABLE
                                    DymoPrinterStatus.BUSY -> PrinterInfo.STATUS_BUSY
                                }
                            ).build()
                            addPrinters(listOf(printer.info))
                        },
                        { e ->
                            log("Error: ${e.message}")
                        }
                    )
                Completable.create {
                    addPrinters(listOf(printer.info))
                }.subscribeOn(AndroidSchedulers.mainThread()).subscribe()

            } else {
                log("Error trying to track an unknown printer ${printer.name}.")
            }
        } else {
            log("Error trying to track a printer not discovered. $printerId")
        }
    }

    private fun storePrinterToPrefs(printer: NetworkPrinterInfo) {
        Prefs.putString(PRINTER_NAME_PREF, printer.name)
        Prefs.putString(PRINTER_IP_PREF, printer.ip)
        Prefs.putInt(PRINTER_PORT_PREF, printer.port)
        Prefs.putLong(PRINTER_ADDED_PREF, System.currentTimeMillis())
        log("Saved printer ${printer.name}")
    }

    override fun onStopPrinterStateTracking(printerId: PrinterId) {
        val printer = printersMap[printerId]
        if (printer != null) {
            connectionManager.stopStatusInterval(printer)
            printerStatusObserver?.dispose()
        }
    }

    override fun onDestroy() {
        connectionManager.tear()
    }

    private fun updatePrinterCapabilities(printer: NetworkPrinterInfo) {
        printer.info =
            addPrinterCapabilities(printer.info.id, PrinterInfo.Builder(printer.info)).build()
    }

    fun tryToHotStartPrinter(printerName: String, printerIp: String, printerPort: Int) {
        val id = service.generatePrinterId(printerName)
        connectionManager.checkIfSavedPrinterExists(id, printerIp, printerPort)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { exists ->
                    if (exists) {
                        val intent = Intent(service, AddPrinterActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra(EXTRA_CAN_SELECT_PRINTER, true)
                        }
                        val infoIntent = PendingIntent.getActivity(service, 0, intent, 0)
                        val builder = PrinterInfo.Builder(id, printerName, PrinterInfo.STATUS_IDLE)
                            .setInfoIntent(infoIntent)
                        val info = addPrinterCapabilities(id, builder).build()
                        printersMap[id] =
                            NetworkPrinterInfo(printerName, printerIp, printerPort, info)
                        addPrinters(listOf(info))
                        log("Added saved printer $printerName")
                    } else {
                        log("Saved printer doesn't exist now")
                    }
                },
                {
                    log("Couldn't hot start saved printer.")
                }
            )
    }


    private class NsdDiscoveryListener(
        private val nsdManager: NsdManager,
        private val service: DymoPrintService,
        private val emitter: ObservableEmitter<NetworkPrinterInfo>,
        private val timeout: Long
    ) : NsdManager.DiscoveryListener {

        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
            log("onServiceFound = ${Gson().toJson(serviceInfo)} , $serviceInfo")
            if(serviceInfo?.serviceName?.contains("DYMO", true) == true){
                nsdManager.resolveService(serviceInfo, NsdResolveListener(service, emitter))
            }
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            log("onStopDiscoveryFailed")
            stopDiscovery()
            emitter.onError(DiscoveryFailedException("Discovery failed during onDiscoveryStopped. ErrorCode: $errorCode"))
        }

        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            log("onStartDiscoveryFailed")
            stopDiscovery()
            emitter.onError(DiscoveryFailedException("Discovery failed during onDiscoveryStarted. ErrorCode: $errorCode"))
        }

        override fun onDiscoveryStarted(serviceType: String?) {
            Handler().postDelayed({ stopDiscovery() }, timeout)
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            log("onDiscoveryStopped")
            emitter.onComplete()
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            log("onServiceLost = ${Gson().toJson(serviceInfo)}")
        }

        fun stopDiscovery() {
            try {
                nsdManager.stopServiceDiscovery(this)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }

    private class NsdResolveListener(
        private val service: DymoPrintService,
        private val emitter: ObservableEmitter<NetworkPrinterInfo>
    ) : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            log("onResolveFailed = ${Gson().toJson(serviceInfo)}")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            log("onServiceResolved = ${Gson().toJson(serviceInfo)}")

            Single.just(serviceInfo.serviceName)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSuccess { printerName ->
                    //if(printerName.contains("DYMO", true)){
                        val id = service.generatePrinterId(printerName)
                        Single.just(id)
                            .observeOn(Schedulers.io())
                            .doOnSuccess { printerId ->
                                val intent = Intent(service, PrinterInfoActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    putExtra(EXTRA_CAN_SELECT_PRINTER, true)
                                    putExtra(PRINTER_ID_EXTRA, printerId)
                                    putExtra(PRINTER_IP_EXTRA, serviceInfo.host.hostName)
                                    putExtra(PRINTER_NAME_EXTRA, serviceInfo.serviceName)
                                    putExtra(PRINTER_PORT_EXTRA, serviceInfo.port)
                                }
                                val infoIntent = PendingIntent.getActivity(service, 0, intent, 0)
                                val builder = PrinterInfo.Builder(
                                    printerId,
                                    serviceInfo.serviceName,
                                    PrinterInfo.STATUS_IDLE
                                )
                                    .setInfoIntent(infoIntent)
                                val info = addPrinterCapabilities(printerId, builder).build()
                                emitter.onNext(
                                    NetworkPrinterInfo(
                                        serviceInfo.serviceName,
                                        serviceInfo.host.hostName,
                                        serviceInfo.port,
                                        info
                                    )
                                )
                                log("onServiceResolved and Completed = ${Gson().toJson(serviceInfo)}")
                            }
                            .subscribe()
                    //}
                }
                .subscribe()
        }

    }


}

fun addPrinterCapabilities(
    printerId: PrinterId,
    builder: PrinterInfo.Builder
): PrinterInfo.Builder {
    val printerCapabilitiesInfoBuilder = PrinterCapabilitiesInfo.Builder(printerId)
    DymoLabels.setupPrinter(printerCapabilitiesInfoBuilder)
    builder.setCapabilities(printerCapabilitiesInfoBuilder.build())
    return builder
}