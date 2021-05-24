package com.mechcell.dymowirelessprintservice.dymo

import io.reactivex.rxjava3.core.Single

enum class DymoPrinterStatus {
    READY,
    OUT_OF_PAPER,
    JAMMED,
    BUSY,
    UNAVAILABLE
}

fun decodeByteArrayToStatus(bArray: ByteArray): Single<DymoPrinterStatus> {

    return if (bArray.size == 32) {
        val status = when {
            bArray[0].toInt() == 0 && bArray[15].toInt() == 0 -> DymoPrinterStatus.READY
            bArray[0].toInt() != 0 -> DymoPrinterStatus.BUSY
            bArray[15].toInt() != 0 -> DymoPrinterStatus.OUT_OF_PAPER
            else -> DymoPrinterStatus.JAMMED
        }
        Single.just(status)
    } else {
        return Single.error(Exception("Not valid array received, cant decode printer status from ${bArray.size} bytes, expected 32 bytes"))
    }
}