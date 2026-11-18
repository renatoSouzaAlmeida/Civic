package br.com.civic.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.Configuracoes
import br.com.civic.launcher.abrirNavegacaoComPainel
import br.com.civic.launcher.navegarFavorito
import br.com.civic.launcher.obd.ObdMonitor

/**
 * Cartão do Waze. O Waze não pode ser embutido por outro app, então o mapa daqui é só ilustração:
 * tocar abre o Waze com o painel de direção por cima.
 */
@Composable
fun CartaoWaze(sensores: Sensores, obd: ObdMonitor, modifier: Modifier) {
    val ctx = LocalContext.current
    val config = remember { Configuracoes.obter(ctx) }
    val vel = (obd.dados.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f).toInt()
    BoxWithConstraints(
        modifier.toqueSuave { ctx.abrirNavegacaoComPainel() }.clip(FormaCartao).border(1.dp, Cores.Borda, FormaCartao)
    ) {
        MapaDecorativo(Modifier.fillMaxSize())
        val compacto = maxHeight < 300.dp
        Column(
            Modifier.fillMaxSize().padding(if (compacto) 14.dp else 22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(if (compacto) 44.dp else 56.dp).clip(RoundedCornerShape(16.dp)).background(Cores.Waze),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Navigation, null, tint = Color.White, modifier = Modifier.size(if (compacto) 26.dp else 32.dp)) }
                    Column {
                        Text(config.mapa.rotulo, color = Cores.Texto, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("Navegação", color = Cores.TextoSuave, fontSize = 14.sp)
                    }
                }
                PilulaFavoritos { destino -> ctx.navegarFavorito(destino) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(Cores.CartaoSolido).border(1.dp, Cores.ChipBorda, RoundedCornerShape(24.dp))
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("$vel", color = Cores.Texto, fontSize = if (compacto) 38.sp else 54.sp, fontWeight = FontWeight.ExtraBold)
                    Text("km/h", color = Cores.TextoSuave, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Cores.Destaque).padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Navigation, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text("Iniciar direção", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PilulaFavoritos(aoEscolher: (String) -> Unit) {
    val forma = RoundedCornerShape(50)
    Row(
        Modifier.clip(forma).background(Cores.CartaoSolido).border(1.dp, Cores.ChipBorda, forma).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Favorito("Casa", Icons.Filled.Home) { aoEscolher("Home") }
        Box(Modifier.width(1.dp).height(24.dp).background(Cores.ChipBorda))
        Favorito("Trabalho", Icons.Filled.Work) { aoEscolher("Work") }
    }
}

@Composable
private fun Favorito(texto: String, icone: androidx.compose.ui.graphics.vector.ImageVector, aoTocar: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = aoTocar).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icone, null, tint = Cores.Destaque, modifier = Modifier.size(22.dp))
        Text(texto, color = Cores.Texto, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Mapa ilustrativo desenhado em Canvas (ruas, parques e uma rota). Estático e barato para a GPU. */
@Composable
private fun MapaDecorativo(modifier: Modifier) {
    val fundo = Cores.MapaFundo
    val via = Cores.MapaVia
    val parque = Cores.MapaParque
    val contorno = if (LocalPaleta.current.escuro) Color(0xFF0A3A55) else Color(0xFF0A6A8F)
    val rota = Color(0xFF33CCFF)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(fundo)
        drawRoundRect(parque, Offset(w * 0.06f, h * 0.30f), Size(w * 0.22f, h * 0.16f), CornerRadius(24f))
        drawRoundRect(parque, Offset(w * 0.72f, h * 0.50f), Size(w * 0.2f, h * 0.18f), CornerRadius(24f))
        drawRoundRect(parque, Offset(w * 0.30f, h * 0.62f), Size(w * 0.14f, h * 0.14f), CornerRadius(24f))
        val estrada = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round)
        drawPath(
            Path().apply {
                moveTo(-20f, h * 0.78f)
                cubicTo(w * 0.3f, h * 0.70f, w * 0.45f, h * 0.50f, w * 0.62f, h * 0.46f)
                cubicTo(w * 0.75f, h * 0.43f, w * 0.90f, h * 0.20f, w + 20f, h * 0.10f)
            }, via, style = estrada,
        )
        drawPath(
            Path().apply {
                moveTo(w * 0.15f, h + 20f)
                cubicTo(w * 0.30f, h * 0.72f, w * 0.50f, h * 0.62f, w * 0.78f, h * 0.55f)
                cubicTo(w * 0.90f, h * 0.52f, w + 20f, h * 0.50f, w + 20f, h * 0.50f)
            }, via, style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round),
        )
        val rt = Path().apply {
            moveTo(w * 0.5f, h + 10f)
            cubicTo(w * 0.5f, h * 0.82f, w * 0.46f, h * 0.66f, w * 0.60f, h * 0.52f)
            cubicTo(w * 0.70f, h * 0.40f, w * 0.78f, h * 0.30f, w * 0.72f, h * 0.06f)
        }
        drawPath(rt, contorno, style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round))
        drawPath(rt, rota, style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))
        val seta = Path().apply {
            val cx = w * 0.5f
            val cy = h * 0.86f
            moveTo(cx, cy - 22.dp.toPx())
            lineTo(cx + 15.dp.toPx(), cy + 16.dp.toPx())
            lineTo(cx, cy + 8.dp.toPx())
            lineTo(cx - 15.dp.toPx(), cy + 16.dp.toPx())
            close()
        }
        drawPath(seta, Color.White)
        drawPath(seta, rota, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
    }
}
