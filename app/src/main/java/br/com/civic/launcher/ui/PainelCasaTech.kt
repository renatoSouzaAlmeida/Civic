package br.com.civic.launcher.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import br.com.civic.launcher.Nuvem
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val URL_PAINEL = "https://casa-tech-renatin.vercel.app/veiculo/launcher"
private const val URL_FUNCAO = "https://qdmsiwxfuzppaqqnljth.supabase.co/functions/v1/civic-painel"

/**
 * Ponte nativa `CivicLauncher` do painel da Casa Tech (ver docs/LAUNCHER-PAINEL.md): troca o token do aparelho por um acesso curto
 * (15 min, só leitura) e entrega à página. O token nunca vai para a página nem para a URL, e nada disto vai para o log.
 */
private class PonteCivic(private val token: () -> String) {
    @Volatile private var acesso: String? = null
    @Volatile private var expiraEm = 0L

    /** O WebView chama isto em thread própria: pode bloquear na rede. Texto vazio = a página mostra "Sem autorização". */
    @JavascriptInterface
    fun acesso(): String {
        val agora = System.currentTimeMillis() / 1000
        if (acesso == null || agora > expiraEm - 120) renovar()
        return acesso ?: ""
    }

    private fun renovar() {
        runCatching {
            val c = URL(URL_FUNCAO).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-civic-token", token())
            c.connectTimeout = 8_000; c.readTimeout = 8_000; c.doOutput = true
            c.outputStream.use { it.write("""{"acao":"entrar"}""".toByteArray()) }
            if (c.responseCode == 200) {
                val j = JSONObject(c.inputStream.bufferedReader().readText())
                acesso = j.getString("acesso"); expiraEm = j.getLong("expira_em")
            } else { acesso = null }
        }.onFailure { acesso = null }
    }
}

/** Painel resumido da Casa Tech dentro do launcher: página somente leitura, aberta numa WebView travada nesse endereço. */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun TelaPainelCasaTech() {
    val ctx = LocalContext.current
    val nuvem = remember { Nuvem.obter(ctx) }
    var semConexao by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    // Sem internet: tenta de novo sozinha a cada 15 s.
    LaunchedEffect(semConexao) { while (semConexao) { delay(15_000); web?.reload() } }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportMultipleWindows(false)
                    addJavascriptInterface(PonteCivic { nuvem.token }, "CivicLauncher")
                    webViewClient = object : WebViewClient() {
                        // Só o painel abre; qualquer outro endereço é bloqueado (a ponte nunca fica exposta a outro site).
                        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = !r.url.toString().startsWith(URL_PAINEL)
                        override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) { semConexao = false }
                        override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) { if (r.isForMainFrame) semConexao = true }
                    }
                    web = this
                    loadUrl(URL_PAINEL)
                }
            },
            onRelease = { w -> w.removeJavascriptInterface("CivicLauncher"); w.destroy(); web = null },
        )
        if (semConexao) Text("Sem conexão com a internet. Tentando de novo…", color = Cores.TextoSuave, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center))
    }
}
