package br.com.civic.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

enum class Combustivel(val rotulo: String) { Gasolina("Gasolina"), Etanol("Etanol") }

data class Abastecimento(
    val id: Long,
    val data: Long,
    val km: Int,
    val litros: Float,
    val precoLitro: Float,
    val combustivel: Combustivel,
    val tanqueCheio: Boolean,
    /** Valor total pago (R$). Pode ser o único valor conhecido: vale o total OU o preço por litro. */
    val valorTotal: Float = 0f,
    val aditivada: Boolean = false,
    /** Detectado pelo app quando o nível do tanque subiu (o valor pago é informado depois). */
    val automatico: Boolean = false,
    val nivelAntes: Int? = null,
    val nivelDepois: Int? = null,
) {
    val total: Float get() = if (valorTotal > 0f) valorTotal else litros * precoLitro
    /** Preço por litro: o informado ou calculado do valor total. 0 = ainda sem valor. */
    val precoEfetivo: Float get() = if (precoLitro > 0f) precoLitro else if (valorTotal > 0f && litros > 0f) valorTotal / litros else 0f
    val semValor: Boolean get() = total <= 0f
}

/** Item de manutenção: vence pelo que chegar primeiro, km ou tempo. */
data class ItemManutencao(
    val id: String,
    val nome: String,
    val intervaloKm: Int?,
    val intervaloMeses: Int?,
    val ultimoKm: Int? = null,
    val ultimaData: Long? = null,
)

enum class SituacaoManutencao(val rotulo: String) { SemRegistro("Sem registro"), EmDia("Em dia"), Proximo("Próximo"), Vencido("Vencido") }

/**
 * Dados do veículo guardados no aparelho (abastecimentos, km e manutenção).
 * Tudo em JSON nas preferências: leve, sem banco de dados extra (app pequeno).
 */
class Veiculo private constructor(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("veiculo", Context.MODE_PRIVATE)
    private val nuvem = Nuvem.obter(ctx)

    val abastecimentos = mutableStateListOf<Abastecimento>()
    val manutencao = mutableStateListOf<ItemManutencao>()
    /** Km informado à mão (o Civic 2012 não informa o odômetro pelo OBD). */
    var kmManual by mutableStateOf(prefs.getInt("km_manual", 0))
        private set

    init {
        carregar()
    }

    /** Km rodados (medidos pelo computador de bordo) desde o último km conhecido. */
    var distanciaDesdeBase by mutableStateOf(prefs.getFloat("dist_base", 0f).toDouble())
        private set

    /**
     * Odômetro do app: o maior entre o km digitado e o do último abastecimento, mais o que o
     * computador de bordo mediu desde então (o Civic 2012 não informa o odômetro pelo OBD).
     */
    val kmAtual: Int get() {
        val base = maxOf(kmManual, abastecimentos.maxOfOrNull { it.km } ?: 0)
        return if (base > 0) base + distanciaDesdeBase.toInt() else 0
    }

    fun somarDistancia(km: Double) {
        distanciaDesdeBase += km
        prefs.edit().putFloat("dist_base", distanciaDesdeBase.toFloat()).apply()
    }

    private fun zerarDistancia() { distanciaDesdeBase = 0.0; prefs.edit().putFloat("dist_base", 0f).apply() }

    fun salvarKmManual(km: Int) { kmManual = km; prefs.edit().putInt("km_manual", km).apply(); zerarDistancia(); aposMudanca() }

    fun adicionar(a: Abastecimento) {
        abastecimentos.add(a)
        abastecimentos.sortBy { it.km }
        if (a.km >= (abastecimentos.maxOfOrNull { it.km } ?: 0)) zerarDistancia() // km do painel vira a nova base
        gravarAbastecimentos()
        val br = java.util.Locale("pt", "BR")
        val consumo = consumos()[a.id]?.let { " · %.1f km/l".format(br, it) } ?: ""
        nuvem.avisar(
            "abastecimento",
            "⛽ Abastecimento: %.1f l de %s · R$ %,.2f · %,d km%s".format(br, a.litros, a.combustivel.rotulo.lowercase(), a.total, a.km, consumo),
        )
        aposMudanca()
    }

    /** Depois de qualquer mudança: envia para a nuvem e confere se alguma manutenção precisa de aviso. */
    fun aposMudanca() {
        nuvem.sincronizar(this)
        nuvem.verificarManutencao(this)
    }

    /** Troca um abastecimento (mesmo id) pelo editado, ex.: ao informar o valor pago de um abastecimento automático. */
    fun atualizar(novo: Abastecimento) {
        val i = abastecimentos.indexOfFirst { it.id == novo.id }
        if (i < 0) return
        abastecimentos[i] = novo
        abastecimentos.sortBy { it.km }
        gravarAbastecimentos()
        aposMudanca()
    }

    /** Registra um abastecimento detectado pelo nível do tanque (sem valor pago ainda). */
    /** Teste: cria um abastecimento "detectado" (15% → 95%) para ver o aviso e o cadastro. Apague o item depois. */
    fun simularAbastecimento(capacidade: Int) {
        val agora = System.currentTimeMillis()
        adicionarAutomatico(
            Abastecimento(agora, agora, kmAtual, (95 - 15) / 100f * capacidade, 0f, Combustivel.Gasolina, true, automatico = true, nivelAntes = 15, nivelDepois = 95),
            teste = true,
        )
    }

    fun adicionarAutomatico(a: Abastecimento, teste: Boolean = false) {
        abastecimentos.add(a)
        abastecimentos.sortBy { it.km }
        zerarDistancia()
        gravarAbastecimentos()
        val br = java.util.Locale("pt", "BR")
        val consumo = consumos()[a.id]?.let { " · consumo %.1f km/l".format(br, it) } ?: ""
        nuvem.avisar(
            "abastecimento",
            (if (teste) "🧪 TESTE (apague o item no histórico)\n" else "") + "⛽ Abastecimento detectado: ~%.1f l (tanque %d%% → %d%%) · %,d km%s\nInforme o valor pago em Veículo → Abastecimentos.".format(
                br, a.litros, a.nivelAntes ?: 0, a.nivelDepois ?: 0, a.km, consumo),
        )
        aposMudanca()
    }

    fun remover(a: Abastecimento) { abastecimentos.remove(a); gravarAbastecimentos(); nuvem.removerAbastecimento(a.id) }

    /**
     * Consumo de cada abastecimento de tanque cheio: km rodados desde o tanque cheio anterior ÷
     * litros colocados nesse intervalo (inclui os parciais). É o método mais confiável.
     */
    fun consumos(): Map<Long, Float> {
        val r = mutableMapOf<Long, Float>()
        var anterior: Abastecimento? = null
        var litros = 0f
        for (a in abastecimentos) {
            if (anterior != null) litros += a.litros
            if (a.tanqueCheio) {
                if (anterior != null && litros > 0f && a.km > anterior.km) r[a.id] = (a.km - anterior.km) / litros
                anterior = a
                litros = 0f
            }
        }
        return r
    }

    fun consumoMedio(combustivel: Combustivel? = null): Float? {
        val c = consumos()
        val lista = abastecimentos.filter { it.id in c && (combustivel == null || it.combustivel == combustivel) }.mapNotNull { c[it.id] }
        return if (lista.isEmpty()) null else lista.average().toFloat()
    }

    /** Média de km rodados entre um abastecimento e o seguinte. */
    fun kmEntreAbastecimentos(): Int? {
        val kms = abastecimentos.map { it.km }
        if (kms.size < 2) return null
        return ((kms.last() - kms.first()) / (kms.size - 1))
    }

    fun gastoNoMes(): Float {
        val agora = Calendar.getInstance()
        return abastecimentos.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.data }
            c.get(Calendar.YEAR) == agora.get(Calendar.YEAR) && c.get(Calendar.MONTH) == agora.get(Calendar.MONTH)
        }.sumOf { it.total.toDouble() }.toFloat()
    }

    /** Custo por km com base no consumo médio e no último preço pago. */
    fun custoPorKm(): Float? {
        val ultimo = abastecimentos.lastOrNull { it.precoEfetivo > 0f } ?: return null
        val consumo = consumoMedio(ultimo.combustivel) ?: consumoMedio() ?: return null
        return ultimo.precoEfetivo / consumo
    }

    /**
     * Proporção de equilíbrio etanol/gasolina: com os consumos reais deste carro, quando houver os dois;
     * senão a regra clássica de 70%.
     */
    fun razaoEtanolGasolina(): Pair<Float, Boolean> {
        val e = consumoMedio(Combustivel.Etanol)
        val g = consumoMedio(Combustivel.Gasolina)
        return if (e != null && g != null && g > 0f) (e / g) to true else 0.70f to false
    }

    // ---- Manutenção ----

    fun situacao(item: ItemManutencao, agora: Long = System.currentTimeMillis()): SituacaoManutencao {
        val kmRest = kmRestante(item)
        val diasRest = diasRestantes(item, agora)
        if (kmRest == null && diasRest == null) return SituacaoManutencao.SemRegistro
        if ((kmRest != null && kmRest <= 0) || (diasRest != null && diasRest <= 0)) return SituacaoManutencao.Vencido
        if ((kmRest != null && kmRest <= 1000) || (diasRest != null && diasRest <= 30)) return SituacaoManutencao.Proximo
        return SituacaoManutencao.EmDia
    }

    fun kmRestante(item: ItemManutencao): Int? =
        if (item.intervaloKm != null && item.ultimoKm != null) item.ultimoKm + item.intervaloKm - kmAtual else null

    fun diasRestantes(item: ItemManutencao, agora: Long = System.currentTimeMillis()): Int? {
        if (item.intervaloMeses == null || item.ultimaData == null) return null
        val vence = Calendar.getInstance().apply { timeInMillis = item.ultimaData; add(Calendar.MONTH, item.intervaloMeses) }.timeInMillis
        return ((vence - agora) / 86_400_000L).toInt()
    }

    /** Marca como feito agora, no km atual. */
    fun registrarFeito(item: ItemManutencao) {
        val i = manutencao.indexOfFirst { it.id == item.id }
        if (i >= 0) { manutencao[i] = item.copy(ultimoKm = kmAtual, ultimaData = System.currentTimeMillis()); gravarManutencao(); aposMudanca() }
    }

    /**
     * Junta o que veio da nuvem: fica com o registro mais recente de cada item e, se a nuvem tiver
     * um km maior que o atual (ex.: revisão lançada direto no Supabase), atualiza o km do painel.
     * Devolve true se algo mudou.
     */
    fun aplicarDaNuvem(remotos: List<Triple<String, Int?, Long?>>): Boolean {
        var mudou = false
        remotos.forEach { (id, km, data) ->
            val i = manutencao.indexOfFirst { it.id == id }
            if (i < 0 || data == null) return@forEach
            val local = manutencao[i]
            if (local.ultimaData == null || data > local.ultimaData) {
                manutencao[i] = local.copy(ultimoKm = km, ultimaData = data)
                mudou = true
            }
        }
        val maiorKm = remotos.mapNotNull { it.second }.maxOrNull()
        if (maiorKm != null && maiorKm > kmAtual) {
            kmManual = maiorKm
            prefs.edit().putInt("km_manual", maiorKm).apply()
            zerarDistancia()
            mudou = true
        }
        if (mudou) gravarManutencao()
        return mudou
    }

    /** Informa quando foi feito pela última vez (km e data), para começar o controle. */
    fun informarUltimo(item: ItemManutencao, km: Int?, data: Long?) {
        val i = manutencao.indexOfFirst { it.id == item.id }
        if (i >= 0) { manutencao[i] = item.copy(ultimoKm = km, ultimaData = data); gravarManutencao(); aposMudanca() }
    }

    // ---- Persistência ----

    private fun carregar() {
        runCatching {
            val arr = JSONArray(prefs.getString("abastecimentos", "[]"))
            for (k in 0 until arr.length()) {
                val o = arr.getJSONObject(k)
                abastecimentos.add(
                    Abastecimento(
                        o.getLong("id"), o.getLong("data"), o.getInt("km"), o.getDouble("litros").toFloat(),
                        o.getDouble("preco").toFloat(), runCatching { Combustivel.valueOf(o.getString("comb")) }.getOrDefault(Combustivel.Gasolina),
                        o.optBoolean("cheio", true),
                        o.optDouble("total", 0.0).toFloat(), o.optBoolean("adit", false), o.optBoolean("auto", false),
                        if (o.has("na")) o.getInt("na") else null, if (o.has("nd")) o.getInt("nd") else null,
                    )
                )
            }
        }
        val salvos = runCatching {
            val arr = JSONArray(prefs.getString("manutencao", "[]"))
            (0 until arr.length()).map { arr.getJSONObject(it) }.associateBy { it.getString("id") }
        }.getOrDefault(emptyMap())
        // Plano padrão do Civic G9 1.8 (uso severo/urbano). Os registros salvos preenchem o último feito.
        PLANO_PADRAO.forEach { p ->
            val o = salvos[p.id]
            manutencao.add(
                if (o == null) p else p.copy(
                    ultimoKm = if (o.has("km")) o.getInt("km") else null,
                    ultimaData = if (o.has("data")) o.getLong("data") else null,
                )
            )
        }
    }

    private fun gravarAbastecimentos() {
        val arr = JSONArray()
        abastecimentos.forEach {
            arr.put(JSONObject().put("id", it.id).put("data", it.data).put("km", it.km).put("litros", it.litros.toDouble())
                .put("preco", it.precoLitro.toDouble()).put("comb", it.combustivel.name).put("cheio", it.tanqueCheio)
                .put("total", it.valorTotal.toDouble()).put("adit", it.aditivada).put("auto", it.automatico)
                .also { o -> it.nivelAntes?.let { v -> o.put("na", v) }; it.nivelDepois?.let { v -> o.put("nd", v) } })
        }
        prefs.edit().putString("abastecimentos", arr.toString()).apply()
    }

    private fun gravarManutencao() {
        val arr = JSONArray()
        manutencao.forEach { m ->
            val o = JSONObject().put("id", m.id)
            m.ultimoKm?.let { o.put("km", it) }
            m.ultimaData?.let { o.put("data", it) }
            arr.put(o)
        }
        prefs.edit().putString("manutencao", arr.toString()).apply()
    }

    companion object {
        /** Intervalos de referência para o Civic G9 1.8 (R18, corrente de comando — sem correia dentada). */
        val PLANO_PADRAO = listOf(
            ItemManutencao("oleo", "Óleo e filtro de óleo", 10_000, 12),
            ItemManutencao("filtro_ar", "Filtro de ar do motor", 20_000, 24),
            ItemManutencao("filtro_cabine", "Filtro de cabine (ar-condicionado)", 10_000, 12),
            ItemManutencao("rodizio", "Rodízio de pneus", 10_000, null),
            ItemManutencao("alinhamento", "Alinhamento e balanceamento", 10_000, 12),
            ItemManutencao("freio_fluido", "Fluido de freio", null, 24),
            ItemManutencao("pastilhas", "Pastilhas de freio (revisar)", 20_000, null),
            ItemManutencao("arrefecimento", "Líquido de arrefecimento", 100_000, 60),
            ItemManutencao("velas", "Velas de ignição", 100_000, null),
            ItemManutencao("atf", "Fluido do câmbio automático", 40_000, 36),
            ItemManutencao("filtro_combustivel", "Filtro de combustível", 20_000, 24),
            ItemManutencao("bicos", "Limpeza dos bicos injetores", 40_000, null),
            ItemManutencao("bateria", "Bateria (troca)", null, 48),
            ItemManutencao("amortecedores", "Amortecedores (revisar)", 40_000, null),
            ItemManutencao("correia_auxiliar", "Tensionador e polias da correia auxiliar", 100_000, 60),
        )

        @Volatile private var instancia: Veiculo? = null
        fun obter(ctx: Context): Veiculo = instancia ?: synchronized(this) {
            instancia ?: Veiculo(ctx.applicationContext).also { instancia = it }
        }
    }
}
