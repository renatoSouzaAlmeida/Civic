package br.com.civic.launcher.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.civic.launcher.EventoCal
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val Navy = Color(0xFF0B1F4B)
private val Cinza = Color(0xFF5B6B8C)
private val FundoChip = Color(0xFFE8F0FC)
private val BRE = Locale("pt", "BR")

/** Nome do aeroporto e cidade dos códigos mais comuns (a API só traz o código); sem o código na lista, mostra só o código. */
private val AEROPORTOS = mapOf(
    "CGH" to ("Congonhas" to "São Paulo"), "GRU" to ("Guarulhos" to "São Paulo"), "VCP" to ("Viracopos" to "Campinas"), "BSB" to ("Presidente Juscelino Kubitschek" to "Brasília"),
    "GIG" to ("Galeão" to "Rio de Janeiro"), "SDU" to ("Santos Dumont" to "Rio de Janeiro"), "CNF" to ("Confins" to "Belo Horizonte"), "PLU" to ("Pampulha" to "Belo Horizonte"),
    "SSA" to ("Deputado Luís Eduardo Magalhães" to "Salvador"), "REC" to ("Guararapes" to "Recife"), "FOR" to ("Pinto Martins" to "Fortaleza"), "POA" to ("Salgado Filho" to "Porto Alegre"),
    "CWB" to ("Afonso Pena" to "Curitiba"), "FLN" to ("Hercílio Luz" to "Florianópolis"), "NAT" to ("Aluízio Alves" to "Natal"), "MCZ" to ("Zumbi dos Palmares" to "Maceió"),
    "BEL" to ("Val-de-Cans" to "Belém"), "MAO" to ("Eduardo Gomes" to "Manaus"), "GYN" to ("Santa Genoveva" to "Goiânia"), "VIX" to ("Eurico Sales" to "Vitória"),
    "CGB" to ("Marechal Rondon" to "Cuiabá"), "SLZ" to ("Marechal Cunha Machado" to "São Luís"), "AJU" to ("Santa Maria" to "Aracaju"), "JPA" to ("Castro Pinto" to "João Pessoa"),
    "CGR" to ("Campo Grande" to "Campo Grande"), "IGU" to ("Cataratas" to "Foz do Iguaçu"), "NVT" to ("Ministro Victor Konder" to "Navegantes"), "UDI" to ("Ten. Cel. Aviador César Bombonato" to "Uberlândia"),
    "RAO" to ("Leite Lopes" to "Ribeirão Preto"), "SJK" to ("Professor Urbano Ernesto Stumpf" to "São José dos Campos"), "THE" to ("Senador Petrônio Portella" to "Teresina"), "PMW" to ("Brigadeiro Lysias Rodrigues" to "Palmas"),
)

/**
 * Cartão de embarque do voo (no estilo do Ponte Aérea), só com o que a API do Launcher entrega: companhia, data e reserva, origem e destino com os horários,
 * situação, trecho e objetivo, voo nacional ou internacional, passageiro, escala, reserva, assento, vaga de garagem, bilhete e observações.
 * Não mostra valores, pontos, custos nem código da compra (a API não os envia) e não tem Editar nem adicionar ao calendário.
 */
@Composable
fun CartaoEmbarque(e: EventoCal, aoFechar: () -> Unit) {
    val ctx = LocalContext.current
    val cor = corHex(e.cor)
    val origem = e.texto("origem") ?: e.titulo.substringBefore("→").trim().ifEmpty { "—" }
    val destino = e.texto("destino") ?: e.titulo.substringAfter("→", "").trim().ifEmpty { "—" }
    val companhia = e.texto("companhia") ?: "Voo"
    val numero = e.texto("numero_voo")
    val reserva = e.texto("localizador")
    val dia = runCatching { LocalDate.parse(e.data) }.getOrNull()
    val dataTexto = dia?.let { d -> d.dayOfWeek.getDisplayName(TextStyle.FULL, BRE).substringBefore("-").replaceFirstChar { it.uppercase() } + ", %02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year) } ?: e.data
    val altMax = LocalConfiguration.current.screenHeightDp.dp * 0.94f

    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth(0.94f).heightIn(max = altMax).clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(cor, cor.copy(alpha = 0.55f), Color(0xFFDCE8FA)))).verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            // Cabeçalho: companhia, data e reserva, e fechar.
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Flight, null, tint = Color.White, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text(companhia, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)).clickable(onClick = aoFechar), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Fechar", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Text(dataTexto + (reserva?.let { " · Reserva $it" } ?: ""), color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Origem → destino, com número do voo e horários.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Aeroporto(origem, Alignment.Start, Modifier.weight(1f))
                    Column(Modifier.weight(1.1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(numero ?: "", color = Cinza, fontSize = 12.sp)
                        Box(Modifier.fillMaxWidth().height(2.dp).background(cor))
                        Text(listOfNotNull(e.horaInicio, e.horaFim).joinToString("→"), color = cor, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                    Aeroporto(destino, Alignment.End, Modifier.weight(1f))
                }
                // Selos.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    e.texto("situacao")?.let { Selo(it.replaceFirstChar { c -> c.uppercase() }, FundoChip, cor) }
                    val trecho = listOfNotNull(e.texto("tipo_trecho")?.replaceFirstChar { it.uppercase() }, e.texto("objetivo")).joinToString(" · ")
                    if (trecho.isNotEmpty()) Selo(trecho, FundoChip, Navy)
                    if (e.detalhes.containsKey("voo_nacional")) Selo(if (e.sim("voo_nacional")) "Nacional" else "Internacional", if (e.sim("voo_nacional")) Color(0xFFDDF6E8) else Color(0xFFFFF0D6), if (e.sim("voo_nacional")) Color(0xFF127A45) else Color(0xFF9A5B00))
                    if (e.sim("remarcar")) Selo("Remarcar", Color(0xFFFFE4E4), Color(0xFFB42318))
                }
                // CIA, VOO, TRECHO, OBJETIVO.
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF1F5FC)).padding(vertical = 10.dp)) {
                    Dado("CIA", companhia, Modifier.weight(1f))
                    Dado("VOO", numero ?: "—", Modifier.weight(1f))
                    Dado("TRECHO", e.texto("tipo_trecho")?.replaceFirstChar { it.uppercase() } ?: "—", Modifier.weight(1f))
                    Dado("OBJETIVO", e.texto("objetivo") ?: "—", Modifier.weight(1f))
                }
                e.passageiro?.let { Text("Passageiro: $it", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                // Escala, se houver.
                e.texto("escala")?.let { esc ->
                    Text("Escala: $esc" + listOfNotNull(e.texto("escala_chegada")?.let { "chega $it" }, e.texto("escala_partida")?.let { "parte $it" }).joinToString(" · ").let { if (it.isEmpty()) "" else " · $it" },
                        color = Navy, fontSize = 14.sp)
                }
                // Reserva e assento (e vaga de garagem e bilhete, se houver).
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Caixa("RESERVA", reserva ?: "—", Modifier.weight(1f), copiar = reserva?.let { r -> { copiar(ctx, r) } })
                    Caixa("ASSENTO", e.texto("assento") ?: "—", Modifier.weight(1f))
                }
                if (e.texto("vaga_garagem") != null || e.texto("tipo_bilhete") != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Caixa("VAGA DE GARAGEM", e.texto("vaga_garagem") ?: "—", Modifier.weight(1f))
                    Caixa("BILHETE", e.texto("tipo_bilhete") ?: "—", Modifier.weight(1f))
                }
                e.texto("observacoes")?.let { Text("Observações: $it", color = Cinza, fontSize = 13.sp) }
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0xFFC6D3EA), RoundedCornerShape(14.dp)).clickable(onClick = aoFechar).padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Fechar", color = Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private fun copiar(ctx: Context, texto: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Reserva", texto))
    Toast.makeText(ctx, "Reserva copiada.", Toast.LENGTH_SHORT).show()
}

@Composable
private fun Aeroporto(codigo: String, alinhamento: Alignment.Horizontal, modifier: Modifier) {
    val info = AEROPORTOS[codigo.uppercase()]
    Column(modifier, horizontalAlignment = alinhamento) {
        Text(codigo, color = Navy, fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        info?.let {
            Text(it.second, color = Cinza, fontSize = 12.sp, maxLines = 1)
            Text(it.first, color = Cinza, fontSize = 11.sp, maxLines = 1, textAlign = if (alinhamento == Alignment.End) TextAlign.End else TextAlign.Start)
        }
    }
}

@Composable
private fun Selo(texto: String, fundo: Color, cor: Color) {
    Text(texto, color = cor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(fundo).padding(horizontal = 12.dp, vertical = 5.dp), maxLines = 1)
}

@Composable
private fun Dado(rotulo: String, valor: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(rotulo, color = Cinza, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(valor, color = Navy, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun Caixa(rotulo: String, valor: String, modifier: Modifier, copiar: (() -> Unit)? = null) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, Color(0xFFC6D3EA), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(rotulo, color = Cinza, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(valor, color = Navy, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        if (copiar != null) Icon(Icons.Filled.ContentCopy, "Copiar", tint = Cinza, modifier = Modifier.size(22.dp).clickable(onClick = copiar))
    }
}
