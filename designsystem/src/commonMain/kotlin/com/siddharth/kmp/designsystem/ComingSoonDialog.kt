package com.siddharth.kmp.designsystem

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * The single deferred-feature affordance. Every web feature not in v1 opens this instead of a real
 * screen (see the "95% later" policy). One component so the message/styling stays consistent.
 */
@Composable
fun ComingSoonDialog(
    onDismiss: () -> Unit,
    title: String = "Coming soon",
    message: String = "This is coming in a future release.",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}
