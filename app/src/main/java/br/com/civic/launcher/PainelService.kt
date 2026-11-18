package br.com.civic.launcher

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
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
import br.com.civic.launcher.obd.ObdMonitor
import br.com.civic.launcher.ui.CivicTheme
import br.com.civic.launcher.ui.ModoTema
import br.com.civic.launcher.ui.PainelDirecao
import br.com.civic.launcher.ui.FormatoPainel
import br.com.civic.launcher.ui.TamanhoPainel
import java.util.Calendar

/**
 * Painel de direção: uma janela sobreposta ao Waze com velocidade, música e botões.
 * Formato escolhido em Config. (ou pelo botão da barra): coluna à direita (vertical) ou faixa embaixo (horizontal);
 * no automático, coluna com a tela deitada e faixa com a tela em pé.
 */
class PainelService : Service() {
    private lateinit var janela: WindowManager
    private lateinit var sensores: Sensores
    private lateinit var monitor: MediaMonitor
    private lateinit var obd: ObdMonitor
    private val dono = Dono()
    private var painel: ComposeView? = null

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
        val tela = resources.displayMetrics
        val paisagem = tela.widthPixels >= tela.heightPixels
        val vertical = FormatoPainel.ler(this).vertical(paisagem)
        val tamanho = TamanhoPainel.ler(this)
        // Na coluna, garante uma largura mínima (≈ 260 dp) para caber a barra e a música mesmo em celular.
        val larguraColuna = maxOf((tela.widthPixels * tamanho.fracaoLargura).toInt(), (260 * tela.density).toInt())
            .coerceAtMost(tela.widthPixels)
        val parametros = WindowManager.LayoutParams(
            if (vertical) larguraColuna else WindowManager.LayoutParams.MATCH_PARENT,
            if (vertical) WindowManager.LayoutParams.MATCH_PARENT else (tela.heightPixels * tamanho.fracaoAltura).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = if (vertical) Gravity.END else Gravity.BOTTOM }

        val vista = ComposeView(this).apply {
            setViewTreeLifecycleOwner(dono)
            setViewTreeSavedStateRegistryOwner(dono)
            setViewTreeViewModelStoreOwner(dono)
            setContent {
                val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                CivicTheme(ModoTema.ler(this@PainelService).escuro(hora)) {
                    PainelDirecao(
                        sensores, monitor, obd,
                        aoVoltarInicio = {
                            startActivity(Intent(this@PainelService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            stopSelf()
                        },
                        aoAlternarTamanho = {
                            // Refaz a janela fora do clique, pois ela mesma será substituída.
                            TamanhoPainel.salvar(this@PainelService, TamanhoPainel.ler(this@PainelService).proximo())
                            Handler(Looper.getMainLooper()).post { mostrar() }
                        },
                        aoAlternarFormato = {
                            FormatoPainel.salvar(this@PainelService, if (vertical) FormatoPainel.Horizontal else FormatoPainel.Vertical)
                            Handler(Looper.getMainLooper()).post { mostrar() }
                        },
                        aoFechar = { stopSelf() },
                    )
                }
            }
        }
        runCatching { janela.addView(vista, parametros) }.onSuccess { painel = vista }
    }

    private fun remover() {
        painel?.let { runCatching { janela.removeView(it) } }
        painel = null
    }

    // Girar a tela muda a posição do painel (coluna ↔ faixa).
    override fun onConfigurationChanged(novaConfiguracao: Configuration) {
        super.onConfigurationChanged(novaConfiguracao)
        if (painel != null) mostrar()
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
