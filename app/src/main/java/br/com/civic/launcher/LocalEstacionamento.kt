package br.com.civic.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

/** Onde o carro foi estacionado: posição, hora, idade da posição (quando foi o último sinal de GPS) e endereço. */
data class Estacionamento(val lat: Double, val lon: Double, val data: Long, val idadePosicaoMs: Long, val endereco: String?)

/**
 * "Onde parei o carro": quando o motor desliga (ou o adaptador OBD perde a energia com a ignição)
 * e continua assim por 3 s; também avisa na hora quando a multimídia desliga (ignição) depois de dirigir, o app entende que você estacionou, guarda a posição, registra no sininho e
 * manda para o Telegram o endereço e o pino do mapa.
 *
 * A posição vem do último sinal de GPS. Com o Waze na frente o GPS está ativo; se o último sinal for antigo
 * a mensagem avisa a idade dele. Pode ser salvo e enviado também à mão (Veículo → Viagem).
 */
class LocalEstacionamento private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("estacionamento", Context.MODE_PRIVATE)
    private val principal = Handler(Looper.getMainLooper())
    private val fila = Executors.newSingleThreadExecutor()

    var atual by mutableStateOf<Estacionamento?>(lerSalvo())
        private set

    /** Dirigiu desde o último aviso (guardado no aparelho: sobrevive se o app morrer junto com a ignição). */
    private var viajando: Boolean
        get() = prefs.getBoolean("viajando", false)
        set(v) { prefs.edit().putBoolean("viajando", v).apply() }

    /** Desligar a ignição derruba a multimídia: estes avisos do sistema chegam nesse instante. */
    private val aoDesligar = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (viajando) estacionar(manual = false, rapido = true)
        }
    }

    init {
        val filtro = IntentFilter().apply {
            addAction(Intent.ACTION_SHUTDOWN); addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_DISCONNECTED); addAction("android.intent.action.QUICKBOOT_POWEROFF")
        }
        runCatching { ctx.registerReceiver(aoDesligar, filtro) }
        // O app morreu antes de avisar (ignição desligou de vez): avisa agora com a última posição guardada.
        if (viajando) principal.postDelayed({ if (viajando) estacionar(manual = false, rapido = true) }, 15_000)
    }

    /** O app foi fechado (arrastado da lista de recentes): se o carro estava em uso, avisa onde parou na hora. */
    fun aoFecharApp() {
        if (ligado || viajando) estacionar(manual = false, rapido = true)
    }

    private var ligado = false
    private var ligadoDesde = 0L
    private var pendente = false
    private val confirmar = Runnable {
        pendente = false
        if (!ligado) { ligadoDesde = 0L; estacionar(manual = false) }
    }

    /** Chamado a cada leitura do OBD (rpm nulo = adaptador desconectado). */
    fun amostra(rpm: Int?, demonstracao: Boolean) {
        if (demonstracao) return
        val agora = System.currentTimeMillis()
        if ((rpm ?: 0) > 400) {
            if (!ligado) { ligado = true; if (ligadoDesde == 0L) ligadoDesde = agora }
            principal.removeCallbacks(confirmar); pendente = false
        } else {
            ligado = false
            // Só vira "estacionou" se o motor rodou por pelo menos 1 minuto (uma calada no semáforo não conta).
            if (ligadoDesde != 0L && agora - ligadoDesde >= 20_000 && !pendente) {
                pendente = true
                principal.postDelayed(confirmar, 3_000)
            }
        }
    }

    /** Guarda a posição de agora (a última conhecida) e avisa. Devolve false se não houver posição. */
    fun estacionar(manual: Boolean, rapido: Boolean = false): Boolean {
        val local = runCatching { ultimaPosicao() }.getOrNull()
        if (local == null) {
            // Sem posição: avisa mesmo assim (só a hora), para o aviso nunca se perder.
            if (manual) return false
            viajando = false
            val hora = java.text.SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(java.util.Date())
            Nuvem.obter(ctx).avisar("estacionamento", "🅿️ Carro estacionado às $hora\n⚠️ Não consegui obter a localização (sem sinal de GPS).")
            return true
        }
        val agora = System.currentTimeMillis()
        val idade = (agora - local.time).coerceAtLeast(0)
        val anterior = atual
        // Mesma vaga de antes (menos de 100 m e menos de 30 min): não repete o aviso automático.
        if (!manual && anterior != null && agora - anterior.data < 10 * 60_000 && distancia(anterior.lat, anterior.lon, local.latitude, local.longitude) < 100f) return true
        val novo = Estacionamento(local.latitude, local.longitude, agora, idade, null)
        atual = novo
        gravar(novo)
        viajando = false
        if (rapido) { avisar(novo, manual); return true } // ignição caindo: manda já, só com as coordenadas
        fila.execute {
            val endereco = endereco(local.latitude, local.longitude)
            val completo = novo.copy(endereco = endereco)
            principal.post { atual = completo; gravar(completo); avisar(completo, manual) }
        }
        return true
    }

    /** Reenvia a localização salva para o Telegram. */
    fun reenviar() { atual?.let { avisar(it) } }

    private fun avisar(e: Estacionamento, manual: Boolean = false) {
        val minutos = e.idadePosicaoMs / 60_000
        val idade = if (minutos >= 10) "\n⚠️ Último sinal de GPS há $minutos min: a posição pode estar um pouco defasada." else ""
        val texto = "🅿️ Carro estacionado" + (e.endereco?.let { " em $it" } ?: "") +
            "\n📍 %.5f, %.5f".format(Locale.US, e.lat, e.lon) +
            "\n🗺️ https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, e.lat, e.lon) + idade
        Nuvem.obter(ctx).avisarLocalizacao(e.lat, e.lon, texto, e.data, e.endereco, e.idadePosicaoMs / 1000, manual)
    }

    /** Última posição conhecida (GPS do sistema ou guardada pelo app), para o resumo enviado à nuvem. */
    fun posicaoRecente(): Location? = runCatching { ultimaPosicao() }.getOrNull()

    private fun ultimaPosicao(): Location? = runCatching {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val sistema = listOfNotNull(
            runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
            runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
        )
        // O último sinal que o app mesmo recebeu (guardado pelo Sensores) também vale.
        val propria = prefs.getString("ultimo_gps", null)?.let { runCatching { JSONObject(it) }.getOrNull() }?.let {
            Location("app").apply { latitude = it.getDouble("lat"); longitude = it.getDouble("lon"); time = it.getLong("t") }
        }
        (sistema + listOfNotNull(propria)).maxByOrNull { it.time }
    }.getOrNull()

    /** Chamado pelo Sensores a cada posição de GPS (guarda a cada 5 s) para termos o último sinal. */
    fun registrarPosicao(local: Location) {
        val agora = System.currentTimeMillis()
        if (local.hasSpeed() && local.speed * 3.6f > 8f) viajando = true
        if (agora - ultimoGravado < 5_000) return
        ultimoGravado = agora
        prefs.edit().putString("ultimo_gps", JSONObject().put("lat", local.latitude).put("lon", local.longitude).put("t", agora).toString()).apply()
    }
    private var ultimoGravado = 0L

    @Suppress("DEPRECATION")
    private fun endereco(lat: Double, lon: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        Geocoder(ctx, Locale("pt", "BR")).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { a ->
            listOfNotNull(a.thoroughfare, a.subThoroughfare, a.subLocality, a.locality).distinct().joinToString(", ").ifEmpty { null }
        }
    }.getOrNull()

    private fun distancia(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val r = FloatArray(1); Location.distanceBetween(lat1, lon1, lat2, lon2, r); return r[0]
    }

    private fun lerSalvo(): Estacionamento? = prefs.getString("atual", null)?.let { texto ->
        runCatching {
            val o = JSONObject(texto)
            Estacionamento(o.getDouble("lat"), o.getDouble("lon"), o.getLong("data"), o.getLong("idade"), if (o.has("end")) o.getString("end") else null)
        }.getOrNull()
    }

    private fun gravar(e: Estacionamento) {
        val o = JSONObject().put("lat", e.lat).put("lon", e.lon).put("data", e.data).put("idade", e.idadePosicaoMs)
        e.endereco?.let { o.put("end", it) }
        prefs.edit().putString("atual", o.toString()).apply()
    }

    companion object {
        @Volatile private var instancia: LocalEstacionamento? = null
        fun obter(ctx: Context): LocalEstacionamento = instancia ?: synchronized(this) {
            instancia ?: LocalEstacionamento(ctx.applicationContext).also { instancia = it }
        }
    }
}
