package br.com.civic.launcher

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Sugestão de rotina: das [inicioMin] às [fimMin] (minutos desde 00:00), nos [dias] marcados, sugerir ir a [destino].
 * [dias] é uma máscara: bit 0 = domingo … bit 6 = sábado (igual a Calendar.DAY_OF_WEEK - 1).
 */
data class Rotina(val id: Long, val inicioMin: Int, val fimMin: Int, val destino: String, val dias: Int) {
    fun temDia(diaDaSemana: Int) = dias and (1 shl (diaDaSemana - 1)) != 0
    val periodo: String get() = "%02d:%02d às %02d:%02d".format(inicioMin / 60, inicioMin % 60, fimMin / 60, fimMin % 60)

    companion object {
        /** Segunda a sexta. */
        const val DIAS_UTEIS = 0b0111110
        val NOMES_DIAS = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
    }
}

/**
 * Rotina diária: sugestões de destino por horário e local (ex.: 07:10 às 07:30 → escola).
 * A sugestão aparece a partir de [ANTECEDENCIA_MIN] minutos antes do início e vai até o fim do período.
 */
class Rotinas private constructor(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("rotinas", Context.MODE_PRIVATE)
    val itens = mutableStateListOf<Rotina>()

    init {
        runCatching {
            val arr = JSONArray(prefs.getString("itens", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                itens.add(Rotina(o.getLong("id"), o.getInt("ini"), o.getInt("fim"), o.getString("dest"), o.getInt("dias")))
            }
        }
        itens.sortBy { it.inicioMin }
    }

    fun salvar(r: Rotina) {
        val i = itens.indexOfFirst { it.id == r.id }
        if (i >= 0) itens[i] = r else itens.add(r)
        itens.sortBy { it.inicioMin }
        gravar()
    }

    fun remover(r: Rotina) { itens.remove(r); gravar() }

    /** Rotinas que valem agora (dia certo e dentro do período, com a antecedência). */
    fun ativas(agora: Calendar = Calendar.getInstance()): List<Rotina> {
        val dia = agora.get(Calendar.DAY_OF_WEEK)
        val min = agora.get(Calendar.HOUR_OF_DAY) * 60 + agora.get(Calendar.MINUTE)
        return itens.filter { it.temDia(dia) && min in (it.inicioMin - ANTECEDENCIA_MIN)..it.fimMin }
    }

    private fun gravar() {
        val arr = JSONArray()
        itens.forEach { arr.put(JSONObject().put("id", it.id).put("ini", it.inicioMin).put("fim", it.fimMin).put("dest", it.destino).put("dias", it.dias)) }
        prefs.edit().putString("itens", arr.toString()).apply()
    }

    companion object {
        const val ANTECEDENCIA_MIN = 15

        @Volatile private var instancia: Rotinas? = null
        fun obter(ctx: Context): Rotinas = instancia ?: synchronized(this) {
            instancia ?: Rotinas(ctx.applicationContext).also { instancia = it }
        }
    }
}
