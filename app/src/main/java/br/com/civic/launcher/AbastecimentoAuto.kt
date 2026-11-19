package br.com.civic.launcher

import android.content.Context
import android.os.SystemClock

/**
 * Detecta abastecimento pelo nível do tanque (PID 012F do OBD): o nível só desce com o uso, então uma subida
 * firme (10 pontos percentuais ou mais) significa que o tanque foi abastecido. Compara a mediana das últimas
 * 5 leituras (o combustível balança na estrada) com o último nível estável, e cadastra o abastecimento na hora:
 * gasolina, tanque cheio, litros estimados pela capacidade do tanque. O valor pago é informado depois.
 * Só funciona se o carro informar o nível do tanque pelo OBD.
 */
class AbastecimentoAuto private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("abastecimento_auto", Context.MODE_PRIVATE)
    private var base: Float? = if (prefs.contains("base")) prefs.getFloat("base", 0f) else null
    private val leituras = ArrayDeque<Int>()
    private var ultimaLeitura = 0L

    /** A cada leitura do OBD. [nivel] é a % do tanque (nulo = o carro não informa). */
    fun amostra(nivel: Int?, rpm: Int?, demonstracao: Boolean) {
        if (demonstracao || nivel == null || (rpm ?: 0) <= 400) return
        val agora = SystemClock.elapsedRealtime()
        if (agora - ultimaLeitura < 8_000) return // uma leitura a cada 8 s, no mínimo
        ultimaLeitura = agora
        leituras.addLast(nivel)
        if (leituras.size > 5) leituras.removeFirst()
        if (leituras.size < 5) return
        val mediana = leituras.sorted()[2]
        val antes = base
        if (antes != null && mediana - antes >= SUBIDA_MINIMA) {
            registrar(antes.toInt(), mediana)
            leituras.clear()
        }
        base = mediana.toFloat()
        prefs.edit().putFloat("base", mediana.toFloat()).apply()
    }

    private fun registrar(antes: Int, depois: Int) {
        val agoraMs = System.currentTimeMillis()
        if (agoraMs - prefs.getLong("ultimo", 0L) < 10 * 60_000L) return // o mesmo abastecimento não vira dois
        prefs.edit().putLong("ultimo", agoraMs).apply()
        val v = Veiculo.obter(ctx)
        val litros = (depois - antes) / 100f * Configuracoes.obter(ctx).capacidadeEfetiva
        v.adicionarAutomatico(
            Abastecimento(
                id = agoraMs, data = agoraMs, km = v.kmAtual, litros = litros, precoLitro = 0f,
                combustivel = Combustivel.Gasolina, tanqueCheio = true,
                automatico = true, nivelAntes = antes, nivelDepois = depois,
            )
        )
    }

    companion object {
        private const val SUBIDA_MINIMA = 10
        @Volatile private var instancia: AbastecimentoAuto? = null
        fun obter(ctx: Context): AbastecimentoAuto = instancia ?: synchronized(this) {
            instancia ?: AbastecimentoAuto(ctx.applicationContext).also { instancia = it }
        }
    }
}
