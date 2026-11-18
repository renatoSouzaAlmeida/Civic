package br.com.civic.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import br.com.civic.launcher.MediaMonitor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.obd.DadosObd
import br.com.civic.launcher.obd.EstadoObd
import br.com.civic.launcher.obd.ObdMonitor

/** Painel esportivo do OBD: velocidade | giros + carro avançando | Spotify, com indicadores nas laterais. */
@Composable
fun TelaCentral(sensores: Sensores, obd: ObdMonitor, monitor: MediaMonitor) {
    val d = obd.dados
    val vel = d.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
    var escolhendo by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Cartao(Modifier.weight(1.5f).fillMaxWidth()) {
                        GaugeArco(vel / 180f, "${vel.toInt()}", "km/h", Modifier.fillMaxSize().padding(10.dp))
                    }
                    Indic("Temp. motor", Icons.Filled.Thermostat, Cores.Ok, d.tempMotor?.toString(), "°C", (d.tempMotor ?: 0) / 120f, Modifier.weight(0.5f))
                    Indic("Bateria", Icons.Filled.BatteryChargingFull, Cores.Ok, d.tensao?.let { "%.1f".format(it) }, "V", ((d.tensao ?: 10f) - 10f) / 5f, Modifier.weight(0.5f))
                }
                Column(Modifier.weight(1.45f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Cartao(Modifier.weight(1.75f).fillMaxWidth()) {
                        Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                            TacometroEsportivo(d.rpm, vel, Modifier.fillMaxSize(), centroRpm = true)
                        }
                    }
                    CartaoCarroAvancando(obd, d, vel, Modifier.weight(1f).fillMaxWidth()) { escolhendo = true }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CartaoSpotify(monitor, Modifier.weight(1.5f).fillMaxWidth())
                    Indic("Ar de admissão", Icons.Filled.AcUnit, Cores.Destaque, d.tempAdmissao?.toString(), "°C", (d.tempAdmissao ?: 0) / 80f, Modifier.weight(0.5f))
                    Indic("Carga do motor", Icons.Filled.Speed, Cores.Destaque, d.carga?.toString(), "%", (d.carga ?: 0) / 100f, Modifier.weight(0.5f))
                }
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Cartao(Modifier.fillMaxWidth().height(380.dp)) {
                    Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                        TacometroEsportivo(d.rpm, vel, Modifier.fillMaxSize(), centroRpm = true)
                    }
                }
                Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Cartao(Modifier.weight(1f).fillMaxHeight()) { GaugeArco(vel / 180f, "${vel.toInt()}", "km/h", Modifier.fillMaxSize().padding(8.dp)) }
                    Cartao(Modifier.weight(1f).fillMaxHeight()) { GaugeArco((d.carga ?: 0) / 100f, d.carga?.toString() ?: "—", "% carga", Modifier.fillMaxSize().padding(8.dp)) }
                }
                CartaoCarroAvancando(obd, d, vel, Modifier.fillMaxWidth().height(300.dp)) { escolhendo = true }
                CartaoSpotify(monitor, Modifier.fillMaxWidth().height(300.dp))
                Indicadores(d, Modifier.fillMaxWidth().height(300.dp), vertical = false)
            }
        }
    }

    if (escolhendo) {
        LaunchedEffect(Unit) { obd.buscarLista() }
        AlertDialog(
            onDismissRequest = { escolhendo = false },
            confirmButton = { TextButton(onClick = { escolhendo = false }) { Text("Fechar", fontSize = 22.sp) } },
            title = { Text("Escolher adaptador OBD") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (obd.buscandoLista) Text("Buscando aparelhos Bluetooth…", fontSize = 18.sp)
                    else if (obd.encontrados.isEmpty()) Text("Nenhum aparelho encontrado. Ligue o carro e tente de novo.", fontSize = 18.sp)
                    obd.encontrados.forEach { dispositivo ->
                        Text(
                            dispositivo.nome,
                            fontSize = 24.sp,
                            modifier = Modifier.fillMaxWidth().clickable { obd.escolher(dispositivo); escolhendo = false }.padding(vertical = 14.dp),
                        )
                    }
                }
            },
        )
    }
}


@Composable
private fun Indic(titulo: String, icone: ImageVector, cor: Color, valor: String?, unidade: String, fracao: Float, modifier: Modifier) {
    CartaoIndicador(titulo, icone, cor, valor ?: "—", unidade, fracao, modifier.fillMaxWidth())
}

@Composable
private fun Indicadores(d: DadosObd, modifier: Modifier, vertical: Boolean) {
    val itens = listOf(
        Triple("Temp. motor", Icons.Filled.Thermostat, Cores.Ok),
        Triple("Bateria", Icons.Filled.BatteryChargingFull, Cores.Ok),
        Triple("Ar de admissão", Icons.Filled.AcUnit, Cores.Destaque),
        Triple("Acelerador", Icons.Filled.Speed, Cores.Destaque),
    )
    val valores = listOf(
        d.tempMotor?.toString() to "°C", d.tensao?.let { "%.1f".format(it) } to "V",
        d.tempAdmissao?.toString() to "°C", d.acelerador?.toString() to "%",
    )
    val fracoes = listOf((d.tempMotor ?: 0) / 120f, ((d.tensao ?: 10f) - 10f) / 5f, (d.tempAdmissao ?: 0) / 80f, (d.acelerador ?: 0) / 100f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (linha in 0..1) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (col in 0..1) {
                val i = linha * 2 + col
                val (t, ic, cor) = itens[i]
                CartaoIndicador(t, ic, cor, valores[i].first ?: "—", valores[i].second, fracoes[i], Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** Carro de trás avançando pela estrada; embaixo, estado do adaptador e botões pequenos. */
@Composable
private fun CartaoCarroAvancando(obd: ObdMonitor, d: DadosObd, vel: Float, modifier: Modifier, aoEscolherAdaptador: () -> Unit) {
    Cartao(modifier) {
        CarroAvancando(vel, Modifier.fillMaxSize())
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.Top) {
            Text(
                when (obd.estado) {
                    EstadoObd.Desconectado -> "Adaptador não conectado"
                    EstadoObd.Procurando -> "Procurando o adaptador…"
                    EstadoObd.Conectando -> "Conectando…"
                    EstadoObd.Conectado -> if (d.rpm == null) "Conectado — ligue o motor" else "Conectado"
                    EstadoObd.Demonstracao -> "Demonstração (simulado)"
                },
                color = Cores.TextoSuave, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotaoMini(if (obd.estado == EstadoObd.Demonstracao) "Sair" else "Demo", Icons.Filled.PlayArrow) { obd.alternarDemonstracao() }
            BotaoMini("Adaptador", Icons.Filled.Bluetooth) { aoEscolherAdaptador() }
        }
    }
}

@Composable
private fun BotaoMini(rotulo: String, icone: ImageVector, aoClicar: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(Cores.Chip).border(1.dp, Cores.ChipBorda, RoundedCornerShape(14.dp))
            .clickable { aoClicar() }.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        androidx.compose.material3.Icon(icone, null, tint = Cores.Texto, modifier = Modifier.size(16.dp))
        Text(rotulo, color = Cores.Texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * Indicador do OBD: título à esquerda e valor grande à direita NA MESMA LINHA, barra embaixo.
 * Assim cabe mesmo em caixas baixas (tablet pequeno); o número acompanha a altura da caixa.
 */
@Composable
private fun CartaoIndicador(titulo: String, icone: ImageVector, cor: Color, valor: String, unidade: String, fracao: Float, modifier: Modifier) {
    Cartao(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val tamanho = minOf((maxHeight.value - 14f) * 0.62f, maxWidth.value * 0.22f).coerceAtLeast(16f).sp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Icon(icone, null, tint = Cores.TextoSuave, modifier = Modifier.size(20.dp))
                        Text(titulo, color = Cores.TextoSuave, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(valor, color = Cores.Texto, fontSize = tamanho, lineHeight = tamanho, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        if (valor != "—") Text(unidade, color = Cores.TextoSuave, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
                BarraFina(fracao, cor, Modifier.fillMaxWidth(), 6.dp)
            }
        }
    }
}
