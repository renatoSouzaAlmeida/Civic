package br.com.civic.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Configuracoes
import br.com.civic.launcher.Tpms
import java.util.Locale

private val BRL = Locale("pt", "BR")

/** Texto de pressão na unidade escolhida (psi ou bar). */
private fun pressao(l: Tpms.Leitura, emBar: Boolean): String =
    if (l.pressaoKpa == null) "—" else if (emBar) "%.1f bar".format(BRL, l.pressaoKpa / 100f) else "%.0f psi".format(BRL, l.psi ?: 0f)

/** Cor da pressão em relação ao ideal: verde no normal, âmbar a menos de 85% ou mais de 125%. */
@Composable
private fun corPressao(l: Tpms.Leitura, idealPsi: Int): Color {
    val psi = l.psi ?: return Cores.TextoSuave
    return if (psi < idealPsi * 0.85f || psi > idealPsi * 1.25f) Cores.Vermelho else Cores.Ok
}

/** Config.: TPMS (pressão dos pneus), desligado por padrão. */
@Composable
fun SecaoTpms() {
    val ctx = LocalContext.current
    val config = remember { Configuracoes.obter(ctx) }
    val tpms = remember { Tpms.obter(ctx) }
    @Suppress("UNUSED_VARIABLE") val v = tpms.versao
    Secao("TPMS (pressão dos pneus) · experimental") {
        Text("Lê sensores de pneu Bluetooth (BLE) externos, como os de tampinha de válvula. Deixe desligado até instalar os sensores. O formato do anúncio varia por fabricante: aqui aparecem os dados brutos de cada sensor achado para ajustar o leitor ao seu modelo.", color = Cores.TextoSuave, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Opcao("Desativado", !config.tpmsAtivo, Modifier.weight(1f)) { config.salvarTpmsAtivo(false); tpms.sincronizar() }
            Opcao("Ativado", config.tpmsAtivo, Modifier.weight(1f)) { config.salvarTpmsAtivo(true); tpms.sincronizar() }
        }
        if (!config.tpmsAtivo) return@Secao

        Text(if (tpms.procurando) "Procurando sensores… (com o carro parado, eles transmitem pouco: pode demorar ou só aparecer rodando)" else "Sem varredura: ligue o Bluetooth e libere a permissão de dispositivos próximos.",
            color = Cores.TextoSuave, fontSize = 13.sp)

        // Pneus atribuídos.
        Tpms.POSICOES.forEach { (pos, nome) ->
            val l = tpms.leitura(pos)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cores.Chip).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pos, color = Cores.Destaque, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(nome.replaceFirstChar { it.uppercase() }, color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            tpms.macDe(pos) == null -> "sem sensor atribuído"
                            l == null -> "aguardando leitura"
                            else -> "${l.tempC?.let { "%.0f °C".format(BRL, it) } ?: "—"} · bateria ${l.bateria ?: "—"}% · há ${(System.currentTimeMillis() - l.em) / 1000} s"
                        }, color = Cores.TextoSuave, fontSize = 12.sp,
                    )
                }
                if (l != null) Text(pressao(l, config.tpmsEmBar), color = corPressao(l, config.tpmsIdealPsi), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                if (tpms.macDe(pos) != null) Icon(Icons.Filled.Close, "Tirar sensor", tint = Cores.TextoSuave, modifier = Modifier.padding(start = 10.dp).size(26.dp).toqueSuave { tpms.limpar(pos) })
            }
        }

        // Sensores encontrados: toque na posição para atribuir.
        Text("Sensores encontrados (${tpms.encontrados.size})", color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (tpms.encontrados.isEmpty()) Text("Nenhum ainda.", color = Cores.TextoSuave, fontSize = 13.sp)
        tpms.encontrados.values.sortedByDescending { it.rssi }.forEach { l ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cores.Chip).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${l.nome.ifEmpty { "(sem nome)" }} · ${l.mac} · sinal ${l.rssi} dBm" + (tpms.posicaoDe(l.mac)?.let { " · atribuído a $it" } ?: ""), color = Cores.Texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (l.plausivel) "leitura: ${pressao(l, config.tpmsEmBar)} · ${"%.0f".format(BRL, l.tempC ?: 0f)} °C · bateria ${l.bateria ?: "—"}%" else "formato não reconhecido (veja os dados brutos)",
                    color = if (l.plausivel) Cores.Ok else Cores.Amarelo, fontSize = 13.sp,
                )
                Text("dados: ${l.hex}", color = Cores.TextoSuave, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tpms.POSICOES.forEach { (pos, _) -> Opcao(pos, tpms.posicaoDe(l.mac) == pos, Modifier.weight(1f)) { tpms.atribuir(pos, l.mac) } }
                }
            }
        }

        Text("Pressão ideal dos pneus (Civic G9: 32 psi). Avisa abaixo de 85% ou acima de 125% do ideal, ou acima de 85 °C.", color = Cores.TextoSuave, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(28, 30, 32, 35, 36).forEach { p -> Opcao("$p psi", config.tpmsIdealPsi == p, Modifier.weight(1f)) { config.salvarTpmsIdeal(p) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Unidade", color = Cores.Texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Opcao("psi", !config.tpmsEmBar, Modifier.width(90.dp)) { config.salvarTpmsBar(false) }
            Opcao("bar", config.tpmsEmBar, Modifier.width(90.dp)) { config.salvarTpmsBar(true) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Listar todos os aparelhos BLE com dados (para achar sensor de outro modelo)", color = Cores.Texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Opcao("Sim", config.tpmsMostrarTodos, Modifier.width(80.dp)) { config.salvarTpmsTodos(true) }
            Opcao("Não", !config.tpmsMostrarTodos, Modifier.width(80.dp)) { config.salvarTpmsTodos(false) }
        }
    }
}

/** Veículo > Viagem: os quatro pneus (só aparece com o TPMS ativado e algum sensor atribuído). */
@Composable
fun CartaoPneus() {
    val ctx = LocalContext.current
    val config = remember { Configuracoes.obter(ctx) }
    val tpms = remember { Tpms.obter(ctx) }
    @Suppress("UNUSED_VARIABLE") val v = tpms.versao
    if (!config.tpmsAtivo || Tpms.POSICOES.none { tpms.macDe(it.first) != null }) return
    Secao("Pneus (TPMS)") {
        listOf(listOf("DE", "DD"), listOf("TE", "TD")).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                linha.forEach { pos ->
                    val l = tpms.leitura(pos)
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Cores.Chip).padding(12.dp)) {
                        Text(pos, color = Cores.TextoSuave, fontSize = 13.sp)
                        Text(if (l == null) "—" else pressao(l, config.tpmsEmBar), color = if (l == null) Cores.TextoSuave else corPressao(l, config.tpmsIdealPsi), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(l?.tempC?.let { "%.0f °C".format(BRL, it) } ?: "sem leitura", color = Cores.TextoSuave, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
