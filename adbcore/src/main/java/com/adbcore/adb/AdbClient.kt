package com.adbcore.adb

import android.util.Log
import com.adbcore.adb.AdbProtocol.ADB_AUTH_RSAPUBLICKEY
import com.adbcore.adb.AdbProtocol.ADB_AUTH_SIGNATURE
import com.adbcore.adb.AdbProtocol.ADB_AUTH_TOKEN
import com.adbcore.adb.AdbProtocol.A_AUTH
import com.adbcore.adb.AdbProtocol.A_CLSE
import com.adbcore.adb.AdbProtocol.A_CNXN
import com.adbcore.adb.AdbProtocol.A_MAXDATA
import com.adbcore.adb.AdbProtocol.A_OKAY
import com.adbcore.adb.AdbProtocol.A_OPEN
import com.adbcore.adb.AdbProtocol.A_STLS
import com.adbcore.adb.AdbProtocol.A_STLS_VERSION
import com.adbcore.adb.AdbProtocol.A_VERSION
import com.adbcore.adb.AdbProtocol.A_WRTE
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

private const val TAG = "AdbClient"

class AdbClient(
    private val host: String,
    private val port: Int,
    private val key: AdbKey,
    private val connectTimeoutMs: Int = 2_000
) : Closeable {

    private lateinit var socket: Socket
    private lateinit var plainInputStream: DataInputStream
    private lateinit var plainOutputStream: DataOutputStream

    private var useTls = false

    private lateinit var tlsSocket: SSLSocket
    private lateinit var tlsInputStream: DataInputStream
    private lateinit var tlsOutputStream: DataOutputStream

    private val inputStream get() = if (useTls) tlsInputStream else plainInputStream
    private val outputStream get() = if (useTls) tlsOutputStream else plainOutputStream

    fun connect() {
        socket = Socket()
        socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
        socket.tcpNoDelay = true
        plainInputStream = DataInputStream(socket.getInputStream())
        plainOutputStream = DataOutputStream(socket.getOutputStream())

        write(A_CNXN, A_VERSION, A_MAXDATA, "host::")

        var message = read()
        if (message.command == A_STLS) {
            // minSdk = 30 (R),A_STLS 仅 Android 9 (Q) 之后才出现,无需再做版本判断
            write(A_STLS, A_STLS_VERSION, 0)

            val sslContext = key.sslContext
            tlsSocket = sslContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket
            tlsSocket.startHandshake()
            Log.d(TAG, "Handshake succeeded.")

            tlsInputStream = DataInputStream(tlsSocket.inputStream)
            tlsOutputStream = DataOutputStream(tlsSocket.outputStream)
            useTls = true

            message = read()
        } else if (message.command == A_AUTH) {
            // A_AUTH 分支里 server 第一帧的 arg0 必为 ADB_AUTH_TOKEN (challenge)
            if (message.arg0 != ADB_AUTH_TOKEN) error("expected A_AUTH ADB_AUTH_TOKEN, got arg0=${message.arg0}")
            write(A_AUTH, ADB_AUTH_SIGNATURE, 0, key.sign(message.data))

            message = read()
            if (message.command != A_CNXN) {
                write(A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, key.adbPublicKey)
                message = read()
            }
        }

        if (message.command != A_CNXN) error("not A_CNXN")
    }

    fun shellCommand(command: String, listener: ((ByteArray) -> Unit)?) =
        openService("shell:$command", listener)

    /**
     * 通用 ADB service 请求。adbd 内置了一系列 service:
     *
     *   - `shell:<cmd>`            执行 shell 命令(默认场景)
     *   - `tcpip:<port>`           等同于电脑端 `adb tcpip <port>`,让 adbd 切到
     *                              TCP 模式监听 0.0.0.0:port,并重启自己
     *   - `usb:`                   切回 USB 模式
     *   - `host:features`          查询 adbd 支持的特性
     *   - `framebuffer:`           截屏
     *   - 等等
     *
     * 协议层只是 [A_OPEN] 携带 `service` 字符串,然后按 [A_OKAY]/[A_WRTE]/[A_CLSE]
     * 状态机交互。**注意一些 service 会立即触发 adbd 自重启**(如 tcpip),
     * 此时当前连接会被对端关闭,本方法可能在中途抛 IOException,调用方应酌情吞掉。
     */
    fun openService(service: String, listener: ((ByteArray) -> Unit)?) {
        val localId = 1
        write(A_OPEN, localId, 0, service)

        var message = read()
        when (message.command) {
            A_OKAY -> {
                while (true) {
                    message = read()
                    val remoteId = message.arg0
                    if (message.command == A_WRTE) {
                        if (message.dataLength > 0) {
                            listener?.invoke(message.data!!)
                        }
                        write(A_OKAY, localId, remoteId)
                    } else if (message.command == A_CLSE) {
                        write(A_CLSE, localId, remoteId)
                        break
                    } else {
                        error("not A_WRTE or A_CLSE")
                    }
                }
            }
            A_CLSE -> {
                val remoteId = message.arg0
                write(A_CLSE, localId, remoteId)
            }
            else -> {
                error("not A_OKAY or A_CLSE")
            }
        }
    }

    private fun write(command: Int, arg0: Int, arg1: Int, data: ByteArray? = null) =
        write(AdbMessage(command, arg0, arg1, data))

    private fun write(command: Int, arg0: Int, arg1: Int, data: String) =
        write(AdbMessage(command, arg0, arg1, data))

    private fun write(message: AdbMessage) {
        outputStream.write(message.toByteArray())
        outputStream.flush()
        Log.d(TAG, "write ${message.toStringShort()}")
    }

    private fun read(): AdbMessage {
        val buffer = ByteBuffer.allocate(AdbMessage.HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN)

        inputStream.readFully(buffer.array(), 0, 24)

        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val dataLength = buffer.int
        val checksum = buffer.int
        val magic = buffer.int
        val data: ByteArray?
        if (dataLength >= 0) {
            data = ByteArray(dataLength)
            inputStream.readFully(data, 0, dataLength)
        } else {
            data = null
        }
        val message = AdbMessage(command, arg0, arg1, dataLength, checksum, magic, data)
        message.validateOrThrow()
        Log.d(TAG, "read ${message.toStringShort()}")
        return message
    }

    override fun close() {
        runCatching { plainInputStream.close() }
        runCatching { plainOutputStream.close() }
        runCatching { socket.close() }
        if (useTls) {
            runCatching { tlsInputStream.close() }
            runCatching { tlsOutputStream.close() }
            runCatching { tlsSocket.close() }
        }
    }
}
