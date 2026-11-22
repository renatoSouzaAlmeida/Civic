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
import br.com.civic.launcher.abrirNavegacaoComPainel
import br.com.civic.launcher.abrirSpotify
import br.com.civic.launcher.navegarFavorito
import androidx.compose.ui.graphics.asImageBitmap
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

/** Meia tela (tela dividida): duas colunas; o mapa já está do outro lado, então não há cartão de navegação. */
@Composable
fun HomeCaixasCompacta(monitor: MediaMonitor, sensores: Sensores, obd: ObdMonitor) {
    Row(Modifier.fillMaxSize().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(EspacoCaixas)) {
        CaixaCarro(obd, Modifier.weight(1f).fillMaxHeight())
        Column(Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(EspacoCaixas)) {
            CaixaResumoObd(obd, Modifier.weight(1.5f).fillMaxWidth())
            CartaoSpotify(monitor, Modifier.weight(1f).fillMaxWidth().sombraCaixa())
            CartaoClima(sensores.clima, Modifier.weight(0.9f).fillMaxWidth().sombraCaixa())
        }
    }
}

/** Foto do carro, nome, placa e situação, numa caixa só. */
@Composable
private fun CaixaCarro(obd: ObdMonitor, modifier: Modifier) {
    Box(modifier.cartaoSuave()) {
        FotoCarro(Modifier.fillMaxSize(), 0.62f)
        Column(Modifier.fillMaxSize().padding(20.dp).escalarSeMenorQue(280.dp), verticalArrangement = Arrangement.SpaceBetween) {
            TituloCarro()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Setas e farol, como no painel do carro.
                Row(Modifier.fillMaxWidth().cartaoSuave(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    SetaObd(obd.dados, esquerda = true, tamanho = 30.dp)
                    FarolObd(obd.dados, 24.dp)
                    SetaObd(obd.dados, esquerda = false, tamanho = 30.dp)
                }
                ChipsCarro(obd.dados)
            }
        }
    }
}

/** Resumo do OBD: giro com barra em degraus e uma grade de leituras. */
@Composable
private fun CaixaResumoObd(obd: ObdMonitor, modifier: Modifier) {
    val ctx = LocalContext.current
    val d = obd.dados
    Column(modifier.cartaoSuave().padding(16.dp).escalarSeMenorQue(250.dp), verticalArrangement = Arrangement.SpaceBetween) {
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
// Proposta "Painel" (segue a imagem escura e o tema do app):
//   esquerda: foto do carro com placa + cartão único de leituras com divisórias
//   centro: velocidade em cima, meia-lua de RPM com o valor no meio, barra de RPM embaixo
//   direita: mapa compacto, Spotify em linha e clima
// ---------------------------------------------------------------------------------------------

@Composable
fun HomePainel(monitor: MediaMonitor, sensores: Sensores, obd: ObdMonitor) {
    val d = obd.dados
    val vel = d.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
    Row(Modifier.fillMaxSize().padding(2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth().cartaoSuave()) {
                FotoCarro(Modifier.fillMaxSize(), 1f)
                Column(Modifier.fillMaxSize().padding(16.dp)) { TituloCarro() }
            }
            GradeLeituras(d, Modifier.weight(1.05f).fillMaxWidth())
        }
        ModuloVelocidade(d, vel, Modifier.weight(1.15f).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MapaCompacto(sensores, Modifier.weight(1.1f).fillMaxWidth())
            SpotifyLinha(monitor, Modifier.weight(1f).fillMaxWidth())
            ClimaLinha(sensores.clima, Modifier.weight(0.9f).fillMaxWidth())
        }
    }
}

/** Meia tela: velocímetro grande de um lado; carro e leituras do outro; Spotify e clima embaixo. */
@Composable
fun HomePainelCompacta(monitor: MediaMonitor, sensores: Sensores, obd: ObdMonitor) {
    val d = obd.dados
    val vel = d.velocidade?.toFloat() ?: sensores.velocidadeKmh ?: 0f
    Column(Modifier.fillMaxSize().padding(2.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.weight(1.8f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VelocimetroDividido(d, vel, Modifier.weight(1.1f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth().cartaoSuave()) {
                    FotoCarro(Modifier.fillMaxSize(), 1f)
                    Column(Modifier.fillMaxSize().padding(14.dp)) { TituloCarro() }
                }
                GradeLeituras(d, Modifier.weight(1.2f).fillMaxWidth())
            }
        }
        Row(Modifier.weight(0.8f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpotifyLinha(monitor, Modifier.weight(1f).fillMaxHeight())
            ClimaLinha(sensores.clima, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** Cartão único com as leituras em grade, separadas por linhas finas (3 + 3 + 2). */
@Composable
private fun GradeLeituras(d: DadosObd, modifier: Modifier) {
    val ctx = LocalContext.current
    val f = d.falhas
    val linhas: List<List<@Composable (Modifier) -> Unit>> = listOf(
        listOf(
            { m -> Celula(Icons.Outlined.Speed, d.acelerador?.toString() ?: "—", "%", "acelerador", m) },
            { m -> Celula(Icons.Outlined.Thermostat, d.tempMotor?.toString() ?: "—", "", "°C motor", m, if ((d.tempMotor ?: 0) >= 105) Cores.Vermelho else Cores.Destaque) },
            { m -> Celula(Icons.Outlined.AcUnit, d.tempAdmissao?.toString() ?: "—", "", "°C ar", m) },
        ),
        listOf(
            { m -> Celula(Icons.Outlined.BatteryChargingFull, d.tensao?.let { "%.1f".format(BR, it) } ?: "—", "V", "bateria", m) },
            { m -> Celula(Icons.Outlined.LocalGasStation, d.nivelCombustivel?.toString() ?: "—", "%", "combustível", m) },
            { m -> Celula(Icons.Outlined.Eco, d.consumoInstantaneo?.let { "%.1f".format(BR, it) } ?: "—", "", "km/l agora", m, Cores.Ok) },
        ),
        listOf(
            { m -> Celula(null, consumo(d, ctx), "", "km/l média", m) },
            { m ->
                Row(m, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (f == 0) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
                        tint = when { f == null -> Cores.TextoSuave; f == 0 -> Cores.Ok; else -> Cores.Vermelho }, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(when (f) { null -> "Falhas —"; 0 -> "Sem falhas"; 1 -> "1 falha"; else -> "$f falhas" }, color = Cores.Texto, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            },
        ),
    )
    Column(modifier.cartaoSuave().padding(horizontal = 8.dp, vertical = 6.dp)) {
        linhas.forEachIndexed { i, linha ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp).background(Cores.Borda))
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                linha.forEachIndexed { j, celula ->
                    if (j > 0) Box(Modifier.width(1.dp).fillMaxHeight(0.6f).background(Cores.Borda))
                    celula(Modifier.weight(if (linha.size == 2 && j == 1) 2f else 1f).fillMaxHeight())
                }
            }
        }
    }
}

/** Ícone + valor grande na mesma linha, rótulo pequeno embaixo (como na proposta). */
@Composable
private fun Celula(icone: ImageVector?, valor: String, unidade: String, rotulo: String, modifier: Modifier, cor: Color = Cores.Destaque) {
    Column(modifier.escalarSeMenorQue(46.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icone != null) Icon(icone, null, tint = cor, modifier = Modifier.size(20.dp))
            Text(valor, color = Cores.Texto, fontSize = 24.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (unidade.isNotEmpty()) Text(unidade, color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(rotulo, color = Cores.TextoSuave, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Mapa ilustrativo de fundo, nome do app, Casa/Trabalho, Iniciar direção e o bairro atual. */
@Composable
private fun MapaCompacto(sensores: Sensores, modifier: Modifier) {
    val ctx = LocalContext.current
    val config = Configuracoes.obter(ctx)
    Box(modifier.cartaoSuave().clickable { ctx.abrirNavegacaoComPainel() }) {
        MapaDecorativo(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.horizontalGradient(
            0f to Cores.CartaoSolido.copy(alpha = 0.85f), 0.6f to Cores.CartaoSolido.copy(alpha = 0.2f), 1f to Color.Transparent)))
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Cores.Waze), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Navigation, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(config.mapa.rotulo, color = Cores.Texto, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Navegação", color = Cores.TextoSuave, fontSize = 12.sp)
                }
            }
            Row(Modifier.clip(RoundedCornerShape(50)).background(Cores.Chip).border(1.dp, Cores.ChipBorda, RoundedCornerShape(50)),
                verticalAlignment = Alignment.CenterVertically) {
                listOf(Triple("Casa", Icons.Filled.Home, "Home"), Triple("Trabalho", Icons.Filled.Work, "Work")).forEach { (rotulo, icone, destino) ->
                    Row(Modifier.clickable { ctx.navegarFavorito(destino) }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(icone, null, tint = Cores.Texto, modifier = Modifier.size(17.dp))
                        Text(rotulo, color = Cores.Texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(RoundedCornerShape(50)).background(Cores.Destaque).padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Navigation, null, tint = Color.White, modifier = Modifier.size(17.dp))
                    Text("Iniciar direção", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                sensores.clima?.local?.let { local ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.DirectionsCar, null, tint = Cores.Destaque, modifier = Modifier.size(18.dp))
                        Text(local, color = Cores.Texto, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Spotify em linha: logo, título e controles (prev, play, next). */
@Composable
internal fun SpotifyLinha(monitor: MediaMonitor, modifier: Modifier) {
    val ctx = LocalContext.current
    val m = monitor.estado
    Row(modifier.cartaoSuave().clickable { ctx.abrirSpotify() }.padding(horizontal = 14.dp, vertical = 10.dp).escalarSeMenorQue(100.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (m.capa != null) {
            androidx.compose.foundation.Image(m.capa.asImageBitmap(), null, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        } else {
            Icon(androidx.compose.ui.res.painterResource(br.com.civic.launcher.R.drawable.ic_spotify), null, tint = Color(0xFF1ED760), modifier = Modifier.size(60.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column {
                Text("Spotify", color = Cores.TextoSuave, fontSize = 12.sp)
                Text(if (m.temSessao && m.titulo.isNotEmpty()) m.titulo else "Nada tocando", color = Cores.Texto, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(when { !m.permitido -> "Toque aqui para ativar capa e título"; m.temSessao -> m.artista; else -> "Toque para abrir" },
                    color = Cores.TextoSuave, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Redondo(Icons.Filled.SkipPrevious, 38.dp, Cores.Chip, Cores.Texto) { monitor.anterior() }
                Redondo(if (m.tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow, 46.dp, Cores.Destaque, Color.White) {
                    if (m.temSessao || !m.permitido) monitor.alternar() else ctx.abrirSpotify()
                }
                Redondo(Icons.Filled.SkipNext, 38.dp, Cores.Chip, Cores.Texto) { monitor.proxima() }
            }
        }
    }
}

@Composable
private fun Redondo(icone: ImageVector, tamanho: androidx.compose.ui.unit.Dp, fundo: Color, cor: Color, aoTocar: () -> Unit) {
    Box(Modifier.size(tamanho).clip(androidx.compose.foundation.shape.CircleShape).background(fundo).clickable(onClick = aoTocar), contentAlignment = Alignment.Center) {
        Icon(icone, null, tint = cor, modifier = Modifier.size(tamanho * 0.55f))
    }
}

/** Clima: bairro e hora no alto; ícone, temperatura e detalhes na mesma linha. Tocar abre a previsão. */
@Composable
internal fun ClimaLinha(clima: ClimaUi?, modifier: Modifier) {
    var aberto by remember { mutableStateOf(false) }
    if (aberto) ModalClima { aberto = false }
    Column(modifier.cartaoSuave().clickable { aberto = true }.padding(horizontal = 14.dp, vertical = 8.dp).escalarSeMenorQue(96.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        if (clima == null) {
            Text("Clima: precisa de internet", color = Cores.TextoSuave, fontSize = 14.sp)
            return@Column
        }
        val (icone, cor) = iconeCondicao(clima.condicao)
        val hora = remember(clima.atualizadoEm) { java.text.SimpleDateFormat("HH:mm", BR).format(java.util.Date(clima.atualizadoEm)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOn, null, tint = Cores.TextoSuave, modifier = Modifier.size(14.dp))
            Text(clima.local, color = Cores.TextoSuave, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
            Text("atualizado $hora", color = Cores.TextoSuave, fontSize = 11.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icone, null, tint = cor, modifier = Modifier.size(36.dp))
            Text("${clima.temperatura}°", color = Cores.Texto, fontSize = 36.sp, lineHeight = 38.sp, fontWeight = FontWeight.Light, maxLines = 1)
            Column(Modifier.weight(1f)) {
                Text(clima.condicao.texto, color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Máx. ${clima.maxima}°  Mín. ${clima.minima}°", color = Cores.TextoSuave, fontSize = 12.sp, maxLines = 1)
                Text(clima.chuva.ifEmpty { "Sensação ${clima.sensacao}° · Umidade ${clima.umidade}%" },
                    color = if (clima.chovendo) Cores.Waze else Cores.TextoSuave, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
