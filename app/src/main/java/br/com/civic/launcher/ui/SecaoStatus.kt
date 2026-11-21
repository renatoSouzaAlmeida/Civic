package br.com.civic.launcher.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.com.civic.launcher.LocalEstacionamento
import br.com.civic.launcher.Nuvem
import br.com.civic.launcher.Voz
import br.com.civic.launcher.VigiaService
import br.com.civic.launcher.obd.EstadoObd
import br.com.civic.launcher.obd.ObdMonitor
import kotlinx.coroutines.delay

private enum class NivelStatus { Ok, Atencao, Erro, Info }
private class ItemStatus(val titulo: String, val nivel: NivelStatus, val detalhe: String, val acao: String? = null, val aoTocar: (() -> Unit)? = null)

/** Checklist do que precisa estar funcionando para o app cumprir o que promete. Atualiza sozinho a cada 2 s. */
@Composable
fun SecaoStatusApp() {
    val ctx = LocalContext.current
    val obd = remember { ObdMonitor.obter(ctx) }
    val nuvem = remember { Nuvem.obter(ctx) }
    var tique by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2_000); tique++ } }
    @Suppress("UNUSED_EXPRESSION") tique

    fun permitido(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    fun abrir(i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    val detalhesApp = { abrir(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}"))) }
    fun idade(ms: Long): String { val s = (System.currentTimeMillis() - ms) / 1000; return if (s < 90) "há $s s" else if (s < 5400) "há ${s / 60} min" else "há ${s / 3600} h" }

    val itens = buildList {
        // Quem é este aparelho (o mesmo identificador aparece na Casa Tech).
        add(ItemStatus("Este aparelho", NivelStatus.Info,
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · app ${runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"} · ID ${nuvem.token.take(8)}"))
        // OBD
        val d = obd.dados
        add(
            when (obd.estado) {
                EstadoObd.Conectado -> ItemStatus("Adaptador OBD", NivelStatus.Ok, if ((d.rpm ?: 0) > 400) "Conectado, motor ligado (${d.rpm} rpm)" else "Conectado, motor desligado")
                EstadoObd.Demonstracao -> ItemStatus("Adaptador OBD", NivelStatus.Info, "Modo de demonstração (dados simulados)")
                EstadoObd.Conectando, EstadoObd.Procurando -> ItemStatus("Adaptador OBD", NivelStatus.Atencao, "Procurando ou conectando…")
                EstadoObd.Desconectado -> ItemStatus("Adaptador OBD", NivelStatus.Erro, "Desconectado. Confira se o adaptador está plugado e o Bluetooth ligado.")
            }
        )
        if (obd.estado == EstadoObd.Conectado) {
            add(
                when (obd.nivelTanqueSuportado) {
                    true -> ItemStatus("Nível do tanque", NivelStatus.Ok, "O carro informa (${d.nivelCombustivel?.let { "$it%" } ?: "—"}): abastecimento automático ativo")
                    false -> ItemStatus("Nível do tanque", NivelStatus.Atencao, "O carro não informa o nível pelo OBD: o abastecimento automático não funciona; lance à mão")
                    null -> ItemStatus("Nível do tanque", NivelStatus.Info, "Será verificado com o motor ligado")
                }
            )
            add(
                when (obd.mafSuportado) {
                    true -> ItemStatus("Consumo (fluxo de ar)", NivelStatus.Ok, "O carro informa a vazão de ar (MAF): consumo preciso")
                    false -> ItemStatus("Consumo (fluxo de ar)", NivelStatus.Info, "Sem MAF: consumo estimado pela pressão do coletor (MAP)")
                    null -> ItemStatus("Consumo (fluxo de ar)", NivelStatus.Info, "Será verificado com o motor ligado")
                }
            )
            if (d.monitoresTotal != null) add(
                if (d.monitoresPendentes.isEmpty()) ItemStatus("Monitores de emissões", NivelStatus.Ok, "${d.monitoresProntos} de ${d.monitoresTotal} prontos: pronto para vistoria")
                else ItemStatus("Monitores de emissões", NivelStatus.Atencao, "${d.monitoresProntos} de ${d.monitoresTotal} prontos. Faltam: ${d.monitoresPendentes.joinToString()}. Pode indicar códigos apagados há pouco tempo.")
            )
        }
        // Localização
        val gps = LocalEstacionamento.obter(ctx).posicaoRecente()
        add(
            when {
                !permitido(Manifest.permission.ACCESS_FINE_LOCATION) -> ItemStatus("GPS", NivelStatus.Erro, "Sem permissão de localização", "Permitir", detalhesApp)
                gps == null -> ItemStatus("GPS", NivelStatus.Atencao, "Sem posição ainda. Vá para um local aberto.")
                System.currentTimeMillis() - gps.time > 10 * 60_000L -> ItemStatus("GPS", NivelStatus.Atencao, "Última posição ${idade(gps.time)}")
                else -> ItemStatus("GPS", NivelStatus.Ok, "Posição recebida ${idade(gps.time)}")
            }
        )
        add(
            if (permitido(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) ItemStatus("Localização o tempo todo", NivelStatus.Ok, "Permitida: o local do estacionamento é guardado com o Waze na frente")
            else ItemStatus("Localização o tempo todo", NivelStatus.Atencao, "Escolha \"Permitir o tempo todo\" para registrar onde parou", "Ajustar", detalhesApp)
        )
        // Nuvem e Telegram
        add(
            when {
                nuvem.ultimaConexao > 0 -> ItemStatus("Nuvem (Supabase)", NivelStatus.Ok, "Respondeu ${idade(nuvem.ultimaConexao)}")
                else -> ItemStatus("Nuvem (Supabase)", NivelStatus.Atencao, "Sem resposta desde que o app abriu. Sem internet?")
            }
        )
        add(
            when {
                !nuvem.vinculado -> ItemStatus("Telegram", NivelStatus.Erro, "Não vinculado: em \"Avisos no celular\" envie o código ao bot e toque em Vincular")
                !nuvem.avisosLigados -> ItemStatus("Telegram", NivelStatus.Atencao, "Vinculado, mas os avisos estão desligados")
                else -> ItemStatus("Telegram", NivelStatus.Ok, "Vinculado e com avisos ligados")
            }
        )
        // Serviços e permissões do Android
        add(
            if (VigiaService.ativo) ItemStatus("Vigia (segundo plano)", NivelStatus.Ok, "Ativo: avisa onde parou mesmo com o app fechado")
            else ItemStatus("Vigia (segundo plano)", NivelStatus.Atencao, "Inativo. Abra o app para iniciar.")
        )
        add(
            if (Settings.canDrawOverlays(ctx)) ItemStatus("Painel de direção", NivelStatus.Ok, "Pode aparecer sobre o mapa")
            else ItemStatus("Painel de direção", NivelStatus.Atencao, "Falta a permissão \"Sobrepor a outros apps\"", "Permitir") { abrir(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
        )
        add(
            if (NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)) ItemStatus("Capa e controles do Spotify", NivelStatus.Ok, "Acesso às notificações concedido")
            else ItemStatus("Capa e controles do Spotify", NivelStatus.Atencao, "Falta o acesso às notificações", "Permitir") { abrir(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        )
        if (Build.VERSION.SDK_INT >= 31) add(
            if (permitido(Manifest.permission.BLUETOOTH_CONNECT) && permitido(Manifest.permission.BLUETOOTH_SCAN)) ItemStatus("Bluetooth", NivelStatus.Ok, "Permitido")
            else ItemStatus("Bluetooth", NivelStatus.Erro, "Sem permissão de dispositivos próximos (o OBD não conecta)", "Permitir", detalhesApp)
        )
        add(
            if (!br.com.civic.launcher.ComandoVoz.obter(ctx).disponivel()) ItemStatus("Comando de voz", NivelStatus.Atencao, "Sem serviço de reconhecimento de voz neste aparelho (instale o Google ou um app de voz)")
            else if (!permitido(Manifest.permission.RECORD_AUDIO)) ItemStatus("Comando de voz", NivelStatus.Info, "Disponível; a permissão do microfone é pedida ao tocar no microfone da barra do topo")
            else ItemStatus("Comando de voz", NivelStatus.Ok, "Pronto: toque no microfone da barra do topo")
        )
        add(
            if (Voz.obter(ctx).pronto) ItemStatus("Voz dos alertas", NivelStatus.Ok, "Síntese de voz pronta")
            else ItemStatus("Voz dos alertas", NivelStatus.Atencao, "Síntese de voz indisponível: instale uma voz em português nas configurações do Android")
        )
    }
    val problemas = itens.count { it.nivel == NivelStatus.Erro || it.nivel == NivelStatus.Atencao }
    val erros = itens.count { it.nivel == NivelStatus.Erro }

    Secao("Status do app") {
        Text(
            when { erros > 0 -> "$erros item(ns) com problema e ${problemas - erros} para conferir"; problemas > 0 -> "$problemas item(ns) para conferir"; else -> "Tudo certo" },
            color = when { erros > 0 -> Cores.Vermelho; problemas > 0 -> Cores.Amarelo; else -> Cores.Ok },
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
        )
        itens.forEach { LinhaStatus(it) }
    }
}

@Composable
private fun LinhaStatus(i: ItemStatus) {
    val cor = when (i.nivel) { NivelStatus.Ok -> Cores.Ok; NivelStatus.Atencao -> Cores.Amarelo; NivelStatus.Erro -> Cores.Vermelho; NivelStatus.Info -> Cores.TextoSuave }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Cores.Chip).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(cor))
        Column(Modifier.weight(1f)) {
            Text(i.titulo, color = Cores.Texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(i.detalhe, color = Cores.TextoSuave, fontSize = 13.sp)
        }
        if (i.acao != null && i.aoTocar != null) {
            Text(i.acao, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Cores.Destaque).clickable(onClick = i.aoTocar).padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}
