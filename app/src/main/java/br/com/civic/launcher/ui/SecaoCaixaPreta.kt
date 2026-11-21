package br.com.civic.launcher.ui

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.CaixaPreta
import br.com.civic.launcher.Configuracoes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BRP = Locale("pt", "BR")

/** Config.: caixa-preta digital (diário técnico de eventos: frenagem brusca, possível colisão, SOS e alerta crítico). */
@Composable
fun SecaoCaixaPreta() {
    val ctx = LocalContext.current
    val config = remember { Configuracoes.obter(ctx) }
    val cp = remember { CaixaPreta.obter(ctx) }
    var aberto by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm:ss", BRP) }
    Secao("Caixa-preta digital") {
        Text("Guarda os últimos segundos de velocidade, rpm, posição e aceleração e, quando acontece uma frenagem brusca, uma possível colisão, um SOS ou um alerta crítico, salva o antes e o depois, com a origem de cada dado (OBD ou GPS), e envia à Casa Tech. É um diário técnico: não é caixa-preta certificada nem substitui o registrador de eventos de um carro homologado.", color = Cores.TextoSuave, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Opcao("Ligada", config.caixaPretaAtiva, Modifier.weight(1f)) { config.salvarCaixaPreta(true) }
            Opcao("Desligada", !config.caixaPretaAtiva, Modifier.weight(1f)) { config.salvarCaixaPreta(false) }
        }
        if (config.caixaPretaAtiva) {
            Text("Segundos guardados ANTES do evento", color = Cores.Texto, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { v -> Opcao("$v s", config.caixaPretaPreS == v, Modifier.weight(1f)) { config.salvarCaixaPretaJanelas(v, config.caixaPretaPosS) } }
            }
            Text("Segundos gravados DEPOIS do evento", color = Cores.Texto, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15, 30).forEach { v -> Opcao("$v s", config.caixaPretaPosS == v, Modifier.weight(1f)) { config.salvarCaixaPretaJanelas(config.caixaPretaPreS, v) } }
            }
            Text("Localização: a posição entra só nos eventos e fica no seu aparelho e na sua Casa Tech. Guarda os 30 últimos eventos no aparelho.", color = Cores.TextoSuave, fontSize = 12.sp)
        }
        BotaoPilula("Testar a captura (evento de teste)", Icons.Filled.Notifications, Modifier.fillMaxWidth()) { cp.disparar("teste", "teste manual em Config.") }
        Text("Eventos salvos (${cp.eventos.size})", color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (cp.eventos.isEmpty()) Text("Nenhum ainda. Eles aparecem aqui quando algo acontece.", color = Cores.TextoSuave, fontSize = 13.sp)
        cp.eventos.forEach { e ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cores.Chip).clickable { aberto = e.id }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Videocam, null, tint = if (e.motivo.contains("colisao") || e.motivo.contains("sos")) Cores.Vermelho else Cores.Destaque, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(CaixaPreta.rotuloMotivo(e.motivo) + " · " + fmt.format(Date(e.t)), color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    val r = e.resumo
                    Text(buildString {
                        if (!r.isNull("vel_antes")) append("de %.0f km/h".format(BRP, r.getDouble("vel_antes")))
                        if (!r.isNull("vel_min")) append(" até %.0f km/h".format(BRP, r.getDouble("vel_min")))
                        if (!r.isNull("g_max")) append(" · pico %.2f g".format(BRP, r.getDouble("g_max")))
                        append(if (e.enviado) " · enviado" else " · aguardando envio")
                    }, color = Cores.TextoSuave, fontSize = 12.sp)
                }
            }
        }
        if (cp.eventos.isNotEmpty()) TextButton(onClick = { cp.apagarTudo() }) { Text("Apagar todos os eventos do aparelho", fontSize = 14.sp) }
    }
    aberto?.let { id -> DetalheCaixaPreta(id, fmt) { aberto = null } }
}

@Composable
private fun DetalheCaixaPreta(id: String, fmt: SimpleDateFormat, aoFechar: () -> Unit) {
    val ctx = LocalContext.current
    val cp = remember { CaixaPreta.obter(ctx) }
    val o = remember(id) { cp.lerEvento(id) }
    if (o == null) { aoFechar(); return }
    val am = o.getJSONArray("amostras")
    val velocidades = remember(id) { (0 until am.length()).mapNotNull { i -> am.getJSONObject(i).takeIf { it.has("v") }?.getDouble("v")?.toFloat() } }
    val rpms = remember(id) { (0 until am.length()).mapNotNull { i -> am.getJSONObject(i).takeIf { it.has("r") }?.getInt("r")?.toFloat() } }
    val r = o.getJSONObject("resumo")
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(CaixaPreta.rotuloMotivo(o.getString("motivo")) + " · " + fmt.format(Date(o.getLong("t"))), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (o.optString("detalhe").isNotEmpty()) Text(o.getString("detalhe"), fontSize = 15.sp)
                Text("Velocidade (km/h), de ${o.getInt("pre_s")} s antes a ${o.getInt("pos_s")} s depois", color = Cores.TextoSuave, fontSize = 12.sp)
                if (velocidades.size >= 2) Sparkline(velocidades, Cores.Destaque, Modifier.fillMaxWidth().height(70.dp)) else Text("Sem velocidade registrada.", fontSize = 13.sp)
                if (rpms.size >= 2) { Text("Rotação (rpm)", color = Cores.TextoSuave, fontSize = 12.sp); Sparkline(rpms, Cores.Amarelo, Modifier.fillMaxWidth().height(50.dp)) }
                Text("Desaceleração máxima %.1f km/h por segundo".format(BRP, r.optDouble("desaceleracao_max_kmh_s", 0.0)), fontSize = 14.sp)
                if (!r.isNull("g_max")) Text("Pico de aceleração do aparelho: %.2f g".format(BRP, r.getDouble("g_max")), fontSize = 14.sp)
                Text("Origem da velocidade: ${(0 until r.getJSONArray("fontes").length()).joinToString(", ") { r.getJSONArray("fontes").getString(it).uppercase() }.ifEmpty { "—" }} · ${r.optDouble("frequencia_hz")} amostras/s · ${r.optInt("lacunas_s")} s sem dado", color = Cores.TextoSuave, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = aoFechar) { Text("Fechar", fontSize = 18.sp, fontWeight = FontWeight.Bold) } },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Caixa-preta Civic Renatin").putExtra(Intent.EXTRA_TEXT, o.toString(2)), "Exportar evento").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Exportar", fontSize = 16.sp) }
                TextButton(onClick = { cp.apagar(id); aoFechar() }) { Text("Apagar", fontSize = 16.sp) }
            }
        },
    )
}
