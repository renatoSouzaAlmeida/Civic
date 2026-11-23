package br.com.civic.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.EventoCal
import br.com.civic.launcher.PonteAerea
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

private val BRC = Locale("pt", "BR")

internal fun corHex(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF6B7280))

/**
 * Calendário padrão (grade do mês, semana de domingo a sábado, dia de hoje destacado). O botão "Ponte Aérea" liga os eventos do calendário do
 * Ponte Aérea (voos, viagens, feriados, eventos, presencial e dias com as filhas) por cima da grade; ao desligar, volta ao calendário simples.
 * A API é somente leitura e o token fica só no aparelho (Config. > Calendário).
 */
@Composable
fun TelaCalendario() {
    val ctx = LocalContext.current
    val pa = remember { PonteAerea.obter(ctx) }
    val hoje = remember { LocalDate.now() }
    var mes by remember { mutableStateOf(YearMonth.from(hoje)) }
    // Dia escolhido; nulo = nenhum dia (a lista mostra todos os eventos do mês). Trocar de mês ou de filtro limpa a escolha.
    var selecionado by remember { mutableStateOf<LocalDate?>(hoje) }
    var ativo by remember { mutableStateOf(false) }
    var passageiro by remember { mutableStateOf<String?>(null) }
    var voo by remember { mutableStateOf<EventoCal?>(null) }
    fun irParaMes(m: YearMonth) { mes = m; selecionado = null }
    fun filtrar(p: String?) { passageiro = p; selecionado = null }

    // Ligado, busca ~6 meses a partir do mês mostrado; ao navegar para fora do que já veio, busca de novo.
    LaunchedEffect(ativo, mes) {
        if (ativo && pa.temToken) {
            val ini = mes.atDay(1).minusDays(7); val fim = mes.atEndOfMonth().plusDays(7)
            val p = pa.periodo
            if (p == null || ini.isBefore(p.first) || fim.isAfter(p.second)) { val de = ini.minusDays(14); pa.atualizar(de, de.plusDays(200)) }
        } else if (ativo) pa.atualizar(hoje, hoje)
    }
    // Com o calendário do Ponte Aérea ligado, atualiza a cada 15 min (a API pede de 15 a 30).
    LaunchedEffect(ativo) { while (ativo) { delay(15 * 60_000L); val de = mes.atDay(1).minusDays(21); pa.atualizar(de, de.plusDays(200)) } }

    val eventos = if (ativo) pa.eventos.filter { passageiro == null || it.tipo != "voo" || it.passageiro == passageiro } else emptyList()
    fun doDia(d: LocalDate) = eventos.filter { it.cobre(d) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Cabeçalho: mês, navegação, Hoje e o botão Ponte Aérea.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Seta(Icons.Filled.ChevronLeft, "Mês anterior") { irParaMes(mes.minusMonths(1)) }
            Text(mes.month.getDisplayName(TextStyle.FULL, BRC).replaceFirstChar { it.uppercase() } + " " + mes.year, color = Cores.Texto, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.widthIn(min = 190.dp))
            Seta(Icons.Filled.ChevronRight, "Próximo mês") { irParaMes(mes.plusMonths(1)) }
            BotaoPilula("Hoje", Icons.Filled.Today) { mes = YearMonth.from(hoje); selecionado = hoje }
            Spacer(Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(50)).then(if (ativo) Modifier.background(Cores.Destaque.copy(alpha = 0.25f)) else Modifier)) {
                BotaoPilula(if (ativo) "Ponte Aérea ✓" else "Ponte Aérea", Icons.Filled.Flight) { ativo = !ativo }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val larga = maxWidth >= 760.dp
            if (larga) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Grade(mes, hoje, selecionado, ::doDia, { selecionado = if (selecionado == it) null else it }, Modifier.weight(1.7f).fillMaxHeight())
                Painel(pa, ativo, mes, selecionado, eventos, passageiro, ::filtrar, { voo = it }, Modifier.weight(1f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Grade(mes, hoje, selecionado, ::doDia, { selecionado = if (selecionado == it) null else it }, Modifier.fillMaxWidth().height(380.dp))
                Painel(pa, ativo, mes, selecionado, eventos, passageiro, ::filtrar, { voo = it }, Modifier.fillMaxWidth())
            }
        }
    }
    voo?.let { CartaoEmbarque(it) { voo = null } }
}

@Composable
private fun Seta(icone: androidx.compose.ui.graphics.vector.ImageVector, descricao: String, aoTocar: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Cores.Chip).clickable(onClick = aoTocar), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(icone, descricao, tint = Cores.Texto, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun Grade(mes: YearMonth, hoje: LocalDate, selecionado: LocalDate?, doDia: (LocalDate) -> List<EventoCal>, aoEscolher: (LocalDate) -> Unit, modifier: Modifier) {
    val primeiro = mes.atDay(1)
    val deslocamento = primeiro.dayOfWeek.value % 7 // domingo = 0
    val semanas = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Cores.Chip).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            semanas.forEach { Text(it, color = Cores.TextoSuave, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        repeat(6) { linha ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { col ->
                    val dia = primeiro.plusDays((linha * 7 + col - deslocamento).toLong())
                    val doMes = dia.month == mes.month
                    val eh = dia == hoje; val sel = dia == selecionado
                    val evs = doDia(dia)
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Cores.Destaque.copy(alpha = 0.22f) else Color.Transparent)
                            .then(if (eh) Modifier.border(2.dp, Cores.Destaque, RoundedCornerShape(12.dp)) else Modifier)
                            .clickable { aoEscolher(dia) }.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(dia.dayOfMonth.toString(), color = if (doMes) Cores.Texto else Cores.TextoSuave.copy(alpha = 0.45f), fontSize = 18.sp, fontWeight = if (eh) FontWeight.ExtraBold else FontWeight.Medium)
                        if (evs.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 3.dp)) {
                            evs.take(3).forEach { Box(Modifier.size(8.dp).clip(CircleShape).background(corHex(it.cor))) }
                            if (evs.size > 3) Text("+", color = Cores.TextoSuave, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Painel(
    pa: PonteAerea, ativo: Boolean, mes: YearMonth, dia: LocalDate?, eventos: List<EventoCal>, passageiro: String?,
    aoFiltrar: (String?) -> Unit, aoAbrirVoo: (EventoCal) -> Unit, modifier: Modifier,
) {
    val fmtDia = remember { SimpleDateFormat("EEEE, dd 'de' MMMM", BRC) }
    val hora = remember { SimpleDateFormat("HH:mm", BRC) }
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Cores.Chip).padding(14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (dia != null) fmtDia.format(Date.from(dia.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())).replaceFirstChar { it.uppercase() }
            else mes.month.getDisplayName(TextStyle.FULL, BRC).replaceFirstChar { it.uppercase() } + " " + mes.year,
            color = Cores.Texto, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        )
        if (!ativo) {
            Text("Calendário simples. Toque em \"Ponte Aérea\" para ver aqui os voos, viagens, feriados e eventos do seu calendário do Ponte Aérea.", color = Cores.TextoSuave, fontSize = 14.sp)
            return@Column
        }
        if (!pa.temToken) { Text("Falta o token da API do Ponte Aérea: informe em Config. > Calendário.", color = Cores.Amarelo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); return@Column }
        pa.proximoVoo?.let { v ->
            Text("Próximo voo: ${v.titulo} · " + runCatching { LocalDate.parse(v.data).let { "%02d/%02d".format(it.dayOfMonth, it.monthValue) } }.getOrDefault(v.data) + (v.horaInicio?.let { " às $it" } ?: ""),
                color = Cores.Destaque, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            when {
                pa.carregando -> "Atualizando…"
                pa.atualizadoEm > 0 -> "Atualizado às ${hora.format(Date(pa.atualizadoEm))}"
                else -> "Ainda sem dados"
            } + (pa.situacao?.let { " · $it" } ?: ""),
            color = if (pa.situacao != null) Cores.Amarelo else Cores.TextoSuave, fontSize = 12.sp,
        )
        // Filtro de passageiro (só afeta os voos): lista todos os passageiros, os da API e os que aparecem nos voos.
        val nomes = (pa.passageiros + pa.eventos.mapNotNull { if (it.tipo == "voo") it.passageiro else null }).distinct()
        if (nomes.isNotEmpty()) {
            // Combo: ocupa uma linha só, com todos os passageiros na lista.
            var aberto by remember { mutableStateOf(false) }
            Box {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Cores.Cartao).border(1.dp, Cores.Borda, RoundedCornerShape(12.dp))
                        .clickable { aberto = true }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Passageiro (voos): ", color = Cores.TextoSuave, fontSize = 14.sp)
                    Text(passageiro ?: "Todos", color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.ArrowDropDown, null, tint = Cores.Texto)
                }
                androidx.compose.material3.DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
                    (listOf<String?>(null) + nomes).forEach { n ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text((n ?: "Todos") + if (n == passageiro) "  ✓" else "", fontSize = 18.sp) },
                            onClick = { aberto = false; aoFiltrar(n) },
                        )
                    }
                }
            }
        }
        // Sem dia escolhido: todos os eventos do mês; com dia: só os dele.
        val lista = (if (dia != null) eventos.filter { it.cobre(dia) } else eventos.filter { e ->
            val ini = runCatching { LocalDate.parse(e.data) }.getOrNull(); val fim = e.dataFim?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: ini
            ini != null && fim != null && !ini.isAfter(mes.atEndOfMonth()) && !fim.isBefore(mes.atDay(1))
        }).sortedWith(compareBy({ it.data }, { it.horaInicio ?: "" }))
        if (lista.isEmpty()) Text(if (dia != null) "Nada neste dia." else "Nada neste mês" + (passageiro?.let { " para $it" } ?: "") + ".", color = Cores.TextoSuave, fontSize = 15.sp)
        var ultimaData = ""
        lista.forEach { e ->
            if (dia == null && e.data != ultimaData) {
                ultimaData = e.data
                Text(runCatching { LocalDate.parse(e.data).let { d -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, BRC).replaceFirstChar { c -> c.uppercase() } + ", %02d/%02d".format(d.dayOfMonth, d.monthValue) } }.getOrDefault(e.data),
                    color = Cores.TextoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cores.Cartao).then(if (e.tipo == "voo") Modifier.clickable { aoAbrirVoo(e) } else Modifier).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.width(5.dp).height(40.dp).clip(RoundedCornerShape(3.dp)).background(corHex(e.cor)))
                Column(Modifier.weight(1f)) {
                    Text(e.titulo, color = Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    e.subtitulo?.let { Text(it, color = Cores.TextoSuave, fontSize = 13.sp) }
                    Text(
                        listOfNotNull(
                            if (e.diaInteiro) "Dia inteiro" else listOfNotNull(e.horaInicio, e.horaFim).joinToString(" – ").ifEmpty { null },
                            when (e.tipo) { "voo" -> "Voo · toque para ver o cartão de embarque"; "viagem" -> "Viagem"; "feriado" -> "Feriado"; "presencial" -> "Presencial"; "familia" -> "Com as filhas"; "fds_filhas" -> "Fim de semana com as filhas"; else -> null },
                        ).joinToString(" · "),
                        color = Cores.TextoSuave, fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
