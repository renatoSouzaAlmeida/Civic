package br.com.civic.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Vigia: serviço em primeiro plano (com uma notificação discreta) que mantém o app vivo mesmo com o Waze na frente
 * e recebe o aviso de "app fechado" (arrastado da lista de recentes). Nesse caso, se o carro estava em uso,
 * manda na hora o "Carro estacionado" para o Telegram e registra o desligamento para o dashboard.
 */
class VigiaService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() { ativo = false; super.onDestroy() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Civic ativo", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, CANAL)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("Civic Renatin ativo")
            .setContentText("Monitorando o carro")
            .setOngoing(true)
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID, n)
        }
        ativo = true
        // Os objetos abaixo vivem no processo; só garantimos que existem.
        Telemetria.obter(this)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        runCatching { LocalEstacionamento.obter(this).aoFecharApp() }
        runCatching { Telemetria.obter(this).aoFecharApp() }
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        /** O serviço está rodando (para a tela Status do app). */
        @Volatile var ativo = false
            private set
        private const val CANAL = "vigia"
        private const val ID = 42
    }
}
