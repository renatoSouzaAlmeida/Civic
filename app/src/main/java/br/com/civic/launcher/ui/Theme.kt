package br.com.civic.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
class Paleta(
    val fundo: Color,
    val cartao: Color,
    val cartaoAlto: Color,
    val destaque: Color,
    val spotify: Color,
    val waze: Color,
    val amarelo: Color,
    val texto: Color,
    val textoSuave: Color,
    val degradeWaze: Color,
    val degradeSpotify: Color,
    val escuro: Boolean,
)

val PaletaEscura = Paleta(
    fundo = Color(0xFF0B0D12),
    cartao = Color(0xFF151923),
    cartaoAlto = Color(0xFF1E2431),
    destaque = Color(0xFFE5383B), // vermelho do tema atual da multimídia
    spotify = Color(0xFF1DB954),
    waze = Color(0xFF33CCFF),
    amarelo = Color(0xFFFFC043),
    texto = Color(0xFFF2F4F8),
    textoSuave = Color(0xFF9AA3B5),
    degradeWaze = Color(0xFF0E2A3A),
    degradeSpotify = Color(0xFF123524),
    escuro = true,
)

// Tons mais fortes que no escuro, para manter o contraste sobre fundo branco.
val PaletaClara = Paleta(
    fundo = Color(0xFFEEF1F6),
    cartao = Color(0xFFFFFFFF),
    cartaoAlto = Color(0xFFE3E8F0),
    destaque = Color(0xFFD62839),
    spotify = Color(0xFF148A3F),
    waze = Color(0xFF0A86B8),
    amarelo = Color(0xFFC98200),
    texto = Color(0xFF0E121B),
    textoSuave = Color(0xFF566074),
    degradeWaze = Color(0xFFD3ECF7),
    degradeSpotify = Color(0xFFD2EFDD),
    escuro = false,
)

val LocalPaleta = staticCompositionLocalOf { PaletaEscura }

/** Atalho para a paleta do tema atual: Cores.Fundo, Cores.Texto etc. */
object Cores {
    val Fundo: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.fundo
    val Cartao: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.cartao
    val CartaoAlto: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.cartaoAlto
    val Destaque: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.destaque
    val Spotify: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.spotify
    val Waze: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.waze
    val Amarelo: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.amarelo
    val Texto: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.texto
    val TextoSuave: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.textoSuave
    val DegradeWaze: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.degradeWaze
    val DegradeSpotify: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.degradeSpotify
}

@Composable
fun CivicTheme(escuro: Boolean = true, content: @Composable () -> Unit) {
    val paleta = if (escuro) PaletaEscura else PaletaClara
    val esquema = if (escuro) {
        darkColorScheme(
            background = paleta.fundo, surface = paleta.cartao, primary = paleta.destaque,
            onBackground = paleta.texto, onSurface = paleta.texto,
        )
    } else {
        lightColorScheme(
            background = paleta.fundo, surface = paleta.cartao, primary = paleta.destaque,
            onBackground = paleta.texto, onSurface = paleta.texto,
        )
    }
    CompositionLocalProvider(LocalPaleta provides paleta) {
        MaterialTheme(colorScheme = esquema, content = content)
    }
}
