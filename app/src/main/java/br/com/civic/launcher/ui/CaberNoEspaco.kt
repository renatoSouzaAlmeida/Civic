package br.com.civic.launcher.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/**
 * Conteúdo de cartão que nunca é cortado: o conteúdo é montado numa altura de projeto ([alturaProjeto]) e, se o espaço real for menor
 * (tela dividida, meia tela), é encolhido por igual para caber inteiro, em vez de ser cortado embaixo ou na lateral. Com espaço
 * suficiente não muda nada. Use só em cartões cuja altura vem do pai (peso ou tamanho fixo), nunca em itens de uma coluna que cresce.
 */
fun Modifier.escalarSeMenorQue(alturaProjeto: Dp): Modifier = layout { medivel, c ->
    val altura = alturaProjeto.roundToPx()
    if (!c.hasBoundedHeight || c.maxHeight >= altura) {
        val p = medivel.measure(c)
        layout(p.width, p.height) { p.place(0, 0) }
    } else {
        val e = (c.maxHeight.toFloat() / altura).coerceAtLeast(0.2f)
        val largura = if (c.hasBoundedWidth) (c.maxWidth / e).roundToInt() else c.minWidth
        val p = medivel.measure(Constraints(minWidth = largura, maxWidth = largura, minHeight = altura, maxHeight = altura))
        val w = if (c.hasBoundedWidth) c.maxWidth else (p.width * e).roundToInt()
        layout(w, c.maxHeight) { p.placeWithLayer(0, 0) { scaleX = e; scaleY = e; transformOrigin = TransformOrigin(0f, 0f) } }
    }
}
