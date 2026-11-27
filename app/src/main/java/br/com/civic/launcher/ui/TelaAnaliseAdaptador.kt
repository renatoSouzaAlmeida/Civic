package br.com.civic.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.obd.AnaliseAdaptador
import br.com.civic.launcher.obd.ObdMonitor
import br.com.civic.launcher.obd.PassoAnalise

/**
 * Análise do adaptador: varredura do que o carro informa (PIDs, marcha, hodômetro, VIN) e teste guiado do CAN
 * (setas, faróis, freio, marchas P/R/N/D, porta, cinto), com o que mudou em cada passo e envio do relatório para a nuvem.
 */
@Composable
fun TelaAnaliseAdaptador(obd: ObdMonitor) {
    val ctx = LocalContext.current
    val a = remember { AnaliseAdaptador.obter(ctx) }
    var passoAberto by remember { mutableStateOf<PassoAnalise?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        a.situacao?.let { Text(it, color = if (a.ocupado) Cores.Destaque else Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotaoPilula("Enviar relatório", Icons.Filled.CloudUpload, Modifier.weight(1f)) { a.enviar() }
            BotaoPilula("Limpar", Icons.Filled.DeleteSweep) { a.limpar() }
        }

        Secao("1. Varredura (o que o carro informa)") {
            Text("Ignição ligada (motor pode estar desligado). O app pergunta todos os PIDs suportados, a marcha (01A4), o hodômetro (01A6), o VIN e testa o tráfego CAN.", color = Cores.TextoSuave, fontSize = 15.sp)
            BotaoPilula(if (a.ocupado) "Aguarde…" else "Rodar varredura", Icons.Filled.Search, Modifier.fillMaxWidth()) { obd.pedirAnalise("varredura") }
            a.varredura.forEach { l ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(l.codigo, color = Cores.TextoSuave, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(64.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l.nome + (l.valor?.let { ": $it" } ?: ""), color = if (l.valor == "sem resposta") Cores.TextoSuave else Cores.Texto, fontSize = 15.sp)
                        if (l.bruto.isNotEmpty()) Text(l.bruto, color = Cores.TextoSuave, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Secao("2. Teste guiado (setas, faróis, marcha…)") {
            Text("Comece pela Base (tudo desligado, em P). Depois, em cada passo, faça o que ele pede e toque: o app escuta o CAN por 5 s e mostra os bytes que mudaram em relação à base. Ao terminar, envie o relatório.", color = Cores.TextoSuave, fontSize = 15.sp)
            PassoAnalise.entries.chunked(3).forEach { linha ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    linha.forEach { p ->
                        val feito = a.capturas[p] != null
                        Opcao((if (feito) "✓ " else "") + p.rotulo, passoAberto == p, Modifier.weight(1f)) { passoAberto = p }
                    }
                    repeat(3 - linha.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            passoAberto?.let { p ->
                Text(p.instrucao, color = Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                BotaoPilula(if (a.ocupado) "Aguarde…" else "Capturar \"${p.rotulo}\" (5 s)", Icons.Filled.Hearing, Modifier.fillMaxWidth()) { obd.pedirAnalise(p.chave) }
                val cap = a.capturas[p]
                if (cap != null) {
                    Text("${cap.size} IDs CAN capturados.", color = Cores.TextoSuave, fontSize = 14.sp)
                    if (p != PassoAnalise.Base) {
                        val dif = remember(a.capturas[PassoAnalise.Base], cap) { a.diferencas(p) }
                        when {
                            a.capturas[PassoAnalise.Base] == null -> Text("Capture a Base primeiro para comparar.", color = Cores.TextoSuave, fontSize = 14.sp)
                            dif.isEmpty() -> Text("Nada mudou em relação à base.", color = Cores.TextoSuave, fontSize = 14.sp)
                            else -> dif.take(30).forEach { Text(it, color = Cores.Ok, fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
                        }
                    }
                }
            }
        }
    }
}
