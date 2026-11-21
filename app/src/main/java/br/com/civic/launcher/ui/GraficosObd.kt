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
fun MostradorCentral(
    fracao: Float, valor: String, rotulo: String, marcas: List<String>, inicioVermelho: Float, modifier: Modifier = Modifier,
    /** Cor do anel ECO (verde, azul claro, azul escuro), como o painel do Civic. Null = sem anel. */
    corAmbiente: Color? = null,
) {
    val Painel = coresPainel()
    val anel by androidx.compose.animation.animateColorAsState(corAmbiente ?: Color.Transparent, androidx.compose.animation.core.tween(if (br.com.civic.launcher.Configuracoes.obter(androidx.compose.ui.platform.LocalContext.current).modoLeve) 0 else 600), label = "anelEco")
    val f = fracao.coerceIn(0f, 1f)
    val divisoes = (marcas.size - 1) * 2
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val lado = minOf(maxWidth, maxHeight)
        Box(Modifier.size(lado)) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                val raio = size.minDimension / 2
                drawCircle(Painel.face, raio * 0.93f, c)
                drawCircle(Brush.sweepGradient(listOf(Painel.borda, Color(0xFF6B7A90), Painel.borda), c), raio * 0.94f, c, style = Stroke(raio * 0.025f))
                // Arco ECO: só a metade de cima (do meio para cima), num degradê suave que some nas duas pontas.
                // Verde = econômico, azul claro = normal, laranja = alto consumo (modo de condução, como o ECO Assist do Civic).
                if (anel.alpha > 0f) {
                    val rAro = raio * 0.915f
                    val transparente = anel.copy(alpha = 0f)
                    val suave = Brush.sweepGradient(
                        0.00f to transparente, 0.50f to transparente, // 180° (meio, à esquerda)
                        0.60f to anel.copy(alpha = 0.40f * anel.alpha),
                        0.75f to anel.copy(alpha = 0.90f * anel.alpha), // 270° (topo)
                        0.90f to anel.copy(alpha = 0.40f * anel.alpha),
                        1.00f to transparente, // 360° (meio, à direita)
                        center = c,
                    )
                    val canto = Offset(c.x - rAro, c.y - rAro); val tam = Size(rAro * 2, rAro * 2)
                    drawArc(suave, 180f, 180f, false, canto, tam, alpha = 0.30f, style = Stroke(raio * 0.13f)) // brilho largo por baixo
                    drawArc(suave, 180f, 180f, false, canto, tam, style = Stroke(raio * 0.045f, cap = StrokeCap.Round))
                }
                // Faixa vermelha externa no fim da escala.
                val rFaixa = raio * 0.90f
                drawArc(
                    Painel.Vermelho, 135f + 270f * inicioVermelho, 270f * (1f - inicioVermelho), false,
                    Offset(c.x - rFaixa, c.y - rFaixa), Size(rFaixa * 2, rFaixa * 2), style = Stroke(raio * 0.05f),
                )
                // Progresso em degradê: azul → ciano → âmbar → vermelho ao longo da escala.
                // O sweepGradient começa em 0° (3 h); as paradas convertem a fração da escala (arco de 135° a 405°)
                // para a posição angular: pos = ((135 + 270·f) mod 360) / 360.
                val rProg = raio * 0.84f
                val canto = Offset(c.x - rProg, c.y - rProg)
                val tam = Size(rProg * 2, rProg * 2)
                val esp = raio * 0.05f
                drawArc(Painel.trilho, 135f, 270f, false, canto, tam, style = Stroke(esp))
                if (f > 0f) {
                    val azulEscuro = Color(0xFF1E5BFF)
                    val ambar = Color(0xFFFFB020)
                    val laranja = Color(0xFFFF6A2B)
                    val degrade = Brush.sweepGradient(
                        0.000f to laranja,
                        0.125f to Painel.Vermelho,
                        0.200f to Painel.Vermelho,
                        0.374f to azulEscuro,
                        0.375f to azulEscuro,
                        0.750f to Painel.Ciano,
                        0.938f to ambar,
                        1.000f to laranja,
                        center = c,
                    )
                    // Brilho largo e suave por baixo + faixa principal.
                    drawArc(degrade, 135f, 270f * f, false, canto, tam, alpha = 0.25f, style = Stroke(esp * 2.6f, cap = StrokeCap.Round))
                    drawArc(degrade, 135f, 270f * f, false, canto, tam, style = Stroke(esp, cap = StrokeCap.Round))
                    // Ponto luminoso na ponta.
                    val aP = Math.toRadians(135.0 + 270.0 * f)
                    val ponta = Offset(c.x + rProg * cos(aP).toFloat(), c.y + rProg * sin(aP).toFloat())
                    drawCircle(Color.White.copy(alpha = 0.35f), esp * 1.4f, ponta)
                    drawCircle(Color.White, esp * 0.55f, ponta)
                }
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
