package br.com.civic.launcher.ui

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.MusicaUi
import br.com.civic.launcher.abrirSpotify
import kotlinx.coroutines.delay


@Composable
fun CartaoSpotify(monitor: MediaMonitor, modifier: Modifier) {
    val ctx = LocalContext.current
    val m = monitor.estado

    BoxWithConstraints(
        // Mesma cor de fundo do cartão do veículo, com um degradê azul bem claro por cima.
        modifier.clip(FormaCartao).background(Cores.Cartao).border(1.dp, Cores.Borda, FormaCartao).clickable { ctx.abrirSpotify() }
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Cores.Destaque.copy(alpha = 0.34f), Cores.Destaque.copy(alpha = 0.08f), Cores.Destaque.copy(alpha = 0.18f)))))
        val compacto = maxHeight < 300.dp
        val abrirAcesso = {
            val abriu = runCatching {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (!abriu) Toast.makeText(ctx, "Ative o acesso às notificações do Civic Renatin nas Configurações", Toast.LENGTH_LONG).show()
        }
        Column(
            Modifier.fillMaxSize().padding(if (compacto) 14.dp else 22.dp).escalarSeMenorQue(210.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Capa, título e progresso cedem espaço; os controles abaixo nunca são espremidos.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
                Row(
                    Modifier.clickable { if (m.permitido) ctx.abrirSpotify() else abrirAcesso() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Capa(m, if (compacto) 76.dp else 108.dp)
                    Column {
                        Text("Spotify", color = Cores.TextoSuave, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (m.temSessao && m.titulo.isNotEmpty()) m.titulo else "Nada tocando",
                            color = Cores.Texto, fontSize = if (compacto) 22.sp else 28.sp, fontWeight = FontWeight.ExtraBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                !m.permitido -> "Toque aqui para ativar capa e título"
                                m.temSessao -> m.artista
                                else -> "Toque para abrir"
                            },
                            color = Cores.TextoSuave, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (m.temSessao && m.duracao > 0) {
                    BarraProgresso(m, mostrarTempos = false, trilho = Cores.Chip, progresso = Cores.Destaque)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compacto) 22.dp else 32.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotaoRedondo(Icons.Filled.SkipPrevious, if (compacto) 48.dp else 60.dp, Cores.Chip, Cores.Texto) { monitor.anterior() }
                BotaoRedondo(
                    if (m.tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (compacto) 60.dp else 76.dp, Cores.Destaque, Color.White,
                ) { if (m.temSessao || !m.permitido) monitor.alternar() else ctx.abrirSpotify() }
                BotaoRedondo(Icons.Filled.SkipNext, if (compacto) 48.dp else 60.dp, Cores.Chip, Cores.Texto) { monitor.proxima() }
            }
        }
    }
}

@Composable
private fun Capa(m: MusicaUi, tamanho: Dp) {
    val forma = RoundedCornerShape(22.dp)
    if (m.capa != null) {
        Image(
            m.capa.asImageBitmap(), null,
            Modifier.size(tamanho).clip(forma).border(2.dp, Cores.Borda, forma),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.size(tamanho).clip(forma).background(Brush.linearGradient(listOf(Color(0xFF7DB4FF), Color(0xFF3D7BFF), Color(0xFF1E3FA0))))
                .border(2.dp, Color(0x59FFFFFF), forma),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(tamanho * 0.4f)) }
    }
}

@Composable
private fun BotaoRedondo(icone: ImageVector, tamanho: Dp, fundo: Color, cor: Color, aoTocar: () -> Unit) {
    Box(
        Modifier.toqueSuave(aoTocar).size(tamanho).clip(CircleShape).background(fundo),
        contentAlignment = Alignment.Center,
    ) { Icon(icone, null, tint = cor, modifier = Modifier.size(tamanho * 0.52f)) }
}

/** Usado pelo painel de direção (sobre o Waze). */
@Composable
internal fun Controle(icone: ImageVector, tamanho: Dp, cor: Color, aoTocar: () -> Unit) {
    Icon(
        icone, null, tint = cor,
        modifier = Modifier.toqueSuave(aoTocar).size(tamanho).clip(RoundedCornerShape(50)),
    )
}

@Composable
internal fun BarraProgresso(
    m: MusicaUi,
    mostrarTempos: Boolean = true,
    trilho: Color = Cores.CartaoAlto,
    progresso: Color = Cores.Spotify,
    corTexto: Color = Cores.TextoSuave,
) {
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
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(trilho)) {
            Box(Modifier.fillMaxWidth(fracao).fillMaxHeight().background(progresso))
        }
        if (mostrarTempos) Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatarTempo(posicao), color = corTexto, fontSize = 16.sp)
            Text(formatarTempo(m.duracao), color = corTexto, fontSize = 16.sp)
        }
    }
}

private fun formatarTempo(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
