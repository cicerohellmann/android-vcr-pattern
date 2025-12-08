package com.hellmannratti.zombie.replay

/** Host app selects mode; library stays platform-agnostic. */
enum class Mode { RECORD, REPLAY, PASSTHROUGH }

data class AppConfig(
    val mode: Mode,
    val tapeFile: java.io.File? = null
)
