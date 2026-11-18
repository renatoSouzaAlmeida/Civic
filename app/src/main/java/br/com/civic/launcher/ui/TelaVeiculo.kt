package br.com.civic.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Abastecimento
import br.com.civic.launcher.Combustivel
import br.com.civic.launcher.ItemManutencao
import br.com.civic.launcher.SituacaoManutencao
import br.com.civic.launcher.Veiculo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BR = Locale("pt", "BR")
private fun reais(v: Float) = "R$ %,.2f".format(BR, v)
private fun numero(texto: String): Float? = texto.trim().replace(".", "").replace(",", ".").toFloatOrNull()
private fun numeroDecimal(texto: String): Float? = texto.trim().replace(",", ".").toFloatOrNull()
private val formatoData = SimpleDateFormat("dd/MM/yyyy", BR)

/** Veículo: abastecimentos (consumo real, gastos, etanol × gasolina) e manutenção (lembretes por km e tempo). */
@Composable
fun TelaVeiculo() {
    var aba by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 520.dp)) {
            Opcao("Abastecimentos", aba == 0, Modifier.weight(1f)) { aba = 0 }
            Opcao("Manutenção", aba == 1, Modifier.weight(1f)) { aba = 1 }
        }
        if (aba == 0) AbaAbastecimentos() else AbaManutencao()
    }
}

// ---------------- Abastecimentos ----------------

@Composable
private fun AbaAbastecimentos() {
    val v = Veiculo.obter(LocalContext.current)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val larga = maxWidth > 760.dp
        if (larga) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    NovoAbastecimento(v)
                    Resumo(v)
                    EtanolOuGasolina(v)
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { Historico(v) }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NovoAbastecimento(v); Resumo(v); EtanolOuGasolina(v); Historico(v)
            }
        }
    }
}

@Composable
private fun NovoAbastecimento(v: Veiculo) {
    var km by remember { mutableStateOf("") }
    var litros by remember { mutableStateOf("") }
    var preco by remember { mutableStateOf("") }
    var comb by remember { mutableStateOf(v.abastecimentos.lastOrNull()?.combustivel ?: Combustivel.Gasolina) }
    var cheio by remember { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }
    Secao("Novo abastecimento") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Campo("Km do painel", km, Modifier.weight(1f), KeyboardType.Number) { km = it.filter(Char::isDigit) }
            Campo("Litros", litros, Modifier.weight(1f), KeyboardType.Decimal) { litros = it }
            Campo("Preço por litro (R$)", preco, Modifier.weight(1f), KeyboardType.Decimal) { preco = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Combustivel.entries.forEach { c -> Opcao(c.rotulo, comb == c, Modifier.weight(1f)) { comb = c } }
            Opcao(if (cheio) "Tanque cheio ✓" else "Parcial", cheio, Modifier.weight(1f)) { cheio = !cheio }
        }
        erro?.let { Text(it, color = Cores.Vermelho, fontSize = 14.sp) }
        BotaoPilula("Salvar abastecimento", Icons.Filled.LocalGasStation, Modifier.fillMaxWidth()) {
            val k = km.toIntOrNull()
            val l = numeroDecimal(litros)
            val p = numeroDecimal(preco)
            erro = when {
                k == null || k <= 0 -> "Informe o km do painel."
                l == null || l <= 0f -> "Informe os litros."
                p == null || p <= 0f -> "Informe o preço por litro."
                v.abastecimentos.isNotEmpty() && k < (v.abastecimentos.maxOf { it.km }) -> "O km é menor que o do último abastecimento."
                else -> null
            }
            if (erro == null) {
                v.adicionar(Abastecimento(System.currentTimeMillis(), System.currentTimeMillis(), k!!, l!!, p!!, comb, cheio))
                km = ""; litros = ""; preco = ""
            }
        }
        Text("Dica: para o consumo real, encha o tanque até o desarme da bomba e marque \"Tanque cheio\".", color = Cores.TextoSuave, fontSize = 13.sp)
    }
}

@Composable
private fun Resumo(v: Veiculo) {
    Secao("Resumo") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Numero("Consumo médio", v.consumoMedio()?.let { "%.1f km/l".format(BR, it) } ?: "—", Modifier.weight(1f))
            Numero("Gasolina", v.consumoMedio(Combustivel.Gasolina)?.let { "%.1f km/l".format(BR, it) } ?: "—", Modifier.weight(1f))
            Numero("Etanol", v.consumoMedio(Combustivel.Etanol)?.let { "%.1f km/l".format(BR, it) } ?: "—", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Numero("Gasto no mês", reais(v.gastoNoMes()), Modifier.weight(1f))
            Numero("Custo por km", v.custoPorKm()?.let { reais(it) } ?: "—", Modifier.weight(1f))
            Numero("Km atual", if (v.kmAtual > 0) "%,d".format(BR, v.kmAtual) else "—", Modifier.weight(1f))
        }
        if (v.consumoMedio() == null) Text("O consumo aparece a partir do segundo abastecimento com tanque cheio.", color = Cores.TextoSuave, fontSize = 13.sp)
    }
}

@Composable
private fun Numero(rotulo: String, valor: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Cores.Chip).padding(12.dp)) {
        Text(rotulo, color = Cores.TextoSuave, fontSize = 13.sp, maxLines = 1)
        Text(valor, color = Cores.Texto, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun EtanolOuGasolina(v: Veiculo) {
    var etanol by remember { mutableStateOf("") }
    var gasolina by remember { mutableStateOf("") }
    val (razao, real) = v.razaoEtanolGasolina()
    Secao("Etanol ou gasolina?") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Campo("Etanol (R$/l)", etanol, Modifier.weight(1f), KeyboardType.Decimal) { etanol = it }
            Campo("Gasolina (R$/l)", gasolina, Modifier.weight(1f), KeyboardType.Decimal) { gasolina = it }
        }
        val e = numeroDecimal(etanol)
        val g = numeroDecimal(gasolina)
        if (e != null && g != null && e > 0f && g > 0f) {
            val proporcao = e / g
            val compensaEtanol = proporcao < razao
            Text(
                if (compensaEtanol) "Abasteça com ETANOL" else "Abasteça com GASOLINA",
                color = if (compensaEtanol) Cores.Ok else Cores.Destaque, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
            )
            Text("Etanol custa %.0f%% da gasolina; compensa abaixo de %.0f%%.".format(BR, proporcao * 100, razao * 100), color = Cores.Texto, fontSize = 15.sp)
        }
        Text(
            if (real) "Limite calculado com o consumo real deste carro (etanol ÷ gasolina)."
            else "Usando a regra de 70%. Registre abastecimentos com os dois combustíveis para usar o consumo real do seu carro.",
            color = Cores.TextoSuave, fontSize = 13.sp,
        )
    }
}

@Composable
private fun Historico(v: Veiculo) {
    val consumos = v.consumos()
    var apagar by remember { mutableStateOf<Abastecimento?>(null) }
    Secao("Histórico (${v.abastecimentos.size})") {
        if (v.abastecimentos.isEmpty()) Text("Nenhum abastecimento registrado ainda.", color = Cores.TextoSuave, fontSize = 15.sp)
        v.abastecimentos.asReversed().forEach { a ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cores.Chip).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${formatoData.format(Date(a.data))} · ${"%,d".format(BR, a.km)} km · ${a.combustivel.rotulo}", color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "%.2f l × %s = %s".format(BR, a.litros, reais(a.precoLitro), reais(a.total)) + (if (!a.tanqueCheio) " · parcial" else ""),
                        color = Cores.TextoSuave, fontSize = 13.sp,
                    )
                }
                consumos[a.id]?.let { Text("%.1f km/l".format(BR, it), color = Cores.Ok, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
                Icon(Icons.Filled.Delete, "Apagar", tint = Cores.TextoSuave, modifier = Modifier.size(26.dp).clickable { apagar = a })
            }
        }
    }
    apagar?.let { a ->
        AlertDialog(
            onDismissRequest = { apagar = null },
            title = { Text("Apagar abastecimento?") },
            text = { Text("${formatoData.format(Date(a.data))} · ${"%,d".format(BR, a.km)} km · ${reais(a.total)}") },
            confirmButton = { TextButton(onClick = { v.remover(a); apagar = null }) { Text("Apagar", fontSize = 18.sp) } },
            dismissButton = { TextButton(onClick = { apagar = null }) { Text("Cancelar", fontSize = 18.sp) } },
        )
    }
}

// ---------------- Manutenção ----------------

@Composable
private fun AbaManutencao() {
    val v = Veiculo.obter(LocalContext.current)
    var km by remember { mutableStateOf(if (v.kmManual > 0) v.kmManual.toString() else "") }
    var informando by remember { mutableStateOf<ItemManutencao?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Secao("Quilometragem") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Campo("Km atual do painel", km, Modifier.weight(1f), KeyboardType.Number) { km = it.filter(Char::isDigit) }
                BotaoPilula("Atualizar km", Icons.Filled.Speed, Modifier.weight(1f)) { km.toIntOrNull()?.let { v.salvarKmManual(it) } }
            }
            Text(
                "Km considerado: ${if (v.kmAtual > 0) "%,d".format(BR, v.kmAtual) else "—"}. Também é atualizado a cada abastecimento.",
                color = Cores.TextoSuave, fontSize = 13.sp,
            )
        }
        val ordem = listOf(SituacaoManutencao.Vencido, SituacaoManutencao.Proximo, SituacaoManutencao.SemRegistro, SituacaoManutencao.EmDia)
        Secao("Plano de manutenção") {
            v.manutencao.sortedBy { ordem.indexOf(v.situacao(it)) }.forEach { item ->
                LinhaManutencao(v, item, aoFeito = { v.registrarFeito(item) }, aoInformar = { informando = item })
            }
            Text("Intervalos de referência para o Civic G9 1.8 em uso urbano. Vale o que vencer primeiro: km ou tempo.", color = Cores.TextoSuave, fontSize = 13.sp)
        }
    }
    informando?.let { item -> DialogoUltimo(v, item) { informando = null } }
}

@Composable
private fun LinhaManutencao(v: Veiculo, item: ItemManutencao, aoFeito: () -> Unit, aoInformar: () -> Unit) {
    val s = v.situacao(item)
    val cor = when (s) {
        SituacaoManutencao.Vencido -> Cores.Vermelho
        SituacaoManutencao.Proximo -> Cores.Amarelo
        SituacaoManutencao.EmDia -> Cores.Ok
        SituacaoManutencao.SemRegistro -> Cores.TextoSuave
    }
    val intervalo = listOfNotNull(item.intervaloKm?.let { "a cada %,d km".format(BR, it) }, item.intervaloMeses?.let { "$it meses" }).joinToString(" ou ")
    val falta = buildList {
        v.kmRestante(item)?.let { add(if (it >= 0) "faltam %,d km".format(BR, it) else "passou %,d km".format(BR, -it)) }
        v.diasRestantes(item)?.let { add(if (it >= 0) "faltam $it dias" else "venceu há ${-it} dias") }
    }.joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cores.Chip).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(cor))
        Column(Modifier.weight(1f)) {
            Text(item.nome, color = Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                listOf(intervalo, s.rotulo + if (falta.isNotEmpty()) " · $falta" else "").joinToString(" · "),
                color = if (s == SituacaoManutencao.Vencido) Cores.Vermelho else Cores.TextoSuave, fontSize = 13.sp,
            )
            item.ultimaData?.let { Text("Último: ${formatoData.format(Date(it))}${item.ultimoKm?.let { k -> " · %,d km".format(BR, k) } ?: ""}", color = Cores.TextoSuave, fontSize = 12.sp) }
        }
        TextButton(onClick = aoInformar) { Text("Informar último", fontSize = 15.sp) }
        TextButton(onClick = aoFeito, enabled = v.kmAtual > 0) { Text("Feito hoje", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun DialogoUltimo(v: Veiculo, item: ItemManutencao, aoFechar: () -> Unit) {
    var km by remember { mutableStateOf(item.ultimoKm?.toString() ?: "") }
    var data by remember { mutableStateOf(item.ultimaData?.let { formatoData.format(Date(it)) } ?: "") }
    var erro by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Última vez: ${item.nome}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Campo("Km em que foi feito", km, Modifier, KeyboardType.Number) { km = it.filter(Char::isDigit) }
                Campo("Data (dd/mm/aaaa)", data, Modifier, KeyboardType.Number) { data = it }
                erro?.let { Text(it, color = Cores.Vermelho) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = runCatching { formatoData.parse(data.trim())?.time }.getOrNull()
                if (data.isNotBlank() && d == null) { erro = "Data inválida (use dd/mm/aaaa)." }
                else { v.informarUltimo(item, km.toIntOrNull(), d); aoFechar() }
            }) { Text("Salvar", fontSize = 18.sp) }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar", fontSize = 18.sp) } },
    )
}
