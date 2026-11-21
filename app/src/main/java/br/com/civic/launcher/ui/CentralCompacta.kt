package br.com.civic.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.Veiculo
import br.com.civic.launcher.obd.ObdMonitor
import br.com.civic.launcher.obd.SeriesObd
import java.util.Locale

private val BRO = Locale("pt", "BR")

/**
 * Tela do OBD para meia tela (tela dividida): os layouts Esportivo, Cockpit e Modular precisam de largura e quebravam
 * (números cortados, textos letra por letra, botões sobre os mostradores). Aqui o velocímetro é grande, o giro é uma faixa fina,
 * seis leituras ficam em duas linhas e embaixo vão o Spotify e o diagnóstico. Em tela cheia volta o layout escolhido.
 */
@Composable
fun CentralCompacta(obd: ObdMonitor, monitor: MediaMonitor, vel: Float, aoEscolherAdaptador: () -> Unit, aoVerFalhas: () -> Unit) {
    val ctx = LocalContext.current
    val d = obd.dados
    val series = remember { SeriesObd.obter() }
    @Suppress("UNUSED_VARIABLE") val tique = series.versao
    val consumo = d.consumoInstantaneo ?: d.consumoMedio ?: remember { Veiculo.obter(ctx).consumoMedio() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SeletorLayoutObd(obd, aoEscolherAdaptador)
        VelocimetroDividido(d, vel, Modifier.weight(2.2f).fillMaxWidth())
        Row(Modifier.weight(0.55f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Motor", d.tempMotor?.toString() ?: "—", "°C", Modifier.weight(1f)) { Sparkline(series.serie("tempMotor"), Cores.Destaque, Modifier.fillMaxWidth().height(14.dp)) }
            Tile("Ar", d.tempAdmissao?.toString() ?: "—", "°C", Modifier.weight(1f)) { Sparkline(series.serie("tempAdmissao"), Cores.Destaque, Modifier.fillMaxWidth().height(14.dp)) }
            Tile("Bateria", d.tensao?.let { "%.1f".format(BRO, it) } ?: "—", "V", Modifier.weight(1f)) { Sparkline(series.serie("tensao"), Cores.Ok, Modifier.fillMaxWidth().height(14.dp)) }
        }
        Row(Modifier.weight(0.55f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Tanque", d.nivelCombustivel?.toString() ?: "—", "%", Modifier.weight(1f)) { BarraFina((d.nivelCombustivel ?: 0) / 100f, Cores.Destaque, Modifier.fillMaxWidth(), 6.dp) }
            Tile("Carga", d.carga?.toString() ?: "—", "%", Modifier.weight(1f)) { BarraFina((d.carga ?: 0) / 100f, Cores.Destaque, Modifier.fillMaxWidth(), 6.dp) }
            Tile("Consumo", consumo?.let { "%.1f".format(BRO, it) } ?: "—", "km/l", Modifier.weight(1f)) {}
        }
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMusica(monitor, Modifier.weight(1.5f).fillMaxHeight())
            CartaoDiagnostico(d, Modifier.weight(1f).fillMaxHeight(), aoVerFalhas)
        }
    }
}

/** Leitura compacta: rótulo, valor e um detalhe fino embaixo (gráfico ou barra). Nada quebra linha. */
@Composable
private fun Tile(rotulo: String, valor: String, unidade: String, modifier: Modifier, detalhe: @Composable () -> Unit) {
    Column(modifier.fillMaxHeight().cartaoSuave().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text(rotulo, color = Cores.TextoSuave, fontSize = 12.sp, maxLines = 1, softWrap = false)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(valor, color = Cores.Texto, fontSize = 24.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
            Text(unidade, color = Cores.TextoSuave, fontSize = 12.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 3.dp))
        }
        detalhe()
    }
}
