package br.com.civic.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

private const val WAZE = "com.waze"
private const val SPOTIFY = "com.spotify.music"

fun Context.abrir(pacote: String) {
    val intent = packageManager.getLaunchIntentForPackage(pacote)
    if (intent != null) startActivity(intent)
    else Toast.makeText(this, "App não instalado: $pacote", Toast.LENGTH_SHORT).show()
}

fun Context.abrirWaze() = abrir(WAZE)
fun Context.abrirSpotify() = abrir(SPOTIFY)

/** Liga o painel de direção (sobre o Waze). Na 1ª vez pede a permissão "Sobrepor a outros apps". */
private fun Context.iniciarPainel(): Boolean {
    if (!Settings.canDrawOverlays(this)) {
        Toast.makeText(this, "Permita \"Sobrepor a outros apps\" para o painel de direção e toque no Waze de novo", Toast.LENGTH_LONG).show()
        runCatching {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return false
    }
    startService(Intent(this, PainelService::class.java))
    return true
}

fun Context.abrirWazeComPainel() {
    if (iniciarPainel()) abrirWaze()
}

/** Destino favorito do Waze (Home/Work). Cai no app se o link falhar. */
fun Context.navegarWazeFavorito(favorito: String) {
    if (!iniciarPainel()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("waze://?favorite=$favorito&navigate=yes"))
        .setPackage(WAZE)
    runCatching { startActivity(intent) }.onFailure { abrirWaze() }
}
