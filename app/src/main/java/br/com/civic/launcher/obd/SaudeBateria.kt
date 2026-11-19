package br.com.civic.launcher.obd

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.civic.launcher.Nuvem
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class AvaliacaoBateria(val rotulo: String, val explicacao: String) {
    Boa("Boa", "A bateria segurou bem a partida."),
    Atencao("Atenção", "A tensão caiu bastante na partida: a bateria está começando a enfraquecer. Faça um teste na próxima revisão."),
    Fraca("Fraca", "A tensão caiu demais na partida: a bateria pode falhar em breve, principalmente em dias frios. Considere trocar."),
}

/** Uma partida do motor: tensão em repouso antes, menor tensão no giro do arranque e carga do alternador depois. */
data class Partida(
    val data: Long,
    val repouso: Float,
    val minima: Float,
    val carga: Float? = null,
    val demonstracao: Boolean = false,
) {
    val avaliacao: AvaliacaoBateria get() = when {
        minima < 9.6f -> AvaliacaoBateria.Fraca
        minima < 10.0f || repouso < 12.2f -> AvaliacaoBateria.Atencao
        else -> AvaliacaoBateria.Boa
    }
}

/**
 * Saúde da bateria na partida: com o motor desligado guarda as últimas leituras de tensão; quando o motor
 * pega, registra a menor tensão do arranque (teste de carga da bateria) e, 20 s depois, a carga do alternador.
 * A leitura vem do adaptador OBD algumas vezes por segundo, então a queda real pode ser um pouco maior.
 */
class SaudeBateria private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("saude_bateria", Context.MODE_PRIVATE)
    /** Mais recente primeiro. */
    val partidas = mutableStateListOf<Partida>()
    var ultimaTensao by mutableStateOf<Float?>(null)
        private set
    var motorLigado by mutableStateOf(false)
        private set

    private val buffer = ArrayDeque<Pair<Long, Float>>()
    private var inicioMotor = 0L
    private var esperandoCarga = false
    private val br = Locale("pt", "BR")

    init {
        runCatching {
            val arr = JSONArray(prefs.getString("partidas", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                partidas.add(Partida(o.getLong("d"), o.getDouble("r").toFloat(), o.getDouble("m").toFloat(),
                    if (o.has("c")) o.getDouble("c").toFloat() else null, o.optBoolean("demo")))
            }
        }
    }

    fun amostra(tensao: Float?, rpm: Int?, demonstracao: Boolean, agora: Long = System.currentTimeMillis()) {
        if (tensao != null) ultimaTensao = tensao
        val ligado = (rpm ?: 0) > 400
        if (!ligado) {
            motorLigado = false
            if (tensao != null) buffer.addLast(agora to tensao)
            while (buffer.isNotEmpty() && agora - buffer.first().first > 10_000) buffer.removeFirst()
            return
        }
        if (!motorLigado) {
            motorLigado = true
            inicioMotor = agora
            registrarPartida(tensao, demonstracao, agora)
            buffer.clear()
        }
        // Carga do alternador: ~20 s depois que o motor pegou.
        if (esperandoCarga && tensao != null && agora - inicioMotor >= 20_000) {
            esperandoCarga = false
            partidas.firstOrNull()?.let { p ->
                partidas[0] = p.copy(carga = tensao)
                gravar()
                avisarSePreciso(partidas[0])
            }
        }
    }

    private fun registrarPartida(tensaoAgora: Float?, demonstracao: Boolean, agora: Long) {
        val amostras = buffer.map { it.second } + listOfNotNull(tensaoAgora)
        if (buffer.size < 2 || agora - buffer.first().first < 1_500) return // poucas leituras antes da partida
        val repouso = buffer.maxOf { it.second }
        val minima = amostras.min()
        // Só conta como partida de verdade se o motor estava parado (sem alternador) e houve queda real.
        if (repouso > 13.0f || repouso - minima < 0.3f) return
        partidas.add(0, Partida(agora, repouso, minima, demonstracao = demonstracao))
        while (partidas.size > 30) partidas.removeAt(partidas.lastIndex)
        esperandoCarga = true
        gravar()
    }

    private fun avisarSePreciso(p: Partida) {
        if (p.demonstracao || p.avaliacao == AvaliacaoBateria.Boa) return
        val agora = System.currentTimeMillis()
        if (agora - prefs.getLong("ultimo_aviso", 0L) < 24 * 3_600_000L) return
        prefs.edit().putLong("ultimo_aviso", agora).apply()
        Nuvem.obter(ctx).avisar(
            "bateria",
            "🔋 Bateria — ${p.avaliacao.rotulo}: na partida caiu para %.1f V (repouso %.1f V). %s".format(br, p.minima, p.repouso, p.avaliacao.explicacao),
        )
    }

    fun limpar() { partidas.clear(); gravar() }

    private fun gravar() {
        val arr = JSONArray()
        partidas.forEach { p ->
            val o = JSONObject().put("d", p.data).put("r", p.repouso.toDouble()).put("m", p.minima.toDouble()).put("demo", p.demonstracao)
            p.carga?.let { o.put("c", it.toDouble()) }
            arr.put(o)
        }
        prefs.edit().putString("partidas", arr.toString()).apply()
    }

    companion object {
        /** Faixa saudável de carga do alternador com o motor ligado. */
        fun cargaOk(v: Float) = v in 13.5f..14.8f

        @Volatile private var instancia: SaudeBateria? = null
        fun obter(ctx: Context): SaudeBateria = instancia ?: synchronized(this) {
            instancia ?: SaudeBateria(ctx.applicationContext).also { instancia = it }
        }
    }
}
