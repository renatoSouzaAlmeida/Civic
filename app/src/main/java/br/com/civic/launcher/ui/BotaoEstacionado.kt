package br.com.civic.launcher.ui

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import br.com.civic.launcher.LocalEstacionamento
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.obd.ObdMonitor

/**
 * Botão "Estacionado": marca a posição de agora como a vaga, guarda o endereço (em "Onde parei") e manda o aviso ao Telegram
 * com as coordenadas e o endereço completo. Só aparece com o carro parado (velocidade abaixo de 1 km/h, pelo OBD ou, sem ele, pelo GPS);
 * sem velocidade conhecida não aparece. Fica na Home (todas as opções), no OBD e na Direção.
 */
@Composable
fun BotaoEstacionado(sensores: Sensores, obd: ObdMonitor, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val vel = obd.dados.velocidade?.toFloat() ?: sensores.velocidadeKmh
    if (vel == null || vel >= 1f) return
    val ultimoToque = remember { longArrayOf(0L) }
    BotaoPilula("Estacionado", Icons.Filled.LocationOn, modifier) {
        val agora = System.currentTimeMillis()
        if (agora - ultimoToque[0] < 10_000L) { Toast.makeText(ctx, "Aviso já enviado há instantes.", Toast.LENGTH_SHORT).show(); return@BotaoPilula }
        ultimoToque[0] = agora
        val ok = LocalEstacionamento.obter(ctx.applicationContext).estacionar(manual = true)
        Toast.makeText(ctx, if (ok) "Estacionamento marcado. Enviando o aviso com o endereço…" else "Sem posição de GPS agora. Tente de novo com sinal de GPS.", Toast.LENGTH_LONG).show()
        if (!ok) ultimoToque[0] = 0L
    }
}
