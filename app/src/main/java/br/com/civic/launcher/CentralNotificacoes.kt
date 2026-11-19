package br.com.civic.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

data class Notificacao(val id: Long, val data: Long, val tipo: String, val mensagem: String, val lida: Boolean = false)

/**
 * Central de notificações do sininho do topo: guarda no aparelho as últimas 50
 * (avisos de manutenção e abastecimento, alertas do motor, nova versão…), lidas ou não.
 */
class CentralNotificacoes private constructor(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("notificacoes", Context.MODE_PRIVATE)
    private val principal = Handler(Looper.getMainLooper())

    /** Mais recente primeiro. */
    val itens = mutableStateListOf<Notificacao>()

    val naoLidas: Int get() = itens.count { !it.lida }

    init {
        runCatching {
            val arr = JSONArray(prefs.getString("itens", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                itens.add(Notificacao(o.getLong("id"), o.getLong("data"), o.getString("tipo"), o.getString("mensagem"), o.optBoolean("lida")))
            }
        }
    }

    /** Pode ser chamado de qualquer thread. */
    fun registrar(tipo: String, mensagem: String) {
        principal.post {
            val agora = System.currentTimeMillis()
            itens.add(0, Notificacao(agora, agora, tipo, mensagem))
            while (itens.size > MAXIMO) itens.removeAt(itens.lastIndex)
            gravar()
        }
    }

    /** Registra só na primeira vez para essa chave (ex.: uma nova versão, que é verificada várias vezes). */
    fun registrarUmaVez(chave: String, tipo: String, mensagem: String) {
        if (prefs.getBoolean("uma_vez_$chave", false)) return
        prefs.edit().putBoolean("uma_vez_$chave", true).apply()
        registrar(tipo, mensagem)
    }

    fun marcarTodasLidas() {
        if (itens.none { !it.lida }) return
        for (i in itens.indices) if (!itens[i].lida) itens[i] = itens[i].copy(lida = true)
        gravar()
    }

    fun limpar() { itens.clear(); gravar() }

    private fun gravar() {
        val arr = JSONArray()
        itens.forEach { arr.put(JSONObject().put("id", it.id).put("data", it.data).put("tipo", it.tipo).put("mensagem", it.mensagem).put("lida", it.lida)) }
        prefs.edit().putString("itens", arr.toString()).apply()
    }

    companion object {
        private const val MAXIMO = 50
        @Volatile private var instancia: CentralNotificacoes? = null
        fun obter(ctx: Context): CentralNotificacoes = instancia ?: synchronized(this) {
            instancia ?: CentralNotificacoes(ctx.applicationContext).also { instancia = it }
        }
    }
}
