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
private val formatoData = SimpleDateFormat("dd/MM/yyyy", BR)
/** Data digitada só com números (ddMMaaaa), sem aceitar datas impossíveis como 31/02. */
private val formatoDataEstrito = SimpleDateFormat("ddMMyyyy", BR).apply { isLenient = false }

/** Veículo: computador de bordo (viagem), abastecimentos (consumo real, gastos, etanol × gasolina) e manutenção. */
@Composable
fun TelaVeiculo() {
    var aba by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BarraAbas(
            listOf(Aba("Viagem", Icons.Filled.Route), Aba("Abastecimentos", Icons.Filled.LocalGasStation), Aba("Manutenção", Icons.Filled.Build)),
            aba, Modifier.fillMaxWidth(),
        ) { aba = it }
        when (aba) {
            0 -> AbaViagem()
            1 -> AbaAbastecimentos()
            else -> AbaManutencao()
        }
    }
}

// ---------------- Computador de bordo ----------------

@Composable
private fun AbaViagem() {
    val cb = br.com.civic.launcher.ComputadorBordo.obter(LocalContext.current)
    val v = Veiculo.obter(LocalContext.current)
    var limpar by remember { mutableStateOf(false) }
    // Comparador: toque em duas viagens do histórico (guarda o início de cada uma).
    var comparar by remember { mutableStateOf(listOf<Long>()) }
    // Atualiza a duração mesmo parado (a viagem só recebe amostras quando há leitura).
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { agora = System.currentTimeMillis(); kotlinx.coroutines.delay(1_000) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val atual = cb.atual
        // Resumo da condução (Driving Score): da viagem em andamento ou, se não houver, da última encerrada.
        (atual ?: cb.historico.firstOrNull())?.let { CartaoConducao(it, andando = atual != null, cb.historico.drop(if (atual == null) 1 else 0).mapNotNull { h -> h.nota }.take(10), agora) }
        CartaoPneus()
        Secao(if (atual == null) "Viagem atual" else if (atual.demonstracao) "Viagem atual (demonstração)" else "Viagem atual") {
            if (atual == null) {
                Text("Nenhuma viagem em andamento. Ela começa sozinha quando o carro anda (ou o motor liga) e termina após 5 min parado.", color = Cores.TextoSuave, fontSize = 15.sp)
            } else {
                val est = cb.estimativa(atual)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Numero("Distância", "%.1f km".format(BR, atual.distanciaKm), Modifier.weight(1f))
                    Numero("Tempo", br.com.civic.launcher.ComputadorBordo.duracao(maxOf(atual.duracaoMs, agora - atual.inicio)), Modifier.weight(1f))
                    Numero("Velocidade média", "%.0f km/h".format(BR, atual.velMedia), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Numero("Velocidade máxima", "${atual.velMaxima} km/h", Modifier.weight(1f))
                    Numero("Combustível (est.)", est?.let { "%.1f l".format(BR, it.first) } ?: "—", Modifier.weight(1f))
                    Numero("Custo (est.)", est?.second?.takeIf { it > 0f }?.let { reais(it) } ?: "—", Modifier.weight(1f))
                }
                if (est == null) Text("Combustível e custo aparecem quando houver consumo médio (2 abastecimentos com tanque cheio).", color = Cores.TextoSuave, fontSize = 13.sp)
                BotaoPilula("Encerrar viagem", Icons.Filled.Flag, Modifier.fillMaxWidth()) { cb.finalizar() }
            }
        }
        OndeParei()
        Secao("Odômetro do app") {
            Text(
                if (v.kmAtual > 0) "%,d km".format(BR, v.kmAtual) else "Informe o km atual na aba Manutenção para começar.",
                color = Cores.Texto, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
            )
            Text("Km do painel informado (ou do último abastecimento) + %.1f km medidos pelo app desde então.".format(BR, v.distanciaDesdeBase), color = Cores.TextoSuave, fontSize = 13.sp)
        }
        Secao("Últimas viagens (${cb.historico.size})") {
            if (cb.historico.isEmpty()) Text("As viagens encerradas aparecem aqui.", color = Cores.TextoSuave, fontSize = 15.sp)
            val fmt = remember { SimpleDateFormat("dd/MM HH:mm", BR) }
            if (cb.historico.size >= 2) Text("Toque em duas viagens para compará-las.", color = Cores.TextoSuave, fontSize = 13.sp)
            if (comparar.size == 2) {
                val a = cb.historico.firstOrNull { it.inicio == comparar[0] }
                val b = cb.historico.firstOrNull { it.inicio == comparar[1] }
                if (a != null && b != null) ComparacaoViagens(a, b, fmt)
                TextButton(onClick = { comparar = emptyList() }) { Text("Limpar comparação", fontSize = 15.sp) }
            }
            cb.historico.forEach { h ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (h.inicio in comparar) Cores.Destaque.copy(alpha = 0.2f) else Cores.Chip)
                        .clickable { comparar = if (h.inicio in comparar) comparar - h.inicio else (comparar + h.inicio).takeLast(2) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(fmt.format(Date(h.inicio)) + if (h.demonstracao) " · demonstração" else "", color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(cb.resumo(h), color = Cores.TextoSuave, fontSize = 13.sp)
                        h.resumoConducao()?.let { Text(it, color = Cores.TextoSuave, fontSize = 12.sp) }
                    }
                    h.nota?.let { n ->
                        Text("$n", color = corNota(n), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text("/100", color = Cores.TextoSuave, fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp, top = 8.dp))
                    }
                }
            }
            if (cb.historico.isNotEmpty()) TextButton(onClick = { limpar = true }) { Text("Limpar histórico", fontSize = 15.sp) }
        }
    }
    if (limpar) AlertDialog(
        onDismissRequest = { limpar = false },
        title = { Text("Limpar o histórico de viagens?") },
        confirmButton = { TextButton(onClick = { cb.limparHistorico(); limpar = false }) { Text("Limpar", fontSize = 18.sp) } },
        dismissButton = { TextButton(onClick = { limpar = false }) { Text("Cancelar", fontSize = 18.sp) } },
    )
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
    var total by remember { mutableStateOf("") }
    var aditivada by remember { mutableStateOf(false) }
    var comb by remember { mutableStateOf(v.abastecimentos.lastOrNull()?.combustivel ?: Combustivel.Gasolina) }
    var cheio by remember { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }
    Secao("Novo abastecimento") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Campo("Km do painel", km, Modifier.weight(1f), mascara = Mascara.Milhar) { km = it }
            Campo("Litros", litros, Modifier.weight(1f), mascara = Mascara.Decimal2) { litros = it }
            Campo("Preço por litro", preco, Modifier.weight(1f), mascara = Mascara.Moeda) { preco = it }
            Campo("Valor total pago", total, Modifier.weight(1f), mascara = Mascara.Moeda) { total = it }
        }
        Text("Informe o preço por litro OU o valor total (o que você tiver).", color = Cores.TextoSuave, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Combustivel.entries.forEach { c -> Opcao(c.rotulo, comb == c, Modifier.weight(1f)) { comb = c } }
            Opcao(if (cheio) "Tanque cheio ✓" else "Parcial", cheio, Modifier.weight(1f)) { cheio = !cheio }
        }
        if (comb == Combustivel.Gasolina) Row { Opcao(if (aditivada) "Aditivada ✓" else "Gasolina comum", aditivada, Modifier.fillMaxWidth()) { aditivada = !aditivada } }
        erro?.let { Text(it, color = Cores.Vermelho, fontSize = 14.sp) }
        BotaoPilula("Salvar abastecimento", Icons.Filled.LocalGasStation, Modifier.fillMaxWidth()) {
            val k = km.toIntOrNull()
            val l = Mascara.centavos(litros)
            val p = Mascara.centavos(preco)
            val t = Mascara.centavos(total)
            erro = when {
                k == null || k <= 0 -> "Informe o km do painel."
                l != null && l > 80f -> "Litros acima de 80: confira (o tanque do Civic tem 50 l)."
                p != null && p > 20f -> "Preço por litro acima de R$ 20,00: confira."
                l == null || l <= 0f -> "Informe os litros."
                (p == null || p <= 0f) && (t == null || t <= 0f) -> "Informe o preço por litro ou o valor total."
                t != null && t > 2000f -> "Valor total acima de R$ 2.000,00: confira."
                v.abastecimentos.isNotEmpty() && k < (v.abastecimentos.maxOf { it.km }) -> "O km é menor que o do último abastecimento."
                else -> null
            }
            if (erro == null) {
                v.adicionar(Abastecimento(System.currentTimeMillis(), System.currentTimeMillis(), k!!, l!!, p ?: 0f, comb, cheio, valorTotal = t ?: 0f, aditivada = aditivada && comb == Combustivel.Gasolina))
                km = ""; litros = ""; preco = ""; total = ""; aditivada = false
            }
        }
        Text("Dica: para o consumo real, encha o tanque até o desarme da bomba e marque \"Tanque cheio\".", color = Cores.TextoSuave, fontSize = 13.sp)
        val capacidade = br.com.civic.launcher.Configuracoes.obter(androidx.compose.ui.platform.LocalContext.current).capacidadeEfetiva
        BotaoPilula("Simular abastecimento detectado (teste)", Icons.Filled.LocalGasStation, Modifier.fillMaxWidth()) { v.simularAbastecimento(capacidade) }
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Numero("Abastecimentos", v.abastecimentos.size.toString(), Modifier.weight(1f))
            Numero("Km entre abast.", v.kmEntreAbastecimentos()?.let { "%,d".format(BR, it) } ?: "—", Modifier.weight(1f))
            Numero("Pendentes de valor", v.abastecimentos.count { it.semValor }.toString(), Modifier.weight(1f))
        }
        if (v.consumoMedio() == null) Text("O consumo aparece a partir do segundo abastecimento com tanque cheio.", color = Cores.TextoSuave, fontSize = 13.sp)
    }
}

/** Duas viagens lado a lado; o melhor valor de cada linha fica em verde (maior é melhor na nota e no consumo; menor é melhor nos eventos). */
@Composable
private fun ComparacaoViagens(a: br.com.civic.launcher.Viagem, b: br.com.civic.launcher.Viagem, fmt: SimpleDateFormat) {
    fun maior(x: Double?, y: Double?) = if (x == null || y == null || x == y) 0 else if (x > y) -1 else 1
    fun menor(x: Int, y: Int) = if (x == y) 0 else if (x < y) -1 else 1
    @Composable fun linha(rotulo: String, va: String, vb: String, melhor: Int = 0) = Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(rotulo, color = Cores.TextoSuave, fontSize = 14.sp, modifier = Modifier.weight(1.3f))
        Text(va, color = if (melhor == -1) Cores.Ok else Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(vb, color = if (melhor == 1) Cores.Ok else Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
    val cb = br.com.civic.launcher.ComputadorBordo
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cores.Chip).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Spacer(Modifier.weight(1.3f))
            Text(fmt.format(Date(a.inicio)), color = Cores.Destaque, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(fmt.format(Date(b.inicio)), color = Cores.Destaque, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        linha("Distância", "%.1f km".format(BR, a.distanciaKm), "%.1f km".format(BR, b.distanciaKm))
        linha("Duração", cb.duracao(a.duracaoMs), cb.duracao(b.duracaoMs))
        linha("Velocidade média", "%.0f km/h".format(BR, a.velMedia), "%.0f km/h".format(BR, b.velMedia))
        linha("Velocidade máxima", "${a.velMaxima} km/h", "${b.velMaxima} km/h")
        linha("Consumo", a.consumoMedio?.let { "%.1f km/l".format(BR, it) } ?: "—", b.consumoMedio?.let { "%.1f km/l".format(BR, it) } ?: "—", maior(a.consumoMedio, b.consumoMedio))
        linha("Nota de condução", a.nota?.let { "$it" } ?: "—", b.nota?.let { "$it" } ?: "—", maior(a.nota?.toDouble(), b.nota?.toDouble()))
        linha("Frenagens bruscas", "${a.frenagens}", "${b.frenagens}", menor(a.frenagens, b.frenagens))
        linha("Acelerações bruscas", "${a.aceleracoes}", "${b.aceleracoes}", menor(a.aceleracoes, b.aceleracoes))
        linha("Excessos de velocidade", "${a.excessos}", "${b.excessos}", menor(a.excessos, b.excessos))
        linha("RPM elevado", if (a.comObd) "${a.rpmAlto}" else "—", if (b.comObd) "${b.rpmAlto}" else "—", if (a.comObd && b.comObd) menor(a.rpmAlto, b.rpmAlto) else 0)
    }
}

@Composable
private fun corNota(n: Int): Color = when { n >= 90 -> Cores.Ok; n >= 75 -> Cores.Destaque; n >= 55 -> Cores.Amarelo; else -> Cores.Vermelho }

/** Resumo da condução: anel com a nota 0–100, selo, distância e tempo, e os eventos da viagem; compara com as viagens anteriores. */
@Composable
private fun CartaoConducao(v: br.com.civic.launcher.Viagem, andando: Boolean, anteriores: List<Int>, agora: Long) {
    val nota = v.nota
    val cor = if (nota != null) corNota(nota) else Cores.TextoSuave
    val vazio = Cores.SegVazio
    val tempo = if (andando) maxOf(v.duracaoMs, agora - v.inicio) else v.duracaoMs
    val loc = BR
    Secao("Resumo da condução") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val esp = 11.dp.toPx()
                    val tam = androidx.compose.ui.geometry.Size(size.width - esp, size.height - esp)
                    val topo = androidx.compose.ui.geometry.Offset(esp / 2, esp / 2)
                    drawArc(vazio, 0f, 360f, false, topo, tam, style = androidx.compose.ui.graphics.drawscope.Stroke(esp))
                    if (nota != null) drawArc(cor, -90f, 360f * nota / 100f, false, topo, tam, style = androidx.compose.ui.graphics.drawscope.Stroke(esp, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(nota?.toString() ?: "—", color = cor, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 40.sp)
                    Text("/ 100", color = Cores.TextoSuave, fontSize = 13.sp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (nota != null) Text(br.com.civic.launcher.Viagem.rotuloNota(nota), color = cor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(if (andando) "Viagem em andamento" else "Viagem concluída", color = Cores.Texto, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("%.1f km".format(loc, v.distanciaKm), color = Cores.Texto, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text(br.com.civic.launcher.ComputadorBordo.duracao(tempo) + " de condução", color = Cores.TextoSuave, fontSize = 14.sp)
                if (nota == null) Text("A nota aparece depois de 0,5 km.", color = Cores.TextoSuave, fontSize = 12.sp)
            }
        }
        @Composable fun linha(rotulo: String, valor: String) = Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(rotulo, color = Cores.TextoSuave, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(valor, color = Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        linha("Frenagens bruscas", "${v.frenagens}")
        linha("Acelerações bruscas", "${v.aceleracoes}")
        linha("Excessos de velocidade", if (br.com.civic.launcher.Configuracoes.obter(LocalContext.current).limiteVelocidade > 0) "${v.excessos}" else "desligado")
        linha("RPM elevado", if (v.comObd) (if (v.rpmAlto == 1) "1 ocorrência" else "${v.rpmAlto} ocorrências") else "— (sem OBD)")
        linha("Consumo médio", v.consumoMedio?.let { "%.1f km/l".format(loc, it) } ?: "— (sem OBD)")
        linha("Velocidade média / máxima", "%.0f / %d km/h".format(loc, v.velMedia, v.velMaxima))
        if (nota != null && anteriores.isNotEmpty()) {
            val media = anteriores.average()
            val dif = nota - media.toInt()
            Text(
                "Média das últimas ${anteriores.size} viagens: %.0f. ".format(loc, media) + when { dif > 2 -> "Esta ficou $dif pontos acima."; dif < -2 -> "Esta ficou ${-dif} pontos abaixo."; else -> "Esta ficou na média." },
                color = Cores.TextoSuave, fontSize = 13.sp,
            )
        }
        Text(
            if (v.comObd) "Medido pelo OBD e pelo GPS." else "Medido pelo GPS (sem OBD): RPM e consumo não aparecem. Funciona também com CarPlay e Android Auto na frente.",
            color = Cores.TextoSuave, fontSize = 12.sp,
        )
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
    val ctx = LocalContext.current
    var anp by remember { mutableStateOf<br.com.civic.launcher.PrecoAnp?>(null) }
    var anpMsg by remember { mutableStateOf("Consultando os preços da ANP…") }
    LaunchedEffect(Unit) {
        br.com.civic.launcher.Localidade.municipioEstado(ctx) { par ->
            if (par == null) anpMsg = "Sem localização para consultar a ANP."
            else br.com.civic.launcher.Nuvem.obter(ctx).precoAnp(par.first, par.second) { r -> anp = r; anpMsg = if (r == null) "Preços da ANP indisponíveis para esta região." else "" }
        }
    }
    Secao("Etanol ou gasolina?") {
        anp?.let { p ->
            val dd = { s: String -> if (s.length >= 10) s.substring(8, 10) + "/" + s.substring(5, 7) else s }
            Text(
                "ANP · ${if (p.fonte == "estado") "média do estado" else p.local.lowercase().replaceFirstChar { it.uppercase() }}: " +
                    listOfNotNull(p.gasolina?.let { "gasolina ${reais(it)}" }, p.aditivada?.let { "aditivada ${reais(it)}" }, p.etanol?.let { "etanol ${reais(it)}" }).joinToString(" · ") +
                    " (semana de ${dd(p.semanaInicio)} a ${dd(p.semanaFim)})",
                color = Cores.Texto, fontSize = 14.sp,
            )
            if (p.etanol != null && p.gasolina != null) BotaoPilula("Usar os preços da ANP", Icons.Filled.LocalGasStation, Modifier.fillMaxWidth()) {
                etanol = Mascara.paraCentavos(p.etanol); gasolina = Mascara.paraCentavos(p.gasolina)
            }
        }
        if (anp == null && anpMsg.isNotEmpty()) Text(anpMsg, color = Cores.TextoSuave, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Campo("Etanol (litro)", etanol, Modifier.weight(1f), mascara = Mascara.Moeda) { etanol = it }
            Campo("Gasolina (litro)", gasolina, Modifier.weight(1f), mascara = Mascara.Moeda) { gasolina = it }
        }
        val e = Mascara.centavos(etanol)
        val g = Mascara.centavos(gasolina)
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
    var editar by remember { mutableStateOf<Abastecimento?>(null) }
    editar?.let { a -> EditarAbastecimento(v, a) { editar = null } }
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
                        (if (a.semValor) "%.2f l · aguardando o valor pago".format(BR, a.litros)
                        else "%.2f l × %s = %s".format(BR, a.litros, reais(a.precoEfetivo), reais(a.total))) +
                            (if (!a.tanqueCheio) " · parcial" else "") + (if (a.aditivada) " · aditivada" else "") +
                            (if (a.automatico) " · detectado pelo tanque (${a.nivelAntes}% → ${a.nivelDepois}%)" else ""),
                        color = if (a.semValor) Cores.Destaque else Cores.TextoSuave, fontSize = 13.sp,
                    )
                }
                consumos[a.id]?.let { Text("%.1f km/l".format(BR, it), color = Cores.Ok, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
                Icon(Icons.Filled.Edit, "Editar", tint = if (a.semValor) Cores.Destaque else Cores.TextoSuave, modifier = Modifier.size(26.dp).clickable { editar = a })
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

/** Informa ou corrige um abastecimento (ex.: o valor pago de um abastecimento detectado pelo tanque). */
@Composable
private fun EditarAbastecimento(v: Veiculo, a: Abastecimento, aoFechar: () -> Unit) {
    var km by remember { mutableStateOf(a.km.toString()) }
    var litros by remember { mutableStateOf(Mascara.paraCentavos(a.litros)) }
    var preco by remember { mutableStateOf(if (a.precoLitro > 0f) Mascara.paraCentavos(a.precoLitro) else "") }
    var total by remember { mutableStateOf(if (a.valorTotal > 0f) Mascara.paraCentavos(a.valorTotal) else "") }
    var aditivada by remember { mutableStateOf(a.aditivada) }
    var comb by remember { mutableStateOf(a.combustivel) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (a.automatico) "Abastecimento detectado" else "Editar abastecimento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${formatoData.format(Date(a.data))}" + (if (a.automatico) " · tanque ${a.nivelAntes}% → ${a.nivelDepois}%" else ""), color = Cores.TextoSuave, fontSize = 14.sp)
                Campo("Valor total pago (R$)", total, mascara = Mascara.Moeda) { total = it }
                Campo("ou preço por litro", preco, mascara = Mascara.Moeda) { preco = it }
                Campo("Litros (corrija com o da bomba)", litros, mascara = Mascara.Decimal2) { litros = it }
                Campo("Km do painel", km, mascara = Mascara.Milhar) { km = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Combustivel.entries.forEach { c -> Opcao(c.rotulo, comb == c, Modifier.weight(1f)) { comb = c } }
                }
                if (comb == Combustivel.Gasolina) Opcao(if (aditivada) "Aditivada ✓" else "Gasolina comum", aditivada, Modifier.fillMaxWidth()) { aditivada = !aditivada }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val l = Mascara.centavos(litros)?.takeIf { it > 0f } ?: a.litros
                v.atualizar(
                    a.copy(
                        km = km.toIntOrNull() ?: a.km, litros = l,
                        precoLitro = Mascara.centavos(preco) ?: 0f, valorTotal = Mascara.centavos(total) ?: 0f,
                        combustivel = comb, aditivada = aditivada && comb == Combustivel.Gasolina,
                    )
                )
                aoFechar()
            }) { Text("Salvar", fontSize = 18.sp) }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar", fontSize = 18.sp) } },
    )
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
                Campo("Km atual do painel", km, Modifier.weight(1f), mascara = Mascara.Milhar) { km = it }
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
    var data by remember { mutableStateOf(item.ultimaData?.let { formatoData.format(Date(it)).filter(Char::isDigit) } ?: "") }
    var erro by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Última vez: ${item.nome}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Campo("Km em que foi feito", km, Modifier, mascara = Mascara.Milhar) { km = it }
                Campo("Data", data, Modifier, mascara = Mascara.Data) { data = it }
                erro?.let { Text(it, color = Cores.Vermelho) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = if (data.length == 8) runCatching { formatoDataEstrito.parse(data)?.time }.getOrNull() else null
                val k = km.toIntOrNull()
                if (data.isNotEmpty() && d == null) { erro = "Data inválida: digite dia, mês e ano (ex.: 19112026)." }
                else if (d != null && d > System.currentTimeMillis()) { erro = "A data não pode ser no futuro." }
                else if (k != null && v.kmAtual > 0 && k > v.kmAtual) { erro = "O km não pode ser maior que o km atual (%,d).".format(BR, v.kmAtual) }
                else { v.informarUltimo(item, k, d); aoFechar() }
            }) { Text("Salvar", fontSize = 18.sp) }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar", fontSize = 18.sp) } },
    )
}

/** "Onde parei o carro": última posição guardada, com atalhos para abrir no mapa, reenviar e salvar a posição agora. */
@Composable
private fun OndeParei() {
    val ctx = LocalContext.current
    val estacionamento = remember { br.com.civic.launcher.LocalEstacionamento.obter(ctx) }
    val e = estacionamento.atual
    var aviso by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("dd/MM 'às' HH:mm", BR) }
    Secao("Onde parei o carro") {
        if (e == null) {
            Text("Ainda não há posição guardada. Ela é salva sozinha quando o motor desliga, ou toque em \"Salvar posição agora\".", color = Cores.TextoSuave, fontSize = 15.sp)
        } else {
            Text(e.endereco ?: "%.5f, %.5f".format(Locale.US, e.lat, e.lon), color = Cores.Texto, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Estacionado em ${fmt.format(Date(e.data))}" + if (e.idadePosicaoMs >= 10 * 60_000) " · posição do GPS de ${e.idadePosicaoMs / 60_000} min antes" else "",
                color = Cores.TextoSuave, fontSize = 14.sp,
            )
            Text("Este cartão mostra a última vez que o carro foi estacionado (motor desligado), não a posição de agora.", color = Cores.TextoSuave, fontSize = 12.sp)
        }
        // A posição atual do aparelho, para comparar (e ver se o GPS está chegando).
        estacionamento.posicaoRecente()?.let { p ->
            val min = (System.currentTimeMillis() - p.time).coerceAtLeast(0) / 60_000
            Text("Posição atual do aparelho: %.5f, %.5f · sinal ${if (min < 1) "agora" else "há $min min"} (${if (p.provider == "gps") "GPS" else "rede/aprox."})".format(Locale.US, p.latitude, p.longitude), color = Cores.Texto, fontSize = 13.sp)
        } ?: Text("Sem posição atual do aparelho (sem sinal de GPS nem de rede).", color = Cores.Amarelo, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotaoPilula("Abrir no mapa", Icons.Filled.Map, Modifier.weight(1f)) {
                e?.let {
                    val uri = android.net.Uri.parse("geo:${it.lat},${it.lon}?q=${it.lat},${it.lon}(Meu carro)")
                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { aviso = "Nenhum app de mapa encontrado." }
                } ?: run { aviso = "Ainda não há posição guardada." }
            }
            BotaoPilula("Enviar ao Telegram", Icons.Filled.Send, Modifier.weight(1f)) {
                if (e == null) aviso = "Ainda não há posição guardada." else { estacionamento.reenviar(); aviso = "Enviado (se o Telegram estiver vinculado)." }
            }
            BotaoPilula("Salvar posição agora", Icons.Filled.MyLocation, Modifier.weight(1f)) {
                aviso = if (estacionamento.estacionar(manual = true)) "Posição salva e enviada." else "Sem sinal de GPS no momento."
            }
        }
        aviso?.let { Text(it, color = Cores.Destaque, fontSize = 14.sp) }
        Text(
            "Para o GPS funcionar com o Waze na frente, em Config. permita a localização \"o tempo todo\" para o Civic Renatin.",
            color = Cores.TextoSuave, fontSize = 13.sp,
        )
    }
}
