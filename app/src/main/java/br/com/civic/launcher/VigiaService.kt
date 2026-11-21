package br.com.civic.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.Build
import android.os.IBinder

/**
 * Vigia: serviço em primeiro plano (com uma notificação discreta) que mantém o app vivo mesmo com o Waze na frente
 * e recebe o aviso de "app fechado" (arrastado da lista de recentes). Nesse caso, se o carro estava em uso,
 * manda na hora o "Carro estacionado" para o Telegram e registra o desligamento para o dashboard.
 */
class VigiaService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    // GPS e posição pela rede sempre ligados enquanto o Vigia roda: o local do estacionamento e o trajeto
    // não dependem de o launcher estar na frente (com o Waze ou outro app na frente o launcher fica em segundo plano).
    private var gpsLigado = false
    private val ouvinteGps = object : LocationListener {
        override fun onLocationChanged(local: Location) {
            LocalEstacionamento.obter(this@VigiaService).registrarPosicao(local)
            Telemetria.obter(this@VigiaService).posicao(local)
            Geofence.obter(this@VigiaService).amostra(local)
            // Sem OBD (ex.: Fit com CarPlay na frente) a viagem é medida pelo GPS mesmo com o launcher em segundo plano.
            if (local.hasSpeed()) {
                ComputadorBordo.obter(this@VigiaService).amostraGps(local.speed * 3.6f)
                Seguranca.obter(this@VigiaService).amostraGps(local.speed * 3.6f)
            }
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }
    private val ouvinteRede = object : LocationListener {
        override fun onLocationChanged(local: Location) { Telemetria.obter(this@VigiaService).posicaoRede(local) }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private fun ligarGps() {
        if (gpsLigado) return
        val lm = getSystemService(LocationManager::class.java)
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f, ouvinteGps, Looper.getMainLooper()); gpsLigado = true }
        runCatching { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60_000L, 0f, ouvinteRede, Looper.getMainLooper()) }
    }

    override fun onDestroy() {
        ativo = false
        runCatching { val lm = getSystemService(LocationManager::class.java); lm.removeUpdates(ouvinteGps); lm.removeUpdates(ouvinteRede) }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Civic ativo", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, CANAL)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("Civic Renatin ativo")
            .setContentText("Monitorando o carro")
            .setOngoing(true)
            .build()
        // Tipo "location": com o CarPlay/Android Auto/Waze na frente o Android só deixa o GPS ligado em segundo plano a um serviço desse tipo
        // (ou com a permissão "localização o tempo todo"). Se não houver permissão de localização, cai para o modo simples.
        val ok = runCatching {
            when {
                Build.VERSION.SDK_INT >= 34 -> startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                Build.VERSION.SDK_INT >= 29 -> startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                else -> startForeground(ID, n)
            }
        }.isSuccess
        if (!ok) runCatching { startForeground(ID, n) }
        ativo = true
        ligarGps()
        // Os objetos abaixo vivem no processo; só garantimos que existem.
        Telemetria.obter(this)
        // Com o CarPlay/Android Auto na frente (ou sem abrir o launcher) o OBD também precisa conectar e ler.
        runCatching { br.com.civic.launcher.obd.ObdMonitor.obter(this).iniciar() }
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
