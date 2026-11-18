package br.com.civic.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Toque com resposta visual (encolhe um pouco) e vibração leve. Barato para a GPU. */
@Composable
fun Modifier.toqueSuave(aoTocar: () -> Unit): Modifier {
    val fonte = remember { MutableInteractionSource() }
    val pressionado by fonte.collectIsPressedAsState()
    val escala by animateFloatAsState(if (pressionado) 0.96f else 1f, tween(90), label = "toque")
    val vibrar = LocalHapticFeedback.current
    return this
        .graphicsLayer { scaleX = escala; scaleY = escala }
        .clickable(interactionSource = fonte, indication = null) {
            vibrar.performHapticFeedback(HapticFeedbackType.LongPress)
            aoTocar()
        }
}
