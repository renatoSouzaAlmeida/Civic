package br.com.civic.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.obd.MapaSaude
import br.com.civic.launcher.obd.NivelSaude
import br.com.civic.launcher.obd.ObdMonitor

/** Mapa de saúde do veículo: um cartão por subsistema com estado, dado observado, fonte, confiança e idade do dado. Atualiza a cada 3 s. */
@Composable
fun TelaSaude(obd: ObdMonitor) {
    val ctx = LocalContext.current
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { agora = System.currentTimeMillis(); kotlinx.coroutines.delay(3_000) } }
    val itens = MapaSaude.montar(ctx.applicationContext, obd, agora)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val alertas = itens.count { it.nivel == NivelSaude.Alerta }
        val atencao = itens.count { it.nivel == NivelSaude.Atencao }
        val semDados = itens.count { it.nivel == NivelSaude.SemDados }
        Secao("Saúde do veículo") {
            Text(
                when { alertas > 0 -> "$alertas subsistema(s) em alerta" + if (atencao > 0) " e $atencao em atenção" else ""; atencao > 0 -> "$atencao subsistema(s) em atenção"; semDados == itens.size -> "Sem dados ainda: conecte o adaptador OBD" else -> "Nada fora do normal nos dados disponíveis" },
                color = if (alertas > 0) Cores.Vermelho else if (atencao > 0) Cores.Amarelo else if (semDados == itens.size) Cores.TextoSuave else Cores.Ok,
                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
            )
            if (semDados > 0 && semDados < itens.size) Text("$semDados subsistema(s) sem dados: não significam \"normal\".", color = Cores.TextoSuave, fontSize = 13.sp)
        }
        itens.forEach { s ->
            val cor = when (s.nivel) { NivelSaude.Alerta -> Cores.Vermelho; NivelSaude.Atencao -> Cores.Amarelo; NivelSaude.Ok -> Cores.Ok; NivelSaude.SemDados -> Cores.TextoSuave }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Cores.Chip).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(14.dp).clip(RoundedCornerShape(7.dp)).background(cor))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.nome, color = Cores.Texto, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(s.nivel.rotulo, color = cor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(s.observado, color = Cores.Texto, fontSize = 14.sp)
                    Text("Fonte: ${s.fonte} · confiança ${s.confianca}" + (s.idade?.let { " · $it" } ?: ""), color = Cores.TextoSuave, fontSize = 12.sp)
                }
            }
        }
        val leituras = obd.leituras(agora)
        Secao("Qualidade dos dados do OBD") {
            Text("Cada leitura mostra de onde vem, quando foi lida e se ainda vale. \"Atrasada\" quer dizer que o último valor é antigo: não use como medição de agora.", color = Cores.TextoSuave, fontSize = 13.sp)
            leituras.forEach { l ->
                val cor = when (l.estado) { br.com.civic.launcher.obd.EstadoLeitura.Atual -> Cores.Ok; br.com.civic.launcher.obd.EstadoLeitura.Atrasada -> Cores.Amarelo; else -> Cores.TextoSuave }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(l.campo, color = Cores.Texto, fontSize = 14.sp, modifier = Modifier.weight(1.2f))
                    Text(if (l.valor != null) "${l.valor} ${l.unidade}" else "—", color = Cores.Texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${l.fonte.rotulo} · ${br.com.civic.launcher.obd.QualidadeDados.idade(l.em, agora)}", color = Cores.TextoSuave, fontSize = 12.sp, modifier = Modifier.weight(1.3f))
                    Text(l.estado.rotulo, color = cor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(92.dp))
                }
            }
            val total = obd.descartadas.values.sum()
            if (total > 0) Text("$total leitura(s) impossível(is) descartada(s): ${obd.descartadas.entries.joinToString(", ") { "${it.key} ×${it.value}" }}.", color = Cores.Amarelo, fontSize = 12.sp)
        }
        Text("Resumo do que o app mede. É indício para conversar com a oficina, não diagnóstico: um subsistema \"normal\" só vale para o que foi medido.", color = Cores.TextoSuave, fontSize = 12.sp)
    }
}
