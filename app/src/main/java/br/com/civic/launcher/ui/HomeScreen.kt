package br.com.civic.launcher.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Atualizador
import br.com.civic.launcher.EstadoAtualizacao
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.R
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.SituacaoVersao
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
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Cores.Fundo, Cores.FundoB)))
            .systemBarsPadding()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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

/** Hora e data à esquerda, nome do app ao centro e situação (versão, OBD, conexões, tema) à direita. */
@Composable
private fun BarraTopo(sensores: Sensores, atualizador: Atualizador, obd: ObdMonitor, modoTema: ModoTema, aoAlternarTema: () -> Unit) {
    val ctx = LocalContext.current
    var agora by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { agora = Date(); delay(1_000) } }
    val hora = DateFormat.getTimeFormat(ctx).format(agora)
    val data = DateFormat.format("EEEE, d 'de' MMMM", agora).toString()

    BoxWithConstraints(Modifier.fillMaxWidth().height(58.dp)) {
        // Em tela estreita (celular em retrato) esconde o que não cabe, mas mantém o botão de tema.
        val compacto = maxWidth < 760.dp
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(hora, color = Cores.Texto, fontSize = if (compacto) 30.sp else 42.sp, fontWeight = FontWeight.ExtraBold)
                if (!compacto) Text(data, color = Cores.TextoSuave, fontSize = 16.sp, modifier = Modifier.padding(bottom = 7.dp), maxLines = 1)
            }
            if (!compacto) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(46.dp))
                    Text("CIVIC RENATIN", color = Cores.Texto, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                }
            }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(if (compacto) 10.dp else 18.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Versao(atualizador, compacto)
                // Verde = lendo o carro; azul = demonstração; amarelo = conectando; cinza = sem adaptador.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val corObd = when (obd.estado) {
                        EstadoObd.Conectado -> Cores.Ok
                        EstadoObd.Demonstracao -> Cores.Waze
                        EstadoObd.Conectando -> Cores.Amarelo
                        EstadoObd.Procurando, EstadoObd.Desconectado -> Cores.TextoSuave
                    }
                    Box(Modifier.size(10.dp).clip(CircleShape).background(corObd))
                    if (!compacto) Text(if (obd.estado == EstadoObd.Demonstracao) "OBD demo" else "OBD", color = Cores.TextoSuave, fontSize = 16.sp)
                }
                if (!compacto) {
                    Icon(Icons.Filled.Bluetooth, null, tint = if (sensores.bluetoothLigado) Cores.Destaque else Cores.TextoSuave, modifier = Modifier.size(26.dp))
                    Icon(Icons.Filled.Wifi, null, tint = if (sensores.wifiLigado) Cores.Destaque else Cores.TextoSuave, modifier = Modifier.size(26.dp))
                }
                Column(Modifier.toqueSuave(aoAlternarTema).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        when (modoTema) {
                            ModoTema.Automatico -> Icons.Filled.BrightnessAuto
                            ModoTema.Claro -> Icons.Filled.LightMode
                            ModoTema.Escuro -> Icons.Filled.DarkMode
                        },
                        "Tema: ${modoTema.rotulo}", tint = Cores.Texto, modifier = Modifier.size(28.dp),
                    )
                    Text(modoTema.rotulo, color = Cores.TextoSuave, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun Versao(atualizador: Atualizador, compacto: Boolean) {
    val tamanho = if (compacto) 11.sp else 14.sp
    when (val e = atualizador.estado) {
        is EstadoAtualizacao.Pronta -> Text(
            "Instalar ${e.versao}", color = Cores.Destaque, fontSize = if (compacto) 13.sp else 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { atualizador.instalar() },
        )
        is EstadoAtualizacao.Baixando -> Text("Baixando ${e.versao}…", color = Cores.TextoSuave, fontSize = tamanho)
        else -> {
            val (situacao, cor) = when (atualizador.situacao) {
                SituacaoVersao.Atualizado -> "✓ Atualizado" to Cores.Ok
                SituacaoVersao.Indisponivel -> "sem verificação" to Cores.TextoSuave
                SituacaoVersao.Verificando -> "verificando…" to Cores.TextoSuave
            }
            // Tocar força uma nova verificação.
            Text(
                "v${atualizador.versaoAtual} · $situacao", color = cor, fontSize = tamanho, maxLines = 1,
                modifier = Modifier.clickable { atualizador.verificar() },
            )
        }
    }
}
