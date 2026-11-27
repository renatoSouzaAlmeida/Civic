package br.com.civic.launcher.obd

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.civic.launcher.Nuvem
import org.json.JSONArray
import org.json.JSONObject

/** Uma linha da varredura: o que foi perguntado ao carro, a resposta bruta e o valor traduzido (quando conhecido). */
data class LinhaVarredura(val codigo: String, val nome: String, val bruto: String, val valor: String?)

/** Um passo do teste guiado: o que o motorista faz enquanto o app escuta o CAN por 5 s. */
enum class PassoAnalise(val chave: String, val rotulo: String, val instrucao: String) {
    Base("base", "Base", "Tudo desligado (setas, faróis, freio solto) e alavanca em P."),
    SetaEsquerda("seta_esquerda", "Seta esquerda", "Ligue a seta para a ESQUERDA e deixe piscando."),
    SetaDireita("seta_direita", "Seta direita", "Ligue a seta para a DIREITA e deixe piscando."),
    Alerta("alerta", "Pisca-alerta", "Ligue o pisca-alerta."),
    Farolete("farolete", "Lanterna", "Ligue só a lanterna (farolete)."),
    FarolBaixo("farol_baixo", "Farol baixo", "Ligue o farol baixo."),
    FarolAlto("farol_alto", "Farol alto", "Ligue o farol alto."),
    Freio("freio", "Freio", "Pise e segure o pedal do freio (alavanca em P)."),
    MarchaR("marcha_r", "Marcha R", "Com o pé no freio, coloque em R (ré)."),
    MarchaN("marcha_n", "Marcha N", "Com o pé no freio, coloque em N (neutro)."),
    MarchaD("marcha_d", "Marcha D", "Com o pé no freio, coloque em D."),
    MarchaP("marcha_p", "Volta ao P", "Com o pé no freio, volte para P."),
    Porta("porta", "Porta aberta", "Abra a porta do motorista."),
    Cinto("cinto", "Cinto", "Afivele o cinto do motorista."),
}

/**
 * Análise do adaptador OBD: descobre tudo o que o carro informa pela tomada.
 * 1) Varredura: PIDs padrão suportados (modo 01, incluindo marcha 01A4 e hodômetro 01A6), VIN e módulos (modo 09) e dados do adaptador.
 * 2) Teste guiado: em cada passo (seta, farol, marcha…) o app escuta o CAN e compara com a base, mostrando quais bytes mudaram.
 * O relatório vai para a nuvem (tabela civic_relatorios) para análise.
 */
class AnaliseAdaptador private constructor(private val ctx: Context) {
    var ocupado by mutableStateOf(false)
    var situacao by mutableStateOf<String?>(null)
    var varredura by mutableStateOf<List<LinhaVarredura>>(emptyList())
    var varreduraEm by mutableStateOf(0L)
    /** Por passo: ID CAN → para cada byte, os valores vistos. */
    val capturas = mutableStateMapOf<PassoAnalise, Map<String, List<Set<Int>>>>()
    var enviadoEm by mutableStateOf(0L)

    /** Resume os quadros de uma captura: por ID, os valores distintos de cada byte (no máximo 32 por byte). */
    fun resumir(quadros: Map<String, List<IntArray>>): Map<String, List<Set<Int>>> = quadros.mapValues { (_, lista) ->
        val n = lista.maxOfOrNull { it.size } ?: 0
        List(n) { i -> lista.mapNotNull { it.getOrNull(i) }.toSortedSet().take(32).toSet() }
    }

    /** O que mudou num passo em relação à base: "ID byte: base → passo". IDs novos ou que sumiram também aparecem. */
    fun diferencas(passo: PassoAnalise): List<String> {
        val base = capturas[PassoAnalise.Base] ?: return emptyList()
        val p = capturas[passo] ?: return emptyList()
        val saida = ArrayList<String>()
        for (id in (base.keys + p.keys).sorted()) {
            val b = base[id]; val q = p[id]
            if (b == null) { saida += "ID $id apareceu"; continue }
            if (q == null) { saida += "ID $id sumiu"; continue }
            for (i in 0 until maxOf(b.size, q.size)) {
                val vb = b.getOrNull(i) ?: emptySet(); val vq = q.getOrNull(i) ?: emptySet()
                if (vb == vq) continue
                // Byte que já varia muito na base (contador, checksum) não ajuda: ignora.
                if (vb.size > 6 && vq.size > 6) continue
                saida += "ID $id byte $i: ${hex(vb)} → ${hex(vq)}"
            }
        }
        return saida
    }

    private fun hex(s: Set<Int>) = if (s.isEmpty()) "—" else s.take(6).joinToString("/") { "%02X".format(it) } + if (s.size > 6) "…(${s.size})" else ""

    fun relatorio(): JSONObject {
        val j = JSONObject().put("gerado_em", System.currentTimeMillis())
        j.put("varredura", JSONArray().apply {
            varredura.forEach { put(JSONObject().put("codigo", it.codigo).put("nome", it.nome).put("bruto", it.bruto).put("valor", it.valor ?: JSONObject.NULL)) }
        })
        val cap = JSONObject()
        capturas.forEach { (passo, ids) ->
            val o = JSONObject()
            ids.forEach { (id, bytes) -> o.put(id, JSONArray().apply { bytes.forEach { put(JSONArray(it.sorted())) } }) }
            cap.put(passo.chave, o)
        }
        j.put("capturas", cap)
        j.put("diferencas", JSONObject().apply { PassoAnalise.entries.drop(1).forEach { p -> if (capturas[p] != null) put(p.chave, JSONArray(diferencas(p))) } })
        return j
    }

    fun enviar() {
        if (varredura.isEmpty() && capturas.isEmpty()) { situacao = "Nada para enviar ainda: rode a varredura ou um passo do teste."; return }
        situacao = "Enviando relatório para a nuvem…"
        val corpo = JSONObject().put("dispositivo", Nuvem.obter(ctx).token).put("tipo", "analise_adaptador").put("dados", relatorio())
        Nuvem.obter(ctx).enviarJson("/rest/v1/civic_relatorios", corpo.toString(), "return=minimal") { ok ->
            if (ok) enviadoEm = System.currentTimeMillis()
            situacao = if (ok) "Relatório enviado. Já dá para analisar na nuvem." else "Não foi possível enviar agora (sem internet?). Tente de novo."
        }
    }

    fun limpar() { varredura = emptyList(); capturas.clear(); situacao = null; enviadoEm = 0L }

    companion object {
        @Volatile private var instancia: AnaliseAdaptador? = null
        fun obter(ctx: Context): AnaliseAdaptador = instancia ?: synchronized(this) {
            instancia ?: AnaliseAdaptador(ctx.applicationContext).also { instancia = it }
        }

        /** Nomes dos PIDs do modo 01 que interessam (o resto aparece como "PID xx"). */
        val NOMES = mapOf(
            0x01 to "Monitores / luz de injeção", 0x03 to "Sistema de combustível", 0x04 to "Carga do motor", 0x05 to "Temp. do motor",
            0x06 to "Correção curta B1", 0x07 to "Correção longa B1", 0x0B to "Pressão do coletor", 0x0C to "Rotação", 0x0D to "Velocidade",
            0x0E to "Avanço da ignição", 0x0F to "Temp. de admissão", 0x10 to "MAF (vazão de ar)", 0x11 to "Acelerador",
            0x13 to "Sondas presentes", 0x14 to "Sonda 1", 0x15 to "Sonda 2", 0x1C to "Norma OBD", 0x1F to "Tempo desde a partida",
            0x21 to "Km com luz de injeção", 0x2E to "Purga EVAP", 0x2F to "Nível do tanque", 0x30 to "Aquecimentos desde apagar",
            0x31 to "Km desde apagar códigos", 0x33 to "Pressão barométrica", 0x34 to "Sonda larga 1", 0x3C to "Temp. do catalisador",
            0x42 to "Tensão do módulo", 0x43 to "Carga absoluta", 0x44 to "Lambda comandado", 0x45 to "Acelerador relativo",
            0x46 to "Temp. ambiente", 0x47 to "Acelerador B", 0x49 to "Pedal D", 0x4A to "Pedal E", 0x4C to "Borboleta comandada",
            0x51 to "Tipo de combustível", 0x52 to "% de etanol", 0x5A to "Pedal relativo", 0x5C to "Temp. do óleo",
            0x5E to "Vazão de combustível", 0x61 to "Torque pedido", 0x62 to "Torque real", 0x63 to "Torque de referência",
            0xA4 to "Marcha atual (câmbio)", 0xA6 to "Hodômetro",
        )

        /** Traduz os PIDs mais úteis. Null = só o bruto. */
        fun traduzir(pid: Int, b: IntArray): String? {
            fun u16() = if (b.size >= 2) b[0] * 256 + b[1] else null
            val a = b.getOrNull(0) ?: return null
            return when (pid) {
                0x04, 0x11, 0x2F, 0x45, 0x47, 0x49, 0x4A, 0x4C, 0x52, 0x5A -> "%.0f %%".format(a * 100 / 255.0)
                0x05, 0x0F, 0x46, 0x5C -> "${a - 40} °C"
                0x0C -> u16()?.let { "${it / 4} rpm" }
                0x0D -> "$a km/h"
                0x0B, 0x33 -> "$a kPa"
                0x10 -> u16()?.let { "%.2f g/s".format(it / 100.0) }
                0x1F -> u16()?.let { "$it s" }
                0x21, 0x31 -> u16()?.let { "$it km" }
                0x42 -> u16()?.let { "%.2f V".format(it / 1000.0) }
                0x51 -> when (a) { 1 -> "Gasolina"; 4 -> "Diesel"; 9 -> "Flex gasolina"; 10 -> "Flex etanol"; 8 -> "Etanol"; else -> "código $a" }
                0x1C -> when (a) { 1 -> "OBD-II (EUA)"; 6 -> "EOBD"; 7 -> "EOBD + OBD-II"; 3 -> "OBD + OBD-II"; else -> "código $a" }
                0x61, 0x62 -> "${a - 125} %"
                0x63 -> u16()?.let { "$it Nm" }
                0xA4 -> if (b.size >= 4) {
                    // A bit 1 = marcha informada; B bits 7-4 = marcha (0 = neutro/P); C,D = relação ×1000.
                    val marcha = (b[1] shr 4) and 0x0F
                    val rel = (b[2] * 256 + b[3]) / 1000.0
                    "marcha $marcha · relação %.3f%s".format(rel, if (a and 0x02 == 0) " (sem marcha informada)" else "")
                } else null
                0xA6 -> if (b.size >= 4) "%.1f km".format(((b[0].toLong() shl 24) or (b[1].toLong() shl 16) or (b[2].toLong() shl 8) or b[3].toLong()) / 10.0) else null
                else -> null
            }
        }
    }
}
