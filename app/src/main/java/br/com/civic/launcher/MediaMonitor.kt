package br.com.civic.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat

data class MusicaUi(
    val permitido: Boolean = false,
    val temSessao: Boolean = false,
    val titulo: String = "",
    val artista: String = "",
    val capa: Bitmap? = null,
    val tocando: Boolean = false,
)

/** Observa a sessão de mídia ativa (Spotify tem prioridade) e expõe estado + controles. */
class MediaMonitor(private val ctx: Context) {
    var estado by mutableStateOf(MusicaUi())
        private set

    private val gerente = ctx.getSystemService(MediaSessionManager::class.java)
    private val componente = ComponentName(ctx, MediaListenerService::class.java)
    private var controller: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = atualizar()
        override fun onPlaybackStateChanged(state: PlaybackState?) = atualizar()
        override fun onSessionDestroyed() = escolherSessao()
    }
    private val aoMudarSessoes = MediaSessionManager.OnActiveSessionsChangedListener { escolherSessao() }

    fun temAcesso(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** Seguro chamar várias vezes (ex.: ao voltar das Configurações). */
    fun iniciar() {
        parar()
        if (!temAcesso()) {
            estado = MusicaUi(permitido = false)
            return
        }
        runCatching { gerente.addOnActiveSessionsChangedListener(aoMudarSessoes, componente) }
        escolherSessao()
    }

    fun parar() {
        runCatching { gerente.removeOnActiveSessionsChangedListener(aoMudarSessoes) }
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun escolherSessao() {
        val sessoes = runCatching { gerente.getActiveSessions(componente) }.getOrDefault(emptyList())
        val nova = sessoes.firstOrNull { it.packageName == "com.spotify.music" }
            ?: sessoes.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessoes.firstOrNull()
        if (nova?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = nova
            nova?.registerCallback(callback)
        }
        atualizar()
    }

    private fun atualizar() {
        val c = controller
        if (c == null) {
            estado = MusicaUi(permitido = true)
            return
        }
        val m = c.metadata
        estado = MusicaUi(
            permitido = true,
            temSessao = true,
            titulo = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artista = m?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            capa = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            tocando = c.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }

    fun alternar() {
        val t = controller?.transportControls ?: return
        if (estado.tocando) t.pause() else t.play()
    }

    fun proxima() { controller?.transportControls?.skipToNext() }
    fun anterior() { controller?.transportControls?.skipToPrevious() }
}
