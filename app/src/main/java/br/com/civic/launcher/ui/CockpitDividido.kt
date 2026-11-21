package br.com.civic.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Direcao
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.Veiculo
import br.com.civic.launcher.obd.ObdMonitor
import java.util.Locale

private val BRC = Locale("pt", "BR")

/**
 * Cockpit do modo Direção em tela dividida (o mapa fica no outro lado). Feito para caber em meia tela: velocímetro com setas, farol e RPM
 * (o mesmo do OBD Modular), quatro leituras grandes e o Spotify. Os alertas do motor aparecem por cima, como em qualquer tela.
 */
@Composable
fun CockpitDividido(obd: ObdMonitor, monitor: MediaMonitor, sensores: Sensores) {
    val ctx = LocalContext.current
    val d = obd.dados
    val vel = d.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
    val consumo = d.consumoInstantaneo ?: d.consumoMedio ?: remember { Veiculo.obter(ctx).consumoMedio() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Modo direção", color = Cores.TextoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            BotaoPilula("Fechar", Icons.Filled.Close) { Direcao.fechar() }
        }
        ModuloVelocidade(d, vel, Modifier.weight(1.7f).fillMaxWidth())
        Row(Modifier.fillMaxWidth().weight(0.45f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Leitura("Motor", d.tempMotor?.let { "$it" } ?: "—", "°C", Modifier.weight(1f))
            Leitura("Bateria", d.tensao?.let { "%.1f".format(BRC, it) } ?: "—", "V", Modifier.weight(1f))
            Leitura("Tanque", d.nivelCombustivel?.let { "$it" } ?: "—", "%", Modifier.weight(1f))
            Leitura("Consumo", consumo?.let { "%.1f".format(BRC, it) } ?: "—", "km/l", Modifier.weight(1f))
        }
        MiniMusica(monitor, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Leitura(rotulo: String, valor: String, unidade: String, modifier: Modifier) {
    Column(modifier.fillMaxHeight().cartaoSuave().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
        Text(rotulo, color = Cores.TextoSuave, fontSize = 13.sp, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(valor, color = Cores.Texto, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(unidade, color = Cores.TextoSuave, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}
