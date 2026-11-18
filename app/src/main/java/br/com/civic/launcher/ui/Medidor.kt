package br.com.civic.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Medidor em arco de 270°. Desenho simples (sem blur/sombra) para poupar a GPU da multimídia. */
@Composable
fun Medidor(
    fracao: Float,
    cor: Color,
    modifier: Modifier = Modifier,
    espessura: Dp = 12.dp,
    conteudo: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val esp = espessura.toPx()
            val tamanho = Size(size.width - esp, size.height - esp)
            val origem = Offset(esp / 2, esp / 2)
            val traco = Stroke(esp, cap = StrokeCap.Round)
            drawArc(Cores.CartaoAlto, 135f, 270f, false, origem, tamanho, style = traco)
            val f = fracao.coerceIn(0f, 1f)
            if (f > 0f) drawArc(cor, 135f, 270f * f, false, origem, tamanho, style = traco)
        }
        conteudo()
    }
}
