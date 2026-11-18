package br.com.civic.launcher.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Atalhos
import br.com.civic.launcher.abrir
import br.com.civic.launcher.abrirSpotify

enum class Tela { Inicio, Central, Apps }

private class Item(val rotulo: String, val icone: ImageVector, val tela: Tela?, val acao: (Context) -> Unit = {})

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
    Item("Telefone", Icons.Filled.Phone, null) { c ->
        abrirFixado(c, Atalhos.TELEFONE, "Telefone") {
            c.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    },
    Item("Música", Icons.Filled.MusicNote, null) { it.abrirSpotify() },
    Item("Rádio", Icons.Filled.Radio, null) { abrirFixado(it, Atalhos.RADIO, "Rádio") },
    Item("YouTube", Icons.Filled.OndemandVideo, null) { it.abrir("com.google.android.youtube") },
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
    Row(
        modifier.clip(RoundedCornerShape(24.dp)).background(Cores.Cartao).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itens.forEach { item ->
            val ativo = item.tela != null && item.tela == atual
            Column(
                Modifier
                    .weight(1f)
                    .toqueSuave { if (item.tela != null) aoIr(item.tela) else item.acao(ctx) }
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (ativo) Cores.Destaque.copy(alpha = 0.22f) else Cores.CartaoAlto.copy(alpha = 0.5f))
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(item.icone, null, tint = if (ativo) Cores.Destaque else Cores.Texto, modifier = Modifier.size(34.dp))
                Text(item.rotulo, color = if (ativo) Cores.Destaque else Cores.TextoSuave, fontSize = 16.sp, maxLines = 1)
            }
        }
    }
}
