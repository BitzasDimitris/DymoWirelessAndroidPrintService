package com.mechcell.dymowirelessprintservice.intercommunication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mechcell.dymowirelessprintservice.model.NetworkPrinterInfo
import com.mechcell.nomad.dymo_printing_service.utils.ACTION_PROVIDE_PRINTERS
import com.mechcell.nomad.dymo_printing_service.utils.ACTION_REQUEST_PRINTERS
import com.mechcell.nomad.dymo_printing_service.utils.PRINTERS_EXTRA
import com.mechcell.nomad.dymo_printing_service.utils.SELECTED_PRINTER_EXTRA
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.SingleSubject

class DymoPrintServiceActivityReceiver : BroadcastReceiver() {
    private val printersSubject: SingleSubject<List<NetworkPrinterInfo>> = SingleSubject.create()

    override fun onReceive(context: Context?, intent: Intent) {
        when (intent.action) {
            ACTION_PROVIDE_PRINTERS -> {
                val list = intent.extras?.get(PRINTERS_EXTRA) as List<*>
                val printers =
                    list.mapNotNull { printer -> printer as NetworkPrinterInfo? }.toList()
                printersSubject.onSuccess(printers)
            }
        }
    }

    fun onPrintersReceived(): Observable<NetworkPrinterInfo> {
        return printersSubject.flattenAsObservable { printers -> printers.asIterable() }
    }

    fun requestPrinters(context: Context) {
        context.sendBroadcast(Intent().apply { action = ACTION_REQUEST_PRINTERS })
    }

    fun selectPrinter(context: Context, printer: NetworkPrinterInfo) {
        val intent = Intent().apply {
            action = ACTION_REQUEST_PRINTERS
            putExtra(SELECTED_PRINTER_EXTRA, printer.info.id)
        }
        context.sendBroadcast(intent)
    }
}