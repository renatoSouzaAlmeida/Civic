package br.com.civic.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.ClimaUi
import br.com.civic.launcher.Configuracoes
import br.com.civic.launcher.LayoutHome
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.Veiculo
import br.com.civic.launcher.obd.DadosObd
import br.com.civic.launcher.obd.ObdMonitor
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val BR = Locale("pt", "BR")
private val EspacoCaixas = 18.dp

/** Pílula para testar as propostas da Tela Início (fica acima do corpo, como no OBD). */
@Composable
fun SeletorLayoutHome(modifier: Modifier = Modifier) {
    val config = Configuracoes.obter(LocalContext.current)
    var aberto by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Cores.Cartao).border(1.dp, Cores.Borda, RoundedCornerShape(50))
                .clickable { aberto = true }.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Dashboard, null, tint = Cores.Destaque, modifier = Modifier.size(18.dp))
            Text("Início: ${config.layoutHome.rotulo}", color = Cores.Texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            LayoutHome.entries.forEach { l ->
                DropdownMenuItem(text = { Text(l.rotulo + if (l == config.layoutHome) "  ✓" else "", fontSize = 18.sp) }, onClick = { config.salvarLayoutHome(l); aberto = false })
            }
        }
    }
}

/** Sombra suave por fora dos cartões já existentes (mapa, Spotify, clima), para destacar cada caixa. */
private fun Modifier.sombraCaixa(): Modifier =
    shadow(10.dp, FormaCartao, clip = false, ambientColor = Color(0x22283C64), spotColor = Color(0x33283C64))

private fun consumo(d: DadosObd, ctx: android.content.Context): String =
    (d.consumoMedio ?: Veiculo.obter(ctx).consumoMedio())?.let { "%.1f".format(BR, it) } ?: "—"

// ---------------------------------------------------------------------------------------------
// Proposta "Caixas" (clara): caixas bem separadas, mapa menor, resumo do OBD em destaque
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeCaixas(monitor: MediaMonitor, sensores: Sensores, obd: ObdMonitor) {
    Row(Modifier.fillMaxSize().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(EspacoCaixas)) {
        CaixaCarro(obd, Modifier.weight(1f).fillMaxHeight())
        Column(Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(EspacoCaixas)) {
            CartaoWaze(sensores, obd, Modifier.weight(0.75f).fillMaxWidth().sombraCaixa())
            CaixaResumoObd(obd, Modifier.weight(1.25f).fillMaxWidth())
        }
        Column(Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(EspacoCaixas)) {
            CartaoSpotify(monitor, Modifier.weight(1.25f).fillMaxWidth().sombraCaixa())
            CartaoClima(sensores.clima, Modifier.weight(1f).fillMaxWidth().sombraCaixa())
        }
    }
}

/** Foto do carro, nome, placa e situação, numa caixa só. */
@Composable
private fun CaixaCarro(obd: ObdMonitor, modifier: Modifier) {
    Box(modifier.cartaoSuave()) {
        FotoCarro(Modifier.fillMaxSize(), 0.62f)
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            TituloCarro()
            ChipsCarro(obd.dados)
        }
    }
}

/** Resumo do OBD: giro com barra em degraus e uma grade de leituras. */
@Composable
private fun CaixaResumoObd(obd: ObdMonitor, modifier: Modifier) {
    val ctx = LocalContext.current
    val d = obd.dados
    Column(modifier.cartaoSuave().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Speed, null, tint = Cores.Destaque, modifier = Modifier.size(20.dp))
                Text("Motor", color = Cores.TextoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(d.rpm?.toString() ?: "—", color = Cores.Texto, fontSize = 30.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, style = SombraNumero)
                Text("rpm", color = Cores.TextoSuave, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        BarraSegmentos((d.rpm ?: 0) / 8000f, Modifier.fillMaxWidth(), 28, 18.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Leitura(Icons.Outlined.Thermostat, d.tempMotor?.toString() ?: "—", "°C motor", Modifier.weight(1f))
            Leitura(Icons.Outlined.BatteryChargingFull, d.tensao?.let { "%.1f".format(BR, it) } ?: "—", "V bateria", Modifier.weight(1f))
            Leitura(Icons.Outlined.AcUnit, d.tempAdmissao?.toString() ?: "—", "°C ar", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Leitura(Icons.Outlined.LocalGasStation, d.nivelCombustivel?.toString() ?: "—", "% tanque", Modifier.weight(1f))
            Leitura(Icons.Outlined.Eco, consumo(d, ctx), "km/l", Modifier.weight(1f))
            val f = d.falhas
            Leitura(
                if (f == 0) Icons.Outlined.CheckCircle else Icons.Outlined.Warning, f?.toString() ?: "—", if (f == 1) "falha" else "falhas", Modifier.weight(1f),
                cor = when { f == null -> Cores.TextoSuave; f == 0 -> Cores.Ok; else -> Cores.Vermelho },
            )
        }
    }
}

/** Mini caixa de leitura (ícone, valor e rótulo), usada nas duas propostas. */
@Composable
private fun Leitura(icone: ImageVector, valor: String, rotulo: String, modifier: Modifier = Modifier, cor: Color = Cores.Destaque) {
    val forma = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(forma).background(Cores.Chip).border(1.dp, Cores.ChipBorda, forma).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icone, null, tint = cor, modifier = Modifier.size(18.dp))
        Text(valor, color = Cores.Texto, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(rotulo, color = Cores.TextoSuave, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------------------------
// Proposta "Painel": carro + leituras | velocidade e giro no centro | mapa, música e clima
// (segue o tema do app: claro de dia, escuro à noite)
// ---------------------------------------------------------------------------------------------

@Composable
fun HomePainel(monitor: MediaMonitor, sensores: Sensores, obd: ObdMonitor) {
    val ctx = LocalContext.current
    val d = obd.dados
    val vel = d.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
    Row(Modifier.fillMaxSize().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(EspacoCaixas)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(EspacoCaixas)) {
            Box(Modifier.weight(1.1f).fillMaxWidth().cartaoSuave()) {
                FotoCarro(Modifier.fillMaxSize(), 0.85f)
                Column(Modifier.fillMaxSize().padding(18.dp)) { TituloCarro() }
            }
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Leitura(Icons.Outlined.Thermostat, d.tempMotor?.toString() ?: "—", "°C motor", Modifier.weight(1f).fillMaxHeight())
                    Leitura(Icons.Outlined.BatteryChargingFull, d.tensao?.let { "%.1f".format(BR, it) } ?: "—", "V bateria", Modifier.weight(1f).fillMaxHeight())
                    Leitura(Icons.Outlined.AcUnit, d.tempAdmissao?.toString() ?: "—", "°C ar", Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Leitura(Icons.Outlined.LocalGasStation, d.nivelCombustivel?.toString() ?: "—", "% tanque", Modifier.weight(1f).fillMaxHeight())
                    Leitura(Icons.Outlined.Eco, consumo(d, ctx), "km/l", Modifier.weight(1f).fillMaxHeight())
                    Leitura(Icons.Outlined.Speed, d.carga?.toString() ?: "—", "% carga", Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        PainelCentral(d, vel, Modifier.weight(1.3f).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(EspacoCaixas)) {
            CartaoWaze(sensores, obd, Modifier.weight(0.85f).fillMaxWidth().sombraCaixa())
            CartaoSpotify(monitor, Modifier.weight(1.3f).fillMaxWidth().sombraCaixa())
            ClimaCompacto(sensores.clima, Modifier.weight(0.7f).fillMaxWidth())
        }
    }
}

/** Velocidade grande dentro de um arco em degraus; embaixo, o giro em barra com temperatura e farol. */
@Composable
private fun PainelCentral(d: DadosObd, vel: Float, modifier: Modifier) {
    val destaque = Cores.Destaque
    val amarelo = Cores.Amarelo
    val vermelho = Cores.Vermelho
    val vazio = Cores.SegVazio
    Column(modifier.cartaoSuave(RoundedCornerShape(26.dp)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val lado = minOf(maxWidth, maxHeight)
            Canvas(Modifier.size(lado)) {
                val segmentos = 44
                val fracao = (vel / 200f).coerceIn(0f, 1f)
                val ligados = (fracao * segmentos).toInt()
                val r = size.minDimension / 2f
                val externo = r * 0.98f
                val interno = r * 0.84f
                val c = Offset(size.width / 2f, size.height / 2f)
                for (i in 0 until segmentos) {
                    val ang = Math.toRadians(135.0 + 270.0 * (i + 0.5) / segmentos)
                    val cor = when {
                        i >= ligados -> vazio
                        i >= segmentos * 0.85f -> vermelho
                        i >= segmentos * 0.6f -> amarelo
                        else -> destaque
                    }
                    drawLine(
                        cor, Offset(c.x + interno * cos(ang).toFloat(), c.y + interno * sin(ang).toFloat()),
                        Offset(c.x + externo * cos(ang).toFloat(), c.y + externo * sin(ang).toFloat()),
                        strokeWidth = r * 0.07f, cap = StrokeCap.Round,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${vel.toInt()}", color = Cores.Texto, fontSize = (lado.value * 0.30f).sp, lineHeight = (lado.value * 0.30f).sp,
                    fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, maxLines = 1, style = SombraNumero)
                Text("km/h", color = Cores.TextoSuave, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                d.consumoInstantaneo?.let {
                    Spacer(Modifier.height(6.dp))
                    ChipInfo("%.1f km/l agora".format(BR, it), Icons.Filled.Eco, Cores.Ok)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Thermostat, null, tint = if ((d.tempMotor ?: 0) >= 105) Cores.Vermelho else Cores.Destaque, modifier = Modifier.size(22.dp))
            Text(d.tempMotor?.let { "$it°" } ?: "—", color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            BarraSegmentos((d.rpm ?: 0) / 8000f, Modifier.weight(1f), 30, 20.dp)
            Text(d.rpm?.let { "%,d".format(BR, it) } ?: "—", color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            LuzEspia(androidx.compose.ui.res.painterResource(br.com.civic.launcher.R.drawable.ic_farol), d.farol, Color(0xFF22C55E), tamanho = 24.dp)
        }
        Text("rpm", color = Cores.TextoSuave, fontSize = 11.sp)
    }
}

/** Clima numa caixa baixa: ícone, temperatura, condição e chuva. Tocar abre a previsão completa. */
@Composable
private fun ClimaCompacto(clima: ClimaUi?, modifier: Modifier) {
    var aberto by remember { mutableStateOf(false) }
    if (aberto) ModalClima { aberto = false }
    Row(
        modifier.cartaoSuave().clickable { aberto = true }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (clima == null) {
            Text("Clima: precisa de internet", color = Cores.TextoSuave, fontSize = 14.sp)
            return@Row
        }
        val (icone, cor) = iconeCondicao(clima.condicao)
        Icon(icone, null, tint = cor, modifier = Modifier.size(38.dp))
        Text("${clima.temperatura}°", color = Cores.Texto, fontSize = 34.sp, fontWeight = FontWeight.Light, maxLines = 1)
        Column(Modifier.weight(1f)) {
            Text(clima.condicao.texto, color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(clima.chuva.ifEmpty { "Máx. ${clima.maxima}°  Mín. ${clima.minima}°" }, color = if (clima.chovendo) Cores.Waze else Cores.TextoSuave,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
