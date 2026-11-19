package br.com.civic.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import br.com.civic.launcher.obd.ObdMonitor
import br.com.civic.launcher.ui.ModoTema
import kotlinx.coroutines.delay
import java.util.Calendar
import br.com.civic.launcher.ui.CivicTheme
import br.com.civic.launcher.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private lateinit var monitor: MediaMonitor
    private lateinit var atualizador: Atualizador
    private lateinit var sensores: Sensores
    private lateinit var obd: ObdMonitor
    private var modoTema by mutableStateOf(ModoTema.Automatico)

    // Localização (velocidade/clima), telefone (sinal da rede) e Bluetooth (ícone de status).
    private val pedirPermissoes =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            sensores.iniciar()
            obd.iniciar()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        monitor = MediaMonitor(this)
        atualizador = Atualizador(this)
        sensores = Sensores(this)
        obd = ObdMonitor.obter(this)
        modoTema = ModoTema.ler(this)
        setContent {
            // Reavalia a cada minuto para o modo automático trocar sozinho ao amanhecer/anoitecer.
            val hora by produceState(horaAtual()) {
                while (true) {
                    delay(60_000)
                    value = horaAtual()
                }
            }
            CivicTheme(modoTema.escuro(hora)) {
                HomeScreen(monitor, atualizador, sensores, obd, modoTema) { alternarTema() }
            }
        }

        val faltando = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (faltando.isNotEmpty()) pedirPermissoes.launch(faltando.toTypedArray())
    }

    private fun horaAtual() = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    private fun alternarTema() {
        modoTema = modoTema.proximo()
        ModoTema.salvar(this, modoTema)
    }

    // Reinicia ao voltar das Configurações (onde o acesso às notificações é concedido).
    override fun onStart() {
        super.onStart()
        // O painel de direção só aparece sobre o Waze; ao voltar para o launcher ele sai.
        stopService(Intent(this, PainelService::class.java))
        monitor.iniciar()
        sensores.iniciar()
        obd.iniciar()
        atualizador.verificar()
        // Sincroniza abastecimentos/manutenção com a nuvem e avisa no celular o que estiver vencendo.
        Veiculo.obter(this).aposMudanca()
    }

    override fun onStop() {
        monitor.parar()
        sensores.parar()
        super.onStop()
    }
}
