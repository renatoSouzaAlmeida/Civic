package br.com.civic.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tacômetro estilo carro esportivo: arco de 270° com zonas (azul, âmbar e vermelha a partir de 6.400 rpm),
 * marcas, números de 0 a 8 (x1000), ponteiro e a velocidade digital no centro.
 * Desenho simples em Canvas, sem blur, para poupar a GPU da multimídia.
 */
@Composable
fun TacometroEsportivo(rpm: Int?, vel: Float, modifier: Modifier = Modifier) {
    val trilho = Cores.SegVazio
    val azul = Cores.Destaque
    val ambar = Cores.Amarelo
    val vermelho = Cores.Vermelho
    val suave = Cores.TextoSuave
    val centroCor = Cores.CartaoSolido
    val fracao = ((rpm ?: 0) / 8000f).coerceIn(0f, 1f)

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val lado = minOf(maxWidth, maxHeight)
        Box(Modifier.size(lado)) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                val raio = size.minDimension / 2
                val esp = raio * 0.10f
                val rArco = raio - esp / 2 - raio * 0.02f
                val canto = Offset(c.x - rArco, c.y - rArco)
                val tamanho = Size(rArco * 2, rArco * 2)
                val faixa = Stroke(esp, cap = StrokeCap.Butt)

                // Trilho e zona vermelha (a partir de 80% = 6.400 rpm).
                drawArc(trilho, 135f, 270f, false, canto, tamanho, style = faixa)
                drawArc(vermelho.copy(alpha = 0.28f), 135f + 270f * 0.8f, 270f * 0.2f, false, canto, tamanho, style = faixa)

                // Preenchimento por zonas.
                val total = 270f * fracao
                val azulFim = minOf(total, 270f * 0.6f)
                if (azulFim > 0f) drawArc(azul, 135f, azulFim, false, canto, tamanho, style = faixa)
                val ambarFim = minOf(total, 270f * 0.8f) - 270f * 0.6f
                if (ambarFim > 0f) drawArc(ambar, 135f + 270f * 0.6f, ambarFim, false, canto, tamanho, style = faixa)
                val vermelhoFim = total - 270f * 0.8f
                if (vermelhoFim > 0f) drawArc(vermelho, 135f + 270f * 0.8f, vermelhoFim, false, canto, tamanho, style = faixa)

                // Marcas a cada 500 rpm (as de milhar são maiores).
                val rExterno = rArco - esp / 2 - raio * 0.03f
                for (i in 0..16) {
                    val f = i / 16f
                    val a = Math.toRadians((135.0 + 270.0 * f))
                    val grande = i % 2 == 0
                    val rInterno = rExterno - raio * (if (grande) 0.09f else 0.05f)
                    drawLine(
                        if (f >= 0.8f) vermelho else suave,
                        Offset(c.x + rInterno * cos(a).toFloat(), c.y + rInterno * sin(a).toFloat()),
                        Offset(c.x + rExterno * cos(a).toFloat(), c.y + rExterno * sin(a).toFloat()),
                        strokeWidth = raio * (if (grande) 0.014f else 0.008f),
                        cap = StrokeCap.Round,
                    )
                }

                // Ponteiro: do miolo até perto das marcas, com cubo no centro.
                val aP = Math.toRadians(135.0 + 270.0 * fracao)
                val rPonta = rExterno - raio * 0.12f
                drawLine(
                    vermelho,
                    Offset(c.x + raio * 0.20f * cos(aP).toFloat(), c.y + raio * 0.20f * sin(aP).toFloat()),
                    Offset(c.x + rPonta * cos(aP).toFloat(), c.y + rPonta * sin(aP).toFloat()),
                    strokeWidth = raio * 0.028f, cap = StrokeCap.Round,
                )
                drawCircle(centroCor, raio * 0.07f, c)
                drawCircle(vermelho, raio * 0.07f, c, style = Stroke(raio * 0.02f))
            }

            // Números 0 a 8 (x1000 rpm), posicionados em círculo.
            val raioRotulo = lado / 2 * 0.64f
            for (k in 0..8) {
                val a = Math.toRadians(135.0 + 270.0 * (k / 8.0))
                val x = lado / 2 + raioRotulo * cos(a).toFloat() - 14.dp
                val y = lado / 2 + raioRotulo * sin(a).toFloat() - 14.dp
                Box(Modifier.offset(x = x, y = y).size(28.dp), contentAlignment = Alignment.Center) {
                    Text("$k", color = if (k >= 7) Cores.Vermelho else Cores.TextoSuave, fontSize = (lado.value * 0.05f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            // Velocidade digital no centro, levemente abaixo do cubo do ponteiro.
            Column(
                Modifier.align(Alignment.Center).offset(y = lado * 0.17f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${vel.toInt()}", color = Cores.Texto, fontSize = (lado.value * 0.20f).sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text("km/h", color = Cores.TextoSuave, fontSize = (lado.value * 0.05f).sp, fontWeight = FontWeight.SemiBold)
                Text(rpm?.let { "$it rpm" } ?: "— rpm", color = Cores.Destaque, fontSize = (lado.value * 0.045f).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
