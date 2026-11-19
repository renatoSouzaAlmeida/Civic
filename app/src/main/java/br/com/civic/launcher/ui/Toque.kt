package br.com.civic.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.platform.LocalContext
import br.com.civic.launcher.Configuracoes
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Toque com resposta visual (encolhe um pouco) e vibração leve. Barato para a GPU. */
@Composable
fun Modifier.toqueSuave(aoTocar: () -> Unit): Modifier {
    val fonte = remember { MutableInteractionSource() }
    val pressionado by fonte.collectIsPressedAsState()
    val leve = Configuracoes.obter(LocalContext.current).modoLeve
    val escala by animateFloatAsState(if (pressionado && !leve) 0.96f else 1f, tween(if (leve) 0 else 90), label = "toque")
    val vibrar = LocalHapticFeedback.current
    return this
        .graphicsLayer { scaleX = escala; scaleY = escala }
        .clickable(interactionSource = fonte, indication = null) {
            if (!leve) vibrar.performHapticFeedback(HapticFeedbackType.LongPress)
            aoTocar()
        }
}

/** Pisca-pisca de opacidade. No modo leve não anima: fica parado em [de] (sem custo de GPU). */
@Composable
fun pulsar(de: Float, ate: Float, ms: Int): State<Float> {
    if (Configuracoes.obter(LocalContext.current).modoLeve) return remember { mutableStateOf(de) }
    return rememberInfiniteTransition(label = "pulso").animateFloat(de, ate, infiniteRepeatable(tween(ms), RepeatMode.Reverse), label = "pulso")
}
