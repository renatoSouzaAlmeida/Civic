package br.com.civic.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Cores {
    val Fundo = Color(0xFF0B0D12)
    val Cartao = Color(0xFF151923)
    val CartaoAlto = Color(0xFF1E2431)
    val Destaque = Color(0xFFE5383B) // vermelho do tema atual da multimídia
    val Spotify = Color(0xFF1DB954)
    val Waze = Color(0xFF33CCFF)
    val Amarelo = Color(0xFFFFC043)
    val Texto = Color(0xFFF2F4F8)
    val TextoSuave = Color(0xFF9AA3B5)
}

@Composable
fun CivicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Cores.Fundo,
            surface = Cores.Cartao,
            primary = Cores.Destaque,
            onBackground = Cores.Texto,
            onSurface = Cores.Texto,
        ),
        content = content,
    )
}
