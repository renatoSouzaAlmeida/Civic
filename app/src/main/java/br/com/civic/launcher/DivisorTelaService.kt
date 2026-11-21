package br.com.civic.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Serviço de acessibilidade opcional. Só faz uma coisa: pedir ao Android "alternar tela dividida" (ação global) para o modo Direção
 * dividir a tela sozinho. Não lê nem guarda nada do que está na tela. O dono liga em Acessibilidade (Config. > Painel de direção).
 */
class DivisorTelaService : AccessibilityService() {
    override fun onServiceConnected() { instancia = this }
    override fun onAccessibilityEvent(evento: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { instancia = null; return super.onUnbind(intent) }

    companion object {
        @Volatile private var instancia: DivisorTelaService? = null
        fun ativo() = instancia != null
        /** Liga ou desliga a tela dividida do sistema. Devolve false se o serviço não está ligado ou o sistema recusou. */
        fun alternarDivisao(): Boolean = instancia?.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) == true
    }
}
