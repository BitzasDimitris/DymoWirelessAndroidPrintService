package com.mechcell.dymowirelessprintservice.ui

import android.content.Intent
import android.os.Bundle
import android.print.PrinterId
import android.printservice.PrintService
import androidx.appcompat.app.AppCompatActivity
import com.mechcell.dymowirelessprintservice.databinding.PrinterInfoActivityBinding
import com.mechcell.nomad.dymo_printing_service.utils.*

class PrinterInfoActivity : AppCompatActivity() {


    private lateinit var binding: PrinterInfoActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PrinterInfoActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        val name = intent.extras?.get(PRINTER_NAME_EXTRA) as String? ?: ""
        val ip = intent.extras?.get(PRINTER_IP_EXTRA) as String? ?: ""
        val port = intent.extras?.get(PRINTER_PORT_EXTRA) as Int? ?: 80
        val printerId = intent.extras?.get(PRINTER_ID_EXTRA) as PrinterId?
        binding.printerName.text = name
        binding.printerIp.text = ip
        binding.printerPort.text = port.toString()

        binding.selectPrinter.setOnClickListener {
            printerId?.let {
                sendBroadcast(Intent(ACTION_SELECT_PRINTER).apply {
                    putExtra(
                        SELECTED_PRINTER_EXTRA,
                        it
                    )
                })
            }
            setResult(RESULT_OK, Intent().apply {
                putExtra(PrintService.EXTRA_SELECT_PRINTER, true)
                printerId?.let {
                    putExtra(SELECTED_PRINTER_EXTRA, it)
                }
            })
        }
    }
}