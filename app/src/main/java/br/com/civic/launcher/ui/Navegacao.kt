package br.com.civic.launcher.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.painterResource
import br.com.civic.launcher.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Atalhos
import br.com.civic.launcher.abrir
import br.com.civic.launcher.abrirSpotify
import br.com.civic.launcher.abrirNavegacaoComPainel

enum class Tela { Inicio, Central, Apps, Config }

private class Item(
    val rotulo: String,
    val icone: ImageVector,
    val tela: Tela?,
    /** Ícone próprio (vetor monocromático em res/drawable), pintado com a mesma cor dos demais. */
    val iconeRes: Int? = null,
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
    Item("Direção", Icons.Filled.Navigation, null) { it.abrirNavegacaoComPainel() },
    Item("Telefone", Icons.Filled.Phone, null) { c ->
        abrirFixado(c, Atalhos.TELEFONE, "Telefone") {
            c.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    },
    Item("Spotify", Icons.Filled.MusicNote, null, R.drawable.ic_spotify) { it.abrirSpotify() },
    Item("OBD", Icons.Filled.DirectionsCar, Tela.Central),
    Item("Apps", Icons.Filled.Apps, Tela.Apps),
    Item("Config.", Icons.Filled.Settings, Tela.Config),
)

@Composable
fun BarraNavegacao(atual: Tela, aoIr: (Tela) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val formaBarra = RoundedCornerShape(26.dp)
    val formaItem = RoundedCornerShape(20.dp)
    val destaque = Brush.verticalGradient(listOf(Color(0xFF4D9BFF), Color(0xFF1F66E0)))
    BoxWithConstraints(modifier) {
        val compacto = maxWidth < 600.dp // celular em retrato: só os ícones, sem rótulos
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compacto) 6.dp else 12.dp),
        ) {
            itens.forEach { item ->
                val ativo = item.tela != null && item.tela == atual
                val corItem = if (ativo) Color.White else Cores.TextoSuave
                Column(
                    Modifier
                        .weight(1f)
                        .toqueSuave { if (item.tela != null) aoIr(item.tela) else item.acao(ctx) }
                        .clip(formaItem)
                        .then(if (ativo) Modifier.background(destaque).border(1.dp, Color(0xE6AAD2FF), formaItem) else Modifier.background(Cores.Cartao).border(1.dp, Cores.Borda, formaItem))
                        .padding(vertical = if (compacto) 12.dp else 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val tamanho = if (compacto) 26.dp else 32.dp
                    if (item.iconeRes != null) {
                        Icon(painterResource(item.iconeRes), item.rotulo, tint = corItem, modifier = Modifier.size(tamanho * 0.9f))
                    } else {
                        Icon(item.icone, item.rotulo, tint = corItem, modifier = Modifier.size(tamanho))
                    }
                    if (!compacto) Text(item.rotulo, color = corItem, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}
