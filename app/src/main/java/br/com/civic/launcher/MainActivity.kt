package br.com.civic.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.civic.launcher.ui.CivicTheme
import br.com.civic.launcher.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private lateinit var monitor: MediaMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        monitor = MediaMonitor(this)
        setContent { CivicTheme { HomeScreen(monitor) } }
    }

    // Reinicia ao voltar das Configurações (onde o acesso é concedido).
    override fun onStart() { super.onStart(); monitor.iniciar() }
    override fun onStop() { monitor.parar(); super.onStop() }
}
