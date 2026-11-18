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
 * Painel esportivo do OBD (sempre escuro, como um quadro de instrumentos):
 * em cima velocidade | giros (maior) | carga do motor; embaixo indicadores sem caixa nas laterais,
 * o carro avançando no centro e um mini player do Spotify à direita.
 */
@Composable
fun TelaCentral(sensores: Sensores, obd: ObdMonitor, monitor: MediaMonitor) {
    val d = obd.dados
    val vel = d.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
    var escolhendo by remember { mutableStateOf(false) }
    val forma = RoundedCornerShape(28.dp)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val paisagem = maxWidth >= maxHeight
        Box(
            Modifier.fillMaxSize().clip(forma).background(Painel.Fundo).border(1.dp, Painel.Borda, forma)
        ) {
            // Brilho azul atrás do conta-giros (gradiente radial simples, sem blur).
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    Brush.radialGradient(listOf(Painel.Azul.copy(alpha = 0.22f), Color.Transparent), Offset(size.width / 2, size.height * 0.30f), size.minDimension * 0.55f),
                    size.minDimension * 0.55f, Offset(size.width / 2, size.height * 0.30f),
                )
            }
            if (paisagem) {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(Modifier.weight(1.3f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        GaugeLateral((vel / 180f), "${vel.toInt()}", "km/h", null, Modifier.weight(1f).fillMaxHeight(0.9f))
                        TacometroPainel(d.rpm, Modifier.weight(1.3f).fillMaxHeight())
                        GaugeLateral((d.carga ?: 0) / 100f, d.carga?.toString() ?: "—", "% carga", Icons.Filled.Speed, Modifier.weight(1f).fillMaxHeight(0.9f))
                    }
                    LinhaBrilho()
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                            InfoPainel("Temp. Motor", Icons.Filled.Thermostat, d.tempMotor?.toString(), "°C")
                            SeparadorPainel()
                            InfoPainel("Tensão", Icons.Filled.BatteryChargingFull, d.tensao?.let { "%.1f".format(it) }, "V")
                        }
                        CentroCarro(obd, d, vel, Modifier.weight(1.3f).fillMaxHeight()) { escolhendo = true }
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                            InfoPainel("Temp. Admissão", Icons.Filled.AcUnit, d.tempAdmissao?.toString(), "°C")
                            SeparadorPainel()
                            MiniSpotify(monitor)
                        }
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TacometroPainel(d.rpm, Modifier.fillMaxWidth().height(340.dp))
                    Row(Modifier.fillMaxWidth().height(190.dp)) {
                        GaugeLateral(vel / 180f, "${vel.toInt()}", "km/h", null, Modifier.weight(1f).fillMaxHeight())
                        GaugeLateral((d.carga ?: 0) / 100f, d.carga?.toString() ?: "—", "% carga", Icons.Filled.Speed, Modifier.weight(1f).fillMaxHeight())
                    }
                    CentroCarro(obd, d, vel, Modifier.fillMaxWidth().height(240.dp)) { escolhendo = true }
                    InfoPainel("Temp. Motor", Icons.Filled.Thermostat, d.tempMotor?.toString(), "°C")
                    InfoPainel("Tensão", Icons.Filled.BatteryChargingFull, d.tensao?.let { "%.1f".format(it) }, "V")
                    InfoPainel("Temp. Admissão", Icons.Filled.AcUnit, d.tempAdmissao?.toString(), "°C")
                    MiniSpotify(monitor)
                }
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



/** Cores fixas do painel esportivo (escuro em qualquer tema, como um quadro de instrumentos). */
internal object Painel {
    val Fundo = Brush.verticalGradient(listOf(Color(0xFF0B1628), Color(0xFF060B16), Color(0xFF03060C)))
    val Borda = Color(0xFF1F4C8F)
    val Azul = Color(0xFF2F7BFF)
    val Ciano = Color(0xFF38C8FF)
    val Vermelho = Color(0xFFFF3B3B)
    val Texto = Color.White
    val Suave = Color(0xFF9DB0CC)
    val Trilho = Color(0xFF1A2638)
    val Face = Color(0xFF070D19)
}

@Composable
private fun LinhaBrilho() {
    Box(
        Modifier.fillMaxWidth().height(2.dp).background(
            Brush.horizontalGradient(listOf(Color.Transparent, Painel.Azul.copy(alpha = 0.8f), Painel.Ciano, Painel.Azul.copy(alpha = 0.8f), Color.Transparent))
        )
    )
}

@Composable
private fun SeparadorPainel() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Painel.Borda, Color.Transparent))))
}

/** Indicador sem caixa: ícone, rótulo pequeno e valor grande (como na referência). */
@Composable
private fun InfoPainel(rotulo: String, icone: ImageVector, valor: String?, unidade: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icone, null, tint = Painel.Texto, modifier = Modifier.size(34.dp))
        Column {
            Text(rotulo, color = Painel.Suave, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(valor ?: "—", color = Painel.Texto, fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (valor != null) Text(unidade, color = Painel.Texto, fontSize = 18.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

/** Carro de trás avançando + estado do adaptador e os botões Demo / Adaptador. */
@Composable
private fun CentroCarro(obd: ObdMonitor, d: DadosObd, vel: Float, modifier: Modifier, aoEscolherAdaptador: () -> Unit) {
    Box(modifier) {
        CarroAvancando(vel, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopStart).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                when (obd.estado) {
                    EstadoObd.Desconectado -> "OBD não conectado"
                    EstadoObd.Procurando -> "Procurando OBD…"
                    EstadoObd.Conectando -> "Conectando…"
                    EstadoObd.Conectado -> if (d.rpm == null) "Ligue o motor" else "OBD conectado"
                    EstadoObd.Demonstracao -> "Demonstração"
                },
                color = Painel.Suave, fontSize = 13.sp, maxLines = 1,
            )
        }
        Row(Modifier.align(Alignment.TopEnd).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BotaoPainel(if (obd.estado == EstadoObd.Demonstracao) "Sair" else "Demo", Icons.Filled.PlayArrow) { obd.alternarDemonstracao() }
            BotaoPainel("Adaptador", Icons.Filled.Bluetooth) { aoEscolherAdaptador() }
        }
    }
}

@Composable
private fun BotaoPainel(rotulo: String, icone: ImageVector, aoClicar: () -> Unit) {
    val f = RoundedCornerShape(12.dp)
    Row(
        Modifier.clip(f).background(Color(0xCC0B1628)).border(1.dp, Painel.Borda, f).clickable { aoClicar() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icone, null, tint = Painel.Texto, modifier = Modifier.size(15.dp))
        Text(rotulo, color = Painel.Texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Mini player do Spotify em uma linha: nota, título/artista e anterior / tocar / próxima. */
@Composable
private fun MiniSpotify(monitor: MediaMonitor) {
    val ctx = LocalContext.current
    val m = monitor.estado
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF4D9BFF), Color(0xFF15367F)))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f).clickable { ctx.abrirSpotify() }) {
            Text(if (m.temSessao && m.titulo.isNotEmpty()) m.titulo else "Spotify", color = Painel.Texto, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (m.temSessao) m.artista else "Nada tocando", color = Painel.Suave, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BotaoMidia(Icons.Filled.SkipPrevious, false) { monitor.anterior() }
        BotaoMidia(if (m.tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow, true) { if (m.temSessao || !m.permitido) monitor.alternar() else ctx.abrirSpotify() }
        BotaoMidia(Icons.Filled.SkipNext, false) { monitor.proxima() }
    }
}

@Composable
private fun BotaoMidia(icone: ImageVector, principal: Boolean, aoClicar: () -> Unit) {
    Box(
        Modifier.size(if (principal) 42.dp else 36.dp).clip(CircleShape)
            .background(if (principal) Color.White else Painel.Trilho).clickable { aoClicar() },
        contentAlignment = Alignment.Center,
    ) { Icon(icone, null, tint = if (principal) Color(0xFF0B1628) else Color.White, modifier = Modifier.size(22.dp)) }
}
