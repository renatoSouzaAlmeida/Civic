package br.com.civic.launcher.obd

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.civic.launcher.CentralNotificacoes
import br.com.civic.launcher.Nuvem
import br.com.civic.launcher.Voz
import java.util.Locale

/** Gravidade: define a cor da faixa e a prioridade quando há mais de um alerta. */
enum class Gravidade { Dica, Aviso, Critico }

enum class TipoAlerta(
    val titulo: String,
    val gravidade: Gravidade,
    /** A condição precisa durar esse tempo para virar alerta (evita leitura falsa). */
    val confirmacaoMs: Long,
    /** Intervalo mínimo entre avisos no celular; 0 = não vai para o celular. */
    val intervaloCelularMs: Long,
    /** Depois de dispensado na tela, só volta a aparecer após esse tempo. */
    val silencioMs: Long,
) {
    Superaquecimento("Superaquecimento", Gravidade.Critico, 10_000, 30 * 60_000L, 5 * 60_000L),
    Injecao("Luz de injeção acesa", Gravidade.Critico, 0, 24 * 3_600_000L, 60 * 60_000L),
    Alternador("Alternador sem carga", Gravidade.Aviso, 30_000, 6 * 3_600_000L, 30 * 60_000L),
    BateriaFraca("Bateria fraca", Gravidade.Aviso, 30_000, 6 * 3_600_000L, 30 * 60_000L),
    FarolDesligado("Farol desligado", Gravidade.Aviso, 5_000, 0, 10 * 60_000L),
    MotorFrio("Motor frio", Gravidade.Dica, 3_000, 0, 5 * 60_000L),
    Fadiga("Hora de uma pausa", Gravidade.Aviso, 0, 3 * 3_600_000L, 30 * 60_000L),
    CambioQuente("Câmbio quente", Gravidade.Aviso, 30_000, 3 * 3_600_000L, 15 * 60_000L),
    /** Passou do limite de velocidade configurado por tempo suficiente (não vai ao Telegram). */
    Velocidade("Velocidade acima do limite", Gravidade.Aviso, 0, 0, 2 * 60_000L),
    /** Pressão ou temperatura de pneu fora do normal (TPMS por sensores Bluetooth, quando ativado). */
    Pneu("Pressão dos pneus", Gravidade.Aviso, 0, 6 * 3_600_000L, 30 * 60_000L),
    /** Entrou ou saiu de uma área do geofence (faixa azul por 20 s; o Telegram é tratado pelo próprio geofence). */
    Geofence("Geofence", Gravidade.Dica, 0, 0, 30_000L),
    /** O adaptador parou de responder com o carro andando (dados do motor desatualizados). */
    ObdSemResposta("Adaptador OBD sem resposta", Gravidade.Aviso, 0, 30 * 60_000L, 10 * 60_000L),
}

data class Alerta(val tipo: TipoAlerta, val mensagem: String)

/**
 * Alertas inteligentes do motor, avaliados a cada leitura do OBD (mesmo com o Waze na frente).
 * Mostra a faixa de maior prioridade e, nos casos sérios, avisa no celular (Telegram) —
 * nunca no modo de demonstração.
 */
class AlertasMotor private constructor(private val ctx: Context) {
    /** Alerta mostrado agora (o mais grave entre os ativos e não dispensados), ou null. */
    var atual by mutableStateOf<Alerta?>(null)
        private set

    private val inicio = mutableMapOf<TipoAlerta, Long>()
    private val silenciadoAte = mutableMapOf<TipoAlerta, Long>()
    private val prefs = ctx.getSharedPreferences("alertas", Context.MODE_PRIVATE)
    private val br = Locale("pt", "BR")
    /** Condições calculadas fora do OBD (ex.: fadiga, pela Segurança). */
    private val externas = java.util.concurrent.ConcurrentHashMap<TipoAlerta, String>()
    /**
     * Define/limpa uma condição externa. Reavalia na hora com os últimos dados do OBD: sem OBD (ou com o adaptador caído)
     * nenhuma leitura nova chega, e sem isso a faixa e a voz desses alertas nunca apareceriam.
     */
    fun definirExterna(tipo: TipoAlerta, mensagem: String?) {
        val mudou = if (mensagem == null) externas.remove(tipo) != null else externas.put(tipo, mensagem) != mensagem
        if (mudou) avaliar(ultimosDados, ultimaDemo)
    }

    private var ultimosDados = DadosObd()
    private var ultimaDemo = false

    fun avaliar(d: DadosObd, demonstracao: Boolean, agora: Long = System.currentTimeMillis()) {
        ultimosDados = d; ultimaDemo = demonstracao
        val rpm = d.rpm ?: 0
        val ligado = rpm > 400
        val temp = d.tempMotor
        val tensao = d.tensao

        val condicoes = mutableMapOf<TipoAlerta, String>()
        if (temp != null && temp >= 105)
            condicoes[TipoAlerta.Superaquecimento] = "Motor a $temp °C. Diminua o ritmo; se continuar subindo, pare em local seguro e desligue o motor."
        if (d.luzMotor)
            condicoes[TipoAlerta.Injecao] = "O carro registrou ${d.falhas ?: 0} código(s) de falha. Veja os detalhes em OBD e leve para avaliar."
        d.tempCambio?.let { c -> if (c >= 110) condicoes[TipoAlerta.CambioQuente] = "Fluido do câmbio a $c °C. Evite subidas e arrancadas fortes; se continuar, pare e deixe esfriar." }
        if (ligado && tensao != null && tensao < 13.2f)
            condicoes[TipoAlerta.Alternador] = "Tensão em ${"%.1f".format(br, tensao)} V com o motor ligado: o alternador pode não estar carregando a bateria."
        if (!ligado && tensao != null && tensao < 12.2f)
            condicoes[TipoAlerta.BateriaFraca] = "Bateria em ${"%.1f".format(br, tensao)} V com o motor desligado. Pode faltar carga para a próxima partida."
        // Segurança: farol apagado com o motor ligado (só quando o carro informa o farol).
        if (ligado && d.farol == false)
            condicoes[TipoAlerta.FarolDesligado] = "Ligue o farol baixo: é item de segurança e obrigatório em rodovias, mesmo de dia."
        if (temp != null && temp < 70 && rpm > 3000)
            condicoes[TipoAlerta.MotorFrio] = "Motor a $temp °C: evite passar de 3.000 rpm até aquecer (70 °C)."

        condicoes.putAll(externas)
        // Confirmação: a condição precisa durar o tempo mínimo de cada tipo.
        TipoAlerta.entries.forEach { t -> if (t !in condicoes) inicio.remove(t) else inicio.putIfAbsent(t, agora) }
        val confirmados = condicoes.filterKeys { t -> agora - (inicio[t] ?: agora) >= t.confirmacaoMs }

        // Celular: só os sérios, com intervalo mínimo por tipo, e nunca na demonstração.
        if (!demonstracao) confirmados.forEach { (t, msg) ->
            if (t.intervaloCelularMs > 0 && agora - prefs.getLong("cel_${t.name}", 0L) >= t.intervaloCelularMs) {
                prefs.edit().putLong("cel_${t.name}", agora).apply()
                Nuvem.obter(ctx).avisar("alerta_motor", "${if (t.gravidade == Gravidade.Critico) "🚨" else "⚠️"} ${t.titulo}: $msg", registrarNoSino = false)
            }
        }

        val visivel = confirmados.filterKeys { t -> agora >= (silenciadoAte[t] ?: 0L) }
            .entries.sortedBy { it.key.ordinal }.firstOrNull()
        val novo = visivel?.let { Alerta(it.key, it.value) }
        // Cada alerta que aparece na tela também entra no sininho (uma vez por ocorrência).
        if (novo != null && novo.tipo != atual?.tipo) {
            if (novo.tipo.gravidade != Gravidade.Dica) Voz.obter(ctx).falar(novo.tipo.titulo + ". " + novo.mensagem.substringBefore('.') + ".")
            CentralNotificacoes.obter(ctx).registrar(
                "alerta_motor",
                "${if (demonstracao) "(Demonstração) " else ""}${if (novo.tipo.gravidade == Gravidade.Critico) "🚨" else "⚠️"} ${novo.tipo.titulo}: ${novo.mensagem}",
            )
        }
        if (novo != atual) atual = novo
    }

    /** O motorista fechou a faixa: some por um tempo (volta se a condição continuar). */
    fun dispensar() {
        val a = atual ?: return
        silenciadoAte[a.tipo] = System.currentTimeMillis() + a.tipo.silencioMs
        atual = null
    }

    companion object {
        @Volatile private var instancia: AlertasMotor? = null
        fun obter(ctx: Context): AlertasMotor = instancia ?: synchronized(this) {
            instancia ?: AlertasMotor(ctx.applicationContext).also { instancia = it }
        }
    }
}
