package br.com.civic.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.CondicaoClima
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.obd.ObdMonitor
import kotlinx.coroutines.delay
import java.util.Date

/** Faixa de cima do Modo Direção: clima, hora, velocidade e botões Início/Fechar. */
@Composable
fun FaixaSuperior(sensores: Sensores, obd: ObdMonitor, aoVoltarInicio: () -> Unit, aoFechar: () -> Unit) {
    val ctx = LocalContext.current
    var agora by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { agora = Date(); delay(1_000) } }
    val hora = DateFormat.getTimeFormat(ctx).format(agora)
    val clima = sensores.clima
    val vel = (obd.dados.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f).toInt()

    BoxWithConstraints(Modifier.fillMaxSize().background(Cores.Fundo)) {
        // Tamanhos acompanham a altura da faixa (que é uma % da tela).
        val h = maxHeight.value
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (clima != null) {
                    val (icone, cor) = iconeClima(clima.condicao)
                    Icon(icone, null, tint = cor, modifier = Modifier.size((h * 0.5f).dp))
                    Text("${clima.temperatura}°C", color = Cores.Texto, fontSize = (h * 0.38f).sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("—", color = Cores.TextoSuave, fontSize = (h * 0.38f).sp)
                }
            }
            Text(hora, color = Cores.Texto, fontSize = (h * 0.38f).sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$vel", color = Cores.Texto, fontSize = (h * 0.62f).sp, fontWeight = FontWeight.Bold)
                Text("km/h", color = Cores.TextoSuave, fontSize = (h * 0.24f).sp, modifier = Modifier.padding(bottom = (h * 0.08f).dp))
            }
            Spacer(Modifier.weight(1f))
            BotaoIcone(Icons.Filled.Home, "Início", Modifier.height((h * 0.85f).dp).aspectRatio(1.2f), aoVoltarInicio)
            BotaoIcone(Icons.Filled.Close, "Fechar", Modifier.height((h * 0.85f).dp).aspectRatio(1.2f), aoFechar)
        }
    }
}

/** Faixa de baixo do Modo Direção: Spotify (capa, título, progresso e controles). */
@Composable
fun FaixaInferior(monitor: MediaMonitor) {
    val m = monitor.estado
    BoxWithConstraints(Modifier.fillMaxSize().background(Cores.Fundo)) {
        val h = maxHeight.value
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (m.capa != null) {
                Image(m.capa.asImageBitmap(), null, Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Filled.MusicNote, null, tint = Cores.Spotify, modifier = Modifier.size((h * 0.5f).dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (m.temSessao && m.titulo.isNotEmpty()) m.titulo else "Spotify",
                    color = Cores.Texto, fontSize = (h * 0.24f).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (m.temSessao) m.artista else "Nada tocando",
                    color = Cores.TextoSuave, fontSize = (h * 0.18f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (m.temSessao && m.duracao > 0) BarraProgresso(m, mostrarTempos = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Controle(Icons.Filled.SkipPrevious, (h * 0.5f).dp, Cores.TextoSuave) { monitor.anterior() }
                Controle(if (m.tocando) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled, (h * 0.7f).dp, Cores.Spotify) { monitor.alternar() }
                Controle(Icons.Filled.SkipNext, (h * 0.5f).dp, Cores.TextoSuave) { monitor.proxima() }
            }
        }
    }
}

@Composable
private fun iconeClima(c: CondicaoClima): Pair<ImageVector, androidx.compose.ui.graphics.Color> = when (c) {
    CondicaoClima.Limpo -> Icons.Filled.WbSunny to Cores.Amarelo
    CondicaoClima.Nublado -> Icons.Filled.Cloud to Cores.TextoSuave
    CondicaoClima.Chuva -> Icons.Filled.WaterDrop to Cores.Waze
    CondicaoClima.Tempestade -> Icons.Filled.Bolt to Cores.Amarelo
}

@Composable
private fun BotaoIcone(icone: ImageVector, descricao: String, modifier: Modifier, aoTocar: () -> Unit) {
    Box(
        modifier.toqueSuave(aoTocar).clip(RoundedCornerShape(16.dp)).background(Cores.CartaoAlto),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, descricao, tint = Cores.Texto, modifier = Modifier.size(32.dp))
    }
}
