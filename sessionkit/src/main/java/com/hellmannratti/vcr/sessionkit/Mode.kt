package com.hellmannratti.vcr.sessionkit

import java.io.File

/** Host app selects mode; SessionKit stays independent of UI framework. */
enum class Mode { RECORD, REPLAY, PASSTHROUGH }

data class SessionKitConfig(
    val mode: Mode,
    val tapeFile: File? = null,
    val appVersion: String = "unknown",
    val device: String = "unknown"
)
