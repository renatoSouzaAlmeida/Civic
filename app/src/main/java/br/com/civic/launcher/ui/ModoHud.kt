package br.com.civic.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Configuracoes
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.obd.AlertasMotor
import br.com.civic.launcher.obd.EcoConducao
import br.com.civic.launcher.obd.ObdMonitor
import kotlin.math.roundToInt

/** Estado global do HUD: aberto ou fechado (acionado pelo botão da barra do topo ou por Config.). */
object Hud {
    var aberto by mutableStateOf(false)
}

/**
 * Modo HUD: velocidade enorme, em fundo preto, espelhada na horizontal para refletir no para-brisa à noite.
 * Mostra também rpm, temperatura e combustível e o alerta do motor. Toque para sair; segure para espelhar ou não.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModoHud(obd: ObdMonitor, sensores: Sensores) {
    val ctx = LocalContext.current
    val config = remember { Configuracoes.obter(ctx) }
    val d = obd.dados
    val vel = d.velocidade ?: sensores.velocidadeKmh?.roundToInt() ?: 0
    val cor = EcoConducao.obter().nivel?.cor ?: Color.White
    val alerta = AlertasMotor.obter(ctx).atual
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .combinedClickable(onClick = { Hud.aberto = false }, onLongClick = { config.salvarHudEspelhado(!config.hudEspelhado) })
            .graphicsLayer { scaleX = if (config.hudEspelhado) -1f else 1f },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$vel", color = cor, fontSize = 300.sp, lineHeight = 300.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("km/h", color = Color(0xFF9AA4B2), fontSize = 44.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                d.tempMotor?.let { Text("$it °C", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.SemiBold) }
                d.rpm?.let { Text("$it rpm", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.SemiBold) }
                d.nivelCombustivel?.let { Text("⛽ $it%", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.SemiBold) }
            }
            alerta?.let {
                Spacer(Modifier.height(12.dp))
                Text("${it.tipo.titulo}: ${it.mensagem.substringBefore('.')}", color = Color(0xFFFF5A5A), fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
            }
        }
        Text("Toque para sair · segure para espelhar", color = Color(0xFF5B6472), fontSize = 16.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
    }
}
