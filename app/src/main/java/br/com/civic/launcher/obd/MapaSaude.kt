package br.com.civic.launcher.obd

import android.content.Context
import br.com.civic.launcher.Configuracoes
import br.com.civic.launcher.Tpms
import java.util.Locale

enum class NivelSaude(val rotulo: String) { SemDados("Sem dados"), Ok("Normal"), Atencao("Atenção"), Alerta("Alerta") }

/** Um subsistema do carro: estado, o dado observado, de onde veio, quão confiável é e a idade do dado. */
data class Subsistema(val nome: String, val nivel: NivelSaude, val observado: String, val fonte: String, val confianca: String, val idade: String? = null)

/**
 * Mapa de saúde do veículo: junta o que o app já sabe de cada subsistema (falhas, arrefecimento, mistura, bateria e alternador,
 * câmbio, marcha lenta, pneus). Cada linha diz o dado observado, a fonte e a confiança; sem dado aparece "Sem dados", nunca "normal".
 * É indício para conversar com a oficina, não diagnóstico.
 */
object MapaSaude {
    private val br = Locale("pt", "BR")

    fun montar(ctx: Context, obd: ObdMonitor, agora: Long = System.currentTimeMillis()): List<Subsistema> {
        val d = obd.dados
        val demo = obd.estado == EstadoObd.Demonstracao
        val conectado = obd.estado == EstadoObd.Conectado || demo
        val respondendo = conectado && (demo || obd.ultimaRespostaEm == 0L || agora - obd.ultimaRespostaEm < 10_000)
        val motorLigado = (d.rpm ?: 0) > 400
        val dg = DiagnosticoAvancado.obter(ctx)
        val sufixoDemo = if (demo) " (demonstração)" else ""
        val itens = mutableListOf<Subsistema>()

        fun idadeUltimaLeitura(): String? = if (!conectado && obd.desconectouEm > 0) "última leitura há ${minutos(agora - obd.desconectouEm)}" else null

        // Falhas e luz da injeção.
        itens += if (!respondendo) Subsistema("Injeção e falhas (DTC)", NivelSaude.SemDados, "O adaptador não está respondendo.", "OBD (modos 03 e 07)", "—", idadeUltimaLeitura())
        else {
            val nCod = d.codigos.size; val nPend = d.codigosPendentes.size
            when {
                d.luzMotor || nCod > 0 -> Subsistema("Injeção e falhas (DTC)", NivelSaude.Alerta, "${if (d.luzMotor) "Luz de injeção acesa. " else ""}$nCod código(s) confirmado(s), $nPend pendente(s)$sufixoDemo.", "OBD (modos 03 e 07)", "alta")
                nPend > 0 -> Subsistema("Injeção e falhas (DTC)", NivelSaude.Atencao, "$nPend código(s) pendente(s), ainda não confirmados$sufixoDemo.", "OBD (modo 07)", "média")
                else -> Subsistema("Injeção e falhas (DTC)", NivelSaude.Ok, "Nenhum código e luz apagada$sufixoDemo.", "OBD (modos 03 e 07)", "alta")
            }
        }

        // Arrefecimento.
        val temp = d.tempMotor
        val aquec = dg.medianaAquecimento()
        itens += if (!respondendo || temp == null) Subsistema("Arrefecimento", NivelSaude.SemDados, "Sem leitura de temperatura agora.", "OBD (PID 0105)", "—", idadeUltimaLeitura())
        else {
            val nivel = when { temp >= 108 -> NivelSaude.Alerta; temp >= 100 -> NivelSaude.Atencao; else -> NivelSaude.Ok }
            val extra = aquec?.let { " Aquecimento típico: ${it / 60} min ${it % 60} s." }.orEmpty()
            Subsistema("Arrefecimento", nivel, "Motor a $temp °C" + (if (temp < 70 && motorLigado) " (ainda aquecendo)" else "") + "." + extra + sufixoDemo, "OBD (PID 0105)", "alta")
        }

        // Mistura (fuel trims).
        val cc = CorrecaoCombustivel.obter(ctx)
        itens += when (cc.situacao) {
            SituacaoMistura.SemDados -> Subsistema("Mistura e injeção", NivelSaude.SemDados, "Ainda coletando leituras (motor quente e malha fechada).", "OBD (correção de combustível)", "—")
            SituacaoMistura.Normal -> Subsistema("Mistura e injeção", NivelSaude.Ok, "Correção normal (lenta ${fmt(cc.mediaLenta)}%, rodando ${fmt(cc.mediaRodando)}%).", "OBD (fuel trims, média dos dias)", "média")
            SituacaoMistura.Atencao -> Subsistema("Mistura e injeção", NivelSaude.Atencao, "Correção fora do ideal (lenta ${fmt(cc.mediaLenta)}%, rodando ${fmt(cc.mediaRodando)}%).", "OBD (fuel trims, média dos dias)", "média")
            SituacaoMistura.Pobre, SituacaoMistura.Rica -> Subsistema("Mistura e injeção", NivelSaude.Alerta, "${cc.situacao.rotulo} (lenta ${fmt(cc.mediaLenta)}%, rodando ${fmt(cc.mediaRodando)}%).", "OBD (fuel trims, média dos dias)", "média")
        }

        // Bateria e alternador.
        val sb = SaudeBateria.obter(ctx)
        val reais = sb.partidas.count { !it.demonstracao }
        val achados = SaudeBateria.analise(sb.partidas.toList())
        val pior = achados.maxOfOrNull { it.nivel } ?: 0
        itens += if (reais < 3) Subsistema("Bateria e alternador", NivelSaude.SemDados, "Poucas partidas reais registradas ($reais de 3).", "OBD (tensão no conector)", "—", sb.ultimaTensao?.let { "agora %.1f V".format(br, it) })
        else {
            val principal = achados.filter { it.nivel == pior }.first()
            Subsistema("Bateria e alternador", when (pior) { 2 -> NivelSaude.Alerta; 1 -> NivelSaude.Atencao; else -> NivelSaude.Ok }, principal.titulo + ".", "OBD (tensão nas partidas)", if (reais >= 15) "alta" else if (reais >= 6) "média" else "baixa")
        }

        // Câmbio.
        itens += if (!dg.cambio.ativo) Subsistema("Câmbio", NivelSaude.SemDados, "Temperatura do fluido não configurada (PID da Honda).", "OBD (PID personalizado)", "—")
        else {
            val t = dg.tempCambio
            if (t == null || !respondendo) Subsistema("Câmbio", NivelSaude.SemDados, "Sem leitura da temperatura do fluido agora.", "OBD (PID personalizado)", "—", idadeUltimaLeitura())
            else Subsistema("Câmbio", when { t >= 115 -> NivelSaude.Alerta; t >= 105 -> NivelSaude.Atencao; else -> NivelSaude.Ok }, "Fluido a $t °C.", "OBD (PID personalizado)", "média")
        }

        // Marcha lenta.
        itens += dg.marchaLenta.let { m ->
            if (m == null) Subsistema("Marcha lenta", NivelSaude.SemDados, "Ainda sem avaliação (precisa de marcha lenta com motor quente).", "OBD (rpm)", "—")
            else Subsistema("Marcha lenta", if (m.irregular) NivelSaude.Atencao else NivelSaude.Ok, "${m.media} rpm, oscilação de ${"%.0f".format(br, m.desvio)} rpm" + if (m.irregular) " (irregular)." else ".", "OBD (rpm, avaliada há ${minutos(agora - m.em)})", "média")
        }

        // Pneus (só com o TPMS ativado).
        if (Configuracoes.obter(ctx).tpmsAtivo) {
            val tpms = Tpms.obter(ctx)
            val ideal = Configuracoes.obter(ctx).tpmsIdealPsi.toFloat()
            val ls = Tpms.POSICOES.mapNotNull { (pos, nome) -> tpms.leitura(pos)?.takeIf { it.plausivel && agora - it.em < 30 * 60_000L && it.psi != null }?.let { nome to it } }
            itens += if (ls.isEmpty()) Subsistema("Pneus (TPMS)", NivelSaude.SemDados, "Nenhum sensor lido nos últimos 30 min.", "Sensores BLE (experimental)", "—")
            else {
                val ruins = ls.filter { (_, l) -> l.psi!! < ideal * 0.85f || l.psi!! > ideal * 1.25f || (l.tempC ?: 0f) > 85f }
                Subsistema("Pneus (TPMS)", if (ruins.isEmpty()) NivelSaude.Ok else NivelSaude.Alerta,
                    if (ruins.isEmpty()) "${ls.size} de 4 pneus lidos, todos na faixa." else ruins.joinToString("; ") { (n, l) -> "$n ${"%.0f".format(br, l.psi)} psi" } + " fora da faixa.",
                    "Sensores BLE (experimental, leitor não validado com hardware)", if (ls.size == 4) "média" else "baixa")
            }
        }
        return itens
    }

    private fun fmt(v: Float?) = v?.let { "%+.1f".format(br, it) } ?: "—"
    private fun minutos(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return if (s < 90) "$s s" else if (s < 5400) "${s / 60} min" else if (s < 172_800) "${s / 3600} h" else "${s / 86_400} dias" }
}
