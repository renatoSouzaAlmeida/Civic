package br.com.civic.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

private fun Context.atividade(): Activity? { var c: Context? = this; while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }; return null }

/** O launcher está numa metade da tela (tela dividida do sistema)? Recalcula quando o tamanho da janela muda. */
@Composable
fun emTelaDividida(): Boolean {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    return remember(cfg.screenWidthDp, cfg.screenHeightDp, cfg.orientation) { ctx.atividade()?.isInMultiWindowMode == true }
}
