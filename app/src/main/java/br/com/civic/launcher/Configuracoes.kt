package br.com.civic.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ImagemCarro(val rotulo: String) { Civic("Civic"), Fit("Fit") }

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
    var imagemCarro by mutableStateOf(lerEnum("imagem_carro", ImagemCarro.Civic))
        private set
    var mapa by mutableStateOf(lerEnum("mapa", MapaPreferido.Waze))
        private set
    var enderecoCasa by mutableStateOf(prefs.getString("endereco_casa", "") ?: "")
        private set
    var enderecoTrabalho by mutableStateOf(prefs.getString("endereco_trabalho", "") ?: "")
        private set

    /** Segunda linha do cartão do carro: "G9 · 2012" (ou só o que estiver preenchido). */
    val modeloEAno: String get() = listOf(modelo, ano).filter { it.isNotBlank() }.joinToString(" · ")

    fun salvarNomeApp(v: String) { nomeApp = v; gravar("nome_app", v) }
    fun salvarNomeCarro(v: String) { nomeCarro = v; gravar("nome_carro", v) }
    fun salvarModelo(v: String) { modelo = v; gravar("modelo", v) }
    fun salvarAno(v: String) { ano = v; gravar("ano", v) }
    fun salvarImagem(v: ImagemCarro) { imagemCarro = v; gravar("imagem_carro", v.name) }
    fun salvarMapa(v: MapaPreferido) { mapa = v; gravar("mapa", v.name) }
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
