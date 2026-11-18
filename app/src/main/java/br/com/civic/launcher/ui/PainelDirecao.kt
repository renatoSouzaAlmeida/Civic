package br.com.civic.launcher.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.CondicaoClima
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.obd.ObdMonitor
import kotlinx.coroutines.delay
import java.util.Date

// Só o fundo do painel e o cartão da música são translúcidos; a barra de botões é opaca.
private const val OPACIDADE_FUNDO = 0.68f
private const val OPACIDADE_CARTAO = 0.78f

/** Tamanho do painel sobre o Waze: fração da largura (paisagem) ou da altura (retrato). */
enum class TamanhoPainel(val fracaoLargura: Float, val fracaoAltura: Float, val rotulo: String) {
    Compacto(0.22f, 0.26f, "Compacto"), Medio(0.28f, 0.32f, "Médio"), Grande(0.36f, 0.42f, "Grande");

    fun proximo() = entries[(ordinal + 1) % entries.size]

    companion object {
        private const val ARQUIVO = "preferencias"
        private const val CHAVE = "tamanho_painel"

        fun ler(ctx: Context): TamanhoPainel = runCatching {
            valueOf(ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).getString(CHAVE, null) ?: "")
        }.getOrDefault(Medio)

        fun salvar(ctx: Context, tamanho: TamanhoPainel) {
            ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit().putString(CHAVE, tamanho.name).apply()
        }
    }
}

/**
 * Modo Direção: painel sobre o Waze. Em cima, uma barra opaca e pequena (Início, hora/clima, tamanho, Fechar);
 * embaixo, a velocidade em uma linha e o Spotify ocupando o resto.
 */
@Composable
fun PainelDirecao(
    sensores: Sensores,
    monitor: MediaMonitor,
    obd: ObdMonitor,
    aoVoltarInicio: () -> Unit,
    aoAlternarTamanho: () -> Unit,
    aoFechar: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Cores.Fundo.copy(alpha = OPACIDADE_FUNDO))) {
        val larga = maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            BarraSuperior(sensores, aoVoltarInicio, aoAlternarTamanho, aoFechar)
            if (larga) {
                // Faixa larga (tela em retrato): velocidade à esquerda, música ocupa o resto.
                Row(Modifier.weight(1f).fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Velocidade(sensores, obd, Modifier.width(110.dp).fillMaxHeight())
                    Musica(monitor, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                // Coluna estreita (tela em paisagem): velocidade numa linha e a música com todo o resto.
                Column(Modifier.weight(1f).fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Velocidade(sensores, obd, Modifier.fillMaxWidth())
                    Musica(monitor, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun BarraSuperior(sensores: Sensores, aoVoltarInicio: () -> Unit, aoAlternarTamanho: () -> Unit, aoFechar: () -> Unit) {
    val ctx = LocalContext.current
    var agora by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { agora = Date(); delay(1_000) } }
    val hora = DateFormat.getTimeFormat(ctx).format(agora)
    val clima = sensores.clima

    BoxWithConstraints(Modifier.fillMaxWidth().background(Cores.CartaoSolido).padding(horizontal = 6.dp, vertical = 5.dp)) {
        val estreita = maxWidth < 230.dp
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BotaoPequeno(Icons.Filled.Home, "Início", aoVoltarInicio)
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!estreita && clima != null) {
                    val (icone, cor) = iconeClima(clima.condicao)
                    Icon(icone, null, tint = cor, modifier = Modifier.size(18.dp))
                    Text("${clima.temperatura}°", color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Text(hora, color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            BotaoPequeno(Icons.Filled.OpenInFull, "Tamanho do painel", aoAlternarTamanho)
            BotaoPequeno(Icons.Filled.Close, "Fechar", aoFechar)
        }
    }
}

@Composable
private fun Velocidade(sensores: Sensores, obd: ObdMonitor, modifier: Modifier) {
    val vel = obd.dados.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        Text("${vel.toInt()}", color = Cores.Texto, fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(" km/h", color = Cores.TextoSuave, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 5.dp))
    }
}

@Composable
private fun Musica(monitor: MediaMonitor, modifier: Modifier) {
    val m = monitor.estado
    BoxWithConstraints(modifier.clip(RoundedCornerShape(20.dp)).background(Cores.Cartao.copy(alpha = OPACIDADE_CARTAO))) {
        val estreita = maxWidth < 190.dp
        val capa = minOf(maxWidth * 0.4f, 120.dp)
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Capa, título e progresso cedem espaço; os controles abaixo nunca são espremidos.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (m.capa != null) {
                        Image(m.capa.asImageBitmap(), null, Modifier.size(capa).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Filled.MusicNote, null, tint = Cores.Spotify, modifier = Modifier.size(capa * 0.5f))
                    }
                    Text(
                        if (m.temSessao && m.titulo.isNotEmpty()) m.titulo else "Spotify",
                        color = Cores.Texto, fontSize = if (estreita) 17.sp else 21.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (m.temSessao) m.artista else "Nada tocando",
                        color = Cores.TextoSuave, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (m.temSessao && m.duracao > 0) BarraProgresso(m)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Controle(Icons.Filled.SkipPrevious, if (estreita) 40.dp else 52.dp, Cores.TextoSuave) { monitor.anterior() }
                Controle(
                    if (m.tocando) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                    if (estreita) 56.dp else 72.dp, Cores.Spotify,
                ) { monitor.alternar() }
                Controle(Icons.Filled.SkipNext, if (estreita) 40.dp else 52.dp, Cores.TextoSuave) { monitor.proxima() }
            }
        }
    }
}

/** Botão pequeno (36 dp) da barra superior do painel. */
@Composable
private fun BotaoPequeno(icone: ImageVector, descricao: String, aoTocar: () -> Unit) {
    Box(
        Modifier.toqueSuave(aoTocar).size(36.dp).clip(RoundedCornerShape(10.dp)).background(Cores.CartaoAlto),
        contentAlignment = Alignment.Center,
    ) { Icon(icone, descricao, tint = Cores.Texto, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun iconeClima(c: CondicaoClima): Pair<ImageVector, Color> = when (c) {
    CondicaoClima.Limpo -> Icons.Filled.WbSunny to Cores.Amarelo
    CondicaoClima.Nublado -> Icons.Filled.Cloud to Cores.TextoSuave
    CondicaoClima.Chuva -> Icons.Filled.WaterDrop to Cores.Waze
    CondicaoClima.Tempestade -> Icons.Filled.Bolt to Cores.Amarelo
}
