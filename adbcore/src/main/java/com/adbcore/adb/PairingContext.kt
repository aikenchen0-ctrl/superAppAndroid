package com.adbcore.adb

/**
 * JNI bridge to libadb.so SPAKE2 pairing context.
 *
 * IMPORTANT: native code in adb_pairing.cpp registers methods against
 * "com/adbcore/adb/PairingContext" — do NOT move or rename this class
 * without updating the JNI class path in libadb.so.
 */
internal class PairingContext private constructor(private val nativePtr: Long) {

    val msg: ByteArray = nativeMsg(nativePtr)

    fun initCipher(theirMsg: ByteArray) = nativeInitCipher(nativePtr, theirMsg)

    fun encrypt(input: ByteArray): ByteArray? = nativeEncrypt(nativePtr, input)

    fun decrypt(input: ByteArray): ByteArray? = nativeDecrypt(nativePtr, input)

    fun destroy() = nativeDestroy(nativePtr)

    private external fun nativeMsg(nativePtr: Long): ByteArray
    private external fun nativeInitCipher(nativePtr: Long, theirMsg: ByteArray): Boolean
    private external fun nativeEncrypt(nativePtr: Long, inbuf: ByteArray): ByteArray?
    private external fun nativeDecrypt(nativePtr: Long, inbuf: ByteArray): ByteArray?
    private external fun nativeDestroy(nativePtr: Long)

    companion object {

        init {
            System.loadLibrary("adb")
        }

        fun create(password: ByteArray): PairingContext? {
            val nativePtr = nativeConstructor(true, password)
            return if (nativePtr != 0L) PairingContext(nativePtr) else null
        }

        @JvmStatic
        private external fun nativeConstructor(isClient: Boolean, password: ByteArray): Long
    }
}
