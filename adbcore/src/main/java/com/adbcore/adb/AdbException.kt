package com.adbcore.adb

/** Base exception for all ADB-related failures. */
open class AdbException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/** Pairing code rejected by adbd (wrong code / expired pairing window). */
class AdbInvalidPairingCodeException : AdbException()
