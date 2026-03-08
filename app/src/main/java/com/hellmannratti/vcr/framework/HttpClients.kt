package com.hellmannratti.vcr.framework

/**
 * Legacy wiring preserved for history.
 *
 * Cassete now installs into an app-owned OkHttpClient; this object remains only as a marker.
 */
@Deprecated("Use VcrApp.okHttpClient or VcrApp.networkClient for Cassete-enabled networking")
object HttpClients
