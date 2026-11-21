package br.com.civic.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import br.com.civic.launcher.Configuracoes
import br.com.civic.launcher.ImagemCarro
import br.com.civic.launcher.R

/**
 * Fotos das telas Início novas (Ousada, Esportiva, Nova linha):
 *  - tema escuro: cena escura com brilho azul e reflexo no chão (Civic ou Fit, conforme Config. > Imagem do carro);
 *  - tema claro, Civic: recorte do carro sem fundo sobre o cartão claro (o Fit ainda não tem recorte e usa a cena).
 * A placa configurada em Config. é desenhada por cima da placa da foto (a da foto tem letras tortas e a placa do Fit seria outra).
 */
@Composable
internal fun cenaEhRecorte(): Boolean {
    val config = Configuracoes.obter(LocalContext.current)
    return !LocalPaleta.current.escuro && config.imagemCarro == ImagemCarro.Civic
}

/**
 * [cortar] = true preenche a caixa cortando a foto (cena larga); false mostra a foto inteira. [viesX]/[viesY] escolhem que parte fica à vista no corte.
 * O recorte sempre aparece inteiro, encostado embaixo.
 */
@Composable
internal fun CenaCarro(modifier: Modifier = Modifier, cortar: Boolean = true, viesX: Float = 0f, viesY: Float = 0.5f) {
    val config = Configuracoes.obter(LocalContext.current)
    val civic = config.imagemCarro == ImagemCarro.Civic
    val recorte = cenaEhRecorte()
    val recurso = when { recorte -> R.drawable.civic_recorte; civic -> R.drawable.civic_cena; else -> R.drawable.fit_cena }
    // Placa na foto: centro (fração x, y), largura e altura (fração da imagem), medidos em cada arquivo.
    val g = when { recorte -> floatArrayOf(0.1766f, 0.7215f, 0.1326f, 0.1300f); civic -> floatArrayOf(0.2911f, 0.6812f, 0.0784f, 0.0659f); else -> floatArrayOf(0.2838f, 0.6732f, 0.1083f, 0.0818f) }
    val iw = if (recorte) 1300f else 1400f
    val ih = if (recorte) 624f else 788f
    val preencher = cortar && !recorte
    BoxWithConstraints(modifier) {
        val w = maxWidth.value; val h = maxHeight.value
        val escala = if (preencher) maxOf(w / iw, h / ih) else minOf(w / iw, h / ih)
        val ox = (w - iw * escala) * (if (preencher) (viesX + 1f) / 2f else 0.5f)
        val oy = (h - ih * escala) * (if (recorte) 1f else if (preencher) (viesY + 1f) / 2f else 0.5f)
        Image(
            painterResource(recurso), null, Modifier.fillMaxSize(),
            contentScale = if (preencher) ContentScale.Crop else ContentScale.Fit,
            alignment = if (recorte) BiasAlignment(0f, 1f) else if (preencher) BiasAlignment(viesX, viesY) else Alignment.Center,
        )
        if (config.placa.isNotEmpty()) {
            val pw = g[2] * iw * escala * 1.04f; val ph = g[3] * ih * escala * 1.06f
            val px = ox + g[0] * iw * escala - pw / 2; val py = oy + g[1] * ih * escala - ph / 2
            // Só desenha se a placa está inteira à vista (num corte apertado ela pode ficar de fora).
            if (px >= 0f && py >= 0f && px + pw <= w + 1f && py + ph <= h + 1f && pw > 24f)
                Box(Modifier.offset(px.dp, py.dp)) { PlacaMercosul(config.placaFormatada, pw.dp, ph.dp) }
        }
    }
}
