package com.hellmannratti.zombie.replay

/**
 * Exception thrown when replay mode is active but no tape file is found or tape is empty.
 */
class NoTapeFoundException(message: String) : Exception(message)
