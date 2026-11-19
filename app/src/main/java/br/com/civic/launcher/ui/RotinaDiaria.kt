package br.com.civic.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.CentralNotificacoes
import br.com.civic.launcher.Rotina
import br.com.civic.launcher.Rotinas
import br.com.civic.launcher.navegarPara
import kotlinx.coroutines.delay
import java.util.Calendar

/** Config.: cadastro das sugestões de rotina (período, local e dias). */
@Composable
internal fun RotinaDiariaConfig() {
    val ctx = LocalContext.current
    val rotinas = remember { Rotinas.obter(ctx) }
    var editando by remember { mutableStateOf<Rotina?>(null) }
    var novo by remember { mutableStateOf(false) }
    Secao("Rotina diária (sugestões de destino)") {
        Text(
            "Cadastre período e local. No horário, o app sugere ir ao destino com um toque. A sugestão aparece ${Rotinas.ANTECEDENCIA_MIN} min antes do início.",
            color = Cores.TextoSuave, fontSize = 13.sp,
        )
        if (rotinas.itens.isEmpty()) Text("Nenhuma rotina cadastrada.", color = Cores.Texto, fontSize = 15.sp)
        rotinas.itens.forEach { r ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cores.Chip).clickable { editando = r }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.Schedule, null, tint = Cores.Destaque, modifier = Modifier.size(26.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.destino, color = Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(r.periodo + " · " + diasTexto(r.dias), color = Cores.TextoSuave, fontSize = 13.sp)
                }
                Icon(Icons.Filled.Edit, "Editar", tint = Cores.TextoSuave, modifier = Modifier.size(22.dp))
            }
        }
        BotaoPilula("Adicionar rotina", Icons.Filled.Add, Modifier.fillMaxWidth()) { novo = true }
    }
    if (novo || editando != null) DialogoRotina(editando, rotinas) { novo = false; editando = null }
}

private fun diasTexto(dias: Int): String = when {
    dias == Rotina.DIAS_UTEIS -> "Seg a Sex"
    dias == 0b1111111 -> "Todos os dias"
    else -> Rotina.NOMES_DIAS.filterIndexed { i, _ -> dias and (1 shl i) != 0 }.joinToString(" ")
}

@Composable
private fun DialogoRotina(rotina: Rotina?, rotinas: Rotinas, aoFechar: () -> Unit) {
    var inicio by remember { mutableStateOf(rotina?.let { "%02d%02d".format(it.inicioMin / 60, it.inicioMin % 60) } ?: "") }
    var fim by remember { mutableStateOf(rotina?.let { "%02d%02d".format(it.fimMin / 60, it.fimMin % 60) } ?: "") }
    var destino by remember { mutableStateOf(rotina?.destino ?: "") }
    var dias by remember { mutableIntStateOf(rotina?.dias ?: Rotina.DIAS_UTEIS) }
    var erro by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (rotina == null) "Nova rotina" else "Editar rotina") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Campo("Início", inicio, Modifier.weight(1f), mascara = Mascara.Hora) { inicio = it }
                    Campo("Fim", fim, Modifier.weight(1f), mascara = Mascara.Hora) { fim = it }
                }
                Campo("Local (nome ou endereço)", destino) { destino = it.take(120) }
                Text("Dias da semana", color = Cores.TextoSuave, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Rotina.NOMES_DIAS.forEachIndexed { i, nome ->
                        val ligado = dias and (1 shl i) != 0
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (ligado) Cores.Destaque else Cores.Chip)
                                .clickable { dias = dias xor (1 shl i) }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(nome, color = if (ligado) Color.White else Cores.Texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    }
                }
                erro?.let { Text(it, color = Cores.Vermelho, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ini = Mascara.minutos(inicio)
                val f = Mascara.minutos(fim)
                erro = when {
                    ini == null -> "Início inválido: digite 4 números (ex.: 0710)."
                    f == null -> "Fim inválido: digite 4 números (ex.: 0730)."
                    f <= ini -> "O fim precisa ser depois do início."
                    destino.isBlank() -> "Informe o local."
                    dias == 0 -> "Marque pelo menos um dia."
                    else -> null
                }
                if (erro == null) {
                    rotinas.salvar(Rotina(rotina?.id ?: System.currentTimeMillis(), ini!!, f!!, destino.trim(), dias))
                    aoFechar()
                }
            }) { Text("Salvar", fontSize = 18.sp) }
        },
        dismissButton = {
            Row {
                if (rotina != null) TextButton(onClick = { rotinas.remover(rotina); aoFechar() }) { Text("Apagar", fontSize = 18.sp) }
                TextButton(onClick = aoFechar) { Text("Cancelar", fontSize = 18.sp) }
            }
        },
    )
}

/**
 * Faixa de sugestão no topo do app: aparece no período de uma rotina (com a antecedência) com o botão "Ir agora".
 * O X esconde a sugestão até o fim do período daquele dia. Cada sugestão do dia entra uma vez no sininho.
 */
@Composable
fun BannerRotina(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val rotinas = remember { Rotinas.obter(ctx) }
    var agora by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) { while (true) { agora = Calendar.getInstance(); delay(20_000) } }
    val dispensadas = remember { mutableStateListOf<String>() }
    val hoje = "${agora.get(Calendar.YEAR)}${agora.get(Calendar.DAY_OF_YEAR)}"
    val r = rotinas.ativas(agora).firstOrNull { "${it.id}-$hoje" !in dispensadas } ?: return
    LaunchedEffect(r.id, hoje) {
        CentralNotificacoes.obter(ctx).registrarUmaVez("rotina_${r.id}_$hoje", "rotina", "🕖 Rotina: ${r.destino} (${r.periodo})")
    }
    val forma = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth().clip(forma).background(Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)))).border(1.dp, Color(0xE6AAD2FF), forma)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Schedule, null, tint = Color.White, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text("Rotina: ${r.destino}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(r.periodo, color = Color(0xFFDBEAFE), fontSize = 13.sp)
        }
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { ctx.navegarPara(r.destino); dispensadas.add("${r.id}-$hoje") }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Navigation, null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(20.dp))
            Text("Ir agora", color = Color(0xFF1D4ED8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Icon(Icons.Filled.Close, "Dispensar", tint = Color.White, modifier = Modifier.size(28.dp).clickable { dispensadas.add("${r.id}-$hoje") })
    }
}
