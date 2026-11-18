package br.com.civic.launcher.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Atalhos
import br.com.civic.launcher.abrir
import br.com.civic.launcher.abrirSpotify
import br.com.civic.launcher.abrirWazeComPainel

enum class Tela { Inicio, Central, Apps }

private class Item(
    val rotulo: String,
    val icone: ImageVector,
    val tela: Tela?,
    /** Se informado e o app estiver instalado, usa o ícone real dele no lugar de `icone`. */
    val pacoteIcone: String? = null,
    val acao: (Context) -> Unit = {},
)

private fun abrirFixado(ctx: Context, chave: String, nome: String, reserva: (() -> Unit)? = null) {
    val pacote = Atalhos.obter(ctx, chave)
    when {
        pacote != null -> ctx.abrir(pacote)
        reserva != null -> reserva()
        else -> Toast.makeText(ctx, "Em Apps, segure o app de $nome e fixe", Toast.LENGTH_LONG).show()
    }
}

private val itens = listOf(
    Item("Início", Icons.Filled.Home, Tela.Inicio),
    Item("Direção", Icons.Filled.Navigation, null) { it.abrirWazeComPainel() },
    Item("Telefone", Icons.Filled.Phone, null) { c ->
        abrirFixado(c, Atalhos.TELEFONE, "Telefone") {
            c.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    },
    Item("Spotify", Icons.Filled.MusicNote, null, "com.spotify.music") { it.abrirSpotify() },
    Item("Câmera", Icons.Filled.Videocam, null) { abrirFixado(it, Atalhos.CAMERA, "Câmera") },
    Item("OBD", Icons.Filled.DirectionsCar, Tela.Central),
    Item("Apps", Icons.Filled.Apps, Tela.Apps),
    Item("Config.", Icons.Filled.Settings, null) {
        it.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    },
)

@Composable
fun BarraNavegacao(atual: Tela, aoIr: (Tela) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    BoxWithConstraints(modifier) {
    val compacto = maxWidth < 600.dp // celular em retrato: só os ícones, sem rótulos
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Cores.Cartao).padding(if (compacto) 6.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compacto) 4.dp else 8.dp),
    ) {
        itens.forEach { item ->
            val ativo = item.tela != null && item.tela == atual
            Column(
                Modifier
                    .weight(1f)
                    .toqueSuave { if (item.tela != null) aoIr(item.tela) else item.acao(ctx) }
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (ativo) Cores.Destaque.copy(alpha = 0.22f) else Cores.CartaoAlto.copy(alpha = 0.5f))
                    .padding(vertical = if (compacto) 12.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val iconeApp = remember(item.pacoteIcone) {
                    item.pacoteIcone?.let { pacote ->
                        runCatching { ctx.packageManager.getApplicationIcon(pacote).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                    }
                }
                if (iconeApp != null) {
                    Image(iconeApp, item.rotulo, Modifier.size(if (compacto) 28.dp else 36.dp))
                } else {
                    Icon(item.icone, item.rotulo, tint = if (ativo) Cores.Destaque else Cores.Texto, modifier = Modifier.size(if (compacto) 26.dp else 34.dp))
                }
                if (!compacto) Text(item.rotulo, color = if (ativo) Cores.Destaque else Cores.TextoSuave, fontSize = 16.sp, maxLines = 1)
            }
        }
    }
    }
}
