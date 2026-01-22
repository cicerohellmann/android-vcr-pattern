package com.hellmannratti.vcr.framework

import android.util.Log
import com.hellmannratti.vcr.sessionkit.TapeLogger

class AndroidTapeLogger : TapeLogger {
    override fun d(tag: String, message: String) { Log.d(tag, message) }
    override fun i(tag: String, message: String) { Log.i(tag, message) }
    override fun w(tag: String, message: String) { Log.w(tag, message) }
    override fun e(tag: String, message: String) { Log.e(tag, message) }
}
