package br.com.civic.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Direcao
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.Veiculo
import br.com.civic.launcher.obd.DadosObd
import br.com.civic.launcher.obd.ObdMonitor
import java.util.Locale

private val BRC = Locale("pt", "BR")

/**
 * Velocímetro para meia tela: o mostrador ocupa quase todo o cartão e o número da velocidade é grande;
 * o giro (RPM) é só uma faixa fina embaixo. Setas e farol ficam em cima.
 */
@Composable
internal fun VelocimetroDividido(d: DadosObd, vel: Float, modifier: Modifier = Modifier) {
    Column(modifier.cartaoSuave(RoundedCornerShape(26.dp)).padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            SetaObd(d, esquerda = true, tamanho = 30.dp)
            FarolObd(d, 22.dp)
            SetaObd(d, esquerda = false, tamanho = 30.dp)
        }
        DialPremium(vel / 200f, (0..10).map { (it * 20).toString() }, Modifier.weight(1f).fillMaxWidth(), menores = 1) { lado ->
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${vel.toInt()}", color = Cores.Texto, fontSize = (lado.value * 0.30f).sp, lineHeight = (lado.value * 0.30f).sp,
                    fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, maxLines = 1, softWrap = false, style = SombraNumero)
                Text("km/h", color = Cores.TextoSuave, fontSize = (lado.value * 0.07f).sp)
            }
        }
        // RPM pequeno: rótulo, faixa fina em degraus e o valor.
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("RPM", color = Cores.TextoSuave, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            BarraSegmentos((d.rpm ?: 0) / 8000f, Modifier.weight(1f), 30, 10.dp)
            Text(d.rpm?.let { "%.1f".format(BRC, it / 1000f) } ?: "—", color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Cockpit do modo Direção em tela dividida (o mapa fica no outro lado): velocímetro grande, quatro leituras e o Spotify.
 * Os alertas do motor aparecem por cima, como em qualquer tela.
 */
@Composable
fun CockpitDividido(obd: ObdMonitor, monitor: MediaMonitor, sensores: Sensores) {
    val ctx = LocalContext.current
    val d = obd.dados
    val vel = d.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
    val consumo = d.consumoInstantaneo ?: d.consumoMedio ?: remember { Veiculo.obter(ctx).consumoMedio() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Modo direção", color = Cores.TextoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            BotaoPilula("Fechar", Icons.Filled.Close) { Direcao.fechar() }
        }
        VelocimetroDividido(d, vel, Modifier.weight(2.4f).fillMaxWidth())
        Row(Modifier.fillMaxWidth().weight(0.5f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Leitura("Motor", d.tempMotor?.let { "$it" } ?: "—", "°C", Modifier.weight(1f))
            Leitura("Bateria", d.tensao?.let { "%.1f".format(BRC, it) } ?: "—", "V", Modifier.weight(1f))
            Leitura("Tanque", d.nivelCombustivel?.let { "$it" } ?: "—", "%", Modifier.weight(1f))
            Leitura("Consumo", consumo?.let { "%.1f".format(BRC, it) } ?: "—", "km/l", Modifier.weight(1f))
        }
        MiniMusica(monitor, Modifier.fillMaxWidth())
    }
}

@Composable
internal fun Leitura(rotulo: String, valor: String, unidade: String, modifier: Modifier) {
    Column(modifier.fillMaxHeight().cartaoSuave().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
        Text(rotulo, color = Cores.TextoSuave, fontSize = 12.sp, maxLines = 1, softWrap = false)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(valor, color = Cores.Texto, fontSize = 24.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
            Text(unidade, color = Cores.TextoSuave, fontSize = 12.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}
