package br.com.civic.launcher.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import br.com.civic.launcher.EstiloMenu
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Atualizador
import br.com.civic.launcher.Configuracoes
import br.com.civic.launcher.EstadoAtualizacao
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.R
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.SituacaoVersao
import br.com.civic.launcher.obd.EstadoObd
import br.com.civic.launcher.obd.ObdMonitor
import kotlinx.coroutines.delay
import java.util.Date

/** Fonte desenhada (Audiowide, licença livre) usada no nome do app. */
val FonteTitulo = FontFamily(Font(R.font.audiowide))

/** Estrutura fixa: barra do topo, conteúdo da tela escolhida e barra de navegação. */
@Composable
fun HomeScreen(
    monitor: MediaMonitor,
    atualizador: Atualizador,
    sensores: Sensores,
    obd: ObdMonitor,
    modoTema: ModoTema,
    aoAlternarTema: () -> Unit,
) {
    var tela by remember { mutableStateOf(Tela.Inicio) }
    val config = Configuracoes.obter(LocalContext.current)
    BackHandler(tela != Tela.Inicio) { tela = Tela.Inicio }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Cores.Fundo, Cores.FundoB)))
            .systemBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BarraTopo(sensores, atualizador, obd, modoTema, aoAlternarTema)
        // Sugestão da rotina diária (só aparece no período cadastrado).
        BannerRotina()
        val dock = config.estiloMenu == EstiloMenu.Dock
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // Com o dock, o conteúdo para acima dele (nada fica escondido embaixo do dock).
            Box(Modifier.fillMaxSize().padding(bottom = if (dock) ALTURA_DOCK + 10.dp else 0.dp)) {
                when (tela) {
                    Tela.Inicio -> TelaInicio(monitor, sensores, obd)
                    Tela.Central -> TelaCentral(sensores, obd, monitor)
                    Tela.Veiculo -> TelaVeiculo()
                    Tela.Apps -> TelaApps()
                    Tela.Config -> TelaConfig()
                }
            }
            if (dock) DockNavegacao(tela, { tela = it }, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
            // Alertas do motor por cima de qualquer tela.
            BannerAlerta(Modifier.align(Alignment.TopCenter).fillMaxWidth(0.7f).padding(top = 4.dp))
        }
        if (!dock) BarraNavegacao(tela, { tela = it }, Modifier.fillMaxWidth())
    }

    ModalNovaVersao(atualizador)
    ModalSemInternet()
    AlertaColisao()
    if (Hud.aberto) ModoHud(obd, sensores)
}

/** Hora e data à esquerda, nome do app ao centro e situação (versão, OBD, conexões, tema) à direita. */
@Composable
private fun BarraTopo(sensores: Sensores, atualizador: Atualizador, obd: ObdMonitor, modoTema: ModoTema, aoAlternarTema: () -> Unit) {
    val ctx = LocalContext.current
    val config = remember { Configuracoes.obter(ctx) }
    var agora by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { agora = Date(); delay(1_000) } }
    val hora = DateFormat.getTimeFormat(ctx).format(agora)
    val data = DateFormat.format("EEEE, d 'de' MMMM", agora).toString()

    BoxWithConstraints(Modifier.fillMaxWidth().height(52.dp)) {
        // Em tela estreita (celular em retrato) esconde o que não cabe, mas mantém o botão de tema.
        val compacto = maxWidth < 760.dp
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // Esquerda: logo, hora e data. Centro: nome do app (editável em Config.).
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(if (compacto) 36.dp else 44.dp))
                // A data fica embaixo da hora para sobrar espaço aos ícones da direita.
                Column {
                    Text(hora, color = Cores.Texto, fontSize = if (compacto) 24.sp else 26.sp, lineHeight = if (compacto) 26.sp else 28.sp, fontFamily = FonteTitulo, maxLines = 1)
                    if (!compacto) Text(data.replaceFirstChar { it.uppercase() }, color = Cores.TextoSuave, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1)
                }
            }
            if (!compacto) {
                Text(
                    config.nomeApp, color = Cores.Texto, fontSize = 24.sp, fontFamily = FonteTitulo,
                    letterSpacing = 3.sp, maxLines = 1,
                )
            }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(if (compacto) 10.dp else 14.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Versao(atualizador, compacto)
                // Verde = lendo o carro; azul = demonstração; amarelo = conectando; cinza = sem adaptador.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val corObd = when (obd.estado) {
                        EstadoObd.Conectado -> Cores.Ok
                        EstadoObd.Demonstracao -> Cores.Waze
                        EstadoObd.Conectando -> Cores.Amarelo
                        EstadoObd.Procurando, EstadoObd.Desconectado -> Cores.TextoSuave
                    }
                    Box(Modifier.size(10.dp).clip(CircleShape).background(corObd))
                    if (!compacto) Text(if (obd.estado == EstadoObd.Demonstracao) "OBD demo" else "OBD", color = Cores.TextoSuave, fontSize = 16.sp)
                }
                if (!compacto) {
                    Icon(Icons.Filled.Bluetooth, null, tint = if (sensores.bluetoothLigado) Cores.Destaque else Cores.TextoSuave, modifier = Modifier.size(26.dp))
                    Icon(Icons.Filled.Wifi, null, tint = if (sensores.wifiLigado) Cores.Destaque else Cores.TextoSuave, modifier = Modifier.size(26.dp))
                }
                Icon(
                    when (modoTema) {
                        ModoTema.Automatico -> Icons.Filled.BrightnessAuto
                        ModoTema.Claro -> Icons.Filled.LightMode
                        ModoTema.Escuro -> Icons.Filled.DarkMode
                    },
                    "Tema: ${modoTema.rotulo}", tint = Cores.Destaque, modifier = Modifier.toqueSuave(aoAlternarTema).size(26.dp),
                )
                // Avisos sonoros: alto-falante liga/desliga (o mesmo de Config. > Avisos sonoros).
                Icon(
                    if (config.vozAlertas) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    if (config.vozAlertas) "Desligar avisos sonoros" else "Ligar avisos sonoros",
                    tint = if (config.vozAlertas) Cores.Destaque else Cores.TextoSuave,
                    modifier = Modifier.toqueSuave { config.salvarVoz(!config.vozAlertas) }.size(26.dp),
                )
                BotaoVoz()
                Sininho()
            }
        }
    }
}

/** Bolinha vermelha que pisca, para a nova versão ficar evidente. */
@Composable
private fun PontoPiscando() {
    val alfa by pulsar(1f, 0.12f, 650)
    Box(Modifier.size(12.dp).clip(CircleShape).background(Cores.Vermelho.copy(alpha = alfa)))
}

@Composable
private fun Versao(atualizador: Atualizador, compacto: Boolean) {
    val tamanho = if (compacto) 12.sp else 15.sp
    val e = atualizador.estado
    // Só o número da versão e um ícone no padrão do app: verde suave com "ok" (em dia) ou laranja suave com
    // "sincronizar" (há versão nova). Tocar no laranja instala; tocar no restante verifica de novo.
    val verdeSuave = Cores.Ok.copy(alpha = 0.75f)
    val laranjaSuave = Color(0xFFF2A25C)
    val (icone, cor) = when {
        e is EstadoAtualizacao.Pronta || e is EstadoAtualizacao.Baixando -> Icons.Filled.Sync to laranjaSuave
        atualizador.situacao == SituacaoVersao.Atualizado -> Icons.Filled.CheckCircle to verdeSuave
        else -> null to Cores.TextoSuave
    }
    Row(
        Modifier.clickable { if (e is EstadoAtualizacao.Pronta) atualizador.instalar() else if (e !is EstadoAtualizacao.Baixando) atualizador.verificar() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("v${atualizador.versaoAtual}", color = cor, fontSize = tamanho, fontWeight = FontWeight.SemiBold, maxLines = 1)
        icone?.let { Icon(it, if (it == Icons.Filled.Sync) "Atualização disponível" else "Versão em dia", tint = cor, modifier = Modifier.size(if (compacto) 14.dp else 18.dp)) }
    }
}

/** Contagem de "possível colisão": ou toca em "Estou bem", ou o aviso com a localização vai para o Telegram. */
@Composable
private fun AlertaColisao() {
    val ctx = LocalContext.current
    val seg = remember { br.com.civic.launcher.Seguranca.obter(ctx) }
    val s = seg.colisao ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("🚨 Possível colisão detectada", fontWeight = FontWeight.Bold, color = Cores.Vermelho) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$s", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = Cores.Vermelho)
                Text("Se não tocar em \"Estou bem\", a sua localização será enviada para o Telegram.", fontSize = 18.sp)
            }
        },
        confirmButton = { TextButton(onClick = { seg.cancelarColisao() }) { Text("Estou bem", fontSize = 24.sp, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { seg.enviarColisaoAgora() }) { Text("Pedir ajuda agora", fontSize = 18.sp) } },
    )
}


/** Janela que aparece ao abrir o app quando há versão nova pronta (uma vez por versão e por abertura). */
@Composable
private fun ModalNovaVersao(atualizador: Atualizador) {
    val e = atualizador.estado
    var dispensada by remember { mutableStateOf<String?>(null) }
    if (e is EstadoAtualizacao.Pronta && dispensada != e.versao) {
        AlertDialog(
            onDismissRequest = { dispensada = e.versao },
            title = { Text("Nova versão disponível", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Versão instalada: ${atualizador.versaoAtual}\n" +
                        "Versão nova: ${e.numero}\n\n" +
                        "A versão ${e.versao} do Civic Renatin já foi baixada e está pronta para instalar.\n\n" +
                        "O Android vai pedir uma confirmação, e o app reabre sozinho ao final.",
                    fontSize = 18.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { dispensada = e.versao; atualizador.instalar() }) {
                    Text("Instalar agora", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { dispensada = e.versao }) { Text("Depois", fontSize = 20.sp) }
            },
        )
    }
}

/** Microfone da barra do topo: pede a permissão uma vez e escuta um comando curto. */
@Composable
private fun BotaoVoz() {
    val ctx = LocalContext.current
    val voz = remember { br.com.civic.launcher.ComandoVoz.obter(ctx) }
    val pedir = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok -> if (ok) voz.ouvir() }
    Icon(
        Icons.Filled.Mic, "Comando de voz", tint = if (voz.ouvindo) Cores.Vermelho else Cores.Destaque,
        modifier = Modifier.toqueSuave {
            if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) voz.ouvir()
            else pedir.launch(android.Manifest.permission.RECORD_AUDIO)
        }.size(26.dp),
    )
}

/** Sininho do topo: contador vermelho de não lidas; ao tocar, abre a lista de notificações. */
@Composable
private fun Sininho() {
    val ctx = LocalContext.current
    val central = remember { br.com.civic.launcher.CentralNotificacoes.obter(ctx) }
    var aberto by remember { mutableStateOf(false) }
    val naoLidas = central.naoLidas
    Box(Modifier.toqueSuave { aberto = true }) {
        Icon(
            if (naoLidas > 0) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
            "Notificações", tint = Cores.Destaque, modifier = Modifier.size(26.dp),
        )
        if (naoLidas > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp).size(18.dp).clip(CircleShape).background(Cores.Vermelho),
                contentAlignment = Alignment.Center,
            ) { Text(if (naoLidas > 9) "9+" else "$naoLidas", color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
    }
    if (aberto) {
        val formato = remember { java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("pt", "BR")) }
        AlertDialog(
            onDismissRequest = { aberto = false; central.marcarTodasLidas() },
            title = { Text("Notificações") },
            text = {
                if (central.itens.isEmpty()) Text("Nenhuma notificação por enquanto.", fontSize = 16.sp)
                else Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    central.itens.forEach { n ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(if (n.lida) androidx.compose.ui.graphics.Color.Transparent else Cores.Destaque))
                            Column(Modifier.weight(1f)) {
                                Text(n.mensagem, fontSize = 15.sp, fontWeight = if (n.lida) FontWeight.Normal else FontWeight.Bold)
                                Text(formato.format(java.util.Date(n.data)), fontSize = 12.sp, color = Cores.TextoSuave)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { aberto = false; central.marcarTodasLidas() }) { Text("Fechar", fontSize = 18.sp) } },
            dismissButton = {
                if (central.itens.isNotEmpty()) TextButton(onClick = { central.limpar(); aberto = false }) { Text("Limpar tudo", fontSize = 18.sp) }
            },
        )
    }
}
