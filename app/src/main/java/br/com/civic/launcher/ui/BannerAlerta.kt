package br.com.civic.launcher.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.R
import br.com.civic.launcher.obd.AlertasMotor
import br.com.civic.launcher.obd.Gravidade
import br.com.civic.launcher.obd.TipoAlerta

/**
 * Faixa do alerta do motor (não aparece se não houver alerta). Crítico em vermelho com o ícone pulsando,
 * aviso em âmbar e dica em azul. Tocar no X esconde por um tempo; volta se a condição continuar.
 */
@Composable
fun BannerAlerta(modifier: Modifier = Modifier, compacto: Boolean = false) {
    val ctx = LocalContext.current
    val alertas = remember { AlertasMotor.obter(ctx.applicationContext) }
    val a = alertas.atual ?: return
    val (fundo, texto) = when (a.tipo.gravidade) {
        Gravidade.Critico -> Color(0xFFDC2626) to Color.White
        Gravidade.Aviso -> Color(0xFFF59E0B) to Color(0xFF1F1300)
        Gravidade.Dica -> Color(0xFF2563EB) to Color.White
    }
    val icone: Painter = when (a.tipo) {
        TipoAlerta.Superaquecimento -> rememberVectorPainter(Icons.Filled.Thermostat)
        TipoAlerta.Injecao -> painterResource(R.drawable.ic_motor)
        TipoAlerta.Alternador, TipoAlerta.BateriaFraca -> rememberVectorPainter(Icons.Filled.BatteryAlert)
        TipoAlerta.FarolDesligado -> painterResource(R.drawable.ic_farol)
        TipoAlerta.MotorFrio -> rememberVectorPainter(Icons.Filled.AcUnit)
        TipoAlerta.Fadiga -> rememberVectorPainter(Icons.Filled.Bedtime)
    }
    val pulso = if (a.tipo.gravidade == Gravidade.Critico) {
        pulsar(1f, 0.35f, 500)
    } else null
    val forma = RoundedCornerShape(if (compacto) 12.dp else 18.dp)
    Row(
        modifier.clip(forma).background(fundo).padding(horizontal = if (compacto) 10.dp else 16.dp, vertical = if (compacto) 6.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compacto) 8.dp else 14.dp),
    ) {
        Icon(icone, null, tint = texto, modifier = Modifier.size(if (compacto) 24.dp else 34.dp).graphicsLayer { alpha = pulso?.value ?: 1f })
        Column(Modifier.weight(1f)) {
            Text(a.tipo.titulo, color = texto, fontSize = if (compacto) 14.sp else 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(a.mensagem, color = texto, fontSize = if (compacto) 12.sp else 15.sp, maxLines = if (compacto) 1 else 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.Close, "Dispensar", tint = texto, modifier = Modifier.size(if (compacto) 26.dp else 32.dp).clickable { alertas.dispensar() })
    }
}
