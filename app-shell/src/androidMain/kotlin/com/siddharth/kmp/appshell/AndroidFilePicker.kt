package com.siddharth.kmp.appshell

import android.content.Context
import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.io.readByteArray

/**
 * Android [FilePicker]. Calf's `rememberFilePickerLauncher`/`rememberFileSaverLauncher` are
 * `@Composable` (they wrap `rememberLauncherForActivityResult`), so — mirroring
 * [AndroidPermissionsProvider.requestBridge] — the host activity's Compose layer registers
 * [pickBridge]/[saveBridge] to the real launchers; with no bridge registered this degrades to
 * reporting no file rather than crashing. File bytes are read via Calf's `KmpFile` I/O, which is
 * plain suspend code, not Compose-bound.
 */
class AndroidFilePicker(private val context: Context) : FilePicker {
    /** Wired/cleared by the host activity: launches the Calf file picker, resumes with the picked file. */
    @Volatile
    var pickBridge: (suspend () -> KmpFile?)? = null

    /** Wired/cleared by the host activity: launches the Calf file saver, resumes with the saved file. */
    @Volatile
    var saveBridge: (suspend (fileName: String, bytes: ByteArray) -> KmpFile?)? = null

    override suspend fun pickFile(): ByteArray? {
        val file = pickBridge?.invoke() ?: return null
        return file.readByteArray(context)
    }

    override suspend fun saveFile(
        fileName: String,
        bytes: ByteArray,
    ): Boolean = saveBridge?.invoke(fileName, bytes) != null
}
