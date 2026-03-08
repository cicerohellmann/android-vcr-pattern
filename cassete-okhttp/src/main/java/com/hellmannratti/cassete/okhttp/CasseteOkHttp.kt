package com.hellmannratti.cassete.okhttp

import com.hellmannratti.cassete.core.CasseteRuntime
import okhttp3.Interceptor
import okhttp3.OkHttpClient

object CasseteOkHttp {
    fun interceptor(controller: CasseteRuntime): Interceptor = CasseteOkHttpInterceptor(controller)

    fun install(builder: OkHttpClient.Builder, controller: CasseteRuntime): OkHttpClient.Builder {
        return builder.addInterceptor(interceptor(controller))
    }
}
