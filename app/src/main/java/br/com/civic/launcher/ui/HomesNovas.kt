package br.com.civic.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Configuracoes
import br.com.civic.launcher.MapaPreferido
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.R
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.Veiculo
import br.com.civic.launcher.abrirNavegacaoComPainel
import br.com.civic.launcher.navegarFavorito
import br.com.civic.launcher.obd.DadosObd
import br.com.civic.launcher.obd.EstadoObd
import br.com.civic.launcher.obd.ObdMonitor
import java.util.Calendar
import java.util.Locale

private val BRN = Locale("pt", "BR")

/** "Bom dia", "Boa tarde" ou "Boa noite" pela hora, com o nome de quem usa (Config. > Identidade) quando houver. */
@Composable
private fun saudacao(): String {
    val config = Configuracoes.obter(LocalContext.current)
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val base = when (h) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }
    return if (config.nomeUsuario.isBlank()) base else "$base, ${config.nomeUsuario.trim()}"
}

private fun consumoNovo(d: DadosObd, ctx: android.content.Context): String =
    (d.consumoMedio ?: Veiculo.obter(ctx).consumoMedio())?.let { "%.1f".format(BRN, it) } ?: "—"

/** Chips "Farol ligado" e "Setas desligadas" (usam as luzes-espia do OBD). */
@Composable
private fun LuzesCarro(d: DadosObd, modifier: Modifier = Modifier, tamanho: Int = 15) {
    val seta = when {
        d.alerta == true -> "Pisca-alerta ligado"; d.setaEsquerda == true -> "Seta à esquerda"; d.setaDireita == true -> "Seta à direita"; else -> "Setas desligadas"
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FarolObd(d, 24.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SetaObd(d, esquerda = true, tamanho = 26.dp); SetaObd(d, esquerda = false, tamanho = 26.dp)
            Text(seta, color = Cores.Texto, fontSize = tamanho.sp, maxLines = 1)
        }
    }
}

/** Cartão "Navegação · Abrir aplicativo": Maps e Waze (o escolhido vira o mapa preferido), com Casa/Trabalho e Iniciar direção se pedido. */
@Composable
private fun CartaoNavegacaoApps(modifier: Modifier, comFavoritos: Boolean = false) {
    val ctx = LocalContext.current
    val config = remember { Configuracoes.obter(ctx) }
    Column(modifier.cartaoSuave().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Navegação", color = Cores.Texto, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("  ·  Abrir aplicativo", color = Cores.TextoSuave, fontSize = 14.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(MapaPreferido.GoogleMaps to Icons.Filled.Place, MapaPreferido.Waze to Icons.Filled.Navigation).forEach { (m, icone) ->
                Row(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Cores.Chip).clickable { config.salvarMapa(m); ctx.abrirNavegacaoComPainel() }.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (m == MapaPreferido.Waze) Cores.Waze else Color.White), contentAlignment = Alignment.Center) {
                        Icon(icone, null, tint = if (m == MapaPreferido.Waze) Color.White else Color(0xFFEA4335), modifier = Modifier.size(22.dp))
                    }
                    Text(if (m == MapaPreferido.Waze) "Waze" else "Maps", color = Cores.Texto, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        if (comFavoritos) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Cores.Chip).border(1.dp, Cores.ChipBorda, RoundedCornerShape(50)), verticalAlignment = Alignment.CenterVertically) {
                listOf("Casa" to Icons.Filled.Home, "Trabalho" to Icons.Filled.Work).forEachIndexed { i, (rotulo, ic) ->
                    Row(Modifier.weight(1f).clickable { ctx.navegarFavorito(if (i == 0) "Home" else "Work") }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(ic, null, tint = Cores.Destaque, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
                        Text(rotulo, color = Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Cores.Destaque).clickable { ctx.abrirNavegacaoComPainel() }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Navigation, null, tint = Color.White, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
                Text("Iniciar direção", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 1) OUSADA: saudação grande, o carro em destaque numa cena escura com brilho, cartões à direita
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeOusada(monitor: MediaMonitor, sensores: Sensores, obd: ObdMonitor) {
    val ctx = LocalContext.current
    val config = remember { Configuracoes.obter(ctx) }
    val d = obd.dados
    val escuro = LocalPaleta.current.escuro
    val brilho = if (escuro) Color(0xFF38BDF8) else Cores.Destaque
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        // Cena do carro: fundo com brilho, saudação, luzes e a foto com a placa no para-choque.
        Box(Modifier.weight(2.1f).fillMaxHeight().clip(RoundedCornerShape(28.dp))) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.verticalGradient(listOf(if (escuro) Color(0xFF0B1424) else Color(0xFFEAF1FB), if (escuro) Color(0xFF05080F) else Color(0xFFF6F9FE))))
                // Brilho do chão e feixe de luz, como na proposta.
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, brilho.copy(alpha = if (escuro) 0.55f else 0.25f), Color.Transparent)), Offset(0f, size.height * 0.62f), androidx.compose.ui.geometry.Size(size.width, size.height * 0.012f))
                drawCircle(Brush.radialGradient(listOf(brilho.copy(alpha = if (escuro) 0.28f else 0.16f), Color.Transparent), center = Offset(size.width * 0.55f, size.height * 0.62f), radius = size.width * 0.5f), size.width * 0.5f, Offset(size.width * 0.55f, size.height * 0.62f))
            }
            FotoCarro(Modifier.fillMaxSize().padding(top = 150.dp), 1f)
            Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Home, null, tint = Cores.Destaque, modifier = Modifier.size(22.dp)); Text("Início", color = Cores.Destaque, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(saudacao(), color = Cores.Texto, fontSize = 46.sp, lineHeight = 50.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(config.nomeCarro, config.modeloEAno).filter { it.isNotBlank() }.joinToString(" "), color = Cores.Texto, fontSize = 26.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                LuzesCarro(d)
            }
        }
        // Cartões da direita.
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ClimaLinha(sensores.clima, Modifier.weight(0.8f).fillMaxWidth())
            SpotifyLinha(monitor, Modifier.weight(1f).fillMaxWidth())
            CartaoNavegacaoApps(Modifier.weight(1f).fillMaxWidth())
            ResumoObdCompacto(d, obd, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/** RPM, °C do motor, % do tanque, km/l e o estado do OBD, em grade de 2 colunas. */
@Composable
private fun ResumoObdCompacto(d: DadosObd, obd: ObdMonitor, modifier: Modifier) {
    val ctx = LocalContext.current
    Column(modifier.cartaoSuave().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth()) {
            Texto2(d.rpm?.let { "%,d".format(BRN, it).replace(",", ".") } ?: "—", "RPM", Modifier.weight(1f))
            Texto2(d.tempMotor?.toString() ?: "—", "°C motor", Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Cores.Borda))
        Row(Modifier.fillMaxWidth()) {
            Texto2(d.nivelCombustivel?.toString() ?: "—", "% combustível", Modifier.weight(1f))
            Texto2(consumoNovo(d, ctx), "km/l", Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Cores.Borda))
        val (txt, cor) = when (obd.estado) { EstadoObd.Conectado -> "OBD conectado" to Cores.Ok; EstadoObd.Demonstracao -> "OBD demonstração" to Cores.Waze; else -> "OBD desconectado" to Cores.TextoSuave }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(txt, color = Cores.Texto, fontSize = 15.sp); Box(Modifier.size(9.dp).clip(CircleShape).background(cor))
        }
    }
}

@Composable
private fun Texto2(valor: String, rotulo: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(valor, color = Cores.Texto, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        Text(rotulo, color = Cores.TextoSuave, fontSize = 13.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 2.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// 2) ESPORTIVA: grade escura — carro e navegação | Spotify, leituras e luzes | clima e consumo
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeEsportiva(monitor: MediaMonitor, sensores: Sensores, obd: ObdMonitor) {
    val ctx = LocalContext.current
    val d = obd.dados
    val vel = d.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
    val f = d.falhas
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1.45f).fillMaxWidth().cartaoSuave()) {
                FotoCarro(Modifier.fillMaxSize(), 1f)
                Column(Modifier.fillMaxSize().padding(18.dp)) { TituloCarro() }
            }
            CartaoNavegacaoApps(Modifier.weight(0.75f).fillMaxWidth())
        }
        Column(Modifier.weight(1.05f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SpotifyLinha(monitor, Modifier.weight(0.85f).fillMaxWidth())
            Column(Modifier.weight(1.05f).fillMaxWidth().cartaoSuave().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Grande("${vel.toInt()}", "km/h", Modifier.weight(1f)); Grande(d.rpm?.toString() ?: "—", "RPM", Modifier.weight(1f))
                    Icon(Icons.Outlined.Speed, null, tint = br.com.civic.launcher.obd.EcoConducao.obter().nivel?.cor ?: Cores.TextoSuave, modifier = Modifier.size(34.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Cores.Borda))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Grande(d.tempMotor?.toString() ?: "—", "°C motor", Modifier.weight(1f))
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.LocalGasStation, null, tint = Cores.Destaque, modifier = Modifier.size(24.dp))
                        Grande(d.nivelCombustivel?.toString() ?: "—", "% combustível", Modifier)
                    }
                    Row(Modifier.clip(RoundedCornerShape(50)).background(if (f == null || f == 0) Cores.Chip else Cores.Vermelho.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(if (f == 0) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, tint = if (f == null) Cores.TextoSuave else if (f == 0) Cores.Ok else Cores.Vermelho, modifier = Modifier.size(18.dp))
                        Text(when (f) { null -> "Falhas —"; 0 -> "Sem falhas"; 1 -> "1 falha"; else -> "$f falhas" }, color = Cores.Texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Box(Modifier.weight(0.5f).fillMaxWidth().cartaoSuave().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) { LuzesCarro(d, Modifier.fillMaxWidth(), 20) }
        }
        Column(Modifier.weight(0.9f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CartaoClima(sensores.clima, Modifier.weight(1.2f).fillMaxWidth())
            Column(Modifier.weight(0.8f).fillMaxWidth().cartaoSuave().padding(18.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(consumoNovo(d, ctx), color = Cores.Texto, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                    Text("km/l", color = Cores.TextoSuave, fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.LocalGasStation, null, tint = Cores.TextoSuave, modifier = Modifier.size(34.dp))
                }
                Text("Média de combustível", color = Cores.TextoSuave, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun Grande(valor: String, rotulo: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, color = Cores.Texto, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
        Text(rotulo, color = Cores.TextoSuave, fontSize = 13.sp, maxLines = 1, softWrap = false)
    }
}

// ---------------------------------------------------------------------------------------------
// 3) NOVA LINHA: carro grande à esquerda; faixa de leituras, navegação, Spotify e clima à direita
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeNovaLinha(monitor: MediaMonitor, sensores: Sensores, obd: ObdMonitor) {
    val ctx = LocalContext.current
    val config = remember { Configuracoes.obter(ctx) }
    val d = obd.dados
    val f = d.falhas
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.weight(0.95f).fillMaxHeight().cartaoSuave(RoundedCornerShape(28.dp))) {
            FotoCarro(Modifier.fillMaxSize(), 1f)
            Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(config.nomeCarro.ifBlank { "Seu carro" }, color = Cores.Texto, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(config.modeloEAno, color = Cores.Texto, fontSize = 26.sp, maxLines = 1)
            }
        }
        Column(Modifier.weight(2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            // Faixa de leituras + situação.
            Column(Modifier.weight(0.75f).fillMaxWidth().cartaoSuave().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val itens = listOf(
                        Triple(d.rpm?.toString() ?: "—", "RPM", Icons.Outlined.Memory), Triple(d.tempMotor?.toString() ?: "—", "°C motor", Icons.Outlined.Thermostat),
                        Triple(d.tempAdmissao?.toString() ?: "—", "°C ar", Icons.Outlined.WaterDrop), Triple(d.tensao?.let { "%.1f".format(BRN, it) } ?: "—", "V", Icons.Outlined.BatteryChargingFull),
                        Triple(d.nivelCombustivel?.toString() ?: "—", "% combustível", Icons.Outlined.LocalGasStation), Triple(consumoNovo(d, ctx), "km/l", Icons.Outlined.Eco),
                    )
                    itens.forEachIndexed { i, (v, r, ic) ->
                        if (i > 0) Box(Modifier.width(1.dp).height(46.dp).background(Cores.Borda))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(ic, null, tint = Cores.Destaque, modifier = Modifier.size(22.dp))
                                Text(v, color = Cores.Texto, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                            }
                            Text(r, color = Cores.TextoSuave, fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Cores.Borda))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.clip(RoundedCornerShape(50)).background(if (f == null || f == 0) Cores.Chip else Cores.Vermelho.copy(alpha = 0.14f)).border(1.dp, Cores.ChipBorda, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (f == 0) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, tint = if (f == null) Cores.TextoSuave else if (f == 0) Cores.Ok else Cores.Vermelho, modifier = Modifier.size(18.dp))
                        Text(when (f) { null -> "Falhas —"; 0 -> "Sem falhas"; 1 -> "1 falha"; else -> "$f falhas" }, color = Cores.Texto, fontSize = 15.sp)
                    }
                    Spacer(Modifier.weight(1f)); LuzesCarro(d, Modifier, 16); Spacer(Modifier.weight(0.4f))
                }
            }
            Row(Modifier.weight(1.5f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                CartaoNavegacaoApps(Modifier.weight(1.3f).fillMaxHeight(), comFavoritos = true)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    CartaoSpotify(monitor, Modifier.weight(1f).fillMaxWidth().sombraCaixaNova())
                    CartaoClima(sensores.clima, Modifier.weight(1f).fillMaxWidth().sombraCaixaNova())
                }
            }
        }
    }
}

private fun Modifier.sombraCaixaNova(): Modifier =
    this.then(Modifier.shadow(10.dp, FormaCartao, clip = false, ambientColor = Color(0x22283C64), spotColor = Color(0x33283C64)))
