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
// Peças comuns
// ---------------------------------------------------------------------------------------------

/** Gráfico de linha com área (últimos 60 s). */
@Composable
private fun Sparkline(valores: List<Float>, cor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (valores.size < 2) {
            drawLine(cor.copy(alpha = 0.35f), Offset(0f, size.height * 0.6f), Offset(size.width, size.height * 0.6f), 3f, StrokeCap.Round)
            return@Canvas
        }
        val lo = valores.min(); val hi = valores.max(); val faixa = (hi - lo).coerceAtLeast(2f)
        val passo = size.width / (valores.size - 1)
        val linha = Path(); val area = Path()
        valores.forEachIndexed { i, v ->
            val x = i * passo
            val y = size.height * 0.9f - ((v - lo) / faixa) * size.height * 0.75f
            if (i == 0) { linha.moveTo(x, y); area.moveTo(x, size.height); area.lineTo(x, y) } else { linha.lineTo(x, y); area.lineTo(x, y) }
        }
        area.lineTo(size.width, size.height); area.close()
        drawPath(area, cor.copy(alpha = 0.16f))
        drawPath(linha, cor, style = Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Mostrador de 270° com marcas e números. [fracao] 0..1; [rotulos] distribuídos do início ao fim; o centro vem de [centro]. */
@Composable
private fun DialNumerado(
    fracao: Float, rotulos: List<String>, modifier: Modifier = Modifier, cor: Color = Cores.Destaque, zonaVermelhaDesde: Float = 2f,
    menores: Int = 4, centro: @Composable BoxScope.(Dp) -> Unit = {},
) {
    val trilho = Cores.SegVazio; val suave = Cores.TextoSuave; val verm = Cores.Vermelho
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val lado = minOf(maxWidth, maxHeight)
        Box(Modifier.size(lado), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2); val raio = size.minDimension / 2
                val esp = raio * 0.075f; val rArco = raio - esp
                val canto = Offset(c.x - rArco, c.y - rArco); val tam = Size(rArco * 2, rArco * 2)
                drawArc(trilho, 135f, 270f, false, canto, tam, style = Stroke(esp, cap = StrokeCap.Round))
                val f = fracao.coerceIn(0f, 1f)
                if (f > 0f) drawArc(cor, 135f, 270f * f, false, canto, tam, style = Stroke(esp, cap = StrokeCap.Round))
                val passos = (rotulos.size - 1) * (menores + 1)
                val rExt = rArco - esp * 1.1f
                for (i in 0..passos) {
                    val p = i / passos.toFloat(); val a = Math.toRadians(135.0 + 270.0 * p)
                    val grande = i % (menores + 1) == 0
                    val rInt = rExt - raio * (if (grande) 0.09f else 0.045f)
                    drawLine(if (p >= zonaVermelhaDesde) verm else suave, Offset(c.x + rInt * cos(a).toFloat(), c.y + rInt * sin(a).toFloat()),
                        Offset(c.x + rExt * cos(a).toFloat(), c.y + rExt * sin(a).toFloat()), raio * (if (grande) 0.016f else 0.008f), StrokeCap.Round)
                }
            }
            val rRot = lado / 2 * 0.66f
            rotulos.forEachIndexed { k, t ->
                val p = k / (rotulos.size - 1).toFloat(); val a = Math.toRadians(135.0 + 270.0 * p)
                Box(Modifier.offset(x = lado / 2 + rRot * cos(a).toFloat() - 16.dp, y = lado / 2 + rRot * sin(a).toFloat() - 12.dp).size(32.dp, 24.dp), contentAlignment = Alignment.Center) {
                    Text(t, color = if (p >= zonaVermelhaDesde) Cores.Vermelho else Cores.TextoSuave, fontSize = (lado.value * 0.055f).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            centro(lado)
        }
    }
}

@Composable
private fun BotaoLayout(texto: String, icone: ImageVector, modifier: Modifier = Modifier, destaque: Boolean = false, aoTocar: () -> Unit) {
    val forma = RoundedCornerShape(18.dp)
    Row(
        modifier.clip(forma).then(if (destaque) Modifier.background(Cores.Destaque) else Modifier.background(Cores.Cartao).border(1.dp, Cores.Borda, forma))
            .clickable(onClick = aoTocar).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icone, null, tint = if (destaque) Color.White else Cores.Destaque, modifier = Modifier.size(28.dp))
        Text(texto, color = if (destaque) Color.White else Cores.Texto, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun MiniMusica(monitor: MediaMonitor, modifier: Modifier = Modifier) {
    val m = monitor.estado
    Row(modifier.clip(RoundedCornerShape(18.dp)).background(Cores.Cartao).border(1.dp, Cores.Borda, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Filled.MusicNote, null, tint = Cores.TextoSuave, modifier = Modifier.size(30.dp))
        Column(Modifier.weight(1f)) {
            Text(if (m.temSessao && m.titulo.isNotEmpty()) m.titulo else "Música", color = Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(if (m.temSessao) m.artista else "Nenhuma mídia", color = Cores.TextoSuave, fontSize = 13.sp, maxLines = 1)
        }
        Icon(Icons.Filled.SkipPrevious, null, tint = Cores.Texto, modifier = Modifier.size(26.dp).clickable { monitor.anterior() })
        Box(Modifier.size(40.dp).clip(CircleShape).background(Cores.Texto).clickable { monitor.alternar() }, contentAlignment = Alignment.Center) {
            Icon(if (m.tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Cores.CartaoSolido, modifier = Modifier.size(24.dp))
        }
        Icon(Icons.Filled.SkipNext, null, tint = Cores.Texto, modifier = Modifier.size(26.dp).clickable { monitor.proxima() })
    }
}

@Composable
private fun CartaoDiagnostico(d: DadosObd, modifier: Modifier = Modifier, aoVerFalhas: () -> Unit) {
    val n = d.codigos.size + d.codigosPendentes.size
    val cor = if (d.luzMotor || n > 0) Cores.Amarelo else Cores.Ok
    Row(modifier.clip(RoundedCornerShape(18.dp)).background(Cores.Cartao).border(1.dp, Cores.Borda, RoundedCornerShape(18.dp)).clickable(onClick = aoVerFalhas).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Diagnóstico", color = Cores.TextoSuave, fontSize = 13.sp)
            Text(if (n == 0 && !d.luzMotor) "OK" else "$n falha(s)", color = cor, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(Modifier.size(38.dp).clip(CircleShape).background(cor), contentAlignment = Alignment.Center) {
            Icon(if (n == 0 && !d.luzMotor) Icons.Filled.Check else Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 1) COCKPIT — dois mostradores, velocidade no centro com estrada, faixa de indicadores e botões
// ---------------------------------------------------------------------------------------------

@Composable
fun LayoutCockpit(obd: ObdMonitor, monitor: MediaMonitor, vel: Float, aoVerFalhas: () -> Unit) {
    val d = obd.dados
    val ctx = LocalContext.current
    val config = remember { Configuracoes.obter(ctx) }
    val v = remember { Veiculo.obter(ctx) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val gauge = minOf(maxHeight * 0.86f, maxWidth * 0.27f)
            val altura = maxHeight
            // Estrada em perspectiva, bem suave, atrás da velocidade.
            val linha = Cores.TextoSuave
            Canvas(Modifier.fillMaxSize()) {
                val topo = Offset(size.width / 2, size.height * 0.52f)
                drawLine(linha.copy(alpha = 0.22f), topo, Offset(size.width * 0.18f, size.height), 4f)
                drawLine(linha.copy(alpha = 0.22f), topo, Offset(size.width * 0.82f, size.height), 4f)
                for (i in 0..5) {
                    val t0 = i / 6f; val t1 = t0 + 0.07f
                    drawLine(linha.copy(alpha = 0.18f), Offset(topo.x, topo.y + (size.height - topo.y) * t0), Offset(topo.x, topo.y + (size.height - topo.y) * t1), 4f + 6f * t0)
                }
            }
            // Cartões do canto superior direito.
            Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniMusica(monitor, Modifier.width(360.dp))
                CartaoDiagnostico(d, Modifier.width(190.dp), aoVerFalhas)
            }
            // Conta-giros à esquerda.
            DialNumerado(((d.rpm ?: 0) / 8000f), listOf("0", "1", "2", "3", "4", "5", "6", "7", "8"), Modifier.align(Alignment.BottomStart).padding(start = 8.dp).size(gauge), zonaVermelhaDesde = 0.87f) { lado ->
                Column(Modifier.align(Alignment.Center).offset(y = lado * 0.16f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.2f × 1000".format(BR, (d.rpm ?: 0) / 1000f), color = Cores.Texto, fontSize = (lado.value * 0.085f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("rpm", color = Cores.TextoSuave, fontSize = (lado.value * 0.065f).sp)
                }
            }
            // Consumo à direita.
            DialNumerado(((d.consumoInstantaneo ?: 0f) / 20f), listOf("0", "5", "10", "15", "20"), Modifier.align(Alignment.BottomEnd).padding(end = 8.dp).size(gauge), menores = 4) { lado ->
                Column(Modifier.align(Alignment.Center).offset(y = lado * 0.06f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(n1(d.consumoInstantaneo), color = Cores.Texto, fontSize = (lado.value * 0.17f).sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(" km/l", color = Cores.TextoSuave, fontSize = (lado.value * 0.07f).sp, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Text("${n1(d.consumoMedio)} km/l", color = Cores.Texto, fontSize = (lado.value * 0.095f).sp, fontWeight = FontWeight.Bold)
                    Text("Média da viagem", color = Cores.TextoSuave, fontSize = (lado.value * 0.05f).sp)
                }
            }
            // Velocidade, carro e quilometragem.
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${vel.toInt()}", color = Cores.Texto, fontSize = (altura.value * 0.46f).sp, lineHeight = (altura.value * 0.46f).sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, maxLines = 1)
                Text("km/h", color = Cores.TextoSuave, fontSize = 30.sp)
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(listOf(config.ano, config.nomeCarro).filter { it.isNotBlank() }.joinToString(" ") + " " + config.modelo, color = Cores.Texto, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(if (v.kmAtual > 0) "Total %,d km".format(BR, v.kmAtual) else "Total — km", color = Cores.TextoSuave, fontSize = 16.sp)
            }
        }
        // Faixa de indicadores.
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Cores.Cartao).border(1.dp, Cores.Borda, RoundedCornerShape(22.dp)).padding(vertical = 10.dp, horizontal = 8.dp)) {
            val itens = listOf(
                Triple("Temp. Motor", n0(d.tempMotor) to "°C", Icons.Filled.Thermostat), Triple("Temp. Admissão", n0(d.tempAdmissao) to "°C", Icons.Filled.AcUnit),
                Triple("Tensão", n1(d.tensao) to "V", Icons.Filled.BatteryChargingFull), Triple("Combustível", n0(d.nivelCombustivel) to "%", Icons.Filled.LocalGasStation),
                Triple("Carga Motor", n0(d.carga) to "%", Icons.Filled.Memory), Triple("MAP", n0(d.map) to "kPa", Icons.Filled.Speed),
                Triple("Avanço", (d.avanco?.let { "%.0f".format(BR, it) } ?: "—") to "°", Icons.Filled.Tune),
            )
            itens.forEach { (rot, vu, ic) ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    Icon(ic, null, tint = Cores.TextoSuave, modifier = Modifier.size(26.dp))
                    Column {
                        Text(rot, color = Cores.TextoSuave, fontSize = 12.sp, maxLines = 1)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(vu.first, color = Cores.Texto, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Text(" ${vu.second}", color = Cores.TextoSuave, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 2) ÓRBITAS — anéis elípticos em volta da velocidade, com os indicadores ligados por linhas
// ---------------------------------------------------------------------------------------------

@Composable
private fun Etiqueta(titulo: String?, valor: String, unidade: String, icone: ImageVector?, modifier: Modifier = Modifier, grande: Boolean = false) {
    val forma = RoundedCornerShape(12.dp)
    Row(
        modifier.then(if (grande) Modifier else Modifier.clip(forma).background(Cores.Cartao).border(1.dp, Cores.Destaque.copy(alpha = 0.45f), forma)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icone?.let { Icon(it, null, tint = Cores.TextoSuave, modifier = Modifier.size(if (grande) 34.dp else 26.dp)) }
        Column {
            titulo?.let { Text(it, color = Cores.TextoSuave, fontSize = 13.sp, maxLines = 1) }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(valor, color = Cores.Texto, fontSize = if (grande) 46.sp else 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(" $unidade", color = Cores.TextoSuave, fontSize = if (grande) 22.sp else 15.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
fun LayoutOrbitas(obd: ObdMonitor, vel: Float) {
    val d = obd.dados
    val azul = Cores.Destaque; val vaz = Cores.SegVazio; val fio = Cores.Destaque.copy(alpha = 0.35f); val solido = Cores.CartaoSolido
    val fTemp = ((d.tempAdmissao ?: 0) / 60f); val fTensao = (((d.tensao ?: 11f) - 11f) / 4f); val fCarga = ((d.carga ?: 0) / 100f); val fComb = ((d.nivelCombustivel ?: 0) / 100f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height * 0.50f
            val rx = size.width * 0.37f; val ry = size.height * 0.31f
            fun ponto(g: Double) = Offset(cx + rx * cos(Math.toRadians(g)).toFloat(), cy + ry * sin(Math.toRadians(g)).toFloat())
            // Anéis: o elíptico principal, outro inclinado e o círculo central.
            drawOval(vaz.copy(alpha = 0.55f), Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), style = Stroke(26f))
            drawOval(fio, Offset(cx - rx * 0.97f, cy - ry * 0.9f), Size(rx * 1.94f, ry * 1.8f), style = Stroke(2f))
            drawCircle(fio, ry * 1.12f, Offset(cx, cy), style = Stroke(2f))
            drawCircle(fio.copy(alpha = 0.2f), ry * 1.32f, Offset(cx, cy), style = Stroke(2f))
            // Segmentos com preenchimento: cada um liga a um indicador.
            fun segmento(de: Double, ate: Double, f: Float) {
                val base = Path(); val cheio = Path()
                for (i in 0..40) { val g = de + (ate - de) * i / 40; val p = ponto(g); if (i == 0) base.moveTo(p.x, p.y) else base.lineTo(p.x, p.y) }
                drawPath(base, vaz, style = Stroke(22f, cap = StrokeCap.Round))
                val fim = de + (ate - de) * f.coerceIn(0.02f, 1f)
                for (i in 0..40) { val g = de + (fim - de) * i / 40; val p = ponto(g); if (i == 0) cheio.moveTo(p.x, p.y) else cheio.lineTo(p.x, p.y) }
                drawPath(cheio, azul.copy(alpha = 0.25f), style = Stroke(34f, cap = StrokeCap.Round))
                drawPath(cheio, azul, style = Stroke(20f, cap = StrokeCap.Round))
                drawCircle(Color.White, 11f, ponto(fim)); drawCircle(azul, 11f, ponto(fim), style = Stroke(4f))
            }
            segmento(200.0, 258.0, fTemp)      // superior esquerdo: temperatura de admissão
            segmento(282.0, 340.0, fTensao)    // superior direito: tensão
            segmento(100.0, 158.0, fCarga)     // inferior esquerdo: carga do motor
            segmento(22.0, 80.0, fComb)        // inferior direito: combustível
            // Conta-giros: nó no lado esquerdo do anel.
            drawCircle(Color.White, 14f, ponto(180.0)); drawCircle(azul, 14f, ponto(180.0), style = Stroke(5f))
            // Linhas até as etiquetas.
            fun liga(de: Offset, paraX: Float, paraY: Float) = drawLine(fio, de, Offset(size.width * paraX, size.height * paraY), 2.5f)
            liga(ponto(207.0), 0.27f, 0.23f); liga(ponto(334.0), 0.745f, 0.22f); liga(ponto(110.0), 0.265f, 0.76f); liga(ponto(30.0), 0.755f, 0.76f)
            // Círculo central.
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), solido), Offset(cx, cy), ry * 0.95f), ry * 0.95f, Offset(cx, cy))
            drawCircle(azul.copy(alpha = 0.55f), ry * 0.95f, Offset(cx, cy), style = Stroke(6f))
        }
        // Velocidade no centro.
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${vel.toInt()}", color = Cores.Texto, fontSize = (h.value * 0.30f).sp, lineHeight = (h.value * 0.30f).sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text("km/h", color = Cores.TextoSuave, fontSize = 30.sp)
        }
        // Etiquetas.
        Etiqueta("Temp. Admissão", n0(d.tempAdmissao), "°C", Icons.Filled.Thermostat, Modifier.offset(w * 0.07f, h * 0.10f))
        Etiqueta(null, n0(d.tempMotor), "°C", Icons.Filled.Thermostat, Modifier.offset(w * 0.46f, h * 0.0f), grande = true)
        Etiqueta("Tensão", n1(d.tensao), "V", Icons.Filled.BatteryChargingFull, Modifier.offset(w * 0.72f, h * 0.08f))
        Etiqueta(null, (d.rpm ?: 0).let { "%,d".format(BR, it) }, "rpm", null, Modifier.offset(w * 0.02f, h * 0.43f), grande = true)
        Etiqueta("MAP", n0(d.map), "kPa", null, Modifier.offset(w * 0.60f, h * 0.38f))
        Etiqueta("Avanço", (d.avanco?.let { "%.0f".format(BR, it) } ?: "—"), "°", null, Modifier.offset(w * 0.61f, h * 0.55f))
        Etiqueta("Carga Motor", n0(d.carga), "%", Icons.Filled.Memory, Modifier.offset(w * 0.09f, h * 0.76f))
        Etiqueta(null, n0(d.nivelCombustivel), "%", Icons.Filled.LocalGasStation, Modifier.offset(w * 0.50f, h * 0.80f), grande = true)
        Etiqueta("Consumo médio", n1(d.consumoMedio), "km/l", Icons.Filled.LocalGasStation, Modifier.offset(w * 0.74f, h * 0.76f))
    }
}

// ---------------------------------------------------------------------------------------------
// 3) MODULAR — "central de comando": velocímetro, cartões com gráfico e coluna lateral
// ---------------------------------------------------------------------------------------------

@Composable
private fun CartaoMetrica(titulo: String, icone: ImageVector, valor: String, unidade: String, modifier: Modifier = Modifier, rodape: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(Cores.Cartao).border(1.dp, Cores.Borda, RoundedCornerShape(22.dp)).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icone, null, tint = Cores.TextoSuave, modifier = Modifier.size(22.dp))
            Text(titulo, color = Cores.TextoSuave, fontSize = 14.sp, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valor, color = Cores.Texto, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(" $unidade", color = Cores.TextoSuave, fontSize = 17.sp, modifier = Modifier.padding(bottom = 5.dp))
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
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Velocidade (com mini conta-giros).
        Box(Modifier.weight(1.15f).fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(Cores.Cartao).border(1.dp, Cores.Borda, RoundedCornerShape(26.dp))) {
            Text("Velocidade", color = Cores.Texto, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
            DialNumerado(vel / 200f, (0..10).map { (it * 20).toString() }, Modifier.align(Alignment.Center).fillMaxSize().padding(top = 36.dp, bottom = 12.dp, start = 10.dp, end = 10.dp), menores = 1, zonaVermelhaDesde = 2f) { lado ->
                Column(Modifier.align(Alignment.Center).offset(y = -lado * 0.05f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${vel.toInt()}", color = Cores.Texto, fontSize = (lado.value * 0.26f).sp, lineHeight = (lado.value * 0.26f).sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, maxLines = 1)
                    Text("km/h", color = Cores.TextoSuave, fontSize = (lado.value * 0.075f).sp)
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).clip(RoundedCornerShape(22.dp)).background(Cores.CartaoSolido).border(1.dp, Cores.Borda, RoundedCornerShape(22.dp)).padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("RPM", color = Cores.TextoSuave, fontSize = 13.sp)
                Text("%.1f".format(BR, (d.rpm ?: 0) / 1000f), color = Cores.Texto, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Text("x1000 rpm", color = Cores.TextoSuave, fontSize = 12.sp)
            }
        }
        // Cartões de indicadores.
        Column(Modifier.weight(2.35f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CartaoMetrica("Temp. Motor", Icons.Filled.Thermostat, n0(d.tempMotor), "°C", Modifier.weight(1f).fillMaxHeight()) { Sparkline(series.serie("tempMotor"), Cores.Destaque, Modifier.fillMaxWidth().height(46.dp)) }
                CartaoMetrica("Temp. Admissão", Icons.Filled.AcUnit, n0(d.tempAdmissao), "°C", Modifier.weight(1f).fillMaxHeight()) { Sparkline(series.serie("tempAdmissao"), Cores.Destaque, Modifier.fillMaxWidth().height(46.dp)) }
                CartaoMetrica("Tensão", Icons.Filled.BatteryChargingFull, n1(d.tensao), "V", Modifier.weight(1f).fillMaxHeight()) { Sparkline(series.serie("tensao"), Cores.Ok, Modifier.fillMaxWidth().height(46.dp)) }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CartaoMetrica("Combustível", Icons.Filled.LocalGasStation, n0(d.nivelCombustivel), "%", Modifier.weight(1f).fillMaxHeight()) { BarraFina((d.nivelCombustivel ?: 0) / 100f, Cores.Destaque, Modifier.fillMaxWidth(), 12.dp) }
                CartaoMetrica("Carga Motor", Icons.Filled.Memory, n0(d.carga), "%", Modifier.weight(1f).fillMaxHeight()) { BarraFina((d.carga ?: 0) / 100f, Cores.Destaque, Modifier.fillMaxWidth(), 12.dp) }
                CartaoMetrica("MAP", Icons.Filled.Speed, n0(d.map), "kPa", Modifier.weight(1f).fillMaxHeight()) { Sparkline(series.serie("map"), Cores.Destaque, Modifier.fillMaxWidth().height(46.dp)) }
            }
            Row(
                Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(22.dp)).background(Cores.Cartao).border(1.dp, Cores.Borda, RoundedCornerShape(22.dp)).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Velocidade média", color = Cores.TextoSuave, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.Bottom) { Text(viagem?.velMedia?.let { "%.0f".format(BR, it) } ?: "—", color = Cores.Texto, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold); Text(" km/h", color = Cores.TextoSuave, fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp)) }
                }
                Box(Modifier.width(1.dp).fillMaxHeight(0.7f).background(Cores.Borda))
                Column(Modifier.weight(1f).padding(start = 18.dp)) {
                    Text("Distância viagem", color = Cores.TextoSuave, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.Bottom) { Text(viagem?.distanciaKm?.let { "%.1f".format(BR, it) } ?: "—", color = Cores.Texto, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold); Text(" km", color = Cores.TextoSuave, fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp)) }
                }
            }
        }
        // Coluna lateral.
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1.15f).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Cores.Cartao).border(1.dp, Cores.Borda, RoundedCornerShape(22.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Ponto de Ignição", color = Cores.TextoSuave, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
                DialNumerado(((d.avanco ?: 0f) + 10f) / 50f, emptyList<String>().ifEmpty { listOf("", "") }, Modifier.weight(1f).fillMaxWidth(), menores = 0) { lado ->
                    Text(d.avanco?.let { "%.0f°".format(BR, it) } ?: "—", color = Cores.Texto, fontSize = (lado.value * 0.26f).sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.Center))
                }
            }
            Column(Modifier.weight(0.85f).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Cores.Cartao).border(1.dp, Cores.Borda, RoundedCornerShape(22.dp)).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("Consumo Médio", color = Cores.TextoSuave, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.Bottom) { Text(n1(d.consumoMedio), color = Cores.Texto, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold); Text(" km/l médio", color = Cores.TextoSuave, fontSize = 15.sp, modifier = Modifier.padding(bottom = 5.dp)) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Cores.Cartao).border(1.dp, Cores.Borda, RoundedCornerShape(22.dp)).clickable(onClick = aoVerFalhas).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (obd.estado == EstadoObd.Conectado) Cores.Ok else if (obd.estado == EstadoObd.Demonstracao) Cores.Waze else Cores.TextoSuave))
                    Text(when (obd.estado) { EstadoObd.Conectado -> "OBD conectado"; EstadoObd.Demonstracao -> "OBD demonstração"; else -> "OBD desconectado" }, color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("Sistemas monitorados", color = Cores.TextoSuave, fontSize = 12.sp)
                Text(if (d.luzMotor) "Atenção: luz de injeção acesa" else if ((d.rpm ?: 0) > 400) "Pronto para dirigir" else "Motor desligado", color = Cores.Texto, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            MiniMusicaVertical(monitor, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun MiniMusicaVertical(monitor: MediaMonitor, modifier: Modifier) {
    val m = monitor.estado
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(Cores.Cartao).border(1.dp, Cores.Borda, RoundedCornerShape(22.dp)).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text("Spotify", color = Cores.TextoSuave, fontSize = 14.sp)
        Text(if (m.temSessao && m.titulo.isNotEmpty()) m.titulo else "Nada tocando", color = Cores.Texto, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SkipPrevious, null, tint = Cores.Texto, modifier = Modifier.size(30.dp).clickable { monitor.anterior() })
            Box(Modifier.size(44.dp).clip(CircleShape).background(Cores.Destaque).clickable { monitor.alternar() }, contentAlignment = Alignment.Center) {
                Icon(if (m.tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Icon(Icons.Filled.SkipNext, null, tint = Cores.Texto, modifier = Modifier.size(30.dp).clickable { monitor.proxima() })
        }
    }
}
