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
) {
    val total: Float get() = litros * precoLitro
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

    fun gastoNoMes(): Float {
        val agora = Calendar.getInstance()
        return abastecimentos.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.data }
            c.get(Calendar.YEAR) == agora.get(Calendar.YEAR) && c.get(Calendar.MONTH) == agora.get(Calendar.MONTH)
        }.sumOf { it.total.toDouble() }.toFloat()
    }

    /** Custo por km com base no consumo médio e no último preço pago. */
    fun custoPorKm(): Float? {
        val ultimo = abastecimentos.lastOrNull() ?: return null
        val consumo = consumoMedio(ultimo.combustivel) ?: consumoMedio() ?: return null
        return ultimo.precoLitro / consumo
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
                .put("preco", it.precoLitro.toDouble()).put("comb", it.combustivel.name).put("cheio", it.tanqueCheio))
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
            ItemManutencao("atf", "Fluido do câmbio automático", 40_000, null),
        )

        @Volatile private var instancia: Veiculo? = null
        fun obter(ctx: Context): Veiculo = instancia ?: synchronized(this) {
            instancia ?: Veiculo(ctx.applicationContext).also { instancia = it }
        }
    }
}
