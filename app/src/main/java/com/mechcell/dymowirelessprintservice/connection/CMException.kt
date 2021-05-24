package com.mechcell.dymowirelessprintservice.connection

import java.net.Socket


open class CMException(e: Exception?) : Exception() {
    init {
        e?.printStackTrace()
    }
}

data class CMUnknownHostException(private val socket: Socket, private val error: Exception?) :
    CMException(error) {
    override val message =
        "Unknown host: ${socket.inetAddress}:${socket.port} ${if (!error?.message.isNullOrEmpty()) "| ${error?.message}" else ""}"
}

data class CMAlreadyConnected(private val socket: Socket, private val error: Exception?) :
    CMException(error) {
    override val message =
        "Already connected to: ${socket.inetAddress}:${socket.port} ${if (!error?.message.isNullOrEmpty()) "| ${error?.message}" else ""}"
}


data class CMConnectException(private val socket: Socket, private val error: Exception?) :
    CMException(error) {
    override val message =
        "Failed to connect: ${socket.inetAddress}:${socket.port} ${if (!error?.message.isNullOrEmpty()) "| ${error?.message}" else ""}"
}

data class CMIOException(private val socket: Socket, private val error: Exception?) :
    CMException(error) {
    override val message =
        "IO exception while connecting: ${socket.inetAddress}:${socket.port} ${if (!error?.message.isNullOrEmpty()) "| ${error?.message}" else ""}"
}

data class CMInvalidStateException(
    private val functionName: String,
    private val status: ConnectionStatus,
    private val error: Exception?
) : CMException(error) {
    override val message =
        "Connection Manager invalid state: trying to call $functionName while connection is ${status.name} ${if (!error?.message.isNullOrEmpty()) "| ${error?.message}" else ""}"
}