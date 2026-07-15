package com.siddharth.kmp.appshell

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Local persistence for the push token + subscribed topics. Platform impls push tokens here; topic
 * subscribe/unsubscribe is local bookkeeping — remote registration against a backend is the app's job.
 */
interface PushTokenStore {
    val token: Flow<String?>

    fun currentToken(): String?

    fun setToken(token: String?)

    fun subscribedTopics(): Set<String>

    fun subscribe(topic: String)

    fun unsubscribe(topic: String)
}

/** Process-lifetime in-memory token store (default / test double). */
class InMemoryPushTokenStore : PushTokenStore {
    private val tokenState = MutableStateFlow<String?>(null)
    private val topics = mutableSetOf<String>()

    override val token: Flow<String?> = tokenState.asStateFlow()

    override fun currentToken(): String? = tokenState.value

    override fun setToken(token: String?) {
        tokenState.value = token
    }

    override fun subscribedTopics(): Set<String> = topics.toSet()

    override fun subscribe(topic: String) {
        topics.add(topic)
    }

    override fun unsubscribe(topic: String) {
        topics.remove(topic)
    }
}

/**
 * Offline [PushMessaging] backed by a [PushTokenStore] — the commonMain default (and the noGms/
 * iOS-without-key fallback) for apps with no push backend of their own to register against.
 */
class LocalPushMessaging(
    private val store: PushTokenStore = InMemoryPushTokenStore(),
) : PushMessaging {
    override suspend fun currentToken(): String? = store.currentToken()

    override val onTokenRefresh: Flow<String> = store.token.filterNotNull()

    override suspend fun subscribeTopic(topic: String) = store.subscribe(topic)

    override suspend fun unsubscribeTopic(topic: String) = store.unsubscribe(topic)
}
