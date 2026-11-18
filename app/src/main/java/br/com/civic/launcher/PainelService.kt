package br.com.civic.launcher

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import br.com.civic.launcher.ui.CivicTheme
import br.com.civic.launcher.ui.ModoTema
import br.com.civic.launcher.obd.ObdMonitor
import br.com.civic.launcher.ui.FaixaInferior
import br.com.civic.launcher.ui.FaixaSuperior
import java.util.Calendar

/**
 * Modo Direção: duas faixas sobrepostas ao Waze, que fica no meio (~70% da tela).
 * Faixa de cima: clima, hora e velocidade (+ Início e Fechar). Faixa de baixo: Spotify.
 */
class PainelService : Service() {
    private lateinit var janela: WindowManager
    private lateinit var sensores: Sensores
    private lateinit var monitor: MediaMonitor
    private lateinit var obd: ObdMonitor
    private val dono = Dono()
    private var faixaTopo: ComposeView? = null
    private var faixaBase: ComposeView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        janela = getSystemService(WindowManager::class.java)
        sensores = Sensores(this)
        monitor = MediaMonitor(this)
        obd = ObdMonitor.obter(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        sensores.iniciar()
        monitor.iniciar()
        mostrar()
        return START_NOT_STICKY
    }

    private fun mostrar() {
        remover()
        val altura = resources.displayMetrics.heightPixels
        faixaTopo = adicionar(Gravity.TOP, (altura * 0.12f).toInt()) {
            FaixaSuperior(
                sensores, obd,
                aoVoltarInicio = {
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    stopSelf()
                },
                aoFechar = { stopSelf() },
            )
        }
        faixaBase = adicionar(Gravity.BOTTOM, (altura * 0.18f).toInt()) { FaixaInferior(monitor) }
    }

    private fun adicionar(gravidade: Int, alturaPx: Int, conteudo: @Composable () -> Unit): ComposeView? {
        val parametros = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            alturaPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = gravidade }
        val vista = ComposeView(this).apply {
            setViewTreeLifecycleOwner(dono)
            setViewTreeSavedStateRegistryOwner(dono)
            setViewTreeViewModelStoreOwner(dono)
            setContent {
                val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                CivicTheme(ModoTema.ler(this@PainelService).escuro(hora)) { conteudo() }
            }
        }
        return if (runCatching { janela.addView(vista, parametros) }.isSuccess) vista else null
    }

    private fun remover() {
        faixaTopo?.let { runCatching { janela.removeView(it) } }
        faixaBase?.let { runCatching { janela.removeView(it) } }
        faixaTopo = null
        faixaBase = null
    }

    // Girar a tela muda a altura das faixas.
    override fun onConfigurationChanged(novaConfiguracao: Configuration) {
        super.onConfigurationChanged(novaConfiguracao)
        if (faixaTopo != null) mostrar()
    }

    override fun onDestroy() {
        remover()
        sensores.parar()
        monitor.parar()
        dono.destruir()
        super.onDestroy()
    }

    /** Uma janela fora de Activity precisa de um "dono" de ciclo de vida para o Compose funcionar. */
    private class Dono : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registro = LifecycleRegistry(this)
        private val controlador = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registro
        override val savedStateRegistry: SavedStateRegistry get() = controlador.savedStateRegistry
        override val viewModelStore = ViewModelStore()

        init {
            controlador.performAttach()
            controlador.performRestore(null)
            registro.currentState = Lifecycle.State.RESUMED
        }

        fun destruir() {
            registro.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }
}
