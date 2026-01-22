package com.hellmannratti.vcr.framework

/**
 * Legacy wiring preserved for history.
 *
 * SessionKit owns the OkHttp plumbing now; the app should not build clients via this object.
 */
@Deprecated("Use SessionKit (app.sessionKit) for recording/replay plumbing")
object HttpClients
