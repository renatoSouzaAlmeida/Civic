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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.abrirSpotify

@Composable
fun CartaoSpotify(monitor: MediaMonitor, modifier: Modifier) {
    val ctx = LocalContext.current
    val m = monitor.estado

    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Cores.Cartao)
            .clickable { ctx.abrirSpotify() }
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (m.capa != null) {
                Image(
                    m.capa.asImageBitmap(), null,
                    Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Filled.MusicNote, null, tint = Cores.Spotify, modifier = Modifier.size(56.dp))
            }
            Column {
                Text(
                    if (m.temSessao && m.titulo.isNotEmpty()) m.titulo else "Spotify",
                    color = Cores.Texto, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        !m.permitido -> "Controle desativado"
                        m.temSessao -> m.artista
                        else -> "Toque para abrir"
                    },
                    color = Cores.TextoSuave, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!m.permitido) {
            Atalho("Ativar controle", Icons.Filled.Settings, Modifier.fillMaxWidth()) {
                val abriu = runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
                if (!abriu) Toast.makeText(ctx, "Ative o acesso às notificações do Civic Launcher nas Configurações", Toast.LENGTH_LONG).show()
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Controle(Icons.Filled.SkipPrevious, 56.dp, Cores.TextoSuave) { monitor.anterior() }
                Controle(
                    if (m.tocando) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                    80.dp, Cores.Spotify,
                ) { if (m.temSessao) monitor.alternar() else ctx.abrirSpotify() }
                Controle(Icons.Filled.SkipNext, 56.dp, Cores.TextoSuave) { monitor.proxima() }
            }
        }
    }
}

@Composable
private fun Controle(icone: androidx.compose.ui.graphics.vector.ImageVector, tamanho: androidx.compose.ui.unit.Dp, cor: androidx.compose.ui.graphics.Color, aoTocar: () -> Unit) {
    Icon(
        icone, null, tint = cor,
        modifier = Modifier.size(tamanho).clip(RoundedCornerShape(50)).clickable(onClick = aoTocar),
    )
}
