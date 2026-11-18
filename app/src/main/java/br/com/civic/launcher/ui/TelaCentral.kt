package br.com.civic.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.obd.EstadoObd
import br.com.civic.launcher.obd.ObdMonitor

/** Central do Civic (Tela 3): velocidade, giros e carga do motor, mais os dados do OBD. */
@Composable
fun TelaCentral(sensores: Sensores, obd: ObdMonitor) {
    val d = obd.dados
    val vel = d.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
    val rpm = d.rpm ?: 0
    var escolhendo by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Cores.Cartao).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Medidor(vel / 220f, Cores.Waze, Modifier.aspectRatio(1f), espessura = 18.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${vel.toInt()}", color = Cores.Texto, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                        Text("km/h", color = Cores.TextoSuave, fontSize = 22.sp)
                    }
                }
            }
            Box(Modifier.weight(1.4f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Medidor(rpm / 7000f, if (rpm >= 6000) Cores.Amarelo else Cores.Destaque, Modifier.aspectRatio(1f), espessura = 22.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (d.rpm != null) "$rpm" else "—", color = Cores.Texto, fontSize = 80.sp, fontWeight = FontWeight.Bold)
                        Text("rpm", color = Cores.TextoSuave, fontSize = 24.sp)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Medidor((d.carga ?: 0) / 100f, Cores.Spotify, Modifier.aspectRatio(1f), espessura = 18.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.carga?.let { "$it%" } ?: "—", color = Cores.Texto, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                        Text("carga do motor", color = Cores.TextoSuave, fontSize = 18.sp)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Indicador(Icons.Filled.Thermostat, d.tempMotor?.let { "$it°C" } ?: "—", "Temp. motor", Modifier.weight(1f))
            Indicador(Icons.Filled.BatteryChargingFull, d.tensao?.let { "%.1f V".format(it) } ?: "—", "Bateria", Modifier.weight(1f))
            Indicador(Icons.Filled.AcUnit, d.tempAdmissao?.let { "$it°C" } ?: "—", "Ar admissão", Modifier.weight(1f))
            Indicador(Icons.Filled.Speed, d.acelerador?.let { "$it%" } ?: "—", "Acelerador", Modifier.weight(1f))
            Indicador(
                Icons.Filled.Warning,
                d.falhas?.let { if (it == 0) "Nenhuma" else "$it" } ?: "—",
                if (d.codigos.isEmpty()) "Falhas" else d.codigos.joinToString(" "),
                Modifier.weight(1f),
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                when (obd.estado) {
                    EstadoObd.Desconectado -> "Adaptador não conectado"
                    EstadoObd.Procurando -> "Procurando o adaptador…"
                    EstadoObd.Conectando -> "Conectando…"
                    EstadoObd.Conectado -> if (d.rpm == null) "Conectado — ligue o motor" else "Conectado"
                    EstadoObd.Demonstracao -> "Modo demonstração (dados simulados)"
                },
                color = Cores.TextoSuave, fontSize = 20.sp, modifier = Modifier.weight(1f),
            )
            Atalho(
                if (obd.estado == EstadoObd.Demonstracao) "Sair da demo" else "Demonstração",
                Icons.Filled.PlayArrow, Modifier.width(300.dp),
            ) { obd.alternarDemonstracao() }
            Atalho("Adaptador", Icons.Filled.Bluetooth, Modifier.width(260.dp)) { escolhendo = true }
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
