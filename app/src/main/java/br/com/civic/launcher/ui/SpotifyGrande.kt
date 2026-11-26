package br.com.civic.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.R
import br.com.civic.launcher.abrirSpotify

/**
 * Spotify de todos os layouts do OBD (o mesmo do Esportivo): capa do álbum grande, música, artista e anterior / tocar-pausar / próxima.
 * Os controles nunca somem: com espaço largo, capa à esquerda e texto e controles à direita; estreito (tela dividida), capa em cima
 * e texto e controles embaixo. Sem capa (ou sem o acesso às notificações), o logo do Spotify no lugar.
 */
@Composable
internal fun SpotifyGrande(
    monitor: MediaMonitor, modifier: Modifier = Modifier,
    texto: Color = Cores.Texto, suave: Color = Cores.TextoSuave, trilho: Color = Cores.Chip, azul: Color = Cores.Destaque, borda: Color = Cores.Borda,
) {
    val ctx = LocalContext.current
    val m = monitor.estado
    val capa = remember(m.capa) { m.capa?.asImageBitmap() }
    val titulo = if (m.temSessao && m.titulo.isNotEmpty()) m.titulo else "Spotify"
    val sub = when { !m.permitido -> "Toque para ativar capa e título"; m.temSessao -> m.artista; else -> "Toque para abrir" }

    @Composable fun Capa(mod: Modifier) {
        val forma = RoundedCornerShape(16.dp)
        Box(
            mod.clip(forma).background(Brush.linearGradient(listOf(Color(0xFF4D9BFF), Color(0xFF15367F)))).border(1.dp, borda, forma).clickable { ctx.abrirSpotify() },
            contentAlignment = Alignment.Center,
        ) {
            if (capa != null) Image(capa, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(painterResource(R.drawable.ic_spotify), null, tint = Color.White, modifier = Modifier.fillMaxSize(0.42f))
        }
    }

    @Composable fun Controles(tamanho: Dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Botao(Icons.Filled.SkipPrevious, false, tamanho, texto, trilho, azul) { monitor.anterior() }
            Botao(if (m.tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow, true, tamanho, texto, trilho, azul) { if (m.temSessao || !m.permitido) monitor.alternar() else ctx.abrirSpotify() }
            Botao(Icons.Filled.SkipNext, false, tamanho, texto, trilho, azul) { monitor.proxima() }
        }
    }

    BoxWithConstraints(modifier) {
        val largo = maxWidth >= maxHeight * 1.45f
        if (largo) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Capa(Modifier.fillMaxHeight().aspectRatio(1f))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                Column(Modifier.clickable { ctx.abrirSpotify() }) {
                    Text(titulo, color = texto, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(sub, color = suave, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Controles(if (maxHeight >= 110.dp) 44.dp else 36.dp)
            }
        } else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Estreito: a capa ocupa o que sobra depois do texto e dos controles (cerca de 100 dp).
            val lado = minOf(maxWidth, (maxHeight - 104.dp).coerceAtLeast(48.dp))
            Capa(Modifier.size(lado))
            Column(Modifier.fillMaxWidth().clickable { ctx.abrirSpotify() }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(titulo, color = texto, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(sub, color = suave, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            Controles(40.dp)
        }
    }
}

@Composable
private fun Botao(icone: ImageVector, principal: Boolean, tamanho: Dp, texto: Color, trilho: Color, azul: Color, aoClicar: () -> Unit) {
    val lado = if (principal) tamanho * 1.15f else tamanho
    Box(
        Modifier.size(lado).clip(CircleShape).background(if (principal) azul else trilho).clickable(onClick = aoClicar),
        contentAlignment = Alignment.Center,
    ) { Icon(icone, null, tint = if (principal) Color.White else texto, modifier = Modifier.size(lado * 0.55f)) }
}
