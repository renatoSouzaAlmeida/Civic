package br.com.civic.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Sensores

/** Central do Civic (Tela 3). Por enquanto só a velocidade (GPS); o restante entra com o OBD. */
@Composable
fun TelaCentral(sensores: Sensores) {
    val vel = sensores.velocidadeKmh ?: 0f
    Row(
        Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Cores.Cartao).padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Medidor(vel / 220f, Cores.Waze, Modifier.fillMaxHeight().aspectRatio(1f), espessura = 28.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${vel.toInt()}", color = Cores.Texto, fontSize = 96.sp, fontWeight = FontWeight.Bold)
                Text("km/h", color = Cores.TextoSuave, fontSize = 28.sp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Central do Civic", color = Cores.Texto, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(
                "RPM, temperatura do motor, tensão da bateria e falhas aparecem aqui quando o adaptador OBD estiver conectado.",
                color = Cores.TextoSuave, fontSize = 22.sp,
            )
        }
    }
}
