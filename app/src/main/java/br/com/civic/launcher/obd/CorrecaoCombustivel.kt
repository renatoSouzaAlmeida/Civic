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
import kotlin.math.abs

enum class Regime(val rotulo: String) { MarchaLenta("Marcha lenta"), Rodando("Rodando") }

enum class SituacaoMistura(val rotulo: String) {
    SemDados("Coletando dados"), Normal("Normal"), Atencao("Atenção"), Pobre("Mistura pobre"), Rica("Mistura rica"),
}

/** Média de um dia, por regime (só leituras válidas: motor quente e injeção corrigindo). */
data class DiaMistura(val dia: String, val lentaSoma: Double, val lentaN: Int, val rodandoSoma: Double, val rodandoN: Int) {
    val lenta: Double? get() = if (lentaN > 0) lentaSoma / lentaN else null
    val rodando: Double? get() = if (rodandoN > 0) rodandoSoma / rodandoN else null
}

/**
 * Correção de combustível (fuel trims): acompanha a correção curta (STFT) e longa (LTFT) que a injeção aplica.
 * Correção total positiva = injeção colocando mais combustível (mistura pobre); negativa = tirando (rica).
 * Separa marcha lenta de rodando, que é o que aponta a causa provável.
 */
class CorrecaoCombustivel private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("correcao_combustivel", Context.MODE_PRIVATE)
    private val janela = mapOf(Regime.MarchaLenta to ArrayDeque<Float>(), Regime.Rodando to ArrayDeque<Float>())
    private val formatoDia = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var amostrasNaoGravadas = 0

    var mediaLenta by mutableStateOf<Float?>(null)
        private set
    var mediaRodando by mutableStateOf<Float?>(null)
        private set
    var situacao by mutableStateOf(SituacaoMistura.SemDados)
        private set
    var causas by mutableStateOf<List<String>>(emptyList())
        private set
    /** Se a leitura atual vale para o diagnóstico (motor quente e malha fechada). */
    var leituraValida by mutableStateOf(false)
        private set
    val historico = mutableStateListOf<DiaMistura>()
    private var demoAtiva = false

    init {
        runCatching {
            val arr = JSONArray(prefs.getString("dias", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                historico.add(DiaMistura(o.getString("d"), o.getDouble("ls"), o.getInt("ln"), o.getDouble("rs"), o.getInt("rn")))
            }
        }
    }

    fun amostra(d: DadosObd, demonstracao: Boolean) {
        if (demonstracao != demoAtiva) { demoAtiva = demonstracao; janela.values.forEach { it.clear() } }
        val stft = d.stft ?: return
        val ltft = d.ltft ?: return
        val rpm = d.rpm ?: 0
        val vel = d.velocidade ?: 0
        val quente = (d.tempMotor ?: 0) >= 70
        val malhaFechada = d.malhaFechada != false // se o carro não informa, considera fechada
        leituraValida = rpm > 400 && quente && malhaFechada
        if (!leituraValida) return
        val regime = when {
            vel <= 3 && rpm < 1100 -> Regime.MarchaLenta
            vel >= 30 || rpm >= 1800 -> Regime.Rodando
            else -> return // transição: não ajuda o diagnóstico
        }
        val total = stft + ltft
        janela.getValue(regime).apply { addLast(total); while (size > 300) removeFirst() }
        if (!demonstracao) acumularDia(regime, total)
        recalcular(demonstracao)
    }

    private fun recalcular(demonstracao: Boolean) {
        fun media(r: Regime) = janela.getValue(r).takeIf { it.size >= 20 }?.average()?.toFloat()
        val lenta = media(Regime.MarchaLenta)
        val rodando = media(Regime.Rodando)
        mediaLenta = lenta
        mediaRodando = rodando
        val pior = listOfNotNull(lenta, rodando).maxByOrNull { abs(it) }
        val anterior = situacao
        situacao = when {
            pior == null -> SituacaoMistura.SemDados
            pior > 10f -> SituacaoMistura.Pobre
            pior < -10f -> SituacaoMistura.Rica
            abs(pior) > 5f -> SituacaoMistura.Atencao
            else -> SituacaoMistura.Normal
        }
        causas = causasProvaveis(lenta, rodando)
        if (!demonstracao && situacao != anterior && (situacao == SituacaoMistura.Pobre || situacao == SituacaoMistura.Rica)) avisar()
    }

    private fun causasProvaveis(lenta: Float?, rodando: Float?): List<String> {
        val l = lenta ?: 0f
        val r = rodando ?: 0f
        return when {
            l > 10f && r <= 5f -> listOf(
                "Pobre só em marcha lenta: típico de entrada falsa de ar (mangueira de vácuo, junta do coletor, válvula PCV).",
            )
            l > 10f || r > 10f -> listOf(
                "Pobre também rodando: combustível chegando pouco (filtro ou bomba de combustível, bico injetor sujo).",
                "Sensor de pressão/fluxo de ar lendo errado.",
                "Logo depois de trocar gasolina ↔ etanol a injeção ainda está reaprendendo: rode 20 km e veja de novo.",
            )
            l < -10f || r < -10f -> listOf(
                "Mistura rica: bico injetor pingando, pressão de combustível alta ou sonda lambda com defeito.",
                "Filtro de ar muito sujo também pode enriquecer a mistura.",
            )
            abs(l) > 5f || abs(r) > 5f -> listOf("Correção um pouco alta, mas ainda aceitável. Vale acompanhar nos próximos dias.")
            else -> emptyList()
        }
    }

    private fun avisar() {
        val agora = System.currentTimeMillis()
        if (agora - prefs.getLong("ultimo_aviso", 0L) < 7 * 86_400_000L) return
        prefs.edit().putLong("ultimo_aviso", agora).apply()
        val br = Locale("pt", "BR")
        Nuvem.obter(ctx).avisar(
            "mistura",
            "⛽ ${situacao.rotulo}: correção da injeção em %s (marcha lenta) e %s (rodando). %s".format(
                br.let { mediaLenta?.let { "%+.0f%%".format(br, it) } ?: "—" },
                mediaRodando?.let { "%+.0f%%".format(br, it) } ?: "—",
                causas.firstOrNull() ?: "",
            ),
        )
    }

    private fun acumularDia(regime: Regime, total: Float) {
        val hoje = formatoDia.format(Date())
        val i = historico.indexOfFirst { it.dia == hoje }
        val atual = if (i >= 0) historico[i] else DiaMistura(hoje, 0.0, 0, 0.0, 0)
        val novo = if (regime == Regime.MarchaLenta) atual.copy(lentaSoma = atual.lentaSoma + total, lentaN = atual.lentaN + 1)
        else atual.copy(rodandoSoma = atual.rodandoSoma + total, rodandoN = atual.rodandoN + 1)
        if (i >= 0) historico[i] = novo else { historico.add(0, novo); while (historico.size > 30) historico.removeAt(historico.lastIndex) }
        if (++amostrasNaoGravadas >= 30) { amostrasNaoGravadas = 0; gravar() }
    }

    fun limpar() { historico.clear(); janela.values.forEach { it.clear() }; recalcular(true); gravar() }

    private fun gravar() {
        val arr = JSONArray()
        historico.forEach { arr.put(JSONObject().put("d", it.dia).put("ls", it.lentaSoma).put("ln", it.lentaN).put("rs", it.rodandoSoma).put("rn", it.rodandoN)) }
        prefs.edit().putString("dias", arr.toString()).apply()
    }

    companion object {
        @Volatile private var instancia: CorrecaoCombustivel? = null
        fun obter(ctx: Context): CorrecaoCombustivel = instancia ?: synchronized(this) {
            instancia ?: CorrecaoCombustivel(ctx.applicationContext).also { instancia = it }
        }
    }
}
