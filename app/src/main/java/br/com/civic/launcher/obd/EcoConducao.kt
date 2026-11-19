package br.com.civic.launcher.obd

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Cores do "ECO Assist" do Civic: verde econômico, azul claro normal, azul escuro alto consumo. */
enum class NivelEco(val rotulo: String, val cor: Color) {
    Economico("econômico", Color(0xFF22C55E)),
    Normal("normal", Color(0xFF38BDF8)),
    AltoConsumo("alto consumo", Color(0xFF1D4ED8)),
}

/**
 * Reproduz o anel ECO Assist do Civic G9 (o sinal original não sai pelo OBD) a partir da carga do motor,
 * do rpm e da aceleração. Uma nota de 0 (econômico) a 1 (alto consumo) é suavizada para a cor não piscar.
 */
class EcoConducao private constructor() {
    /** Nível atual, ou null quando não há dados do motor (sem OBD). */
    var nivel by mutableStateOf<NivelEco?>(null)
        private set
    /** Nota suavizada de 0 a 1 (para quem quiser desenhar uma barra). */
    var nota by mutableStateOf(0f)
        private set

    private var ultimaVel: Float? = null
    private var ultimoTempo = 0L

    fun amostra(d: DadosObd, agora: Long = System.currentTimeMillis()) {
        val rpm = d.rpm
        val vel = d.velocidade?.toFloat()
        if (rpm == null || rpm < 400) { nivel = null; ultimaVel = null; return } // motor desligado
        // Aceleração em km/h por segundo.
        val acel = if (vel != null && ultimaVel != null && agora > ultimoTempo) (vel - ultimaVel!!) / ((agora - ultimoTempo) / 1000f) else 0f
        ultimaVel = vel
        ultimoTempo = agora

        val carga = d.carga
        val bruta = when {
            // Andando com o pé fora do acelerador (desacelerando/embalado): é o verde do Civic.
            carga != null && carga < 15 && (vel ?: 0f) > 5f -> 0f
            else -> maxOf(
                carga?.let { ((it - 30f) / 40f) } ?: 0f, // 30% → começa a subir, 70% → máximo
                (rpm - 2500f) / 1500f, // 2.500 → 4.000 rpm
                (acel - 2f) / 6f, // acelerando forte
            ).coerceIn(0f, 1f)
        }
        // Parado em marcha lenta: azul claro, como no painel.
        val alvo = if ((vel ?: 0f) < 3f && carga != null && carga < 40) 0.45f else bruta
        nota = if (nivel == null) alvo else nota * 0.65f + alvo * 0.35f
        nivel = when {
            nota < 0.33f -> NivelEco.Economico
            nota < 0.66f -> NivelEco.Normal
            else -> NivelEco.AltoConsumo
        }
    }

    companion object {
        @Volatile private var instancia: EcoConducao? = null
        fun obter(@Suppress("UNUSED_PARAMETER") ctx: Context? = null): EcoConducao = instancia ?: synchronized(this) {
            instancia ?: EcoConducao().also { instancia = it }
        }
    }
}
