package br.com.civic.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import br.com.civic.launcher.obd.DadosObd
import br.com.civic.launcher.obd.SaudeBateria
import org.json.JSONArray
import org.json.JSONObject

/**
 * Último estado do carro: durante o uso o app guarda um resumo no aparelho (sem internet) e, assim que o carro
 * liga de novo, envia esse resumo do último uso ao Supabase (civic_estados). Um envio pequeno, sem lote.
 * Se estiver sem internet, o resumo fica pendente e é tentado de novo a cada 5 min com o motor ligado.
 */
class EstadoCarro private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("estado_carro", Context.MODE_PRIVATE)
    private val principal = Handler(Looper.getMainLooper())

    // Últimos valores conhecidos (um valor lido nunca é apagado por uma leitura vazia).
    private var tensao: Float? = null
    private var tempMotor: Int? = null
    private var tempAdmissao: Int? = null
    private var nivelCombustivel: Int? = null
    private var consumoMedio: Float? = null
    private var stft: Float? = null
    private var ltft: Float? = null
    private var luzMotor = false
    private var codigos: List<String> = emptyList()
    private var pendentes: List<String> = emptyList()
    private var kmApagar: Int? = null
    private var tempCambio: Int? = null
    private var velMax = 0
    private var rpmMax = 0

    private var motorLigado = false
    private var primeira = true
    private var ultimoSalvo = 0L
    private var ultimaTentativa = 0L
    private var enviando = false

    /** Chamado a cada leitura do OBD. */
    fun amostra(d: DadosObd, demonstracao: Boolean) {
        if (demonstracao) return
        val agora = SystemClock.elapsedRealtime()
        val girando = (d.rpm ?: 0) > 400
        if (girando) {
            d.tensao?.let { tensao = it }; d.tempMotor?.let { tempMotor = it }; d.tempAdmissao?.let { tempAdmissao = it }
            d.nivelCombustivel?.let { nivelCombustivel = it }; d.consumoMedio?.let { consumoMedio = it }
            d.stft?.let { stft = it }; d.ltft?.let { ltft = it }
            d.kmDesdeApagar?.let { kmApagar = it }; d.tempCambio?.let { tempCambio = it }
            luzMotor = d.luzMotor; codigos = d.codigos; pendentes = d.codigosPendentes
            velMax = maxOf(velMax, d.velocidade ?: 0); rpmMax = maxOf(rpmMax, d.rpm ?: 0)
        }
        if (girando && !motorLigado) {
            // Motor ligou. A 1ª leitura do processo só conta se o aparelho acabou de ligar (senão o app só reabriu).
            if (!primeira || agora < 10 * 60_000L) {
                if (prefs.getBoolean("pendente", false)) tentarEnviar()
                velMax = 0; rpmMax = 0
                ultimoSalvo = agora // não sobrescreve o resumo antigo (ainda pendente) com dados vazios de uma partida nova
            }
        }
        if (girando && agora - ultimoSalvo >= 30_000) { ultimoSalvo = agora; salvar() }
        if (!girando && motorLigado) salvar() // motor desligou: guarda o estado final
        if (girando && agora - ultimaTentativa >= 5 * 60_000L && prefs.getBoolean("pendente", false)) tentarEnviar()
        motorLigado = girando
        primeira = false
    }

    /** Guarda o resumo do último uso no aparelho e marca como pendente de envio. */
    private fun salvar() {
        val agoraMs = System.currentTimeMillis()
        val o = JSONObject().put("registrado_em", iso(agoraMs)).put("versao_app", versao())
        tensao?.let { o.put("tensao", it.toDouble()) }
        tempMotor?.let { o.put("temp_motor", it) }
        tempAdmissao?.let { o.put("temp_admissao", it) }
        nivelCombustivel?.let { o.put("nivel_combustivel", it) }
        consumoMedio?.let { o.put("consumo_medio", it.toDouble()) }
        stft?.let { o.put("stft", it.toDouble()) }
        ltft?.let { o.put("ltft", it.toDouble()) }
        o.put("luz_motor", luzMotor).put("dtc", JSONArray(codigos)).put("dtc_pendentes", JSONArray(pendentes))
        o.put("vel_max", velMax).put("rpm_max", rpmMax)
        kmApagar?.let { o.put("km_desde_apagar", it) }
        tempCambio?.let { o.put("temp_cambio", it) }
        runCatching {
            val dg = br.com.civic.launcher.obd.DiagnosticoAvancado.obter(ctx)
            dg.aquecimentos.firstOrNull { it.segundos != null }?.segundos?.let { o.put("termostato_s", it) }
            dg.catalisador?.razao?.let { o.put("catalisador_razao", it) }
            dg.marchaLenta?.let { o.put("marcha_lenta_desvio", it.desvio) }
            dg.ultimaTaxaDreno()?.let { o.put("dreno_v_h", it) }
            if (dg.freezeFrames.isNotEmpty()) o.put("freeze_frames", JSONArray(dg.freezeFrames.take(5).map { f ->
                JSONObject().put("codigo", f.codigo).put("em", iso(f.em)).put("itens", JSONObject().also { m -> f.itens.forEach { p -> m.put(p.first, p.second) } })
            }))
        }
        ComputadorBordo.obter(ctx).let { cb ->
            (cb.atual ?: cb.historico.lastOrNull())?.let { v ->
                o.put("viagem_km", v.distanciaKm).put("viagem_duracao_s", v.duracaoMs / 1000).put("viagem_vel_media", v.velMedia)
            }
        }
        runCatching { LocalEstacionamento.obter(ctx).posicaoRecente() }.getOrNull()?.let {
            o.put("lat", it.latitude).put("lon", it.longitude).put("gps_em", iso(it.time))
        }
        runCatching { SaudeBateria.obter(ctx).partidas.lastOrNull { !it.demonstracao } }.getOrNull()?.let {
            o.put("bateria_repouso", it.repouso.toDouble()).put("bateria_minima", it.minima.toDouble()).put("bateria_avaliacao", it.avaliacao.name)
        }
        prefs.edit().putString("resumo", o.toString()).putBoolean("pendente", true).apply()
    }

    /** Simulação (demonstração do OBD): envia já um resumo do "último uso" com os dados simulados, marcado como simulação. */
    fun enviarSimulacao(v: Viagem?, d: DadosObd) {
        val o = JSONObject().put("registrado_em", iso(System.currentTimeMillis())).put("versao_app", versao() + " (simulação)")
        d.tensao?.let { o.put("tensao", it.toDouble()) }; d.tempMotor?.let { o.put("temp_motor", it) }; d.tempAdmissao?.let { o.put("temp_admissao", it) }
        d.nivelCombustivel?.let { o.put("nivel_combustivel", it) }; d.consumoMedio?.let { o.put("consumo_medio", it.toDouble()) }
        d.stft?.let { o.put("stft", it.toDouble()) }; d.ltft?.let { o.put("ltft", it.toDouble()) }
        o.put("luz_motor", d.luzMotor).put("dtc", JSONArray(d.codigos)).put("dtc_pendentes", JSONArray(d.codigosPendentes))
        v?.let { o.put("viagem_km", it.distanciaKm).put("viagem_duracao_s", it.duracaoMs / 1000).put("viagem_vel_media", it.velMedia).put("vel_max", it.velMaxima).put("rpm_max", it.rpmMaximo) }
        runCatching { LocalEstacionamento.obter(ctx).posicaoRecente() }.getOrNull()?.let { o.put("lat", it.latitude).put("lon", it.longitude).put("gps_em", iso(it.time)) }
        Nuvem.obter(ctx).enviarEstado(o) { }
    }

    private fun tentarEnviar() {
        if (enviando) return
        val resumo = prefs.getString("resumo", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        enviando = true
        ultimaTentativa = SystemClock.elapsedRealtime()
        // Só marca como enviado se o resumo não foi trocado por um mais novo enquanto subia.
        val chave = resumo.optString("registrado_em")
        Nuvem.obter(ctx).enviarEstado(resumo) { ok ->
            enviando = false
            if (ok) {
                val atual = prefs.getString("resumo", null)?.let { runCatching { JSONObject(it).optString("registrado_em") }.getOrNull() }
                if (atual == chave) prefs.edit().putBoolean("pendente", false).apply()
            }
        }
    }

    private fun versao(): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

    private fun iso(ms: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(ms))

    companion object {
        @Volatile private var instancia: EstadoCarro? = null
        fun obter(ctx: Context): EstadoCarro = instancia ?: synchronized(this) {
            instancia ?: EstadoCarro(ctx.applicationContext).also { instancia = it }
        }
    }
}
