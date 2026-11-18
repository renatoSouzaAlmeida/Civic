package br.com.civic.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.Sensores

/** Conteúdo do painel sobreposto ao Waze: velocímetro, música e botões. */
@Composable
fun PainelDirecao(sensores: Sensores, monitor: MediaMonitor, aoVoltarInicio: () -> Unit, aoFechar: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Cores.Fundo).padding(10.dp)) {
        if (maxWidth > maxHeight) {
            // Faixa larga (tela em retrato): tudo em linha.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Velocidade(sensores, Modifier.weight(0.8f).fillMaxHeight())
                Musica(monitor, Modifier.weight(1.8f).fillMaxHeight())
                Botoes(aoVoltarInicio, aoFechar, Modifier.weight(0.6f).fillMaxHeight(), vertical = true)
            }
        } else {
            // Coluna estreita (tela em paisagem).
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Velocidade(sensores, Modifier.weight(1f).fillMaxWidth())
                Musica(monitor, Modifier.weight(1.3f).fillMaxWidth())
                Botoes(aoVoltarInicio, aoFechar, Modifier.fillMaxWidth().height(64.dp), vertical = false)
            }
        }
    }
}

@Composable
private fun Velocidade(sensores: Sensores, modifier: Modifier) {
    val vel = sensores.velocidadeKmh ?: 0f
    Box(modifier, contentAlignment = Alignment.Center) {
        Medidor(vel / 220f, Cores.Destaque, Modifier.aspectRatio(1f), espessura = 12.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${vel.toInt()}", color = Cores.Texto, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text("km/h", color = Cores.TextoSuave, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun Musica(monitor: MediaMonitor, modifier: Modifier) {
    val m = monitor.estado
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Cores.Cartao).padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (m.capa != null) {
                Image(m.capa.asImageBitmap(), null, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Filled.MusicNote, null, tint = Cores.Spotify, modifier = Modifier.size(40.dp))
            }
            Column {
                Text(
                    if (m.temSessao && m.titulo.isNotEmpty()) m.titulo else "Música",
                    color = Cores.Texto, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(if (m.temSessao) m.artista else "Nada tocando", color = Cores.TextoSuave, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (m.temSessao && m.duracao > 0) BarraProgresso(m)
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
        modifier.toqueSuave(aoTocar).clip(RoundedCornerShape(16.dp)).background(Cores.CartaoAlto),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, descricao, tint = Cores.Texto, modifier = Modifier.size(32.dp))
    }
}
