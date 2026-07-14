package com.siddharth.kmp.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

internal actual fun httpClientEngine(): HttpClientEngine = Darwin.create()
