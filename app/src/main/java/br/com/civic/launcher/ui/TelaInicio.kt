package br.com.civic.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.ClimaUi
import br.com.civic.launcher.CondicaoClima
import br.com.civic.launcher.ConectividadeUi
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.obd.ObdMonitor

/** Paisagem: Waze, Spotify e widgets lado a lado. Retrato (futura virada de tela): empilha. */
@Composable
fun TelaInicio(monitor: MediaMonitor, sensores: Sensores, obd: ObdMonitor) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= maxHeight) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CartaoWaze(Modifier.weight(1.1f).fillMaxHeight())
                    CartaoSpotify(monitor, Modifier.weight(1.5f).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CartaoClima(sensores.clima, Modifier.weight(1f).fillMaxWidth())
                        CartaoConectividade(sensores.conectividade, Modifier.weight(1f).fillMaxWidth())
                    }
                }
                LinhaOBD(sensores, obd, Modifier.fillMaxWidth().height(130.dp))
            }
        } else {
            // Retrato (celular, ou a multimídia virada): blocos com altura fixa e rolagem se faltar espaço.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CartaoWaze(Modifier.fillMaxWidth().height(260.dp))
                CartaoSpotify(monitor, Modifier.fillMaxWidth().height(330.dp))
                Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CartaoClima(sensores.clima, Modifier.weight(1f).fillMaxHeight())
                    CartaoConectividade(sensores.conectividade, Modifier.weight(1f).fillMaxHeight())
                }
                LinhaOBD(sensores, obd, Modifier.fillMaxWidth().height(120.dp))
            }
        }
    }
}

@Composable
private fun Cartao(modifier: Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(Cores.Cartao).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        content = conteudo,
    )
}

@Composable
private fun CartaoClima(clima: ClimaUi?, modifier: Modifier) {
    Cartao(modifier) {
        if (clima == null) {
            Text("Clima", color = Cores.TextoSuave, fontSize = 20.sp)
            Text("Sem dados", color = Cores.TextoSuave, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text("Precisa de internet", color = Cores.TextoSuave, fontSize = 16.sp)
        } else {
            val (icone, cor) = when (clima.condicao) {
                CondicaoClima.Limpo -> Icons.Filled.WbSunny to Cores.Amarelo
                CondicaoClima.Nublado -> Icons.Filled.Cloud to Cores.TextoSuave
                CondicaoClima.Chuva -> Icons.Filled.WaterDrop to Cores.Waze
                CondicaoClima.Tempestade -> Icons.Filled.Bolt to Cores.Amarelo
            }
            Text(clima.local, color = Cores.TextoSuave, fontSize = 20.sp, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icone, null, tint = cor, modifier = Modifier.size(48.dp))
                Text("${clima.temperatura}°C", color = Cores.Texto, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            }
            Text("${clima.condicao.texto} · Máx ${clima.maxima}° · Mín ${clima.minima}°", color = Cores.TextoSuave, fontSize = 16.sp, maxLines = 1)
        }
    }
}

@Composable
private fun CartaoConectividade(c: ConectividadeUi, modifier: Modifier) {
    Cartao(modifier) {
        Text("Conectividade", color = Cores.TextoSuave, fontSize = 20.sp)
        Row(
            Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val pct = c.percentual
            val cor = when {
                pct == null -> Cores.TextoSuave
                pct >= 60 -> Cores.Spotify
                pct >= 30 -> Cores.Amarelo
                else -> Cores.Destaque
            }
            Medidor((pct ?: 0) / 100f, cor, Modifier.fillMaxHeight().aspectRatio(1f), espessura = 10.dp) {
                Text(if (pct != null) "$pct%" else "—", color = Cores.Texto, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(
                    listOf(c.operadora, c.rede).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Sem rede" },
                    color = Cores.Texto, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
                Text(if (c.dbm != null) "${c.dbm} dBm" else "Sinal indisponível", color = Cores.TextoSuave, fontSize = 16.sp)
            }
        }
    }
}

/** Velocidade vem do GPS. RPM, temperatura, tensão e falhas só aparecem com o adaptador OBD. */
@Composable
private fun LinhaOBD(sensores: Sensores, obd: ObdMonitor, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(24.dp)).background(Cores.Cartao).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val d = obd.dados
        val vel = d.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
        Medidor(vel / 220f, Cores.Destaque, Modifier.fillMaxHeight().aspectRatio(1f), espessura = 10.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${vel.toInt()}", color = Cores.Texto, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("km/h", color = Cores.TextoSuave, fontSize = 16.sp)
            }
        }
        Indicador(Icons.Filled.Speed, d.rpm?.toString() ?: "—", "RPM", Modifier.weight(1f))
        Indicador(Icons.Filled.Thermostat, d.tempMotor?.let { "$it°C" } ?: "—", "Temp. motor", Modifier.weight(1f))
        Indicador(Icons.Filled.BatteryChargingFull, d.tensao?.let { "%.1f V".format(it) } ?: "—", "Bateria", Modifier.weight(1f))
        Indicador(Icons.Filled.Warning, d.falhas?.let { if (it == 0) "Nenhuma" else "$it" } ?: "—", "Falhas", Modifier.weight(1f))
    }
}

@Composable
internal fun Indicador(icone: ImageVector, valor: String, rotulo: String, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val estreito = maxWidth < 100.dp
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icone, null, tint = Cores.TextoSuave, modifier = Modifier.size(if (estreito) 24.dp else 34.dp))
            Text(valor, color = Cores.Texto, fontSize = if (estreito) 20.sp else 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(rotulo, color = Cores.TextoSuave, fontSize = if (estreito) 11.sp else 16.sp, maxLines = 1)
        }
    }
}
