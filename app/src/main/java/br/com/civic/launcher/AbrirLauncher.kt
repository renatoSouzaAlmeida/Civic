package br.com.civic.launcher

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Traz o launcher para a frente quando a multimídia liga (ou acorda da suspensão). Algumas multimídias abrem a tela do fabricante
 * por cima, mesmo com o launcher escolhido como padrão do Android. Tenta aos 3 s e de novo aos 10 s (a do fabricante pode abrir depois).
 * Só funciona com a permissão "Sobrepor a outros apps" (o Android não deixa um app abrir uma tela em segundo plano sem ela) e pode ser
 * desligado em Config. (no Fit com CarPlay, que abre direto, convém desligar).
 */
object AbrirLauncher {
    private val principal = Handler(Looper.getMainLooper())

    fun agendar(ctx: Context) {
        val app = ctx.applicationContext
        if (!Configuracoes.obter(app).abrirAoLigar || !Settings.canDrawOverlays(app)) return
        listOf(3_000L, 10_000L).forEach { espera -> principal.postDelayed({ abrir(app) }, espera) }
    }

    private fun abrir(ctx: Context) {
        if (MainActivity.visivel) return
        // Pela "tela inicial" (Início), não como app: assim o Android trata o launcher como o principal.
        LauncherPadrao.abrirComoInicio(ctx)
    }
}
