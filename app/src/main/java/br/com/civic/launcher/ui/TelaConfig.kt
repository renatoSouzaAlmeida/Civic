package br.com.civic.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Configuracoes
import br.com.civic.launcher.ImagemCarro
import br.com.civic.launcher.MapaPreferido
import br.com.civic.launcher.R

/** Tela Config.: nome do app, dados do carro, imagem, mapa preferido e tamanho do painel de direção. */
@Composable
fun TelaConfig() {
    val ctx = LocalContext.current
    val config = remember { Configuracoes.obter(ctx) }
    var tamanhoPainel by remember { mutableStateOf(TamanhoPainel.ler(ctx)) }
    var formatoPainel by remember { mutableStateOf(FormatoPainel.ler(ctx)) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val duasColunas = maxWidth >= 860.dp
        val esquerda: @Composable ColumnScope.() -> Unit = {
            Secao("Identidade") {
                Campo("Nome do app (aparece no topo)", config.nomeApp) { config.salvarNomeApp(it) }
                Campo("Nome do carro", config.nomeCarro) { config.salvarNomeCarro(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Campo("Modelo", config.modelo, Modifier.weight(1f)) { config.salvarModelo(it) }
                    Campo("Ano", config.ano, Modifier.weight(0.6f), KeyboardType.Number) { config.salvarAno(it.filter { c -> c.isDigit() }.take(4)) }
                }
            }
            Secao("Imagem do carro") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImagemCarro.entries.forEach { opcao ->
                        val recurso = if (opcao == ImagemCarro.Civic) R.drawable.carro_cinza else R.drawable.carro_fit_cinza
                        CartaoOpcaoImagem(opcao.rotulo, recurso, config.imagemCarro == opcao, Modifier.weight(1f)) { config.salvarImagem(opcao) }
                    }
                }
            }
        }
        val direita: @Composable ColumnScope.() -> Unit = {
            Secao("Mapa de navegação") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MapaPreferido.entries.forEach { m ->
                        Opcao(m.rotulo, config.mapa == m, Modifier.weight(1f)) { config.salvarMapa(m) }
                    }
                }
                Campo("Endereço de Casa (opcional)", config.enderecoCasa) { config.salvarEnderecoCasa(it) }
                Campo("Endereço do Trabalho (opcional)", config.enderecoTrabalho) { config.salvarEnderecoTrabalho(it) }
                Text(
                    "No Waze, sem endereço o app usa seus favoritos Casa e Trabalho. No Google Maps, o endereço é necessário.",
                    color = Cores.TextoSuave, fontSize = 13.sp,
                )
            }
            Secao("Painel de direção") {
                Text("Formato", color = Cores.TextoSuave, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormatoPainel.entries.forEach { f ->
                        Opcao(f.rotulo, formatoPainel == f, Modifier.weight(1f)) { formatoPainel = f; FormatoPainel.salvar(ctx, f) }
                    }
                }
                Text("Tamanho", color = Cores.TextoSuave, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TamanhoPainel.entries.forEach { t ->
                        Opcao(t.rotulo, tamanhoPainel == t, Modifier.weight(1f)) { tamanhoPainel = t; TamanhoPainel.salvar(ctx, t) }
                    }
                }
            }
            Secao("Sistema") {
                BotaoPilula("Configurações do Android", Icons.Filled.Settings, Modifier.fillMaxWidth()) {
                    ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                BotaoPilula("Acesso às notificações (capa do Spotify)", Icons.Filled.Notifications, Modifier.fillMaxWidth()) {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                BotaoPilula("Sobrepor a outros apps (painel de direção)", Icons.Filled.Layers, Modifier.fillMaxWidth()) {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (duasColunas) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), content = esquerda)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), content = direita)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    esquerda()
                    direita()
                }
            }
        }
    }
}

@Composable
private fun Secao(titulo: String, conteudo: @Composable ColumnScope.() -> Unit) {
    Cartao(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, color = Cores.Texto, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            conteudo()
        }
    }
}

@Composable
private fun Campo(
    rotulo: String,
    valor: String,
    modifier: Modifier = Modifier,
    teclado: KeyboardType = KeyboardType.Text,
    aoMudar: (String) -> Unit,
) {
    OutlinedTextField(
        value = valor, onValueChange = aoMudar,
        label = { Text(rotulo) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun Opcao(texto: String, selecionada: Boolean, modifier: Modifier, aoTocar: () -> Unit) {
    val forma = RoundedCornerShape(50)
    Box(
        modifier.clip(forma)
            .background(if (selecionada) Cores.Destaque else Cores.Chip)
            .border(1.dp, if (selecionada) Cores.Destaque else Cores.ChipBorda, forma)
            .clickable(onClick = aoTocar).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = if (selecionada) Color.White else Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CartaoOpcaoImagem(rotulo: String, recurso: Int, selecionada: Boolean, modifier: Modifier, aoTocar: () -> Unit) {
    val forma = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(forma)
            .border(if (selecionada) 3.dp else 1.dp, if (selecionada) Cores.Destaque else Cores.ChipBorda, forma)
            .clickable(onClick = aoTocar),
    ) {
        Image(painterResource(recurso), rotulo, Modifier.fillMaxWidth().height(110.dp), contentScale = ContentScale.Crop)
        Text(
            rotulo, color = Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().background(Cores.Chip).padding(vertical = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
