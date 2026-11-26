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
    private var ultimaSincVeiculo = 0L
    private var porGps = false
    private var ultimoMovimento = 0L
    // Vigia do silêncio: a multimídia apaga junto com a chave, então a nuvem precisa saber, ANTES disso, onde o carro parou.
    // Ao parar, manda na hora o ponto e o status com o endereço (resolvido aqui no aparelho); se o carro parar de dar sinal,
    // a nuvem avisa no Telegram a partir dessa última posição.
    private var parado = false
    @Volatile private var enderecoParado: String? = null
    private var latEnd = 0.0
    private var lonEnd = 0.0
    private var ultimoEnd = 0L
    @Volatile private var buscandoEnd = false

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
            runCatching { Nuvem.obter(ctx).reenviarPendentes() } // avisos que ficaram sem internet
            runCatching { CaixaPreta.obter(ctx).enviarPendentes() } // eventos da caixa-preta ainda não enviados
            // O que foi lançado na Casa Tech (valores de combustível, manutenção, km) chega ao aparelho em até 5 min.
            if (System.currentTimeMillis() - ultimaSincVeiculo >= 5 * 60_000L) { ultimaSincVeiculo = System.currentTimeMillis(); runCatching { Nuvem.obter(ctx).baixarESincronizar(Veiculo.obter(ctx)) } }
            runCatching { Tpms.obter(ctx).sincronizar() } // liga/desliga a procura de sensores conforme a chave em Config.
            runCatching { ComputadorBordo.obter(ctx).enviarPendentes() } // viagens que ficaram sem internet
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
                    runCatching { Seguranca.obter(ctx).partidaPorGps() }
                    enviar(forcarStatus = true)
                }
            } else if (velocidade > 3f) ultimoMovimento = agora
        }
        val acabouDeParar = velocidade < 3f && !parado && motorLigado
        if ((motorLigado || velocidade > 3f) && (agora - ultimoPonto >= 15_000 || acabouDeParar)) {
            ultimoPonto = agora
            pontos.put(JSONObject().put("em", iso(agora)).put("lat", local.latitude).put("lon", local.longitude)
                .put("vel", Math.round(velocidade * 10) / 10.0).put("viagem", viagem))
            limitar(); gravar("pontos", pontos)
        }
        parado = velocidade < 3f
        if (velocidade > 8f) enderecoParado = null
        if (acabouDeParar) enviar(forcarStatus = true) // a nuvem fica com a posição da parada antes de a chave ser desligada
        if (parado && motorLigado) enderecoDaParada(local)
    }

    /** Resolve o endereço de onde o carro parou (no aparelho; a nuvem não consegue) e manda no status. */
    private fun enderecoDaParada(local: Location) {
        val agora = System.currentTimeMillis()
        val r = FloatArray(1); Location.distanceBetween(latEnd, lonEnd, local.latitude, local.longitude, r)
        if (buscandoEnd || (enderecoParado != null && r[0] < 40f) || agora - ultimoEnd < 20_000L) return
        buscandoEnd = true; ultimoEnd = agora
        val lat = local.latitude; val lon = local.longitude
        Thread {
            val e = runCatching { Nuvem.obter(ctx).enderecoAgora(lat, lon) }.getOrNull()
            principal.post {
                buscandoEnd = false
                if (e != null) { enderecoParado = e; latEnd = lat; lonEnd = lon; enviar(forcarStatus = true) }
            }
        }.start()
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
        // O banco (PostgREST) recusa lote cujas linhas têm campos diferentes (erro 400): todas as linhas ganham os mesmos campos, nulos quando faltam.
        val ev = JSONArray(); for (i in 0 until eventos.length()) ev.put(JSONObject(eventos.getJSONObject(i).toString()).put("dispositivo", token).also { o -> listOf("lat", "lon", "tensao").forEach { k -> if (!o.has(k)) o.put(k, JSONObject.NULL) } })
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
                // Quem é quem no dashboard: modelo do aparelho, versão do Android e o nome do carro configurado no app.
                .put("aparelho", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim())
                .put("android_versao", "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                .put("nome_carro", Configuracoes.obter(ctx).let { c -> listOf(c.nomeCarro, c.modeloEAno).filter { it.isNotBlank() }.joinToString(" · ") })
            posicao?.let { s.put("lat", it.latitude).put("lon", it.longitude).put("vel", Math.round(velocidade * 10) / 10.0) }
            tensao?.let { s.put("tensao", it.toDouble()) }
            s.put("endereco", if (velocidade < 3f) enderecoParado ?: JSONObject.NULL else JSONObject.NULL)
            nuvem.enviarJson("/rest/v1/civic_status?on_conflict=dispositivo", JSONArray().put(s).toString(), "resolution=merge-duplicates") { fim() }
        }
    }

    fun pontosPendentes() = pontos.length()
    fun eventosPendentes() = eventos.length()

    /** Fila limitada (política de retenção): sem internet, descarta o que passou do tempo (pontos 24 h, eventos 7 dias) e, no máximo, 600 pontos e 100 eventos. */
    private fun limitar() {
        pontos = retirar(pontos, maxOf(0, pontos.length() - 600))
        eventos = retirar(eventos, maxOf(0, eventos.length() - 100))
        val agora = System.currentTimeMillis()
        pontos = semAntigos(pontos, agora - Sincronizacao.RETENCAO_PONTOS_MS)
        eventos = semAntigos(eventos, agora - Sincronizacao.RETENCAO_EVENTOS_MS)
    }

    /** As listas estão em ordem de hora: tira do começo o que é mais velho que o corte. */
    private fun semAntigos(a: JSONArray, corte: Long): JSONArray {
        var n = 0
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        while (n < a.length()) {
            val t = runCatching { f.parse(a.getJSONObject(n).getString("em"))?.time }.getOrNull() ?: break
            if (t >= corte) break
            n++
        }
        return if (n == 0) a else retirar(a, n)
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
