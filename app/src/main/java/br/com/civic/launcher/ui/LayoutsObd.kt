package br.com.civic.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Configuracoes
import br.com.civic.launcher.LayoutObd
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.Veiculo
import br.com.civic.launcher.obd.DadosObd
import br.com.civic.launcher.obd.EstadoObd
import br.com.civic.launcher.obd.ObdMonitor
import br.com.civic.launcher.obd.SeriesObd
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val BR = Locale("pt", "BR")
private fun n1(v: Float?) = v?.let { "%.1f".format(BR, it) } ?: "—"
private fun n0(v: Int?) = v?.toString() ?: "—"

/**
 * Barra de ferramentas de teste dos layouts do OBD (canto superior esquerdo): escolher o layout, ligar a demonstração
 * e escolher o adaptador. Fica fora das propostas, que seguem só o corpo das imagens.
 */
@Composable
fun SeletorLayoutObd(obd: ObdMonitor, aoEscolherAdaptador: () -> Unit, modifier: Modifier = Modifier) {
    val config = Configuracoes.obter(LocalContext.current)
    var aberto by remember { mutableStateOf(false) }
    @Composable fun pilula(texto: String, icone: ImageVector, ativo: Boolean = false, aoTocar: () -> Unit) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(if (ativo) Cores.Destaque else Cores.Cartao).border(1.dp, Cores.Borda, RoundedCornerShape(50))
                .clickable(onClick = aoTocar).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icone, null, tint = if (ativo) Color.White else Cores.Destaque, modifier = Modifier.size(18.dp))
            Text(texto, color = if (ativo) Color.White else Cores.Texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            pilula("Layout: ${config.layoutObd.rotulo}", Icons.Filled.Dashboard) { aberto = true }
            DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
                LayoutObd.entries.forEach { l ->
                    DropdownMenuItem(text = { Text(l.rotulo + if (l == config.layoutObd) "  ✓" else "", fontSize = 18.sp) }, onClick = { config.salvarLayoutObd(l); aberto = false })
                }
            }
        }
        pilula(if (obd.estado == EstadoObd.Demonstracao) "Parar demo" else "Demo", Icons.Filled.PlayArrow, obd.estado == EstadoObd.Demonstracao) { obd.alternarDemonstracao() }
        pilula(if (obd.estado == EstadoObd.Conectado) "Conectado" else "Adaptador", Icons.Filled.Bluetooth, obd.estado == EstadoObd.Conectado, aoEscolherAdaptador)
    }
}

// ---------------------------------------------------------------------------------------------
// Peças comuns (acabamento: cartões brancos com sombra suave, mostradores com moldura e brilho)
// ---------------------------------------------------------------------------------------------

private val FormaCartaoObd = RoundedCornerShape(22.dp)

/** Cartão com sombra suave e borda fina, como nas propostas. */
@Composable
private fun Modifier.cartaoSuave(forma: androidx.compose.ui.graphics.Shape = FormaCartaoObd): Modifier =
    this.shadow(10.dp, forma, clip = false, ambientColor = Color(0x22283C64), spotColor = Color(0x33283C64))
        .clip(forma).background(Cores.CartaoSolido).border(1.dp, Cores.Borda, forma)

private val SombraNumero = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x22000000), Offset(0f, 6f), 16f))

/** Gráfico de linha suave com área (últimos 60 s). */
@Composable
private fun Sparkline(valores: List<Float>, cor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (valores.size < 2) {
            drawLine(cor.copy(alpha = 0.35f), Offset(0f, size.height * 0.6f), Offset(size.width, size.height * 0.6f), 3f, StrokeCap.Round)
            return@Canvas
        }
        val lo = valores.min(); val hi = valores.max(); val faixa = (hi - lo).coerceAtLeast(2f)
        val passo = size.width / (valores.size - 1)
        val pts = valores.mapIndexed { i, v -> Offset(i * passo, size.height * 0.88f - ((v - lo) / faixa) * size.height * 0.7f) }
        val linha = Path(); val area = Path()
        linha.moveTo(pts[0].x, pts[0].y); area.moveTo(0f, size.height); area.lineTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            // Curva suave entre os pontos (ponto médio como controle).
            val a = pts[i - 1]; val b = pts[i]; val meio = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
            linha.quadraticBezierTo(a.x, a.y, meio.x, meio.y); area.quadraticBezierTo(a.x, a.y, meio.x, meio.y)
        }
        linha.lineTo(pts.last().x, pts.last().y); area.lineTo(pts.last().x, pts.last().y)
        area.lineTo(size.width, size.height); area.close()
        drawPath(area, Brush.verticalGradient(listOf(cor.copy(alpha = 0.28f), cor.copy(alpha = 0.02f))))
        drawPath(linha, cor, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Mostrador premium de 270°: face com degradê e sombra, moldura clara, trilho, arco de valor em degradê com brilho,
 * marcas e números (alinhados de verdade sobre o círculo). O centro vem de [centro].
 */
@Composable
private fun DialPremium(
    fracao: Float, rotulos: List<String>, modifier: Modifier = Modifier, zonaVermelhaDesde: Float = 2f, menores: Int = 4,
    espessuraArco: Float = 0.075f, centro: @Composable BoxScope.(Dp) -> Unit = {},
) {
    val trilho = Cores.SegVazio; val suave = Cores.TextoSuave; val verm = Cores.Vermelho
    val azul = Cores.Destaque; val azulClaro = Color(0xFF6FB3FF); val face = Cores.CartaoSolido; val borda = Cores.Borda
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val lado = minOf(maxWidth, maxHeight)
        Box(Modifier.size(lado).shadow(18.dp, CircleShape, clip = false, ambientColor = Color(0x22283C64), spotColor = Color(0x44283C64)).clip(CircleShape)
            .background(Brush.radialGradient(listOf(face, face, borda.copy(alpha = 0.55f)))), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2); val raio = size.minDimension / 2
                // Moldura.
                drawCircle(Brush.linearGradient(listOf(Color.White, borda, Color.White), Offset(0f, 0f), Offset(size.width, size.height)), raio - raio * 0.02f, c, style = Stroke(raio * 0.04f))
                val esp = raio * espessuraArco; val rArco = raio * 0.86f
                val canto = Offset(c.x - rArco, c.y - rArco); val tam = Size(rArco * 2, rArco * 2)
                drawArc(trilho.copy(alpha = 0.7f), 135f, 270f, false, canto, tam, style = Stroke(esp, cap = StrokeCap.Round))
                if (zonaVermelhaDesde < 1f) drawArc(verm.copy(alpha = 0.25f), 135f + 270f * zonaVermelhaDesde, 270f * (1f - zonaVermelhaDesde), false, canto, tam, style = Stroke(esp, cap = StrokeCap.Butt))
                val f = fracao.coerceIn(0f, 1f)
                if (f > 0f) {
                    val grad = Brush.linearGradient(listOf(azulClaro, azul), Offset(canto.x, canto.y + tam.height), Offset(canto.x + tam.width, canto.y))
                    drawArc(azul.copy(alpha = 0.16f), 135f, 270f * f, false, canto, tam, style = Stroke(esp * 2.2f, cap = StrokeCap.Round))
                    drawArc(grad, 135f, 270f * f, false, canto, tam, style = Stroke(esp, cap = StrokeCap.Round))
                }
                val passos = (rotulos.size - 1).coerceAtLeast(1) * (menores + 1)
                val rExt = rArco - esp * 0.9f
                for (i in 0..passos) {
                    val p = i / passos.toFloat(); val a = Math.toRadians(135.0 + 270.0 * p)
                    val grande = i % (menores + 1) == 0
                    val rInt = rExt - raio * (if (grande) 0.085f else 0.045f)
                    drawLine((if (p >= zonaVermelhaDesde) verm else suave).copy(alpha = if (grande) 0.9f else 0.55f),
                        Offset(c.x + rInt * cos(a).toFloat(), c.y + rInt * sin(a).toFloat()),
                        Offset(c.x + rExt * cos(a).toFloat(), c.y + rExt * sin(a).toFloat()), raio * (if (grande) 0.014f else 0.007f), StrokeCap.Round)
                }
            }
            // Números: posicionados a partir do canto (TopStart), senão o Box os centraliza e eles saem do lugar.
            val rRot = lado / 2 * 0.60f
            val caixa = lado * 0.12f
            if (rotulos.size >= 2) rotulos.forEachIndexed { k, t ->
                val p = k / (rotulos.size - 1).toFloat(); val a = Math.toRadians(135.0 + 270.0 * p)
                Box(
                    Modifier.align(Alignment.TopStart).offset(x = lado / 2 + rRot * cos(a).toFloat() - caixa / 2, y = lado / 2 + rRot * sin(a).toFloat() - caixa / 2).size(caixa),
                    contentAlignment = Alignment.Center,
                ) { Text(t, color = if (p >= zonaVermelhaDesde) Cores.Vermelho else Cores.TextoSuave, fontSize = (lado.value * 0.05f).sp, fontWeight = FontWeight.Medium, maxLines = 1) }
            }
            centro(lado)
        }
    }
}

@Composable
private fun MiniMusica(monitor: MediaMonitor, modifier: Modifier = Modifier) {
    val m = monitor.estado
    Row(modifier.cartaoSuave(RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Cores.Chip), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MusicNote, null, tint = Cores.Texto, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(if (m.temSessao && m.titulo.isNotEmpty()) m.titulo else "Música", color = Cores.Texto, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(if (m.temSessao) m.artista else "Nenhuma mídia", color = Cores.TextoSuave, fontSize = 13.sp, maxLines = 1)
        }
        Icon(Icons.Filled.SkipPrevious, null, tint = Cores.Texto, modifier = Modifier.size(28.dp).clickable { monitor.anterior() })
        Box(Modifier.size(44.dp).clip(CircleShape).background(Cores.Texto).clickable { monitor.alternar() }, contentAlignment = Alignment.Center) {
            Icon(if (m.tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Cores.CartaoSolido, modifier = Modifier.size(26.dp))
        }
        Icon(Icons.Filled.SkipNext, null, tint = Cores.Texto, modifier = Modifier.size(28.dp).clickable { monitor.proxima() })
    }
}

@Composable
private fun CartaoDiagnostico(d: DadosObd, modifier: Modifier = Modifier, aoVerFalhas: () -> Unit) {
    val n = d.codigos.size + d.codigosPendentes.size
    val ok = n == 0 && !d.luzMotor
    val cor = if (ok) Cores.Ok else Cores.Amarelo
    Row(modifier.cartaoSuave(RoundedCornerShape(20.dp)).clickable(onClick = aoVerFalhas).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Diagnóstico", color = Cores.TextoSuave, fontSize = 13.sp)
            Text(if (ok) "OK" else "$n falha(s)", color = cor, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Box(Modifier.size(42.dp).clip(CircleShape).background(cor), contentAlignment = Alignment.Center) {
            Icon(if (ok) Icons.Filled.Check else Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 1) COCKPIT
// ---------------------------------------------------------------------------------------------

@Composable
fun LayoutCockpit(obd: ObdMonitor, monitor: MediaMonitor, vel: Float, aoVerFalhas: () -> Unit) {
    val d = obd.dados
    val ctx = LocalContext.current
    val config = remember { Configuracoes.obter(ctx) }
    val v = remember { Veiculo.obter(ctx) }
    val linha = Cores.TextoSuave
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val altura = maxHeight
            val gauge = minOf(maxHeight * 0.80f, maxWidth * 0.255f)
            // Estrada em perspectiva, bem suave, abaixo da velocidade.
            Canvas(Modifier.fillMaxSize()) {
                val yTopo = size.height * 0.58f; val cx = size.width / 2
                val pista = Path().apply {
                    moveTo(cx - size.width * 0.02f, yTopo); lineTo(cx + size.width * 0.02f, yTopo)
                    lineTo(cx + size.width * 0.20f, size.height); lineTo(cx - size.width * 0.20f, size.height); close()
                }
                drawPath(pista, Brush.verticalGradient(listOf(linha.copy(alpha = 0.0f), linha.copy(alpha = 0.10f)), startY = yTopo, endY = size.height))
                drawLine(linha.copy(alpha = 0.18f), Offset(cx - size.width * 0.02f, yTopo), Offset(cx - size.width * 0.20f, size.height), 3f)
                drawLine(linha.copy(alpha = 0.18f), Offset(cx + size.width * 0.02f, yTopo), Offset(cx + size.width * 0.20f, size.height), 3f)
                for (i in 0..4) {
                    val t0 = 0.08f + i * 0.2f; val t1 = t0 + 0.09f
                    drawLine(linha.copy(alpha = 0.22f), Offset(cx, yTopo + (size.height - yTopo) * t0), Offset(cx, yTopo + (size.height - yTopo) * t1), 3f + 5f * t0, StrokeCap.Round)
                }
            }
            Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniMusica(monitor, Modifier.width(360.dp).height(66.dp))
                CartaoDiagnostico(d, Modifier.width(210.dp).height(66.dp), aoVerFalhas)
            }
            DialPremium((d.rpm ?: 0) / 8000f, listOf("0", "1", "2", "3", "4", "5", "6", "7", "8"), Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 4.dp).size(gauge), zonaVermelhaDesde = 0.8f) { lado ->
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.2f".format(BR, (d.rpm ?: 0) / 1000f) + " × 1000", color = Cores.Texto, fontSize = (lado.value * 0.085f).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("rpm", color = Cores.TextoSuave, fontSize = (lado.value * 0.06f).sp)
                }
            }
            val consumo = d.consumoInstantaneo ?: d.consumoMedio
            DialPremium((consumo ?: 0f) / 20f, listOf("0", "5", "10", "15", "20"), Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 4.dp).size(gauge)) { lado ->
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(n1(consumo), color = Cores.Texto, fontSize = (lado.value * 0.15f).sp, fontWeight = FontWeight.Bold, maxLines = 1, style = SombraNumero)
                        Text(" km/l", color = Cores.TextoSuave, fontSize = (lado.value * 0.06f).sp, modifier = Modifier.padding(bottom = (lado.value * 0.025f).dp))
                    }
                    Text("${n1(d.consumoMedio)} km/l", color = Cores.Texto, fontSize = (lado.value * 0.075f).sp, fontWeight = FontWeight.SemiBold)
                    Text("Média da viagem", color = Cores.TextoSuave, fontSize = (lado.value * 0.045f).sp)
                }
            }
            Column(Modifier.align(Alignment.TopCenter).padding(top = altura * 0.10f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${vel.toInt()}", color = Cores.Texto, fontSize = (altura.value * 0.36f).sp, lineHeight = (altura.value * 0.36f).sp, fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic, letterSpacing = (-4).sp, maxLines = 1, style = SombraNumero)
                Text("km/h", color = Cores.TextoSuave, fontSize = 28.sp)
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(listOf(config.ano, config.nomeCarro, config.modelo).filter { it.isNotBlank() }.joinToString(" "), color = Cores.Texto, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(if (v.kmAtual > 0) "Total %,d km".format(BR, v.kmAtual) else "Total — km", color = Cores.TextoSuave, fontSize = 16.sp)
            }
        }
        // Faixa de indicadores.
        Row(Modifier.fillMaxWidth().height(86.dp).cartaoSuave().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val itens = listOf(
                Triple("Temp. Motor", n0(d.tempMotor) to "°C", Icons.Filled.Thermostat), Triple("Temp. Admissão", n0(d.tempAdmissao) to "°C", Icons.Filled.AcUnit),
                Triple("Tensão", n1(d.tensao) to "V", Icons.Filled.BatteryChargingFull), Triple("Combustível", n0(d.nivelCombustivel) to "%", Icons.Filled.LocalGasStation),
                Triple("Carga Motor", n0(d.carga) to "%", Icons.Filled.Memory), Triple("MAP", n0(d.map) to "kPa", Icons.Filled.Speed),
                Triple("Avanço", (d.avanco?.let { "%.0f".format(BR, it) } ?: "—") to "°", Icons.Filled.Tune),
            )
            itens.forEachIndexed { i, (rot, vu, ic) ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight(0.55f).background(Cores.Borda))
                Row(Modifier.weight(1f).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(ic, null, tint = Cores.TextoSuave, modifier = Modifier.size(28.dp))
                    Column {
                        Text(rot, color = Cores.TextoSuave, fontSize = 13.sp, maxLines = 1)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(vu.first, color = Cores.Texto, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(" ${vu.second}", color = Cores.TextoSuave, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 2) ÓRBITAS
// ---------------------------------------------------------------------------------------------

@Composable
private fun Etiqueta(titulo: String?, valor: String, unidade: String, icone: ImageVector?, modifier: Modifier = Modifier, grande: Boolean = false) {
    val forma = RoundedCornerShape(16.dp)
    Row(
        modifier.then(if (grande) Modifier else Modifier.cartaoSuave(forma).border(1.dp, Cores.Destaque.copy(alpha = 0.35f), forma)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icone?.let { Icon(it, null, tint = Cores.TextoSuave, modifier = Modifier.size(if (grande) 36.dp else 28.dp)) }
        Column {
            titulo?.let { Text(it, color = Cores.TextoSuave, fontSize = 14.sp, maxLines = 1) }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(valor, color = Cores.Texto, fontSize = if (grande) 52.sp else 32.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = if (grande) SombraNumero else androidx.compose.ui.text.TextStyle.Default)
                Text(" $unidade", color = Cores.TextoSuave, fontSize = if (grande) 24.sp else 16.sp, modifier = Modifier.padding(bottom = if (grande) 8.dp else 5.dp), maxLines = 1)
            }
        }
    }
}

@Composable
fun LayoutOrbitas(obd: ObdMonitor, vel: Float) {
    val d = obd.dados
    val azul = Cores.Destaque; val azulClaro = Color(0xFF7CC4FF); val tubo = Cores.SegVazio; val fio = Cores.Destaque.copy(alpha = 0.28f)
    val solido = Cores.CartaoSolido; val borda = Cores.Borda
    val fTemp = (d.tempAdmissao ?: 0) / 60f; val fTensao = ((d.tensao ?: 11f) - 11f) / 4f; val fCarga = (d.carga ?: 0) / 100f; val fComb = (d.nivelCombustivel ?: 0) / 100f
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height * 0.5f
            val rx = size.width * 0.33f; val ry = size.height * 0.36f
            val rCentro = size.height * 0.26f
            fun ponto(g: Double) = Offset(cx + rx * cos(Math.toRadians(g)).toFloat(), cy + ry * sin(Math.toRadians(g)).toFloat())
            // Órbitas finas inclinadas que cruzam pelo centro (o "atomo").
            for (ang in listOf(-14f, 14f)) rotate(ang, Offset(cx, cy)) {
                drawOval(fio, Offset(cx - rx * 0.98f, cy - ry * 0.62f), Size(rx * 1.96f, ry * 1.24f), style = Stroke(2.2f))
            }
            // Halos em volta do centro.
            for (k in 1..3) drawCircle(fio.copy(alpha = 0.22f / k), rCentro * (1.12f + k * 0.16f), Offset(cx, cy), style = Stroke(2f))
            // Anel principal em "tubo": sombra, corpo e reflexo.
            drawOval(Color(0x14000000), Offset(cx - rx, cy - ry + 6f), Size(rx * 2, ry * 2), style = Stroke(40f))
            drawOval(tubo, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), style = Stroke(30f))
            drawOval(Color.White.copy(alpha = 0.85f), Offset(cx - rx, cy - ry - 7f), Size(rx * 2, ry * 2), style = Stroke(3f))
            // Segmentos acesos (cada um ligado a um indicador).
            fun arco(de: Double, ate: Double): Path = Path().apply {
                for (i in 0..48) { val p = ponto(de + (ate - de) * i / 48); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            }
            fun no(p: Offset, r: Float) {
                drawCircle(azul.copy(alpha = 0.18f), r * 2.1f, p); drawCircle(Color.White, r, p); drawCircle(azul, r, p, style = Stroke(r * 0.4f))
            }
            fun segmento(de: Double, ate: Double, f: Float): Offset {
                val fim = de + (ate - de) * f.coerceIn(0.04f, 1f)
                val caminho = arco(de, fim)
                drawPath(caminho, azul.copy(alpha = 0.18f), style = Stroke(40f, cap = StrokeCap.Round))
                drawPath(caminho, Brush.linearGradient(listOf(azulClaro, azul), ponto(de), ponto(fim)), style = Stroke(20f, cap = StrokeCap.Round))
                drawPath(caminho, Color.White.copy(alpha = 0.55f), style = Stroke(4f, cap = StrokeCap.Round))
                no(ponto(de), 12f); no(ponto(fim), 13f)
                return ponto(de)
            }
            val pTL = segmento(196.0, 250.0, fTemp)
            val pTR = segmento(290.0, 344.0, fTensao)
            val pBL = segmento(106.0, 160.0, fCarga)
            val pBR = segmento(16.0, 72.0, fComb)
            no(ponto(180.0), 14f)
            // Linhas com cotovelo até as etiquetas.
            fun liga(de: Offset, x: Float, y: Float) {
                val fimP = Offset(size.width * x, size.height * y); val cot = Offset(de.x + (fimP.x - de.x) * 0.45f, fimP.y)
                val p = Path().apply { moveTo(de.x, de.y); lineTo(cot.x, cot.y); lineTo(fimP.x, fimP.y) }
                drawPath(p, azul.copy(alpha = 0.55f), style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(azul, 5f, fimP)
            }
            liga(pTL, 0.245f, 0.16f); liga(pTR, 0.765f, 0.16f); liga(pBL, 0.245f, 0.86f); liga(pBR, 0.765f, 0.86f)
            // Esfera central de vidro com brilho.
            drawCircle(Brush.radialGradient(listOf(azul.copy(alpha = 0.22f), Color.Transparent), Offset(cx, cy), rCentro * 1.45f), rCentro * 1.45f, Offset(cx, cy))
            drawCircle(Brush.radialGradient(listOf(Color.White, solido, borda.copy(alpha = 0.5f)), Offset(cx - rCentro * 0.2f, cy - rCentro * 0.25f), rCentro * 1.2f), rCentro, Offset(cx, cy))
            drawCircle(azulClaro.copy(alpha = 0.35f), rCentro + 10f, Offset(cx, cy), style = Stroke(14f))
            drawCircle(Brush.sweepGradient(listOf(azulClaro, azul, azulClaro, azul, azulClaro), Offset(cx, cy)), rCentro + 2f, Offset(cx, cy), style = Stroke(5f))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${vel.toInt()}", color = Cores.Texto, fontSize = (h.value * 0.25f).sp, lineHeight = (h.value * 0.25f).sp, fontWeight = FontWeight.Bold, letterSpacing = (-3).sp, maxLines = 1, style = SombraNumero)
            Text("km/h", color = Cores.TextoSuave, fontSize = 28.sp)
        }
        // Etiquetas (posições casam com as linhas desenhadas acima).
        Etiqueta("Temp. Admissão", n0(d.tempAdmissao), "°C", Icons.Filled.Thermostat, Modifier.align(Alignment.TopStart).offset(w * 0.03f, h * 0.08f).width(w * 0.215f))
        Etiqueta("Tensão", n1(d.tensao), "V", Icons.Filled.BatteryChargingFull, Modifier.align(Alignment.TopStart).offset(w * 0.765f, h * 0.08f).width(w * 0.205f))
        Etiqueta("Carga Motor", n0(d.carga), "%", Icons.Filled.Memory, Modifier.align(Alignment.TopStart).offset(w * 0.03f, h * 0.78f).width(w * 0.215f))
        Etiqueta("Consumo médio", n1(d.consumoMedio), "km/l", Icons.Filled.LocalGasStation, Modifier.align(Alignment.TopStart).offset(w * 0.765f, h * 0.78f).width(w * 0.205f))
        Etiqueta(null, n0(d.tempMotor), "°C", Icons.Filled.Thermostat, Modifier.align(Alignment.TopCenter).offset(x = w * 0.06f), grande = true)
        Etiqueta(null, n0(d.nivelCombustivel), "%", Icons.Filled.LocalGasStation, Modifier.align(Alignment.BottomCenter).offset(x = w * 0.08f), grande = true)
        Column(Modifier.align(Alignment.CenterStart).padding(start = 4.dp), horizontalAlignment = Alignment.End) {
            Text((d.rpm ?: 0).let { "%,d".format(BR, it) }, color = Cores.Texto, fontSize = 50.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = SombraNumero)
            Text("rpm", color = Cores.TextoSuave, fontSize = 20.sp)
        }
        Etiqueta("MAP", n0(d.map), "kPa", null, Modifier.align(Alignment.TopStart).offset(w * 0.635f, h * 0.30f))
        Etiqueta("Avanço", (d.avanco?.let { "%.0f".format(BR, it) } ?: "—"), "°", null, Modifier.align(Alignment.TopStart).offset(w * 0.635f, h * 0.55f))
    }
}

// ---------------------------------------------------------------------------------------------
// 3) MODULAR
// ---------------------------------------------------------------------------------------------

@Composable
private fun CartaoMetrica(titulo: String, icone: ImageVector, valor: String, unidade: String, modifier: Modifier = Modifier, rodape: @Composable ColumnScope.() -> Unit) {
    Column(modifier.cartaoSuave().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icone, null, tint = Cores.TextoSuave, modifier = Modifier.size(22.dp))
            Text(titulo, color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valor, color = Cores.Texto, fontSize = 40.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(" $unidade", color = Cores.TextoSuave, fontSize = 18.sp, modifier = Modifier.padding(bottom = 6.dp), maxLines = 1)
        }
        rodape()
    }
}

@Composable
fun LayoutModular(obd: ObdMonitor, monitor: MediaMonitor, vel: Float, aoVerFalhas: () -> Unit) {
    val d = obd.dados
    val ctx = LocalContext.current
    val series = remember { SeriesObd.obter() }
    @Suppress("UNUSED_VARIABLE") val tique = series.versao
    val cb = remember { br.com.civic.launcher.ComputadorBordo.obter(ctx) }
    val viagem = cb.atual
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // Velocidade e conta-giros.
        Column(Modifier.weight(1.12f).fillMaxHeight().cartaoSuave(RoundedCornerShape(26.dp)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Velocidade", color = Cores.Texto, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            DialPremium(vel / 200f, (0..10).map { (it * 20).toString() }, Modifier.weight(1f).fillMaxWidth(), menores = 1) { lado ->
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${vel.toInt()}", color = Cores.Texto, fontSize = (lado.value * 0.24f).sp, lineHeight = (lado.value * 0.24f).sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, maxLines = 1, style = SombraNumero)
                    Text("km/h", color = Cores.TextoSuave, fontSize = (lado.value * 0.07f).sp)
                }
            }
            Row(Modifier.cartaoSuave(RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialPremium((d.rpm ?: 0) / 9000f, emptyList(), Modifier.size(58.dp), zonaVermelhaDesde = 0.8f, menores = 0, espessuraArco = 0.13f)
                Column {
                    Text("RPM", color = Cores.TextoSuave, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%.1f".format(BR, (d.rpm ?: 0) / 1000f), color = Cores.Texto, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(" x1000 rpm", color = Cores.TextoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
                    }
                }
            }
        }
        // Cartões de indicadores.
        Column(Modifier.weight(2.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CartaoMetrica("Temp. Motor", Icons.Filled.Thermostat, n0(d.tempMotor), "°C", Modifier.weight(1f).fillMaxHeight()) { Sparkline(series.serie("tempMotor"), Cores.Destaque, Modifier.fillMaxWidth().height(44.dp)) }
                CartaoMetrica("Temp. Admissão", Icons.Filled.AcUnit, n0(d.tempAdmissao), "°C", Modifier.weight(1f).fillMaxHeight()) { Sparkline(series.serie("tempAdmissao"), Cores.Destaque, Modifier.fillMaxWidth().height(44.dp)) }
                CartaoMetrica("Tensão", Icons.Filled.BatteryChargingFull, n1(d.tensao), "V", Modifier.weight(1f).fillMaxHeight()) { Sparkline(series.serie("tensao"), Cores.Ok, Modifier.fillMaxWidth().height(44.dp)) }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CartaoMetrica("Combustível", Icons.Filled.LocalGasStation, n0(d.nivelCombustivel), "%", Modifier.weight(1f).fillMaxHeight()) { BarraFina((d.nivelCombustivel ?: 0) / 100f, Cores.Destaque, Modifier.fillMaxWidth(), 12.dp) }
                CartaoMetrica("Carga Motor", Icons.Filled.Memory, n0(d.carga), "%", Modifier.weight(1f).fillMaxHeight()) { BarraFina((d.carga ?: 0) / 100f, Cores.Destaque, Modifier.fillMaxWidth(), 12.dp) }
                CartaoMetrica("MAP", Icons.Filled.Speed, n0(d.map), "kPa", Modifier.weight(1f).fillMaxHeight()) { Sparkline(series.serie("map"), Cores.Destaque, Modifier.fillMaxWidth().height(44.dp)) }
            }
            Row(Modifier.fillMaxWidth().height(90.dp).cartaoSuave().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Speed, null, tint = Cores.TextoSuave, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Velocidade média", color = Cores.Texto, fontSize = 15.sp)
                    Row(verticalAlignment = Alignment.Bottom) { Text(viagem?.velMedia?.let { "%.0f".format(BR, it) } ?: "—", color = Cores.Texto, fontSize = 32.sp, fontWeight = FontWeight.Bold); Text(" km/h", color = Cores.TextoSuave, fontSize = 16.sp, modifier = Modifier.padding(bottom = 5.dp)) }
                }
                Box(Modifier.width(1.dp).fillMaxHeight(0.6f).background(Cores.Borda))
                Icon(Icons.Filled.Timeline, null, tint = Cores.TextoSuave, modifier = Modifier.padding(start = 20.dp).size(24.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Distância viagem", color = Cores.Texto, fontSize = 15.sp)
                    Row(verticalAlignment = Alignment.Bottom) { Text(viagem?.distanciaKm?.let { "%.1f".format(BR, it) } ?: "—", color = Cores.Texto, fontSize = 32.sp, fontWeight = FontWeight.Bold); Text(" km", color = Cores.TextoSuave, fontSize = 16.sp, modifier = Modifier.padding(bottom = 5.dp)) }
                }
            }
        }
        // Coluna lateral.
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1.2f).fillMaxWidth().cartaoSuave().padding(14.dp)) {
                Text("Ponto de Ignição", color = Cores.Texto, fontSize = 15.sp)
                DialPremium(((d.avanco ?: 0f) + 10f) / 50f, emptyList(), Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp), menores = 0, espessuraArco = 0.09f) { lado ->
                    Text(d.avanco?.let { "%.0f°".format(BR, it) } ?: "—", color = Cores.Texto, fontSize = (lado.value * 0.26f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                }
            }
            Column(Modifier.weight(0.8f).fillMaxWidth().cartaoSuave().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("Consumo Médio", color = Cores.Texto, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.Bottom) { Text(n1(d.consumoMedio), color = Cores.Texto, fontSize = 36.sp, fontWeight = FontWeight.Bold, maxLines = 1); Text(" km/l médio", color = Cores.TextoSuave, fontSize = 14.sp, modifier = Modifier.padding(bottom = 5.dp), maxLines = 1) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().cartaoSuave().clickable(onClick = aoVerFalhas).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (obd.estado == EstadoObd.Conectado) Cores.Ok else if (obd.estado == EstadoObd.Demonstracao) Cores.Waze else Cores.TextoSuave))
                    Text(when (obd.estado) { EstadoObd.Conectado -> "OBD conectado"; EstadoObd.Demonstracao -> "OBD demonstração"; else -> "OBD desconectado" }, color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                Text("Sistemas monitorados", color = Cores.TextoSuave, fontSize = 12.sp)
                Text(if (d.luzMotor) "Luz de injeção acesa" else if ((d.rpm ?: 0) > 400) "Pronto para dirigir" else "Motor desligado", color = if (d.luzMotor) Cores.Amarelo else Cores.Texto, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            MiniMusicaVertical(monitor, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun MiniMusicaVertical(monitor: MediaMonitor, modifier: Modifier) {
    val m = monitor.estado
    Column(modifier.cartaoSuave().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(br.com.civic.launcher.R.drawable.ic_spotify), null, tint = Cores.Spotify, modifier = Modifier.size(20.dp))
            Text("Spotify", color = Cores.Texto, fontSize = 15.sp)
        }
        Text(if (m.temSessao && m.titulo.isNotEmpty()) m.titulo else "Nada tocando", color = Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SkipPrevious, null, tint = Cores.Texto, modifier = Modifier.size(30.dp).clickable { monitor.anterior() })
            Box(Modifier.size(46.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Cores.Destaque).clickable { monitor.alternar() }, contentAlignment = Alignment.Center) {
                Icon(if (m.tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Icon(Icons.Filled.SkipNext, null, tint = Cores.Texto, modifier = Modifier.size(30.dp).clickable { monitor.proxima() })
        }
    }
}
