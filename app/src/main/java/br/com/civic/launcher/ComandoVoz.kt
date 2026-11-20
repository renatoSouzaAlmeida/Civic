package br.com.civic.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.civic.launcher.obd.ObdMonitor
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Comando de voz curto (botão do microfone na barra do topo). Entende: "onde parei", "quanto de combustível",
 * "ir para casa", "ir para o trabalho", "bateria", "temperatura", "consumo" e "que horas são". Responde falando.
 * Depende de um serviço de reconhecimento de voz instalado no aparelho (o Status do app mostra se há).
 */
class ComandoVoz private constructor(private val ctx: Context) {
    var ouvindo by mutableStateOf(false)
        private set
    private var reconhecedor: SpeechRecognizer? = null
    private val br = Locale("pt", "BR")

    fun disponivel() = SpeechRecognizer.isRecognitionAvailable(ctx)

    /** Começa a ouvir (chamar na thread principal, com a permissão do microfone já concedida). */
    fun ouvir() {
        if (ouvindo) return
        if (!disponivel()) { responder("O reconhecimento de voz não está disponível neste aparelho."); return }
        reconhecedor?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        reconhecedor = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { Toast.makeText(ctx, "Pode falar…", Toast.LENGTH_SHORT).show() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onError(error: Int) { ouvindo = false; responder("Não consegui ouvir. Tente de novo.") }
            override fun onResults(results: Bundle?) {
                ouvindo = false
                val frase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (frase.isNullOrBlank()) responder("Não entendi.") else interpretar(frase)
            }
        })
        ouvindo = true
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }

    private fun sem(texto: String) = Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    fun interpretar(fraseOriginal: String) {
        val f = sem(fraseOriginal)
        val d = ObdMonitor.obter(ctx).dados
        val resposta = when {
            "onde" in f && ("parei" in f || "estacion" in f || "carro" in f || "estou" in f) -> {
                val e = LocalEstacionamento.obter(ctx).atual
                if (e == null) "Ainda não sei onde você parou."
                else "Você parou ${e.endereco?.let { "em $it, " } ?: ""}às ${SimpleDateFormat("HH:mm", br).format(Date(e.data))}."
            }
            "combustivel" in f || "tanque" in f || "gasolina" in f ->
                d.nivelCombustivel?.let { "O tanque está com $it por cento." } ?: "Não consigo ler o nível do tanque agora."
            ("casa" in f) && ("ir" in f || "vou" in f || "leva" in f || "navega" in f || "rota" in f) -> { ctx.navegarFavorito("Home"); "Abrindo a rota para casa." }
            ("trabalho" in f) && ("ir" in f || "vou" in f || "leva" in f || "navega" in f || "rota" in f) -> { ctx.navegarFavorito("Work"); "Abrindo a rota para o trabalho." }
            "bateria" in f -> d.tensao?.let { "A bateria está com %.1f volts.".format(br, it) } ?: "Não consigo ler a bateria agora."
            "temperatura" in f || "motor" in f -> d.tempMotor?.let { "O motor está a $it graus." } ?: "Não consigo ler a temperatura agora."
            "consumo" in f -> Veiculo.obter(ctx).consumoMedio()?.let { "O consumo médio é de %.1f quilômetros por litro.".format(br, it) } ?: "Ainda não tenho consumo médio."
            "hora" in f -> "São ${SimpleDateFormat("HH 'e' mm", br).format(Date())}."
            else -> "Não entendi: $fraseOriginal."
        }
        responder(resposta)
    }

    private fun responder(texto: String) {
        Toast.makeText(ctx, texto, Toast.LENGTH_LONG).show()
        Voz.obter(ctx).falar(texto, forcar = true)
    }

    companion object {
        @Volatile private var instancia: ComandoVoz? = null
        fun obter(ctx: Context): ComandoVoz = instancia ?: synchronized(this) { instancia ?: ComandoVoz(ctx.applicationContext).also { instancia = it } }
    }
}
