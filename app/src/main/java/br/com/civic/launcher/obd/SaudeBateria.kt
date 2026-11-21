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

/** Um achado da análise de carga elétrica. nivel: 0 = tudo certo, 1 = atenção, 2 = alerta. */
data class AchadoEletrico(val titulo: String, val texto: String, val nivel: Int)

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
                avisarAlternador()
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

    /** Alternador fraco de forma persistente (não uma leitura isolada): avisa no Telegram, no máximo uma vez por semana. */
    private fun avisarAlternador() {
        val a = analise(partidas).firstOrNull { it.nivel >= 1 && it.titulo.startsWith("Alternador") } ?: return
        val agora = System.currentTimeMillis()
        if (agora - prefs.getLong("ultimo_aviso_alt", 0L) < 7 * 24 * 3_600_000L) return
        prefs.edit().putLong("ultimo_aviso_alt", agora).apply()
        Nuvem.obter(ctx).avisar("bateria", "🔌 ${a.titulo}: ${a.texto}")
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
        /**
         * Análise de carga elétrica pelas partidas reais (sem as de demonstração): alternador, queda na partida e bateria em repouso.
         * Procura padrões que se repetem, nunca uma leitura isolada. É indício, não diagnóstico: a tensão vem do conector OBD (algumas leituras por segundo).
         */
        fun analise(todas: List<Partida>): List<AchadoEletrico> {
            val p = todas.filter { !it.demonstracao } // mais recente primeiro
            if (p.size < 3) return listOf(AchadoEletrico("Dados insuficientes", "A análise precisa de pelo menos 3 partidas reais registradas (hoje: ${p.size}). Confiança: baixa.", 0))
            val conf = when { p.size >= 15 -> "alta"; p.size >= 6 -> "média"; else -> "baixa" }
            val achados = mutableListOf<AchadoEletrico>()
            fun f(v: Float) = "%.1f".format(Locale("pt", "BR"), v)

            // Alternador (carga 20 s depois da partida).
            val cargas = p.take(6).mapNotNull { it.carga }
            if (cargas.size >= 3) {
                val media = cargas.average().toFloat()
                val fora = cargas.count { it < 13.5f }
                achados += when {
                    fora * 10 >= cargas.size * 6 && media < 13.0f -> AchadoEletrico("Alternador: carga baixa", "Em $fora de ${cargas.size} partidas a carga ficou abaixo de 13,5 V (média ${f(media)} V). Pode ser alternador, correia ou cabos; mande verificar. Confiança: $conf.", 2)
                    fora * 10 >= cargas.size * 6 -> AchadoEletrico("Alternador: carga abaixo do ideal", "Em $fora de ${cargas.size} partidas a carga ficou abaixo de 13,5 V (média ${f(media)} V). Acompanhe; se piorar, verifique o alternador. Confiança: $conf.", 1)
                    media > 14.8f -> AchadoEletrico("Alternador: carga alta", "Média de ${f(media)} V com o motor ligado, acima de 14,8 V (possível regulador de tensão). Confiança: $conf.", 1)
                    else -> AchadoEletrico("Alternador: carga normal", "Média de ${f(media)} V nas últimas ${cargas.size} partidas (faixa saudável: 13,5 a 14,8 V).", 0)
                }
            }
            // Queda de tensão na partida: as 3 mais recentes contra as 3 anteriores.
            if (p.size >= 6) {
                val recente = p.take(3).map { it.minima }.average().toFloat()
                val anterior = p.drop(3).take(3).map { it.minima }.average().toFloat()
                achados += if (anterior - recente >= 0.4f) AchadoEletrico("Partida: queda maior que antes", "A menor tensão no arranque caiu de ${f(anterior)} V para ${f(recente)} V. Bateria se desgastando ou conexões com resistência. Confiança: $conf.", 1)
                else AchadoEletrico("Partida: estável", "A menor tensão no arranque está estável (${f(recente)} V contra ${f(anterior)} V antes).", 0)
            }
            // Bateria descarregando entre os usos.
            val baixas = p.take(5).count { it.repouso < 12.2f }
            if (baixas >= 3) achados += AchadoEletrico("Bateria em repouso baixa", "Em $baixas das últimas ${minOf(5, p.size)} partidas a tensão em repouso ficou abaixo de 12,2 V. Pode ser uso só em trajetos curtos, bateria cansada ou consumo com o carro desligado (veja o teste de dreno em Diagnóstico). Confiança: $conf.", 1)
            else achados += AchadoEletrico("Bateria em repouso: normal", "Tensão em repouso normalmente acima de 12,2 V.", 0)
            return achados
        }

        /** Faixa saudável de carga do alternador com o motor ligado. */
        fun cargaOk(v: Float) = v in 13.5f..14.8f

        @Volatile private var instancia: SaudeBateria? = null
        fun obter(ctx: Context): SaudeBateria = instancia ?: synchronized(this) {
            instancia ?: SaudeBateria(ctx.applicationContext).also { instancia = it }
        }
    }
}
