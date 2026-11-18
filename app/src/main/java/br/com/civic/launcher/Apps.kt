package br.com.civic.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

private const val SPOTIFY = "com.spotify.music"

fun Context.abrir(pacote: String) {
    val intent = packageManager.getLaunchIntentForPackage(pacote)
    if (intent != null) startActivity(intent)
    else Toast.makeText(this, "App não instalado: $pacote", Toast.LENGTH_SHORT).show()
}

fun Context.abrirSpotify() = abrir(SPOTIFY)

/** Abre o app de navegação escolhido em Config. (Waze ou Google Maps). */
fun Context.abrirNavegacao() = abrir(Configuracoes.obter(this).mapa.pacote)

/** Liga o painel de direção (sobre o mapa). Na 1ª vez pede a permissão "Sobrepor a outros apps". */
private fun Context.iniciarPainel(): Boolean {
    if (!Settings.canDrawOverlays(this)) {
        Toast.makeText(this, "Permita \"Sobrepor a outros apps\" para o painel de direção e toque de novo", Toast.LENGTH_LONG).show()
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

fun Context.abrirNavegacaoComPainel() {
    if (iniciarPainel()) abrirNavegacao()
}

/**
 * Navega para Casa ("Home") ou Trabalho ("Work"). Com endereço cadastrado em Config., usa o endereço
 * (funciona nos dois mapas); sem endereço, o Waze usa os favoritos dele. O Google Maps precisa do endereço.
 */
fun Context.navegarFavorito(destino: String) {
    if (!iniciarPainel()) return
    val config = Configuracoes.obter(this)
    val mapa = config.mapa
    val endereco = if (destino == "Home") config.enderecoCasa else config.enderecoTrabalho
    val intent = when {
        mapa == MapaPreferido.Waze && endereco.isNotBlank() ->
            Intent(Intent.ACTION_VIEW, Uri.parse("waze://?q=${Uri.encode(endereco)}&navigate=yes"))
        mapa == MapaPreferido.Waze ->
            Intent(Intent.ACTION_VIEW, Uri.parse("waze://?favorite=$destino&navigate=yes"))
        endereco.isNotBlank() ->
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(endereco)}"))
        else -> null
    }
    if (intent == null) {
        Toast.makeText(this, "Cadastre o endereço em Config. para usar Casa e Trabalho no Google Maps", Toast.LENGTH_LONG).show()
        abrirNavegacao()
        return
    }
    runCatching { startActivity(intent.setPackage(mapa.pacote)) }.onFailure { abrirNavegacao() }
}
