package br.com.civic.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Configuracoes
import br.com.civic.launcher.ImagemCarro
import br.com.civic.launcher.R
import br.com.civic.launcher.obd.DadosObd
import br.com.civic.launcher.obd.ObdMonitor

/**
 * Foto do carro (cinza) na parte de cima do cartão, esmaecendo para o fundo do cartão, com a placa Mercosul
 * desenhada no para-choque, logo abaixo do emblema (onde fica a placa de verdade).
 */
@Composable
internal fun FotoCarro(modifier: Modifier, fracaoAltura: Float) {
    val config = Configuracoes.obter(LocalContext.current)
    val fit = config.imagemCarro == ImagemCarro.Fit
    Box(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight(fracaoAltura)) {
            Image(
                painterResource(if (fit) R.drawable.carro_fit_cinza else R.drawable.carro_cinza), null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(-0.3f, 0f),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.72f to Color.Transparent, 1f to Cores.CartaoSolido)))
            if (config.placa.isNotEmpty()) {
                // Mesma conta do ContentScale.Crop + BiasAlignment(-0.3, 0) da foto (800 x 460) para achar o para-choque.
                val escala = maxOf(maxWidth.value / 800f, maxHeight.value / 460f)
                val larguraFoto = 800f * escala; val alturaFoto = 460f * escala
                val ox = (maxWidth.value - larguraFoto) * 0.35f
                val oy = (maxHeight.value - alturaFoto) / 2f
                val (px, py) = if (fit) 0.335f to 0.755f else 0.272f to 0.83f
                val largura = (larguraFoto * 0.2f).coerceIn(64f, 132f)
                val altura = largura * 0.34f
                val x = (ox + px * larguraFoto - largura / 2f).coerceIn(4f, maxWidth.value - largura - 4f)
                val y = (oy + py * alturaFoto - altura / 2f).coerceIn(4f, maxHeight.value - altura - 4f)
                Box(Modifier.offset(x.dp, y.dp)) { PlacaMercosul(config.placaFormatada, largura.dp) }
            }
        }
    }
}

@Composable
internal fun TituloCarro() {
    val config = Configuracoes.obter(LocalContext.current)
    val sombra = TextStyle(shadow = Shadow(Color(0xB3000000), Offset(0f, 2f), 12f))
    Column {
        Text(config.nomeCarro, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = sombra, maxLines = 1)
        Text(config.modeloEAno, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, style = sombra, maxLines = 1)
    }
}

/** Placa Mercosul discreta, escura e integrada à foto do carro: fundo grafite quase opaco, faixa "BRASIL" apagada e caracteres em cinza-claro. */
@Composable
internal fun PlacaMercosul(texto: String, largura: androidx.compose.ui.unit.Dp = 132.dp, altura: androidx.compose.ui.unit.Dp? = null) {
    val k = largura.value / 132f
    val forma = androidx.compose.foundation.shape.RoundedCornerShape((4 * k).dp)
    val fundo = Color(0xFF12161D).copy(alpha = 0.94f)
    val letras = Color(0xFFC3C9D4)
    Column(
        Modifier.width(largura).then(if (altura != null) Modifier.height(altura) else Modifier).clip(forma).background(fundo)
            .border((0.8f * k).coerceAtLeast(0.6f).dp, Color(0xFF2B323E), forma),
    ) {
        Box(Modifier.fillMaxWidth().height((10 * k).dp).background(Color(0xFF1A2232)), contentAlignment = Alignment.Center) {
            Text("BRASIL", color = Color(0xFF7C879E), fontSize = (6.5f * k).sp, lineHeight = (8 * k).sp, fontWeight = FontWeight.Medium, letterSpacing = (1 * k).sp, maxLines = 1)
        }
        if (altura != null) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(texto, color = letras, fontSize = ((altura.value - 10 * k) * 0.66f).sp, lineHeight = ((altura.value - 10 * k) * 0.7f).sp, fontWeight = FontWeight.Bold, letterSpacing = (1.5f * k).sp, maxLines = 1, softWrap = false)
        } else Text(
            texto, color = letras, fontSize = (20 * k).sp, lineHeight = (22 * k).sp, fontWeight = FontWeight.Bold, letterSpacing = (1.5f * k).sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = (2 * k).dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
        )
    }
}

/** Chips de situação do carro. Só mostra o que o adaptador OBD realmente informou. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipsCarro(d: DadosObd) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val f = d.falhas) {
            null -> ChipInfo("Falhas —", Icons.Filled.Warning, Cores.TextoSuave)
            0 -> ChipInfo("Sem falhas", Icons.Filled.Check, Cores.Ok, corTexto = Cores.Ok)
            else -> ChipInfo(if (f == 1) "1 falha" else "$f falhas", Icons.Filled.Warning, Cores.Vermelho, corTexto = Cores.Vermelho)
        }
        d.tempAdmissao?.let { ChipInfo("$it °C ar", Icons.Filled.AcUnit, Cores.Destaque) }
        d.acelerador?.let { ChipInfo("$it % acel.", Icons.Filled.Speed, Cores.Destaque) }
    }
}

/** Cartão do carro da Tela Início: foto, giros em degraus e situação. */
@Composable
fun CartaoCarro(obd: ObdMonitor, modifier: Modifier) {
    val d = obd.dados
    Cartao(modifier) {
        FotoCarro(Modifier.fillMaxSize(), 0.55f)
        Column(Modifier.fillMaxSize().padding(20.dp).escalarSeMenorQue(250.dp), verticalArrangement = Arrangement.SpaceBetween) {
            TituloCarro()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("RPM", color = Cores.TextoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(d.rpm?.toString() ?: "—", color = Cores.Texto, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }
                BarraSegmentos((d.rpm ?: 0) / 8000f, Modifier.fillMaxWidth(), 32, 24.dp)
                ChipsCarro(d)
            }
        }
    }
}
