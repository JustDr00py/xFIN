package com.xfin.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Default User-Agent; many IPTV providers reject unknown or empty agents. */
const val DEFAULT_USER_AGENT = "xFIN/1.0 (Linux; Android) ExoPlayerLib"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.header("User-Agent") != null) {
                chain.proceed(request)
            } else {
                chain.proceed(request.newBuilder().header("User-Agent", DEFAULT_USER_AGENT).build())
            }
        }
        .build()
}
