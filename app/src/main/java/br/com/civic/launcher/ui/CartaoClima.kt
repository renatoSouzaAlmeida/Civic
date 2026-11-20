package br.com.civic.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.ClimaUi
import br.com.civic.launcher.CondicaoClima

@Composable
fun iconeCondicao(c: CondicaoClima): Pair<ImageVector, Color> = when (c) {
    CondicaoClima.Limpo -> Icons.Filled.WbSunny to Cores.Amarelo
    CondicaoClima.Parcial -> Icons.Filled.WbSunny to Cores.Amarelo
    CondicaoClima.Nublado -> Icons.Filled.Cloud to Cores.TextoSuave
    CondicaoClima.Chuva -> Icons.Filled.WaterDrop to Cores.Waze
    CondicaoClima.Tempestade -> Icons.Filled.Bolt to Cores.Amarelo
}

/**
 * Cartão do tempo no estilo do app de clima do celular: local e hora da atualização, temperatura grande com
 * condição e máx/mín, sensação, umidade e vento, aviso de chuva e a previsão das próximas horas.
 */
@Composable
fun CartaoClima(clima: ClimaUi?, modifier: Modifier = Modifier) {
    Cartao(modifier) {
        if (clima == null) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                Text("Clima", color = Cores.TextoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Precisa de internet", color = Cores.TextoSuave, fontSize = 13.sp)
            }
            return@Cartao
        }
        val (icone, cor) = iconeCondicao(clima.condicao)
        val hora = remember(clima.atualizadoEm) { java.text.SimpleDateFormat("HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(clima.atualizadoEm)) }
        val corChuva = if (clima.chovendo || clima.chuva.startsWith("Chuva")) Cores.Waze else Cores.Ok
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, null, tint = Cores.TextoSuave, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(clima.local, color = Cores.TextoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("atualizado $hora", color = Cores.TextoSuave, fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icone, null, tint = cor, modifier = Modifier.size(46.dp))
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        Text("${clima.temperatura}", color = Cores.Texto, fontSize = 52.sp, lineHeight = 54.sp, fontWeight = FontWeight.Light, maxLines = 1)
                        Text("°", color = Cores.Texto, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Light)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(clima.condicao.texto, color = Cores.Texto, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Máx. ${clima.maxima}°  Mín. ${clima.minima}°", color = Cores.TextoSuave, fontSize = 13.sp, maxLines = 1)
                    Text("Sensação ${clima.sensacao}° · Umidade ${clima.umidade}% · Vento ${clima.vento} km/h", color = Cores.TextoSuave, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (clima.chuva.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.WaterDrop, null, tint = corChuva, modifier = Modifier.size(15.dp))
                Text(clima.chuva + if (clima.chuvaHoje > 0) " · hoje até ${clima.chuvaHoje}%" else "", color = corChuva, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (clima.horas.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                clima.horas.forEach { h ->
                    val (ic, c) = iconeCondicao(h.condicao)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(h.rotulo, color = Cores.TextoSuave, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Icon(ic, null, tint = c, modifier = Modifier.size(20.dp))
                        Text("${h.temperatura}°", color = Cores.Texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(if (h.chuva >= 30) "${h.chuva}%" else " ", color = Cores.Waze, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
