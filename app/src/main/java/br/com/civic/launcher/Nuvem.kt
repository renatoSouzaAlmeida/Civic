package br.com.civic.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors

/** Preços médios da ANP: gasolina comum, aditivada e etanol (R$/l). [fonte] = "municipio" ou "estado". */
data class PrecoAnp(val local: String, val fonte: String, val semanaInicio: String, val semanaFim: String, val gasolina: Float?, val aditivada: Float?, val etanol: Float?)

/**
 * Nuvem do carro (Supabase, projeto CasaTechRenatin) + avisos no celular pelo Telegram.
 *
 * - Cada aparelho gera um token secreto aleatório na primeira vez e se cadastra; as regras do banco (RLS)
 *   só deixam esse token ver e alterar os próprios dados.
 * - A chave usada aqui é a "publishable" do Supabase, feita para ficar em apps (é pública).
 *   O token do bot do Telegram NÃO fica no app: está guardado como segredo na função civic-notificar.
 * - Tudo roda em segundo plano; sem internet, nada trava — os dados continuam salvos no aparelho.
 */
class Nuvem private constructor(ctx: Context) {
    private val app = ctx.applicationContext
    private val prefs = ctx.getSharedPreferences("nuvem", Context.MODE_PRIVATE)
    private val fila = Executors.newSingleThreadExecutor()
    private val principal = Handler(Looper.getMainLooper())
    private val central = CentralNotificacoes.obter(ctx)

    val token: String = prefs.getString("token", null) ?: gerarToken().also { prefs.edit().putString("token", it).apply() }
    var codigo: String = prefs.getString("codigo", null) ?: gerarCodigo().also { prefs.edit().putString("codigo", it).apply() }
        private set

    var avisosLigados by mutableStateOf(prefs.getBoolean("avisos", true))
        private set
    var vinculado by mutableStateOf(prefs.getBoolean("vinculado", false))
        private set
    var nomeBot by mutableStateOf(prefs.getString("bot", null))
        private set
    /** Quando a nuvem respondeu com sucesso pela última vez (ms; 0 = nunca nesta abertura). */
    var ultimaConexao by mutableStateOf(0L)
        private set
    /** Texto curto do último resultado (para mostrar em Config.). */
    var situacao by mutableStateOf("")
        private set

    fun ligarAvisos(ligar: Boolean) { avisosLigados = ligar; prefs.edit().putBoolean("avisos", ligar).apply() }

    // ---------- Cadastro ----------

    private fun garantirCadastro(): Boolean {
        if (prefs.getBoolean("cadastrado", false)) return true
        repeat(3) {
            val corpo = JSONObject().put("token", token).put("nome", "Civic Renatin").put("codigo_vinculo", codigo)
            val (status, _) = http("POST", "/rest/v1/civic_dispositivos", corpo.toString(), mapOf("Prefer" to "return=minimal"))
            when (status) {
                201, 204 -> { prefs.edit().putBoolean("cadastrado", true).apply(); return true }
                409 -> {
                    // Já cadastrado com este token, ou o código colidiu com o de outro aparelho: troca o código.
                    val (s2, r2) = http("GET", "/rest/v1/civic_dispositivos?select=codigo_vinculo", null)
                    val atual = runCatching { JSONArray(r2).getJSONObject(0).getString("codigo_vinculo") }.getOrNull()
                    if (s2 == 200 && atual != null) {
                        codigo = atual; prefs.edit().putString("codigo", atual).putBoolean("cadastrado", true).apply(); return true
                    }
                    codigo = gerarCodigo(); prefs.edit().putString("codigo", codigo).apply()
                }
                else -> return false
            }
        }
        return false
    }

    // ---------- Sincronização ----------

    /** Envia o que o aparelho tem (abastecimentos, manutenção e a quilometragem do app). Cada registro leva a sua data de edição. */
    fun sincronizar(v: Veiculo) {
        val abast = v.abastecimentos.toList()
        val manut = v.manutencao.map { it to v.situacao(it) }
        val km = v.kmAtual
        fila.execute {
            if (!garantirCadastro()) return@execute
            val agora = System.currentTimeMillis()
            if (abast.isNotEmpty()) {
                val arr = JSONArray()
                abast.forEach {
                    arr.put(JSONObject().put("dispositivo", token).put("id", it.id).put("data", iso(it.data)).put("km", it.km)
                        .put("litros", it.litros.toDouble())
                        .put("preco_litro", if (it.precoEfetivo > 0f) it.precoEfetivo.toDouble() else JSONObject.NULL)
                        .put("valor_total", if (it.total > 0f) it.total.toDouble() else JSONObject.NULL)
                        .put("combustivel", it.combustivel.name).put("tanque_cheio", it.tanqueCheio)
                        .put("aditivada", it.aditivada).put("automatico", it.automatico)
                        .put("nivel_antes", it.nivelAntes ?: JSONObject.NULL).put("nivel_depois", it.nivelDepois ?: JSONObject.NULL)
                        .put("atualizado_em", iso(if (it.atualizadoEm > 0) it.atualizadoEm else agora)))
                }
                http("POST", "/rest/v1/civic_abastecimentos", arr.toString(), mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
            }
            val arr = JSONArray()
            manut.forEach { (m, s) ->
                arr.put(JSONObject().put("dispositivo", token).put("id", m.id).put("nome", m.nome)
                    .put("intervalo_km", m.intervaloKm ?: JSONObject.NULL).put("intervalo_meses", m.intervaloMeses ?: JSONObject.NULL)
                    .put("ultimo_km", m.ultimoKm ?: JSONObject.NULL).put("ultima_data", m.ultimaData?.let { iso(it) } ?: JSONObject.NULL)
                    .put("situacao", s.name).put("atualizado_em", iso(if (m.atualizadoEm > 0) m.atualizadoEm else agora)))
            }
            http("POST", "/rest/v1/civic_manutencao", arr.toString(), mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
            // Quilometragem do app (a Casa Tech escreve em km_casa_tech, outra coluna: não há disputa).
            if (km > 0) http("POST", "/rest/v1/civic_ajustes?on_conflict=dispositivo", JSONObject().put("dispositivo", token).put("km_app", km).put("km_app_em", iso(agora)).put("atualizado_em", iso(agora)).toString(),
                mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
        }
    }

    /**
     * Sincronização de mão dupla: baixa manutenção, abastecimentos e a quilometragem lançada na Casa Tech, junta com a do aparelho
     * (vale o registro editado por último), depois envia tudo de volta e confere os avisos de manutenção.
     */
    fun baixarESincronizar(v: Veiculo) {
        fila.execute {
            var manut = emptyList<ManutencaoNuvem>(); var abast = emptyList<AbastecimentoNuvem>(); var km: Pair<Int, Long>? = null
            var config: Pair<JSONObject, Long>? = null; var areasNuvem: List<AreaNuvem>? = null; var contatosNuvem: List<ContatoNuvem>? = null
            if (garantirCadastro()) {
                val (s1, t1) = http("GET", "/rest/v1/civic_manutencao?select=id,nome,intervalo_km,intervalo_meses,ultimo_km,ultima_data,atualizado_em", null)
                if (s1 == 200) manut = runCatching {
                    val arr = JSONArray(t1)
                    (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        ManutencaoNuvem(o.getString("id"), o.optString("nome"), if (o.isNull("intervalo_km")) null else o.getInt("intervalo_km"),
                            if (o.isNull("intervalo_meses")) null else o.getInt("intervalo_meses"), if (o.isNull("ultimo_km")) null else o.getInt("ultimo_km"),
                            if (o.isNull("ultima_data")) null else lerIso(o.getString("ultima_data")), lerIso(o.optString("atualizado_em")) ?: 0L)
                    }
                }.getOrDefault(emptyList())
                val (s2, t2) = http("GET", "/rest/v1/civic_abastecimentos?select=id,data,km,litros,preco_litro,valor_total,combustivel,tanque_cheio,aditivada,automatico,nivel_antes,nivel_depois,atualizado_em,removido", null)
                if (s2 == 200) abast = runCatching {
                    val arr = JSONArray(t2)
                    (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        AbastecimentoNuvem(
                            Abastecimento(
                                o.getLong("id"), lerIso(o.getString("data")) ?: 0L, o.getInt("km"), o.getDouble("litros").toFloat(),
                                if (o.isNull("preco_litro")) 0f else o.getDouble("preco_litro").toFloat(),
                                runCatching { Combustivel.valueOf(o.getString("combustivel")) }.getOrDefault(Combustivel.Gasolina), o.optBoolean("tanque_cheio", true),
                                if (o.isNull("valor_total")) 0f else o.getDouble("valor_total").toFloat(), o.optBoolean("aditivada", false), o.optBoolean("automatico", false),
                                if (o.isNull("nivel_antes")) null else o.getInt("nivel_antes"), if (o.isNull("nivel_depois")) null else o.getInt("nivel_depois"),
                                lerIso(o.optString("atualizado_em")) ?: 0L,
                            ),
                            o.optBoolean("removido", false),
                        )
                    }
                }.getOrDefault(emptyList())
                val (s3, t3) = http("GET", "/rest/v1/civic_ajustes?select=km_casa_tech,km_casa_tech_em", null)
                if (s3 == 200) km = runCatching {
                    val o = JSONArray(t3).optJSONObject(0)
                    if (o == null || o.isNull("km_casa_tech") || o.isNull("km_casa_tech_em")) null else o.getInt("km_casa_tech") to (lerIso(o.getString("km_casa_tech_em")) ?: 0L)
                }.getOrNull()
                // Configuração do veículo, áreas do geofence e contatos do SOS lançados na Casa Tech.
                val (s4, t4) = http("GET", "/rest/v1/civic_configuracao?select=valores,atualizado_em", null)
                if (s4 == 200) config = runCatching {
                    val o = JSONArray(t4).optJSONObject(0)
                    if (o == null || o.optJSONObject("valores") == null || o.getJSONObject("valores").length() == 0) null else o.getJSONObject("valores") to (lerIso(o.getString("atualizado_em")) ?: 0L)
                }.getOrNull()
                val (s5, t5) = http("GET", "/rest/v1/civic_areas?select=id,nome,lat,lon,raio_m,ativa,removida,atualizado_em", null)
                if (s5 == 200) areasNuvem = runCatching {
                    val arr = JSONArray(t5)
                    (0 until arr.length()).map { i -> arr.getJSONObject(i).let { o ->
                        AreaNuvem(Area(o.getString("id"), o.getString("nome"), o.getDouble("lat"), o.getDouble("lon"), o.getInt("raio_m"), o.optBoolean("ativa", true), lerIso(o.optString("atualizado_em")) ?: 0L), o.optBoolean("removida", false))
                    } }
                }.getOrNull()
                val (s6, t6) = http("GET", "/rest/v1/civic_contatos_sos?select=id,nome,telefone,ativo,removido,atualizado_em", null)
                if (s6 == 200) contatosNuvem = runCatching {
                    val arr = JSONArray(t6)
                    (0 until arr.length()).map { i -> arr.getJSONObject(i).let { o ->
                        ContatoNuvem(ContatoSos(o.getString("nome"), o.getString("telefone"), o.getString("id"), lerIso(o.optString("atualizado_em")) ?: 0L, o.optBoolean("ativo", true)), o.optBoolean("removido", false))
                    } }
                }.getOrNull()
            }
            principal.post {
                v.aplicarDaNuvem(manut, abast, km)
                config?.let { (valores, em) -> Configuracoes.obter(app).aplicarRemoto(valores, em) }
                areasNuvem?.let { Geofence.obter(app).aplicarDaNuvem(it) }
                contatosNuvem?.let { Sos.obter(app).aplicarDaNuvem(it) }
                sincronizar(v)
                Geofence.obter(app).sincronizar()
                Sos.obter(app).sincronizarContatos()
                enviarRetratoConfig()
                verificarManutencao(v)
            }
        }
    }

    /** Publica o retrato da configuração do veículo (a Casa Tech mostra os valores atuais e edita em `valores`). */
    private fun enviarRetratoConfig() {
        val corpo = JSONObject().put("dispositivo", token).put("valores_app", Configuracoes.obter(app).retratoRemoto()).put("app_em", iso(System.currentTimeMillis()))
        fila.execute { if (garantirCadastro()) http("POST", "/rest/v1/civic_configuracao?on_conflict=dispositivo", corpo.toString(), mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")) }
    }

    /** Envia o resumo do último estado do carro (uma linha em civic_estados). Devolve o resultado em [aoTerminar]. */
    fun enviarEstado(estado: JSONObject, aoTerminar: (Boolean) -> Unit) {
        fila.execute {
            val ok = garantirCadastro() && http(
                "POST", "/rest/v1/civic_estados?on_conflict=dispositivo,registrado_em",
                estado.put("dispositivo", token).toString(),
                mapOf("Prefer" to "resolution=ignore-duplicates,return=minimal"),
            ).first in 200..299
            principal.post { aoTerminar(ok) }
        }
    }

    /** POST genérico de linhas (JSON) numa tabela civic_*; devolve true se o servidor aceitou. Sem internet = false. */
    fun enviarJson(caminho: String, corpo: String, prefer: String, aoTerminar: (Boolean) -> Unit) {
        val canal = Sincronizacao.canalDe(caminho)
        // Tentativas graduais: canal que falhou há pouco espera (o dado continua na fila do aparelho).
        if (!Sincronizacao.podeTentar(canal)) { principal.post { aoTerminar(false) }; return }
        fila.execute {
            val ok = garantirCadastro() && http("POST", caminho, corpo, mapOf("Prefer" to "$prefer,return=minimal")).first in 200..299
            principal.post { Sincronizacao.resultado(canal, ok); aoTerminar(ok) }
        }
    }

    /** Quantos avisos (estacionamento, alertas, SOS) estão na fila sem internet. */
    fun avisosPendentes(): Int = lerFilaAvisos().length()

    fun removerAbastecimento(id: Long) {
        fila.execute {
            if (garantirCadastro()) http("DELETE", "/rest/v1/civic_abastecimentos?id=eq.$id", null)
        }
    }

    // ---------- Telegram ----------

    /** Consulta o bot e se este aparelho já está vinculado. */
    fun atualizarInfo() = funcao(JSONObject().put("acao", "info")) { r ->
        if (r == null) { situacao = "Sem conexão com a nuvem."; return@funcao }
        if (!r.optBoolean("configurado", true)) { situacao = "O bot do Telegram ainda não foi configurado no Supabase."; return@funcao }
        nomeBot = r.optString("bot").ifEmpty { null }
        definirVinculado(r.optBoolean("vinculado"))
        situacao = if (vinculado) "Vinculado ao Telegram." else "Ainda não vinculado."
        if (vinculado) registrarBot()
    }

    /** Preço médio da ANP (semana mais recente) do município; se não houver, do estado. Null = indisponível. */
    fun precoAnp(municipio: String, estado: String, aoTerminar: (PrecoAnp?) -> Unit) =
        funcao(JSONObject().put("acao", "preco_anp").put("municipio", municipio).put("estado", estado)) { r ->
            if (r == null || !r.optBoolean("encontrado")) { aoTerminar(null); return@funcao }
            fun f(k: String) = if (r.isNull(k)) null else r.optDouble(k).toFloat()
            aoTerminar(PrecoAnp(r.optString("local"), r.optString("fonte"), r.optString("semana_inicio"), r.optString("semana_fim"), f("gasolina"), f("aditivada"), f("etanol")))
        }

    /** Liga os comandos do bot (/onde, /status, /bateria, /consumo, /manutencao). No máximo uma vez por dia. */
    private fun registrarBot() {
        val agora = System.currentTimeMillis()
        if (agora - prefs.getLong("bot_registrado_em", 0L) < 24 * 3_600_000L) return
        funcao(JSONObject().put("acao", "registrar_bot")) { r ->
            if (r?.optBoolean("registrado") == true) prefs.edit().putLong("bot_registrado_em", agora).apply()
        }
    }

    fun vincular() = funcao(JSONObject().put("acao", "vincular")) { r ->
        when {
            r == null -> situacao = "Sem conexão com a nuvem."
            !r.optBoolean("configurado", true) -> situacao = "O bot do Telegram ainda não foi configurado no Supabase."
            r.optBoolean("vinculado") -> { definirVinculado(true); situacao = "Vinculado! Você recebeu uma mensagem no Telegram."; registrarBot() }
            else -> situacao = "Código não encontrado. Envie $codigo para o bot e toque em Vincular de novo."
        }
    }

    fun enviarTeste() = avisar("teste", "🚗 Teste do Civic Renatin: as notificações estão funcionando.", forcar = true)

    /**
     * Envia um aviso ao celular (se ligado). Fica registrado em civic_eventos e, por padrão,
     * também no sininho do topo do app.
     */
    fun avisar(tipo: String, mensagem: String, forcar: Boolean = false, registrarNoSino: Boolean = true,
        /** Resposta da nuvem: (enviado ao Telegram, vinculado, sem conexão). */ aoResponder: ((Boolean, Boolean, Boolean) -> Unit)? = null) {
        if (registrarNoSino && tipo != "teste") central.registrar(tipo, mensagem)
        if (!avisosLigados && !forcar) return
        funcao(JSONObject().put("acao", "enviar").put("tipo", tipo).put("mensagem", mensagem)) { r ->
            if (forcar) situacao = when {
                r == null -> "Sem conexão com a nuvem."
                r.optBoolean("enviado") -> "Mensagem de teste enviada."
                !r.optBoolean("vinculado") -> "Vincule o Telegram primeiro."
                else -> "Não foi possível enviar."
            }
            aoResponder?.invoke(r?.optBoolean("enviado") == true, r?.optBoolean("vinculado") == true, r == null)
        }
    }

    /** Avisa onde o carro foi estacionado: registra no sininho e manda o texto e o pino do mapa ao Telegram. */
    fun avisarLocalizacao(lat: Double, lon: Double, mensagem: String, data: Long = System.currentTimeMillis(), endereco: String? = null, idadeGpsS: Long = 0, manual: Boolean = false, simulado: Boolean = false,
        /** "evento" = aviso de segurança com mapa (não entra no histórico de estacionamentos). */
        origem: String? = null, tipo: String? = null, desconhecido: Boolean = false) {
        central.registrar(tipo ?: "estacionamento", mensagem.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("🧪") } ?: mensagem)
        if (!avisosLigados) return
        funcao(JSONObject().put("acao", "localizacao").put("tipo", tipo ?: if (simulado) "simulacao" else "estacionamento").put("mensagem", mensagem).put("lat", lat).put("lon", lon)
            .put("data", data).put("idade_gps_s", idadeGpsS).put("origem", origem ?: if (simulado) "simulado" else if (manual) "manual" else "auto").put("desconhecido", desconhecido).also { o -> endereco?.let { o.put("endereco", it) } }) { }
    }

    /**
     * Avisa manutenção próxima ou vencida. Cada item avisa uma vez por situação e, se continuar
     * vencido, repete a cada 7 dias (para não virar spam).
     */
    fun verificarManutencao(v: Veiculo) {
        val agora = System.currentTimeMillis()
        v.manutencao.forEach { m ->
            val s = v.situacao(m)
            if (s != SituacaoManutencao.Vencido && s != SituacaoManutencao.Proximo) return@forEach
            val chave = "aviso_${m.id}_${s.name}"
            val ultimo = prefs.getLong(chave, 0L)
            if (ultimo != 0L && (s == SituacaoManutencao.Proximo || agora - ultimo < 7 * 86_400_000L)) return@forEach
            val detalhes = listOfNotNull(
                v.kmRestante(m)?.let { if (it >= 0) "faltam %,d km".format(Locale("pt", "BR"), it) else "passou %,d km".format(Locale("pt", "BR"), -it) },
                v.diasRestantes(m)?.let { if (it >= 0) "faltam $it dias" else "venceu há ${-it} dias" },
            ).joinToString(" · ")
            val titulo = if (s == SituacaoManutencao.Vencido) "🔴 Manutenção vencida" else "🟡 Manutenção próxima"
            avisar("manutencao", "$titulo: ${m.nome}${if (detalhes.isNotEmpty()) " ($detalhes)" else ""}")
            prefs.edit().putLong(chave, agora).apply()
        }
    }

    private fun definirVinculado(sim: Boolean) { vinculado = sim; prefs.edit().putBoolean("vinculado", sim).apply() }

    private fun funcao(corpo: JSONObject, aoTerminar: (JSONObject?) -> Unit) {
        // Camada de eventos confiável: todo aviso nasce com um identificador próprio (gerado aqui, no aparelho) e a hora em que aconteceu.
        // Se a resposta se perder ou o aviso for para a fila sem internet, o reenvio leva o MESMO event_id e a nuvem não duplica.
        if (corpo.optString("acao") in ACOES_NA_FILA && !corpo.has("event_id")) {
            corpo.put("event_id", UUID.randomUUID().toString()).put("ocorreu_em", System.currentTimeMillis()).put("fonte", "app")
                .put("versao_app", runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "?").put("payload_versao", 1)
        }
        fila.execute {
            val r = if (!garantirCadastro()) null else {
                val (status, texto) = http("POST", "/functions/v1/civic-notificar", corpo.toString())
                if (status in 200..299) runCatching { JSONObject(texto) }.getOrNull() else null
            }
            // Sem internet na hora: guarda o aviso (estacionamento, alertas, SOS...) e envia quando a conexão voltar.
            if (r == null && corpo.optString("acao") in ACOES_NA_FILA) guardarPendente(corpo)
            principal.post { aoTerminar(r) }
        }
    }

    // ---------- fila de avisos sem internet ----------

    private fun lerFilaAvisos(): JSONArray = runCatching { JSONArray(prefs.getString("fila_avisos", "[]")) }.getOrDefault(JSONArray())

    private fun guardarPendente(corpo: JSONObject) {
        val a = lerFilaAvisos()
        a.put(JSONObject().put("c", corpo).put("t", System.currentTimeMillis()))
        val n = JSONArray(); for (i in maxOf(0, a.length() - 30) until a.length()) n.put(a.get(i))
        prefs.edit().putString("fila_avisos", n.toString()).apply()
    }

    /** Envia os avisos que ficaram sem internet (chamado a cada 30 s pela telemetria). Avisos com mais de 48 h são descartados. */
    fun reenviarPendentes() {
        if (!Sincronizacao.podeTentar("avisos")) return
        fila.execute {
            val a = lerFilaAvisos()
            if (a.length() == 0) return@execute
            val restantes = JSONArray()
            var parou = false
            val agora = System.currentTimeMillis()
            for (i in 0 until a.length()) {
                val item = a.getJSONObject(i)
                if (parou) { restantes.put(item); continue }
                val idade = agora - item.getLong("t")
                if (idade > 48 * 3_600_000L) continue
                val c = JSONObject(item.getJSONObject("c").toString())
                if (idade > 120_000L) c.put("mensagem", "⏱️ Enviado com atraso (${idade / 60_000} min): estava sem internet.\n" + c.optString("mensagem"))
                val ok = garantirCadastro() && http("POST", "/functions/v1/civic-notificar", c.toString()).first in 200..299
                Sincronizacao.resultado("avisos", ok)
                if (!ok) { parou = true; restantes.put(item) }
            }
            prefs.edit().putString("fila_avisos", restantes.toString()).apply()
        }
    }

    // ---------- HTTP ----------

    private fun http(metodo: String, caminho: String, corpo: String?, extras: Map<String, String> = emptyMap()): Pair<Int, String> =
        runCatching {
            val c = URL(URL_SUPABASE + caminho).openConnection() as HttpURLConnection
            c.requestMethod = metodo
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("apikey", CHAVE_PUBLICA)
            c.setRequestProperty("x-civic-token", token)
            c.setRequestProperty("Content-Type", "application/json")
            extras.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (corpo != null) { c.doOutput = true; c.outputStream.use { it.write(corpo.toByteArray()) } }
            val status = c.responseCode
            if (status in 200..299) principal.post { ultimaConexao = System.currentTimeMillis() }
            val texto = runCatching { (if (status >= 400) c.errorStream else c.inputStream)?.bufferedReader()?.readText() }.getOrNull() ?: ""
            c.disconnect()
            status to texto
        }.getOrDefault(-1 to "")

    companion object {
        private val ACOES_NA_FILA = setOf("enviar", "localizacao")
        private const val URL_SUPABASE = "https://qdmsiwxfuzppaqqnljth.supabase.co"
        private const val CHAVE_PUBLICA = "sb_publishable_ltZaPMAa7lm4dNbB5xZvLw_2NUhvVDE"

        private fun gerarToken(): String {
            val b = ByteArray(32); SecureRandom().nextBytes(b)
            return b.joinToString("") { "%02x".format(it) }
        }

        private fun gerarCodigo(): String {
            val letras = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // sem 0/O/1/I para não confundir
            val r = SecureRandom()
            return "CIVIC-" + (1..5).map { letras[r.nextInt(letras.length)] }.joinToString("")
        }

        /** Lê datas do Supabase (ex.: 2026-11-16T15:00:00+00:00). */
        private fun lerIso(texto: String): Long? = runCatching {
            java.time.OffsetDateTime.parse(texto.replace(" ", "T")).toInstant().toEpochMilli()
        }.getOrNull()

        private fun iso(ms: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

        @Volatile private var instancia: Nuvem? = null
        fun obter(ctx: Context): Nuvem = instancia ?: synchronized(this) {
            instancia ?: Nuvem(ctx.applicationContext).also { instancia = it }
        }
    }
}
