package br.com.civic.launcher.obd

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.civic.launcher.Nuvem
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

/** Foto dos dados do motor no instante em que um código de falha surgiu (modo 02 do OBD). */
data class FreezeFrame(val codigo: String, val em: Long, val itens: List<Pair<String, String>>)
/** Um aquecimento do motor a frio: segundos até 80 °C (null = não chegou em 25 min de motor ligado). */
data class Aquecimento(val em: Long, val segundos: Int?, val tempInicial: Int)
data class ResultadoCatalisador(val em: Long, val razao: Double?, val oscilacoesAntes: Int, val oscilacoesDepois: Int, val parecer: String)
data class ResultadoMarchaLenta(val em: Long, val media: Int, val desvio: Double, val faixa: Int, val irregular: Boolean)
data class EventoDreno(val em: Long, val horasParado: Double, val tensaoPartida: Float, val taxaVh: Double?, val suspeito: Boolean)
/** PID personalizado para a temperatura do fluido do câmbio (a Honda usa mensagens próprias, modo 22). */
data class ConfigCambio(val cabecalho: String = "", val comando: String = "", val bytes: Int = 1, val multiplicador: Double = 1.0, val deslocamento: Double = -40.0) {
    val ativo: Boolean get() = comando.length >= 4
}

/**
 * Diagnósticos avançados do OBD: freeze frame, saúde do termostato, catalisador (sondas lambda), dreno de bateria,
 * marcha lenta irregular, códigos apagados, modo 06 e temperatura do câmbio. Os dados ficam no aparelho e
 * os achados anormais viram aviso no Telegram.
 */
class DiagnosticoAvancado private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("diagnostico", Context.MODE_PRIVATE)
    private val fmt = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))
    private val br = Locale("pt", "BR")

    val freezeFrames = mutableStateListOf<FreezeFrame>()
    val aquecimentos = mutableStateListOf<Aquecimento>()
    val drenos = mutableStateListOf<EventoDreno>()
    var catalisador by mutableStateOf<ResultadoCatalisador?>(null)
        private set
    var marchaLenta by mutableStateOf<ResultadoMarchaLenta?>(null)
        private set
    var modo06 by mutableStateOf<List<String>>(emptyList())
        private set
    var apagadoEm by mutableStateOf(prefs.getLong("apagado_em", 0L))
        private set
    var kmDesdeApagar by mutableStateOf<Int?>(null)
        private set
    var cambio by mutableStateOf(lerCambio())
        private set
    var tempCambio by mutableStateOf<Int?>(null)
    /** Mensagem do último teste (catalisador, modo 06 ou câmbio) e se há um teste rodando. */
    var situacao by mutableStateOf<String?>(null)
    var ocupado by mutableStateOf(false)

    init { carregar() }

    // ---------- freeze frame ----------

    fun jaTemFreeze(codigo: String) = freezeFrames.any { it.codigo == codigo && System.currentTimeMillis() - it.em < 7 * 86_400_000L }

    fun registrarFreeze(f: FreezeFrame) {
        freezeFrames.add(0, f)
        while (freezeFrames.size > 10) freezeFrames.removeAt(freezeFrames.lastIndex)
        gravarLista("freeze", freezeFrames) { JSONObject().put("c", it.codigo).put("e", it.em).put("i", JSONArray(it.itens.map { p -> "${p.first}|${p.second}" })) }
        Nuvem.obter(ctx).avisar("freeze", "🧊 Freeze frame de ${f.codigo} (dados no instante da falha): " + f.itens.joinToString(" · ") { "${it.first} ${it.second}" })
    }

    // ---------- termostato ----------

    private var inicioAq = 0L
    private var tempIni = 0
    private var marcado = true
    private var sessao = false

    /** A cada leitura do OBD: se o motor ligou frio (até 45 °C), mede quanto tempo leva para chegar a 80 °C. */
    fun termostato(temp: Int?, rpm: Int?) {
        val agora = System.currentTimeMillis()
        if ((rpm ?: 0) <= 400) { inicioAq = 0L; marcado = true; sessao = false; return }
        temp ?: return
        if (!sessao) { sessao = true; if (temp <= 45) { inicioAq = agora; tempIni = temp; marcado = false } }
        if (marcado || inicioAq == 0L) return
        if (temp >= 80) {
            val s = ((agora - inicioAq) / 1000).toInt()
            marcado = true
            val base = aquecimentos.mapNotNull { it.segundos }.sorted()
            registrarAquecimento(Aquecimento(agora, s, tempIni))
            if (base.size >= 3) {
                val mediana = base[base.size / 2]
                if (s > mediana * 1.6 && s > 15 * 60) Nuvem.obter(ctx).avisar("termostato", "🌡️ O motor demorou ${s / 60} min para chegar a 80 °C (o normal deste carro: ~${mediana / 60} min). Pode ser o termostato preso aberto: aumenta o consumo e o desgaste.")
            }
        } else if (agora - inicioAq >= 25 * 60_000L) {
            marcado = true
            registrarAquecimento(Aquecimento(agora, null, tempIni))
            Nuvem.obter(ctx).avisar("termostato", "🌡️ O motor não chegou a 80 °C em 25 min de uso (partiu com $tempIni °C). Possível termostato preso aberto.")
        }
    }

    private fun registrarAquecimento(a: Aquecimento) {
        aquecimentos.add(0, a)
        while (aquecimentos.size > 20) aquecimentos.removeAt(aquecimentos.lastIndex)
        gravarLista("aquecimentos", aquecimentos) { JSONObject().put("e", it.em).put("s", it.segundos ?: -1).put("t", it.tempInicial) }
    }

    /** Mediana do tempo para chegar a 80 °C (s), ou null sem dados. */
    fun medianaAquecimento(): Int? = aquecimentos.mapNotNull { it.segundos }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

    // ---------- dreno de bateria ----------

    private var motorEraLigado = false
    private val recentesDesligado = ArrayDeque<Float>()

    fun dreno(tensao: Float?, rpm: Int?) {
        val agora = System.currentTimeMillis()
        val ligado = (rpm ?: 0) > 400
        if (!ligado) {
            tensao?.let { recentesDesligado.addLast(it); while (recentesDesligado.size > 5) recentesDesligado.removeFirst() }
            if (motorEraLigado) { prefs.edit().putLong("desligou_em", agora).remove("v60").apply() }
            val desligou = prefs.getLong("desligou_em", 0L)
            if (desligou > 0 && !prefs.contains("v60") && agora - desligou >= 60 * 60_000L && tensao != null) prefs.edit().putFloat("v60", tensao).apply()
        } else if (!motorEraLigado) {
            val desligou = prefs.getLong("desligou_em", 0L)
            if (desligou > 0 && recentesDesligado.size >= 2) {
                val horas = (agora - desligou) / 3_600_000.0
                val vStart = recentesDesligado.max()
                val v60 = if (prefs.contains("v60")) prefs.getFloat("v60", 0f) else null
                val taxa = if (v60 != null && horas >= 8) (v60 - vStart) / (horas - 1) else null
                val suspeito = (horas >= 6 && vStart < 12.2f) || (taxa != null && taxa >= 0.02)
                if (horas >= 1) {
                    drenos.add(0, EventoDreno(agora, horas, vStart, taxa, suspeito))
                    while (drenos.size > 20) drenos.removeAt(drenos.lastIndex)
                    gravarLista("drenos", drenos) { JSONObject().put("e", it.em).put("h", it.horasParado).put("v", it.tensaoPartida.toDouble()).put("t", it.taxaVh ?: JSONObject.NULL).put("s", it.suspeito) }
                    if (suspeito) Nuvem.obter(ctx).avisar("bateria", "🔋 Possível dreno de bateria: depois de %.0f h parado, a tensão estava em %.1f V%s. Confira alarme, som e acessórios ligados direto na bateria.".format(br, horas, vStart, taxa?.let { " (queda de %.0f mV/h)".format(br, it * 1000) } ?: ""))
                }
            }
            recentesDesligado.clear()
        }
        motorEraLigado = ligado
    }

    fun ultimaTaxaDreno(): Double? = drenos.firstOrNull { it.taxaVh != null }?.taxaVh

    // ---------- marcha lenta irregular ----------

    private val rpmsParado = ArrayList<Pair<Long, Int>>()
    private var avaliacoesIrregulares = 0
    private var ultimaAvaliacao = 0L

    fun marchaLenta(rpm: Int?, vel: Int?, temp: Int?) {
        val agora = System.currentTimeMillis()
        val ok = rpm != null && rpm in 500..1200 && (vel ?: 0) == 0 && (temp ?: 0) >= 80
        if (!ok) { rpmsParado.clear(); return }
        rpmsParado += agora to rpm!!
        if (rpmsParado.size > 120) rpmsParado.removeAt(0)
        val duracao = agora - rpmsParado.first().first
        if (rpmsParado.size >= 40 && duracao >= 25_000L && agora - ultimaAvaliacao >= 10 * 60_000L) {
            val v = rpmsParado.map { it.second }
            val media = v.average()
            val desvio = sqrt(v.sumOf { (it - media) * (it - media) } / v.size)
            val faixa = v.max() - v.min()
            val irregular = desvio > 45 || faixa > 180
            ultimaAvaliacao = agora
            marchaLenta = ResultadoMarchaLenta(agora, media.toInt(), desvio, faixa, irregular)
            avaliacoesIrregulares = if (irregular) avaliacoesIrregulares + 1 else 0
            if (avaliacoesIrregulares == 3) Nuvem.obter(ctx).avisar("marcha_lenta", "⚙️ Marcha lenta irregular nas últimas 3 medições (oscila ±%.0f rpm, faixa de %d rpm). Pode ser vela, bobina ou entrada falsa de ar. Se o ar-condicionado estiver ligando e desligando, é normal.".format(br, desvio, faixa))
            rpmsParado.clear()
        }
    }

    // ---------- códigos apagados ----------

    fun verificarApagados(km: Int?) {
        km ?: return
        kmDesdeApagar = km
        val ultimo = if (prefs.contains("km_apagar")) prefs.getInt("km_apagar", 0) else null
        if (ultimo != null && km < ultimo - 1) {
            apagadoEm = System.currentTimeMillis()
            prefs.edit().putLong("apagado_em", apagadoEm).apply()
            Nuvem.obter(ctx).avisar("codigos_apagados", "🧹 Os códigos de falha foram apagados (ou a bateria foi desligada): $km km rodados desde então. Os monitores de emissões levam alguns dias de uso para ficarem prontos; evite a vistoria antes disso.")
        }
        prefs.edit().putInt("km_apagar", km).apply()
    }

    // ---------- catalisador ----------

    /** Compara a oscilação da sonda antes e depois do catalisador: depois do catalisador saudável ela fica quase parada. */
    fun analisarCatalisador(antes: List<Double>, depois: List<Double>): ResultadoCatalisador {
        fun cruzamentos(v: List<Double>, ref: Double): Int { var n = 0; for (i in 1 until v.size) if ((v[i - 1] - ref) * (v[i] - ref) < 0) n++; return n }
        val o1 = cruzamentos(antes, antes.average())
        val o2 = cruzamentos(depois, 0.45)
        val r = if (o1 >= 10) o2.toDouble() / o1 else null
        val parecer = when {
            r == null -> "Dados insuficientes: a sonda de antes quase não oscilou (motor frio ou fora de malha fechada). Repita com o motor quente, em velocidade constante."
            r < 0.35 -> "Catalisador eficiente: a sonda de depois oscila bem menos que a de antes."
            r < 0.65 -> "Atenção: a sonda de depois está oscilando bastante. O catalisador pode estar perdendo eficiência."
            else -> "Suspeito: a sonda de depois oscila quase como a de antes. Compatível com catalisador gasto (P0420)."
        }
        val res = ResultadoCatalisador(System.currentTimeMillis(), r, o1, o2, parecer)
        catalisador = res
        prefs.edit().putString("catalisador", JSONObject().put("e", res.em).put("r", r ?: JSONObject.NULL).put("a", o1).put("d", o2).put("p", parecer).toString()).apply()
        if (r != null && r >= 0.65) Nuvem.obter(ctx).avisar("catalisador", "🧪 Teste do catalisador: $parecer (razão %.2f)".format(br, r))
        return res
    }

    // ---------- modo 06 e câmbio ----------

    fun definirModo06(linhas: List<String>) { modo06 = linhas }

    fun salvarCambio(c: ConfigCambio) {
        cambio = c
        prefs.edit().putString("cambio", JSONObject().put("h", c.cabecalho).put("c", c.comando).put("b", c.bytes).put("m", c.multiplicador).put("d", c.deslocamento).toString()).apply()
    }
    private fun lerCambio(): ConfigCambio = runCatching {
        val o = JSONObject(prefs.getString("cambio", null) ?: return ConfigCambio())
        ConfigCambio(o.getString("h"), o.getString("c"), o.getInt("b"), o.getDouble("m"), o.getDouble("d"))
    }.getOrDefault(ConfigCambio())

    // ---------- persistência ----------

    private fun <T> gravarLista(chave: String, itens: List<T>, json: (T) -> JSONObject) {
        val arr = JSONArray(); itens.forEach { arr.put(json(it)) }
        prefs.edit().putString(chave, arr.toString()).apply()
    }

    private fun carregar() {
        fun arr(k: String) = runCatching { JSONArray(prefs.getString(k, "[]")) }.getOrDefault(JSONArray())
        arr("freeze").let { a -> for (i in 0 until a.length()) runCatching {
            val o = a.getJSONObject(i); val it = o.getJSONArray("i")
            freezeFrames += FreezeFrame(o.getString("c"), o.getLong("e"), (0 until it.length()).map { j -> it.getString(j).split("|").let { p -> p[0] to p[1] } })
        } }
        arr("aquecimentos").let { a -> for (i in 0 until a.length()) runCatching { val o = a.getJSONObject(i); aquecimentos += Aquecimento(o.getLong("e"), o.getInt("s").takeIf { s -> s >= 0 }, o.getInt("t")) } }
        arr("drenos").let { a -> for (i in 0 until a.length()) runCatching { val o = a.getJSONObject(i); drenos += EventoDreno(o.getLong("e"), o.getDouble("h"), o.getDouble("v").toFloat(), if (o.isNull("t")) null else o.getDouble("t"), o.getBoolean("s")) } }
        runCatching { JSONObject(prefs.getString("catalisador", null) ?: return@runCatching).let { o -> catalisador = ResultadoCatalisador(o.getLong("e"), if (o.isNull("r")) null else o.getDouble("r"), o.getInt("a"), o.getInt("d"), o.getString("p")) } }
    }

    fun quando(ms: Long): String = fmt.format(Date(ms))

    companion object {
        @Volatile private var instancia: DiagnosticoAvancado? = null
        fun obter(ctx: Context): DiagnosticoAvancado = instancia ?: synchronized(this) { instancia ?: DiagnosticoAvancado(ctx.applicationContext).also { instancia = it } }
    }
}
