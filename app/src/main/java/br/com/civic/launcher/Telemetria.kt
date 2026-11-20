package br.com.civic.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Telemetria para o dashboard da Casa Tech (Supabase):
 *  - **Eventos de ignição** (civic_ignicao): motor ligou/desligou e multimídia ligou/desligou, com hora e posição.
 *  - **Posição e trajeto** (civic_posicoes): um ponto a cada 15 s com o motor ligado ou andando.
 *  - **Status online** (civic_status): o carro "bate o ponto" a cada 60 s com o motor ligado (5 min desligado).
 * Tudo é guardado no aparelho primeiro e enviado em pacotes pequenos a cada 30 s; sem internet, fica na fila
 * (limitada) e sai quando a conexão voltar.
 */
class Telemetria private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("telemetria", Context.MODE_PRIVATE)
    private val principal = Handler(Looper.getMainLooper())

    private var pontos = ler("pontos")
    private var eventos = ler("eventos")

    private var motorLigado = false
    private var semGiroDesde = 0L
    private var viagem = prefs.getLong("viagem", 0L)
    private var ultimoPonto = 0L
    private var ultimoStatus = 0L
    private var posicao: Location? = null
    private var fonteRede = false
    private var ultimoGps = 0L
    private var velocidade = 0f
    private var tensao: Float? = null
    private var enviando = false
    /** Última leitura com giro vindo do OBD; sem OBD (Fit) o uso do carro é detectado pelo GPS. */
    private var ultimaObd = 0L
    private var porGps = false
    private var ultimoMovimento = 0L

    private val aoDesligar = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SHUTDOWN || i.action == "android.intent.action.QUICKBOOT_POWEROFF") {
                if (motorLigado) { registrar("motor_desligou"); if (porGps) EstadoCarro.obter(ctx).usoSemObd(false) }
                motorLigado = false; porGps = false
                registrar("multimidia_desligou")
                enviar(forcarStatus = true)
            }
        }
    }

    private val ciclo = object : Runnable {
        override fun run() {
            // Sem OBD: 5 min parado depois de andar = carro desligado.
            if (porGps && motorLigado && System.currentTimeMillis() - ultimoMovimento >= 5 * 60_000L) desligouSemObd()
            enviar(); principal.postDelayed(this, 30_000)
        }
    }

    init {
        registrar("multimidia_ligou")
        runCatching { ctx.registerReceiver(aoDesligar, IntentFilter().apply { addAction(Intent.ACTION_SHUTDOWN); addAction("android.intent.action.QUICKBOOT_POWEROFF") }) }
        principal.postDelayed(ciclo, 15_000)
    }

    /** O app foi fechado com o motor ligado: registra o desligamento e envia já. */
    fun aoFecharApp() {
        if (motorLigado) { motorLigado = false; registrar("motor_desligou"); if (porGps) EstadoCarro.obter(ctx).usoSemObd(false) }
        porGps = false
        enviar(forcarStatus = true)
    }

    /** A cada leitura do OBD: detecta motor ligando/desligando. */
    fun amostra(rpm: Int?, tensaoAgora: Float?, demonstracao: Boolean) {
        if (demonstracao) return
        tensaoAgora?.let { tensao = it }
        val agora = System.currentTimeMillis()
        if (rpm != null) { ultimaObd = agora; if (porGps) { porGps = false; motorLigado = false } }
        if ((rpm ?: 0) > 400) {
            semGiroDesde = 0L
            if (!motorLigado) {
                motorLigado = true
                viagem = agora; prefs.edit().putLong("viagem", viagem).apply()
                registrar("motor_ligou")
                enviar(forcarStatus = true)
            }
        } else if (motorLigado) {
            if (semGiroDesde == 0L) semGiroDesde = agora
            else if (agora - semGiroDesde >= 10_000) { // 10 s sem giro = desligou (uma calada rápida não conta)
                motorLigado = false; semGiroDesde = 0L
                registrar("motor_desligou")
                enviar(forcarStatus = true)
            }
        }
    }

    private fun desligouSemObd() {
        motorLigado = false; porGps = false
        registrar("motor_desligou")
        EstadoCarro.obter(ctx).usoSemObd(false)
        // Reserva: se a multimídia não desligar com a chave, avisa onde parou mesmo assim (não repete se já avisou nessa vaga).
        runCatching { LocalEstacionamento.obter(ctx).aoFecharApp() }
        enviar(forcarStatus = true)
    }

    /** A cada posição de GPS: guarda um ponto do trajeto a cada 15 s (com o motor ligado ou andando). */
    /** Posição pela rede (Wi-Fi/antenas), usada no status só quando o GPS não dá sinal há mais de 5 min (ex.: tablet em casa). */
    fun posicaoRede(local: Location) {
        if (System.currentTimeMillis() - ultimoGps > 5 * 60_000L) { posicao = local; fonteRede = true; velocidade = 0f }
    }

    fun posicao(local: Location) {
        posicao = local; fonteRede = false; ultimoGps = System.currentTimeMillis()
        velocidade = if (local.hasSpeed()) local.speed * 3.6f else 0f
        val agora = System.currentTimeMillis()
        // Sem OBD (ex.: Honda Fit): andar a mais de 8 km/h marca o carro como "em uso" (ligou) e 5 min parado como desligou.
        if (agora - ultimaObd > 60_000L) {
            if (velocidade > 8f) {
                ultimoMovimento = agora
                if (!motorLigado) {
                    motorLigado = true; porGps = true
                    viagem = agora; prefs.edit().putLong("viagem", viagem).apply()
                    registrar("motor_ligou")
                    EstadoCarro.obter(ctx).usoSemObd(true)
                    enviar(forcarStatus = true)
                }
            } else if (velocidade > 3f) ultimoMovimento = agora
        }
        if ((motorLigado || velocidade > 3f) && agora - ultimoPonto >= 15_000) {
            ultimoPonto = agora
            pontos.put(JSONObject().put("em", iso(agora)).put("lat", local.latitude).put("lon", local.longitude)
                .put("vel", Math.round(velocidade * 10) / 10.0).put("viagem", viagem))
            limitar(); gravar("pontos", pontos)
        }
    }

    /** Simulação (demonstração do OBD): registra o "motor ligou" e o "motor desligou" da viagem simulada e envia já. */
    fun simularViagem(inicio: Long, fim: Long) {
        listOf("motor_ligou" to inicio, "motor_desligou" to fim).forEach { (tipo, em) ->
            val o = JSONObject().put("tipo", tipo).put("em", iso(em)).put("viagem", inicio)
            posicao?.let { o.put("lat", it.latitude).put("lon", it.longitude) }
            eventos.put(o)
        }
        limitar(); gravar("eventos", eventos)
        enviar(forcarStatus = true)
    }

    private fun registrar(tipo: String) {
        val o = JSONObject().put("tipo", tipo).put("em", iso(System.currentTimeMillis())).put("viagem", viagem)
        posicao?.let { o.put("lat", it.latitude).put("lon", it.longitude) }
        tensao?.let { o.put("tensao", it.toDouble()) }
        eventos.put(o); limitar(); gravar("eventos", eventos)
    }

    private fun enviar(forcarStatus: Boolean = false) {
        if (enviando) return
        val agora = System.currentTimeMillis()
        val intervalo = if (motorLigado) 60_000L else 300_000L
        val status = forcarStatus || agora - ultimoStatus >= intervalo
        if (pontos.length() == 0 && eventos.length() == 0 && !status) return
        enviando = true
        val token = Nuvem.obter(ctx).token
        val ev = JSONArray(); for (i in 0 until eventos.length()) ev.put(JSONObject(eventos.getJSONObject(i).toString()).put("dispositivo", token))
        val pt = JSONArray(); for (i in 0 until pontos.length()) pt.put(JSONObject(pontos.getJSONObject(i).toString()).put("dispositivo", token))
        val nEv = ev.length(); val nPt = pt.length()
        val nuvem = Nuvem.obter(ctx)
        var pendentes = (if (nEv > 0) 1 else 0) + (if (nPt > 0) 1 else 0) + (if (status) 1 else 0)
        fun fim() { if (--pendentes <= 0) enviando = false }
        if (nEv > 0) nuvem.enviarJson("/rest/v1/civic_ignicao?on_conflict=dispositivo,tipo,em", ev.toString(), "resolution=ignore-duplicates") { ok ->
            if (ok) { eventos = retirar(eventos, nEv); gravar("eventos", eventos) }; fim()
        }
        if (nPt > 0) nuvem.enviarJson("/rest/v1/civic_posicoes?on_conflict=dispositivo,em", pt.toString(), "resolution=ignore-duplicates") { ok ->
            if (ok) { pontos = retirar(pontos, nPt); gravar("pontos", pontos) }; fim()
        }
        if (status) {
            ultimoStatus = agora
            val s = JSONObject().put("dispositivo", token).put("atualizado_em", iso(agora)).put("motor_ligado", motorLigado)
                .put("posicao_fonte", if (fonteRede) "rede" else "gps").put("heartbeat_s", intervalo / 1000).put("viagem", viagem).put("versao_app", versao())
            posicao?.let { s.put("lat", it.latitude).put("lon", it.longitude).put("vel", Math.round(velocidade * 10) / 10.0) }
            tensao?.let { s.put("tensao", it.toDouble()) }
            nuvem.enviarJson("/rest/v1/civic_status?on_conflict=dispositivo", JSONArray().put(s).toString(), "resolution=merge-duplicates") { fim() }
        }
    }

    /** Fila limitada: sem internet por muito tempo, descarta os mais antigos. */
    private fun limitar() {
        pontos = retirar(pontos, maxOf(0, pontos.length() - 600))
        eventos = retirar(eventos, maxOf(0, eventos.length() - 100))
    }

    private fun retirar(a: JSONArray, qtd: Int): JSONArray = JSONArray().also { n -> for (i in qtd until a.length()) n.put(a.get(i)) }
    private fun ler(chave: String) = runCatching { JSONArray(prefs.getString(chave, "[]")) }.getOrDefault(JSONArray())
    private fun gravar(chave: String, a: JSONArray) = prefs.edit().putString(chave, a.toString()).apply()
    private fun versao() = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    private fun iso(ms: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    companion object {
        @Volatile private var instancia: Telemetria? = null
        fun obter(ctx: Context): Telemetria = instancia ?: synchronized(this) {
            instancia ?: Telemetria(ctx.applicationContext).also { instancia = it }
        }
    }
}
