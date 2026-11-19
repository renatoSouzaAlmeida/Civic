package br.com.civic.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.obd.CorrecaoCombustivel
import br.com.civic.launcher.obd.ObdMonitor
import br.com.civic.launcher.obd.SituacaoMistura
import java.util.Locale
import kotlin.math.abs

private val BRM = Locale("pt", "BR")
private fun pct(v: Float?) = v?.let { "%+.1f%%".format(BRM, it) } ?: "—"
private fun corPct(v: Float?): Color = when {
    v == null -> Color(0xFF8FA3BF)
    abs(v) > 10f -> Color(0xFFEF4444)
    abs(v) > 5f -> Color(0xFFFFB020)
    else -> Color(0xFF22C55E)
}

/**
 * Correção de combustível: valores agora (curta, longa e total), médias por regime (marcha lenta / rodando),
 * diagnóstico com causas prováveis e histórico diário.
 */
@Composable
fun TelaMistura(obd: ObdMonitor) {
    val ctx = LocalContext.current
    val cc = remember { CorrecaoCombustivel.obter(ctx.applicationContext) }
    val d = obd.dados
    val total = if (d.stft != null && d.ltft != null) d.stft + d.ltft else null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Secao("Agora") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ValorMistura("Correção curta (STFT)", pct(d.stft), corPct(d.stft), Modifier.weight(1f))
                ValorMistura("Correção longa (LTFT)", pct(d.ltft), corPct(d.ltft), Modifier.weight(1f))
                ValorMistura("Total", pct(total), corPct(total), Modifier.weight(1f))
            }
            BarraCorrecao(total, Modifier.fillMaxWidth().height(34.dp))
            Text(
                when {
                    d.stft == null -> "Sem leitura: conecte o adaptador (ou use a demonstração) com o motor ligado."
                    !cc.leituraValida -> "Aguardando o motor esquentar (70 °C) e a injeção entrar em correção para valer no diagnóstico."
                    else -> "Positivo = injeção colocando mais combustível (mistura pobre). Negativo = tirando (mistura rica)."
                },
                color = Cores.TextoSuave, fontSize = 13.sp,
            )
        }
        Secao("Diagnóstico") {
            val cor = when (cc.situacao) {
                SituacaoMistura.Normal -> Color(0xFF22C55E)
                SituacaoMistura.Atencao -> Color(0xFFFFB020)
                SituacaoMistura.Pobre, SituacaoMistura.Rica -> Color(0xFFEF4444)
                SituacaoMistura.SemDados -> Cores.TextoSuave
            }
            Text(cc.situacao.rotulo, color = cor, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ValorMistura("Média em marcha lenta", pct(cc.mediaLenta), corPct(cc.mediaLenta), Modifier.weight(1f))
                ValorMistura("Média rodando", pct(cc.mediaRodando), corPct(cc.mediaRodando), Modifier.weight(1f))
            }
            if (cc.situacao == SituacaoMistura.SemDados) {
                Text("O diagnóstico aparece depois de alguns minutos com o motor quente, parado em marcha lenta e rodando.", color = Cores.TextoSuave, fontSize = 14.sp)
            } else if (cc.causas.isEmpty()) {
                Text("A injeção está corrigindo pouco: mistura dentro do normal (até ±5%).", color = Cores.Texto, fontSize = 15.sp)
            } else {
                Text("Causas prováveis:", color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                cc.causas.forEach { Text("• $it", color = Cores.Texto, fontSize = 15.sp) }
            }
            Text(
                "Faixas: até ±5% normal · ±5 a ±10% atenção · acima de ±10% problema provável. " +
                    "No flex, depois de trocar de combustível a injeção leva uns 20 km para reaprender.",
                color = Cores.TextoSuave, fontSize = 13.sp,
            )
        }
        Secao("Histórico por dia (${cc.historico.size})") {
            if (cc.historico.isEmpty()) Text("As médias diárias aparecem aqui.", color = Cores.TextoSuave, fontSize = 15.sp)
            cc.historico.forEach { dia ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cores.Chip).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val partes = dia.dia.split("-")
                    Text(if (partes.size == 3) "${partes[2]}/${partes[1]}" else dia.dia, color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("marcha lenta ${pct(dia.lenta?.toFloat())}", color = corPct(dia.lenta?.toFloat()), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("rodando ${pct(dia.rodando?.toFloat())}", color = corPct(dia.rodando?.toFloat()), fontSize = 14.sp, modifier = Modifier.weight(1f))
                }
            }
            if (cc.historico.isNotEmpty()) TextButton(onClick = { cc.limpar() }) { Text("Limpar histórico", fontSize = 15.sp) }
        }
    }
}

@Composable
private fun ValorMistura(rotulo: String, valor: String, cor: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Cores.Chip).padding(12.dp)) {
        Text(rotulo, color = Cores.TextoSuave, fontSize = 13.sp, maxLines = 1)
        Text(valor, color = cor, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** Barra de -25% a +25% com faixas verde (±5), âmbar (±10) e vermelha, e o marcador do valor atual. */
@Composable
private fun BarraCorrecao(total: Float?, modifier: Modifier) {
    val trilho = Cores.Chip
    val texto = Cores.Texto
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun x(v: Float) = w * ((v.coerceIn(-25f, 25f) + 25f) / 50f)
        drawRoundRect(trilho, cornerRadius = CornerRadius(h / 2, h / 2))
        listOf(Triple(-25f, -10f, Color(0x55EF4444)), Triple(-10f, -5f, Color(0x55FFB020)), Triple(-5f, 5f, Color(0x5522C55E)),
            Triple(5f, 10f, Color(0x55FFB020)), Triple(10f, 25f, Color(0x55EF4444))).forEach { (a, b, c) ->
            drawRect(c, Offset(x(a), h * 0.2f), Size(x(b) - x(a), h * 0.6f))
        }
        drawLine(texto.copy(alpha = 0.5f), Offset(x(0f), 0f), Offset(x(0f), h), 2f)
        total?.let { drawCircle(corPct(it), h * 0.42f, Offset(x(it), h / 2)) }
    }
}
