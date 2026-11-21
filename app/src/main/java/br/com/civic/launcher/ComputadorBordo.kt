package br.com.civic.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import br.com.civic.launcher.obd.AlertasMotor
import br.com.civic.launcher.obd.TipoAlerta
import java.util.Locale

/** Um ponto da linha do tempo da viagem. `tipo`: partida, frenagem, aceleracao, excesso, rpm, colisao, sos, alerta, estacionou. */
data class EventoViagem(val em: Long, val tipo: String, val texto: String)

/** Uma viagem: do carro começar a andar (ou o motor ligar) até ficar 5 min parado/desligado. */
data class Viagem(
    val inicio: Long,
    val fim: Long,
    val distanciaKm: Double = 0.0,
    val emMovimentoMs: Long = 0,
    val velMaxima: Int = 0,
    val rpmMaximo: Int = 0,
    val demonstracao: Boolean = false,
    /** Modo de condução: eventos contados durante a viagem (vale com OBD ou só com GPS). */
    val frenagens: Int = 0,
    val aceleracoes: Int = 0,
    val excessos: Int = 0,
    val rpmAlto: Int = 0,
    /** Maior velocidade acima do limite (km/h) e tempo total acima dele (ms). */
    val excessoMaxKmh: Int = 0,
    val excessoMs: Long = 0,
    /** Consumo medido na viagem (só com OBD): quilômetros e litros acumulados. */
    val consKm: Double = 0.0,
    val consLitros: Double = 0.0,
    /** A viagem teve leituras do OBD (senão foi medida só pelo GPS, ex.: Fit com CarPlay). */
    val comObd: Boolean = false,
    val latIni: Double? = null, val lonIni: Double? = null,
    val latFim: Double? = null, val lonFim: Double? = null,
    /** Linha do tempo: partida, frenagens e acelerações bruscas, excessos, RPM alto, possível colisão, SOS, alertas e estacionamento (no máx. 80; a partida fica sempre). */
    val eventos: List<EventoViagem> = emptyList(),
) {
    fun comEvento(em: Long, tipo: String, texto: String): Viagem =
        copy(eventos = (if (eventos.size >= 80) eventos.filterIndexed { i, _ -> i != 1 } else eventos) + EventoViagem(em, tipo, texto))
    val duracaoMs: Long get() = fim - inicio
    /** Média só do tempo em movimento (como o computador de bordo do carro). */
    val velMedia: Double get() = if (emMovimentoMs > 0) distanciaKm / (emMovimentoMs / 3_600_000.0) else 0.0
    val consumoMedio: Double? get() = if (consLitros > 0.05 && consKm > 0.5) consKm / consLitros else null

    /** Nota de condução de 0 a 100 (Driving Score), ou null em viagem curta demais (menos de 0,5 km). */
    val nota: Int? get() {
        if (distanciaKm < 0.5) return null
        val fator = 10.0 / maxOf(10.0, distanciaKm) // viagens longas diluem os eventos
        return (100 - (6 * frenagens + 4 * aceleracoes + 3 * excessos + 2 * rpmAlto) * fator).toInt().coerceIn(0, 100)
    }

    fun resumoConducao(): String? = nota?.let {
        "condução $it/100 ($frenagens frenagens, $aceleracoes acelerações" + (if (excessos > 0) ", $excessos excessos de velocidade (máx. $excessoMaxKmh km/h)" else "") + (if (comObd) ", $rpmAlto RPM elevado" else "") + ")"
    }

    companion object {
        fun rotuloNota(n: Int) = when { n >= 90 -> "Muito bom"; n >= 75 -> "Bom"; n >= 55 -> "Atenção"; else -> "Precisa melhorar" }
    }
}

/**
 * Computador de bordo: mede a viagem atual (distância, tempo, médias, máximas) com a velocidade
 * do OBD — ou do GPS quando o OBD não informa — e guarda o histórico das últimas viagens.
 * A distância também alimenta o odômetro do app (Veículo).
 */
class ComputadorBordo private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("computador_bordo", Context.MODE_PRIVATE)
    private val principal = Handler(Looper.getMainLooper())

    var atual by mutableStateOf<Viagem?>(null)
        private set
    val historico = mutableStateListOf<Viagem>()

    private var ultimaAmostra = 0L
    private var ultimaAtividade = 0L
    private var ultimaObd = 0L
    private var rpmAltoAtivo = false
    private var ultimoRpmAlto = 0L
    private var excessoAtivo = false
    private var acimaDesde = 0L
    private var ultimoExcesso = 0L
    private var consumoInst: Float? = null

    private val verificador = object : Runnable {
        override fun run() {
            if (atual != null && System.currentTimeMillis() - ultimaAtividade > 5 * 60_000) finalizar()
            principal.postDelayed(this, 60_000)
        }
    }

    init {
        carregar()
        principal.postDelayed(verificador, 60_000)
    }

    /** Velocidade e rotação vindas do OBD (prioridade sobre o GPS). */
    fun amostraObd(velKmh: Int?, rpm: Int?, demonstracao: Boolean, consumoInstantaneo: Float? = null) {
        if (velKmh == null && rpm == null) return
        ultimaObd = System.currentTimeMillis()
        consumoInst = consumoInstantaneo
        amostra((velKmh ?: 0).toFloat(), rpm ?: 0, demonstracao, comObd = true)
    }

    /** A segurança avisa frenagem/aceleração brusca; entra na nota da viagem em andamento. */
    fun frenagemBrusca(detalhe: String = "") { val t = System.currentTimeMillis(); atual = atual?.let { it.copy(frenagens = it.frenagens + 1).comEvento(t, "frenagem", "Frenagem brusca" + if (detalhe.isNotEmpty()) " ($detalhe)" else "") }; salvarAtual() }
    fun aceleracaoBrusca(detalhe: String = "") { val t = System.currentTimeMillis(); atual = atual?.let { it.copy(aceleracoes = it.aceleracoes + 1).comEvento(t, "aceleracao", "Aceleração brusca" + if (detalhe.isNotEmpty()) " ($detalhe)" else "") }; salvarAtual() }
    /** Marca um ponto na linha do tempo da viagem em andamento (colisão, SOS, alerta crítico...). Sem viagem em andamento, não faz nada. */
    fun registrarEvento(tipo: String, texto: String) { atual = atual?.comEvento(System.currentTimeMillis(), tipo, texto); salvarAtual() }
    private fun salvarAtual() { atual?.let { prefs.edit().putString("atual", paraJson(it).toString()).apply() } }

    /** Velocidade do GPS: só usada se o OBD não mandou nada nos últimos 5 s. */
    fun amostraGps(velKmh: Float?) {
        if (velKmh == null || System.currentTimeMillis() - ultimaObd < 5_000) return
        amostra(velKmh, 0, false)
    }

    private fun amostra(vel: Float, rpm: Int, demonstracao: Boolean, comObd: Boolean = false) {
        val agora = System.currentTimeMillis()
        VolumeAutomatico.obter(ctx).amostra(vel, agora) // volume pela velocidade (OBD ou GPS)
        val andando = vel >= 3f
        val ligado = rpm > 400
        var v = atual
        if (v != null && v.demonstracao != demonstracao) { finalizar(); v = null }
        if (v == null) {
            if (!andando && !ligado) { AlertasMotor.obter(ctx).definirExterna(TipoAlerta.Velocidade, null); return }
            val p = runCatching { LocalEstacionamento.obter(ctx).posicaoRecente() }.getOrNull()
            v = Viagem(inicio = agora, fim = agora, demonstracao = demonstracao, latIni = p?.latitude, lonIni = p?.longitude)
                .comEvento(agora, "partida", if (ligado) "Partida (motor ligado)" else "Partida (carro começou a andar)")
            ultimaAmostra = agora
            rpmAltoAtivo = false; excessoAtivo = false
        }
        // Integra a distância; lacunas grandes (sem sinal) não contam para não inventar km.
        val dt = (agora - ultimaAmostra).coerceIn(0, 5_000)
        val km = if (andando) vel * dt / 3_600_000.0 else 0.0
        v = v.copy(
            fim = agora,
            distanciaKm = v.distanciaKm + km,
            emMovimentoMs = v.emMovimentoMs + if (andando) dt else 0,
            velMaxima = maxOf(v.velMaxima, vel.toInt()),
            rpmMaximo = maxOf(v.rpmMaximo, rpm),
            comObd = v.comObd || comObd,
        )
        // RPM elevado (4.500 rpm ou mais): conta uma ocorrência por subida, com 10 s entre ocorrências.
        if (rpm >= 4_500) { if (!rpmAltoAtivo && agora - ultimoRpmAlto > 10_000) { v = v.copy(rpmAlto = v.rpmAlto + 1).comEvento(agora, "rpm", "RPM elevado ($rpm rpm)"); ultimoRpmAlto = agora }; rpmAltoAtivo = true }
        else if (rpm < 4_000) rpmAltoAtivo = false
        // Excesso de velocidade: acima do limite (+2 km/h de tolerância) por 5 s seguidos conta uma ocorrência (30 s entre elas);
        // registra a maior velocidade e o tempo total acima do limite, e mostra a faixa com voz.
        val limite = Configuracoes.obter(ctx).limiteVelocidade
        if (limite > 0 && vel > limite + 2) {
            if (acimaDesde == 0L) acimaDesde = agora
            if (excessoAtivo) v = v.copy(excessoMs = v.excessoMs + dt)
            v = v.copy(excessoMaxKmh = maxOf(v.excessoMaxKmh, vel.toInt()))
            if (!excessoAtivo && agora - acimaDesde >= 5_000L && agora - ultimoExcesso > 30_000L) {
                v = v.copy(excessos = v.excessos + 1).comEvento(agora, "excesso", "Excesso de velocidade (${vel.toInt()} km/h, limite $limite)"); ultimoExcesso = agora; excessoAtivo = true
                AlertasMotor.obter(ctx).definirExterna(TipoAlerta.Velocidade, "Reduza a velocidade: o limite configurado é de $limite km/h.")
            }
        } else if (limite == 0 || vel < limite - 5) {
            acimaDesde = 0L; excessoAtivo = false
            AlertasMotor.obter(ctx).definirExterna(TipoAlerta.Velocidade, null)
        }
        // Consumo da viagem (só com OBD): litros = km / (km/l instantâneo).
        val ci = consumoInst
        if (comObd && ci != null && ci > 1f && km > 0) v = v.copy(consKm = v.consKm + km, consLitros = v.consLitros + km / ci)
        ultimaAmostra = agora
        if (andando || ligado) ultimaAtividade = agora
        atual = v
        if (km > 0 && !demonstracao) Veiculo.obter(ctx).somarDistancia(km)
        prefs.edit().putString("atual", paraJson(v).toString()).apply()
    }

    /** Encerra a viagem atual (automático após 5 min parado/desligado, ou pelo botão). */
    fun finalizar(simulacao: Boolean = false) {
        val v0 = atual ?: return
        atual = null
        prefs.edit().remove("atual").apply()
        if (v0.distanciaKm < 0.1 && !simulacao) return // manobra na garagem não vira viagem
        val p = runCatching { LocalEstacionamento.obter(ctx).posicaoRecente() }.getOrNull()
        val v = v0.copy(latFim = p?.latitude ?: v0.latFim, lonFim = p?.longitude ?: v0.lonFim).comEvento(v0.fim, "estacionou", "Estacionou / viagem encerrada")
        historico.add(0, v)
        while (historico.size > 30) historico.removeAt(historico.lastIndex)
        gravarHistorico()
        if (!v.demonstracao) Nuvem.obter(ctx).avisar("viagem", "🏁 Viagem: ${resumo(v)}" + (v.resumoConducao()?.let { " · $it" } ?: ""))
        // Simulação (botão de demonstração do OBD): manda a mesma mensagem, marcada, para conferir o aviso no Telegram.
        else if (simulacao) Nuvem.obter(ctx).avisar("simulacao", "🧪 SIMULAÇÃO\n🏁 Viagem: ${resumo(v)}" + (v.resumoConducao()?.let { " · $it" } ?: ""))
        if (!v.demonstracao || simulacao) enviarParaNuvem(v)
    }

    // ---------- envio para o banco (Casa Tech) ----------

    /** Resolve os endereços de saída e chegada (se houver internet/Geocoder), guarda na fila e tenta enviar. */
    private fun enviarParaNuvem(v: Viagem) {
        Thread {
            val le = LocalEstacionamento.obter(ctx)
            val eIni = if (v.latIni != null && v.lonIni != null) runCatching { le.endereco(v.latIni, v.lonIni) }.getOrNull() else null
            val eFim = if (v.latFim != null && v.lonFim != null) runCatching { le.endereco(v.latFim, v.lonFim) }.getOrNull() else null
            val o = JSONObject().put("inicio", iso(v.inicio)).put("fim", iso(v.fim)).put("distancia_km", Math.round(v.distanciaKm * 100) / 100.0)
                .put("duracao_s", v.duracaoMs / 1000).put("em_movimento_s", v.emMovimentoMs / 1000).put("vel_media", Math.round(v.velMedia * 10) / 10.0)
                .put("vel_max", v.velMaxima).put("frenagens", v.frenagens).put("aceleracoes", v.aceleracoes).put("excessos", v.excessos).put("excesso_max_kmh", v.excessoMaxKmh).put("excesso_s", v.excessoMs / 1000)
                .put("fonte", if (v.comObd) "obd" else "gps").put("simulado", v.demonstracao).put("versao_app", runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?")
            if (v.comObd) { o.put("rpm_max", v.rpmMaximo).put("rpm_alto", v.rpmAlto) }
            v.consumoMedio?.let { o.put("consumo_km_l", Math.round(it * 10) / 10.0) }
            v.nota?.let { o.put("nota", it) }
            if (v.eventos.isNotEmpty()) o.put("eventos", JSONArray().also { a -> v.eventos.forEach { e -> a.put(JSONObject().put("em", iso(e.em)).put("tipo", e.tipo).put("texto", e.texto)) } })
            v.latIni?.let { o.put("lat_ini", it) }; v.lonIni?.let { o.put("lon_ini", it) }
            v.latFim?.let { o.put("lat_fim", it) }; v.lonFim?.let { o.put("lon_fim", it) }
            eIni?.let { o.put("endereco_ini", it) }; eFim?.let { o.put("endereco_fim", it) }
            principal.post {
                val fila = runCatching { JSONArray(prefs.getString("fila_nuvem", "[]")) }.getOrDefault(JSONArray())
                fila.put(o); prefs.edit().putString("fila_nuvem", fila.toString()).apply()
                enviarPendentes()
            }
        }.start()
    }

    private var enviando = false

    /** Envia as viagens que ainda não subiram (sem internet ficam na fila; chamado também pela telemetria a cada 30 s). */
    fun enviarPendentes() {
        if (enviando) return
        val fila = runCatching { JSONArray(prefs.getString("fila_nuvem", "[]")) }.getOrDefault(JSONArray())
        if (fila.length() == 0) return
        val nuvem = Nuvem.obter(ctx)
        // Mesmos campos em todas as linhas (nulos quando faltam), senão o banco recusa o lote com erro 400.
        val campos = listOf("inicio", "fim", "distancia_km", "duracao_s", "em_movimento_s", "vel_media", "vel_max", "frenagens", "aceleracoes", "excessos", "excesso_max_kmh", "excesso_s",
            "fonte", "simulado", "versao_app", "rpm_max", "rpm_alto", "consumo_km_l", "nota", "lat_ini", "lon_ini", "lat_fim", "lon_fim", "endereco_ini", "endereco_fim", "eventos")
        val lote = JSONArray(); for (i in 0 until fila.length()) lote.put(JSONObject(fila.getJSONObject(i).toString()).put("dispositivo", nuvem.token).also { o -> campos.forEach { k -> if (!o.has(k)) o.put(k, JSONObject.NULL) } })
        val n = fila.length()
        enviando = true
        nuvem.enviarJson("/rest/v1/civic_viagens?on_conflict=dispositivo,inicio", lote.toString(), "resolution=merge-duplicates") { ok ->
            principal.post {
                enviando = false
                if (ok) {
                    val atual2 = runCatching { JSONArray(prefs.getString("fila_nuvem", "[]")) }.getOrDefault(JSONArray())
                    val resto = JSONArray(); for (i in n until atual2.length()) resto.put(atual2.get(i))
                    prefs.edit().putString("fila_nuvem", resto.toString()).apply()
                }
            }
        }
    }

    private fun iso(ms: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(ms))

    fun limparHistorico() { historico.clear(); gravarHistorico() }

    /** Litros e custo estimados pelo consumo médio real registrado nos abastecimentos. */
    fun estimativa(v: Viagem): Pair<Float, Float>? {
        val veiculo = Veiculo.obter(ctx)
        val consumo = veiculo.consumoMedio() ?: return null
        val litros = (v.distanciaKm / consumo).toFloat()
        val preco = veiculo.abastecimentos.lastOrNull { it.precoEfetivo > 0f }?.precoEfetivo ?: return litros to 0f
        return litros to litros * preco
    }

    fun resumo(v: Viagem): String {
        val br = Locale("pt", "BR")
        val est = estimativa(v)?.let { (l, c) -> " · ~%.1f l".format(br, l) + if (c > 0f) " (R$ %.2f)".format(br, c) else "" } ?: ""
        return "%.1f km em %s · média %.0f km/h · máx. %d km/h".format(br, v.distanciaKm, duracao(v.duracaoMs), v.velMedia, v.velMaxima) + est
    }

    private fun carregar() {
        runCatching {
            val arr = JSONArray(prefs.getString("historico", "[]"))
            for (i in 0 until arr.length()) historico.add(deJson(arr.getJSONObject(i)))
        }
        // Viagem que estava em andamento quando o app foi fechado: continua se foi há pouco, senão encerra.
        prefs.getString("atual", null)?.let { texto ->
            runCatching { deJson(JSONObject(texto)) }.getOrNull()?.let { v ->
                atual = v
                ultimaAmostra = v.fim
                ultimaAtividade = v.fim
            }
        }
    }

    private fun gravarHistorico() {
        val arr = JSONArray()
        historico.forEach { arr.put(paraJson(it)) }
        prefs.edit().putString("historico", arr.toString()).apply()
    }

    private fun paraJson(v: Viagem) = JSONObject().put("ini", v.inicio).put("fim", v.fim).put("km", v.distanciaKm)
        .put("mov", v.emMovimentoMs).put("vmax", v.velMaxima).put("rpm", v.rpmMaximo).put("demo", v.demonstracao)
        .put("exm", v.excessoMaxKmh).put("exs", v.excessoMs)
        .put("fren", v.frenagens).put("acel", v.aceleracoes).put("exc", v.excessos).put("rpma", v.rpmAlto)
        .put("ck", v.consKm).put("cl", v.consLitros).put("obd", v.comObd)
        .put("ev", JSONArray().also { a -> v.eventos.forEach { e -> a.put(JSONObject().put("t", e.em).put("k", e.tipo).put("x", e.texto)) } })
        .apply { v.latIni?.let { put("lai", it) }; v.lonIni?.let { put("loi", it) }; v.latFim?.let { put("laf", it) }; v.lonFim?.let { put("lof", it) } }

    private fun deJson(o: JSONObject) = Viagem(
        o.getLong("ini"), o.getLong("fim"), o.getDouble("km"), o.getLong("mov"), o.getInt("vmax"), o.getInt("rpm"), o.optBoolean("demo"),
        o.optInt("fren"), o.optInt("acel"), o.optInt("exc"), o.optInt("rpma"), o.optInt("exm"), o.optLong("exs"), o.optDouble("ck", 0.0), o.optDouble("cl", 0.0), o.optBoolean("obd"),
        if (o.has("lai")) o.getDouble("lai") else null, if (o.has("loi")) o.getDouble("loi") else null,
        if (o.has("laf")) o.getDouble("laf") else null, if (o.has("lof")) o.getDouble("lof") else null,
        o.optJSONArray("ev")?.let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { EventoViagem(it.getLong("t"), it.getString("k"), it.getString("x")) } } } ?: emptyList(),
    )

    companion object {
        fun duracao(ms: Long): String {
            val min = ms / 60_000
            return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
        }

        @Volatile private var instancia: ComputadorBordo? = null
        fun obter(ctx: Context): ComputadorBordo = instancia ?: synchronized(this) {
            instancia ?: ComputadorBordo(ctx.applicationContext).also { instancia = it }
        }
    }
}
