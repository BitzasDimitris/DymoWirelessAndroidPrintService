package com.mechcell.dymowirelessprintservice.dymo

import android.print.PrintAttributes
import android.print.PrintAttributes.MediaSize
import android.print.PrinterCapabilitiesInfo
import com.mechcell.nomad.dymo_printing_service.utils.byteFromInt
import com.mechcell.nomad.dymo_printing_service.utils.mmToMils

class DymoLabels {
    @Suppress("MemberVisibilityCanBePrivate")
    companion object {

        const val RES_FIX = 300.0f / 72.0f

        val Address30252 = Label("Address30252", 259, 28, 89, 329, 2100, 18, 138)

        val Address30320 = Label("Address30320", 260, 28, 89, 329, 2100, 18, 138)

        val HandingFileInsert30376 =
            Label("HandingFileInsert30376", 261, 28, 51, 330, 1200, 36, 188)

        val StandardAddress99010 = Label("StandardAddress99010", 262, 28, 89, 329, 2100, 18, 138)

        val Shipping30256 = Label("Shipping30256", 263, 59, 102, 694, 2400, 17, 140)

        val Shipping99014 = Label("Shipping99014", 264, 54, 102, 638, 2382, 18, 128)

        val NameBadgeLabel99014 = Label("NameBadgeLabel99014", 265, 54, 101, 638, 2382, 18, 128)

        val Shipping30323 = Label("Shipping30323", 266, 54, 101, 638, 2382, 18, 128)

        val PCPostage3Part30383 = Label("PCPostage3Part30383", 267, 57, 178, 675, 4200, 17, 132)

        val PCPostage2Part30384 = Label("PCPostage2Part30384", 268, 59, 191, 694, 4500, 17, 132)

        val Diskette20258 = Label("Diskette20258", 270, 54, 70, 638, 1650, 17, 132)

        val Diskette99015 = Label("Diskette99015", 271, 54, 70, 638, 1650, 12, 132)

        val Diskette30324 = Label("Diskette30324", 272, 54, 70, 638, 1650, 12, 132)

        val ReturnAddress30330 = Label("ReturnAddress30330", 273, 19, 51, 225, 1200, 17, 136)

        val Address2Up30253 = Label("Address2Up30253", 274, 59, 89, 693, 2100, 17, 136)

        val FileFolder2Up30277 = Label("FileFolder2Up30277", 275, 29, 87, 338, 2062, 18, 120)

        val FileFolder30327 = Label("FileFolder30327", 276, 20, 87, 235, 2062, 0, 132)

        val Zipdisk30370 = Label("Zipdisk30370", 277, 51, 60, 600, 1406, 18, 132)

        val LargeAddress30321 = Label("LargeAddress30321", 278, 36, 89, 422, 2092, 18, 134)

        val LargeAddress99012 = Label("LargeAddress99012", 279, 36, 89, 422, 2092, 18, 134)

        val NameBadgeLabel30364 = Label("NameBadgeLabel30364", 280, 59, 102, 694, 2400, 17, 140)

        val NameBadgeCard30365 = Label("NameBadgeCard30365", 281, 59, 89, 696, 2100, 0, 224)

        val AppointmantCard30374 = Label("AppointmantCard30374", 282, 51, 89, 600, 2100, 36, 224)

        //TODO add the rest from https://github.com/minlux/dymon/blob/master/doc/paper_size.md


        private fun fillPrinterMediaSizes(capBuilder: PrinterCapabilitiesInfo.Builder) {
            capBuilder.addMediaSize(Address30252.mediaSize, false)
            capBuilder.addMediaSize(Address30320.mediaSize, false)
            capBuilder.addMediaSize(HandingFileInsert30376.mediaSize, false)
            capBuilder.addMediaSize(StandardAddress99010.mediaSize, false)
            capBuilder.addMediaSize(Shipping30256.mediaSize, false)
            capBuilder.addMediaSize(Shipping99014.mediaSize, false)
            capBuilder.addMediaSize(NameBadgeLabel99014.mediaSize, false)
            capBuilder.addMediaSize(Shipping30323.mediaSize, true)
            capBuilder.addMediaSize(PCPostage3Part30383.mediaSize, false)
            capBuilder.addMediaSize(PCPostage2Part30384.mediaSize, false)
            capBuilder.addMediaSize(Diskette20258.mediaSize, false)
            capBuilder.addMediaSize(Diskette99015.mediaSize, false)
            capBuilder.addMediaSize(Diskette30324.mediaSize, false)
            capBuilder.addMediaSize(ReturnAddress30330.mediaSize, false)
            capBuilder.addMediaSize(Address2Up30253.mediaSize, false)
            capBuilder.addMediaSize(FileFolder2Up30277.mediaSize, false)
            capBuilder.addMediaSize(FileFolder30327.mediaSize, false)
            capBuilder.addMediaSize(Zipdisk30370.mediaSize, false)
            capBuilder.addMediaSize(LargeAddress30321.mediaSize, false)
            capBuilder.addMediaSize(LargeAddress99012.mediaSize, false)
            capBuilder.addMediaSize(NameBadgeLabel30364.mediaSize, false)
            capBuilder.addMediaSize(NameBadgeCard30365.mediaSize, false)
            capBuilder.addMediaSize(AppointmantCard30374.mediaSize, false)
        }

        private fun fillPrinterResolution(capBuilder: PrinterCapabilitiesInfo.Builder) {
            capBuilder.addResolution(
                PrintAttributes.Resolution(
                    "TextQuality",
                    "TextQuality",
                    300,
                    300
                ), false
            )
            capBuilder.addResolution(
                PrintAttributes.Resolution(
                    "GraphicsQuality",
                    "GraphicsQuality",
                    300,
                    600
                ), true
            )
        }

        fun setupPrinter(capBuilder: PrinterCapabilitiesInfo.Builder) {
            fillPrinterMediaSizes(capBuilder)
            fillPrinterResolution(capBuilder)
            capBuilder.setColorModes(
                PrintAttributes.COLOR_MODE_MONOCHROME,
                PrintAttributes.COLOR_MODE_MONOCHROME
            )
            capBuilder.setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            capBuilder.setDuplexModes(
                PrintAttributes.DUPLEX_MODE_NONE,
                PrintAttributes.DUPLEX_MODE_NONE
            )
        }
    }

    class Label(
        val labelName: String,
        val optionId: Int,
        val formatWidth: Int,
        val formatHeight: Int,
        val pageWidth: Int,
        val pageHeight: Int,
        val printableOriginWidth: Int,
        val printableOriginHeight: Int,

        ) {
        val lengthCommand: ByteArray
            get() {
                val bArray = ByteArray(4)
                val length = pageHeight / 2 + 300
                val cmd = getLabelLengthCommand()
                bArray[0] = cmd[0]
                bArray[1] = cmd[1]
                bArray[2] = byteFromInt(length, 1)
                bArray[3] = byteFromInt(length, 0)
                return bArray
            }
        val mediaSize: MediaSize
            get() {
                return MediaSize(
                    labelName,
                    labelName,
                    ((mmToMils(formatWidth) - printableOriginWidth) * RES_FIX).toInt(),
                    ((mmToMils(formatHeight) - 3 * printableOriginHeight) * RES_FIX).toInt()
                )
            }
    }
}

