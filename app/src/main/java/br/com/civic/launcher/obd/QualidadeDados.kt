package br.com.civic.launcher.obd

import java.util.Locale

/** De onde vem cada dado. Nada é mostrado como medição se for estimativa. */
enum class FonteDado(val rotulo: String) { Obd("OBD"), Gps("GPS"), Sensor("Sensor do aparelho"), Calculado("Calculado"), Usuario("Informado") }

/** Estado de uma leitura: Atual (dentro da validade), Atrasada (passou da validade), Indisponível (nunca lida ou adaptador sem resposta). */
enum class EstadoLeitura(val rotulo: String) { Atual("Atual"), Atrasada("Atrasada"), Indisponivel("Indisponível") }

/** Uma leitura com valor, unidade, origem, hora e validade. `descartadas` = quantas leituras fora do possível foram jogadas fora. */
data class LeituraQ(
    val campo: String, val valor: String?, val unidade: String, val fonte: FonteDado,
    val em: Long, val validadeMs: Long, val estado: EstadoLeitura, val descartadas: Int = 0,
)

/**
 * Camada de qualidade e origem dos dados do OBD: cada leitura carrega valor, unidade, fonte, quando foi lida e por quanto tempo vale;
 * valores impossíveis (ex.: 300 °C no motor) são descartados e contados, em vez de contaminar alertas e gráficos.
 */
object QualidadeDados {
    /** Faixa fisicamente possível de cada campo (mínimo, máximo), na unidade do campo. */
    private val FAIXAS = mapOf(
        "rpm" to (0.0 to 10_000.0), "velocidade" to (0.0 to 300.0), "tempMotor" to (-40.0 to 150.0), "tensao" to (6.0 to 20.0),
        "carga" to (0.0 to 100.0), "tempAdmissao" to (-40.0 to 120.0), "acelerador" to (0.0 to 100.0), "nivelCombustivel" to (0.0 to 100.0),
        "map" to (0.0 to 255.0), "avanco" to (-64.0 to 64.0), "stft" to (-100.0 to 100.0), "ltft" to (-100.0 to 100.0), "tempCambio" to (-40.0 to 200.0),
    )

    fun plausivel(campo: String, valor: Double): Boolean = FAIXAS[campo]?.let { valor in it.first..it.second } ?: true

    /** Devolve os dados com os valores impossíveis trocados por nulo; chama `aoDescartar(campo)` para cada um. */
    fun sanear(d: DadosObd, aoDescartar: (String) -> Unit): DadosObd {
        var r = d
        fun ok(campo: String, v: Number?): Boolean = v == null || plausivel(campo, v.toDouble()).also { if (!it) aoDescartar(campo) }
        if (!ok("rpm", r.rpm)) r = r.copy(rpm = null)
        if (!ok("velocidade", r.velocidade)) r = r.copy(velocidade = null)
        if (!ok("tempMotor", r.tempMotor)) r = r.copy(tempMotor = null)
        if (!ok("tensao", r.tensao)) r = r.copy(tensao = null)
        if (!ok("carga", r.carga)) r = r.copy(carga = null)
        if (!ok("tempAdmissao", r.tempAdmissao)) r = r.copy(tempAdmissao = null)
        if (!ok("acelerador", r.acelerador)) r = r.copy(acelerador = null)
        if (!ok("nivelCombustivel", r.nivelCombustivel)) r = r.copy(nivelCombustivel = null)
        if (!ok("map", r.map)) r = r.copy(map = null)
        if (!ok("avanco", r.avanco)) r = r.copy(avanco = null)
        if (!ok("stft", r.stft)) r = r.copy(stft = null)
        if (!ok("ltft", r.ltft)) r = r.copy(ltft = null)
        if (!ok("tempCambio", r.tempCambio)) r = r.copy(tempCambio = null)
        return r
    }

    fun estado(valor: Any?, em: Long, validadeMs: Long, agora: Long, adaptadorRespondendo: Boolean): EstadoLeitura = when {
        valor == null || em == 0L -> EstadoLeitura.Indisponivel
        !adaptadorRespondendo || agora - em > validadeMs -> EstadoLeitura.Atrasada
        else -> EstadoLeitura.Atual
    }

    /** "há 12 s", "há 3 min", "há 2 h". */
    fun idade(em: Long, agora: Long): String {
        if (em == 0L) return "nunca"
        val s = ((agora - em) / 1000).coerceAtLeast(0)
        return "há " + if (s < 90) "$s s" else if (s < 5400) "${s / 60} min" else "${s / 3600} h"
    }

    fun formatar(v: Number?, casas: Int = 0): String? = v?.let { if (casas == 0) it.toDouble().let { d -> Math.round(d).toString() } else "%.${casas}f".format(Locale("pt", "BR"), it.toDouble()) }
}
