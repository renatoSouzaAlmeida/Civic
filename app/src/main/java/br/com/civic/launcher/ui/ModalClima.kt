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

/** Um local encontrado na busca: nome curto, endereço completo e coordenadas. */
private data class LocalAchado(val titulo: String, val detalhe: String, val lat: Double, val lon: Double, val km: Float?)

/**
 * Procura até 5 locais com esse nome: primeiro no Geocoder do aparelho (endereço, bairro, cidade), e, se ele não existir
 * ou não achar nada, no geocodificador do Open-Meteo (cidades e bairros do Brasil). Ordena pelo mais perto daqui.
 */
@Suppress("DEPRECATION")
private fun buscarLocais(ctx: android.content.Context, texto: String, refLat: Double?, refLon: Double?): List<LocalAchado> {
    fun km(lat: Double, lon: Double): Float? = if (refLat == null || refLon == null) null else
        FloatArray(1).also { android.location.Location.distanceBetween(refLat, refLon, lat, lon, it) }[0] / 1000f
    val lista = mutableListOf<LocalAchado>()
    runCatching {
        if (Geocoder.isPresent()) Geocoder(ctx, Locale("pt", "BR")).getFromLocationName(texto, 5)?.forEach { a ->
            val titulo = listOfNotNull(a.featureName?.takeIf { it != a.subThoroughfare && it.any { c -> c.isLetter() } }, a.thoroughfare, a.subLocality, a.locality)
                .firstOrNull() ?: texto
            val detalhe = (0..a.maxAddressLineIndex).mapNotNull { a.getAddressLine(it) }.joinToString(", ")
                .ifEmpty { listOfNotNull(a.subLocality, a.locality ?: a.subAdminArea, a.adminArea).joinToString(", ") }
            lista += LocalAchado(titulo, detalhe, a.latitude, a.longitude, km(a.latitude, a.longitude))
        }
    }
    if (lista.isEmpty()) runCatching {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=5&language=pt&countryCode=BR&name=" + java.net.URLEncoder.encode(texto, "UTF-8")
        val con = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 10_000 }
        val r = org.json.JSONObject(con.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results")
        if (r != null) for (k in 0 until r.length()) {
            val o = r.getJSONObject(k)
            val lat = o.getDouble("latitude"); val lon = o.getDouble("longitude")
            val detalhe = listOf(o.optString("admin3"), o.optString("admin2"), o.optString("admin1"), o.optString("country"))
                .filter { it.isNotBlank() && it != o.optString("name") }.distinct().joinToString(", ")
            lista += LocalAchado(o.optString("name", texto), detalhe, lat, lon, km(lat, lon))
        }
    }
    return lista.distinctBy { "%.3f,%.3f".format(Locale.US, it.lat, it.lon) }.sortedBy { it.km ?: 0f }
}

/**
 * Chuva no destino: digite o bairro, a cidade ou o endereço (ou toque em Casa/Trabalho). As sugestões aparecem
 * enquanto digita, com o endereço completo e a distância; o local escolhido fica destacado com as coordenadas
 * e o botão "Ver no mapa", para não haver dúvida de onde é a previsão.
 */
@Composable
private fun DestinoChuva(casa: String, trabalho: String, post: (() -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("sensores", android.content.Context.MODE_PRIVATE) }
    val refLat = remember { prefs.getString("clima_lat", null)?.toDoubleOrNull() }
    val refLon = remember { prefs.getString("clima_lon", null)?.toDoubleOrNull() }
    var texto by remember { mutableStateOf("") }
    var sugestoes by remember { mutableStateOf<List<LocalAchado>>(emptyList()) }
    var escolhido by remember { mutableStateOf<LocalAchado?>(null) }
    var horas by remember { mutableStateOf<List<HoraDet>?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busca by remember { mutableIntStateOf(0) }
    var semSugestao by remember { mutableStateOf<String?>(null) } // texto posto por Casa/Trabalho: já busca e escolhe sozinho

    fun escolher(l: LocalAchado) {
        escolhido = l; sugestoes = emptyList(); horas = null; msg = "Consultando a previsão…"
        ClimaDetalhe.chuvaNoPonto(l.lat, l.lon, post) { r -> horas = r; msg = if (r == null) "Previsão indisponível para este local." else null }
    }

    /** Procura; com [automatico], já escolhe o primeiro resultado (Casa/Trabalho). */
    fun procurar(t: String, automatico: Boolean = false) {
        if (t.trim().length < 3) { sugestoes = emptyList(); return }
        val id = ++busca
        msg = "Procurando \"${t.trim()}\"…"
        thread(name = "busca-destino") {
            val r = buscarLocais(ctx, t.trim(), refLat, refLon)
            post {
                if (id != busca) return@post // chegou resposta de uma busca antiga
                when {
                    r.isEmpty() -> { sugestoes = emptyList(); msg = "Nada encontrado para \"${t.trim()}\". Tente com o nome da cidade junto (ex.: Centro, Barueri)." }
                    automatico -> escolher(r.first())
                    else -> { sugestoes = r; msg = if (r.size == 1) "1 local encontrado. Toque para confirmar." else "${r.size} locais encontrados. Toque no certo." }
                }
            }
        }
    }

    // Sugestões enquanto digita (espera 0,8 s sem digitar).
    LaunchedEffect(texto) {
        if (texto == semSugestao) return@LaunchedEffect
        kotlinx.coroutines.delay(800)
        procurar(texto)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (casa.isNotBlank()) Opcao("Casa", false, Modifier.weight(1f)) { semSugestao = casa; texto = casa; procurar(casa, automatico = true) }
        if (trabalho.isNotBlank()) Opcao("Trabalho", false, Modifier.weight(1f)) { semSugestao = trabalho; texto = trabalho; procurar(trabalho, automatico = true) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value = texto, onValueChange = { texto = it; escolhido = null; horas = null },
            label = { Text("Bairro, cidade ou endereço do destino") }, singleLine = true, modifier = Modifier.weight(1f),
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (texto.isNotEmpty()) Icon(Icons.Filled.Close, "Limpar", Modifier.clickable { texto = ""; sugestoes = emptyList(); escolhido = null; horas = null; msg = null }) })
        BotaoPilula("Buscar", Icons.Filled.Search) { procurar(texto) }
    }
    msg?.let { Text(it, color = Cores.TextoSuave, fontSize = 14.sp) }

    // Sugestões: nome, endereço completo e distância daqui.
    sugestoes.forEach { l ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Cores.Chip).border(1.dp, Cores.ChipBorda, RoundedCornerShape(12.dp))
            .clickable { escolher(l) }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Place, null, tint = Cores.Destaque, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(l.titulo, color = Cores.Texto, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(l.detalhe, color = Cores.TextoSuave, fontSize = 13.sp, maxLines = 2)
            }
            l.km?.let { Text(if (it < 1f) "${(it * 1000).toInt()} m" else "%.0f km".format(Locale("pt", "BR"), it), color = Cores.TextoSuave, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }

    // Local consultado: sem dúvida de onde é a previsão.
    escolhido?.let { l ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cores.Destaque.copy(alpha = 0.12f)).border(1.5.dp, Cores.Destaque, RoundedCornerShape(14.dp))
            .padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.LocationOn, null, tint = Cores.Destaque, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Previsão para", color = Cores.TextoSuave, fontSize = 12.sp)
                Text(l.titulo, color = Cores.Texto, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(l.detalhe, color = Cores.TextoSuave, fontSize = 13.sp)
                Text("%.5f, %.5f".format(Locale.US, l.lat, l.lon) + (l.km?.let { " · a %.0f km daqui".format(Locale("pt", "BR"), it) } ?: ""),
                    color = Cores.TextoSuave, fontSize = 12.sp)
            }
            BotaoPilula("Ver no mapa", Icons.Filled.Map) {
                runCatching {
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("geo:${l.lat},${l.lon}?q=${l.lat},${l.lon}(" + android.net.Uri.encode(l.titulo) + ")"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
    horas?.let { h ->
        val r = ClimaDetalhe.resumoChuva(h)
        Text(r, color = if (r.startsWith("Sem")) Cores.Ok else Cores.Waze, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        LinhaHoras(h)
    }
}
