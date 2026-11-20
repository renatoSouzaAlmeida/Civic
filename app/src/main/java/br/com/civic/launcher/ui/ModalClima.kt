package br.com.civic.launcher.ui

import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.civic.launcher.ClimaDet
import br.com.civic.launcher.ClimaDetalhe
import br.com.civic.launcher.Configuracoes
import br.com.civic.launcher.HoraDet
import java.util.Locale
import kotlin.concurrent.thread

/** Janela com o tempo completo do dia (agora, por hora, sol, UV, vento, próximos dias) e a chuva no destino escolhido. */
@Composable
fun ModalClima(aoFechar: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("sensores", android.content.Context.MODE_PRIVATE) }
    val config = remember { Configuracoes.obter(ctx) }
    val lat = prefs.getString("clima_lat", null)?.toDoubleOrNull()
    val lon = prefs.getString("clima_lon", null)?.toDoubleOrNull()
    val nome = prefs.getString("clima_nome", "Local atual") ?: "Local atual"
    var det by remember { mutableStateOf<ClimaDet?>(null) }
    var falhou by remember { mutableStateOf(false) }
    val principal = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val post: (() -> Unit) -> Unit = { f -> principal.post { f() } }
    LaunchedEffect(Unit) { if (lat != null && lon != null) ClimaDetalhe.buscar(lat, lon, post) { det = it; falhou = it == null } else falhou = true }

    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.82f).fillMaxHeight(0.92f).clip(RoundedCornerShape(24.dp)).background(Cores.CartaoSolido).border(1.dp, Cores.Borda, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, null, tint = Cores.TextoSuave, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(nome, color = Cores.Texto, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Close, "Fechar", tint = Cores.TextoSuave, modifier = Modifier.size(30.dp).clickable(onClick = aoFechar))
            }
            val d = det
            if (d == null) Text(if (falhou) "Não foi possível carregar a previsão (sem internet ou sem localização)." else "Carregando a previsão…", color = Cores.TextoSuave, fontSize = 16.sp)
            else {
                val (ic, cor) = iconeCondicao(d.agora.cond)
                // Agora
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(ic, null, tint = cor, modifier = Modifier.size(72.dp))
                    Text("${d.agora.temp}°", color = Cores.Texto, fontSize = 80.sp, fontWeight = FontWeight.Light)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(d.agora.cond.texto, color = Cores.Texto, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Máx. ${d.dias[0].max}°  Mín. ${d.dias[0].min}°", color = Cores.TextoSuave, fontSize = 16.sp)
                        Text("Sensação ${d.agora.sensacao}° · Umidade ${d.agora.umidade}%", color = Cores.TextoSuave, fontSize = 15.sp)
                    }
                }
                // Hoje em números
                val h = d.dias[0]
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Info("Chuva hoje", "${h.chuva}% · ${"%.1f".format(Locale("pt", "BR"), h.mm)} mm", Modifier.weight(1f))
                    Info("Vento", "${d.agora.vento} km/h (rajadas ${d.agora.rajada})", Modifier.weight(1f))
                    Info("Índice UV", "%.0f".format(Locale("pt", "BR"), h.uv) + uvTexto(h.uv), Modifier.weight(1f))
                    Info("Sol", "${h.nascer} → ${h.por}", Modifier.weight(1f))
                }
                // Por hora
                Text("Próximas 24 horas", color = Cores.TextoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                LinhaHoras(d.horas)
                // Próximos dias
                Text("Próximos dias", color = Cores.TextoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                d.dias.drop(1).forEach { dia ->
                    val (i2, c2) = iconeCondicao(dia.cond)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Cores.Chip).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(dia.rotulo, color = Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(i2, null, tint = c2, modifier = Modifier.size(24.dp))
                        Text("${dia.chuva}%", color = Cores.Waze, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                        Text("${dia.min}° / ${dia.max}°", color = Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            // Chuva no destino
            Text("Chuva no destino", color = Cores.TextoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            DestinoChuva(config.enderecoCasa, config.enderecoTrabalho, post)
        }
    }
}

private fun uvTexto(uv: Double) = when { uv < 3 -> " (baixo)"; uv < 6 -> " (moderado)"; uv < 8 -> " (alto)"; else -> " (muito alto)" }

@Composable
private fun Info(titulo: String, valor: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Cores.Chip).padding(12.dp)) {
        Text(titulo, color = Cores.TextoSuave, fontSize = 12.sp)
        Text(valor, color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LinhaHoras(horas: List<HoraDet>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        horas.forEach { h ->
            val (ic, c) = iconeCondicao(h.cond)
            Column(Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).background(Cores.Chip).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(h.rotulo, color = Cores.TextoSuave, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Icon(ic, null, tint = c, modifier = Modifier.size(24.dp))
                Text("${h.temp}°", color = Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(if (h.chuva >= 10) "${h.chuva}%" else " ", color = Cores.Waze, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Escolha de um destino (Casa, Trabalho ou endereço digitado) e a previsão de chuva nas próximas 12 h por lá. */
@Composable
private fun DestinoChuva(casa: String, trabalho: String, post: (() -> Unit) -> Unit) {
    val ctx = LocalContext.current
    var texto by remember { mutableStateOf("") }
    var rotulo by remember { mutableStateOf<String?>(null) }
    var horas by remember { mutableStateOf<List<HoraDet>?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    @Suppress("DEPRECATION")
    fun consultar(endereco: String) {
        if (endereco.isBlank()) return
        msg = "Procurando o destino…"; horas = null
        thread(name = "geocode-destino") {
            val a = runCatching { Geocoder(ctx, Locale("pt", "BR")).getFromLocationName(endereco, 1)?.firstOrNull() }.getOrNull()
            if (a == null) { post { msg = "Não encontrei esse destino. Tente com cidade ou bairro." }; return@thread }
            val nomeDestino = listOfNotNull(a.subLocality ?: a.thoroughfare, a.locality ?: a.subAdminArea).joinToString(", ").ifEmpty { endereco }
            post { rotulo = nomeDestino; msg = "Consultando a previsão…" }
            ClimaDetalhe.chuvaNoPonto(a.latitude, a.longitude, post) { r -> horas = r; msg = if (r == null) "Previsão indisponível para este destino." else null }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (casa.isNotBlank()) Opcao("Casa", false, Modifier.weight(1f)) { texto = casa; consultar(casa) }
        if (trabalho.isNotBlank()) Opcao("Trabalho", false, Modifier.weight(1f)) { texto = trabalho; consultar(trabalho) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value = texto, onValueChange = { texto = it }, label = { Text("Cidade, bairro ou endereço do destino") }, singleLine = true, modifier = Modifier.weight(1f))
        BotaoPilula("Ver chuva", Icons.Filled.WaterDrop) { consultar(texto) }
    }
    msg?.let { Text(it, color = Cores.TextoSuave, fontSize = 14.sp) }
    horas?.let { h ->
        val r = ClimaDetalhe.resumoChuva(h)
        Text("${rotulo ?: "Destino"}: $r", color = if (r.startsWith("Sem")) Cores.Ok else Cores.Waze, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        LinhaHoras(h)
    }
}
