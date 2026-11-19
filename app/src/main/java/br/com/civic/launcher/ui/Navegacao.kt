package br.com.civic.launcher.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

enum class Tela { Inicio, Central, Veiculo, Apps, Config }

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
    Item("OBD", Icons.Filled.DirectionsCar, Tela.Central),
    Item("Telefone", Icons.Filled.Phone, null) { c ->
        abrirFixado(c, Atalhos.TELEFONE, "Telefone") {
            c.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    },
    Item("Spotify", Icons.Filled.MusicNote, null, R.drawable.ic_spotify) { it.abrirSpotify() },
    Item("Veículo", Icons.Filled.LocalGasStation, Tela.Veiculo),
    Item("Apps", Icons.Filled.Apps, Tela.Apps),
    Item("Config.", Icons.Filled.Settings, Tela.Config),
)

/** Altura do dock flutuante (estilo D); o conteúdo das telas para acima dele. */
val ALTURA_DOCK = 64.dp

@Composable
private fun IconeItem(item: Item, cor: Color, tamanho: androidx.compose.ui.unit.Dp) {
    if (item.iconeRes != null) Icon(painterResource(item.iconeRes), item.rotulo, tint = cor, modifier = Modifier.size(tamanho * 0.9f))
    else Icon(item.icone, item.rotulo, tint = cor, modifier = Modifier.size(tamanho))
}

private val DestaqueMenu = Brush.verticalGradient(listOf(Color(0xFF4D9BFF), Color(0xFF1F66E0)))

/** Menu B — barra fina (≈ 52 dp) embaixo; só o item ativo mostra o rótulo. */
@Composable
fun BarraNavegacao(atual: Tela, aoIr: (Tela) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val formaBarra = RoundedCornerShape(18.dp)
    val formaItem = RoundedCornerShape(14.dp)
    Row(
        modifier.height(52.dp).clip(formaBarra).background(Cores.Cartao).border(1.dp, Cores.Borda, formaBarra).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itens.forEach { item ->
            val ativo = item.tela != null && item.tela == atual
            Row(
                Modifier
                    .toqueSuave { if (item.tela != null) aoIr(item.tela) else item.acao(ctx) }
                    .clip(formaItem)
                    .then(if (ativo) Modifier.background(DestaqueMenu) else Modifier)
                    .padding(horizontal = if (ativo) 16.dp else 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconeItem(item, if (ativo) Color.White else Cores.TextoSuave, 26.dp)
                if (ativo) Text(item.rotulo, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/** Menu D — dock flutuante em cápsula, centralizado sobre o conteúdo, só com ícones. */
@Composable
fun DockNavegacao(atual: Tela, aoIr: (Tela) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val forma = RoundedCornerShape(50)
    Row(
        modifier.height(ALTURA_DOCK).clip(forma).background(Cores.CartaoSolido.copy(alpha = 0.94f))
            .border(1.dp, Cores.Destaque.copy(alpha = 0.45f), forma).padding(horizontal = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itens.forEach { item ->
            val ativo = item.tela != null && item.tela == atual
            Box(
                Modifier.size(50.dp)
                    .toqueSuave { if (item.tela != null) aoIr(item.tela) else item.acao(ctx) }
                    .clip(CircleShape)
                    .then(if (ativo) Modifier.background(DestaqueMenu) else Modifier),
                contentAlignment = Alignment.Center,
            ) { IconeItem(item, if (ativo) Color.White else Cores.TextoSuave, 26.dp) }
        }
    }
}
