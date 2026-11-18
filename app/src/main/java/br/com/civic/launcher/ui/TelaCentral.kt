package br.com.civic.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import br.com.civic.launcher.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.abrirSpotify
import br.com.civic.launcher.obd.DadosObd
import br.com.civic.launcher.obd.EstadoObd
import br.com.civic.launcher.obd.ObdMonitor

/**
 * Painel esportivo do OBD (segue o tema dia/noite). Um único Canvas desenha o fundo, a estrada animada
 * e o "berço" brilhante que liga os três mostradores; mostradores, carro e textos ficam por cima,
 * posicionados com a mesma geometria ([GeoPainel]).
 */
@Composable
fun TelaCentral(sensores: Sensores, obd: ObdMonitor, monitor: MediaMonitor) {
    val d = obd.dados
    val vel = d.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
    var escolhendo by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val largura = maxWidth // lido aqui: dentro da coluna aninhada o Kotlin não deixa ler maxWidth
        if (maxWidth >= maxHeight) {
            PainelEsportivo(obd, d, vel, monitor, Modifier.fillMaxSize(), comInfos = true) { escolhendo = true }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PainelEsportivo(obd, d, vel, monitor, Modifier.fillMaxWidth().height(largura * 0.46f), comInfos = false) { escolhendo = true }
                InfoPainel("Temp. Motor", Icons.Filled.Thermostat, d.tempMotor?.toString(), "°C")
                InfoPainel("Tensão", Icons.Filled.BatteryChargingFull, d.tensao?.let { "%.1f".format(it) }, "V")
                InfoPainel("Temp. Admissão", Icons.Filled.AcUnit, d.tempAdmissao?.toString(), "°C")
                MiniSpotify(monitor)
            }
        }
    }

    if (escolhendo) {
        LaunchedEffect(Unit) { obd.buscarLista() }
        AlertDialog(
            onDismissRequest = { escolhendo = false },
            confirmButton = { TextButton(onClick = { escolhendo = false }) { Text("Fechar", fontSize = 22.sp) } },
            title = { Text("Escolher adaptador OBD") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (obd.buscandoLista) Text("Buscando aparelhos Bluetooth…", fontSize = 18.sp)
                    else if (obd.encontrados.isEmpty()) Text("Nenhum aparelho encontrado. Ligue o carro e tente de novo.", fontSize = 18.sp)
                    obd.encontrados.forEach { dispositivo ->
                        Text(
                            dispositivo.nome,
                            fontSize = 24.sp,
                            modifier = Modifier.fillMaxWidth().clickable { obd.escolher(dispositivo); escolhendo = false }.padding(vertical = 14.dp),
                        )
                    }
                }
            },
        )
    }
}

/** Geometria do painel, em qualquer unidade (dp para posicionar, px para desenhar); `u` = 1 dp nessa unidade. */
private class GeoPainel(val w: Float, val h: Float, u: Float) {
    val rT = minOf(h * 0.31f, w * 0.16f)          // raio do conta-giros
    val cy = rT + 8f * u                           // centro vertical dos mostradores
    val cx = w / 2
    val rS = rT * 0.80f                            // raio dos mostradores laterais
    val xEsq = w * 0.19f
    val xDir = w * 0.81f
    val rBerco = rT + 12f * u                      // arco do berço em volta do conta-giros
    val yBerco = cy + rS + 18f * u                 // linha que passa sob os laterais
    val yFuga = cy + rT * 0.55f                    // ponto de fuga da estrada (escondido atrás do conta-giros)
    val baseEsq = w * 0.25f
    val baseDir = w * 0.75f
    val larguraCarro = w * 0.19f
    val alturaCarro = larguraCarro * 448f / 720f
}

/** Cores do painel conforme o tema (dia claro / noite escuro). */
internal class CoresPainel(
    val fundo: Brush, val face: Color, val borda: Color, val trilho: Color, val texto: Color, val suave: Color,
    val estrada: Brush, val brilho: Float, val faixa: Color, val botao: Color,
) {
    val Face get() = face
    val Borda get() = borda
    val Trilho get() = trilho
    val Texto get() = texto
    val Suave get() = suave
    val Azul = Color(0xFF2F7BFF)
    val Ciano = Color(0xFF38C8FF)
    val Vermelho = Color(0xFFFF3B3B)
}

private val PainelEscuro = CoresPainel(
    fundo = Brush.verticalGradient(listOf(Color(0xFF0B1628), Color(0xFF03060C))),
    face = Color(0xFF070D19), borda = Color(0xFF1F4C8F), trilho = Color(0xFF1A2638),
    texto = Color.White, suave = Color(0xFF9DB0CC),
    estrada = Brush.verticalGradient(listOf(Color(0xFF0A1424), Color(0xFF13233D))),
    brilho = 0.35f, faixa = Color.White, botao = Color(0xCC0B1628),
)
private val PainelClaro = CoresPainel(
    fundo = Brush.verticalGradient(listOf(Color.White, Color(0xFFE9EFF8))),
    face = Color(0xFFF7FAFF), borda = Color(0xFF9DB7E0), trilho = Color(0xFFDCE4EF),
    texto = Color(0xFF0F172A), suave = Color(0xFF5B6B82),
    estrada = Brush.verticalGradient(listOf(Color(0xFFDCE5F2), Color(0xFFC3D2E8))),
    brilho = 0.18f, faixa = Color(0xFF1E293B), botao = Color(0xE6FFFFFF),
)

@Composable
internal fun coresPainel(): CoresPainel = if (LocalPaleta.current.escuro) PainelEscuro else PainelClaro

@Composable
private fun PainelEsportivo(
    obd: ObdMonitor, d: DadosObd, vel: Float, monitor: MediaMonitor, modifier: Modifier, comInfos: Boolean, aoEscolherAdaptador: () -> Unit,
) {
    val p = coresPainel()
    val forma = RoundedCornerShape(28.dp)
    // Fase da animação da estrada: anda conforme a velocidade real (parado = estrada parada).
    val velAtual by rememberUpdatedState(vel)
    var fase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var ultimo = 0L
        while (true) {
            withFrameNanos { t ->
                if (ultimo != 0L) fase = (fase + (t - ultimo) / 1e9f * (velAtual / 100f) * 2.5f) % 7f
                ultimo = t
            }
        }
    }
    val caminho = remember { Path() }

    BoxWithConstraints(modifier.clip(forma).background(p.fundo).border(1.dp, p.borda, forma)) {
        val g = GeoPainel(maxWidth.value, maxHeight.value, 1f)
        fun dp(v: Float): Dp = v.dp

        Canvas(Modifier.fillMaxSize()) {
            val k = GeoPainel(size.width, size.height, density)
            desenharFundo(k, p, fase, caminho)
        }

        // Mostradores.
        GaugeLateral(vel / 180f, "${vel.toInt()}", "km/h", null,
            Modifier.offset(dp(g.xEsq - g.rS), dp(g.cy - g.rS)).size(dp(g.rS * 2)))
        GaugeLateral((d.carga ?: 0) / 100f, d.carga?.toString() ?: "—", "% carga", null,
            Modifier.offset(dp(g.xDir - g.rS), dp(g.cy - g.rS)).size(dp(g.rS * 2)))
        TacometroPainel(d.rpm, Modifier.offset(dp(g.cx - g.rT), dp(g.cy - g.rT)).size(dp(g.rT * 2)))

        // Carro (traseira) sobre a estrada.
        Image(
            painterResource(R.drawable.carro_traseira), null,
            Modifier.offset(dp(g.cx - g.larguraCarro / 2), dp(g.h - g.alturaCarro - 2f)).size(dp(g.larguraCarro), dp(g.alturaCarro)),
            contentScale = ContentScale.Fit,
        )

        // Estado do OBD (canto superior esquerdo) e botões redondos (canto superior direito).
        Text(
            when (obd.estado) {
                EstadoObd.Desconectado -> "OBD não conectado"
                EstadoObd.Procurando -> "Procurando OBD…"
                EstadoObd.Conectando -> "Conectando…"
                EstadoObd.Conectado -> if (d.rpm == null) "Ligue o motor" else "OBD conectado"
                EstadoObd.Demonstracao -> "Demonstração"
            },
            color = p.suave, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 18.dp, top = 10.dp),
        )
        Row(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotaoRedondoPainel(if (obd.estado == EstadoObd.Demonstracao) Icons.Filled.Stop else Icons.Filled.PlayArrow) { obd.alternarDemonstracao() }
            BotaoRedondoPainel(Icons.Filled.Bluetooth) { aoEscolherAdaptador() }
        }

        if (comInfos) {
            val topo = dp(g.yBerco + 10f)
            Column(
                Modifier.offset(16.dp, topo).width(dp(g.w * 0.26f)).height(dp(g.h - g.yBerco - 14f)),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                InfoPainel("Temp. Motor", Icons.Filled.Thermostat, d.tempMotor?.toString(), "°C")
                InfoPainel("Tensão", Icons.Filled.BatteryChargingFull, d.tensao?.let { "%.1f".format(it) }, "V")
            }
            Column(
                Modifier.offset(dp(g.w * 0.735f), topo).width(dp(g.w * 0.25f)).height(dp(g.h - g.yBerco - 14f)),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                InfoPainel("Temp. Admissão", Icons.Filled.AcUnit, d.tempAdmissao?.toString(), "°C")
                MiniSpotify(monitor)
            }
        }
    }
}

/** Fundo do painel: brilhos, estrada com faixas em movimento, riscos de velocidade, berço e ligações. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.desenharFundo(g: GeoPainel, p: CoresPainel, fase: Float, caminho: Path) {
    val u = density
    val azul = p.Azul
    val ciano = p.Ciano
    val cx = g.cx
    val h = g.h

    // Brilho atrás do conta-giros.
    drawCircle(
        Brush.radialGradient(listOf(azul.copy(alpha = p.brilho), Color.Transparent), Offset(cx, g.cy), g.rBerco * 1.9f),
        g.rBerco * 1.9f, Offset(cx, g.cy),
    )

    // Estrada (triângulo do ponto de fuga até a base).
    caminho.reset()
    caminho.moveTo(cx, g.yFuga); caminho.lineTo(g.baseDir, h); caminho.lineTo(g.baseEsq, h); caminho.close()
    drawPath(caminho, p.estrada)

    val yVisivel = g.cy + g.rBerco
    val meia = (g.baseDir - g.baseEsq) / 2
    fun yEm(t: Float) = g.yFuga + (h - g.yFuga) * t * t
    // Faixas tracejadas (quadriláteros em perspectiva) que vêm em direção ao carro.
    for (k in 0 until 7) {
        val t0 = ((k + fase) % 7f) / 7f
        val t1 = minOf(1f, t0 + 0.06f)
        if (yEm(t1) < yVisivel) continue
        for (l in floatArrayOf(-1f / 3f, 0f, 1f / 3f)) {
            val x0 = cx + l * 2 * meia * t0 * t0 * 0.75f
            val x1 = cx + l * 2 * meia * t1 * t1 * 0.75f
            val w0 = (1f + 5f * t0 * t0) * u
            val w1 = (1f + 5f * t1 * t1) * u
            caminho.reset()
            caminho.moveTo(x0 - w0, yEm(t0)); caminho.lineTo(x0 + w0, yEm(t0)); caminho.lineTo(x1 + w1, yEm(t1)); caminho.lineTo(x1 - w1, yEm(t1)); caminho.close()
            drawPath(caminho, p.faixa.copy(alpha = 0.25f + 0.6f * t0))
        }
    }
    // Bordas da pista com brilho.
    for ((largura, alfa) in listOf(14f to 0.12f, 6f to 0.25f, 2.2f to 1f)) {
        drawLine(ciano.copy(alpha = alfa), Offset(cx, g.yFuga), Offset(g.baseEsq, h), largura * u, StrokeCap.Round)
        drawLine(ciano.copy(alpha = alfa), Offset(cx, g.yFuga), Offset(g.baseDir, h), largura * u, StrokeCap.Round)
    }
    // Riscos de velocidade fora da pista.
    for (k in 0 until 5) {
        val t0 = ((k * 1.37f + fase * 1.6f) % 5f) / 5f
        val t1 = minOf(1f, t0 + 0.18f)
        if (yEm(t1) < yVisivel) continue
        for (s in floatArrayOf(-1f, 1f)) {
            fun x(t: Float) = cx + s * (meia * t * t + g.w * 0.06f * t * t)
            drawLine(
                Brush.linearGradient(listOf(Color.Transparent, ciano.copy(alpha = 0.5f * t1)), Offset(x(t0), yEm(t0)), Offset(x(t1), yEm(t1))),
                Offset(x(t0), yEm(t0)), Offset(x(t1), yEm(t1)), (1f + 2f * t1) * u,
            )
        }
    }
    // Reflexo vermelho das lanternas no chão.
    drawCircle(
        Brush.radialGradient(listOf(Color(0x59FF2828), Color.Transparent), Offset(cx, h - 6 * u), g.larguraCarro * 0.7f),
        g.larguraCarro * 0.7f, Offset(cx, h - 6 * u),
    )

    // Berço: linha que passa sob os laterais e abraça a base do conta-giros (liga os três mostradores).
    val yB = g.yBerco
    val dy = yB + 2 * u - g.cy
    val dx = kotlin.math.sqrt(maxOf(0f, g.rBerco * g.rBerco - dy * dy))
    val a0 = Math.toDegrees(kotlin.math.atan2(dy, -dx).toDouble()).toFloat()
    val a1 = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat()
    caminho.reset()
    caminho.moveTo(g.w * 0.02f, yB)
    caminho.lineTo(cx - dx - 40 * u, yB)
    caminho.quadraticBezierTo(cx - dx - 8 * u, yB, cx - dx, yB + 2 * u)
    caminho.arcTo(Rect(cx - g.rBerco, g.cy - g.rBerco, cx + g.rBerco, g.cy + g.rBerco), a0, a1 - a0, false)
    caminho.quadraticBezierTo(cx + dx + 8 * u, yB, cx + dx + 40 * u, yB)
    caminho.lineTo(g.w * 0.98f, yB)
    for ((largura, alfa) in listOf(16f to 0.10f, 7f to 0.22f)) {
        drawPath(caminho, azul.copy(alpha = alfa), style = androidx.compose.ui.graphics.drawscope.Stroke(largura * u, cap = StrokeCap.Round))
    }
    drawPath(caminho, ciano, style = androidx.compose.ui.graphics.drawscope.Stroke(2f * u, cap = StrokeCap.Round))

    // Ligações horizontais entre os mostradores.
    val yLig = g.cy + g.rS * 0.55f
    for ((xa, xb) in listOf(g.xEsq + g.rS + 6 * u to cx - g.rBerco, cx + g.rBerco to g.xDir - g.rS - 6 * u)) {
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, ciano.copy(alpha = 0.9f), Color.Transparent), xa, xb),
            Offset(xa, yLig), Offset(xb, yLig), 2f * u,
        )
    }
    // Halo dos mostradores laterais.
    for (x in floatArrayOf(g.xEsq, g.xDir)) {
        drawCircle(
            Brush.radialGradient(
                0.64f to azul.copy(alpha = p.brilho), 1f to Color.Transparent,
                center = Offset(x, g.cy), radius = g.rS * 1.25f,
            ),
            g.rS * 1.25f, Offset(x, g.cy),
        )
    }
}

/** Indicador sem caixa: ícone, rótulo pequeno e valor grande. */
@Composable
private fun InfoPainel(rotulo: String, icone: ImageVector, valor: String?, unidade: String) {
    val p = coresPainel()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icone, null, tint = p.texto, modifier = Modifier.size(30.dp))
        Column {
            Text(rotulo, color = p.suave, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(valor ?: "—", color = p.texto, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (valor != null) Text(unidade, color = p.texto, fontSize = 16.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun BotaoRedondoPainel(icone: ImageVector, aoClicar: () -> Unit) {
    val p = coresPainel()
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(p.botao).border(1.dp, p.borda, CircleShape).clickable { aoClicar() },
        contentAlignment = Alignment.Center,
    ) { Icon(icone, null, tint = p.texto, modifier = Modifier.size(20.dp)) }
}

/** Mini player do Spotify em uma linha: nota, título/artista e anterior / tocar / próxima. */
@Composable
private fun MiniSpotify(monitor: MediaMonitor) {
    val ctx = LocalContext.current
    val p = coresPainel()
    val m = monitor.estado
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF4D9BFF), Color(0xFF15367F)))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f).clickable { ctx.abrirSpotify() }) {
            Text(if (m.temSessao && m.titulo.isNotEmpty()) m.titulo else "Spotify", color = p.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (m.temSessao) m.artista else "Nada tocando", color = p.suave, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BotaoMidia(Icons.Filled.SkipPrevious, false) { monitor.anterior() }
        BotaoMidia(if (m.tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow, true) { if (m.temSessao || !m.permitido) monitor.alternar() else ctx.abrirSpotify() }
        BotaoMidia(Icons.Filled.SkipNext, false) { monitor.proxima() }
    }
}

@Composable
private fun BotaoMidia(icone: ImageVector, principal: Boolean, aoClicar: () -> Unit) {
    val p = coresPainel()
    Box(
        Modifier.size(if (principal) 38.dp else 32.dp).clip(CircleShape)
            .background(if (principal) p.Azul else p.trilho).clickable { aoClicar() },
        contentAlignment = Alignment.Center,
    ) { Icon(icone, null, tint = if (principal) Color.White else p.texto, modifier = Modifier.size(20.dp)) }
}
