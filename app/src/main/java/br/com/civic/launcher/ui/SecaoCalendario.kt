package br.com.civic.launcher.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.PonteAerea

/** Config.: endereço e token da API do calendário do Ponte Aérea (o token fica só neste aparelho, nunca no APK). */
@Composable
fun SecaoCalendario() {
    val ctx = LocalContext.current
    val pa = remember { PonteAerea.obter(ctx) }
    var base by remember { mutableStateOf(pa.baseUrl) }
    var token by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    Secao("Calendário (Ponte Aérea)") {
        Text("O botão \"Ponte Aérea\" na tela Calendário mostra os voos, viagens, feriados e eventos do seu calendário do Ponte Aérea (somente leitura). " +
            "O token fica guardado só neste aparelho: não vai no aplicativo, que é público, nem para a nuvem do launcher.", color = Cores.TextoSuave, fontSize = 13.sp)
        Text(if (pa.temToken) "Token salvo: ${pa.tokenMascarado()}" else "Nenhum token salvo.", color = if (pa.temToken) Cores.Ok else Cores.Amarelo, fontSize = 14.sp)
        Campo("Endereço da API (padrão ${PonteAerea.PADRAO_BASE})", base) { base = it }
        Campo("Token (pa_launcher_…)", token) { token = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotaoPilula("Colar", Icons.Filled.ContentPaste, Modifier.weight(1f)) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val texto = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim().orEmpty()
                if (texto.startsWith("pa_launcher_")) { token = texto; msg = "Token colado. Toque em Salvar." } else msg = "A área de transferência não tem um token (pa_launcher_…)."
            }
            BotaoPilula("Salvar", Icons.Filled.Check, Modifier.weight(1f)) {
                if (token.isNotBlank() && !token.trim().startsWith("pa_launcher_")) msg = "O token começa com pa_launcher_. Confira."
                else { pa.salvarConfig(base, token.ifBlank { null }); token = ""; msg = "Salvo." }
            }
            if (pa.temToken) BotaoPilula("Remover token", Icons.Filled.Delete, Modifier.weight(1f)) { pa.removerToken(); msg = "Token removido." }
        }
        msg?.let { Text(it, color = Cores.TextoSuave, fontSize = 13.sp) }
    }
}
