package br.com.civic.launcher

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import br.com.civic.launcher.obd.AlertasMotor
import br.com.civic.launcher.obd.TipoAlerta
import org.json.JSONObject

/**
 * TPMS: pressão e temperatura dos pneus por sensores Bluetooth (BLE) externos. **Desligado por padrão** (Config. > TPMS).
 *
 * EXPERIMENTAL: não existe protocolo único de sensor de pneu. O leitor entende o formato de anúncio BLE mais comum
 * (dados do fabricante: endereço 6 B, pressão uint32 LE em milésimos de kPa, temperatura int32 LE em centésimos de °C,
 * bateria %), e a tela mostra os dados brutos (hex) de cada sensor achado, para ajustar o leitor ao modelo real
 * quando os sensores chegarem. Sensores costumam transmitir pouco com o carro parado: ficar sem leitura é normal.
 */
@SuppressLint("MissingPermission")
class Tpms private constructor(private val ctx: Context) {
    data class Leitura(val mac: String, val nome: String, val rssi: Int, val hex: String, val pressaoKpa: Float?, val tempC: Float?, val bateria: Int?, val em: Long) {
        val plausivel: Boolean get() = pressaoKpa != null && pressaoKpa in 50f..1000f && tempC != null && tempC in -40f..125f
        val psi: Float? get() = pressaoKpa?.let { it * 0.145038f }
    }

    private val prefs = ctx.getSharedPreferences("tpms", Context.MODE_PRIVATE)
    /** Sensores vistos (por endereço MAC). */
    val encontrados = mutableStateMapOf<String, Leitura>()
    /** Muda quando a atribuição de posições muda (para a tela recompor). */
    var versao by mutableIntStateOf(0)
        private set
    private var escaneando = false
    private var ultimoProblema: String? = null

    fun macDe(pos: String): String? = prefs.getString("pos_$pos", null)
    fun leitura(pos: String): Leitura? = macDe(pos)?.let { encontrados[it] }
    fun posicaoDe(mac: String): String? = POSICOES.firstOrNull { macDe(it.first) == mac }?.first

    fun atribuir(pos: String, mac: String) {
        val e = prefs.edit()
        POSICOES.forEach { if (macDe(it.first) == mac) e.remove("pos_${it.first}") }
        e.putString("pos_$pos", mac).apply(); versao++
    }
    fun limpar(pos: String) { prefs.edit().remove("pos_$pos").apply(); versao++ }

    // ---------- varredura ----------

    private fun temPermissao() =
        if (Build.VERSION.SDK_INT >= 31) ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        else ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Liga ou desliga a procura de sensores conforme a chave em Config. (chamado também a cada 30 s pela telemetria). */
    fun sincronizar() {
        val quer = Configuracoes.obter(ctx).tpmsAtivo
        if (quer && !escaneando && temPermissao()) {
            val sc = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return
            runCatching { sc.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(), callback); escaneando = true }
        } else if (!quer && escaneando) {
            runCatching { ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(callback) }
            escaneando = false
            AlertasMotor.obter(ctx).definirExterna(TipoAlerta.Pneu, null)
        }
    }

    val procurando: Boolean get() = escaneando

    private val callback = object : ScanCallback() {
        override fun onScanResult(tipo: Int, r: ScanResult) { processar(r) }
        override fun onBatchScanResults(l: MutableList<ScanResult>) { l.forEach { processar(it) } }
    }

    private fun processar(r: ScanResult) {
        val rec = r.scanRecord ?: return
        val md = rec.manufacturerSpecificData ?: return
        val config = Configuracoes.obter(ctx)
        val mac = r.device.address
        for (i in 0 until md.size()) {
            val bytes = md.valueAt(i) ?: continue
            if (bytes.size < 15) continue
            val p = u32(bytes, 6) / 1000f
            val t = i32(bytes, 10) / 100f
            val nome = rec.deviceName ?: ""
            val l = Leitura(mac, nome, r.rssi, bytes.joinToString(" ") { "%02X".format(it) }, p, t, bytes[14].toInt() and 0xFF, System.currentTimeMillis())
            val pareceSensor = l.plausivel || nome.contains("tpms", true) || nome.contains("tire", true)
            if (!pareceSensor && !config.tpmsMostrarTodos) continue
            val antes = encontrados[mac]
            if (antes != null && l.em - antes.em < 1_000 && antes.hex == l.hex) continue
            encontrados[mac] = l
            if (posicaoDe(mac) != null) avaliar()
            return
        }
    }

    private fun u32(b: ByteArray, i: Int): Long = (b[i].toLong() and 0xFF) or ((b[i + 1].toLong() and 0xFF) shl 8) or ((b[i + 2].toLong() and 0xFF) shl 16) or ((b[i + 3].toLong() and 0xFF) shl 24)
    private fun i32(b: ByteArray, i: Int): Int = u32(b, i).toInt()

    // ---------- alertas ----------

    /** Pressão abaixo de 85% ou acima de 125% do ideal, ou temperatura acima de 85 °C; confirma em duas avaliações seguidas. */
    private fun avaliar() {
        val ideal = Configuracoes.obter(ctx).tpmsIdealPsi.toFloat()
        val agora = System.currentTimeMillis()
        val problemas = POSICOES.mapNotNull { (pos, nome) ->
            val l = leitura(pos) ?: return@mapNotNull null
            if (agora - l.em > 10 * 60_000L || !l.plausivel) return@mapNotNull null
            val psi = l.psi ?: return@mapNotNull null
            when {
                psi < ideal * 0.85f -> "Pneu $nome com %.0f psi (baixa; ideal %.0f).".format(psi, ideal)
                psi > ideal * 1.25f -> "Pneu $nome com %.0f psi (alta; ideal %.0f).".format(psi, ideal)
                (l.tempC ?: 0f) > 85f -> "Pneu $nome a %.0f °C (quente).".format(l.tempC)
                else -> null
            }
        }
        val msg = problemas.joinToString(" ").ifEmpty { null }
        if (msg == null) { AlertasMotor.obter(ctx).definirExterna(TipoAlerta.Pneu, null); ultimoProblema = null }
        else { if (msg == ultimoProblema) AlertasMotor.obter(ctx).definirExterna(TipoAlerta.Pneu, msg); ultimoProblema = msg }
    }

    /** Último estado dos pneus para a nuvem (civic_estados.pneus), ou null se desligado ou sem leituras. */
    fun resumoJson(): JSONObject? {
        if (!Configuracoes.obter(ctx).tpmsAtivo) return null
        val o = JSONObject()
        POSICOES.forEach { (pos, _) -> leitura(pos)?.takeIf { it.plausivel }?.let {
            o.put(pos, JSONObject().put("psi", Math.round((it.psi ?: 0f) * 10) / 10.0).put("c", Math.round((it.tempC ?: 0f) * 10) / 10.0).put("bat", it.bateria ?: JSONObject.NULL).put("em", it.em))
        } }
        return if (o.length() == 0) null else o
    }

    companion object {
        val POSICOES = listOf("DE" to "dianteiro esquerdo", "DD" to "dianteiro direito", "TE" to "traseiro esquerdo", "TD" to "traseiro direito")
        @Volatile private var instancia: Tpms? = null
        fun obter(ctx: Context): Tpms = instancia ?: synchronized(this) { instancia ?: Tpms(ctx.applicationContext).also { instancia = it } }
    }
}
