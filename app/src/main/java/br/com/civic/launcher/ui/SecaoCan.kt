package br.com.civic.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.obd.MapaCan
import br.com.civic.launcher.obd.ObdMonitor
import br.com.civic.launcher.obd.SinaisCan
import br.com.civic.launcher.obd.SinalCan

/**
 * Luzes-espia pelo CAN (experimental). Fluxo: escolha a luz, capture com ela apagada, capture com ela acesa,
 * e o app mostra quais bits mudaram. Se a tomada OBD do carro não deixar passar esses quadros, nada aparece
 * e o recurso pode ser descontinuado.
 */
@Composable
fun SecaoLuzesCan() {
    val ctx = LocalContext.current
    val obd = remember { ObdMonitor.obter(ctx) }
    val mapa = remember { MapaCan.obter(ctx) }
    var sinal by remember { mutableStateOf(SinalCan.Farol) }
    val candidatos = remember(obd.capturaBase, obd.capturaLigado) { SinaisCan.candidatos(obd.capturaBase, obd.capturaLigado) }

    Secao("Luzes-espia pelo CAN (experimental)") {
        Text(
            "Com o adaptador conectado e a ignição ligada: escolha a luz, capture com ela apagada e depois acesa (5 s cada).",
            color = Cores.TextoSuave, fontSize = 15.sp,
        )
        SinalCan.entries.chunked(3).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                linha.forEach { s -> Opcao(s.rotulo, sinal == s, Modifier.weight(1f)) { sinal = s } }
            }
        }
        BotaoPilula("1. Capturar com ${sinal.rotulo} APAGADO", Icons.Filled.Lightbulb, Modifier.fillMaxWidth()) { obd.capturarCan(ligado = false) }
        BotaoPilula("2. Capturar com ${sinal.rotulo} ACESO", Icons.Filled.Lightbulb, Modifier.fillMaxWidth()) { obd.capturarCan(ligado = true) }
        obd.situacaoCan?.let { Text(it, color = Cores.Texto, fontSize = 15.sp) }
        candidatos.forEach { c ->
            BotaoPilula("Usar: ${c.texto}", Icons.Filled.Lightbulb, Modifier.fillMaxWidth()) { mapa.definir(sinal, c.regra) }
        }
        mapa.regras[sinal]?.let { r ->
            Text("${sinal.rotulo}: ID ${r.id} · byte ${r.posicao} · máscara %02X → %02X".format(r.mascara, r.valor), color = Cores.Ok, fontSize = 15.sp)
            BotaoPilula("Remover ${sinal.rotulo}", Icons.Filled.Lightbulb, Modifier.fillMaxWidth()) { mapa.remover(sinal) }
        }
    }
}
