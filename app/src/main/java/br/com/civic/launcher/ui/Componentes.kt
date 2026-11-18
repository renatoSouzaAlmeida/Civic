package br.com.civic.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

val FormaCartao: Shape = RoundedCornerShape(28.dp)

/** Cartão padrão da interface: cantos grandes, fundo do tema e borda fina (sem sombra, para poupar a GPU). */
@Composable
fun Cartao(modifier: Modifier = Modifier, forma: Shape = FormaCartao, conteudo: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.clip(forma).background(Cores.Cartao).border(1.dp, Cores.Borda, forma),
        content = conteudo,
    )
}

/** Barra em degraus, como a do HUD: azul, âmbar perto do limite e zona de alerta no fim. */
@Composable
fun BarraSegmentos(fracao: Float, modifier: Modifier = Modifier, segmentos: Int = 32, altura: Dp = 26.dp) {
    val ligados = (fracao.coerceIn(0f, 1f) * segmentos).roundToInt()
    Row(modifier.height(altura), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until segmentos) {
            val cor = when {
                i < ligados -> if (i < segmentos * 0.6f) Cores.Destaque else Cores.Amarelo
                i >= segmentos * 0.8f -> Cores.SegAlerta
                else -> Cores.SegVazio
            }
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(cor))
        }
    }
}

/** Barra de progresso simples (carga do motor, acelerador...). */
@Composable
fun BarraFina(fracao: Float, cor: Color, modifier: Modifier = Modifier, altura: Dp = 7.dp) {
    Box(modifier.height(altura).clip(RoundedCornerShape(50)).background(Cores.SegVazio)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fracao.coerceIn(0f, 1f)).background(cor))
    }
}

@Composable
fun ChipInfo(texto: String, icone: ImageVector, corIcone: Color, modifier: Modifier = Modifier, corTexto: Color = Cores.Texto) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Cores.Chip).border(1.dp, Cores.ChipBorda, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(icone, null, tint = corIcone, modifier = Modifier.size(16.dp))
        Text(texto, color = corTexto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Caixa pequena com título, valor grande e uma linha de apoio (clima, rede, motor, bateria). O valor escala com a altura da caixa. */
@Composable
fun MiniCartao(
    titulo: String,
    icone: ImageVector,
    corIcone: Color,
    valor: String,
    unidade: String,
    apoio: String,
    modifier: Modifier = Modifier,
) {
    Cartao(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
            val tamanho = (maxHeight.value * 0.36f).coerceIn(18f, 40f).sp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icone, null, tint = corIcone, modifier = Modifier.size(18.dp))
                    Text(titulo, color = Cores.TextoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(valor, color = Cores.Texto, fontSize = tamanho, lineHeight = tamanho, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    if (unidade.isNotEmpty()) Text(unidade, color = Cores.TextoSuave, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 3.dp))
                }
                Text(apoio, color = Cores.TextoSuave, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Botão em pílula (ex.: Demonstração, Adaptador). */
@Composable
fun BotaoPilula(texto: String, icone: ImageVector, modifier: Modifier = Modifier, aoTocar: () -> Unit) {
    Row(
        modifier.toqueSuave(aoTocar).clip(RoundedCornerShape(50)).background(Cores.Chip).border(1.dp, Cores.ChipBorda, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icone, null, tint = Cores.Destaque, modifier = Modifier.size(20.dp))
        Text(texto, color = Cores.Texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
