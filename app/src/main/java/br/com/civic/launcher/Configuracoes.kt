package br.com.civic.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ImagemCarro(val rotulo: String) { Civic("Civic"), Fit("Fit") }

/** Layout da tela do OBD: o atual e três propostas para comparar (a escolha final será feita em uso). */
enum class LayoutObd(val rotulo: String) {
    Atual("Esportivo"), Cockpit("Cockpit"), Modular("Modular"),
    EsportivoNovo("01 · Esportivo (novo)"), CockpitDigital("02 · Cockpit digital"), Tecnico("03 · Técnico / Diagnóstico"), DirecaoSimples("04 · Direção simplificada"),
}
/** Como o modo Direção funciona: painel flutuante sobre o mapa (padrão) ou tela dividida (mapa de um lado, cockpit do outro). */
enum class ModoDirecao(val rotulo: String) { Flutuante("Painel flutuante"), Dividido("Tela dividida") }
enum class LayoutHome(val rotulo: String) { Padrao("Padrão"), Caixas("Caixas"), Painel("Painel"), Ousada("Ousada"), Esportiva("Esportiva"), NovaLinha("Nova linha") }

/** Modelo do menu: B = barra fina embaixo; D = dock flutuante no centro. */
enum class EstiloMenu(val rotulo: String) { Barra("Barra fina"), Dock("Dock flutuante") }

enum class MapaPreferido(val rotulo: String, val pacote: String) {
    Waze("Waze", "com.waze"),
    GoogleMaps("Google Maps", "com.google.android.apps.maps"),
}

/**
 * Configurações escolhidas pelo usuário (tela Config.). Cada campo é um estado do Compose,
 * então a interface se atualiza na hora, e é gravado no aparelho a cada mudança.
 */
class Configuracoes private constructor(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("config", Context.MODE_PRIVATE)

    var nomeApp by mutableStateOf(prefs.getString("nome_app", NOME_APP_PADRAO) ?: NOME_APP_PADRAO)
        private set
    var nomeCarro by mutableStateOf(prefs.getString("nome_carro", "Seu Civic") ?: "")
        private set
    var modelo by mutableStateOf(prefs.getString("modelo", "G9") ?: "")
        private set
    var ano by mutableStateOf(prefs.getString("ano", "2012") ?: "")
        private set
    /** Placa só com letras e números, em maiúsculas (ex.: ABC1D23 ou ABC1234). Fica só no aparelho. */
    /** Nome de quem usa, para a saudação da Tela Início ("Boa tarde, Renato"). Vazio = só "Boa tarde". */
    var nomeUsuario by mutableStateOf(prefs.getString("nome_usuario", "") ?: "")
    var placa by mutableStateOf(prefs.getString("placa", "") ?: "")
        private set
    var imagemCarro by mutableStateOf(lerEnum("imagem_carro", ImagemCarro.Civic))
        private set
    var mapa by mutableStateOf(lerEnum("mapa", MapaPreferido.Waze))
        private set
    var estiloMenu by mutableStateOf(lerEnum("estilo_menu", EstiloMenu.Barra))
        private set
    /** Modo leve (multimídia mais simples, ex.: Honda Fit): sem animações contínuas nem vibração de toque. */
    var modoLeve by mutableStateOf(prefs.getBoolean("modo_leve", false))
        private set
    /** Capacidade do tanque em litros; 0 = padrão do carro (Civic 56 l, Fit 45 l). Usada para estimar os litros pelo nível. */
    var capacidadeTanque by mutableStateOf(prefs.getInt("capacidade_tanque", 0))
        private set
    val capacidadeEfetiva: Int get() = if (capacidadeTanque > 0) capacidadeTanque else if (imagemCarro == ImagemCarro.Fit) 45 else 56
    /** Alertas falados (voz) nos avisos do motor, fadiga e colisão. */
    var vozAlertas by mutableStateOf(prefs.getBoolean("voz_alertas", true))
        private set
    /** Caixa-preta digital: liga/desliga e as janelas (segundos antes e depois do evento). */
    var caixaPretaAtiva by mutableStateOf(prefs.getBoolean("cp_ativa", true))
        private set
    var caixaPretaPreS by mutableStateOf(prefs.getInt("cp_pre_s", 30))
        private set
    var caixaPretaPosS by mutableStateOf(prefs.getInt("cp_pos_s", 15))
        private set
    /** TPMS (pressão dos pneus por sensores Bluetooth): desligado até o dono ter os sensores. */
    var tpmsAtivo by mutableStateOf(prefs.getBoolean("tpms_ativo", false))
        private set
    /** Pressão ideal dos pneus em psi (Civic G9: 32) e unidade de exibição. */
    var tpmsIdealPsi by mutableStateOf(prefs.getInt("tpms_ideal_psi", 32))
        private set
    var tpmsEmBar by mutableStateOf(prefs.getBoolean("tpms_bar", false))
        private set
    /** Lista todos os aparelhos BLE com dados (para descobrir sensores de outro modelo). */
    var tpmsMostrarTodos by mutableStateOf(prefs.getBoolean("tpms_todos", false))
        private set
    /** Geofence: avisar no Telegram e/ou mostrar a faixa quando o carro sai ou entra de uma área. */
    var geofenceTelegram by mutableStateOf(prefs.getBoolean("geofence_telegram", true))
        private set
    var geofenceFaixa by mutableStateOf(prefs.getBoolean("geofence_faixa", true))
    /** Abrir o launcher sozinho quando a multimídia liga (algumas multimídias abrem a tela do fabricante por cima). No Fit com CarPlay, desligar. */
    var abrirAoLigar by mutableStateOf(prefs.getBoolean("abrir_ao_ligar", true))
        private set
    /** Limite de velocidade (km/h) para contar excessos na viagem; 0 = não contar. */
    var limiteVelocidade by mutableStateOf(prefs.getInt("limite_velocidade", 110))
        private set
    /** Vigia total: avisa no Telegram toda partida do motor (para quando o carro ficar parado em local de risco). */
    var vigiaTotal by mutableStateOf(prefs.getBoolean("vigia_total", false))
        private set
    /** Modo HUD espelhado (para refletir no para-brisa). Segurar a tela no HUD alterna. */
    var hudEspelhado by mutableStateOf(prefs.getBoolean("hud_espelhado", true))
        private set
    var layoutObd by mutableStateOf(lerEnum("layout_obd", LayoutObd.Atual))
    var modoDirecao by mutableStateOf(lerEnum("modo_direcao", ModoDirecao.Flutuante))
    var layoutHome by mutableStateOf(lerEnum("layout_home", LayoutHome.Padrao))
        private set
    var enderecoCasa by mutableStateOf(prefs.getString("endereco_casa", "") ?: "")
        private set
    var enderecoTrabalho by mutableStateOf(prefs.getString("endereco_trabalho", "") ?: "")
        private set

    /** Segunda linha do cartão do carro: "G9 · 2012" (ou só o que estiver preenchido). */
    val modeloEAno: String get() = listOf(modelo, ano).filter { it.isNotBlank() }.joinToString(" · ")

    /**
     * Retrato dos dados do veículo e das regras de segurança que a Casa Tech pode editar (as preferências de tela, tema e layouts ficam só no launcher).
     * Vai para a nuvem em civic_configuracao.valores_app, para a Casa Tech mostrar os valores atuais.
     */
    fun retratoRemoto(): org.json.JSONObject = org.json.JSONObject()
        .put("nome_carro", nomeCarro).put("modelo", modelo).put("ano", ano).put("placa", placa).put("imagem_carro", imagemCarro.name)
        .put("capacidade_tanque", capacidadeTanque).put("endereco_casa", enderecoCasa).put("endereco_trabalho", enderecoTrabalho).put("mapa", mapa.name)
        .put("limite_velocidade", limiteVelocidade).put("vigia_total", vigiaTotal).put("voz_alertas", vozAlertas).put("tpms_ideal_psi", tpmsIdealPsi)
        .put("geofence_telegram", geofenceTelegram).put("geofence_faixa", geofenceFaixa)

    /** Aplica o que a Casa Tech definiu (uma vez por edição lá). A Casa Tech manda: vale o que foi salvo por último. */
    fun aplicarRemoto(valores: org.json.JSONObject, em: Long): Boolean {
        if (em <= prefs.getLong("config_remota_em", 0L)) return false
        valores.keys().forEach { k ->
            runCatching {
                when (k) {
                    "nome_carro" -> salvarNomeCarro(valores.getString(k)); "modelo" -> salvarModelo(valores.getString(k)); "ano" -> salvarAno(valores.getString(k))
                    "placa" -> salvarPlaca(valores.getString(k)); "imagem_carro" -> salvarImagem(ImagemCarro.valueOf(valores.getString(k)))
                    "capacidade_tanque" -> salvarCapacidade(valores.getInt(k).toString()); "endereco_casa" -> salvarEnderecoCasa(valores.getString(k))
                    "endereco_trabalho" -> salvarEnderecoTrabalho(valores.getString(k)); "mapa" -> salvarMapa(MapaPreferido.valueOf(valores.getString(k)))
                    "limite_velocidade" -> salvarLimiteVelocidade(valores.getInt(k)); "vigia_total" -> salvarVigia(valores.getBoolean(k))
                    "voz_alertas" -> salvarVoz(valores.getBoolean(k)); "tpms_ideal_psi" -> salvarTpmsIdeal(valores.getInt(k))
                    "geofence_telegram" -> salvarGeofenceTelegram(valores.getBoolean(k)); "geofence_faixa" -> salvarGeofenceFaixa(valores.getBoolean(k))
                }
            }
        }
        prefs.edit().putLong("config_remota_em", em).apply()
        return true
    }

    fun salvarNomeApp(v: String) { nomeApp = v; gravar("nome_app", v) }
    fun salvarNomeCarro(v: String) { nomeCarro = v; gravar("nome_carro", v) }
    fun salvarNomeUsuario(v: String) { nomeUsuario = v.take(40); gravar("nome_usuario", nomeUsuario) }
    fun salvarModelo(v: String) { modelo = v; gravar("modelo", v) }
    fun salvarAno(v: String) { ano = v; gravar("ano", v) }
    fun salvarPlaca(v: String) {
        val limpa = v.uppercase().filter { it.isLetterOrDigit() && it.code < 128 }.take(7)
        placa = limpa; gravar("placa", limpa)
    }

    /** Placa para exibir: modelo antigo com hífen (ABC-1234); Mercosul como está (ABC1D23). */
    val placaFormatada: String get() =
        if (Regex("^[A-Z]{3}[0-9]{4}$").matches(placa)) placa.substring(0, 3) + "-" + placa.substring(3) else placa
    fun salvarImagem(v: ImagemCarro) { imagemCarro = v; gravar("imagem_carro", v.name) }
    fun salvarModoLeve(v: Boolean) { modoLeve = v; prefs.edit().putBoolean("modo_leve", v).apply() }
    fun salvarCapacidade(v: String) { capacidadeTanque = v.filter { it.isDigit() }.take(3).toIntOrNull() ?: 0; prefs.edit().putInt("capacidade_tanque", capacidadeTanque).apply() }
    fun salvarVoz(v: Boolean) { vozAlertas = v; prefs.edit().putBoolean("voz_alertas", v).apply() }
    fun salvarCaixaPreta(ativa: Boolean) { caixaPretaAtiva = ativa; prefs.edit().putBoolean("cp_ativa", ativa).apply() }
    fun salvarCaixaPretaJanelas(pre: Int, pos: Int) { caixaPretaPreS = pre; caixaPretaPosS = pos; prefs.edit().putInt("cp_pre_s", pre).putInt("cp_pos_s", pos).apply() }
    fun salvarTpmsAtivo(v: Boolean) { tpmsAtivo = v; prefs.edit().putBoolean("tpms_ativo", v).apply() }
    fun salvarTpmsIdeal(v: Int) { tpmsIdealPsi = v; prefs.edit().putInt("tpms_ideal_psi", v).apply() }
    fun salvarTpmsBar(v: Boolean) { tpmsEmBar = v; prefs.edit().putBoolean("tpms_bar", v).apply() }
    fun salvarTpmsTodos(v: Boolean) { tpmsMostrarTodos = v; prefs.edit().putBoolean("tpms_todos", v).apply() }
    fun salvarGeofenceTelegram(v: Boolean) { geofenceTelegram = v; prefs.edit().putBoolean("geofence_telegram", v).apply() }
    fun salvarGeofenceFaixa(v: Boolean) { geofenceFaixa = v; prefs.edit().putBoolean("geofence_faixa", v).apply() }
    fun salvarAbrirAoLigar(v: Boolean) { abrirAoLigar = v; prefs.edit().putBoolean("abrir_ao_ligar", v).apply() }
    fun salvarLimiteVelocidade(v: Int) { limiteVelocidade = v; prefs.edit().putInt("limite_velocidade", v).apply() }
    fun salvarVigia(v: Boolean) { vigiaTotal = v; prefs.edit().putBoolean("vigia_total", v).apply() }
    fun salvarHudEspelhado(v: Boolean) { hudEspelhado = v; prefs.edit().putBoolean("hud_espelhado", v).apply() }
    fun salvarLayoutObd(v: LayoutObd) { layoutObd = v; gravar("layout_obd", v.name) }
    fun salvarModoDirecao(v: ModoDirecao) { modoDirecao = v; gravar("modo_direcao", v.name) }
    fun salvarLayoutHome(v: LayoutHome) { layoutHome = v; gravar("layout_home", v.name) }
    fun salvarMapa(v: MapaPreferido) { mapa = v; gravar("mapa", v.name) }
    fun salvarEstiloMenu(v: EstiloMenu) { estiloMenu = v; gravar("estilo_menu", v.name) }
    fun salvarEnderecoCasa(v: String) { enderecoCasa = v; gravar("endereco_casa", v) }
    fun salvarEnderecoTrabalho(v: String) { enderecoTrabalho = v; gravar("endereco_trabalho", v) }

    private fun gravar(chave: String, valor: String) = prefs.edit().putString(chave, valor).apply()

    private inline fun <reified T : Enum<T>> lerEnum(chave: String, padrao: T): T =
        runCatching { enumValueOf<T>(prefs.getString(chave, null) ?: "") }.getOrDefault(padrao)

    companion object {
        const val NOME_APP_PADRAO = "CIVIC RENATIN"

        @Volatile private var instancia: Configuracoes? = null

        fun obter(ctx: Context): Configuracoes =
            instancia ?: synchronized(this) { instancia ?: Configuracoes(ctx.applicationContext).also { instancia = it } }
    }
}
