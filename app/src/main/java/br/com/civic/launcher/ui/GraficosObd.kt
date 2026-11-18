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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Medidor em arco segmentado (270°), estilo painel esportivo, com o valor grande no centro.
 * Desenho simples em Canvas, sem blur, para poupar a GPU.
 */
@Composable
fun GaugeArco(fracao: Float, valor: String, unidade: String, modifier: Modifier = Modifier, cor: Color = Cores.Destaque) {
    val trilho = Cores.SegVazio
    val f = fracao.coerceIn(0f, 1f)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val lado = minOf(maxWidth, maxHeight)
        Box(Modifier.size(lado), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                val raio = size.minDimension / 2
                val esp = raio * 0.13f
                val rArco = raio - esp / 2 - raio * 0.02f
                val canto = Offset(c.x - rArco, c.y - rArco)
                val tam = Size(rArco * 2, rArco * 2)
                val n = 30
                val passo = 270f / n
                for (i in 0 until n) {
                    val ligado = (i + 1) / n.toFloat() <= f + 0.001f
                    val brilho = 0.45f + 0.55f * (i / n.toFloat())
                    drawArc(
                        if (ligado) cor.copy(alpha = brilho) else trilho,
                        135f + i * passo + 1.2f, passo - 2.4f, false, canto, tam, style = Stroke(esp, cap = StrokeCap.Butt),
                    )
                }
                drawCircle(cor.copy(alpha = 0.10f), rArco - esp, c)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(valor, color = Cores.Texto, fontSize = (lado.value * 0.26f).sp, lineHeight = (lado.value * 0.28f).sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(unidade, color = Cores.TextoSuave, fontSize = (lado.value * 0.075f).sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Carro visto de trás "avançando": a estrada em perspectiva corre em direção ao carro
 * numa velocidade proporcional à velocidade real. Parado, a estrada para (sem movimento falso).
 */
@Composable
fun CarroAvancando(velKmh: Float, modifier: Modifier = Modifier) {
    val vel by rememberUpdatedState(velKmh)
    var fase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var ultimo = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                if (ultimo != 0L) fase = (fase + (t - ultimo) / 1e9f * (vel / 100f) * 4f) % 6f
                ultimo = t
            }
        }
    }
    val azul = Cores.Destaque
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val fuga = Offset(w / 2, h * 0.22f)
            // brilho do horizonte
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, azul.copy(alpha = 0.22f), Color.Transparent), fuga.y - h * 0.12f, fuga.y + h * 0.12f))
            // bordas da pista (com um traço mais largo e fraco para dar o brilho)
            for ((largura, alfa) in listOf(10f to 0.14f, 3f to 0.85f)) {
                drawLine(azul.copy(alpha = alfa), fuga, Offset(w * 0.04f, h), largura, StrokeCap.Round)
                drawLine(azul.copy(alpha = alfa), fuga, Offset(w * 0.96f, h), largura, StrokeCap.Round)
            }
            // faixas tracejadas que vêm em direção ao carro
            for (k in 0 until 6) {
                val t0 = ((k + fase) % 6f) / 6f
                val t1 = (t0 + 0.07f).coerceAtMost(1f)
                for (lado in listOf(-0.30f, 0f, 0.30f)) {
                    val x0 = w / 2 + lado * w * t0
                    val x1 = w / 2 + lado * w * t1
                    val y0 = fuga.y + (h - fuga.y) * t0 * t0
                    val y1 = fuga.y + (h - fuga.y) * t1 * t1
                    drawLine(Color.White.copy(alpha = 0.15f + 0.55f * t0), Offset(x0, y0), Offset(x1, y1), 1.5f + 7f * t0, StrokeCap.Round)
                }
            }
        }
        Image(
            painterResource(R.drawable.carro_traseira), null,
            Modifier.align(Alignment.BottomCenter).fillMaxHeight(0.62f).padding(bottom = 4.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
