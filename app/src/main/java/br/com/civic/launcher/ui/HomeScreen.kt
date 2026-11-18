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
import br.com.civic.launcher.obd.EstadoObd
import br.com.civic.launcher.obd.ObdMonitor
import kotlinx.coroutines.delay
import java.util.Date

/** Estrutura fixa: barra do topo, conteúdo da tela escolhida e barra de navegação. */
@Composable
fun HomeScreen(
    monitor: MediaMonitor,
    atualizador: Atualizador,
    sensores: Sensores,
    obd: ObdMonitor,
    modoTema: ModoTema,
    aoAlternarTema: () -> Unit,
) {
    var tela by remember { mutableStateOf(Tela.Inicio) }
    BackHandler(tela != Tela.Inicio) { tela = Tela.Inicio }

    Column(
        Modifier.fillMaxSize().background(Cores.Fundo).systemBarsPadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BarraTopo(sensores, atualizador, obd, modoTema, aoAlternarTema)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tela) {
                Tela.Inicio -> TelaInicio(monitor, sensores, obd)
                Tela.Central -> TelaCentral(sensores, obd)
                Tela.Apps -> TelaApps()
            }
        }
        BarraNavegacao(tela, { tela = it }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun BarraTopo(sensores: Sensores, atualizador: Atualizador, obd: ObdMonitor, modoTema: ModoTema, aoAlternarTema: () -> Unit) {
    val ctx = LocalContext.current
    var agora by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { agora = Date(); delay(1_000) } }
    val hora = DateFormat.getTimeFormat(ctx).format(agora)
    val data = DateFormat.format("EEE, d 'de' MMM", agora).toString()
    val rede = sensores.conectividade

    BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp)) {
    // Em tela estreita (celular em retrato) esconde o que não cabe, mas mantém o botão de tema.
    val compacto = maxWidth < 760.dp
    Row(
        Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compacto) 8.dp else 12.dp)) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(if (compacto) 48.dp else 64.dp))
            Column {
                Text(
                    "CIVIC RENATIN", color = Cores.Texto, fontSize = if (compacto) 18.sp else 26.sp,
                    fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, letterSpacing = if (compacto) 1.sp else 2.sp,
                )
                when (val e = atualizador.estado) {
                    is EstadoAtualizacao.Pronta -> Text(
                        "Instalar ${e.versao}", color = Cores.Destaque, fontSize = if (compacto) 14.sp else 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { atualizador.instalar() },
                    )
                    is EstadoAtualizacao.Baixando -> Text("Baixando ${e.versao}…", color = Cores.TextoSuave, fontSize = if (compacto) 12.sp else 16.sp)
                    else -> {
                        val (situacao, cor) = when (atualizador.situacao) {
                            SituacaoVersao.Atualizado -> "✓ Atualizado" to Cores.Spotify
                            SituacaoVersao.Indisponivel -> "sem verificação" to Cores.TextoSuave
                            SituacaoVersao.Verificando -> "verificando…" to Cores.TextoSuave
                        }
                        // Tocar força uma nova verificação.
                        Text(
                            "Versão ${atualizador.versaoAtual} · $situacao", color = cor, fontSize = if (compacto) 12.sp else 16.sp,
                            modifier = Modifier.clickable { atualizador.verificar() },
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compacto) 10.dp else 20.dp)) {
            // Verde = lendo o carro; azul = demonstração; amarelo = conectando; cinza = sem adaptador.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val corObd = when (obd.estado) {
                    EstadoObd.Conectado -> Cores.Spotify
                    EstadoObd.Demonstracao -> Cores.Waze
                    EstadoObd.Conectando -> Cores.Amarelo
                    EstadoObd.Procurando, EstadoObd.Desconectado -> Cores.TextoSuave
                }
                Box(Modifier.size(10.dp).clip(CircleShape).background(corObd))
                if (!compacto) Text(if (obd.estado == EstadoObd.Demonstracao) "OBD demo" else "OBD", color = Cores.TextoSuave, fontSize = 18.sp)
            }
            if (!compacto) Icon(Icons.Filled.Bluetooth, null, tint = if (sensores.bluetoothLigado) Cores.Waze else Cores.TextoSuave, modifier = Modifier.size(30.dp))
            if (!compacto) Icon(Icons.Filled.Wifi, null, tint = if (sensores.wifiLigado) Cores.Waze else Cores.TextoSuave, modifier = Modifier.size(30.dp))
            if (!compacto) Row(verticalAlignment = Alignment.CenterVertically) {
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
                Text(hora, color = Cores.Texto, fontSize = if (compacto) 22.sp else 32.sp, fontWeight = FontWeight.Bold)
                if (!compacto) Text(data, color = Cores.TextoSuave, fontSize = 16.sp)
            }
        }
    }
    }
}

@Composable
internal fun CartaoWaze(modifier: Modifier) {
    val ctx = LocalContext.current
    BoxWithConstraints(
        modifier
            .toqueSuave { ctx.abrirWazeComPainel() }
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Cores.DegradeWaze, Cores.Cartao)))
    ) {
        val compacto = maxHeight < 330.dp
        Column(
            Modifier.fillMaxSize().padding(if (compacto) 14.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // O cabeçalho é o que encolhe/corta; os atalhos (embaixo) sempre aparecem inteiros.
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(if (compacto) 52.dp else 72.dp).clip(RoundedCornerShape(16.dp)).background(Cores.Waze.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Navigation, null, tint = Cores.Waze, modifier = Modifier.size(if (compacto) 32.dp else 44.dp))
                    }
                    if (compacto) Text("Waze", color = Cores.Texto, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                if (!compacto) {
                    Spacer(Modifier.height(12.dp))
                    Text("Waze", color = Cores.Texto, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text("Navegação", color = Cores.TextoSuave, fontSize = 20.sp)
                }
            }
            if (compacto) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Atalho("Casa", Icons.Filled.Home, Modifier.weight(1f), compacto = true) { ctx.navegarWazeFavorito("Home") }
                    Atalho("Trabalho", Icons.Filled.Work, Modifier.weight(1f), compacto = true) { ctx.navegarWazeFavorito("Work") }
                }
            } else {
                Atalho("Casa", Icons.Filled.Home, Modifier.fillMaxWidth()) { ctx.navegarWazeFavorito("Home") }
                Atalho("Trabalho", Icons.Filled.Work, Modifier.fillMaxWidth()) { ctx.navegarWazeFavorito("Work") }
            }
        }
    }
}

@Composable
internal fun Atalho(rotulo: String, icone: ImageVector, modifier: Modifier, compacto: Boolean = false, aoTocar: () -> Unit) {
    // Alvo de toque grande (72dp de altura) para uso em direção.
    Row(
        modifier
            .toqueSuave(aoTocar)
            .height(if (compacto) 60.dp else 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Cores.CartaoAlto)
            .padding(horizontal = if (compacto) 10.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compacto) 6.dp else 12.dp),
    ) {
        Icon(icone, null, tint = Cores.Destaque, modifier = Modifier.size(if (compacto) 26.dp else 34.dp))
        Text(rotulo, color = Cores.Texto, fontSize = if (compacto) 18.sp else 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
