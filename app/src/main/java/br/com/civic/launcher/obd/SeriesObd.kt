package br.com.civic.launcher.obd

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Últimos 60 s de alguns indicadores (uma amostra por segundo), para os gráficos de linha dos layouts do OBD. */
class SeriesObd private constructor() {
    private val max = 60
    private val ultimas = HashMap<String, ArrayDeque<Float>>()
    private var ultimo = 0L
    /** Muda a cada amostra: quem desenha lê isto para atualizar. */
    var versao by mutableStateOf(0)
        private set

    fun amostra(d: DadosObd) {
        val agora = System.currentTimeMillis()
        if (agora - ultimo < 1_000) return
        ultimo = agora
        add("tempMotor", d.tempMotor?.toFloat()); add("tempAdmissao", d.tempAdmissao?.toFloat()); add("tensao", d.tensao)
        add("map", d.map?.toFloat()); add("carga", d.carga?.toFloat()); add("rpm", d.rpm?.toFloat()); add("velocidade", d.velocidade?.toFloat())
        versao++
    }

    private fun add(nome: String, v: Float?) {
        v ?: return
        val fila = ultimas.getOrPut(nome) { ArrayDeque() }
        fila.addLast(v); while (fila.size > max) fila.removeFirst()
    }

    fun serie(nome: String): List<Float> = ultimas[nome]?.toList() ?: emptyList()

    companion object {
        @Volatile private var instancia: SeriesObd? = null
        fun obter(): SeriesObd = instancia ?: synchronized(this) { instancia ?: SeriesObd().also { instancia = it } }
    }
}
