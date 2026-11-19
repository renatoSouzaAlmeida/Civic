package br.com.civic.launcher.obd

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** Luzes-espia que o app tenta ler da rede CAN (experimental: depende do que a tomada OBD do carro deixa passar). */
enum class SinalCan(val rotulo: String) {
    Farol("Farol"), Farolete("Farolete"), SetaEsquerda("Seta esquerda"), SetaDireita("Seta direita"),
    Alerta("Pisca-alerta"), Piloto("Piloto automático"),
}

/** Um sinal vale "aceso" quando `(byte[posicao] and mascara) == valor` no quadro de [id]. */
data class RegraCan(val id: String, val posicao: Int, val mascara: Int, val valor: Int) {
    fun ativo(quadro: IntArray) = quadro.size > posicao && (quadro[posicao] and mascara) == valor
}

/** Bits que ficaram sempre iguais com a luz apagada, sempre iguais com ela acesa, e diferentes entre as duas capturas. */
data class CandidatoCan(val regra: RegraCan) {
    val texto: String get() = "ID ${regra.id} · byte ${regra.posicao} · máscara %02X → %02X".format(regra.mascara, regra.valor)
}

object SinaisCan {
    /** Quadros capturados por ID (só o corpo, sem o ID). */
    fun candidatos(base: Map<String, List<IntArray>>, ligado: Map<String, List<IntArray>>): List<CandidatoCan> {
        val achados = ArrayList<CandidatoCan>()
        for ((id, quadrosBase) in base) {
            val quadrosLigado = ligado[id] ?: continue
            val tamanho = minOf(quadrosBase.minOf { it.size }, quadrosLigado.minOf { it.size })
            for (pos in 0 until tamanho) {
                var mascara = 0
                var valor = 0
                for (bit in 0..7) {
                    val m = 1 shl bit
                    val a = quadrosBase.map { it[pos] and m }.distinct()
                    val b = quadrosLigado.map { it[pos] and m }.distinct()
                    if (a.size == 1 && b.size == 1 && a[0] != b[0]) { mascara = mascara or m; valor = valor or b[0] }
                }
                if (mascara != 0) achados += CandidatoCan(RegraCan(id, pos, mascara, valor))
            }
        }
        // Poucos bits mudando = candidato mais provável (contadores e checksums mudam muitos bits).
        return achados.sortedBy { Integer.bitCount(it.regra.mascara) }.take(12)
    }
}

/** Regras escolhidas pelo usuário, guardadas no aparelho. */
class MapaCan private constructor(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("sinais_can", Context.MODE_PRIVATE)
    var regras by mutableStateOf(ler())
        private set

    fun definir(sinal: SinalCan, regra: RegraCan) { regras = regras + (sinal to regra); gravar() }
    fun remover(sinal: SinalCan) { regras = regras - sinal; gravar() }

    private fun gravar() {
        val o = JSONObject()
        regras.forEach { (s, r) -> o.put(s.name, JSONObject().put("id", r.id).put("pos", r.posicao).put("mascara", r.mascara).put("valor", r.valor)) }
        prefs.edit().putString("regras", o.toString()).apply()
    }

    private fun ler(): Map<SinalCan, RegraCan> = runCatching {
        val o = JSONObject(prefs.getString("regras", "{}") ?: "{}")
        SinalCan.entries.mapNotNull { s ->
            o.optJSONObject(s.name)?.let { s to RegraCan(it.getString("id"), it.getInt("pos"), it.getInt("mascara"), it.getInt("valor")) }
        }.toMap()
    }.getOrDefault(emptyMap())

    companion object {
        @Volatile private var instancia: MapaCan? = null
        fun obter(ctx: Context): MapaCan = instancia ?: synchronized(this) { instancia ?: MapaCan(ctx.applicationContext).also { instancia = it } }
    }
}
