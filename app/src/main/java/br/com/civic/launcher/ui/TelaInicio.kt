package br.com.civic.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.civic.launcher.CondicaoClima
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.obd.ObdMonitor

/**
 * Paisagem: carro | Waze (maior) | Spotify + 4 caixas (clima, rede, motor, bateria).
 * Retrato (futura virada de tela): tudo empilhado, com rolagem se faltar espaço.
 */
@Composable
fun TelaInicio(monitor: MediaMonitor, sensores: Sensores, obd: ObdMonitor) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CartaoCarro(obd, Modifier.weight(1.25f).fillMaxHeight())
                CartaoWaze(sensores, obd, Modifier.weight(1.05f).fillMaxHeight())
                Column(Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CartaoSpotify(monitor, Modifier.weight(1.15f).fillMaxWidth())
                    QuatroCaixas(sensores, obd, Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CartaoCarro(obd, Modifier.fillMaxWidth().height(330.dp))
                CartaoWaze(sensores, obd, Modifier.fillMaxWidth().height(300.dp))
                CartaoSpotify(monitor, Modifier.fillMaxWidth().height(320.dp))
                QuatroCaixas(sensores, obd, Modifier.fillMaxWidth().height(300.dp))
            }
        }
    }
}

/** Clima, rede, motor e bateria. Motor e bateria só têm valor com o adaptador OBD conectado. */
@Composable
private fun QuatroCaixas(sensores: Sensores, obd: ObdMonitor, modifier: Modifier) {
    val d = obd.dados
    val c = sensores.conectividade
    val clima = sensores.clima
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (clima != null) {
                val (icone, cor) = when (clima.condicao) {
                    CondicaoClima.Limpo -> Icons.Filled.WbSunny to Cores.Amarelo
                    CondicaoClima.Nublado -> Icons.Filled.Cloud to Cores.TextoSuave
                    CondicaoClima.Chuva -> Icons.Filled.WaterDrop to Cores.Waze
                    CondicaoClima.Tempestade -> Icons.Filled.Bolt to Cores.Amarelo
                }
                MiniCartao(clima.local, icone, cor, "${clima.temperatura}", "°C", "${clima.condicao.texto} · ${clima.maxima}° / ${clima.minima}°", Modifier.weight(1f).fillMaxHeight())
            } else {
                MiniCartao("Clima", Icons.Filled.WbSunny, Cores.TextoSuave, "—", "", "Precisa de internet", Modifier.weight(1f).fillMaxHeight())
            }
            MiniCartao(
                if (c.rede.isNotEmpty()) "Rede ${c.rede}" else "Rede", Icons.Filled.SignalCellularAlt, Cores.Destaque,
                c.percentual?.toString() ?: "—", if (c.percentual != null) "%" else "",
                listOf(c.operadora, c.dbm?.let { "$it dBm" }).filterNotNull().filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Sem rede" },
                Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MiniCartao(
                "Motor", Icons.Filled.Thermostat, Cores.Ok,
                d.tempMotor?.toString() ?: "—", if (d.tempMotor != null) "°C" else "",
                when {
                    d.tempMotor == null -> "OBD desconectado"
                    d.tempMotor > 105 -> "Temperatura alta"
                    else -> "Temperatura normal"
                },
                Modifier.weight(1f).fillMaxHeight(),
            )
            MiniCartao(
                "Bateria", Icons.Filled.BatteryChargingFull, Cores.Ok,
                d.tensao?.let { "%.1f".format(it) } ?: "—", if (d.tensao != null) "V" else "",
                when {
                    d.tensao == null -> "OBD desconectado"
                    d.tensao >= 13.2f -> "Alternador carregando"
                    d.tensao < 12.0f -> "Bateria baixa"
                    else -> "Motor desligado"
                },
                Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}
