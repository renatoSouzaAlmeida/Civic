package br.com.civic.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Alertas falados (síntese de voz do Android, em português): o carro avisa sem o motorista precisar olhar a tela.
 * Pede o foco de áudio "baixando" a música enquanto fala e devolve depois.
 */
class Voz private constructor(private val ctx: Context) {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private var pronto = false
    private val fila = ArrayDeque<String>()
    private var foco: AudioFocusRequest? = null
    private val tts: TextToSpeech = TextToSpeech(ctx) { status ->
        pronto = status == TextToSpeech.SUCCESS
        if (pronto) {
            runCatching { tts.language = Locale("pt", "BR") }
            tts.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onError(id: String?) = soltarFoco()
                override fun onDone(id: String?) = soltarFoco()
            })
            while (fila.isNotEmpty()) dizer(fila.removeFirst())
        }
    }

    /** Fala o texto (se "Alertas falados" estiver ligado em Config.). [forcar] ignora a chave (botão de teste). */
    fun falar(texto: String, forcar: Boolean = false) {
        if (!forcar && !Configuracoes.obter(ctx).vozAlertas) return
        if (pronto) dizer(texto) else if (fila.size < 3) fila.addLast(texto)
    }

    private fun dizer(texto: String) {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .build()
        foco = req
        runCatching { audio.requestAudioFocus(req) }
        tts.speak(texto, TextToSpeech.QUEUE_ADD, null, "civic-${System.nanoTime()}")
    }

    private fun soltarFoco() { foco?.let { runCatching { audio.abandonAudioFocusRequest(it) } }; foco = null }

    companion object {
        @Volatile private var instancia: Voz? = null
        fun obter(ctx: Context): Voz = instancia ?: synchronized(this) { instancia ?: Voz(ctx.applicationContext).also { instancia = it } }
    }
}
