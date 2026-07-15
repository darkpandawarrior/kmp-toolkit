package com.siddharth.kmp.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

actual fun httpClientEngine(): HttpClientEngine = CIO.create()
