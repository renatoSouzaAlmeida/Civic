package br.com.civic.launcher.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Há internet de verdade (Wi-Fi, 3G/4G/5G ou outra), já validada pelo Android? */
private fun temInternet(cm: ConnectivityManager): Boolean {
    val c = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
    return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/**
 * Aviso de "Sem internet" (nem Wi-Fi nem dados móveis). Só aparece depois de 15 s sem conexão, para não piscar
 * na troca de rede ou ao ligar a multimídia; fecha sozinho quando a conexão volta. "Entendi" esconde até a próxima queda.
 */
@Composable
fun ModalSemInternet() {
    val ctx = LocalContext.current
    val cm = remember { ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var online by remember { mutableStateOf(runCatching { temInternet(cm) }.getOrDefault(true)) }
    var dispensado by remember { mutableStateOf(false) }
    var mostrar by remember { mutableStateOf(false) }

    DisposableEffect(cm) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) { online = runCatching { temInternet(cm) }.getOrDefault(true) }
            override fun onLost(n: Network) { online = runCatching { temInternet(cm) }.getOrDefault(false) }
            override fun onAvailable(n: Network) { online = runCatching { temInternet(cm) }.getOrDefault(true) }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm.unregisterNetworkCallback(cb) } }
    }
    LaunchedEffect(online) {
        if (online) { mostrar = false; dispensado = false }
        else { delay(15_000); mostrar = true }
    }

    if (mostrar && !online && !dispensado) {
        val abrir = { acao: String -> runCatching { ctx.startActivity(Intent(acao).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; Unit }
        AlertDialog(
            onDismissRequest = { dispensado = true },
            icon = { Icon(Icons.Filled.SignalCellularOff, null, tint = Cores.Vermelho, modifier = Modifier.size(40.dp)) },
            title = { Text("Sem conexão com a internet", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "A multimídia não está conectada nem ao Wi-Fi nem aos dados móveis (3G/4G).\n\n" +
                        "Sem internet ficam parados: clima, mapa, Spotify, avisos no Telegram e envio para a Casa Tech. " +
                        "Os dados do carro continuam guardados e são enviados quando a conexão voltar.",
                    fontSize = 18.sp,
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { abrir(Settings.ACTION_WIFI_SETTINGS) }) { Text("Wi-Fi", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { abrir(Settings.ACTION_DATA_ROAMING_SETTINGS) }) { Text("Dados móveis", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = { TextButton(onClick = { dispensado = true }) { Text("Entendi", fontSize = 20.sp) } },
        )
    }
}
