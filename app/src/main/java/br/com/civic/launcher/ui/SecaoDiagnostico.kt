package br.com.civic.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.obd.ConfigCambio
import br.com.civic.launcher.obd.DiagnosticoAvancado
import br.com.civic.launcher.obd.ObdMonitor
import java.util.Locale

private val BRL = Locale("pt", "BR")
private fun dur(s: Int) = if (s >= 60) "${s / 60} min ${s % 60} s" else "$s s"

/** Diagnósticos avançados do OBD (aparecem na tela de falhas): freeze frame, termostato, catalisador e mais. */
@Composable
fun SecaoDiagnosticoAvancado(obd: ObdMonitor) {
    val ctx = LocalContext.current
    val dg = remember { DiagnosticoAvancado.obter(ctx) }
    val d = obd.dados

    dg.situacao?.let { Text(it, color = Cores.Destaque, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }

    Secao("Freeze frame (dados no instante da falha)") {
        if (dg.freezeFrames.isEmpty()) Text("Nenhum ainda. É guardado automaticamente quando surge um código de falha novo.", color = Cores.TextoSuave, fontSize = 14.sp)
        dg.freezeFrames.take(5).forEach { f ->
            Text("${f.codigo} · ${dg.quando(f.em)}", color = Cores.Texto, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(f.itens.joinToString(" · ") { "${it.first} ${it.second}" }, color = Cores.TextoSuave, fontSize = 13.sp)
        }
    }

    Secao("Termostato (aquecimento do motor)") {
        val mediana = dg.medianaAquecimento()
        Text(
            if (mediana != null) "Tempo típico até 80 °C: ${dur(mediana)}" else "Ainda sem medições: o app mede sozinho quando o motor liga frio (até 45 °C).",
            color = Cores.Texto, fontSize = 16.sp,
        )
        dg.aquecimentos.take(5).forEach {
            Text("${dg.quando(it.em)} · " + (it.segundos?.let { s -> dur(s) } ?: "não chegou a 80 °C em 25 min") + " (partiu com ${it.tempInicial} °C)", color = Cores.TextoSuave, fontSize = 13.sp)
        }
        Text("Muito acima do normal ou nunca chegar a 80 °C indica termostato preso aberto.", color = Cores.TextoSuave, fontSize = 13.sp)
    }

    Secao("Catalisador (sondas de oxigênio)") {
        BotaoPilula(if (dg.ocupado) "Testando…" else "Testar catalisador (45 s)", Icons.Filled.Refresh, Modifier.fillMaxWidth()) { if (!dg.ocupado) obd.pedirTeste("catalisador") }
        dg.catalisador?.let { c ->
            Text(c.parecer, color = Cores.Texto, fontSize = 15.sp)
            Text("${dg.quando(c.em)} · oscilações antes ${c.oscilacoesAntes} · depois ${c.oscilacoesDepois}" + (c.razao?.let { " · razão %.2f".format(BRL, it) } ?: ""), color = Cores.TextoSuave, fontSize = 13.sp)
        }
        Text("Faça com o motor quente, em velocidade constante (50–80 km/h). A sonda de depois deve oscilar bem menos que a de antes.", color = Cores.TextoSuave, fontSize = 13.sp)
    }

    Secao("Marcha lenta") {
        val m = dg.marchaLenta
        if (m == null) Text("Medida sozinha com o carro parado e o motor quente.", color = Cores.TextoSuave, fontSize = 14.sp)
        else {
            Text(if (m.irregular) "Irregular" else "Estável", color = if (m.irregular) Cores.Vermelho else Cores.Ok, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("${dg.quando(m.em)} · média ${m.media} rpm · oscila ±%.0f rpm · faixa ${m.faixa} rpm".format(BRL, m.desvio), color = Cores.TextoSuave, fontSize = 13.sp)
        }
    }

    Secao("Dreno de bateria") {
        if (dg.drenos.isEmpty()) Text("Compara a tensão de quando o motor desliga com a da próxima partida. Sem registros ainda.", color = Cores.TextoSuave, fontSize = 14.sp)
        dg.drenos.take(5).forEach {
            Text(
                "${dg.quando(it.em)} · parado %.0f h · %.1f V na partida".format(BRL, it.horasParado, it.tensaoPartida) +
                    (it.taxaVh?.let { t -> " · queda %.0f mV/h".format(BRL, t * 1000) } ?: "") + if (it.suspeito) " · SUSPEITO" else "",
                color = if (it.suspeito) Cores.Vermelho else Cores.TextoSuave, fontSize = 13.sp,
            )
        }
    }

    Secao("Códigos apagados e monitores") {
        Text(
            d.kmDesdeApagar?.let { "Km desde que os códigos foram apagados: %,d".format(BRL, it) } ?: "O carro ainda não informou (PID 0131).",
            color = Cores.Texto, fontSize = 15.sp,
        )
        if (dg.apagadoEm > 0) Text("Último apagamento detectado: ${dg.quando(dg.apagadoEm)}", color = Cores.TextoSuave, fontSize = 13.sp)
        d.monitoresTotal?.let {
            Text("Monitores de emissões: ${d.monitoresProntos} de $it prontos" + if (d.monitoresPendentes.isNotEmpty()) " · faltam: ${d.monitoresPendentes.joinToString()}" else " · pronto para vistoria", color = Cores.TextoSuave, fontSize = 13.sp)
        }
    }

    Secao("Modo 06 (testes do módulo do motor) · experimental") {
        BotaoPilula(if (dg.ocupado) "Lendo…" else "Ler modo 06", Icons.Filled.Refresh, Modifier.fillMaxWidth()) { if (!dg.ocupado) obd.pedirTeste("modo06") }
        dg.modo06.forEach { Text(it, color = Cores.TextoSuave, fontSize = 12.sp) }
        Text("Resultados brutos de cada teste (inclui falhas de ignição por cilindro, em alguns carros). Útil para um mecânico ou para análise.", color = Cores.TextoSuave, fontSize = 13.sp)
    }

    SecaoCambio(obd, dg)
}

@Composable
private fun SecaoCambio(obd: ObdMonitor, dg: DiagnosticoAvancado) {
    var cab by remember { mutableStateOf(dg.cambio.cabecalho) }
    var cmd by remember { mutableStateOf(dg.cambio.comando) }
    var bytes by remember { mutableStateOf(dg.cambio.bytes.toString()) }
    var mult by remember { mutableStateOf(dg.cambio.multiplicador.toString()) }
    var desl by remember { mutableStateOf(dg.cambio.deslocamento.toString()) }
    fun salvar() = dg.salvarCambio(
        ConfigCambio(cab.trim(), cmd.trim().uppercase(), bytes.toIntOrNull()?.coerceIn(1, 2) ?: 1, mult.replace(',', '.').toDoubleOrNull() ?: 1.0, desl.replace(',', '.').toDoubleOrNull() ?: 0.0)
    )
    Secao("Temperatura do fluido do câmbio · experimental") {
        Text(dg.tempCambio?.let { "Agora: $it °C (alerta a partir de 110 °C)" } ?: "Sem leitura. A Honda usa mensagens próprias: informe o PID da temperatura do câmbio do seu modelo.", color = Cores.Texto, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Campo("Cabeçalho (opcional)", cab, Modifier.weight(1f)) { cab = it }
            Campo("Comando (ex.: 221234)", cmd, Modifier.weight(1.4f)) { cmd = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Campo("Bytes (1 ou 2)", bytes, Modifier.weight(1f)) { bytes = it }
            Campo("Multiplicador", mult, Modifier.weight(1f)) { mult = it }
            Campo("Deslocamento", desl, Modifier.weight(1f)) { desl = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotaoPilula("Salvar", Icons.Filled.Refresh, Modifier.weight(1f)) { salvar() }
            BotaoPilula(if (dg.ocupado) "Lendo…" else "Salvar e testar", Icons.Filled.Refresh, Modifier.weight(1f)) { salvar(); if (!dg.ocupado) obd.pedirTeste("cambio") }
        }
        Text("Valor = byte × multiplicador + deslocamento (ex.: temperatura com 1 byte: ×1, −40). Depois de salvar, o app lê a cada poucos segundos e avisa se passar de 110 °C.", color = Cores.TextoSuave, fontSize = 13.sp)
    }
}
