package br.com.civic.launcher.obd

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import br.com.civic.launcher.Nuvem
import org.json.JSONArray
import org.json.JSONObject

/** Um código já visto: quando apareceu pela primeira e pela última vez, e se era pendente. */
data class RegistroFalha(val codigo: String, val primeiraVez: Long, val ultimaVez: Long, val pendente: Boolean)

/**
 * Histórico dos códigos de falha lidos do carro (guardado no aparelho). Um código novo gera aviso
 * no sininho e no Telegram. A demonstração não entra no histórico.
 */
class HistoricoFalhas private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("historico_falhas", Context.MODE_PRIVATE)
    /** Mais recente primeiro. */
    val itens = mutableStateListOf<RegistroFalha>()
    private var ultimaLeitura: Pair<List<String>, List<String>>? = null

    init {
        runCatching {
            val arr = JSONArray(prefs.getString("itens", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                itens.add(RegistroFalha(o.getString("c"), o.getLong("p"), o.getLong("u"), o.optBoolean("pend")))
            }
        }
    }

    fun registrar(confirmados: List<String>, pendentes: List<String>) {
        if (confirmados.isEmpty() && pendentes.isEmpty()) return
        if (ultimaLeitura == confirmados to pendentes) return
        ultimaLeitura = confirmados to pendentes
        val agora = System.currentTimeMillis()
        (confirmados.map { it to false } + pendentes.filter { it !in confirmados }.map { it to true }).forEach { (c, pend) ->
            val i = itens.indexOfFirst { it.codigo == c }
            if (i >= 0) {
                itens[i] = itens[i].copy(ultimaVez = agora, pendente = pend)
            } else {
                itens.add(0, RegistroFalha(c, agora, agora, pend))
                val info = CodigosFalha.info(c)
                Nuvem.obter(ctx).avisar(
                    "falha",
                    "🛠️ ${if (pend) "Código pendente" else "Novo código de falha"} $c: ${info.descricao} (${info.gravidade.rotulo})",
                )
            }
        }
        itens.sortByDescending { it.ultimaVez }
        gravar()
    }

    fun limpar() { itens.clear(); ultimaLeitura = null; gravar() }

    private fun gravar() {
        val arr = JSONArray()
        itens.take(50).forEach { arr.put(JSONObject().put("c", it.codigo).put("p", it.primeiraVez).put("u", it.ultimaVez).put("pend", it.pendente)) }
        prefs.edit().putString("itens", arr.toString()).apply()
    }

    companion object {
        @Volatile private var instancia: HistoricoFalhas? = null
        fun obter(ctx: Context): HistoricoFalhas = instancia ?: synchronized(this) {
            instancia ?: HistoricoFalhas(ctx.applicationContext).also { instancia = it }
        }
    }
}
