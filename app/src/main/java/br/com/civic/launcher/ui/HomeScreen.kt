package br.com.civic.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.abrirWaze
import br.com.civic.launcher.navegarWazeFavorito
import kotlinx.coroutines.delay
import java.util.Date

/**
 * Layout adaptativo: largura >= altura (paisagem, multimídia) = mapa à esquerda
 * e painel à direita; caso contrário (retrato, futura virada de tela) o painel
 * vai para baixo. Nenhum tamanho é fixo, só proporções.
 */
@Composable
fun HomeScreen(monitor: MediaMonitor) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Cores.Fundo)
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        if (maxWidth >= maxHeight) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CartaoWaze(Modifier.weight(0.62f).fillMaxHeight())
                PainelLateral(monitor, Modifier.weight(0.38f).fillMaxHeight())
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CartaoWaze(Modifier.weight(0.58f).fillMaxWidth())
                PainelLateral(monitor, Modifier.weight(0.42f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CartaoWaze(modifier: Modifier) {
    val ctx = LocalContext.current
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF12202B), Cores.Cartao)))
            .clickable { ctx.abrirWaze() }
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Icon(Icons.Filled.Navigation, null, tint = Cores.Waze, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(8.dp))
            Text("Waze", color = Cores.Texto, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("Toque para abrir o mapa", color = Cores.TextoSuave, fontSize = 18.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Atalho("Casa", Icons.Filled.Home, Modifier.weight(1f)) { ctx.navegarWazeFavorito("Home") }
            Atalho("Trabalho", Icons.Filled.Work, Modifier.weight(1f)) { ctx.navegarWazeFavorito("Work") }
        }
    }
}

@Composable
private fun PainelLateral(monitor: MediaMonitor, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Relogio(Modifier.fillMaxWidth())
        CartaoSpotify(monitor, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun Relogio(modifier: Modifier) {
    val ctx = LocalContext.current
    var agora by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { agora = Date(); delay(1_000) } }
    val hora = DateFormat.getTimeFormat(ctx).format(agora)
    val data = DateFormat.format("EEEE, d 'de' MMM", agora).toString()
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Cores.Cartao)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(hora, color = Cores.Texto, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        Text(data, color = Cores.TextoSuave, fontSize = 20.sp)
    }
}

@Composable
internal fun Atalho(rotulo: String, icone: ImageVector, modifier: Modifier, aoTocar: () -> Unit) {
    // Alvo de toque grande (80dp de altura) para uso em direção.
    Row(
        modifier
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Cores.CartaoAlto)
            .clickable(onClick = aoTocar)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icone, null, tint = Cores.Destaque, modifier = Modifier.size(36.dp))
        Text(rotulo, color = Cores.Texto, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}
