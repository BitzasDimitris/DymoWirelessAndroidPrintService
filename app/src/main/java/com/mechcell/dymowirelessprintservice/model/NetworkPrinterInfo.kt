package com.mechcell.dymowirelessprintservice.model

import android.print.PrinterInfo

data class NetworkPrinterInfo(
    val name: String,
    val ip: String,
    val port: Int,
    var info: PrinterInfo
)
