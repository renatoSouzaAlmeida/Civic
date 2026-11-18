package br.com.civic.launcher

import android.content.Context

/** Apps fixados pelo usuário (ex.: o app de Rádio ou de Câmera da multimídia). */
object Atalhos {
    const val TELEFONE = "telefone"
    const val RADIO = "radio"
    const val CAMERA = "camera"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("atalhos", Context.MODE_PRIVATE)

    fun obter(ctx: Context, chave: String): String? = prefs(ctx).getString(chave, null)

    fun fixar(ctx: Context, chave: String, pacote: String) {
        prefs(ctx).edit().putString(chave, pacote).apply()
    }
}
