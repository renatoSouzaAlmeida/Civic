package br.com.civic.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mostrador lateral (velocidade / carga): face escura redonda, arco segmentado azul→ciano
 * e o valor grande em itálico no centro, como num painel esportivo.
 */
@Composable
fun GaugeLateral(fracao: Float, valor: String, unidade: String, icone: ImageVector?, modifier: Modifier = Modifier) {
    val Painel = coresPainel()
    val f = fracao.coerceIn(0f, 1f)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val lado = minOf(maxWidth, maxHeight)
        Box(Modifier.size(lado), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                val raio = size.minDimension / 2
                drawCircle(Painel.face, raio * 0.98f, c)
                drawCircle(Painel.borda, raio * 0.98f, c, style = Stroke(raio * 0.02f))
                val esp = raio * 0.14f
                val rArco = raio * 0.80f
                val canto = Offset(c.x - rArco, c.y - rArco)
                val tam = Size(rArco * 2, rArco * 2)
                val n = 32
                val passo = 270f / n
                for (i in 0 until n) {
                    val ligado = (i + 1) / n.toFloat() <= f + 0.001f
                    val t = i / (n - 1f)
                    val cor = if (ligado) lerp(Painel.Azul, Painel.Ciano, t) else Painel.trilho
                    drawArc(cor, 135f + i * passo + 1.3f, passo - 2.6f, false, canto, tam, style = Stroke(esp, cap = StrokeCap.Butt))
                }
                drawCircle(Painel.borda.copy(alpha = 0.6f), rArco - esp, c, style = Stroke(raio * 0.012f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (icone != null) Icon(icone, null, tint = Painel.texto, modifier = Modifier.size(lado * 0.13f))
                val escala = minOf(1f, 3f / valor.length.coerceAtLeast(1))
                Text(valor, color = Painel.texto, fontSize = (lado.value * 0.26f * escala).sp, lineHeight = (lado.value * 0.28f * escala).sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, maxLines = 1)
                Text(unidade, color = Painel.texto, fontSize = (lado.value * 0.075f).sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Mostrador central grande: face, marcas (as maiores com número), faixa vermelha no fim da escala,
 * arco de progresso e o valor grande em itálico no centro. Usado como velocímetro (0–200 km/h).
 */
@Composable
fun MostradorCentral(fracao: Float, valor: String, rotulo: String, marcas: List<String>, inicioVermelho: Float, modifier: Modifier = Modifier) {
    val Painel = coresPainel()
    val f = fracao.coerceIn(0f, 1f)
    val divisoes = (marcas.size - 1) * 2
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val lado = minOf(maxWidth, maxHeight)
        Box(Modifier.size(lado)) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                val raio = size.minDimension / 2
                drawCircle(Painel.face, raio * 0.99f, c)
                drawCircle(Brush.sweepGradient(listOf(Painel.borda, Color(0xFF6B7A90), Painel.borda), c), raio * 0.99f, c, style = Stroke(raio * 0.025f))
                // Faixa vermelha externa no fim da escala.
                val rFaixa = raio * 0.90f
                drawArc(
                    Painel.Vermelho, 135f + 270f * inicioVermelho, 270f * (1f - inicioVermelho), false,
                    Offset(c.x - rFaixa, c.y - rFaixa), Size(rFaixa * 2, rFaixa * 2), style = Stroke(raio * 0.05f),
                )
                // Progresso (ciano, vermelho na faixa final).
                val rProg = raio * 0.84f
                val canto = Offset(c.x - rProg, c.y - rProg)
                val tam = Size(rProg * 2, rProg * 2)
                drawArc(Painel.trilho, 135f, 270f, false, canto, tam, style = Stroke(raio * 0.035f))
                val azulFim = minOf(f, inicioVermelho) * 270f
                if (azulFim > 0f) drawArc(Painel.Ciano, 135f, azulFim, false, canto, tam, style = Stroke(raio * 0.035f, cap = StrokeCap.Round))
                if (f > inicioVermelho) drawArc(Painel.Vermelho, 135f + 270f * inicioVermelho, (f - inicioVermelho) * 270f, false, canto, tam, style = Stroke(raio * 0.035f, cap = StrokeCap.Round))
                // Marcas.
                val rExt = raio * 0.78f
                for (i in 0..divisoes) {
                    val t = i / divisoes.toFloat()
                    val a = Math.toRadians(135.0 + 270.0 * t)
                    val grande = i % 2 == 0
                    val rInt = rExt - raio * (if (grande) 0.11f else 0.06f)
                    drawLine(
                        if (t >= inicioVermelho) Painel.Vermelho else Painel.texto,
                        Offset(c.x + rInt * cos(a).toFloat(), c.y + rInt * sin(a).toFloat()),
                        Offset(c.x + rExt * cos(a).toFloat(), c.y + rExt * sin(a).toFloat()),
                        strokeWidth = raio * (if (grande) 0.022f else 0.012f), cap = StrokeCap.Round,
                    )
                }
            }
            // Números da escala.
            val rRot = lado / 2 * 0.55f
            val tamRotulo = (lado.value * (if (marcas.maxOf { it.length } >= 3) 0.052f else 0.065f)).sp
            marcas.forEachIndexed { k, texto ->
                val t = k / (marcas.size - 1f)
                val a = Math.toRadians(135.0 + 270.0 * t)
                Box(
                    Modifier.offset(x = lado / 2 + rRot * cos(a).toFloat() - 20.dp, y = lado / 2 + rRot * sin(a).toFloat() - 14.dp).size(40.dp, 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(texto, color = if (t >= inicioVermelho) Painel.Vermelho else Painel.texto, fontSize = tamRotulo, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, maxLines = 1)
                }
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(valor, color = Painel.texto, fontSize = (lado.value * 0.22f).sp, lineHeight = (lado.value * 0.24f).sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, maxLines = 1)
                Text(rotulo, color = Painel.suave, fontSize = (lado.value * 0.06f).sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
