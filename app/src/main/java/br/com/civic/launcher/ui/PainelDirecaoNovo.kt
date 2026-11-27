package br.com.civic.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.ComputadorBordo
import br.com.civic.launcher.Configuracoes
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.Veiculo
import br.com.civic.launcher.obd.EcoConducao
import br.com.civic.launcher.obd.ObdMonitor
import java.util.Locale

private val BRP = Locale("pt", "BR")

/** Uma leitura do painel (ícone, rótulo, valor e unidade). */
private class LeituraPainel(val icone: ImageVector, val rotulo: String, val valor: String, val unidade: String, val cor: Color? = null)

@Composable
private fun leituras(sensores: Sensores, obd: ObdMonitor): Map<String, LeituraPainel> {
    val ctx = LocalContext.current
    val d = obd.dados
    val medio = d.consumoMedio?.toDouble() ?: Veiculo.obter(ctx).consumoMedio()?.toDouble()
    val autonomia = if (d.nivelCombustivel != null && medio != null) (d.nivelCombustivel / 100.0 * Configuracoes.obter(ctx).capacidadeEfetiva * medio).toInt() else null
    val viagem = ComputadorBordo.obter(ctx).atual
    val clima = sensores.clima
    fun n0(v: Number?) = v?.let { "%,d".format(BRP, it.toLong()) } ?: "—"
    fun n1(v: Number?) = v?.let { "%.1f".format(BRP, it.toDouble()) } ?: "—"
    return linkedMapOf(
        "rpm" to LeituraPainel(Icons.Filled.Speed, "Rotação", n0(d.rpm), "rpm", if ((d.rpm ?: 0) >= 4500) Cores.Vermelho else null),
        "motor" to LeituraPainel(Icons.Filled.Thermostat, "Motor", n0(d.tempMotor), "°C", if ((d.tempMotor ?: 0) >= 105) Cores.Vermelho else null),
        "tensao" to LeituraPainel(Icons.Filled.BatteryChargingFull, "Tensão", n1(d.tensao), "V"),
        "combustivel" to LeituraPainel(Icons.Filled.LocalGasStation, "Combustível", n0(d.nivelCombustivel), "%"),
        "consumo" to LeituraPainel(Icons.Filled.EvStation, "Consumo médio", n1(medio), "km/l"),
        "autonomia" to LeituraPainel(Icons.Filled.AddRoad, "Autonomia", n0(autonomia), "km"),
        "viagem" to LeituraPainel(Icons.Filled.Route, "Viagem", n1(viagem?.distanciaKm), "km"),
        "tempo" to LeituraPainel(Icons.Filled.Timer, "Tempo de viagem", viagem?.let { ComputadorBordo.duracao(it.duracaoMs) } ?: "—", ""),
        "media" to LeituraPainel(Icons.Filled.Timeline, "Média da viagem", viagem?.let { "%.0f".format(BRP, it.velMedia) } ?: "—", "km/h"),
        "clima" to LeituraPainel(Icons.Filled.WbSunny, clima?.condicao?.texto ?: "Clima", clima?.let { "${it.temperatura}°" } ?: "—", ""),
    )
}

@Composable
private fun corCartao(fundoEscuro: Boolean): Color = if (fundoEscuro) Cores.CartaoSolido else Cores.Cartao.copy(alpha = 0.80f)

@Composable
private fun CartaoLeitura(l: LeituraPainel, fundoEscuro: Boolean, modifier: Modifier, grande: Boolean = false) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(corCartao(fundoEscuro)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(l.icone, null, tint = l.cor ?: Cores.Destaque, modifier = Modifier.size(if (grande) 30.dp else 24.dp))
        Column {
            Text(l.rotulo, color = Cores.TextoSuave, fontSize = 12.sp, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(l.valor, color = l.cor ?: Cores.Texto, fontSize = if (grande) 26.sp else 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (l.unidade.isNotEmpty()) Text(" ${l.unidade}", color = Cores.TextoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp), maxLines = 1)
            }
        }
    }
}

/** Velocímetro grande (cor do modo de condução) com a velocidade no centro. */
@Composable
private fun VelocimetroPainel(sensores: Sensores, obd: ObdMonitor, modifier: Modifier) {
    val vel = obd.dados.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
    val cor = EcoConducao.obter().nivel?.cor
    Box(modifier, contentAlignment = Alignment.Center) {
        Medidor(vel / 220f, cor ?: Cores.Destaque, Modifier.aspectRatio(1f), espessura = 14.dp) { dm ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${vel.toInt()}", color = cor ?: Cores.Texto, fontSize = (dm.value * 0.30f).sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text("km/h", color = Cores.TextoSuave, fontSize = (dm.value * 0.09f).sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Selo do modo de condução (econômico / normal / alto consumo), em verde quando econômico. */
@Composable
private fun SeloEco(fundoEscuro: Boolean, modifier: Modifier = Modifier) {
    val n = EcoConducao.obter().nivel
    Row(
        modifier.clip(RoundedCornerShape(50)).background(corCartao(fundoEscuro)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Eco, null, tint = n?.cor ?: Cores.TextoSuave, modifier = Modifier.size(20.dp))
        Text(n?.let { "Condução ${it.rotulo}" } ?: "ECO: sem dados", color = n?.cor ?: Cores.TextoSuave, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * Estilos novos do painel de direção (Completo e Cartões). A largura escolhida (22% a 50% da tela) define quanta informação aparece:
 * estreito, o essencial (velocidade, setas e farol, motor, combustível e Spotify); largo, também rpm, tensão, consumo, autonomia,
 * viagem e clima. Em faixa (topo/baixo), tudo numa linha só.
 */
@Composable
internal fun PainelNovo(sensores: Sensores, monitor: MediaMonitor, obd: ObdMonitor, estilo: EstiloPainel, fundoEscuro: Boolean, barra: @Composable (Boolean) -> Unit) {
    val l = leituras(sensores, obd)
    val fundo = Cores.Fundo.copy(alpha = if (fundoEscuro) 0.97f else 0.68f)
    BoxWithConstraints(Modifier.fillMaxSize().background(fundo)) {
        val w = maxWidth; val h = maxHeight
        val faixa = w > h
        Column(Modifier.fillMaxSize()) {
            barra(faixa)
            if (faixa) Faixa(sensores, monitor, obd, l, fundoEscuro, w, Modifier.weight(1f).fillMaxWidth().padding(8.dp))
            else if (estilo == EstiloPainel.Completo) Completo(sensores, monitor, obd, l, fundoEscuro, w, Modifier.weight(1f).fillMaxWidth().padding(10.dp))
            else Cartoes(sensores, monitor, obd, l, fundoEscuro, w, Modifier.weight(1f).fillMaxWidth().padding(10.dp))
        }
    }
}

@Composable
private fun Spotify(monitor: MediaMonitor, fundoEscuro: Boolean, modifier: Modifier) =
    SpotifyGrande(monitor, modifier.clip(RoundedCornerShape(18.dp)).background(corCartao(fundoEscuro)).padding(10.dp))

/** Grade de leituras em [colunas] colunas, linha a linha. */
@Composable
private fun Grade(itens: List<LeituraPainel>, colunas: Int, fundoEscuro: Boolean, modifier: Modifier, alturaLinha: Dp? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itens.chunked(colunas).forEach { linha ->
            Row(Modifier.fillMaxWidth().then(if (alturaLinha != null) Modifier.height(alturaLinha) else Modifier.weight(1f)), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                linha.forEach { CartaoLeitura(it, fundoEscuro, Modifier.weight(1f).fillMaxHeight()) }
                repeat(colunas - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Completo (coluna): velocímetro grande com setas e farol, leituras que crescem com a largura e o Spotify com a capa embaixo. */
@Composable
private fun Completo(sensores: Sensores, monitor: MediaMonitor, obd: ObdMonitor, l: Map<String, LeituraPainel>, fundoEscuro: Boolean, w: Dp, modifier: Modifier) {
    val largo = w >= 520.dp; val medio = w >= 400.dp
    val chaves = when {
        largo -> listOf("rpm", "motor", "tensao", "combustivel", "consumo", "autonomia", "viagem", "tempo", "clima")
        medio -> listOf("motor", "combustivel", "consumo", "autonomia", "rpm", "viagem")
        else -> listOf("motor", "combustivel", "consumo", "autonomia")
    }
    val colunas = if (largo) 3 else 2
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1.25f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VelocimetroPainel(sensores, obd, Modifier.weight(1f).fillMaxHeight())
            if (medio) Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IndicadoresVeiculo(obd, tamanho = 40.dp)
                SeloEco(fundoEscuro)
                CartaoLeitura(l.getValue("media"), fundoEscuro, Modifier.fillMaxWidth())
            }
        }
        if (!medio) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IndicadoresVeiculo(obd, tamanho = 34.dp); SeloEco(fundoEscuro)
        }
        Grade(chaves.map { l.getValue(it) }, colunas, fundoEscuro, Modifier.fillMaxWidth(), alturaLinha = 62.dp)
        Spotify(monitor, fundoEscuro, Modifier.weight(1f).fillMaxWidth())
    }
}

/** Cartões (coluna): velocidade em destaque, as leituras em cartões (mais colunas quanto mais largo) e o Spotify. */
@Composable
private fun Cartoes(sensores: Sensores, monitor: MediaMonitor, obd: ObdMonitor, l: Map<String, LeituraPainel>, fundoEscuro: Boolean, w: Dp, modifier: Modifier) {
    val largo = w >= 520.dp; val medio = w >= 400.dp
    val vel = obd.dados.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
    val cor = EcoConducao.obter().nivel?.cor
    val chaves = when {
        largo -> listOf("rpm", "motor", "tensao", "combustivel", "consumo", "autonomia", "viagem", "media", "clima")
        medio -> listOf("rpm", "motor", "tensao", "combustivel", "consumo", "autonomia")
        else -> listOf("motor", "combustivel", "consumo", "autonomia")
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(corCartao(fundoEscuro)).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${vel.toInt()}", color = cor ?: Cores.Texto, fontSize = if (medio) 64.sp else 50.sp, lineHeight = 64.sp, fontWeight = FontWeight.ExtraBold)
                Text(" km/h", color = Cores.TextoSuave, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IndicadoresVeiculo(obd, tamanho = 34.dp)
                if (medio) SeloEco(fundoEscuro)
            }
        }
        Grade(chaves.map { l.getValue(it) }, if (largo) 3 else 2, fundoEscuro, Modifier.weight(1.3f).fillMaxWidth())
        Spotify(monitor, fundoEscuro, Modifier.weight(1f).fillMaxWidth())
    }
}

/** Faixa (painel no topo ou embaixo): velocidade, leituras e Spotify lado a lado. */
@Composable
private fun Faixa(sensores: Sensores, monitor: MediaMonitor, obd: ObdMonitor, l: Map<String, LeituraPainel>, fundoEscuro: Boolean, w: Dp, modifier: Modifier) {
    val vel = obd.dados.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
    val cor = EcoConducao.obter().nivel?.cor
    val chaves = if (w >= 1000.dp) listOf("motor", "combustivel", "consumo", "autonomia") else listOf("motor", "combustivel")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(corCartao(fundoEscuro)).fillMaxHeight().padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${vel.toInt()}", color = cor ?: Cores.Texto, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text("km/h", color = Cores.TextoSuave, fontSize = 12.sp)
        }
        IndicadoresVeiculo(obd, tamanho = 30.dp)
        chaves.forEach { CartaoLeitura(l.getValue(it), fundoEscuro, Modifier.weight(1f).fillMaxHeight()) }
        Spotify(monitor, fundoEscuro, Modifier.weight(2f).fillMaxHeight())
    }
}
