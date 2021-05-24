package com.mechcell.dymowirelessprintservice

import android.content.Context
import android.content.ContextWrapper
import android.net.nsd.NsdManager
import android.print.PrintJobId
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import com.mechcell.dymowirelessprintservice.connection.DymoPrinterConnectionManager
import com.mechcell.nomad.dymo_printing_service.utils.*
import com.pixplicity.easyprefs.library.Prefs

class DymoPrintService : PrintService() {
    private val timeout = 4000L

    private var workers = HashMap<PrintJobId, PrintJobWorker>()

    private val connectionManager = DymoPrinterConnectionManager()

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        val nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        val printDiscoverySession =
            DymoPrinterDiscoverySession(this, nsdManager, connectionManager, timeout)
        val printerName = Prefs.getString(PRINTER_NAME_PREF, "")
        val printerIp = Prefs.getString(PRINTER_IP_PREF, "")
        val printerPort = Prefs.getInt(PRINTER_PORT_PREF, -1)
        val printerAdded = Prefs.getLong(PRINTER_ADDED_PREF, -1L)
        if (printerName.isNotEmpty() && printerIp.isNotEmpty() && printerPort > 0 && printerAdded > 0 && System.currentTimeMillis() - printerAdded <= 300000) {
            printDiscoverySession.tryToHotStartPrinter(printerName, printerIp, printerPort)
        }

        return printDiscoverySession
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        log("Cancel PrintJob $printJob")
        workers[printJob.id]?.cancel()
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        log("PrintJob queued ${printJob.document.info.name}")
        workers[printJob.id] = PrintJobWorker(this, printJob, connectionManager).apply { execute() }
    }

    override fun onConnected() {
        super.onConnected()
        Prefs.Builder()
            .setContext(this)
            .setMode(ContextWrapper.MODE_PRIVATE)
            .setPrefsName(packageName)
            .setUseDefaultSharedPreference(true)
            .build()
    }
}