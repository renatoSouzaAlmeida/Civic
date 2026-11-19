package br.com.civic.launcher

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Retomar a música de onde parou: enquanto o motor está ligado, o app guarda se a música estava tocando.
 * Na próxima partida (motor ligando), se ela estava tocando quando o carro foi desligado, manda o Spotify
 * dar "play" — ele continua da faixa e do ponto em que parou. Se você tinha pausado de propósito, não retoma.
 *
 * Também vale logo depois do boot da multimídia (o launcher abre junto com o carro).
 */
class RetomarMusica private constructor(private val ctx: Context) {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val prefs = ctx.getSharedPreferences("retomar_musica", Context.MODE_PRIVATE)
    private val principal = Handler(Looper.getMainLooper())

    var ligado by mutableStateOf(prefs.getBoolean("ligado", true))
        private set

    private var primeira = true
    private var motorLigado = false
    private var ultimaChecagem = 0L
    private var agendado = false

    fun definirLigado(v: Boolean) { ligado = v; prefs.edit().putBoolean("ligado", v).apply() }

    /** Chamado a cada leitura do OBD. */
    fun amostra(rpm: Int?, demonstracao: Boolean) {
        if (demonstracao) return
        val girando = (rpm ?: 0) > 400
        val agora = SystemClock.elapsedRealtime()
        if (girando) {
            val partida = !motorLigado && (!primeira || agora < 10 * 60_000L) // 1ª leitura só vale se o aparelho acabou de ligar
            if (partida && ligado && prefs.getBoolean("tocava", false) && !agendado) {
                agendado = true
                principal.postDelayed({ agendado = false; retomar() }, 6_000) // espera o áudio e o Bluetooth se acertarem
            }
            motorLigado = true
            // Com o motor ligado, guarda se a música está tocando (a multimídia pode pausar sozinha ao desligar).
            if (agora - ultimaChecagem >= 3_000) {
                ultimaChecagem = agora
                val tocando = runCatching { audio.isMusicActive }.getOrDefault(false)
                if (tocando != prefs.getBoolean("tocava", false)) prefs.edit().putBoolean("tocava", tocando).apply()
            }
        } else {
            motorLigado = false
        }
        primeira = false
    }

    /** Manda o Spotify tocar (se não houver música tocando). Devolve false se não deu para tentar. */
    fun retomar(): Boolean {
        if (runCatching { audio.isMusicActive }.getOrDefault(false)) return true // já está tocando
        if (audio.mode != AudioManager.MODE_NORMAL) return false // em chamada
        val spotify = runCatching {
            ctx.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(ctx, MediaListenerService::class.java))
                .firstOrNull { it.packageName == "com.spotify.music" }
        }.getOrNull()
        if (spotify != null) {
            spotify.transportControls.play()
        } else {
            // Sem sessão ativa (acesso às notificações desligado ou Spotify fechado): usa a tecla "play" de mídia.
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
        }
        return true
    }

    companion object {
        @Volatile private var instancia: RetomarMusica? = null
        fun obter(ctx: Context): RetomarMusica = instancia ?: synchronized(this) {
            instancia ?: RetomarMusica(ctx.applicationContext).also { instancia = it }
        }
    }
}
