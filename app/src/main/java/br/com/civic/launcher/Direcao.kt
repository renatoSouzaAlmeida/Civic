package br.com.civic.launcher

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Modo Direção em tela dividida: o cockpit do Civic Renatin de um lado (esta tela) e o mapa do outro.
 *
 * Como divide: (1) se o launcher já está em tela dividida (o dono dividiu pelo sistema da multimídia), o mapa abre ao lado com
 * FLAG_ACTIVITY_LAUNCH_ADJACENT; (2) senão, se o serviço de acessibilidade opcional está ligado, pede ao Android para dividir a tela e abre o mapa ao lado;
 * (3) senão, avisa como ligar. EXPERIMENTAL: depende de como a multimídia implementa a tela dividida.
 */
object Direcao {
    /** O cockpit do modo Direção está na tela do launcher. */
    var aberta by mutableStateOf(false)
    private var semDivisao by mutableStateOf(false)
    private var divididoPorNos = false
    private val principal = Handler(Looper.getMainLooper())

    private fun atividade(ctx: Context): Activity? { var c: Context? = ctx; while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }; return null }

    /** Abre o cockpit e coloca o mapa ao lado. [intentMapa] opcional leva direto a um destino (Casa, Trabalho, rotina). */
    fun iniciar(ctx: Context, intentMapa: Intent? = null) {
        aberta = true
        val act = atividade(ctx)
        when {
            act?.isInMultiWindowMode == true -> lancarMapa(ctx, intentMapa)
            DivisorTelaService.ativo() && DivisorTelaService.alternarDivisao() -> {
                divididoPorNos = true
                principal.postDelayed({ lancarMapa(ctx, intentMapa) }, 1_200) // dá tempo de o sistema dividir
            }
            else -> semDivisao = true
        }
    }

    private fun lancarMapa(ctx: Context, intentMapa: Intent?) {
        val mapa = Configuracoes.obter(ctx).mapa
        val intent = intentMapa ?: ctx.packageManager.getLaunchIntentForPackage(mapa.pacote)
        if (intent == null) { Toast.makeText(ctx, "App de navegação não instalado: ${mapa.pacote}", Toast.LENGTH_LONG).show(); return }
        runCatching { ctx.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)) }
    }

    /** Fecha o cockpit (e desfaz a divisão, se foi este app que dividiu). */
    fun fechar() {
        aberta = false
        if (divididoPorNos) { divididoPorNos = false; DivisorTelaService.alternarDivisao() }
    }

    /** Aviso de como ligar a divisão quando não foi possível dividir sozinho. */
    @Composable
    fun AvisoSemDivisao() {
        if (!semDivisao) return
        val ctx = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { semDivisao = false },
            title = { Text("Dividir a tela", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "O cockpit já está aberto aqui. Para ver o mapa ao lado, divida a tela:\n\n" +
                        "• use o botão de tela dividida da multimídia com o Civic Renatin e o mapa, ou\n" +
                        "• ligue o serviço \"Civic Renatin: dividir a tela\" em Acessibilidade para dividir sozinho (Config. > Painel de direção).\n\n" +
                        "Com a tela já dividida, toque em Direção de novo e o mapa abre ao lado.",
                    fontSize = 18.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    semDivisao = false
                    runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Abrir Acessibilidade", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { semDivisao = false }) { Text("Entendi", fontSize = 20.sp) } },
        )
    }
}
