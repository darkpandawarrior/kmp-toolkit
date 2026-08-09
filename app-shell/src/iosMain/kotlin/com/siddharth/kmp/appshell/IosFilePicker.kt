package com.siddharth.kmp.appshell

import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.io.readByteArray

/**
 * iOS [FilePicker]. Calf's file-picker/file-saver launchers are `@Composable` — like
 * [IosDocumentScanner]'s VisionKit flow, they must be presented from live Compose UI, which this
 * headless module has no hook into. Mirroring [AndroidFilePicker], the host's Compose layer wires
 * [pickBridge]/[saveBridge] to the real launchers; with no bridge registered this degrades to
 * reporting no file, the same truthful-no-op fallback [IosDocumentScanner] uses.
 */
class IosFilePicker : FilePicker {
    // ponytail: no @Volatile — that's a JVM-only annotation (kotlin.jvm.Volatile), unavailable on
    // Kotlin/Native; iOS is single-threaded here (main-actor Compose UI wiring the bridge), so a
    // plain var is enough.
    var pickBridge: (suspend () -> KmpFile?)? = null

    var saveBridge: (suspend (fileName: String, bytes: ByteArray) -> KmpFile?)? = null

    override suspend fun pickFile(): ByteArray? {
        val file = pickBridge?.invoke() ?: return null
        return file.readByteArray()
    }

    override suspend fun saveFile(
        fileName: String,
        bytes: ByteArray,
    ): Boolean = saveBridge?.invoke(fileName, bytes) != null
}
