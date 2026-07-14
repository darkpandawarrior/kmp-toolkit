package com.siddharth.kmp.designsystem

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistence seam for the theme choice. The default is in-memory; `core:data` binds a
 * DataStore-backed implementation in Koin (same interface → no shell/settings changes), which is
 * how the dark-mode choice survives process death.
 *
 * ponytail: synchronous read/write. The stored value is a single boolean read once at construction
 * and written on toggle — a DataStore actual can `runBlocking` a first-value read at startup without
 * a perceptible cost. Move to a suspend/Flow seam only if theme grows into a multi-field profile.
 */
interface ThemeStore {
    /** The persisted dark-mode choice, or null if the user hasn't chosen yet (→ use the default). */
    fun darkOverride(): Boolean?

    /** Persist the user's dark-mode choice. */
    fun setDark(dark: Boolean)
}

/** Default seam: holds the choice for the process lifetime only. */
object InMemoryThemeStore : ThemeStore {
    private var value: Boolean? = null

    override fun darkOverride(): Boolean? = value

    override fun setDark(dark: Boolean) {
        value = dark
    }
}

/**
 * App-wide theme state holder (Mileway ThemeController idiom). Dark-first: defaults to true when the
 * user hasn't chosen. Reads the persisted choice from [store] at construction and writes every change
 * back, so the Settings toggle survives process death once a persistent [ThemeStore] is bound.
 * Wasm-safe. Bound as a Koin singleton so the shell and the Settings screen share one instance.
 */
class ThemeController(
    private val store: ThemeStore = InMemoryThemeStore,
    defaultDark: Boolean = true,
) {
    private val _isDark = MutableStateFlow(store.darkOverride() ?: defaultDark)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    fun setDark(dark: Boolean) {
        _isDark.value = dark
        store.setDark(dark)
    }

    fun toggle() = setDark(!_isDark.value)
}
