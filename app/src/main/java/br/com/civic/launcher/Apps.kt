package br.com.civic.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

private const val WAZE = "com.waze"
private const val SPOTIFY = "com.spotify.music"

private fun Context.abrir(pacote: String) {
    val intent = packageManager.getLaunchIntentForPackage(pacote)
    if (intent != null) startActivity(intent)
    else Toast.makeText(this, "App não instalado: $pacote", Toast.LENGTH_SHORT).show()
}

fun Context.abrirWaze() = abrir(WAZE)
fun Context.abrirSpotify() = abrir(SPOTIFY)

/** Destino favorito do Waze (Home/Work). Cai no app se o link falhar. */
fun Context.navegarWazeFavorito(favorito: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("waze://?favorite=$favorito&navigate=yes"))
        .setPackage(WAZE)
    runCatching { startActivity(intent) }.onFailure { abrirWaze() }
}
