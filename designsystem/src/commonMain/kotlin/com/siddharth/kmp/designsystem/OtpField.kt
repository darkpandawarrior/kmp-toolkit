package com.siddharth.kmp.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How a single OTP cell is drawn. */
enum class OtpCellShape {
    /** Full outline around the digit. */
    Box,

    /** Underline only. */
    Line,

    /** Circular outline — reads well on a watch. */
    Circle,
}

/** Resolved visual state of one cell, in precedence order. */
enum class OtpCellState { Error, Active, Filled, Empty }

/**
 * Which cell the caret is on, and how each cell should be painted.
 *
 * Pure so it is testable without a composition. Note the [fieldFocused] gate: the source idiom this
 * came from computed `isFocused = value.length == index` unconditionally, so a cell was painted with
 * the focus ring even when the field held no focus at all — the form looked active while the keyboard
 * was closed. A cell is only active when the field itself is focused.
 */
fun otpCellState(
    index: Int,
    value: String,
    fieldFocused: Boolean,
    isError: Boolean,
): OtpCellState = when {
    isError -> OtpCellState.Error
    fieldFocused && index == value.length -> OtpCellState.Active
    index < value.length -> OtpCellState.Filled
    else -> OtpCellState.Empty
}

/**
 * Digits only, capped at [length]. Applied to every edit, so pasting "OTP: 123 456" from a
 * notification yields "123456" rather than being rejected — the single most common way a user
 * actually enters a code.
 */
fun sanitizeOtp(input: String, length: Int): String =
    input.filter { it.isDigit() }.take(length)

@Immutable
data class OtpFieldStyle(
    val cellSize: Dp,
    val spacing: Dp,
    val textStyle: TextStyle,
    val shape: Shape,
    val borderWidth: Dp,
    val cellShape: OtpCellShape,
    val activeColor: Color,
    val filledColor: Color,
    val emptyColor: Color,
    val errorColor: Color,
)

object OtpFieldDefaults {
    /**
     * Every colour comes from `MaterialTheme.colorScheme` — this module ships no palette, so the
     * consumer's theme decides what "error" and "active" look like. The source idiom hardcoded
     * `Color.Blue` / `Color.Black` / `Color.Gray` / `Color.Red`, which is invisible in dark mode.
     *
     * Cell size follows the surface: a wrist cannot fit six 48dp boxes, and a 48dp box is
     * unreadable from a sofa.
     */
    @Composable
    fun style(
        cellShape: OtpCellShape = OtpCellShape.Box,
        cellSize: Dp = when (LocalFormFactor.current) {
            FormFactor.Watch -> 28.dp
            FormFactor.Tv -> 64.dp
            FormFactor.Handheld, FormFactor.Desktop -> 48.dp
        },
    ): OtpFieldStyle {
        val tokens = LocalAdaptiveTokens.current
        return OtpFieldStyle(
            cellSize = cellSize,
            spacing = tokens.itemSpacing,
            textStyle = LocalTextStyle.current.copy(
                fontSize = tokens.title,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            ),
            shape = DesignTokens.Shape.badge,
            borderWidth = 2.dp,
            cellShape = cellShape,
            activeColor = MaterialTheme.colorScheme.primary,
            filledColor = MaterialTheme.colorScheme.onSurface,
            emptyColor = MaterialTheme.colorScheme.outlineVariant,
            errorColor = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * A one-time-code field: [length] cells backed by a single real text field.
 *
 * Built as a `BasicTextField` with a `decorationBox` that paints the cells, rather than the usual
 * trick of an invisible `alpha(0f)` field stacked behind a clickable Row. That matters for more than
 * tidiness — with a real field, focus, the caret, IME actions, select-all, paste and the platform's
 * own SMS-code suggestion all work by default, where the stacked-overlay version has to re-implement
 * each one by hand and typically ships without them.
 *
 * [onComplete] fires when the code first reaches [length]; re-editing the last digit to the same
 * value will not re-fire it, so it is safe to hang a network call off.
 *
 * ```
 * var code by remember { mutableStateOf("") }
 * OtpField(code, { code = it }, onComplete = { viewModel.verify(it) }, isError = state.rejected)
 * ```
 *
 * ponytail: no `enabled = false` styling beyond what the text field already does, and no built-in
 * resend timer — a timer is screen state, not field state, and belongs in the caller's ViewModel.
 */
@Composable
fun OtpField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    enabled: Boolean = true,
    isError: Boolean = false,
    onComplete: (String) -> Unit = {},
    style: OtpFieldStyle = OtpFieldDefaults.style(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    BasicTextField(
        value = value,
        onValueChange = { raw ->
            val clean = sanitizeOtp(raw, length)
            if (clean == value) return@BasicTextField
            onValueChange(clean)
            if (clean.length == length) onComplete(clean)
        },
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        // The real text and caret are hidden; the decoration box below is the entire visual. Keeping
        // them transparent rather than absent preserves the field's own accessibility semantics.
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = {
            Row(
                // The cells are decoration. Without this a screen reader reads six loose digits
                // instead of the field's own "one-time code, 123456" announcement.
                modifier = Modifier.clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(style.spacing),
            ) {
                repeat(length) { index ->
                    OtpCell(
                        char = value.getOrNull(index),
                        state = otpCellState(index, value, focused && enabled, isError),
                        style = style,
                    )
                }
            }
        },
    )
}

@Composable
private fun OtpCell(char: Char?, state: OtpCellState, style: OtpFieldStyle) {
    val color = when (state) {
        OtpCellState.Error -> style.errorColor
        OtpCellState.Active -> style.activeColor
        OtpCellState.Filled -> style.filledColor
        OtpCellState.Empty -> style.emptyColor
    }
    val outline = when (style.cellShape) {
        OtpCellShape.Line -> Modifier.drawBehind {
            val stroke = style.borderWidth.toPx()
            val y = size.height - stroke / 2
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
        }
        OtpCellShape.Circle -> Modifier.border(style.borderWidth, color, CircleShape)
        OtpCellShape.Box -> Modifier.border(style.borderWidth, color, style.shape)
    }
    Box(
        modifier = Modifier.size(style.cellSize).then(outline),
        contentAlignment = Alignment.Center,
    ) {
        if (char != null) Text(char.toString(), style = style.textStyle)
    }
}
