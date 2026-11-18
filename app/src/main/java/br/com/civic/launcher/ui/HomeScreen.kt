package br.com.civic.launcher.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Atualizador
import br.com.civic.launcher.EstadoAtualizacao
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.R
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.SituacaoVersao
import br.com.civic.launcher.abrirWazeComPainel
import br.com.civic.launcher.navegarWazeFavorito
import kotlinx.coroutines.delay
import java.util.Date

/** Estrutura fixa: barra do topo, conteúdo da tela escolhida e barra de navegação. */
@Composable
fun HomeScreen(
    monitor: MediaMonitor,
    atualizador: Atualizador,
    sensores: Sensores,
    modoTema: ModoTema,
    aoAlternarTema: () -> Unit,
) {
    var tela by remember { mutableStateOf(Tela.Inicio) }
    BackHandler(tela != Tela.Inicio) { tela = Tela.Inicio }

    Column(
        Modifier.fillMaxSize().background(Cores.Fundo).systemBarsPadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BarraTopo(sensores, atualizador, modoTema, aoAlternarTema)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tela) {
                Tela.Inicio -> TelaInicio(monitor, sensores)
                Tela.Central -> TelaCentral(sensores)
                Tela.Apps -> TelaApps()
            }
        }
        BarraNavegacao(tela, { tela = it }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun BarraTopo(sensores: Sensores, atualizador: Atualizador, modoTema: ModoTema, aoAlternarTema: () -> Unit) {
    val ctx = LocalContext.current
    var agora by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { agora = Date(); delay(1_000) } }
    val hora = DateFormat.getTimeFormat(ctx).format(agora)
    val data = DateFormat.format("EEE, d 'de' MMM", agora).toString()
    val rede = sensores.conectividade

    Row(
        Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(64.dp))
            Column {
                Text(
                    "CIVIC RENATIN", color = Cores.Texto, fontSize = 26.sp,
                    fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, letterSpacing = 2.sp,
                )
                when (val e = atualizador.estado) {
                    is EstadoAtualizacao.Pronta -> Text(
                        "Instalar ${e.versao}", color = Cores.Destaque, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { atualizador.instalar() },
                    )
                    is EstadoAtualizacao.Baixando -> Text("Baixando ${e.versao}…", color = Cores.TextoSuave, fontSize = 16.sp)
                    else -> {
                        val (situacao, cor) = when (atualizador.situacao) {
                            SituacaoVersao.Atualizado -> "✓ Atualizado" to Cores.Spotify
                            SituacaoVersao.Indisponivel -> "sem verificação" to Cores.TextoSuave
                            SituacaoVersao.Verificando -> "verificando…" to Cores.TextoSuave
                        }
                        // Tocar força uma nova verificação.
                        Text(
                            "Versão ${atualizador.versaoAtual} · $situacao", color = cor, fontSize = 16.sp,
                            modifier = Modifier.clickable { atualizador.verificar() },
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Fica cinza até o adaptador OBD ser conectado (fase seguinte).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Cores.TextoSuave))
                Text("OBD", color = Cores.TextoSuave, fontSize = 18.sp)
            }
            Icon(Icons.Filled.Bluetooth, null, tint = if (sensores.bluetoothLigado) Cores.Waze else Cores.TextoSuave, modifier = Modifier.size(30.dp))
            Icon(Icons.Filled.Wifi, null, tint = if (sensores.wifiLigado) Cores.Waze else Cores.TextoSuave, modifier = Modifier.size(30.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SignalCellularAlt, null, tint = Cores.Texto, modifier = Modifier.size(30.dp))
                if (rede.rede.isNotEmpty()) Text(rede.rede, color = Cores.Texto, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.toqueSuave(aoAlternarTema).padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    when (modoTema) {
                        ModoTema.Automatico -> Icons.Filled.BrightnessAuto
                        ModoTema.Claro -> Icons.Filled.LightMode
                        ModoTema.Escuro -> Icons.Filled.DarkMode
                    },
                    "Tema: ${modoTema.rotulo}", tint = Cores.Texto, modifier = Modifier.size(36.dp),
                )
                Text(modoTema.rotulo, color = Cores.TextoSuave, fontSize = 14.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(hora, color = Cores.Texto, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(data, color = Cores.TextoSuave, fontSize = 16.sp)
            }
        }
    }
}

@Composable
internal fun CartaoWaze(modifier: Modifier) {
    val ctx = LocalContext.current
    Column(
        modifier
            .toqueSuave { ctx.abrirWazeComPainel() }
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Cores.DegradeWaze, Cores.Cartao)))
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Cores.Waze.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Navigation, null, tint = Cores.Waze, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Waze", color = Cores.Texto, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("Navegação", color = Cores.TextoSuave, fontSize = 20.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Atalho("Casa", Icons.Filled.Home, Modifier.fillMaxWidth()) { ctx.navegarWazeFavorito("Home") }
            Atalho("Trabalho", Icons.Filled.Work, Modifier.fillMaxWidth()) { ctx.navegarWazeFavorito("Work") }
        }
    }
}

@Composable
internal fun Atalho(rotulo: String, icone: ImageVector, modifier: Modifier, aoTocar: () -> Unit) {
    // Alvo de toque grande (72dp de altura) para uso em direção.
    Row(
        modifier
            .toqueSuave(aoTocar)
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Cores.CartaoAlto)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icone, null, tint = Cores.Destaque, modifier = Modifier.size(34.dp))
        Text(rotulo, color = Cores.Texto, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}
