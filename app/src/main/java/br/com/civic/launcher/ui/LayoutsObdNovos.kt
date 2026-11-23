package br.com.civic.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.ComputadorBordo
import br.com.civic.launcher.Configuracoes
import br.com.civic.launcher.ImagemCarro
import br.com.civic.launcher.LayoutObd
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.R
import br.com.civic.launcher.Veiculo
import br.com.civic.launcher.obd.DadosObd
import br.com.civic.launcher.obd.EcoConducao
import br.com.civic.launcher.obd.NivelEco
import br.com.civic.launcher.obd.ObdMonitor

/** Ações comuns dos layouts novos (abas e menu). */
internal class AcoesObd(val aoVerDiagnostico: () -> Unit, val aoVerViagem: () -> Unit, val aoConfig: () -> Unit, val aoMais: () -> Unit, val aoSensores: () -> Unit, val aoVerDiagnosticoAba: (Int) -> Unit = { aoVerDiagnostico() })

// ------------------------------------------- dados -------------------------------------------

private class ValoresObd(val consumoMedio: Double?, val autonomiaKm: Int?, val tripKm: Double?)

@Composable
private fun valores(d: DadosObd): ValoresObd {
    val ctx = LocalContext.current
    val cfg = Configuracoes.obter(ctx)
    val medio = d.consumoMedio?.toDouble() ?: Veiculo.obter(ctx).consumoMedio()?.toDouble()
    val autonomia = if (d.nivelCombustivel != null && medio != null) (d.nivelCombustivel / 100.0 * cfg.capacidadeEfetiva * medio).toInt() else null
    return ValoresObd(medio, autonomia, ComputadorBordo.obter(ctx).atual?.distanciaKm)
}

/** Marcha mostrada como na proposta: o OBD não informa a marcha; "D" com o motor ligado e o carro andando, "P" parado. */
private fun marcha(d: DadosObd, vel: Float): String = if ((d.rpm ?: 0) > 400 && vel >= 2f) "D" else "P"

@Composable
private fun SeloMarcha(t: TemaObd, letra: String, tamanho: Dp = 44.dp) {
    Box(
        Modifier.size(width = tamanho * 1.25f, height = tamanho).clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3B8DFF), Color(0xFF1655D8)))).border(1.dp, t.neon, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(letra, color = Color.White, fontSize = (tamanho.value * 0.55f).sp, fontWeight = FontWeight.ExtraBold) }
}

private fun abasPadrao(a: AcoesObd, comViagem: Boolean): List<AbaObd> = buildList {
    add(AbaObd("Direção", Icons.Filled.Home) {})
    if (comViagem) add(AbaObd("Viagem", Icons.Filled.Route, a.aoVerViagem))
    add(AbaObd("Sensores", Icons.Filled.ShowChart, a.aoSensores))
    if (comViagem) { add(AbaObd("Gráficos", Icons.Filled.BarChart, a.aoVerViagem)); add(AbaObd("Diagnóstico", Icons.Outlined.ReportProblem, a.aoVerDiagnostico)) }
    else { add(AbaObd("Diagnóstico", Icons.Outlined.ReportProblem, a.aoVerDiagnostico)); add(AbaObd("Gráficos", Icons.Filled.BarChart, a.aoVerViagem)) }
    add(AbaObd("Mais", Icons.Filled.GridView, a.aoMais))
}

// ---------------------------------- Layout 01 · Esportivo -----------------------------------

/** Layout 01 (Esportivo): conta-giros neon no centro com a velocidade, estrada com luzes azuis e a traseira do carro; leituras dos dois lados. */
@Composable
internal fun LayoutEsportivoNovo(obd: ObdMonitor, monitor: MediaMonitor, vel: Float, a: AcoesObd) {
    val t = temaObd(); val d = obd.dados; val v = valores(d)
    PranchetaObd {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(t.fundo).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Canvas(Modifier.fillMaxSize()) { estradaNeon(t, size.height * 0.62f, cidade = false) }
                // Centro: conta-giros com a velocidade.
                MostradorNeon(
                    t, (d.rpm ?: 0) / 8000f, (0..8).map { it.toString() }, Modifier.align(Alignment.TopCenter).fillMaxHeight(0.92f).aspectRatio(1f), vermelhoDesde = 0.75f, tamanhoRotulo = 20.sp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-8).dp)) {
                        Text(fmt0(d.rpm), color = t.texto, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        Text("rpm", color = t.suave, fontSize = 15.sp)
                        Text("%.0f".format(BRO, vel), color = t.texto, fontSize = 84.sp, lineHeight = 86.sp, fontWeight = FontWeight.ExtraBold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        Text("km/h", color = t.texto, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("x1000", color = t.suave, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 46.dp))
                }
                Image(painterResource(R.drawable.carro_traseira), null, Modifier.align(Alignment.BottomCenter).width(250.dp), contentScale = ContentScale.Fit)
                // Colunas laterais.
                Column(Modifier.align(Alignment.TopStart).width(260.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        InfoObd(t, Icons.Filled.Thermostat, "Temp. Motor", fmt0(d.tempMotor), "°C")
                        InfoObd(t, Icons.Filled.Air, "Temp. Admissão", fmt0(d.tempAdmissao), "°C")
                        InfoObd(t, Icons.Filled.BatteryChargingFull, "Tensão", fmt1(d.tensao), "V")
                        InfoObd(t, Icons.Filled.LocalGasStation, "Combustível", fmt0(d.nivelCombustivel), "%")
                    }
                    LuzesObd(d, Modifier.padding(start = 6.dp, bottom = 4.dp), tamanho = 52.dp)
                }
                Column(Modifier.align(Alignment.TopEnd).width(320.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoObd(t, Icons.Filled.LocalGasStation, "Consumo Inst.", fmt1(d.consumoInstantaneo), "km/l")
                        InfoObd(t, Icons.Filled.EvStation, "Consumo Médio", fmt1(v.consumoMedio?.toFloat()), "km/l")
                        InfoObd(t, Icons.Filled.AddRoad, "Autonomia", fmt0(v.autonomiaKm), "km")
                        InfoObd(t, Icons.Filled.Place, "Trip (viagem)", fmt1(v.tripKm?.toFloat()), "km")
                    }
                    SpotifyObd(t, monitor, Modifier.fillMaxWidth())
                    EcoAssist(t, Modifier.padding(bottom = 4.dp), grande = true)
                }
            }
            AbasObdNovo(t, abasPadrao(a, comViagem = false), 0)
        }
    }
}

// ------------------------------- Layout 02 · Cockpit digital --------------------------------

/** Layout 02 (Cockpit digital): conta-giros e velocímetro neon dos lados, o Civic no centro com aura azul, marcha, modo ECO e 7 leituras embaixo. */
@Composable
internal fun LayoutCockpitDigital(obd: ObdMonitor, monitor: MediaMonitor, vel: Float, a: AcoesObd) {
    val t = temaObd(); val d = obd.dados; val v = valores(d)
    val cfg = Configuracoes.obter(LocalContext.current)
    PranchetaObd {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(t.fundo).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MostradorNeon(t, (d.rpm ?: 0) / 8000f, (0..8).map { it.toString() }, Modifier.weight(1f).fillMaxHeight(), vermelhoDesde = 0.75f, tamanhoRotulo = 18.sp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmt0(d.rpm), color = t.texto, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        Text("RPM", color = t.suave, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Box(Modifier.weight(1.45f).fillMaxHeight()) {
                    Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SetaObd(d, esquerda = true, tamanho = 46.dp)
                        SetaObd(d, esquerda = false, tamanho = 46.dp)
                    }
                    Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(width = 38.dp, height = 28.dp).border(2.dp, t.texto, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                            Text("H", color = t.texto, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(listOf(cfg.nomeCarro, cfg.modeloEAno).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { "Civic" }, color = t.texto, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    // Aura azul do piso, embaixo do carro.
                    Canvas(Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2, size.height * 0.70f)
                        for (k in 4 downTo 1) drawOval(t.aura.copy(alpha = 0.08f * k), Offset(c.x - size.width * 0.12f * k, c.y - size.height * 0.035f * k), Size(size.width * 0.24f * k, size.height * 0.07f * k))
                        drawOval(t.neon.copy(alpha = 0.7f), Offset(c.x - size.width * 0.38f, c.y - size.height * 0.07f), Size(size.width * 0.76f, size.height * 0.14f), style = Stroke(3f))
                    }
                    Image(painterResource(if (cfg.imagemCarro == ImagemCarro.Fit) R.drawable.carro_fit_cinza else R.drawable.civic_recorte), null,
                        Modifier.align(Alignment.Center).fillMaxWidth(0.82f).offset(y = 6.dp), contentScale = ContentScale.Fit)
                    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SeloMarcha(t, marcha(d, vel), 40.dp)
                        ModosConducao(t)
                    }
                }
                MostradorNeon(t, vel / 220f, (0..11).map { (it * 20).toString() }, Modifier.weight(1f).fillMaxHeight(), vermelhoDesde = 0.91f, tamanhoRotulo = 14.sp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%.0f".format(BRO, vel), color = t.texto, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        Text("km/h", color = t.suave, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpotifyObd(t, monitor, Modifier.weight(3.2f).fillMaxHeight())
                Ladrilho(t, Icons.Filled.LocalGasStation, "${fmt0(d.nivelCombustivel)} %", "Combustível", Modifier.weight(1f))
                Ladrilho(t, Icons.Filled.Thermostat, "${fmt0(d.tempMotor)} °C", "Temp. Motor", Modifier.weight(1f))
                Ladrilho(t, Icons.Filled.Air, "${fmt0(d.tempAdmissao)} °C", "Temp. Admissão", Modifier.weight(1f))
                Ladrilho(t, Icons.Filled.BatteryChargingFull, "${fmt1(d.tensao)} V", "Tensão", Modifier.weight(1f))
                Ladrilho(t, Icons.Filled.EvStation, "${fmt1(v.consumoMedio?.toFloat())} km/l", "Cons. Médio", Modifier.weight(1f))
                Ladrilho(t, Icons.Filled.Speed, "${fmt0(d.map)} kPa", "MAP", Modifier.weight(1f))
                Ladrilho(t, Icons.Filled.DirectionsRun, "${fmt0(d.acelerador)} %", "Acelerador", Modifier.weight(1f))
            }
            AbasObdNovo(t, abasPadrao(a, comViagem = true), 0)
        }
    }
}

@Composable
private fun Ladrilho(t: TemaObd, icone: ImageVector, valor: String, rotulo: String, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(t.vidro).border(1.dp, t.bordaVidro, RoundedCornerShape(12.dp)).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icone, null, tint = t.texto, modifier = Modifier.size(20.dp))
        Text(valor, color = t.texto, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(rotulo, color = t.suave, fontSize = 11.sp, maxLines = 1)
    }
}

/** ECO (verde, folha) · NORMAL · SPORT: o ativo segue o nível de consumo calculado pelo app (econômico, normal ou alto consumo). */
@Composable
private fun ModosConducao(t: TemaObd) {
    val n = EcoConducao.obter().nivel
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(t.vidro).border(1.dp, t.bordaVidro, RoundedCornerShape(50)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(NivelEco.Economico to "ECO", NivelEco.Normal to "NORMAL", NivelEco.AltoConsumo to "SPORT").forEach { (nivel, rotulo) ->
            val ativo = n == nivel
            val cor = if (nivel == NivelEco.Economico) t.verde else if (nivel == NivelEco.AltoConsumo) t.laranja else t.neon
            Row(
                Modifier.clip(RoundedCornerShape(50)).then(if (ativo) Modifier.background(cor.copy(alpha = 0.22f)).border(1.dp, cor, RoundedCornerShape(50)) else Modifier).padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (nivel == NivelEco.Economico) Icon(Icons.Filled.Eco, null, tint = if (ativo) t.verde else t.suave, modifier = Modifier.size(16.dp))
                Text(rotulo, color = if (ativo) cor else t.texto, fontSize = 13.sp, fontWeight = if (ativo) FontWeight.ExtraBold else FontWeight.SemiBold)
            }
        }
    }
}

// ----------------------------- Layout 03 · Técnico / Diagnóstico -----------------------------

/** Layout 03 (Técnico / Diagnóstico): menu à esquerda, o carro em raio-X com o motor aceso e os sensores, tabela de leituras e a situação OBD embaixo. */
@Composable
internal fun LayoutTecnico(obd: ObdMonitor, monitor: MediaMonitor, vel: Float, a: AcoesObd) {
    val t = temaObd(); val d = obd.dados
    val cfg = Configuracoes.obter(LocalContext.current)
    PranchetaObd {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(t.fundo).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Menu.
                Column(Modifier.width(310.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // As mesmas opções da tela de diagnóstico: Falhas, Bateria, Mistura e Saúde (Resumo é esta tela).
                    listOf(
                        Triple(Icons.Filled.Dashboard, "Resumo", -1), Triple(Icons.Outlined.ReportProblem, "Falhas (DTC)", 0), Triple(Icons.Filled.BatteryChargingFull, "Bateria", 1),
                        Triple(Icons.Filled.Tune, "Mistura", 2), Triple(Icons.Filled.CheckCircle, "Saúde", 3), Triple(Icons.Filled.GridView, "Mais (layout, demo, adaptador)", -2),
                    ).forEachIndexed { i, (ic, rotulo, aba) ->
                        val sel = i == 0
                        Row(
                            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp))
                                .then(if (sel) Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF3B8DFF), Color(0xFF1764E8)))).border(1.dp, t.neon, RoundedCornerShape(10.dp)) else Modifier)
                                .clickable { when { aba >= 0 -> a.aoVerDiagnosticoAba(aba); aba == -2 -> a.aoMais() } }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(ic, null, tint = if (sel) Color.White else t.texto, modifier = Modifier.size(20.dp))
                            Text(rotulo, color = if (sel) Color.White else t.texto, fontSize = 15.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    SpotifyObd(t, monitor, Modifier.fillMaxWidth())
                }
                // Carro em raio-X.
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2, size.height * 0.80f)
                        for (k in 3 downTo 1) drawOval(t.neon.copy(alpha = 0.35f / k), Offset(c.x - size.width * 0.16f * k, c.y - size.height * 0.04f * k), Size(size.width * 0.32f * k, size.height * 0.08f * k), style = Stroke(2.5f))
                        drawOval(Brush.radialGradient(listOf(t.aura.copy(alpha = 0.35f), Color.Transparent), c, size.width * 0.4f), Offset(c.x - size.width * 0.45f, c.y - size.height * 0.12f), Size(size.width * 0.9f, size.height * 0.24f))
                    }
                    val raioX = remember(t.escuro) {
                        // Escala de cinza e depois azul: o carro vira "raio-X".
                        val m = ColorMatrix(floatArrayOf(
                            0.10f, 0.20f, 0.05f, 0f, if (t.escuro) 10f else 60f,
                            0.25f, 0.45f, 0.10f, 0f, if (t.escuro) 60f else 110f,
                            0.35f, 0.60f, 0.15f, 0f, if (t.escuro) 150f else 200f,
                            0f, 0f, 0f, 0.80f, 0f,
                        ))
                        ColorFilter.colorMatrix(m)
                    }
                    Box(Modifier.align(Alignment.Center).fillMaxWidth(0.86f).aspectRatio(1300f / 624f)) {
                        Image(painterResource(if (cfg.imagemCarro == ImagemCarro.Fit) R.drawable.carro_fit_cinza else R.drawable.civic_recorte), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, colorFilter = raioX)
                        // Motor aceso (laranja) sob o capô.
                        Canvas(Modifier.fillMaxSize()) {
                            val m = Offset(size.width * 0.33f, size.height * 0.40f)
                            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB020).copy(alpha = 0.95f), Color(0xFFFF7A00).copy(alpha = 0.55f), Color.Transparent), m, size.width * 0.13f), size.width * 0.13f, m)
                        }
                    }
                    // Sensores em volta, ligados ao carro.
                    Canvas(Modifier.fillMaxSize()) {
                        val cor = t.neon.copy(alpha = 0.6f)
                        drawLine(cor, Offset(size.width * 0.17f, size.height * 0.16f), Offset(size.width * 0.33f, size.height * 0.40f), 2f)
                        drawLine(cor, Offset(size.width * 0.86f, size.height * 0.16f), Offset(size.width * 0.66f, size.height * 0.38f), 2f)
                        drawLine(cor, Offset(size.width * 0.06f, size.height * 0.54f), Offset(size.width * 0.28f, size.height * 0.52f), 2f)
                        drawLine(cor, Offset(size.width * 0.86f, size.height * 0.74f), Offset(size.width * 0.66f, size.height * 0.62f), 2f)
                    }
                    // Setas acima do carro.
                    Row(Modifier.align(Alignment.TopCenter).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(70.dp)) {
                        SetaObd(d, esquerda = true, tamanho = 44.dp)
                        SetaObd(d, esquerda = false, tamanho = 44.dp)
                    }
                    NoSensor(t, Icons.Filled.Settings, Modifier.align(Alignment.TopStart).offset(x = 70.dp, y = 8.dp))
                    NoSensor(t, Icons.Filled.BatteryChargingFull, Modifier.align(Alignment.TopEnd).offset(x = (-40).dp, y = 8.dp))
                    NoSensor(t, Icons.Filled.Thermostat, Modifier.align(Alignment.CenterStart).offset(y = 20.dp))
                    NoSensor(t, Icons.Filled.Timeline, Modifier.align(Alignment.BottomEnd).offset(x = (-40).dp, y = (-50).dp))
                }
                // Tabela de leituras.
                Column(Modifier.width(300.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(t.vidro).border(1.dp, t.bordaVidro, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceEvenly) {
                    LinhaTabela(t, "RPM", "${fmt0(d.rpm)} rpm")
                    LinhaTabela(t, "Velocidade", "%.0f km/h".format(BRO, vel))
                    LinhaTabela(t, "Temp. Motor", "${fmt0(d.tempMotor)} °C")
                    LinhaTabela(t, "Temp. Admissão", "${fmt0(d.tempAdmissao)} °C")
                    LinhaTabela(t, "Tensão", "${fmt1(d.tensao)} V")
                    LinhaTabela(t, "Carga do Motor", "${fmt0(d.carga)} %")
                    LinhaTabela(t, "MAP", "${fmt0(d.map)} kPa")
                    LinhaTabela(t, "Acelerador", "${fmt0(d.acelerador)} %")
                    LinhaTabela(t, "Avanço", "${fmt1(d.avanco)} °")
                    LinhaTabela(t, "STFT", d.stft?.let { "%+.1f %%".format(BRO, it) } ?: "—")
                    LinhaTabela(t, "LTFT", d.ltft?.let { "%+.1f %%".format(BRO, it) } ?: "—")
                    LinhaTabela(t, "Malha (Loop)", when (d.malhaFechada) { true -> "Fechada"; false -> "Aberta"; null -> "—" },
                        when (d.malhaFechada) { true -> t.verde; false -> t.laranja; null -> t.suave })
                }
            }
            // Situação OBD.
            Row(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(t.vidro).border(1.dp, t.bordaVidro, RoundedCornerShape(14.dp)).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                val falhas = d.falhas
                Situacao(t, Icons.Filled.CheckCircle, if (falhas == null || falhas == 0) t.verde else t.vermelho,
                    when (falhas) { null -> "Sistema"; 0 -> "Sistema OK"; else -> "$falhas falha(s)" }, when (falhas) { null -> "sem leitura"; 0 -> "(sem falhas)"; else -> "veja em Falhas (DTC)" })
                Situacao(t, Icons.Filled.Shield, t.verde, "Monitores OBD", d.monitoresTotal?.let { "${d.monitoresProntos} de $it prontos" } ?: "sem leitura")
                val pronto = d.monitoresTotal != null && d.monitoresPendentes.isEmpty()
                Situacao(t, Icons.Filled.CheckCircle, if (pronto) t.verde else t.laranja, "Readiness", if (d.monitoresTotal == null) "sem leitura" else if (pronto) "✓ Pronto" else "Pendente")
                Situacao(t, Icons.Filled.Settings, if (d.luzMotor) t.vermelho else t.laranja, "MIL", if (d.luzMotor) "Acesa" else "Desligada")
            }
        }
    }
}

@Composable
private fun NoSensor(t: TemaObd, icone: ImageVector, modifier: Modifier) {
    Box(modifier.size(46.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            for (k in 3 downTo 1) drawCircle(t.neon.copy(alpha = 0.08f * k), size.minDimension / 2 * (0.8f + 0.08f * k))
            drawCircle(if (t.escuro) Color(0xFF0A1630) else Color.White, size.minDimension / 2 * 0.82f)
            drawCircle(t.neon, size.minDimension / 2 * 0.82f, style = Stroke(2.5f))
        }
        Icon(icone, null, tint = t.texto, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun LinhaTabela(t: TemaObd, rotulo: String, valor: String, cor: Color? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(rotulo, color = t.texto, fontSize = 14.sp)
        Text(valor, color = cor ?: t.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Situacao(t: TemaObd, icone: ImageVector, cor: Color, titulo: String, detalhe: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icone, null, tint = cor, modifier = Modifier.size(30.dp))
        Column {
            Text(titulo, color = t.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(detalhe, color = t.suave, fontSize = 12.sp)
        }
    }
}

// ----------------------------- Layout 04 · Direção simplificada ------------------------------

/** Layout 04 (Direção simplificada, alta legibilidade): velocidade enorme sob um arco neon, cidade e estrada ao fundo, ECO Assist no alto e 6 leituras grandes. */
@Composable
internal fun LayoutDirecaoSimples(obd: ObdMonitor, monitor: MediaMonitor, vel: Float, a: AcoesObd) {
    val t = temaObd(); val d = obd.dados; val v = valores(d)
    PranchetaObd {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(t.fundo).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Canvas(Modifier.fillMaxSize()) { estradaNeon(t, size.height * 0.58f, cidade = true) }
                // Arco da velocidade (0 a 200 km/h).
                Canvas(Modifier.align(Alignment.TopCenter).padding(top = 64.dp).fillMaxWidth(0.42f).aspectRatio(1.6f)) {
                    val r = size.width / 2 * 0.92f
                    val tl = Offset(size.width / 2 - r, size.height * 0.95f - r); val tam = Size(r * 2, r * 2)
                    drawArc(t.suave.copy(alpha = 0.25f), 200f, 140f, false, tl, tam, style = Stroke(size.width * 0.035f))
                    arcoComBrilho(t.neon, 200f, 140f * (vel / 200f).coerceIn(0f, 1f), tl, tam, size.width * 0.035f, 4, 1.2f)
                }
                EcoAssist(t, Modifier.align(Alignment.TopCenter), grande = true)
                Column(Modifier.align(Alignment.TopCenter).padding(top = 116.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.0f".format(BRO, vel), color = t.texto, fontSize = 120.sp, lineHeight = 118.sp, fontWeight = FontWeight.ExtraBold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    Text("km/h", color = t.texto, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.carro_traseira), null, Modifier.width(220.dp), contentScale = ContentScale.Fit)
                    SeloMarcha(t, marcha(d, vel), 36.dp)
                }
                Column(Modifier.align(Alignment.TopStart).padding(top = 10.dp).width(260.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        InfoObd(t, Icons.Filled.Settings, "RPM", fmt0(d.rpm), "", grande = true, rotuloEmbaixo = true)
                        InfoObd(t, Icons.Filled.Thermostat, "Temp. Motor", fmt0(d.tempMotor), "°C", grande = true, rotuloEmbaixo = true)
                        InfoObd(t, Icons.Filled.BatteryChargingFull, "Tensão", fmt1(d.tensao), "V", grande = true, rotuloEmbaixo = true)
                    }
                    LuzesObd(d, Modifier.padding(start = 6.dp, bottom = 4.dp), tamanho = 54.dp, farolPrimeiro = false)
                }
                Column(Modifier.align(Alignment.TopEnd).width(320.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.align(Alignment.End).size(40.dp).clip(CircleShape).background(t.vidro).border(1.dp, t.bordaVidro, CircleShape).clickable(onClick = a.aoMais),
                            contentAlignment = Alignment.Center) { Icon(Icons.Filled.MoreHoriz, "Layout, demonstração e adaptador", tint = t.texto, modifier = Modifier.size(24.dp)) }
                        InfoObd(t, Icons.Filled.EvStation, "Cons. Médio", fmt1(v.consumoMedio?.toFloat()), "km/l", grande = true, rotuloEmbaixo = true)
                        InfoObd(t, Icons.Filled.LocalGasStation, "Combustível", fmt0(d.nivelCombustivel), "%", grande = true, rotuloEmbaixo = true)
                        InfoObd(t, Icons.Filled.AddRoad, "Autonomia", fmt0(v.autonomiaKm), "km", grande = true, rotuloEmbaixo = true)
                    }
                    SpotifyObd(t, monitor, Modifier.fillMaxWidth().padding(bottom = 4.dp))
                }
            }
        }
    }
}

/** Os 4 layouts novos, para a TelaCentral escolher pelo LayoutObd. */
@Composable
internal fun LayoutObdNovo(layout: LayoutObd, obd: ObdMonitor, monitor: MediaMonitor, vel: Float, a: AcoesObd): Boolean {
    when (layout) {
        LayoutObd.EsportivoNovo -> LayoutEsportivoNovo(obd, monitor, vel, a)
        LayoutObd.CockpitDigital -> LayoutCockpitDigital(obd, monitor, vel, a)
        LayoutObd.Tecnico -> LayoutTecnico(obd, monitor, vel, a)
        LayoutObd.DirecaoSimples -> LayoutDirecaoSimples(obd, monitor, vel, a)
        else -> return false
    }
    return true
}
