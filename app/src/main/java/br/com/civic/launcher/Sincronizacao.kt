package br.com.civic.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap

/**
 * Estado e política de sincronização com a nuvem (Supabase), compartilhados por toda a telemetria:
 *  - **Tentativas graduais:** depois de uma falha, o mesmo canal só tenta de novo após 30 s, 1 min, 2 min, 4 min e no máximo 5 min
 *    (poupa bateria e rede sem internet). Qualquer envio que dá certo mostra que a rede voltou e zera a espera de todos os canais.
 *  - **Estado visível:** última sincronização, falhas seguidas e o que está na fila (tela Status do app).
 *  - **Retenção:** o que não sobe fica na fila do aparelho por tempo limitado (pontos do trajeto 24 h; eventos 7 dias; avisos 48 h;
 *    viagens e caixa-preta até subirem), para o aparelho não encher sem internet.
 * Os dados importantes (ignição, avisos, viagens, caixa-preta) usam chaves únicas ou `event_id`, então reenviar nunca duplica.
 */
object Sincronizacao {
    private class Canal { @Volatile var falhas = 0; @Volatile var ultimaFalha = 0L }
    private val canais = ConcurrentHashMap<String, Canal>()

    /** Última vez que algum envio deu certo (0 = nenhum desde que o app abriu). */
    var ultimoOk by mutableStateOf(0L)
        private set
    /** Falhas seguidas, somando os canais (para mostrar). */
    var falhasSeguidas by mutableStateOf(0)
        private set

    const val ESPERA_MIN_MS = 30_000L
    const val ESPERA_MAX_MS = 5 * 60_000L
    const val RETENCAO_PONTOS_MS = 24 * 3_600_000L
    const val RETENCAO_EVENTOS_MS = 7 * 24 * 3_600_000L

    /** "civic_posicoes?on_conflict=..." -> "posicoes". */
    fun canalDe(caminho: String): String = caminho.substringAfter("civic_", caminho).substringBefore('?').substringBefore('/')

    private fun espera(falhas: Int): Long = (ESPERA_MIN_MS shl (falhas - 1).coerceIn(0, 4)).coerceAtMost(ESPERA_MAX_MS)

    /** Falta esperar? Sem falha anterior, ou com a espera já vencida, pode tentar. */
    fun podeTentar(canal: String, agora: Long = System.currentTimeMillis()): Boolean {
        val c = canais[canal] ?: return true
        return c.falhas == 0 || agora - c.ultimaFalha >= espera(c.falhas)
    }

    /** Quando será a próxima tentativa do canal que está esperando há mais tempo (0 = nenhum espera). */
    fun proximaTentativaEm(agora: Long = System.currentTimeMillis()): Long =
        canais.values.filter { it.falhas > 0 }.minOfOrNull { it.ultimaFalha + espera(it.falhas) }?.takeIf { it > agora } ?: 0L

    fun resultado(canal: String, ok: Boolean) {
        val agora = System.currentTimeMillis()
        if (ok) {
            // A rede voltou: nenhum canal precisa mais esperar.
            canais.values.forEach { it.falhas = 0 }
            ultimoOk = agora; falhasSeguidas = 0
        } else {
            val c = canais.getOrPut(canal) { Canal() }
            c.falhas++; c.ultimaFalha = agora
            falhasSeguidas = canais.values.sumOf { it.falhas }
        }
    }

    /** O que está na fila do aparelho esperando para subir: (rótulo, quantidade), só os não vazios. */
    fun fila(ctx: Context): List<Pair<String, Int>> = listOf(
        "pontos do trajeto" to Telemetria.obter(ctx).pontosPendentes(),
        "eventos de ignição" to Telemetria.obter(ctx).eventosPendentes(),
        "viagens" to ComputadorBordo.obter(ctx).viagensPendentes(),
        "avisos" to Nuvem.obter(ctx).avisosPendentes(),
        "caixa-preta" to CaixaPreta.obter(ctx).pendentes(),
    ).filter { it.second > 0 }

    /** Texto da situação para a tela Status. */
    fun resumo(ctx: Context, agora: Long = System.currentTimeMillis()): Pair<Boolean, String> {
        val fila = fila(ctx)
        val total = fila.sumOf { it.second }
        val ok = if (ultimoOk > 0) "último envio há ${tempo(agora - ultimoOk)}" else "nenhum envio desde que o app abriu"
        if (total == 0 && falhasSeguidas == 0) return true to "Tudo enviado ($ok)."
        val prox = proximaTentativaEm(agora).let { if (it > 0) " Nova tentativa em ${(it - agora) / 1000 + 1} s." else "" }
        val itens = fila.joinToString(", ") { "${it.second} ${it.first}" }
        return false to (if (total > 0) "Na fila: $itens. " else "") + (if (falhasSeguidas > 0) "$falhasSeguidas falha(s) seguida(s) (sem internet?). " else "") + ok + "." + prox
    }

    private fun tempo(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return if (s < 90) "$s s" else if (s < 5400) "${s / 60} min" else "${s / 3600} h" }
}
