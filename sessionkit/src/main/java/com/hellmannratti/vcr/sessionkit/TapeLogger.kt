package com.hellmannratti.vcr.sessionkit

/**
 * Platform-agnostic logger for SessionKit.
 * Provide an implementation in the host app (e.g., delegating to android.util.Log).
 */
interface TapeLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String)

    object NoOp : TapeLogger {
        override fun d(tag: String, message: String) {}
        override fun i(tag: String, message: String) {}
        override fun w(tag: String, message: String) {}
        override fun e(tag: String, message: String) {}
    }
}
