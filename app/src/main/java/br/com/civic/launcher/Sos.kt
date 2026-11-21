package br.com.civic.launcher

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import br.com.civic.launcher.obd.ObdMonitor
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class ContatoSos(val nome: String, val telefone: String, val id: String = java.util.UUID.randomUUID().toString(), val atualizadoEm: Long = 0L, val ativo: Boolean = true)

/** Contato como veio da nuvem (a Casa Tech cadastra, edita, pausa e remove). */
data class ContatoNuvem(val contato: ContatoSos, val removido: Boolean)

/**
 * Botão SOS: depois de uma contagem curta (5 s, com "Cancelar") manda ao Telegram vinculado e, por SMS, a cada contato
 * cadastrado: localização com link do mapa, hora, identificação do carro e o que o OBD tiver (velocidade, combustível, falhas).
 * O texto diz que foi um acionamento manual: **nunca afirma colisão**.
 *
 * Honestidade do resultado: só diz "enviado" depois da confirmação (resposta da nuvem / retorno do sistema de SMS).
 * Sem telefonia ou sem permissão de SMS, informa que o canal está indisponível e segue pelo Telegram.
 */
class Sos private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("sos", Context.MODE_PRIVATE)
    private val principal = Handler(Looper.getMainLooper())

    val contatos = mutableStateListOf<ContatoSos>()
    /** Segundos restantes da contagem (null = sem contagem). */
    var contagem by mutableStateOf<Int?>(null)
        private set
    /** Linhas do resultado do último acionamento/teste (null = nada a mostrar). */
    val resultado = mutableStateListOf<String>()
    var mostrandoResultado by mutableStateOf(false)
    private var ultimoTexto: String? = null
    private var ultimoTeste = false

    init {
        runCatching {
            val arr = JSONArray(prefs.getString("contatos", "[]"))
            for (i in 0 until arr.length()) arr.getJSONObject(i).let { contatos.add(ContatoSos(it.getString("nome"), it.getString("tel"), it.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() }, it.optLong("ue", 0L), it.optBoolean("ativo", true))) }
        }
        val rec = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val nome = i.getStringExtra("nome") ?: return
                val ok = resultCode == Activity.RESULT_OK
                principal.post { linha(if (ok) "SMS enviado a $nome." else "SMS NÃO enviado a $nome (código $resultCode). Tente de novo ou ligue.") }
            }
        }
        runCatching { ContextCompat.registerReceiver(ctx, rec, IntentFilter(ACAO_SMS), ContextCompat.RECEIVER_NOT_EXPORTED) }
    }

    fun temTelefonia() = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    fun temPermissaoSms() = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun adicionar(nome: String, telefone: String): Boolean {
        val tel = telefone.filter { it.isDigit() || it == '+' }
        if (tel.length < 8 || nome.isBlank()) return false
        contatos.add(ContatoSos(nome.trim(), tel, atualizadoEm = System.currentTimeMillis())); salvar(); sincronizarContatos(); return true
    }
    fun remover(c: ContatoSos) {
        contatos.remove(c); salvar()
        val r = runCatching { JSONArray(prefs.getString("removidos", "[]")) }.getOrDefault(JSONArray()); r.put(c.id)
        prefs.edit().putString("removidos", r.toString()).apply()
        sincronizarContatos()
    }
    private fun salvar() {
        val arr = JSONArray(); contatos.forEach { arr.put(JSONObject().put("nome", it.nome).put("tel", it.telefone).put("id", it.id).put("ue", it.atualizadoEm).put("ativo", it.ativo)) }
        prefs.edit().putString("contatos", arr.toString()).apply()
    }

    // ---------- sincronização com a Casa Tech (vale a edição mais recente) ----------

    private val remotoEm = HashMap<String, Long>()

    /** Junta os contatos que vieram da nuvem. Devolve true se algo mudou aqui. */
    fun aplicarDaNuvem(remotos: List<ContatoNuvem>): Boolean {
        val removidosLocais = runCatching { JSONArray(prefs.getString("removidos", "[]")) }.getOrDefault(JSONArray()).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        var mudou = false
        remotos.forEach { (r, removido) ->
            remotoEm[r.id] = r.atualizadoEm
            val i = contatos.indexOfFirst { it.id == r.id }
            when {
                removido -> if (i >= 0 && r.atualizadoEm >= contatos[i].atualizadoEm) { contatos.removeAt(i); mudou = true }
                r.id in removidosLocais -> {}
                i < 0 -> { contatos.add(r); mudou = true }
                r.atualizadoEm > contatos[i].atualizadoEm -> { contatos[i] = r; mudou = true }
            }
        }
        if (mudou) salvar()
        return mudou
    }

    /** Envia à nuvem só os contatos que o aparelho editou depois da versão de lá (e os removidos aqui). */
    fun sincronizarContatos() {
        val nuvem = Nuvem.obter(ctx)
        val agora = System.currentTimeMillis()
        fun iso(ms: Long) = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(ms))
        val removidos = runCatching { JSONArray(prefs.getString("removidos", "[]")) }.getOrDefault(JSONArray())
        val lote = JSONArray()
        contatos.filter { it.atualizadoEm > (remotoEm[it.id] ?: -1L) }.forEach {
            lote.put(JSONObject().put("dispositivo", nuvem.token).put("id", it.id).put("nome", it.nome).put("telefone", it.telefone).put("ativo", it.ativo)
                .put("removido", false).put("atualizado_em", iso(if (it.atualizadoEm > 0) it.atualizadoEm else agora)))
        }
        for (i in 0 until removidos.length()) lote.put(JSONObject().put("dispositivo", nuvem.token).put("id", removidos.getString(i)).put("nome", "(removido)").put("telefone", "00000000")
            .put("ativo", false).put("removido", true).put("atualizado_em", iso(agora)))
        if (lote.length() == 0) return
        val n = removidos.length()
        nuvem.enviarJson("/rest/v1/civic_contatos_sos?on_conflict=dispositivo,id", lote.toString(), "resolution=merge-duplicates") { ok ->
            if (ok && n > 0) prefs.edit().putString("removidos", "[]").apply()
        }
    }

    // ---------- acionamento ----------

    /** Botão SOS: inicia a contagem de 5 s. */
    fun acionar() {
        if (contagem != null) return
        contagem = 5
        Voz.obter(ctx).falar("S O S em 5 segundos. Toque em cancelar para parar.", forcar = true)
        principal.postDelayed(tique, 1_000)
    }

    private val tique = object : Runnable {
        override fun run() {
            val restante = (contagem ?: return) - 1
            if (restante <= 0) { contagem = null; enviar(teste = false) } else { contagem = restante; principal.postDelayed(this, 1_000) }
        }
    }

    fun cancelar() {
        if (contagem == null) return
        contagem = null; principal.removeCallbacks(tique)
        Nuvem.obter(ctx).avisar("sos", "🆘 SOS cancelado pelo motorista antes do envio.")
    }

    fun enviarAgora() { if (contagem == null) return; contagem = null; principal.removeCallbacks(tique); enviar(teste = false) }

    /** Config.: manda uma mensagem de TESTE aos contatos e ao Telegram (sem localização real). */
    fun testar() = enviar(teste = true)

    fun tentarNovamente() { ultimoTexto?.let { enviarTexto(it, ultimoTeste) } }

    private fun enviar(teste: Boolean) {
        val config = Configuracoes.obter(ctx)
        val l = runCatching { LocalEstacionamento.obter(ctx).posicaoRecente() }.getOrNull()
        val d = ObdMonitor.obter(ctx).dados
        val hora = java.text.SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(java.util.Date())
        val carro = listOf(config.nomeCarro, config.modeloEAno, config.placaFormatada).filter { it.isNotBlank() }.joinToString(" · ")
        val sb = StringBuilder()
        sb.append(if (teste) "🧪 TESTE do SOS (não é emergência)\n" else "🆘 SOS: o motorista pediu ajuda pelo botão do Civic Renatin.\n")
        sb.append("🚗 $carro\n🕒 $hora\n")
        if (l != null) {
            val idade = ((System.currentTimeMillis() - l.time) / 60_000).coerceAtLeast(0)
            sb.append("📍 %.5f, %.5f".format(Locale.US, l.latitude, l.longitude)).append(if (idade >= 5) " (sinal de GPS de $idade min atrás)" else "").append('\n')
            sb.append("🗺️ https://maps.google.com/?q=%.6f,%.6f\n".format(Locale.US, l.latitude, l.longitude))
        } else sb.append("📍 Sem posição de GPS no momento.\n")
        val dados = listOfNotNull(d.velocidade?.let { "$it km/h" }, d.nivelCombustivel?.let { "combustível $it%" }, d.tempMotor?.let { "motor $it °C" }).joinToString(" · ")
        if (dados.isNotEmpty()) sb.append("📊 $dados\n")
        if (d.codigos.isNotEmpty()) sb.append("🔧 Falhas registradas: ${d.codigos.joinToString(", ")}\n")
        if (!teste) sb.append("ℹ️ Acionamento manual: não indica colisão confirmada.")
        enviarTexto(sb.toString().trim(), teste)
    }

    private fun enviarTexto(texto: String, teste: Boolean) {
        ultimoTexto = texto; ultimoTeste = teste
        resultado.clear(); mostrandoResultado = true
        linha(if (teste) "Enviando o teste…" else "Enviando o SOS…")
        // Telegram (o aviso sai mesmo com os avisos do app desligados) e registro remoto em civic_eventos.
        Nuvem.obter(ctx).avisar(if (teste) "sos_teste" else "sos", texto, forcar = true) { enviado, vinculado, semConexao ->
            principal.post {
                linha(when {
                    enviado -> "Telegram: enviado."
                    semConexao -> "Telegram: NÃO enviado (sem internet). Toque em \"Tentar de novo\" quando houver conexão."
                    !vinculado -> "Telegram: NÃO enviado (o Telegram não está vinculado)."
                    else -> "Telegram: NÃO enviado (falha no serviço)."
                })
            }
        }
        // SMS aos contatos.
        val ativos = contatos.filter { it.ativo }
        if (ativos.isEmpty()) { linha("SMS: nenhum contato ativo cadastrado."); return }
        when {
            !temTelefonia() -> linha("SMS: indisponível (esta multimídia não tem telefonia). Só o Telegram foi usado.")
            !temPermissaoSms() -> linha("SMS: sem permissão. Libere em Config. > SOS e contatos de emergência.")
            else -> ativos.forEachIndexed { i, c -> enviarSms(c, texto, teste, i) }
        }
    }

    private fun enviarSms(c: ContatoSos, texto: String, teste: Boolean, i: Int) {
        runCatching {
            val link = Regex("https://maps\\.google\\.com\\S+").find(texto)?.value
            val prefixo = if (teste) "TESTE do SOS Civic (nao e emergencia)" else "SOS: preciso de ajuda. ${Configuracoes.obter(ctx).nomeCarro}"
            val corpo = if (link != null) "$prefixo. Local: $link" else "$prefixo. Sem GPS no momento."
            val sms = ctx.getSystemService(SmsManager::class.java)
            val pi = PendingIntent.getBroadcast(ctx, i, Intent(ACAO_SMS).setPackage(ctx.packageName).putExtra("nome", c.nome), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val partes = sms.divideMessage(corpo)
            if (partes.size == 1) sms.sendTextMessage(c.telefone, null, corpo, pi, null)
            else sms.sendMultipartTextMessage(c.telefone, null, partes, ArrayList<PendingIntent>().apply { partes.forEach { add(pi) } }, null)
            linha("SMS para ${c.nome}: aguardando confirmação do sistema…")
        }.onFailure { linha("SMS NÃO enviado a ${c.nome}: ${it.message ?: "erro"}") }
    }

    private fun linha(t: String) {
        resultado.add(t)
        if (resultado.size > 20) resultado.removeAt(0)
    }

    companion object {
        private const val ACAO_SMS = "br.com.civic.launcher.SOS_SMS"
        @Volatile private var instancia: Sos? = null
        fun obter(ctx: Context): Sos = instancia ?: synchronized(this) { instancia ?: Sos(ctx.applicationContext).also { instancia = it } }
    }
}
