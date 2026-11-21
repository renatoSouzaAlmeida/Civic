package br.com.civic.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Liga o Vigia (GPS, estacionamento, telemetria, condução) assim que a multimídia/tablet liga ou o app é atualizado.
 * Sem isto o monitoramento só começava quando a tela do launcher era aberta, e no Honda Fit o CarPlay abre direto,
 * então o launcher nunca aparecia, nada era monitorado e o aviso de estacionamento não saía.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON", "com.htc.intent.action.QUICKBOOT_POWERON", Intent.ACTION_MY_PACKAGE_REPLACED ->
                runCatching { ContextCompat.startForegroundService(context, Intent(context, VigiaService::class.java)) }
        }
    }
}
