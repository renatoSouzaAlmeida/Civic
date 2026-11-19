package br.com.civic.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.obd.AvaliacaoBateria
import br.com.civic.launcher.obd.ObdMonitor
import br.com.civic.launcher.obd.Partida
import br.com.civic.launcher.obd.SaudeBateria
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BRB = Locale("pt", "BR")

private fun corAvaliacao(a: AvaliacaoBateria): Color = when (a) {
    AvaliacaoBateria.Boa -> Color(0xFF22C55E)
    AvaliacaoBateria.Atencao -> Color(0xFFFFB020)
    AvaliacaoBateria.Fraca -> Color(0xFFEF4444)
}

/**
 * Saúde da bateria: última partida (repouso, menor tensão no arranque e carga do alternador), tensão agora,
 * gráfico das últimas partidas e histórico.
 */
@Composable
fun TelaBateria(obd: ObdMonitor) {
    val ctx = LocalContext.current
    val saude = remember { SaudeBateria.obter(ctx.applicationContext) }
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", BRB) }
    val ultima = saude.partidas.firstOrNull()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Secao("Última partida") {
            if (ultima == null) {
                Text(
                    "Ainda não há partida registrada. Com o adaptador conectado, ligue a ignição, espere 2 segundos e dê a partida: " +
                        "o app mede a queda de tensão no arranque.",
                    color = Cores.TextoSuave, fontSize = 15.sp,
                )
            } else {
                val cor = corAvaliacao(ultima.avaliacao)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Filled.BatteryChargingFull, null, tint = cor, modifier = Modifier.size(52.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Bateria ${ultima.avaliacao.rotulo.lowercase()}", color = cor, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(ultima.avaliacao.explicacao, color = Cores.Texto, fontSize = 15.sp)
                        Text(fmt.format(Date(ultima.data)) + if (ultima.demonstracao) " · demonstração" else "", color = Cores.TextoSuave, fontSize = 13.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Valor("Em repouso", "%.1f V".format(BRB, ultima.repouso), if (ultima.repouso >= 12.4f) "carga cheia" else if (ultima.repouso >= 12.2f) "meia carga" else "carga baixa", Modifier.weight(1f))
                    Valor("Menor na partida", "%.1f V".format(BRB, ultima.minima), "boa acima de 10,0 V", Modifier.weight(1f))
                    Valor(
                        "Alternador", ultima.carga?.let { "%.1f V".format(BRB, it) } ?: "—",
                        ultima.carga?.let { if (SaudeBateria.cargaOk(it)) "carregando bem" else "fora de 13,5–14,8 V" } ?: "medido 20 s após a partida",
                        Modifier.weight(1f),
                    )
                }
            }
        }
        Secao("Agora") {
            Text(
                (saude.ultimaTensao?.let { "%.1f V".format(BRB, it) } ?: "—") + " · motor " + if (saude.motorLigado) "ligado" else "desligado",
                color = Cores.Texto, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            )
        }
        if (saude.partidas.size >= 2) Secao("Queda de tensão nas últimas partidas") {
            GraficoPartidas(saude.partidas.take(15).reversed(), Modifier.fillMaxWidth().height(150.dp))
            Text("Cada ponto é a menor tensão de uma partida. Linha amarela: 10,0 V · vermelha: 9,6 V. Quedas cada vez maiores indicam bateria se desgastando.", color = Cores.TextoSuave, fontSize = 13.sp)
        }
        Secao("Histórico (${saude.partidas.size})") {
            if (saude.partidas.isEmpty()) Text("As partidas registradas aparecem aqui.", color = Cores.TextoSuave, fontSize = 15.sp)
            saude.partidas.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cores.Chip).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(corAvaliacao(p.avaliacao)))
                    Column(Modifier.weight(1f)) {
                        Text(fmt.format(Date(p.data)) + if (p.demonstracao) " · demonstração" else "", color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "repouso %.1f V · partida %.1f V".format(BRB, p.repouso, p.minima) + (p.carga?.let { " · alternador %.1f V".format(BRB, it) } ?: ""),
                            color = Cores.TextoSuave, fontSize = 13.sp,
                        )
                    }
                    Text(p.avaliacao.rotulo, color = corAvaliacao(p.avaliacao), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (saude.partidas.isNotEmpty()) TextButton(onClick = { saude.limpar() }) { Text("Limpar histórico", fontSize = 15.sp) }
        }
        Text(
            "Como funciona: o adaptador lê a tensão algumas vezes por segundo; a queda real no arranque pode ser um pouco maior que a mostrada. " +
                "Bateria boa costuma ficar acima de 10,0 V na partida; abaixo de 9,6 V está no fim.",
            color = Cores.TextoSuave, fontSize = 13.sp,
        )
    }
}

@Composable
private fun Valor(rotulo: String, valor: String, apoio: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Cores.Chip).padding(12.dp)) {
        Text(rotulo, color = Cores.TextoSuave, fontSize = 13.sp, maxLines = 1)
        Text(valor, color = Cores.Texto, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(apoio, color = Cores.TextoSuave, fontSize = 12.sp, maxLines = 1)
    }
}

/** Gráfico simples (Canvas) da menor tensão de cada partida, com as linhas de referência 10,0 V e 9,6 V. */
@Composable
private fun GraficoPartidas(lista: List<Partida>, modifier: Modifier) {
    val linha = Cores.Destaque
    val grade = Cores.Borda
    Canvas(modifier) {
        val min = 8.5f
        val max = 13.0f
        fun y(v: Float) = size.height * (1f - ((v.coerceIn(min, max) - min) / (max - min)))
        listOf(10.0f to Color(0xFFFFB020), 9.6f to Color(0xFFEF4444), 12.4f to grade).forEach { (v, c) ->
            drawLine(c, Offset(0f, y(v)), Offset(size.width, y(v)), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
        }
        if (lista.isEmpty()) return@Canvas
        val passo = if (lista.size > 1) size.width / (lista.size - 1) else 0f
        val pontos = lista.mapIndexed { i, p -> Offset(i * passo, y(p.minima)) }
        for (i in 1 until pontos.size) drawLine(linha, pontos[i - 1], pontos[i], 4f)
        pontos.forEachIndexed { i, o -> drawCircle(corAvaliacao(lista[i].avaliacao), 8f, o) }
    }
}
