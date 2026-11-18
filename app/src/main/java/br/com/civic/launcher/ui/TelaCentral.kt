package br.com.civic.launcher.ui

import androidx.compose.foundation.clickable
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

/** Central do Civic: giros e velocidade | indicadores do OBD | carro (à direita, como na Tela Início). */
@Composable
fun TelaCentral(sensores: Sensores, obd: ObdMonitor) {
    val d = obd.dados
    val vel = d.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
    var escolhendo by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CartaoHud(d, vel, Modifier.weight(1.7f).fillMaxHeight())
                Indicadores(d, Modifier.weight(0.95f).fillMaxHeight(), vertical = true)
                CartaoCarroCentral(obd, d, Modifier.weight(0.9f).fillMaxHeight()) { escolhendo = true }
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CartaoHud(d, vel, Modifier.fillMaxWidth().height(380.dp))
                Indicadores(d, Modifier.fillMaxWidth().height(300.dp), vertical = false)
                CartaoCarroCentral(obd, d, Modifier.fillMaxWidth().height(400.dp)) { escolhendo = true }
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
private fun CartaoHud(d: DadosObd, vel: Float, modifier: Modifier) {
    Cartao(modifier) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("RPM", color = Cores.TextoSuave, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(d.rpm?.toString() ?: "—", color = Cores.Texto, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Text(" / 8.000", color = Cores.TextoSuave, fontSize = 14.sp, modifier = Modifier.padding(bottom = 3.dp))
                    }
                }
                BarraSegmentos((d.rpm ?: 0) / 8000f, Modifier.fillMaxWidth(), 34, 24.dp)
            }
            // Gráfico esportivo: ocupa todo o espaço que sobra no cartão.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                TacometroEsportivo(d.rpm, vel, Modifier.fillMaxSize())
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("Carga do motor", color = Cores.TextoSuave, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(d.carga?.let { "$it%" } ?: "—", color = Cores.Texto, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                }
                BarraFina((d.carga ?: 0) / 100f, Cores.Destaque, Modifier.fillMaxWidth(), 8.dp)
            }
        }
    }
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
    val fracoes = listOf(
        (d.tempMotor ?: 0) / 120f, ((d.tensao ?: 10f) - 10f) / 5f, (d.tempAdmissao ?: 0) / 80f, (d.acelerador ?: 0) / 100f,
    )
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            itens.forEachIndexed { i, (t, ic, cor) ->
                CartaoIndicador(t, ic, cor, valores[i].first ?: "—", valores[i].second, fracoes[i], Modifier.weight(1f).fillMaxWidth())
            }
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for (linha in 0..1) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (col in 0..1) {
                    val i = linha * 2 + col
                    val (t, ic, cor) = itens[i]
                    CartaoIndicador(t, ic, cor, valores[i].first ?: "—", valores[i].second, fracoes[i], Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
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

@Composable
private fun CartaoCarroCentral(obd: ObdMonitor, d: DadosObd, modifier: Modifier, aoEscolherAdaptador: () -> Unit) {
    Cartao(modifier) {
        FotoCarro(Modifier.fillMaxSize(), 0.5f)
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            TituloCarro()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChipsCarro(d)
                Text(
                    when (obd.estado) {
                        EstadoObd.Desconectado -> "Adaptador não conectado"
                        EstadoObd.Procurando -> "Procurando o adaptador…"
                        EstadoObd.Conectando -> "Conectando…"
                        EstadoObd.Conectado -> if (d.rpm == null) "Conectado — ligue o motor" else "Conectado"
                        EstadoObd.Demonstracao -> "Demonstração (dados simulados)"
                    },
                    color = Cores.TextoSuave, fontSize = 13.sp, maxLines = 1,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotaoPilula(
                        if (obd.estado == EstadoObd.Demonstracao) "Sair da demonstração" else "Demonstração",
                        Icons.Filled.PlayArrow, Modifier.fillMaxWidth(),
                    ) { obd.alternarDemonstracao() }
                    BotaoPilula("Escolher adaptador", Icons.Filled.Bluetooth, Modifier.fillMaxWidth()) { aoEscolherAdaptador() }
                }
            }
        }
    }
}
