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
import androidx.compose.ui.graphics.Color
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

// Fundos parcialmente transparentes: o mapa do Waze continua aparecendo por trás.
private const val OPACIDADE_FUNDO = 0.68f
private const val OPACIDADE_CARTAO = 0.78f

/**
 * Modo Direção: coluna sobre o Waze (à direita em paisagem; faixa embaixo em retrato).
 * Mostra clima e hora, velocidade, música e os botões Início/Fechar.
 */
@Composable
fun PainelDirecao(sensores: Sensores, monitor: MediaMonitor, obd: ObdMonitor, aoVoltarInicio: () -> Unit, aoFechar: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Cores.Fundo.copy(alpha = OPACIDADE_FUNDO)).padding(10.dp)) {
        if (maxWidth > maxHeight) {
            // Faixa larga (tela em retrato): tudo em linha.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Velocidade(sensores, obd, Modifier.weight(0.8f).fillMaxHeight())
                Musica(monitor, Modifier.weight(1.8f).fillMaxHeight())
                Botoes(aoVoltarInicio, aoFechar, Modifier.weight(0.6f).fillMaxHeight(), vertical = true)
            }
        } else {
            // Coluna estreita (tela em paisagem).
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Cabecalho(sensores)
                Velocidade(sensores, obd, Modifier.weight(0.7f).fillMaxWidth())
                Musica(monitor, Modifier.weight(1.5f).fillMaxWidth())
                Botoes(aoVoltarInicio, aoFechar, Modifier.fillMaxWidth().height(64.dp), vertical = false)
            }
        }
    }
}

@Composable
private fun Cabecalho(sensores: Sensores) {
    val ctx = LocalContext.current
    var agora by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { agora = Date(); delay(1_000) } }
    val hora = DateFormat.getTimeFormat(ctx).format(agora)
    val clima = sensores.clima
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (clima != null) {
                val (icone, cor) = iconeClima(clima.condicao)
                Icon(icone, null, tint = cor, modifier = Modifier.size(28.dp))
                Text("${clima.temperatura}°C", color = Cores.Texto, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(hora, color = Cores.Texto, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Velocidade(sensores: Sensores, obd: ObdMonitor, modifier: Modifier) {
    val vel = obd.dados.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
    Box(modifier, contentAlignment = Alignment.Center) {
        Medidor(vel / 220f, Cores.Destaque, Modifier.aspectRatio(1f), espessura = 12.dp) { dm ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${vel.toInt()}", color = Cores.Texto, fontSize = (dm.value * 0.30f).sp, fontWeight = FontWeight.Bold)
                Text("km/h", color = Cores.TextoSuave, fontSize = (dm.value * 0.10f).sp)
            }
        }
    }
}

@Composable
private fun Musica(monitor: MediaMonitor, modifier: Modifier) {
    val m = monitor.estado
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Cores.Cartao.copy(alpha = OPACIDADE_CARTAO)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Capa, título e progresso cedem espaço; os controles abaixo nunca são espremidos.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (m.capa != null) {
                    Image(m.capa.asImageBitmap(), null, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.MusicNote, null, tint = Cores.Spotify, modifier = Modifier.size(40.dp))
                }
                Column {
                    Text(
                        if (m.temSessao && m.titulo.isNotEmpty()) m.titulo else "Spotify",
                        color = Cores.Texto, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(if (m.temSessao) m.artista else "Nada tocando", color = Cores.TextoSuave, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (m.temSessao && m.duracao > 0) BarraProgresso(m)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Controle(Icons.Filled.SkipPrevious, 48.dp, Cores.TextoSuave) { monitor.anterior() }
            Controle(if (m.tocando) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled, 64.dp, Cores.Spotify) { monitor.alternar() }
            Controle(Icons.Filled.SkipNext, 48.dp, Cores.TextoSuave) { monitor.proxima() }
        }
    }
}

@Composable
private fun Botoes(aoVoltarInicio: () -> Unit, aoFechar: () -> Unit, modifier: Modifier, vertical: Boolean) {
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BotaoIcone(Icons.Filled.Home, "Início", Modifier.weight(1f).fillMaxWidth(), aoVoltarInicio)
            BotaoIcone(Icons.Filled.Close, "Fechar", Modifier.weight(1f).fillMaxWidth(), aoFechar)
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotaoIcone(Icons.Filled.Home, "Início", Modifier.weight(1f).fillMaxHeight(), aoVoltarInicio)
            BotaoIcone(Icons.Filled.Close, "Fechar", Modifier.weight(1f).fillMaxHeight(), aoFechar)
        }
    }
}

@Composable
private fun BotaoIcone(icone: ImageVector, descricao: String, modifier: Modifier, aoTocar: () -> Unit) {
    Box(
        modifier.toqueSuave(aoTocar).clip(RoundedCornerShape(16.dp)).background(Cores.CartaoAlto.copy(alpha = OPACIDADE_CARTAO)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, descricao, tint = Cores.Texto, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun iconeClima(c: CondicaoClima): Pair<ImageVector, Color> = when (c) {
    CondicaoClima.Limpo -> Icons.Filled.WbSunny to Cores.Amarelo
    CondicaoClima.Nublado -> Icons.Filled.Cloud to Cores.TextoSuave
    CondicaoClima.Chuva -> Icons.Filled.WaterDrop to Cores.Waze
    CondicaoClima.Tempestade -> Icons.Filled.Bolt to Cores.Amarelo
}
