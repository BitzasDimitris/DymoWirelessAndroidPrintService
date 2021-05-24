package com.mechcell.nomad.dymo_printing_service

sealed class PrintException : Exception()

data class DiscoveryFailedException(override val message: String) : PrintException()