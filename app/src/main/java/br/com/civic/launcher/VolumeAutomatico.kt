package br.com.civic.launcher

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class NivelVolumeAuto(val rotulo: String, val passos: Int) {
    Desligado("Desligado", 0), Leve("Leve", 2), Medio("Médio", 4), Forte("Forte", 6),
}

/**
 * Volume automático pela velocidade: compensa o ruído da estrada subindo o volume de mídia aos poucos
 * a partir de 40 km/h (máximo perto de 110 km/h) e voltando ao normal ao desacelerar.
 *
 * - A referência é o volume que o motorista escolhe devagar ou parado; se ele mexer no volume,
 *   o app aceita o novo valor como referência (nunca briga com o motorista).
 * - Muda de um em um passo, com pelo menos 4 s entre passos, para não dar "pulos".
 * - Usa a velocidade do OBD (ou do GPS) e por isso continua com o Waze na frente.
 */
class VolumeAutomatico private constructor(ctx: Context) {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val prefs = ctx.getSharedPreferences("volume_auto", Context.MODE_PRIVATE)

    var nivel by mutableStateOf(runCatching { NivelVolumeAuto.valueOf(prefs.getString("nivel", "") ?: "") }.getOrDefault(NivelVolumeAuto.Desligado))
        private set
    /** Passos somados ao volume de referência neste momento (para mostrar em Config.). */
    var ajusteAtual by mutableIntStateOf(0)
        private set
    var volumeBase by mutableIntStateOf(-1)
        private set

    private var ultimoAplicado = -1
    private var ultimaMudanca = 0L

    fun definirNivel(novo: NivelVolumeAuto) {
        if (novo == NivelVolumeAuto.Desligado) restaurar()
        nivel = novo
        prefs.edit().putString("nivel", novo.name).apply()
    }

    fun amostra(velKmh: Float, agora: Long = System.currentTimeMillis()) {
        if (nivel == NivelVolumeAuto.Desligado) return
        runCatching {
            if (audio.mode != AudioManager.MODE_NORMAL) return // em chamada, não mexe
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val atual = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            // O volume mudou por fora (motorista, botão do volante…): vira a nova referência.
            if (volumeBase < 0 || (ultimoAplicado >= 0 && atual != ultimoAplicado)) {
                volumeBase = (atual - ajusteAtual).coerceIn(0, max)
                if (ultimoAplicado >= 0 && atual != ultimoAplicado) ajusteAtual = atual - volumeBase
            }
            // Rampa: 0 abaixo de 40 km/h, máximo em 110 km/h. Os passos escalam com o alcance do volume (padrão 15).
            val rampa = ((velKmh - 40f) / 70f).coerceIn(0f, 1f)
            val alvoPassos = Math.round(rampa * nivel.passos * max / 15f)
            val alvo = (volumeBase + alvoPassos).coerceIn(0, max)
            if (alvo == atual || agora - ultimaMudanca < 4_000) return
            val novo = if (alvo > atual) atual + 1 else atual - 1
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, novo, 0)
            ultimoAplicado = novo
            ajusteAtual = novo - volumeBase
            ultimaMudanca = agora
        }
    }

    /** Devolve o volume à referência (ao desligar a função). */
    private fun restaurar() {
        runCatching {
            if (volumeBase >= 0 && ultimoAplicado >= 0 && audio.getStreamVolume(AudioManager.STREAM_MUSIC) == ultimoAplicado) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBase, 0)
            }
        }
        volumeBase = -1; ultimoAplicado = -1; ajusteAtual = 0
    }

    companion object {
        @Volatile private var instancia: VolumeAutomatico? = null
        fun obter(ctx: Context): VolumeAutomatico = instancia ?: synchronized(this) {
            instancia ?: VolumeAutomatico(ctx.applicationContext).also { instancia = it }
        }
    }
}
