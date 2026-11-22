package br.com.civic.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Viagem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BRA = Locale("pt", "BR")
private val FundoEscuro = Color(0xFF111827)

@Composable
private fun corDaNota(n: Int): Color = when { n >= 90 -> Cores.Ok; n >= 75 -> Cores.Destaque; n >= 55 -> Cores.Amarelo; else -> Cores.Vermelho }
private fun mmss(ms: Long): String { val s = ms / 1000; return "%02d:%02d".format(BRA, s / 60, s % 60) }

/**
 * Análise de condução da viagem escolhida (proposta "Análise de Condução"): indicadores de aceleração, rpm e consumo, a nota com a barra,
 * o resumo (velocidade média, distância, consumo, marcha lenta), os gráficos ao longo da viagem e os eventos. Só o que a viagem mediu
 * aparece com valor; sem OBD (viagem só por GPS) aceleração, rpm, temperatura e consumo mostram "—" e os gráficos explicam por quê.
 */
@Composable
fun AnaliseViagem(v: Viagem) {
    val s = v.serie
    val acel = s.filter { it.acel >= 0 }.map { it.acel.toFloat() }
    val rpms = s.filter { it.rpm > 400 }.map { it.rpm }
    val temps = s.filter { it.temp >= 0 }.map { it.temp.toFloat() }
    val cons = s.filter { it.cons >= 0 }.map { it.cons / 10f }
    val vels = s.map { it.vel.toFloat() }
    val acelMedia = acel.takeIf { it.isNotEmpty() }?.average()
    val rpmMedio = rpms.takeIf { it.isNotEmpty() }?.average()
    val tempMax = temps.maxOrNull()
    val nota = v.nota
    val corNota = nota?.let { corDaNota(it) } ?: Cores.TextoSuave
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", BRA) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Análise de condução · " + fmt.format(Date(v.inicio)) + (if (v.demonstracao) " · demonstração" else "") + " · " + (if (v.comObd) "OBD" else "só GPS"),
            color = Cores.TextoSuave, fontSize = 14.sp)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val larga = maxWidth >= 900.dp
            if (larga) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(3.2f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Indicadores(acelMedia, rpmMedio, v, cons)
                    CartaoNota(v, nota, corNota)
                    Graficos(acel, rpms, temps, vels, tempMax, acelMedia, true)
                }
                Eventos(v, tempMax, Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Indicadores(acelMedia, rpmMedio, v, cons)
                CartaoNota(v, nota, corNota)
                Graficos(acel, rpms, temps, vels, tempMax, acelMedia, false)
                Eventos(v, tempMax, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Caixa(modifier: Modifier = Modifier, escuro: Boolean = false, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(if (escuro) FundoEscuro else Cores.Chip).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), content = conteudo,
    )
}

@Composable
private fun Indicadores(acelMedia: Double?, rpmMedio: Double?, v: Viagem, cons: List<Float>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Caixa(Modifier.weight(1f)) {
            Text("Aceleração média", color = Cores.TextoSuave, fontSize = 13.sp)
            Text(acelMedia?.let { "%.0f %%".format(BRA, it) } ?: "—", color = Cores.Texto, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Barra(((acelMedia ?: 0.0) / 100.0).toFloat(), Cores.Destaque)
        }
        Caixa(Modifier.weight(1f)) {
            Text("RPM médio", color = Cores.TextoSuave, fontSize = 13.sp)
            Text(rpmMedio?.let { "%,.0f".format(BRA, it) } ?: "—", color = Cores.Texto, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            // Verde na zona eficiente (1.500 a 2.500), amarelo até 3.500 e vermelho acima.
            val f = ((rpmMedio ?: 0.0) / 6000.0).toFloat().coerceIn(0f, 1f)
            Barra(f, when { rpmMedio == null -> Cores.TextoSuave; rpmMedio < 3000 -> Cores.Ok; rpmMedio < 4000 -> Cores.Amarelo; else -> Cores.Vermelho })
        }
        Caixa(Modifier.weight(1f)) {
            Text("Consumo médio", color = Cores.TextoSuave, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(v.consumoMedio?.let { "%.1f".format(BRA, it) } ?: "—", color = Cores.Texto, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("km/l", color = Cores.TextoSuave, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
            Linha(cons, Cores.Ok, Modifier.fillMaxWidth().height(22.dp))
        }
    }
}

@Composable
private fun Barra(fracao: Float, cor: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Cores.Borda)) {
        Box(Modifier.fillMaxWidth(fracao.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(cor))
    }
}

@Composable
private fun CartaoNota(v: Viagem, nota: Int?, cor: Color) {
    Caixa(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (nota == null || nota >= 55) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, tint = cor, modifier = Modifier.size(26.dp))
            Text(
                when { nota == null -> "Viagem curta demais para dar nota"; nota >= 75 -> "Condução eficiente"; nota >= 55 -> "Condução com pontos de atenção"; else -> "Condução a melhorar" },
                color = Cores.Texto, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            )
        }
        Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(11.dp)).background(Cores.Borda)) {
            if (nota != null) Box(Modifier.fillMaxWidth(nota / 100f).fillMaxHeight().clip(RoundedCornerShape(11.dp)).background(Brush.horizontalGradient(listOf(cor.copy(alpha = 0.75f), cor))))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text(nota?.let { Viagem.rotuloNota(it) } ?: "—", color = Cores.Texto, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        nota == null -> "Menos de 0,5 km medidos"
                        v.frenagens + v.aceleracoes + v.excessos + v.rpmAlto == 0 -> "Ritmo suave, sem eventos"
                        else -> "${v.frenagens} frenagens, ${v.aceleracoes} acelerações, ${v.excessos} excessos, ${v.rpmAlto} rpm alto"
                    },
                    color = Cores.TextoSuave, fontSize = 13.sp,
                )
            }
            Text(nota?.let { "$it %" } ?: "—", color = cor, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Dado("Velocidade média", "%.0f km/h".format(BRA, v.velMedia), Modifier.weight(1f))
            Dado("Distância", "%.1f km".format(BRA, v.distanciaKm), Modifier.weight(1f))
            Dado("Duração", br.com.civic.launcher.ComputadorBordo.duracao(v.duracaoMs), Modifier.weight(1f))
            Dado("Marcha lenta", if (v.comObd) mmss(v.marchaLentaMs) else "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Dado(rotulo: String, valor: String, modifier: Modifier) {
    Column(modifier) {
        Text(rotulo, color = Cores.TextoSuave, fontSize = 12.sp, maxLines = 1)
        Text(valor, color = Cores.Texto, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun Graficos(acel: List<Float>, rpms: List<Int>, temps: List<Float>, vels: List<Float>, tempMax: Float?, acelMedia: Double?, larga: Boolean) {
    val semObd = "Sem OBD nesta viagem: o gráfico só existe com o adaptador."
    val conteudo: @Composable (Modifier) -> Unit = { m ->
        Row(m.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Caixa(Modifier.weight(1f)) {
                Text("Aceleração (pedal)", color = Cores.TextoSuave, fontSize = 13.sp)
                Text(acelMedia?.let { "%.0f %%".format(BRA, it) } ?: "—", color = Cores.Texto, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                if (acel.size >= 2) Linha(acel, Cores.Destaque, Modifier.fillMaxWidth().height(80.dp), 0f, 100f)
                else Text(semObd, color = Cores.TextoSuave, fontSize = 12.sp)
            }
            Caixa(Modifier.weight(1f)) {
                Text("RPM · tempo em cada faixa", color = Cores.TextoSuave, fontSize = 13.sp)
                if (rpms.size >= 2) {
                    Histograma(rpms, Modifier.fillMaxWidth().height(80.dp))
                    Text("Verde: zona eficiente (1.500 a 2.500 rpm) · vermelho: acima de 4.000", color = Cores.TextoSuave, fontSize = 11.sp)
                } else Text(semObd, color = Cores.TextoSuave, fontSize = 12.sp)
            }
            Caixa(Modifier.weight(1f)) {
                Text(if (temps.isNotEmpty()) "Temperatura do motor" else "Velocidade", color = Cores.TextoSuave, fontSize = 13.sp)
                if (temps.isNotEmpty()) {
                    Text("%.0f °C máx.".format(BRA, tempMax ?: 0f), color = Cores.Texto, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Linha(temps, Cores.Amarelo, Modifier.fillMaxWidth().height(80.dp))
                } else if (vels.size >= 2) {
                    Text("%.0f km/h máx.".format(BRA, vels.max()), color = Cores.Texto, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Linha(vels, Cores.Destaque, Modifier.fillMaxWidth().height(80.dp), 0f)
                } else Text("Sem dados de temperatura nesta viagem.", color = Cores.TextoSuave, fontSize = 12.sp)
            }
        }
    }
    conteudo(Modifier)
}

@Composable
private fun Eventos(v: Viagem, tempMax: Float?, modifier: Modifier) {
    Caixa(modifier, escuro = true) {
        Text("Eventos da viagem", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Linha2("Frenagens bruscas", v.frenagens.toString())
        Linha2("Acelerações bruscas", v.aceleracoes.toString())
        Linha2("Excessos de velocidade", v.excessos.toString() + if (v.excessos > 0) " (máx. ${v.excessoMaxKmh} km/h)" else "")
        Linha2("RPM elevado", if (v.comObd) v.rpmAlto.toString() else "—")
        Linha2("Velocidade máxima", "${v.velMaxima} km/h")
        Linha2("RPM máximo", if (v.comObd) "%,d".format(BRA, v.rpmMaximo) else "—")
        Linha2("Temperatura máxima", tempMax?.let { "%.0f °C".format(BRA, it) } ?: "—")
        Linha2("Registros na linha do tempo", v.eventos.size.toString())
    }
}

@Composable
private fun Linha2(rotulo: String, valor: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(rotulo, color = Color(0xFF9CA3AF), fontSize = 13.sp)
        Text(valor, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Linha simples: valores ao longo da viagem. [min]/[max] fixam a escala (senão usa a faixa dos próprios dados). */
@Composable
private fun Linha(valores: List<Float>, cor: Color, modifier: Modifier, min: Float? = null, max: Float? = null) {
    Canvas(modifier) {
        if (valores.size < 2) return@Canvas
        val lo = min ?: valores.min(); var hi = max ?: valores.max()
        if (hi - lo < 1f) hi = lo + 1f
        val p = Path()
        valores.forEachIndexed { i, y ->
            val px = size.width * i / (valores.size - 1)
            val py = size.height - (y - lo) / (hi - lo) * size.height
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        drawPath(p, cor, style = Stroke(width = 2.5.dp.toPx()))
    }
}

/** Barras do tempo em cada faixa de 500 rpm (de 500 a 6.000); verde na zona eficiente e vermelho acima de 4.000. */
@Composable
private fun Histograma(rpms: List<Int>, modifier: Modifier) {
    val faixas = 11
    val contagem = IntArray(faixas)
    rpms.forEach { r -> contagem[((r - 500) / 500).coerceIn(0, faixas - 1)]++ }
    val maior = (contagem.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(modifier) {
        val larg = size.width / faixas
        contagem.forEachIndexed { i, c ->
            val inicio = 500 + i * 500
            val cor = when { inicio in 1500..2499 -> Color(0xFF22C55E); inicio >= 4000 -> Color(0xFFEF4444); else -> Color(0xFF7C9CF5) }
            val h = size.height * c / maior
            drawRoundRect(cor, Offset(i * larg + larg * 0.12f, size.height - h), Size(larg * 0.76f, h), CornerRadius(3.dp.toPx()))
        }
    }
}
