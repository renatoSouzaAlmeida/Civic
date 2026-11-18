package br.com.civic.launcher.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.R
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.obd.ObdMonitor

/**
 * Dados rápidos da Tela Início (abaixo do mapa): km/h, km/l, combustível, °C do motor, farol e setas.
 * O que depende do OBD mostra "—" enquanto o adaptador não informa.
 */
@Composable
fun CartaoSinais(sensores: Sensores, obd: ObdMonitor, modifier: Modifier) {
    val d = obd.dados
    val vel = (d.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f).toInt()
    Cartao(modifier) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Celula("$vel", "km/h", Modifier.weight(1f))
                Divisor()
                Celula(d.consumoMedio?.let { "%.1f".format(it) } ?: "—", "km/l", Modifier.weight(1f))
                Divisor()
                Celula(d.nivelCombustivel?.toString() ?: "—", "% combustível", Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Cores.Borda))
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Celula(d.tempMotor?.toString() ?: "—", "°C motor", Modifier.weight(1f))
                Divisor()
                Farol(d.farol, Modifier.weight(1f))
                Divisor()
                Setas(d.setaEsquerda, d.setaDireita, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Divisor() {
    Box(Modifier.fillMaxHeight().padding(vertical = 12.dp).width(1.dp).background(Cores.Borda))
}

/** Valor grande (acompanha a altura da célula) com a unidade embaixo. */
@Composable
private fun Celula(valor: String, unidade: String, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        val tamanho = minOf(maxHeight.value * 0.36f, maxWidth.value * 0.30f).coerceIn(16f, 40f).sp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(valor, color = Cores.Texto, fontSize = tamanho, lineHeight = tamanho, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(unidade, color = Cores.TextoSuave, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Farol: ícone verde e "farol ligado" quando aceso; apagado e "farol desligado"; "—" se o carro não informa. */
@Composable
private fun Farol(ligado: Boolean?, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        val icone = minOf(maxHeight.value * 0.38f, 40f).dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painterResource(R.drawable.ic_farol), null,
                tint = when (ligado) {
                    true -> Color(0xFF22C55E)
                    false -> Cores.TextoSuave.copy(alpha = 0.4f)
                    null -> Cores.TextoSuave.copy(alpha = 0.18f)
                },
                modifier = Modifier.size(icone),
            )
            Text(
                when (ligado) { true -> "farol ligado"; false -> "farol desligado"; null -> "farol —" },
                color = Cores.TextoSuave, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            )
        }
    }
}

/** Setas: a acionada acende em verde e pisca; a outra fica apagada. "—" se o carro não informa. */
@Composable
private fun Setas(esquerda: Boolean?, direita: Boolean?, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        val icone = minOf(maxHeight.value * 0.38f, 40f).dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Seta(esquerda, false, icone)
                Seta(direita, true, icone)
            }
            Text(
                when {
                    esquerda == true -> "seta esquerda"
                    direita == true -> "seta direita"
                    esquerda == null && direita == null -> "setas —"
                    else -> "setas"
                },
                color = Cores.TextoSuave, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            )
        }
    }
}

@Composable
private fun Seta(ativa: Boolean?, direita: Boolean, tamanho: androidx.compose.ui.unit.Dp) {
    val verde = Color(0xFF22C55E)
    val cor = when (ativa) {
        true -> verde
        false -> Cores.TextoSuave.copy(alpha = 0.4f)
        null -> Cores.TextoSuave.copy(alpha = 0.18f)
    }
    val pisca = if (ativa == true) {
        rememberInfiniteTransition(label = "seta").animateFloat(1f, 0.15f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "alfa")
    } else null
    Icon(
        painterResource(R.drawable.ic_seta), null, tint = cor,
        modifier = Modifier.size(tamanho).graphicsLayer {
            alpha = pisca?.value ?: 1f
            if (direita) scaleX = -1f
        },
    )
}
