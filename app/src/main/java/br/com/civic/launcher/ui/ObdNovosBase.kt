package br.com.civic.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.obd.DadosObd
import br.com.civic.launcher.obd.EstadoObd
import br.com.civic.launcher.obd.ObdMonitor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Peças comuns dos 4 layouts novos do OBD (Esportivo, Cockpit digital, Técnico e Direção simplificada),
// fiéis às propostas: fundo azul-noite com auras azuis no tema escuro, branco-azulado no claro, verde no modo de condução.
// ---------------------------------------------------------------------------------------------

/** Cores dos layouts novos: as mesmas proporções da proposta, em versão escura e clara. */
@Immutable
internal class TemaObd(
    val escuro: Boolean,
    val fundo: Brush,
    val texto: Color,
    val suave: Color,
    val vidro: Color,
    val bordaVidro: Color,
    val azul: Color,
    val neon: Color,
    val aura: Color,
    val verde: Color,
    val vermelho: Color,
    val laranja: Color,
)

@Composable
internal fun temaObd(): TemaObd {
    val escuro = LocalPaleta.current.escuro
    return if (escuro) TemaObd(
        true, Brush.verticalGradient(listOf(Color(0xFF040A18), Color(0xFF071330), Color(0xFF040A18))),
        Color(0xFFF3F7FF), Color(0xFF93A4C3), Color(0xFF0C1A36).copy(alpha = 0.78f), Color(0xFF2A4C8A).copy(alpha = 0.55f),
        Color(0xFF2F8CFF), Color(0xFF3DB6FF), Color(0xFF1E7BFF), Color(0xFF22C55E), Color(0xFFEF4444), Color(0xFFF59E0B),
    ) else TemaObd(
        false, Brush.verticalGradient(listOf(Color(0xFFF7FAFF), Color(0xFFEAF2FE), Color(0xFFF7FAFF))),
        Color(0xFF0B1B3A), Color(0xFF5B6B8C), Color.White.copy(alpha = 0.92f), Color(0xFFC9DAF5),
        Color(0xFF1F6FEB), Color(0xFF2F8CFF), Color(0xFF5AA2FF), Color(0xFF16A34A), Color(0xFFDC2626), Color(0xFFF59E0B),
    )
}

/**
 * Prancheta de projeto: o layout é montado sempre em [largura] dp (como na proposta) e escalado por igual para caber no espaço,
 * em tela cheia ou dividida, sem cortar nada. A altura acompanha o espaço disponível entre [alturaMin] e [alturaMax].
 */
@Composable
internal fun PranchetaObd(largura: Dp = 1280.dp, alturaMin: Dp = 520.dp, alturaMax: Dp = 720.dp, modifier: Modifier = Modifier, conteudo: @Composable () -> Unit) {
    Layout(conteudo, modifier) { medidos, c ->
        val wP = largura.roundToPx()
        val disponivelW = if (c.hasBoundedWidth) c.maxWidth else wP
        val disponivelH = if (c.hasBoundedHeight) c.maxHeight else alturaMax.roundToPx()
        var e = disponivelW.toFloat() / wP
        val hP = (disponivelH / e).roundToInt().coerceIn(alturaMin.roundToPx(), alturaMax.roundToPx())
        if (hP * e > disponivelH) e = disponivelH.toFloat() / hP
        val p = medidos.first().measure(Constraints.fixed(wP, hP))
        val w = (wP * e).roundToInt(); val h = (hP * e).roundToInt()
        layout(disponivelW, disponivelH) {
            p.placeWithLayer((disponivelW - w) / 2, (disponivelH - h) / 2) { scaleX = e; scaleY = e; transformOrigin = TransformOrigin(0f, 0f) }
        }
    }
}

internal val BRO = Locale("pt", "BR")
internal fun fmt1(v: Float?) = v?.let { "%.1f".format(BRO, it) } ?: "—"
internal fun fmt0(v: Number?) = v?.let { "%,d".format(BRO, it.toLong()) } ?: "—"

/** Cabeçalho da proposta: hora e data, "Civic Renatin · OBD" ao centro, situação do OBD e ícones à direita. */
@Composable
internal fun CabecalhoObdNovo(t: TemaObd, obd: ObdMonitor, aoConfig: () -> Unit, modifier: Modifier = Modifier, centro: @Composable () -> Unit = {
    Text("Civic Renatin · OBD", color = t.texto, fontSize = 17.sp, fontWeight = FontWeight.Bold)
}) {
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { agora = System.currentTimeMillis(); kotlinx.coroutines.delay(15_000) } }
    val hora = remember { SimpleDateFormat("HH:mm", BRO) }
    val data = remember { SimpleDateFormat("EEE, dd/MMM", BRO) }
    val (texto, cor) = when (obd.estado) {
        EstadoObd.Conectado -> "OBD Conectado" to t.verde
        EstadoObd.Demonstracao -> "OBD Demonstração" to t.neon
        EstadoObd.Conectando, EstadoObd.Procurando -> "Conectando…" to t.laranja
        EstadoObd.Desconectado -> "OBD Desconectado" to t.suave
    }
    Box(modifier.fillMaxWidth().height(34.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(hora.format(Date(agora)), color = t.texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(data.format(Date(agora)).replaceFirstChar { it.uppercase() }.replace(".", ""), color = t.suave, fontSize = 14.sp)
        }
        Box(Modifier.align(Alignment.Center)) { centro() }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(cor))
            Text(texto, color = t.texto, fontSize = 13.sp)
            Icon(Icons.Filled.Bluetooth, null, tint = t.neon, modifier = Modifier.size(18.dp))
            Icon(Icons.Filled.Wifi, null, tint = t.texto, modifier = Modifier.size(18.dp))
            Icon(Icons.Filled.VolumeUp, null, tint = t.texto, modifier = Modifier.size(18.dp))
            Icon(Icons.Filled.Settings, "Adaptador", tint = t.texto, modifier = Modifier.size(20.dp).clickable(onClick = aoConfig))
        }
    }
}

internal class AbaObd(val rotulo: String, val icone: ImageVector, val acao: () -> Unit)

/** Barra de abas de baixo da proposta (a primeira, ativa, em pílula azul). */
@Composable
internal fun AbasObdNovo(t: TemaObd, abas: List<AbaObd>, ativa: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(t.vidro).border(1.dp, t.bordaVidro, RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        abas.forEachIndexed { i, a ->
            val sel = i == ativa
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                    .then(if (sel) Modifier.background(Brush.verticalGradient(listOf(Color(0xFF3B8DFF), Color(0xFF1764E8)))) else Modifier)
                    .clickable(onClick = a.acao),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(a.icone, null, tint = if (sel) Color.White else t.texto, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(a.rotulo, color = if (sel) Color.White else t.texto, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

/** Linha de informação com ícone em caixinha, rótulo pequeno e valor (colunas laterais dos layouts 1 e 4). */
@Composable
internal fun InfoObd(t: TemaObd, icone: ImageVector, rotulo: String, valor: String, unidade: String, modifier: Modifier = Modifier, grande: Boolean = false, rotuloEmbaixo: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(if (grande) 48.dp else 40.dp).clip(RoundedCornerShape(10.dp)).background(t.vidro).border(1.dp, t.bordaVidro, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icone, null, tint = t.texto, modifier = Modifier.size(if (grande) 28.dp else 24.dp)) }
        Column {
            if (!rotuloEmbaixo) Text(rotulo, color = t.suave, fontSize = 12.sp, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(valor, color = t.texto, fontSize = if (grande) 28.sp else 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (unidade.isNotEmpty()) Text(" $unidade", color = t.texto, fontSize = if (grande) 15.sp else 13.sp, modifier = Modifier.padding(bottom = if (grande) 4.dp else 2.dp), maxLines = 1)
            }
            if (rotuloEmbaixo) Text(rotulo, color = t.suave, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** Selo "ECO Assist": folha verde num círculo com brilho e a barra em segmentos verdes (mais segmentos = condução mais econômica). */
@Composable
internal fun EcoAssist(t: TemaObd, modifier: Modifier = Modifier, compacto: Boolean = false) {
    val eco = br.com.civic.launcher.obd.EcoConducao.obter()
    val ativo = eco.nivel != null
    val cheios = if (!ativo) 0 else ((1f - eco.nota) * 5f).roundToInt().coerceIn(1, 5)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(if (compacto) 32.dp else 42.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                if (ativo) for (k in 3 downTo 1) drawCircle(t.verde.copy(alpha = 0.10f * k), r * (0.8f + 0.12f * k))
                drawCircle(if (ativo) t.verde else t.suave.copy(alpha = 0.4f), r * 0.78f)
            }
            Icon(Icons.Filled.Eco, null, tint = Color.White, modifier = Modifier.size(if (compacto) 18.dp else 24.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ECO Assist", color = t.texto, fontSize = if (compacto) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(5) { i -> Box(Modifier.size(width = if (compacto) 16.dp else 22.dp, height = 7.dp).clip(RoundedCornerShape(2.dp)).background(if (i < cheios) t.verde else t.suave.copy(alpha = 0.35f))) }
            }
        }
    }
}

/** Farol (verde quando aceso) e setas (verdes piscando quando ligadas), como no rodapé dos layouts 1 e 4. */
@Composable
internal fun LuzesObd(d: DadosObd, modifier: Modifier = Modifier, tamanho: Dp = 32.dp, farolPrimeiro: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val farol: @Composable () -> Unit = {
            LuzEspia(androidx.compose.ui.res.painterResource(br.com.civic.launcher.R.drawable.ic_farol), d.farol, Color(0xFF22C55E), tamanho = tamanho)
        }
        val setas: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { SetaObd(d, esquerda = true, tamanho = tamanho); SetaObd(d, esquerda = false, tamanho = tamanho) }
        }
        if (farolPrimeiro) { farol(); setas() } else { setas(); farol() }
    }
}

/** Desenha um traço com brilho (várias camadas de transparência), para as auras azuis da proposta. */
internal fun DrawScope.arcoComBrilho(cor: Color, inicio: Float, varredura: Float, topLeft: Offset, tam: Size, largura: Float, camadas: Int = 4, brilho: Float = 1f) {
    for (k in camadas downTo 1) {
        drawArc(cor.copy(alpha = 0.07f * brilho * (camadas - k + 1)), inicio, varredura, false, Offset(topLeft.x, topLeft.y), tam, style = Stroke(largura * (1f + k * 1.1f), cap = StrokeCap.Round))
    }
    drawArc(cor, inicio, varredura, false, topLeft, tam, style = Stroke(largura, cap = StrokeCap.Round))
}

/**
 * Mostrador neon das propostas: arco com degradê azul→vermelho no fim, anel externo com aura azul, marcas e números.
 * [fracao] 0..1; [rotulos] em volta; [vermelhoDesde] fração onde começa o vermelho; [inicio]/[varredura] em graus.
 */
@Composable
internal fun MostradorNeon(
    t: TemaObd, fracao: Float, rotulos: List<String>, modifier: Modifier = Modifier, vermelhoDesde: Float = 0.75f,
    inicio: Float = 135f, varredura: Float = 270f, espessura: Float = 0.07f, tamanhoRotulo: TextUnit = 15.sp, aneis: Boolean = true,
    centro: @Composable BoxScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val lado = minOf(maxWidth, maxHeight)
        Box(Modifier.size(lado), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension / 2
                // Fundo do mostrador (vidro escuro ou branco) e aura azul em volta.
                drawCircle(Brush.radialGradient(listOf(if (t.escuro) Color(0xFF0A1630) else Color.White, if (t.escuro) Color(0xFF050C1E) else Color(0xFFEFF5FF)), c, r), r * 0.94f, c)
                if (aneis) {
                    for (k in 5 downTo 1) drawCircle(t.aura.copy(alpha = 0.05f * k), r * (0.94f + 0.012f * k), c, style = Stroke(r * 0.02f * k))
                    drawCircle(t.neon.copy(alpha = 0.85f), r * 0.95f, c, style = Stroke(r * 0.012f))
                }
                val esp = r * espessura; val rArco = r * 0.83f
                val tl = Offset(c.x - rArco, c.y - rArco); val tam = Size(rArco * 2, rArco * 2)
                drawArc(t.suave.copy(alpha = if (t.escuro) 0.18f else 0.22f), inicio, varredura, false, tl, tam, style = Stroke(esp, cap = StrokeCap.Butt))
                val f = fracao.coerceIn(0f, 1f)
                val azulAte = min(f, vermelhoDesde)
                if (azulAte > 0f) arcoComBrilho(t.neon, inicio, varredura * azulAte, tl, tam, esp, 3)
                // Zona vermelha: sempre marcada em vermelho translúcido; acesa até o valor.
                drawArc(t.vermelho.copy(alpha = 0.35f), inicio + varredura * vermelhoDesde, varredura * (1f - vermelhoDesde), false, tl, tam, style = Stroke(esp, cap = StrokeCap.Butt))
                if (f > vermelhoDesde) arcoComBrilho(t.vermelho, inicio + varredura * vermelhoDesde, varredura * (f - vermelhoDesde), tl, tam, esp, 3)
                // Marcas.
                val passos = (rotulos.size - 1).coerceAtLeast(1) * 5
                val rExt = rArco - esp * 0.8f
                for (i in 0..passos) {
                    val p = i / passos.toFloat(); val a = Math.toRadians((inicio + varredura * p).toDouble())
                    val grande = i % 5 == 0
                    val rInt = rExt - r * (if (grande) 0.07f else 0.035f)
                    drawLine((if (p >= vermelhoDesde) t.vermelho else t.texto).copy(alpha = if (grande) 0.9f else 0.45f),
                        Offset(c.x + rInt * cos(a).toFloat(), c.y + rInt * sin(a).toFloat()), Offset(c.x + rExt * cos(a).toFloat(), c.y + rExt * sin(a).toFloat()),
                        r * (if (grande) 0.012f else 0.006f), StrokeCap.Round)
                }
            }
            val rRot = lado / 2 * 0.58f; val caixa = lado * 0.14f
            if (rotulos.size >= 2) rotulos.forEachIndexed { k, s ->
                val p = k / (rotulos.size - 1).toFloat(); val a = Math.toRadians((inicio + varredura * p).toDouble())
                Box(Modifier.align(Alignment.TopStart).offset(x = lado / 2 + rRot * cos(a).toFloat() - caixa / 2, y = lado / 2 + rRot * sin(a).toFloat() - caixa / 2).size(caixa), contentAlignment = Alignment.Center) {
                    Text(s, color = if (p >= vermelhoDesde) t.vermelho else t.texto, fontSize = tamanhoRotulo, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            centro()
        }
    }
}

/** Estrada em perspectiva com faixas e luzes azuis (fundo dos layouts 1 e 4). */
internal fun DrawScope.estradaNeon(t: TemaObd, horizonte: Float, cidade: Boolean) {
    val w = size.width; val h = size.height; val cx = w / 2
    // Brilho no horizonte.
    drawRect(Brush.radialGradient(listOf(t.aura.copy(alpha = if (t.escuro) 0.55f else 0.35f), Color.Transparent), Offset(cx, horizonte), w * 0.45f), Offset(0f, horizonte - h * 0.5f), Size(w, h))
    if (cidade) skyline(t, horizonte)
    // Asfalto.
    val asfalto = Path().apply { moveTo(cx - w * 0.04f, horizonte); lineTo(cx + w * 0.04f, horizonte); lineTo(w * 0.98f, h); lineTo(w * 0.02f, h); close() }
    drawPath(asfalto, Brush.verticalGradient(listOf(if (t.escuro) Color(0xFF0B1A38) else Color(0xFFDCE8FA), if (t.escuro) Color(0xFF07122A) else Color(0xFFC8DBF7)), horizonte, h))
    // Bordas e faixas com brilho.
    fun linhaBrilho(x0: Float, x1: Float, cor: Color, larg: Float) {
        for (k in 3 downTo 1) drawLine(cor.copy(alpha = 0.10f * k), Offset(x0, horizonte), Offset(x1, h), larg * (1 + k * 1.6f))
        drawLine(cor, Offset(x0, horizonte), Offset(x1, h), larg)
    }
    linhaBrilho(cx - w * 0.035f, w * 0.06f, t.neon, 3f)
    linhaBrilho(cx + w * 0.035f, w * 0.94f, t.neon, 3f)
    linhaBrilho(cx - w * 0.012f, w * 0.33f, t.neon.copy(alpha = 0.7f), 2f)
    linhaBrilho(cx + w * 0.012f, w * 0.67f, t.neon.copy(alpha = 0.7f), 2f)
    // Tracejado central.
    var y = horizonte + 6f; var passo = 6f
    while (y < h) {
        val p = (y - horizonte) / (h - horizonte)
        drawLine(Color.White.copy(alpha = if (t.escuro) 0.55f else 0.9f), Offset(cx, y), Offset(cx, minOf(h, y + passo)), 2f + 5f * p)
        y += passo * 2.2f; passo *= 1.35f
    }
}

/** Silhueta de cidade no horizonte (layout 4). */
private fun DrawScope.skyline(t: TemaObd, base: Float) {
    val w = size.width
    val alturas = floatArrayOf(0.30f, 0.45f, 0.38f, 0.62f, 0.50f, 0.80f, 0.55f, 0.70f, 0.42f, 0.95f, 0.60f, 0.48f, 0.75f, 0.52f, 0.66f, 0.40f, 0.86f, 0.58f, 0.44f, 0.72f, 0.36f, 0.54f, 0.31f)
    val n = alturas.size; val larg = w / n
    val corPredio = if (t.escuro) Color(0xFF0E2550) else Color(0xFFB9D0F2)
    val corJanela = if (t.escuro) Color(0xFF5FB2FF) else Color(0xFF8DB6EC)
    for (i in 0 until n) {
        val hh = base * 0.55f * alturas[i]
        val x = i * larg + larg * 0.08f
        drawRect(corPredio.copy(alpha = if (t.escuro) 0.9f else 0.75f), Offset(x, base - hh), Size(larg * 0.84f, hh))
        var yy = base - hh + 8f
        while (yy < base - 8f) {
            var xx = x + 5f
            while (xx < x + larg * 0.84f - 6f) { drawRect(corJanela.copy(alpha = 0.35f), Offset(xx, yy), Size(3f, 4f)); xx += 9f }
            yy += 11f
        }
    }
}
