package br.com.civic.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.R
import br.com.civic.launcher.obd.DadosObd
import br.com.civic.launcher.obd.ObdMonitor

/** Foto do carro (cinza) na parte de cima do cartão, esmaecendo para o fundo do cartão. */
@Composable
internal fun ImagemCarro(modifier: Modifier, fracaoAltura: Float) {
    Box(modifier) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(fracaoAltura)) {
            Image(
                painterResource(R.drawable.carro_cinza), null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(-0.3f, 0f),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Cores.CartaoSolido)))
        }
    }
}

@Composable
internal fun TituloCarro() {
    val sombra = TextStyle(shadow = Shadow(Color(0xB3000000), Offset(0f, 2f), 12f))
    Column {
        Text("Seu Civic", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = sombra)
        Text("G9 · 2012", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, style = sombra)
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
        ImagemCarro(Modifier.fillMaxSize(), 0.55f)
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
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
