package br.com.civic.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.R
import br.com.civic.launcher.obd.CodigosFalha
import br.com.civic.launcher.obd.EstadoObd
import br.com.civic.launcher.obd.HistoricoFalhas
import br.com.civic.launcher.obd.ObdMonitor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Diagnóstico do OBD: abas Falhas (DTC), Bateria (saúde na partida) e Mistura (correção de combustível). */
@Composable
fun TelaDiagnostico(obd: ObdMonitor, aoVoltar: () -> Unit) {
    var aba by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotaoPilula("Voltar", Icons.Filled.ArrowBack) { aoVoltar() }
            BarraAbas(
                listOf(
                    Aba("Falhas (DTC)", iconeRes = R.drawable.ic_motor),
                    Aba("Bateria", Icons.Filled.BatteryChargingFull),
                    Aba("Mistura", Icons.Filled.Tune),
                ),
                aba, Modifier.weight(1f),
            ) { aba = it }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (aba) {
                0 -> TelaFalhas(obd)
                1 -> TelaBateria(obd)
                else -> TelaMistura(obd)
            }
        }
    }
}

/**
 * Tela de falhas (DTC): luz de injeção, códigos confirmados e pendentes com descrição em português
 * e gravidade, histórico de quando cada código apareceu, e botões para ler de novo e apagar.
 */
@Composable
fun TelaFalhas(obd: ObdMonitor, aoVoltar: (() -> Unit)? = null) {
    val d = obd.dados
    val ctx = LocalContext.current
    val historico = remember { HistoricoFalhas.obter(ctx.applicationContext) }
    var confirmarApagar by remember { mutableStateOf(false) }
    val conectado = obd.estado == EstadoObd.Conectado || obd.estado == EstadoObd.Demonstracao
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            aoVoltar?.let { voltar -> BotaoPilula("Voltar", Icons.Filled.ArrowBack) { voltar() } }
            Text("Falhas do motor (DTC)", color = Cores.Texto, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            BotaoPilula(if (obd.ocupadoFalhas) "Lendo…" else "Ler agora", Icons.Filled.Refresh) { if (!obd.ocupadoFalhas) obd.lerFalhasAgora() }
            BotaoPilula("Apagar códigos", Icons.Filled.DeleteSweep) { if (!obd.ocupadoFalhas) confirmarApagar = true }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Situação geral
            Cartao(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val cor = when {
                        !conectado -> Cores.TextoSuave
                        d.luzMotor -> Color(0xFFFFB020)
                        else -> Cores.Ok
                    }
                    Icon(painterResource(R.drawable.ic_motor), null, tint = cor, modifier = Modifier.size(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                !conectado -> "Adaptador OBD não conectado"
                                d.luzMotor -> "Luz de injeção ACESA"
                                d.codigos.isEmpty() && d.codigosPendentes.isEmpty() -> "Nenhuma falha no momento"
                                else -> "Luz de injeção apagada"
                            },
                            color = Cores.Texto, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            if (!conectado) "Conecte o adaptador (ou use a demonstração) para ler os códigos do carro."
                            else "${d.codigos.size} confirmado(s) · ${d.codigosPendentes.size} pendente(s)" +
                                if (obd.estado == EstadoObd.Demonstracao) " · demonstração" else "",
                            color = Cores.TextoSuave, fontSize = 14.sp,
                        )
                        obd.situacaoFalhas?.let { Text(it, color = Cores.Destaque, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            ListaCodigos("Códigos confirmados", "O carro confirmou o defeito (geralmente acende a luz de injeção).", d.codigos)
            ListaCodigos("Códigos pendentes", "Detectados uma vez; viram confirmados se o defeito se repetir.", d.codigosPendentes)
            Secao("Histórico") {
                if (historico.itens.isEmpty()) Text("Nenhum código registrado até agora.", color = Cores.TextoSuave, fontSize = 15.sp)
                historico.itens.forEach { h ->
                    val info = CodigosFalha.info(h.codigo)
                    val ativo = h.codigo in d.codigos || h.codigo in d.codigosPendentes
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cores.Chip).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(h.codigo, color = Cores.Texto, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Column(Modifier.weight(1f)) {
                            Text(info.descricao, color = Cores.Texto, fontSize = 14.sp)
                            Text(
                                "Primeira vez: ${fmt.format(Date(h.primeiraVez))} · última: ${fmt.format(Date(h.ultimaVez))}",
                                color = Cores.TextoSuave, fontSize = 12.sp,
                            )
                        }
                        Text(if (ativo) "ativo" else "resolvido", color = if (ativo) Color(0xFFFFB020) else Cores.Ok, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (historico.itens.isNotEmpty()) TextButton(onClick = { historico.limpar() }) { Text("Limpar histórico", fontSize = 15.sp) }
            }
            Text(
                "As descrições são uma referência. Para o diagnóstico completo, leve o código a uma oficina de confiança.",
                color = Cores.TextoSuave, fontSize = 13.sp,
            )
        }
    }

    if (confirmarApagar) AlertDialog(
        onDismissRequest = { confirmarApagar = false },
        title = { Text("Apagar os códigos de falha?") },
        text = {
            Text(
                "• Apagar NÃO conserta o defeito: se ele continuar, o código e a luz voltam.\n" +
                    "• Também zera os monitores de emissões do carro — evite fazer logo antes de uma vistoria.\n" +
                    "• Faça com a ignição ligada e o motor desligado.",
                fontSize = 16.sp,
            )
        },
        confirmButton = { TextButton(onClick = { confirmarApagar = false; obd.apagarFalhas() }) { Text("Apagar", fontSize = 18.sp) } },
        dismissButton = { TextButton(onClick = { confirmarApagar = false }) { Text("Cancelar", fontSize = 18.sp) } },
    )
}

@Composable
private fun ListaCodigos(titulo: String, explicacao: String, codigos: List<String>) {
    Secao("$titulo (${codigos.size})") {
        Text(explicacao, color = Cores.TextoSuave, fontSize = 13.sp)
        if (codigos.isEmpty()) Text("Nenhum.", color = Cores.Texto, fontSize = 15.sp)
        codigos.forEach { c ->
            val info = CodigosFalha.info(c)
            val cor = when (info.gravidade) {
                CodigosFalha.Gravidade.Grave -> Cores.Vermelho
                CodigosFalha.Gravidade.Atencao -> Color(0xFFFFB020)
                CodigosFalha.Gravidade.Leve -> Cores.Destaque
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cores.Chip).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(width = 6.dp, height = 44.dp).clip(RoundedCornerShape(3.dp)).background(cor))
                Text(c, color = Cores.Texto, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Column(Modifier.weight(1f)) {
                    Text(info.descricao, color = Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(info.gravidade.rotulo, color = cor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
