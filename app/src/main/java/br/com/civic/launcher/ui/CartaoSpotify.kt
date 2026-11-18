package br.com.civic.launcher.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.MusicaUi
import br.com.civic.launcher.abrirSpotify

@Composable
fun CartaoSpotify(monitor: MediaMonitor, modifier: Modifier) {
    val ctx = LocalContext.current
    val m = monitor.estado

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (m.temSessao) Brush.verticalGradient(listOf(Cores.DegradeSpotify, Cores.Cartao))
                else Brush.verticalGradient(listOf(Cores.Cartao, Cores.Cartao))
            )
            .clickable { ctx.abrirSpotify() }
    ) {
    val compacto = maxHeight < 360.dp
    Column(
        Modifier.fillMaxSize().padding(if (compacto) 16.dp else 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        val abrirAcesso = {
            val abriu = runCatching {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (!abriu) Toast.makeText(ctx, "Ative o acesso às notificações do Civic Renatin nas Configurações", Toast.LENGTH_LONG).show()
        }
        // Capa, título e progresso (cedem espaço); os controles abaixo nunca são espremidos.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
        Row(
            Modifier.clickable { if (m.permitido) ctx.abrirSpotify() else abrirAcesso() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (m.capa != null) {
                Image(
                    m.capa.asImageBitmap(), null,
                    Modifier.size(if (compacto) 80.dp else 112.dp).clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Filled.MusicNote, null, tint = Cores.Spotify, modifier = Modifier.size(56.dp))
            }
            Column {
                Text(
                    if (m.temSessao && m.titulo.isNotEmpty()) m.titulo else "Spotify",
                    color = Cores.Texto, fontSize = if (compacto) 24.sp else 28.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        !m.permitido -> "Toque aqui para ativar capa e título"
                        m.temSessao -> m.artista
                        else -> "Toque para abrir"
                    },
                    color = Cores.TextoSuave, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (m.temSessao && m.duracao > 0) BarraProgresso(m)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Controle(Icons.Filled.SkipPrevious, if (compacto) 48.dp else 56.dp, Cores.TextoSuave) { monitor.anterior() }
            Controle(
                if (m.tocando) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                if (compacto) 64.dp else 80.dp, Cores.Spotify,
            ) { if (m.temSessao || !m.permitido) monitor.alternar() else ctx.abrirSpotify() }
            Controle(Icons.Filled.SkipNext, if (compacto) 48.dp else 56.dp, Cores.TextoSuave) { monitor.proxima() }
        }
    }
    }
}

@Composable
internal fun Controle(icone: androidx.compose.ui.graphics.vector.ImageVector, tamanho: androidx.compose.ui.unit.Dp, cor: androidx.compose.ui.graphics.Color, aoTocar: () -> Unit) {
    Icon(
        icone, null, tint = cor,
        modifier = Modifier.toqueSuave(aoTocar).size(tamanho).clip(RoundedCornerShape(50)),
    )
}

@Composable
internal fun BarraProgresso(m: MusicaUi, mostrarTempos: Boolean = true) {
    var agora by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(m.tocando, m.posicao, m.atualizadoEm) {
        agora = SystemClock.elapsedRealtime()
        while (m.tocando) {
            delay(1_000)
            agora = SystemClock.elapsedRealtime()
        }
    }
    val posicao = if (m.tocando) m.posicao + ((agora - m.atualizadoEm) * m.velocidade).toLong() else m.posicao
    val fracao = (posicao.toFloat() / m.duracao).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Cores.CartaoAlto)
        ) {
            Box(Modifier.fillMaxWidth(fracao).fillMaxHeight().background(Cores.Spotify))
        }
        if (mostrarTempos) Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatarTempo(posicao), color = Cores.TextoSuave, fontSize = 16.sp)
            Text(formatarTempo(m.duracao), color = Cores.TextoSuave, fontSize = 16.sp)
        }
    }
}

private fun formatarTempo(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
