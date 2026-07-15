package com.siddharth.kmp.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

actual fun httpClientEngine(): HttpClientEngine = Js.create()
