package br.com.civic.launcher.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Paleta completa de um tema (Direção F: clean da B + disposição da E). */
@Immutable
class Paleta(
    val fundo: Color,
    val fundoB: Color,
    val cartao: Color,
    val cartaoSolido: Color,
    val cartaoAlto: Color,
    val borda: Color,
    val chip: Color,
    val chipBorda: Color,
    val destaque: Color,
    val vermelho: Color,
    val ok: Color,
    val spotify: Color,
    val waze: Color,
    val amarelo: Color,
    val texto: Color,
    val textoSuave: Color,
    val segVazio: Color,
    val segAlerta: Color,
    val mapaFundo: Color,
    val mapaVia: Color,
    val mapaParque: Color,
    val degradeWaze: Color,
    val degradeSpotify: Color,
    val escuro: Boolean,
)

val PaletaEscura = Paleta(
    fundo = Color(0xFF0A1530),
    fundoB = Color(0xFF08122A),
    cartao = Color(0x1FFFFFFF),
    cartaoSolido = Color(0xFF0F1D3D),
    cartaoAlto = Color(0xFF1B2A52),
    borda = Color(0x2EFFFFFF),
    chip = Color(0x990A142D),
    chipBorda = Color(0x2EFFFFFF),
    destaque = Color(0xFF3D8BFF),
    vermelho = Color(0xFFFF6A6A),
    ok = Color(0xFF43E8A0),
    spotify = Color(0xFF1DB954),
    waze = Color(0xFF33CCFF),
    amarelo = Color(0xFFFFC043),
    texto = Color(0xFFF1F6FF),
    textoSuave = Color(0xFF9FB6E0),
    segVazio = Color(0x24FFFFFF),
    segAlerta = Color(0x40FF5050),
    mapaFundo = Color(0xFF0F1B2E),
    mapaVia = Color(0xFF1D3050),
    mapaParque = Color(0xFF12302A),
    degradeWaze = Color(0xFF0E2A3A),
    degradeSpotify = Color(0xFF123524),
    escuro = true,
)

// Tons mais fortes que no escuro, para manter o contraste sobre fundo claro.
val PaletaClara = Paleta(
    fundo = Color(0xFFF1F4FA),
    fundoB = Color(0xFFE2E9F4),
    cartao = Color(0xE0FFFFFF),
    cartaoSolido = Color(0xFFFFFFFF),
    cartaoAlto = Color(0xFFE3E8F0),
    borda = Color(0xFFFFFFFF),
    chip = Color(0xFFF1F5F9),
    chipBorda = Color(0xFFE2E8F0),
    destaque = Color(0xFF0A84FF),
    vermelho = Color(0xFFF04438),
    ok = Color(0xFF12B76A),
    spotify = Color(0xFF148A3F),
    waze = Color(0xFF0A86B8),
    amarelo = Color(0xFFC98200),
    texto = Color(0xFF0F172A),
    textoSuave = Color(0xFF64748B),
    segVazio = Color(0xFFE2E8F0),
    segAlerta = Color(0xFFFECACA),
    mapaFundo = Color(0xFFE8EDF3),
    mapaVia = Color(0xFFFFFFFF),
    mapaParque = Color(0xFFD9EBDC),
    degradeWaze = Color(0xFFD3ECF7),
    degradeSpotify = Color(0xFFD2EFDD),
    escuro = false,
)

val LocalPaleta = staticCompositionLocalOf { PaletaEscura }

/** Atalho para a paleta do tema atual: Cores.Fundo, Cores.Texto etc. */
object Cores {
    val Fundo: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.fundo
    val FundoB: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.fundoB
    val Cartao: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.cartao
    val CartaoSolido: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.cartaoSolido
    val CartaoAlto: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.cartaoAlto
    val Borda: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.borda
    val Chip: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.chip
    val ChipBorda: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.chipBorda
    val Destaque: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.destaque
    val Vermelho: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.vermelho
    val Ok: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.ok
    val Spotify: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.spotify
    val Waze: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.waze
    val Amarelo: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.amarelo
    val Texto: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.texto
    val TextoSuave: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.textoSuave
    val SegVazio: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.segVazio
    val SegAlerta: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.segAlerta
    val MapaFundo: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.mapaFundo
    val MapaVia: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.mapaVia
    val MapaParque: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.mapaParque
    val DegradeWaze: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.degradeWaze
    val DegradeSpotify: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.degradeSpotify
}

@Composable
fun CivicTheme(escuro: Boolean = true, content: @Composable () -> Unit) {
    val paleta = if (escuro) PaletaEscura else PaletaClara
    val esquema = if (escuro) {
        darkColorScheme(
            background = paleta.fundo, surface = paleta.cartaoSolido, primary = paleta.destaque,
            onBackground = paleta.texto, onSurface = paleta.texto,
        )
    } else {
        lightColorScheme(
            background = paleta.fundo, surface = paleta.cartaoSolido, primary = paleta.destaque,
            onBackground = paleta.texto, onSurface = paleta.texto,
        )
    }
    CompositionLocalProvider(LocalPaleta provides paleta) {
        MaterialTheme(colorScheme = esquema, content = content)
    }
}

/** Automático = claro de dia (6h às 18h) e escuro à noite. */
enum class ModoTema(val rotulo: String) {
    Automatico("Auto"), Claro("Claro"), Escuro("Escuro");

    fun proximo() = entries[(ordinal + 1) % entries.size]

    fun escuro(hora: Int): Boolean = when (this) {
        Automatico -> hora !in 6..17
        Claro -> false
        Escuro -> true
    }

    companion object {
        private const val ARQUIVO = "preferencias"
        private const val CHAVE = "modo_tema"

        fun ler(ctx: Context): ModoTema = runCatching {
            valueOf(ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).getString(CHAVE, null) ?: "")
        }.getOrDefault(Automatico)

        fun salvar(ctx: Context, modo: ModoTema) {
            ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit().putString(CHAVE, modo.name).apply()
        }
    }
}
