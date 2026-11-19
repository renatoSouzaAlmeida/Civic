package br.com.civic.launcher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

enum class SituacaoVersao { Verificando, Atualizado, Indisponivel }

sealed interface EstadoAtualizacao {
    data object Nenhuma : EstadoAtualizacao
    data class Baixando(val versao: String) : EstadoAtualizacao
    /** [numero] = número da versão nova (ex.: 0.3.57), para comparar com a instalada. */
    data class Pronta(val versao: String, val numero: String) : EstadoAtualizacao
}

/**
 * Procura a última Release do repositório (público) e baixa o APK se for mais nova
 * que a versão instalada. A instalação em si exige um toque de confirmação do usuário
 * (o Android não permite instalar em silêncio sem ser app de sistema).
 */
class Atualizador(private val ctx: Context) {
    var estado by mutableStateOf<EstadoAtualizacao>(EstadoAtualizacao.Nenhuma)
        private set

    /** Resultado da última verificação: usado para mostrar "Atualizado" na tela inicial. */
    var situacao by mutableStateOf(SituacaoVersao.Verificando)
        private set

    private val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    private val codigoAtual = PackageInfoCompat.getLongVersionCode(info)
    val versaoAtual: String = info.versionName ?: "?"

    private val principal = Handler(Looper.getMainLooper())
    private var ocupado = false
    private var arquivoPronto: File? = null

    fun verificar() {
        if (ocupado || estado is EstadoAtualizacao.Pronta) return
        ocupado = true
        situacao = SituacaoVersao.Verificando
        thread(name = "atualizador") {
            try {
                buscarEBaixar()
            } catch (_: Exception) {
                // Sem internet (garagem) ou repositório inacessível: tenta de novo na próxima abertura.
                publicarSituacao(SituacaoVersao.Indisponivel)
            } finally {
                ocupado = false
            }
        }
    }

    private fun buscarEBaixar() {
        val resposta = abrir("https://api.github.com/repos/$REPO/releases/latest")
        if (resposta.responseCode != 200) {
            publicarSituacao(SituacaoVersao.Indisponivel)
            return
        }
        val json = JSONObject(resposta.inputStream.bufferedReader().use { it.readText() })
        val codigo = json.getString("tag_name").removePrefix("build-").toLongOrNull() ?: return
        if (codigo <= codigoAtual) {
            publicarSituacao(SituacaoVersao.Atualizado)
            return
        }

        val assets = json.getJSONArray("assets")
        var url: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name") == "Civic-Renatin.apk") url = a.getString("browser_download_url")
        }
        url ?: return
        val versao = json.optString("name", "build $codigo")

        val pasta = File(ctx.cacheDir, "atualizacoes").apply { mkdirs() }
        val destino = File(pasta, "civic-$codigo.apk")
        if (!destino.exists() || destino.length() == 0L) {
            publicar(EstadoAtualizacao.Baixando(versao))
            pasta.listFiles()?.forEach { it.delete() }
            val temp = File(pasta, "civic-$codigo.tmp")
            abrir(url).inputStream.use { entrada -> temp.outputStream().use { entrada.copyTo(it) } }
            temp.renameTo(destino)
        }
        arquivoPronto = destino
        publicar(EstadoAtualizacao.Pronta(versao, versaoAtual.substringBeforeLast('.', "0.3") + ".$codigo"))
        CentralNotificacoes.obter(ctx).registrarUmaVez("versao_$versao", "versao", "⬆️ Nova versão $versao pronta para instalar. Toque na versão no topo para atualizar.")
    }

    private fun abrir(endereco: String): HttpURLConnection =
        (URL(endereco).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "CivicLauncher")
        }

    private fun publicar(novo: EstadoAtualizacao) = principal.post { estado = novo }

    private fun publicarSituacao(nova: SituacaoVersao) = principal.post { situacao = nova }

    /** Chamado ao tocar em "Instalar". Pede, uma única vez, a permissão de instalar apps. */
    fun instalar() {
        val apk = arquivoPronto ?: return
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val instalador = ctx.packageManager.packageInstaller
        val id = instalador.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        instalador.openSession(id).use { sessao ->
            apk.inputStream().use { entrada ->
                sessao.openWrite("Civic-Renatin.apk", 0, apk.length()).use { saida ->
                    entrada.copyTo(saida)
                    sessao.fsync(saida)
                }
            }
            val pendente = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, InstalacaoReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            sessao.commit(pendente.intentSender)
        }
    }

    private companion object {
        const val REPO = "renatoSouzaAlmeida/Civic"
    }
}
