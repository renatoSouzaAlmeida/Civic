package br.com.civic.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import br.com.civic.launcher.ui.CivicTheme
import br.com.civic.launcher.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private lateinit var monitor: MediaMonitor
    private lateinit var atualizador: Atualizador
    private lateinit var sensores: Sensores

    // Localização (velocidade/clima), telefone (sinal da rede) e Bluetooth (ícone de status).
    private val pedirPermissoes =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { sensores.iniciar() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        monitor = MediaMonitor(this)
        atualizador = Atualizador(this)
        sensores = Sensores(this)
        setContent { CivicTheme { HomeScreen(monitor, atualizador, sensores) } }

        val faltando = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (faltando.isNotEmpty()) pedirPermissoes.launch(faltando.toTypedArray())
    }

    // Reinicia ao voltar das Configurações (onde o acesso às notificações é concedido).
    override fun onStart() {
        super.onStart()
        monitor.iniciar()
        sensores.iniciar()
        atualizador.verificar()
    }

    override fun onStop() {
        monitor.parar()
        sensores.parar()
        super.onStop()
    }
}
