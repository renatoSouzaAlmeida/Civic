package br.com.civic.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Uma viagem: do carro começar a andar (ou o motor ligar) até ficar 5 min parado/desligado. */
data class Viagem(
    val inicio: Long,
    val fim: Long,
    val distanciaKm: Double = 0.0,
    val emMovimentoMs: Long = 0,
    val velMaxima: Int = 0,
    val rpmMaximo: Int = 0,
    val demonstracao: Boolean = false,
) {
    val duracaoMs: Long get() = fim - inicio
    /** Média só do tempo em movimento (como o computador de bordo do carro). */
    val velMedia: Double get() = if (emMovimentoMs > 0) distanciaKm / (emMovimentoMs / 3_600_000.0) else 0.0
}

/**
 * Computador de bordo: mede a viagem atual (distância, tempo, médias, máximas) com a velocidade
 * do OBD — ou do GPS quando o OBD não informa — e guarda o histórico das últimas viagens.
 * A distância também alimenta o odômetro do app (Veículo).
 */
class ComputadorBordo private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("computador_bordo", Context.MODE_PRIVATE)
    private val principal = Handler(Looper.getMainLooper())

    var atual by mutableStateOf<Viagem?>(null)
        private set
    val historico = mutableStateListOf<Viagem>()

    private var ultimaAmostra = 0L
    private var ultimaAtividade = 0L
    private var ultimaObd = 0L

    private val verificador = object : Runnable {
        override fun run() {
            if (atual != null && System.currentTimeMillis() - ultimaAtividade > 5 * 60_000) finalizar()
            principal.postDelayed(this, 60_000)
        }
    }

    init {
        carregar()
        principal.postDelayed(verificador, 60_000)
    }

    /** Velocidade e rotação vindas do OBD (prioridade sobre o GPS). */
    fun amostraObd(velKmh: Int?, rpm: Int?, demonstracao: Boolean) {
        if (velKmh == null && rpm == null) return
        ultimaObd = System.currentTimeMillis()
        amostra((velKmh ?: 0).toFloat(), rpm ?: 0, demonstracao)
    }

    /** Velocidade do GPS: só usada se o OBD não mandou nada nos últimos 5 s. */
    fun amostraGps(velKmh: Float?) {
        if (velKmh == null || System.currentTimeMillis() - ultimaObd < 5_000) return
        amostra(velKmh, 0, false)
    }

    private fun amostra(vel: Float, rpm: Int, demonstracao: Boolean) {
        val agora = System.currentTimeMillis()
        VolumeAutomatico.obter(ctx).amostra(vel, agora) // volume pela velocidade (OBD ou GPS)
        val andando = vel >= 3f
        val ligado = rpm > 400
        var v = atual
        if (v != null && v.demonstracao != demonstracao) { finalizar(); v = null }
        if (v == null) {
            if (!andando && !ligado) return
            v = Viagem(inicio = agora, fim = agora, demonstracao = demonstracao)
            ultimaAmostra = agora
        }
        // Integra a distância; lacunas grandes (sem sinal) não contam para não inventar km.
        val dt = (agora - ultimaAmostra).coerceIn(0, 5_000)
        val km = if (andando) vel * dt / 3_600_000.0 else 0.0
        v = v.copy(
            fim = agora,
            distanciaKm = v.distanciaKm + km,
            emMovimentoMs = v.emMovimentoMs + if (andando) dt else 0,
            velMaxima = maxOf(v.velMaxima, vel.toInt()),
            rpmMaximo = maxOf(v.rpmMaximo, rpm),
        )
        ultimaAmostra = agora
        if (andando || ligado) ultimaAtividade = agora
        atual = v
        if (km > 0 && !demonstracao) Veiculo.obter(ctx).somarDistancia(km)
        prefs.edit().putString("atual", paraJson(v).toString()).apply()
    }

    /** Encerra a viagem atual (automático após 5 min parado/desligado, ou pelo botão). */
    fun finalizar() {
        val v = atual ?: return
        atual = null
        prefs.edit().remove("atual").apply()
        if (v.distanciaKm < 0.1) return // manobra na garagem não vira viagem
        historico.add(0, v)
        while (historico.size > 30) historico.removeAt(historico.lastIndex)
        gravarHistorico()
        if (!v.demonstracao) Nuvem.obter(ctx).avisar("viagem", "🏁 Viagem: ${resumo(v)}")
    }

    fun limparHistorico() { historico.clear(); gravarHistorico() }

    /** Litros e custo estimados pelo consumo médio real registrado nos abastecimentos. */
    fun estimativa(v: Viagem): Pair<Float, Float>? {
        val veiculo = Veiculo.obter(ctx)
        val consumo = veiculo.consumoMedio() ?: return null
        val litros = (v.distanciaKm / consumo).toFloat()
        val preco = veiculo.abastecimentos.lastOrNull()?.precoLitro ?: return litros to 0f
        return litros to litros * preco
    }

    fun resumo(v: Viagem): String {
        val br = Locale("pt", "BR")
        val est = estimativa(v)?.let { (l, c) -> " · ~%.1f l".format(br, l) + if (c > 0f) " (R$ %.2f)".format(br, c) else "" } ?: ""
        return "%.1f km em %s · média %.0f km/h · máx. %d km/h".format(br, v.distanciaKm, duracao(v.duracaoMs), v.velMedia, v.velMaxima) + est
    }

    private fun carregar() {
        runCatching {
            val arr = JSONArray(prefs.getString("historico", "[]"))
            for (i in 0 until arr.length()) historico.add(deJson(arr.getJSONObject(i)))
        }
        // Viagem que estava em andamento quando o app foi fechado: continua se foi há pouco, senão encerra.
        prefs.getString("atual", null)?.let { texto ->
            runCatching { deJson(JSONObject(texto)) }.getOrNull()?.let { v ->
                atual = v
                ultimaAmostra = v.fim
                ultimaAtividade = v.fim
            }
        }
    }

    private fun gravarHistorico() {
        val arr = JSONArray()
        historico.forEach { arr.put(paraJson(it)) }
        prefs.edit().putString("historico", arr.toString()).apply()
    }

    private fun paraJson(v: Viagem) = JSONObject().put("ini", v.inicio).put("fim", v.fim).put("km", v.distanciaKm)
        .put("mov", v.emMovimentoMs).put("vmax", v.velMaxima).put("rpm", v.rpmMaximo).put("demo", v.demonstracao)

    private fun deJson(o: JSONObject) = Viagem(
        o.getLong("ini"), o.getLong("fim"), o.getDouble("km"), o.getLong("mov"), o.getInt("vmax"), o.getInt("rpm"), o.optBoolean("demo"),
    )

    companion object {
        fun duracao(ms: Long): String {
            val min = ms / 60_000
            return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
        }

        @Volatile private var instancia: ComputadorBordo? = null
        fun obter(ctx: Context): ComputadorBordo = instancia ?: synchronized(this) {
            instancia ?: ComputadorBordo(ctx.applicationContext).also { instancia = it }
        }
    }
}
