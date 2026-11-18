package br.com.civic.launcher

import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.roundToInt

data class ConectividadeUi(
    val operadora: String = "",
    val rede: String = "",
    val percentual: Int? = null,
    val dbm: Int? = null,
)

enum class CondicaoClima(val texto: String) {
    Limpo("Céu limpo"), Nublado("Nublado"), Chuva("Chuva"), Tempestade("Tempestade")
}

data class ClimaUi(val local: String, val temperatura: Int, val maxima: Int, val minima: Int, val condicao: CondicaoClima)

/** Velocidade (GPS), sinal da rede, Wi-Fi, Bluetooth e clima. Tudo sem depender do OBD. */
class Sensores(context: Context) {
    private val ctx = context.applicationContext

    var velocidadeKmh by mutableStateOf<Float?>(null)
        private set
    var conectividade by mutableStateOf(ConectividadeUi())
        private set
    var clima by mutableStateOf<ClimaUi?>(null)
        private set
    var bluetoothLigado by mutableStateOf(false)
        private set
    var wifiLigado by mutableStateOf(false)
        private set

    private val principal = Handler(Looper.getMainLooper())
    private val gps = ctx.getSystemService(LocationManager::class.java)
    private val telefonia = ctx.getSystemService(TelephonyManager::class.java)
    private var rodando = false
    private var ultimaLocalizacao: Location? = null
    private var proximoClima = 0L

    // Todos os métodos são implementados: em Android < 11 eles são abstratos.
    private val ouvinte = object : LocationListener {
        override fun onLocationChanged(local: Location) {
            ultimaLocalizacao = local
            velocidadeKmh = if (local.hasSpeed()) local.speed * 3.6f else null
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val ciclo = object : Runnable {
        override fun run() {
            if (!rodando) return
            atualizarRede()
            principal.postDelayed(this, 3_000)
        }
    }

    fun iniciar() {
        parar()
        rodando = true
        runCatching {
            gps.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, ouvinte, Looper.getMainLooper())
        }
        ciclo.run()
    }

    fun parar() {
        rodando = false
        principal.removeCallbacks(ciclo)
        runCatching { gps.removeUpdates(ouvinte) }
    }

    private fun atualizarRede() {
        wifiLigado = runCatching { ctx.getSystemService(WifiManager::class.java).isWifiEnabled }.getOrDefault(false)
        bluetoothLigado = runCatching {
            ctx.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true
        }.getOrDefault(false)

        val dbm = runCatching { telefonia.signalStrength?.cellSignalStrengths?.firstOrNull()?.dbm }
            .getOrNull()?.takeIf { it != Int.MAX_VALUE && it < 0 }
        val rede = runCatching { nomeRede(telefonia.dataNetworkType) }.getOrDefault("")
        conectividade = ConectividadeUi(
            operadora = telefonia.networkOperatorName.orEmpty(),
            rede = rede,
            // Aproximação: -120 dBm = 0% e -70 dBm = 100%.
            percentual = dbm?.let { (((it + 120) * 100) / 50).coerceIn(0, 100) },
            dbm = dbm,
        )

        if (System.currentTimeMillis() >= proximoClima) buscarClima()
    }

    private fun nomeRede(tipo: Int): String = when (tipo) {
        20 -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G"
        TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
        else -> ""
    }

    private fun buscarClima() {
        proximoClima = System.currentTimeMillis() + 30 * 60_000L
        val local = ultimaLocalizacao ?: runCatching { gps.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
        // Sem GPS ainda: usa Brasília como padrão.
        val lat = local?.latitude ?: -15.79
        val lon = local?.longitude ?: -47.88
        val nome = if (local != null) "Local atual" else "Brasília, DF"
        thread(name = "clima") {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto&forecast_days=1"
                val con = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }
                if (con.responseCode != 200) throw IllegalStateException()
                val json = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
                val atual = json.getJSONObject("current")
                val dia = json.getJSONObject("daily")
                val resultado = ClimaUi(
                    local = nome,
                    temperatura = atual.getDouble("temperature_2m").roundToInt(),
                    maxima = dia.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                    minima = dia.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                    condicao = condicao(atual.getInt("weather_code")),
                )
                principal.post { clima = resultado }
            } catch (_: Exception) {
                // Sem internet: tenta de novo em 1 minuto.
                proximoClima = System.currentTimeMillis() + 60_000L
            }
        }
    }

    private fun condicao(codigo: Int) = when (codigo) {
        0, 1 -> CondicaoClima.Limpo
        2, 3, 45, 48 -> CondicaoClima.Nublado
        95, 96, 99 -> CondicaoClima.Tempestade
        else -> CondicaoClima.Chuva
    }
}
