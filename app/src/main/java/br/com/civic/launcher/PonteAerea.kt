package br.com.civic.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.Executors

/** Um evento do calendário do Ponte Aérea (voo, viagem, feriado, evento, presencial, família ou fim de semana com as filhas). */
data class EventoCal(
    val id: String, val tipo: String, val titulo: String, val subtitulo: String?,
    val data: String, val dataFim: String?, val horaInicio: String?, val horaFim: String?,
    val passageiro: String?, val diaInteiro: Boolean, val cor: String,
) {
    /** O evento cobre este dia? (de `data` até `dataFim`, inclusive) */
    fun cobre(dia: LocalDate): Boolean {
        val ini = runCatching { LocalDate.parse(data) }.getOrNull() ?: return false
        val fim = dataFim?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: ini
        return !dia.isBefore(ini) && !dia.isAfter(fim)
    }
}

/**
 * Calendário do Ponte Aérea (API do Launcher, somente leitura, versão 1): GET /api/launcher/calendario com `Authorization: Bearer <token>`.
 *  - O token NÃO vai no build: o APK é publicado numa Release pública, então o token é informado em Config. > Calendário e fica só no aparelho.
 *  - Usa ETag (304 = nada mudou) e guarda a última resposta boa para mostrar sem internet.
 *  - Não repete na hora em 401 (token errado) nem em 429 (limite); respeita o intervalo mínimo entre consultas (a API aceita 60 em 10 min).
 *  - O token nunca vai para log nem para a tela (só o início, mascarado).
 */
class PonteAerea private constructor(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("ponte_aerea", Context.MODE_PRIVATE)
    private val principal = Handler(Looper.getMainLooper())
    private val fila = Executors.newSingleThreadExecutor()

    var baseUrl by mutableStateOf(prefs.getString("base", PADRAO_BASE) ?: PADRAO_BASE)
        private set
    private var token: String = prefs.getString("token", "") ?: ""
    var temToken by mutableStateOf(token.isNotEmpty())
        private set

    var eventos by mutableStateOf<List<EventoCal>>(emptyList())
        private set
    var passageiros by mutableStateOf<List<String>>(emptyList())
        private set
    var proximoVoo by mutableStateOf<EventoCal?>(null)
        private set
    var atualizadoEm by mutableStateOf(prefs.getLong("atualizado_em", 0L))
        private set
    var carregando by mutableStateOf(false)
        private set
    /** Mensagem para a tela (erro, aviso ou "sem internet, mostrando o que foi guardado"); nulo = tudo certo. */
    var situacao by mutableStateOf<String?>(null)
        private set
    /** Período já carregado ("YYYY-MM-DD", "YYYY-MM-DD"), para saber se precisa buscar de novo ao mudar de mês. */
    var periodo by mutableStateOf<Pair<LocalDate, LocalDate>?>(null)
        private set

    private var ultimaConsulta = 0L
    private var bloqueadoAte = 0L

    init { runCatching { prefs.getString("cache", null)?.let { aplicar(JSONObject(it)) } } }

    /** Início do token mascarado, para mostrar na Config. sem expor o resto. */
    fun tokenMascarado(): String = if (token.length > 14) token.take(12) + "••••••••" else if (token.isNotEmpty()) "••••••••" else ""

    fun salvarConfig(novaBase: String, novoToken: String?) {
        val b = novaBase.trim().trimEnd('/').ifEmpty { PADRAO_BASE }
        baseUrl = b; prefs.edit().putString("base", b).apply()
        if (novoToken != null && novoToken.isNotBlank()) {
            token = novoToken.trim(); temToken = true
            prefs.edit().putString("token", token).remove("etag").apply()
            bloqueadoAte = 0L; situacao = null
        }
    }

    fun removerToken() {
        token = ""; temToken = false
        prefs.edit().remove("token").remove("etag").remove("cache").remove("atualizado_em").apply()
        eventos = emptyList(); passageiros = emptyList(); proximoVoo = null; atualizadoEm = 0L; periodo = null
    }

    /**
     * Busca os eventos de [de] a [ate] (no máximo 400 dias). [forcar] ignora o intervalo mínimo de 20 s (botão do usuário);
     * mesmo assim respeita o bloqueio depois de 401 ou 429.
     */
    fun atualizar(de: LocalDate, ate: LocalDate, forcar: Boolean = false) {
        if (!temToken) { situacao = "Informe o token da API em Config. > Calendário."; return }
        val agora = System.currentTimeMillis()
        if (carregando) return
        if (agora < bloqueadoAte) return
        if (!forcar && agora - ultimaConsulta < 20_000L && periodo == de to ate) return
        ultimaConsulta = agora
        carregando = true
        val base = baseUrl; val tk = token
        val limite = if (ate.isAfter(de.plusDays(399))) de.plusDays(399) else ate
        fila.execute {
            var resultado: Triple<Int, String?, String?> = Triple(0, null, null) // código, corpo, etag
            var retryApos = 0L
            runCatching {
                val c = URL("$base/api/launcher/calendario?de=$de&ate=$limite").openConnection() as HttpURLConnection
                c.requestMethod = "GET"
                c.setRequestProperty("Authorization", "Bearer $tk")
                c.setRequestProperty("Accept", "application/json")
                // O ETag só vale para o mesmo período.
                val etag = prefs.getString("etag", null); val etagPeriodo = prefs.getString("etag_periodo", null)
                if (etag != null && etagPeriodo == "$de|$limite") c.setRequestProperty("If-None-Match", etag)
                c.connectTimeout = 10_000; c.readTimeout = 10_000
                val code = c.responseCode
                retryApos = c.getHeaderField("Retry-After")?.toLongOrNull() ?: 0L
                val corpo = if (code == 200) c.inputStream.bufferedReader().readText() else null
                resultado = Triple(code, corpo, c.getHeaderField("ETag"))
                c.disconnect()
            }
            val (code, corpo, etag) = resultado
            principal.post {
                carregando = false
                when {
                    code == 200 && corpo != null -> runCatching {
                        val j = JSONObject(corpo)
                        aplicar(j)
                        periodo = de to limite
                        atualizadoEm = System.currentTimeMillis(); situacao = null
                        prefs.edit().putString("cache", corpo).putLong("atualizado_em", atualizadoEm).putString("etag_periodo", "$de|$limite")
                            .apply { if (etag != null) putString("etag", etag) else remove("etag") }.apply()
                    }.onFailure { situacao = "Resposta inesperada do Ponte Aérea." }
                    code == 304 -> { periodo = de to limite; atualizadoEm = System.currentTimeMillis(); situacao = null; prefs.edit().putLong("atualizado_em", atualizadoEm).apply() }
                    code == 401 -> { bloqueadoAte = System.currentTimeMillis() + 5 * 60_000L; situacao = "Token recusado pelo Ponte Aérea. Confira o token em Config. > Calendário." }
                    code == 429 -> { bloqueadoAte = System.currentTimeMillis() + maxOf(retryApos, 60L) * 1000L; situacao = "Limite de consultas atingido; tente de novo em alguns minutos." }
                    code == 400 -> situacao = "Período inválido para o Ponte Aérea."
                    code >= 500 -> situacao = "O Ponte Aérea está com problema agora (erro $code)."
                    else -> situacao = if (eventos.isEmpty()) "Sem conexão com o Ponte Aérea." else "Sem conexão: mostrando o que foi guardado."
                }
            }
        }
    }

    private fun aplicar(j: JSONObject) {
        val arr = j.optJSONArray("eventos")
        val lista = ArrayList<EventoCal>()
        if (arr != null) for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            fun s(k: String) = if (o.isNull(k)) null else o.optString(k).ifEmpty { null }
            lista.add(EventoCal(o.optString("id"), o.optString("tipo"), o.optString("titulo"), s("subtitulo"), o.optString("data"), s("data_fim"),
                s("hora_inicio"), s("hora_fim"), s("passageiro"), o.optBoolean("dia_inteiro"), o.optString("cor", "#6B7280")))
        }
        eventos = lista
        passageiros = j.optJSONArray("passageiros")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("nome")?.ifEmpty { null } } } ?: emptyList()
        proximoVoo = j.optJSONObject("proximo_voo")?.let { o ->
            lista.firstOrNull { it.id == o.optString("id") }
        }
        runCatching {
            val p = j.optJSONObject("periodo"); if (p != null) periodo = LocalDate.parse(p.getString("de")) to LocalDate.parse(p.getString("ate"))
        }
    }

    companion object {
        const val PADRAO_BASE = "https://ponte-area.vercel.app"
        @Volatile private var instancia: PonteAerea? = null
        fun obter(ctx: Context): PonteAerea = instancia ?: synchronized(this) { instancia ?: PonteAerea(ctx.applicationContext).also { instancia = it } }
    }
}
