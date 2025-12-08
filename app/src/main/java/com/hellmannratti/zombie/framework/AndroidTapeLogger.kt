package com.hellmannratti.zombie.framework

import android.util.Log
import com.hellmannratti.zombie.replay.TapeLogger

class AndroidTapeLogger : TapeLogger {
    override fun d(tag: String, message: String) { Log.d(tag, message) }
    override fun i(tag: String, message: String) { Log.i(tag, message) }
    override fun w(tag: String, message: String) { Log.w(tag, message) }
    override fun e(tag: String, message: String) { Log.e(tag, message) }
}
