package com.mechcell.dymowirelessprintservice.ui

import android.content.Intent
import android.os.Bundle
import android.printservice.PrintService.EXTRA_SELECT_PRINTER
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mechcell.dymowirelessprintservice.databinding.AddPrinterActivityBinding
import com.mechcell.dymowirelessprintservice.databinding.PrinterViewHolderBinding
import com.mechcell.dymowirelessprintservice.intercommunication.DymoPrintServiceActivityReceiver
import com.mechcell.dymowirelessprintservice.model.NetworkPrinterInfo

class AddPrinterActivity : AppCompatActivity() {


    private lateinit var binding: AddPrinterActivityBinding

    private var serviceCommunication = DymoPrintServiceActivityReceiver()

    private val printersAdapter = PrintersAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AddPrinterActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        binding.printerList.adapter = printersAdapter
        binding.printerList.layoutManager = LinearLayoutManager(this)
        binding.printerList.setHasFixedSize(true)
        binding.printerList.addItemDecoration(
            DividerItemDecoration(
                this,
                DividerItemDecoration.VERTICAL
            )
        )

        binding.findPrinters.setOnClickListener {
            serviceCommunication.requestPrinters(this)
            serviceCommunication.onPrintersReceived().subscribe { printer ->
                printersAdapter.addPrinter(printer)
            }
        }
        setResult(RESULT_OK, Intent().apply { putExtra(EXTRA_SELECT_PRINTER, true) })
    }

    inner class PrintersAdapter : RecyclerView.Adapter<PrinterViewHolder>() {
        private val printers = ArrayList<NetworkPrinterInfo>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrinterViewHolder {
            val binding = PrinterViewHolderBinding.inflate(layoutInflater, parent, false)
            return PrinterViewHolder(binding)
        }

        override fun onBindViewHolder(holder: PrinterViewHolder, position: Int) {
            if (position < printers.size) {
                holder.bind(printers[position])
            }
        }

        override fun getItemCount() = printers.size

        fun addPrinter(printer: NetworkPrinterInfo) {
            printers.add(printer)
            notifyItemInserted(printers.size - 1)
        }

    }


    inner class PrinterViewHolder(private val binding: PrinterViewHolderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(printer: NetworkPrinterInfo) {
            binding.printerName.text = printer.name
            binding.printerIp.text = printer.ip
            binding.printerPort.text = printer.port.toString()
            binding.root.setOnClickListener {
                serviceCommunication.selectPrinter(this@AddPrinterActivity, printer)
            }
        }
    }

}