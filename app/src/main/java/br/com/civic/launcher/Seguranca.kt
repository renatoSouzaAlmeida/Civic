package br.com.civic.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.civic.launcher.obd.AlertasMotor
import br.com.civic.launcher.obd.DadosObd
import br.com.civic.launcher.obd.TipoAlerta
import org.json.JSONArray
import java.util.Calendar
import java.util.Locale

/**
 * Segurança do motorista, tudo a partir da velocidade e do rpm do OBD:
 *  - Fadiga: mais de 2 h sem pausa de 15 min, ou dirigindo de madrugada, sugere parar (faixa + voz + Telegram).
 *  - Frenagens e acelerações bruscas: contadas por viagem; viram uma nota de condução no resumo da viagem.
 *  - Possível colisão: queda brusca de velocidade seguida do motor apagando abre uma contagem de 30 s; se
 *    ninguém tocar em "Estou bem", manda a localização para o Telegram.
 *  - Uso incomum: partida do motor em horário em que o carro nunca é usado (ou qualquer partida, com o Vigia total).
 */
class Seguranca private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("seguranca", Context.MODE_PRIVATE)
    private val principal = Handler(Looper.getMainLooper())

    /** Segundos restantes da contagem de colisão (null = sem contagem). */
    var colisao by mutableStateOf<Int?>(null)
        private set
    private var colisaoTeste = false

    // Motor e partida
    private var motorLigado = false
    private var semGiroDesde = 0L
    private var primeira = true

    // Velocidade
    private data class Ponto(val t: Long, val v: Float)
    private val janela = ArrayDeque<Ponto>()
    private var impactoEm = 0L
    private var ultimaFrenagem = 0L
    private var ultimaAceleracao = 0L
    private var silencioColisaoAte = 0L

    private var ultimoTempo = 0L
    private var ultimaObd = 0L

    // Fadiga
    private var dirigindoDesde = 0L
    private var paradoDesde = 0L

    /** A cada leitura do OBD. */
    fun amostra(d: DadosObd, demonstracao: Boolean) {
        if (demonstracao) return
        val agora = System.currentTimeMillis()
        ultimaObd = agora
        val rpm = d.rpm ?: 0
        val vel = (d.velocidade ?: 0).toFloat()
        val girando = rpm > 400
        controlarMotor(girando, agora)
        if (girando) { condicao(vel, agora); fadiga(vel, agora) } else { AlertasMotor.obter(ctx).definirExterna(TipoAlerta.Fadiga, null); if (!motorLigado) janela.clear() }
        verificarImpacto(rpm, vel, agora)
        ultimoTempo = agora
    }

    // ---------- motor, partida e uso incomum ----------

    private fun controlarMotor(girando: Boolean, agora: Long) {
        if (girando) {
            semGiroDesde = 0L
            if (!motorLigado) {
                motorLigado = true
                val partidaReal = !primeira || SystemClock.elapsedRealtime() < 10 * 60_000L
                if (agora - paradoDesde > 15 * 60_000L || dirigindoDesde == 0L) dirigindoDesde = 0L
                if (partidaReal) usoIncomum(agora)
            }
        } else if (motorLigado) {
            if (semGiroDesde == 0L) semGiroDesde = agora
            else if (agora - semGiroDesde >= 10_000L) { motorLigado = false; semGiroDesde = 0L; paradoDesde = agora }
        }
        primeira = false
    }

    /**
     * Sem OBD (Fit com CarPlay ou carro com Android Auto): a partida é detectada pelo GPS (começou a andar).
     * Faz a mesma checagem de uso incomum / vigia total e zera a contagem de fadiga.
     */
    fun partidaPorGps() {
        val agora = System.currentTimeMillis()
        if (agora - paradoDesde > 15 * 60_000L || dirigindoDesde == 0L) dirigindoDesde = 0L
        usoIncomum(agora)
    }

    /**
     * Velocidade do GPS (serviço Vigia, com o launcher em segundo plano): quando o OBD não está lendo há mais de 5 s,
     * a condução (frenagem, aceleração, excesso de velocidade) e a fadiga continuam sendo medidas por aqui.
     */
    fun amostraGps(velKmh: Float) {
        val agora = System.currentTimeMillis()
        if (agora - ultimaObd < 5_000L) return
        if (velKmh >= 3f) { condicao(velKmh, agora); fadiga(velKmh, agora) }
        else { janela.clear(); fadiga(0f, agora) }
    }

    private fun usoIncomum(agora: Long) {
        val hora = Calendar.getInstance().apply { timeInMillis = agora }.get(Calendar.HOUR_OF_DAY)
        val hist = runCatching { JSONArray(prefs.getString("partidas", "[]")) }.getOrDefault(JSONArray())
        val horas = (0 until hist.length()).map { hist.getInt(it) }
        // Incomum: já conhecemos o hábito (12+ partidas) e nunca houve partida a ≤ 1 h desta hora.
        val incomum = horas.size >= 12 && horas.none { val d = Math.abs(it - hora); minOf(d, 24 - d) <= 1 }
        val vigia = Configuracoes.obter(ctx).vigiaTotal
        if (incomum || vigia) {
            val quando = "%02d:%02d".format(Locale("pt", "BR"), Calendar.getInstance().apply { timeInMillis = agora }.get(Calendar.HOUR_OF_DAY),
                Calendar.getInstance().apply { timeInMillis = agora }.get(Calendar.MINUTE))
            avisarComLocal("uso_incomum", "🚨 " + (if (vigia && !incomum) "Vigia: o motor foi ligado às $quando." else "Uso incomum: o motor foi ligado às $quando, fora do seu horário habitual.") + " Se não foi você, fique atento.")
        }
        val novo = JSONArray(); (horas + hora).takeLast(60).forEach { novo.put(it) }
        prefs.edit().putString("partidas", novo.toString()).apply()
    }

    // ---------- frenagem e aceleração bruscas ----------

    private fun condicao(vel: Float, agora: Long) {
        val ant = janela.lastOrNull()
        if (ant != null) {
            val dt = (agora - ant.t) / 1000f
            if (dt in 0.15f..3.2f) {
                val a = (vel - ant.v) / dt // km/h por segundo
                if (a <= -11f && ant.v >= 15f && agora - ultimaFrenagem > 3_000) { ComputadorBordo.obter(ctx).frenagemBrusca(); ultimaFrenagem = agora }
                if (a >= 9f && vel >= 8f && agora - ultimaAceleracao > 3_000) { ComputadorBordo.obter(ctx).aceleracaoBrusca(); ultimaAceleracao = agora }
            }
        }
        janela.addLast(Ponto(agora, vel))
        while (janela.size > 1 && agora - janela.first().t > 3_000) janela.removeFirst()
    }

    // ---------- fadiga ----------

    private fun fadiga(vel: Float, agora: Long) {
        if (vel >= 3f) { if (dirigindoDesde == 0L) dirigindoDesde = agora; paradoDesde = 0L }
        else {
            if (paradoDesde == 0L) paradoDesde = agora
            else if (agora - paradoDesde >= 15 * 60_000L) dirigindoDesde = 0L
        }
        val min = if (dirigindoDesde == 0L) 0L else (agora - dirigindoDesde) / 60_000L
        val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val msg = when {
            min >= 120 -> "Você está dirigindo há ${min / 60} h ${min % 60} min sem pausa. Pare em local seguro e descanse alguns minutos."
            hora in 0..4 && min >= 30 -> "Dirigir de madrugada aumenta o risco de sono. Se sentir cansaço, pare e descanse."
            else -> null
        }
        AlertasMotor.obter(ctx).definirExterna(TipoAlerta.Fadiga, msg)
    }

    // ---------- possível colisão ----------

    private fun verificarImpacto(rpm: Int, vel: Float, agora: Long) {
        if (colisao != null || agora < silencioColisaoAte) return
        val maior = janela.maxOfOrNull { it.v } ?: 0f
        // Queda de 30 km/h ou mais em até 3 s, partindo de 35 km/h ou mais.
        if (impactoEm == 0L && motorLigado && maior >= 35f && vel <= maior - 30f) impactoEm = agora
        if (impactoEm != 0L) {
            if (agora - impactoEm > 12_000L) impactoEm = 0L
            else if (rpm < 400) { impactoEm = 0L; iniciarContagem(30, teste = false) }
        }
    }

    /** Botão de teste em Config.: simula a detecção com contagem de 10 s e mensagem marcada como simulação. */
    fun testarColisao() = iniciarContagem(10, teste = true)

    private fun iniciarContagem(segundos: Int, teste: Boolean) {
        if (colisao != null) return
        colisaoTeste = teste
        colisao = segundos
        Voz.obter(ctx).falar("Possível colisão detectada. Toque em estou bem para cancelar.")
        principal.postDelayed(tique, 1_000)
    }

    private val tique = object : Runnable {
        override fun run() {
            val restante = (colisao ?: return) - 1
            if (restante <= 0) { enviarColisao(); return }
            colisao = restante
            if (restante % 10 == 0) Voz.obter(ctx).falar("Aviso de colisão em $restante segundos.")
            principal.postDelayed(this, 1_000)
        }
    }

    /** "Estou bem": cancela a contagem e silencia novas detecções por 2 min. */
    fun cancelarColisao() {
        principal.removeCallbacks(tique)
        colisao = null
        silencioColisaoAte = System.currentTimeMillis() + 2 * 60_000L
    }

    /** "Pedir ajuda agora": não espera o fim da contagem. */
    fun enviarColisaoAgora() { principal.removeCallbacks(tique); enviarColisao() }

    private fun enviarColisao() {
        colisao = null
        silencioColisaoAte = System.currentTimeMillis() + 5 * 60_000L
        val teste = colisaoTeste
        avisarComLocal("colisao", (if (teste) "🧪 SIMULAÇÃO\n" else "") + "🚨 POSSÍVEL COLISÃO: queda brusca de velocidade e motor desligado, sem confirmação de que está tudo bem.", simulado = teste)
        Voz.obter(ctx).falar("Aviso enviado.")
    }

    // ---------- Telegram ----------

    private fun avisarComLocal(tipo: String, texto: String, simulado: Boolean = false) {
        val l = LocalEstacionamento.obter(ctx).posicaoRecente()
        if (l == null) { Nuvem.obter(ctx).avisar(tipo, texto + "\n(sem posição de GPS)"); return }
        val completo = texto + "\n🗺️ https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, l.latitude, l.longitude)
        Nuvem.obter(ctx).avisarLocalizacao(l.latitude, l.longitude, completo, origem = if (simulado) "simulado" else "evento", tipo = tipo)
    }

    companion object {
        @Volatile private var instancia: Seguranca? = null
        fun obter(ctx: Context): Seguranca = instancia ?: synchronized(this) { instancia ?: Seguranca(ctx.applicationContext).also { instancia = it } }
    }
}
