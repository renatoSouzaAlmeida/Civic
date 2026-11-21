package br.com.civic.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.civic.launcher.Configuracoes
import br.com.civic.launcher.Geofence
import br.com.civic.launcher.Sos

/** Config.: áreas permitidas (geofence), por GPS e sem OBD. */
@Composable
fun SecaoGeofence() {
    val ctx = LocalContext.current
    val gf = remember { Geofence.obter(ctx) }
    val config = remember { Configuracoes.obter(ctx) }
    var nome by remember { mutableStateOf("") }
    var raio by remember { mutableIntStateOf(300) }
    var msg by remember { mutableStateOf<String?>(null) }
    var editando by remember { mutableStateOf<String?>(null) }
    var mover by remember { mutableStateOf(false) }
    Secao("Geofence (áreas permitidas)") {
        Text("Avisa quando o carro sai ou entra de uma área. Funciona pelo GPS (inclusive sem OBD). Perda de sinal de GPS não conta como saída, e o aviso só vale depois de 3 leituras seguidas.", color = Cores.TextoSuave, fontSize = 13.sp)
        if (gf.areas.isEmpty()) Text("Nenhuma área. Fique com o carro no local e toque em \"Criar área aqui\".", color = Cores.TextoSuave, fontSize = 14.sp)
        gf.areas.forEach { a ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cores.Chip).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.LocationOn, null, tint = if (a.ativa) Cores.Destaque else Cores.TextoSuave, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f).clickable { editando = a.id; nome = a.nome; raio = a.raioM; mover = false; msg = "Editando \"${a.nome}\": altere o nome ou o raio abaixo e toque em Salvar." }) {
                    Text(a.nome + if (editando == a.id) " (editando)" else "", color = Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("raio ${a.raioM} m · " + when {
                        !a.ativa -> "pausada"
                        gf.estado[a.id] == true -> "carro dentro"
                        gf.estado[a.id] == false -> "carro fora"
                        else -> "aguardando o GPS"
                    }, color = Cores.TextoSuave, fontSize = 13.sp)
                }
                Opcao(if (a.ativa) "Ativa" else "Pausada", a.ativa, Modifier.width(110.dp)) { gf.alternar(a.id) }
                Icon(Icons.Filled.Close, "Remover área", tint = Cores.TextoSuave, modifier = Modifier.size(28.dp).clickable { gf.remover(a.id) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Opcao("Casa", nome == "Casa", Modifier.weight(1f)) { nome = "Casa" }
            Opcao("Trabalho", nome == "Trabalho", Modifier.weight(1f)) { nome = "Trabalho" }
        }
        Campo("Nome da área", nome) { nome = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Raios próprios (ex.: 200 m de uma área editada) também aparecem, para não perder o valor atual.
            (listOf(100, 300, 500, 1000) + raio).distinct().sorted().forEach { r -> Opcao("$r m", raio == r, Modifier.weight(1f)) { raio = r } }
        }
        val emEdicao = editando?.takeIf { id -> gf.areas.any { it.id == id } }
        if (emEdicao != null) {
            Opcao("Mover o centro para a posição atual", mover, Modifier.fillMaxWidth()) { mover = !mover }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotaoPilula("Salvar alterações", Icons.Filled.Check, Modifier.weight(1f)) {
                    msg = if (gf.editar(emEdicao, nome, raio, mover)) { editando = null; nome = ""; mover = false; "Área atualizada." } else "Sem posição de GPS agora para mover o centro."
                }
                BotaoPilula("Cancelar", Icons.Filled.Close, Modifier.weight(1f)) { editando = null; nome = ""; mover = false; msg = null }
            }
        } else BotaoPilula("Criar área aqui (posição atual)", Icons.Filled.AddLocation, Modifier.fillMaxWidth()) {
            msg = if (gf.criarAqui(nome, raio)) { nome = ""; "Área criada na posição atual." } else "Sem posição de GPS agora. Tente de novo com sinal de GPS."
        }
        msg?.let { Text(it, color = Cores.TextoSuave, fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Aviso no Telegram", color = Cores.Texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Opcao("Ligado", config.geofenceTelegram, Modifier.width(100.dp)) { config.salvarGeofenceTelegram(true) }
            Opcao("Desligado", !config.geofenceTelegram, Modifier.width(110.dp)) { config.salvarGeofenceTelegram(false) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Faixa na tela", color = Cores.Texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Opcao("Ligada", config.geofenceFaixa, Modifier.width(100.dp)) { config.salvarGeofenceFaixa(true) }
            Opcao("Desligada", !config.geofenceFaixa, Modifier.width(110.dp)) { config.salvarGeofenceFaixa(false) }
        }
    }
}

/** Config.: contatos de emergência do botão SOS. */
@Composable
fun SecaoSos() {
    val ctx = LocalContext.current
    val sos = remember { Sos.obter(ctx) }
    var nome by remember { mutableStateOf("") }
    var tel by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var permitido by remember { mutableStateOf(sos.temPermissaoSms()) }
    val pedir = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { permitido = it }
    Secao("SOS e contatos de emergência") {
        Text("O botão SOS (vermelho, na barra do topo) tem uma contagem de 5 s para cancelar. Depois manda ao Telegram vinculado e por SMS aos contatos: localização, hora, o carro e os dados do OBD que existirem. Não afirma colisão.", color = Cores.TextoSuave, fontSize = 13.sp)
        Text(
            when {
                !sos.temTelefonia() -> "Esta multimídia não tem telefonia: o SMS não funciona; o SOS vai só pelo Telegram."
                !permitido -> "SMS: falta liberar a permissão."
                else -> "SMS liberado."
            },
            color = if (sos.temTelefonia() && permitido) Cores.Ok else Cores.Amarelo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        )
        if (sos.temTelefonia() && !permitido) BotaoPilula("Liberar envio de SMS", Icons.Filled.Sms, Modifier.fillMaxWidth()) { pedir.launch(android.Manifest.permission.SEND_SMS) }
        sos.contatos.forEach { c ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cores.Chip).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, null, tint = Cores.Destaque, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(c.nome, color = Cores.Texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(c.telefone, color = Cores.TextoSuave, fontSize = 13.sp)
                }
                Icon(Icons.Filled.Close, "Remover contato", tint = Cores.TextoSuave, modifier = Modifier.size(28.dp).clickable { sos.remover(c) })
            }
        }
        Campo("Nome do contato", nome) { nome = it }
        Campo("Telefone com DDD (ex.: 11999998888)", tel, teclado = KeyboardType.Phone) { tel = it }
        BotaoPilula("Adicionar contato", Icons.Filled.PersonAdd, Modifier.fillMaxWidth()) {
            msg = if (sos.adicionar(nome, tel)) { nome = ""; tel = ""; "Contato adicionado." } else "Informe o nome e um telefone válido."
        }
        BotaoPilula("Testar o SOS (mensagem de teste)", Icons.Filled.Notifications, Modifier.fillMaxWidth()) { sos.testar() }
        msg?.let { Text(it, color = Cores.TextoSuave, fontSize = 13.sp) }
    }
}
