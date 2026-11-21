package br.com.civic.launcher

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import br.com.civic.launcher.obd.AlertasMotor
import br.com.civic.launcher.obd.TipoAlerta
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/** Área circular permitida (Casa, Trabalho ou ponto escolhido). */
data class Area(val id: String, val nome: String, val lat: Double, val lon: Double, val raioM: Int, val ativa: Boolean = true, val atualizadoEm: Long = 0L)

/** Área como veio da nuvem (a Casa Tech cria, edita e remove). */
data class AreaNuvem(val area: Area, val removida: Boolean)

/**
 * Geofence: avisa quando o carro sai ou entra de uma área. Funciona só com o GPS (vale para o Fit, sem OBD).
 *
 * Regras contra falso alerta:
 *  - leituras com precisão pior que 80 m são ignoradas; **perda de sinal não gera saída** (sem leitura, nada muda);
 *  - histerese: só conta "fora" além do raio + folga (25 m ou a precisão), e a faixa entre o raio e a folga é ignorada;
 *  - uma transição só vale depois de 3 leituras seguidas no novo estado e com 2 min desde o último evento da área;
 *  - o primeiro estado conhecido de uma área é guardado sem avisar (nada de alerta ao criar ou ao reiniciar o app).
 */
class Geofence private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("geofence", Context.MODE_PRIVATE)
    private val principal = Handler(Looper.getMainLooper())
    val areas = mutableStateListOf<Area>()
    /** id da área -> o carro está dentro (ausente = ainda não sabemos). */
    val estado = mutableStateMapOf<String, Boolean>()
    private val contagem = HashMap<String, Int>()
    private val ultimoEvento = HashMap<String, Long>()

    init { carregar() }

    fun criarAqui(nome: String, raioM: Int): Boolean {
        val l = runCatching { LocalEstacionamento.obter(ctx).posicaoRecente() }.getOrNull() ?: return false
        areas.add(Area(UUID.randomUUID().toString(), nome.trim().ifEmpty { "Área ${areas.size + 1}" }, l.latitude, l.longitude, raioM, atualizadoEm = System.currentTimeMillis()))
        salvar(); return true
    }

    fun alternar(id: String) { val i = areas.indexOfFirst { it.id == id }; if (i >= 0) { areas[i] = areas[i].copy(ativa = !areas[i].ativa, atualizadoEm = System.currentTimeMillis()); estado.remove(id); salvar() } }

    fun remover(id: String) {
        areas.removeAll { it.id == id }; estado.remove(id)
        val r = runCatching { JSONArray(prefs.getString("removidas", "[]")) }.getOrDefault(JSONArray()); r.put(id)
        prefs.edit().putString("removidas", r.toString()).apply()
        salvar()
    }

    /** O carro está dentro de alguma área ativa? (usado para não chamar de "local desconhecido" um lugar permitido) */
    fun dentroDeAlgumaArea(lat: Double, lon: Double): Boolean = areas.any { it.ativa && distancia(it.lat, it.lon, lat, lon) <= it.raioM }

    /** A cada posição de GPS (serviço Vigia). */
    fun amostra(l: Location) {
        if (l.hasAccuracy() && l.accuracy > 80f) return
        val agora = System.currentTimeMillis()
        areas.filter { it.ativa }.forEach { a ->
            val d = distancia(a.lat, a.lon, l.latitude, l.longitude)
            val folga = maxOf(25f, if (l.hasAccuracy()) l.accuracy else 0f)
            val leitura: Boolean? = when { d <= a.raioM -> true; d > a.raioM + folga -> false; else -> null }
            if (leitura == null) { contagem[a.id] = 0; return@forEach }
            val atual = estado[a.id]
            if (atual == null) { estado[a.id] = leitura; salvarEstado(); return@forEach }
            if (leitura == atual) { contagem[a.id] = 0; return@forEach }
            val n = (contagem[a.id] ?: 0) + 1
            contagem[a.id] = n
            if (n >= 3 && agora - (ultimoEvento[a.id] ?: 0L) >= 120_000L) {
                estado[a.id] = leitura; contagem[a.id] = 0; ultimoEvento[a.id] = agora; salvarEstado()
                aviso(a, entrou = leitura, l)
            }
        }
    }

    private fun aviso(a: Area, entrou: Boolean, l: Location) {
        val config = Configuracoes.obter(ctx)
        val hora = java.text.SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(java.util.Date())
        val texto = "📍 ${if (entrou) "Entrou em" else "Saiu de"} ${a.nome} (raio ${a.raioM} m) às $hora" +
            "\n🗺️ https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, l.latitude, l.longitude)
        if (config.geofenceTelegram) Nuvem.obter(ctx).avisar("geofence", texto) // registra no sino e manda ao Telegram
        else CentralNotificacoes.obter(ctx).registrar("geofence", texto.lineSequence().first())
        if (config.geofenceFaixa) {
            val alertas = AlertasMotor.obter(ctx)
            alertas.definirExterna(TipoAlerta.Geofence, "${if (entrou) "Entrou em" else "Saiu de"} ${a.nome}.")
            principal.postDelayed({ alertas.definirExterna(TipoAlerta.Geofence, null) }, 20_000)
        }
    }

    // ---------- persistência e nuvem ----------

    private fun carregar() {
        runCatching {
            val arr = JSONArray(prefs.getString("areas", "[]"))
            for (i in 0 until arr.length()) arr.getJSONObject(i).let { o ->
                areas.add(Area(o.getString("id"), o.getString("nome"), o.getDouble("lat"), o.getDouble("lon"), o.getInt("raio"), o.optBoolean("ativa", true), o.optLong("ue", 0L)))
            }
            val e = JSONObject(prefs.getString("dentro", "{}"))
            e.keys().forEach { k -> if (areas.any { it.id == k }) estado[k] = e.getBoolean(k) }
        }
    }

    private fun salvarEstado() {
        val e = JSONObject(); estado.forEach { (k, v) -> e.put(k, v) }
        prefs.edit().putString("dentro", e.toString()).apply()
    }

    private fun salvar() {
        val arr = JSONArray()
        areas.forEach { arr.put(JSONObject().put("id", it.id).put("nome", it.nome).put("lat", it.lat).put("lon", it.lon).put("raio", it.raioM).put("ativa", it.ativa).put("ue", it.atualizadoEm)) }
        prefs.edit().putString("areas", arr.toString()).apply()
        sincronizar()
    }

    /** Quando cada área foi editada por último na nuvem (da última baixada), para só enviar o que o aparelho editou depois. */
    private val remotoEm = HashMap<String, Long>()

    /** Junta o que veio da nuvem: vale a edição mais recente (aparelho ou Casa Tech). Devolve true se algo mudou aqui. */
    fun aplicarDaNuvem(remotas: List<AreaNuvem>): Boolean {
        val removidasLocais = runCatching { JSONArray(prefs.getString("removidas", "[]")) }.getOrDefault(JSONArray()).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        var mudou = false
        remotas.forEach { (r, removida) ->
            remotoEm[r.id] = r.atualizadoEm
            val i = areas.indexOfFirst { it.id == r.id }
            when {
                removida -> if (i >= 0 && r.atualizadoEm >= areas[i].atualizadoEm) { areas.removeAt(i); estado.remove(r.id); mudou = true }
                r.id in removidasLocais -> {} // removida aqui e ainda não enviada: não ressuscita
                i < 0 -> { areas.add(r); mudou = true }
                r.atualizadoEm > areas[i].atualizadoEm -> { areas[i] = r; estado.remove(r.id); mudou = true }
            }
        }
        if (mudou) {
            val arr = JSONArray()
            areas.forEach { arr.put(JSONObject().put("id", it.id).put("nome", it.nome).put("lat", it.lat).put("lon", it.lon).put("raio", it.raioM).put("ativa", it.ativa).put("ue", it.atualizadoEm)) }
            prefs.edit().putString("areas", arr.toString()).apply(); salvarEstado()
        }
        return mudou
    }

    /** Envia ao Supabase só as áreas que o aparelho editou depois da versão da nuvem (as removidas vão marcadas). Sem internet, tenta na próxima. */
    fun sincronizar() {
        val nuvem = Nuvem.obter(ctx)
        val removidas = runCatching { JSONArray(prefs.getString("removidas", "[]")) }.getOrDefault(JSONArray())
        val agora = System.currentTimeMillis()
        fun iso(ms: Long) = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(ms))
        val lote = JSONArray()
        areas.filter { it.atualizadoEm > (remotoEm[it.id] ?: -1L) }.forEach { lote.put(JSONObject().put("dispositivo", nuvem.token).put("id", it.id).put("nome", it.nome).put("lat", it.lat).put("lon", it.lon)
            .put("raio_m", it.raioM).put("ativa", it.ativa).put("removida", false).put("atualizado_em", iso(if (it.atualizadoEm > 0) it.atualizadoEm else agora))) }
        for (i in 0 until removidas.length()) lote.put(JSONObject().put("dispositivo", nuvem.token).put("id", removidas.getString(i)).put("nome", "(removida)")
            .put("lat", 0).put("lon", 0).put("raio_m", 1).put("ativa", false).put("removida", true).put("atualizado_em", iso(agora)))
        if (lote.length() == 0) return
        val n = removidas.length()
        nuvem.enviarJson("/rest/v1/civic_areas?on_conflict=dispositivo,id", lote.toString(), "resolution=merge-duplicates") { ok ->
            if (ok && n > 0) prefs.edit().putString("removidas", "[]").apply()
        }
    }

    private fun distancia(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float =
        FloatArray(1).also { Location.distanceBetween(lat1, lon1, lat2, lon2, it) }[0]

    companion object {
        @Volatile private var instancia: Geofence? = null
        fun obter(ctx: Context): Geofence = instancia ?: synchronized(this) { instancia ?: Geofence(ctx.applicationContext).also { instancia = it } }
    }
}
