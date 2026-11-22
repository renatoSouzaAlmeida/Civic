package br.com.civic.launcher

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import br.com.civic.launcher.obd.DadosObd
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/** Uma amostra de 1 segundo da caixa-preta. [fonte] diz de onde veio a velocidade ("obd" ou "gps"). */
data class AmostraCP(val t: Long, val vel: Float?, val fonte: String, val rpm: Int?, val lat: Double?, val lon: Double?, val g: Float?, val tempMotor: Int?, val tensao: Float?)

/** Resumo de um evento salvo (a lista de Config. usa só isto; as amostras ficam no arquivo). */
data class EventoCP(val id: String, val t: Long, val motivo: String, val detalhe: String, val resumo: JSONObject, val enviado: Boolean)

/**
 * Caixa-preta digital (diário técnico de eventos). Guarda sempre os últimos segundos de telemetria (1 amostra por segundo: velocidade do OBD ou do GPS,
 * rpm, posição, pico de aceleração do sensor do aparelho, temperatura e tensão) e, quando algo acontece (frenagem brusca, possível colisão, SOS ou
 * alerta crítico), salva a janela de ANTES e de DEPOIS do evento, com a origem e a qualidade de cada sinal. Fica no aparelho (até 30 eventos) e vai à Casa Tech.
 * Limite: não é caixa-preta certificada nem substitui um EDR homologado.
 */
class CaixaPreta private constructor(private val ctx: Context) {
    private val principal = Handler(Looper.getMainLooper())
    private val prefs = ctx.getSharedPreferences("caixa_preta", Context.MODE_PRIVATE)
    private val pasta = File(ctx.filesDir, "caixa_preta").also { it.mkdirs() }
    private val buffer = ArrayDeque<AmostraCP>()
    val eventos = mutableStateListOf<EventoCP>()

    // Últimos valores recebidos (os alimentadores só atualizam estes campos; a amostra de 1 s é montada no tique).
    private var vel: Float? = null; private var fonte = "gps"; private var velEm = 0L
    private var rpm: Int? = null; private var rpmEm = 0L
    private var lat: Double? = null; private var lon: Double? = null; private var posEm = 0L
    private var tempMotor: Int? = null; private var tensao: Float? = null
    private var gPico = 0f; private var ultimoMovimento = 0L

    private class Gravacao(val t0: Long, val motivos: MutableList<String>, val detalhes: MutableList<String>, var fim: Long, val pre: List<AmostraCP>, val pos: MutableList<AmostraCP> = mutableListOf())
    private var gravando: Gravacao? = null
    private val ultimoDisparo = HashMap<String, Long>()

    // ---------- acelerômetro (só enquanto o carro anda) ----------
    private val sensores = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val acelerometro = sensores?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var ouvindo = false
    private val ouvinte = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val mag = Math.sqrt((e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble()).toFloat()
            gPico = max(gPico, abs(mag - SensorManager.GRAVITY_EARTH) / SensorManager.GRAVITY_EARTH)
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private val tique = object : Runnable {
        override fun run() { amostrar(); principal.postDelayed(this, 1_000) }
    }

    init {
        carregarEventos()
        principal.postDelayed(tique, 1_000)
    }

    // ---------- alimentadores ----------
    fun amostraObd(d: DadosObd) {
        val agora = System.currentTimeMillis()
        d.velocidade?.let { vel = it.toFloat(); fonte = "obd"; velEm = agora }
        d.rpm?.let { rpm = it; rpmEm = agora }
        d.tempMotor?.let { tempMotor = it }; d.tensao?.let { tensao = it }
    }

    fun amostraGps(l: Location) {
        val agora = System.currentTimeMillis()
        lat = l.latitude; lon = l.longitude; posEm = agora
        // O GPS só dá a velocidade se o OBD não deu nos últimos 3 s.
        if (l.hasSpeed() && agora - velEm > 3_000) { vel = l.speed * 3.6f; fonte = "gps"; velEm = agora }
    }

    // ---------- tique de 1 s ----------
    private fun amostrar() {
        val config = Configuracoes.obter(ctx)
        if (!config.caixaPretaAtiva) { desligarSensor(); buffer.clear(); return }
        val agora = System.currentTimeMillis()
        val velAtual = if (agora - velEm <= 5_000) vel else null
        val rpmAtual = if (agora - rpmEm <= 5_000) rpm else null
        val emUso = (velAtual ?: 0f) >= 3f || (rpmAtual ?: 0) > 400
        if (emUso) ultimoMovimento = agora
        val ativo = agora - ultimoMovimento < 60_000L || gravando != null
        if (ativo) ligarSensor() else desligarSensor()
        if (!ativo) { buffer.clear(); return }
        val a = AmostraCP(agora, velAtual, if (velAtual != null) fonte else "gps", rpmAtual,
            if (agora - posEm <= 5_000) lat else null, if (agora - posEm <= 5_000) lon else null,
            if (ouvindo) gPico else null, tempMotor, tensao)
        gPico = 0f
        buffer.addLast(a)
        while (buffer.size > 130 || (buffer.isNotEmpty() && agora - buffer.first().t > 125_000L)) buffer.removeFirst()
        gravando?.let { g -> g.pos.add(a); if (agora >= g.fim) finalizar(g) }
    }

    private fun ligarSensor() { if (!ouvindo && acelerometro != null) ouvindo = runCatching { sensores?.registerListener(ouvinte, acelerometro, SensorManager.SENSOR_DELAY_NORMAL) ?: false }.getOrDefault(false) }
    private fun desligarSensor() { if (ouvindo) { runCatching { sensores?.unregisterListener(ouvinte) }; ouvindo = false; gPico = 0f } }

    // ---------- disparo ----------
    /** Algo aconteceu: salva a janela de antes e continua gravando a de depois. Vários motivos juntos viram um evento só. */
    fun disparar(motivo: String, detalhe: String = "") {
        val config = Configuracoes.obter(ctx)
        if (!config.caixaPretaAtiva) return
        val agora = System.currentTimeMillis()
        if (agora - (ultimoDisparo[motivo] ?: 0L) < 30_000L && motivo != "teste") return
        ultimoDisparo[motivo] = agora
        val fim = agora + config.caixaPretaPosS * 1_000L
        val g = gravando
        if (g != null) { g.motivos.add(motivo); if (detalhe.isNotEmpty()) g.detalhes.add(detalhe); g.fim = max(g.fim, fim); return }
        // Garante pelo menos uma amostra "agora" mesmo parado (ex.: SOS com o carro parado).
        amostrar()
        val pre = buffer.filter { agora - it.t <= config.caixaPretaPreS * 1_000L }
        gravando = Gravacao(agora, mutableListOf(motivo), if (detalhe.isNotEmpty()) mutableListOf(detalhe) else mutableListOf(), fim, pre)
        if (config.caixaPretaPosS == 0) gravando?.let { finalizar(it) }
    }

    private fun finalizar(g: Gravacao) {
        gravando = null
        val config = Configuracoes.obter(ctx)
        val todas = (g.pre + g.pos).distinctBy { it.t }.sortedBy { it.t }
        val id = UUID.randomUUID().toString()
        val amostras = JSONArray()
        todas.forEach { a ->
            amostras.put(JSONObject().put("s", Math.round((a.t - g.t0) / 100.0) / 10.0).put("f", a.fonte)
                .apply { a.vel?.let { put("v", Math.round(it * 10) / 10.0) }; a.rpm?.let { put("r", it) }; a.lat?.let { put("la", it) }; a.lon?.let { put("lo", it) }
                    a.g?.let { put("g", Math.round(it * 100) / 100.0) }; a.tempMotor?.let { put("tm", it) }; a.tensao?.let { put("tv", Math.round(it * 10) / 10.0) } })
        }
        // Qualidade: frequência real, lacunas (segundos sem velocidade) e fontes usadas.
        val comVel = todas.filter { it.vel != null }
        val duracao = if (todas.size >= 2) (todas.last().t - todas.first().t) / 1000.0 else 0.0
        var maiorQueda = 0.0
        for (i in 1 until comVel.size) {
            val dt = (comVel[i].t - comVel[i - 1].t) / 1000.0
            if (dt in 0.5..2.5) maiorQueda = max(maiorQueda, (comVel[i - 1].vel!! - comVel[i].vel!!) / dt)
        }
        val antes = todas.filter { it.t <= g.t0 && it.vel != null }.lastOrNull()?.vel
        val resumo = JSONObject().put("vel_antes", antes?.let { Math.round(it * 10) / 10.0 } ?: JSONObject.NULL)
            .put("vel_min", comVel.minOfOrNull { it.vel!! }?.let { Math.round(it * 10) / 10.0 } ?: JSONObject.NULL)
            .put("vel_max", comVel.maxOfOrNull { it.vel!! }?.let { Math.round(it * 10) / 10.0 } ?: JSONObject.NULL)
            .put("desaceleracao_max_kmh_s", Math.round(maiorQueda * 10) / 10.0)
            .put("g_max", todas.mapNotNull { it.g }.maxOrNull()?.let { Math.round(it * 100) / 100.0 } ?: JSONObject.NULL)
            .put("fontes", JSONArray(todas.mapNotNull { if (it.vel != null) it.fonte else null }.distinct()))
            .put("frequencia_hz", if (duracao > 0) Math.round((todas.size - 1) / duracao * 100) / 100.0 else 0.0)
            .put("lacunas_s", todas.count { it.vel == null })
        val ultimaPos = todas.lastOrNull { it.lat != null }
        val evento = JSONObject().put("id", id).put("t", g.t0).put("motivo", g.motivos.distinct().joinToString(",")).put("detalhe", g.detalhes.joinToString(" | "))
            .put("pre_s", config.caixaPretaPreS).put("pos_s", config.caixaPretaPosS).put("amostras", amostras).put("resumo", resumo).put("enviado", false)
            .apply { ultimaPos?.let { put("lat", it.lat); put("lon", it.lon) } }
        File(pasta, "$id.json").writeText(evento.toString())
        limitarArquivos()
        carregarEventos()
        enviarPendentes()
    }

    // ---------- armazenamento local ----------
    private fun limitarArquivos() {
        val arquivos = pasta.listFiles { f -> f.extension == "json" }?.sortedByDescending { it.lastModified() } ?: return
        arquivos.drop(30).forEach { it.delete() }
    }

    fun carregarEventos() {
        val lista = (pasta.listFiles { f -> f.extension == "json" } ?: emptyArray()).mapNotNull { f ->
            runCatching {
                val o = JSONObject(f.readText())
                EventoCP(o.getString("id"), o.getLong("t"), o.getString("motivo"), o.optString("detalhe"), o.getJSONObject("resumo"), o.optBoolean("enviado"))
            }.getOrNull()
        }.sortedByDescending { it.t }
        eventos.clear(); eventos.addAll(lista)
    }

    /** Evento completo (com amostras) para o gráfico, a exportação e o envio. */
    fun lerEvento(id: String): JSONObject? = runCatching { JSONObject(File(pasta, "$id.json").readText()) }.getOrNull()

    fun apagar(id: String) { File(pasta, "$id.json").delete(); carregarEventos() }
    fun apagarTudo() { pasta.listFiles()?.forEach { it.delete() }; carregarEventos() }

    // ---------- nuvem ----------
    fun pendentes(): Int = eventos.count { !it.enviado }

    /** Envia à Casa Tech os eventos ainda não enviados (sem internet, ficam guardados e saem depois). */
    fun enviarPendentes() {
        val nuvem = Nuvem.obter(ctx)
        val versao = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
        eventos.filter { !it.enviado }.forEach { e ->
            val o = lerEvento(e.id) ?: return@forEach
            val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(o.getLong("t")))
            val linha = JSONObject().put("dispositivo", nuvem.token).put("id", e.id).put("ocorreu_em", iso).put("motivo", o.getString("motivo")).put("detalhe", o.optString("detalhe"))
                .put("janela_pre_s", o.getInt("pre_s")).put("janela_pos_s", o.getInt("pos_s")).put("amostras", o.getJSONArray("amostras")).put("resumo", o.getJSONObject("resumo"))
                .put("simulado", o.getString("motivo") == "teste").put("versao_app", versao)
                .put("lat", if (o.has("lat")) o.getDouble("lat") else JSONObject.NULL).put("lon", if (o.has("lon")) o.getDouble("lon") else JSONObject.NULL)
            nuvem.enviarJson("/rest/v1/civic_caixa_preta?on_conflict=dispositivo,id", JSONArray().put(linha).toString(), "resolution=ignore-duplicates") { ok ->
                if (ok) { o.put("enviado", true); runCatching { File(pasta, "${e.id}.json").writeText(o.toString()) }; principal.post { carregarEventos() } }
            }
        }
    }

    companion object {
        @Volatile private var instancia: CaixaPreta? = null
        fun obter(ctx: Context): CaixaPreta = instancia ?: synchronized(this) { instancia ?: CaixaPreta(ctx.applicationContext).also { instancia = it } }

        fun rotuloMotivo(m: String): String = m.split(",").joinToString(" + ") {
            when (it) { "frenagem_brusca" -> "Frenagem brusca"; "possivel_colisao" -> "Possível colisão"; "sos" -> "SOS"; "teste" -> "Teste"; else -> if (it.startsWith("alerta_critico")) "Alerta crítico" else it }
        }
    }
}
