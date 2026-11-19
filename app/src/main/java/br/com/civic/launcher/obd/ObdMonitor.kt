package br.com.civic.launcher.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class EstadoObd { Desconectado, Procurando, Conectando, Conectado, Demonstracao }

data class DadosObd(
    val rpm: Int? = null,
    val velocidade: Int? = null,
    val tempMotor: Int? = null,
    val tensao: Float? = null,
    val carga: Int? = null,
    val tempAdmissao: Int? = null,
    val acelerador: Int? = null,
    val falhas: Int? = null,
    val luzMotor: Boolean = false,
    val codigos: List<String> = emptyList(),
    /** Códigos pendentes (modo 07): o carro detectou, mas ainda não confirmou nem acendeu a luz. */
    val codigosPendentes: List<String> = emptyList(),
    /** Correção de combustível curta e longa (%), banco 1. Positiva = injeção enriquecendo (mistura pobre). */
    val stft: Float? = null,
    val ltft: Float? = null,
    /** Injeção em malha fechada (corrigindo pela sonda). Null = o carro não informou. */
    val malhaFechada: Boolean? = null,
    // Combustível (previsto; ver docs/BACKLOG.md). Nulo = ainda não lido.
    val nivelCombustivel: Int? = null,      // % do tanque (PID 012F, se o carro informar)
    val consumoMedio: Float? = null,        // km/l desde que o app começou a medir
    val consumoInstantaneo: Float? = null,  // km/l agora (parado = nulo)
    // Sinais do carro (previstos). Não fazem parte do OBD padrão: dependem da rede CAN de carroceria
    // do Honda. Nulo = desconhecido (a luz-espia aparece apagada e esmaecida).
    val farol: Boolean? = null,
    val farolete: Boolean? = null,
    val pilotoAutomatico: Boolean? = null,
    val alerta: Boolean? = null,
    val setaEsquerda: Boolean? = null,
    val setaDireita: Boolean? = null,
)

class DispositivoBle(val nome: String, val endereco: String)

/**
 * Leitor OBD-II por Bluetooth de baixa energia (BLE) para adaptadores ELM327, como o Veepeak OBDCheck BLE+.
 * Vive durante todo o processo (singleton), então continua lendo mesmo com o Waze na frente.
 * Também tem um modo de demonstração (dados simulados) para testar a tela sem o adaptador.
 */
@SuppressLint("MissingPermission")
class ObdMonitor private constructor(private val ctx: Context) {
    var estado by mutableStateOf(EstadoObd.Desconectado)
        private set
    private var dadosAtuais by mutableStateOf(DadosObd())
    /** Últimos dados lidos. Cada atualização passa pelos alertas inteligentes do motor. */
    var dados: DadosObd
        get() = dadosAtuais
        private set(novo) {
            dadosAtuais = novo
            alertas.avaliar(novo, demonstracao = demo)
            br.com.civic.launcher.ComputadorBordo.obter(ctx).amostraObd(novo.velocidade, novo.rpm, demonstracao = demo)
            if (!demo) HistoricoFalhas.obter(ctx).registrar(novo.codigos, novo.codigosPendentes)
            SaudeBateria.obter(ctx).amostra(novo.tensao, novo.rpm, demonstracao = demo)
            CorrecaoCombustivel.obter(ctx).amostra(novo, demonstracao = demo)
            EcoConducao.obter().amostra(novo)
            br.com.civic.launcher.LocalEstacionamento.obter(ctx).amostra(novo.rpm, demonstracao = demo)
            br.com.civic.launcher.RetomarMusica.obter(ctx).amostra(novo.rpm, demonstracao = demo)
            br.com.civic.launcher.EstadoCarro.obter(ctx).amostra(novo, demonstracao = demo)
            br.com.civic.launcher.Telemetria.obter(ctx).amostra(novo.rpm, novo.tensao, demonstracao = demo)
            br.com.civic.launcher.AbastecimentoAuto.obter(ctx).amostra(novo.nivelCombustivel, novo.rpm, demonstracao = demo)
        }
    private val alertas = AlertasMotor.obter(ctx)
    var encontrados by mutableStateOf<List<DispositivoBle>>(emptyList())
        private set
    var buscandoLista by mutableStateOf(false)
        private set

    private val principal = Handler(Looper.getMainLooper())
    private val prefs = ctx.getSharedPreferences("obd", Context.MODE_PRIVATE)
    private val trava = Object()
    private val resposta = StringBuilder()
    @Volatile private var respostaPronta = false

    private var iniciado = false
    private var demo = false
    private var demoCodigos = listOf("P0420")
    private var demoPendentes = listOf("P0171")
    @Volatile private var pedidoApagar = false
    @Volatile private var pedidoLer = false
    /** Mensagem do último pedido de leitura/apagamento de falhas (para a tela de falhas). */
    var situacaoFalhas by mutableStateOf<String?>(null)
        private set
    var ocupadoFalhas by mutableStateOf(false)
        private set
    private var inicioDemo = 0L
    private var estadoReal = EstadoObd.Desconectado
    private var gatt: BluetoothGatt? = null
    private var escrita: BluetoothGattCharacteristic? = null
    private var sessao: Thread? = null
    private var buscando = false
    // Combustível: PIDs que o carro diz suportar e acumuladores do consumo médio (litros e km desde que o app abriu).
    private var suportados: Set<Int>? = null
    private var litrosAcum = 0.0
    private var kmAcum = 0.0
    private var ultimoCombustivel = 0L
    // Luzes-espia pelo CAN (experimental): captura guiada para descobrir quais bits mudam com cada luz.
    @Volatile private var tarefaCan: Boolean? = null // null = nada; false = captura "apagado"; true = captura "aceso"
    var capturaBase by mutableStateOf<Map<String, List<IntArray>>>(emptyMap())
        private set
    var capturaLigado by mutableStateOf<Map<String, List<IntArray>>>(emptyMap())
        private set
    var situacaoCan by mutableStateOf<String?>(null)
        private set
    private val falhasPorPid = HashMap<String, Int>()
    private val pausaAte = HashMap<String, Long>()

    private val adaptador: BluetoothAdapter? get() = ctx.getSystemService(BluetoothManager::class.java)?.adapter

    // ---------- ciclo de vida ----------

    /** Seguro chamar várias vezes: liga a tentativa automática de conexão. */
    fun iniciar() {
        if (iniciado) return
        iniciado = true
        tentarConectar()
    }

    private fun tentarConectar() {
        principal.postDelayed({ tentarConectar() }, 15_000)
        if (demo || estadoReal != EstadoObd.Desconectado) return
        val bt = adaptador ?: return
        if (!bt.isEnabled) return
        val salvo = prefs.getString("endereco", null)
        if (salvo != null) {
            runCatching { conectar(bt.getRemoteDevice(salvo)) }
        } else {
            buscar(10_000) { d -> if (pareceAdaptador(d)) { pararBusca(); conectar(d) } }
        }
    }

    private fun definirReal(novo: EstadoObd) {
        estadoReal = novo
        if (!demo) estado = novo
    }

    // ---------- busca BLE ----------

    private fun nomeDe(d: BluetoothDevice, r: ScanResult? = null): String =
        r?.scanRecord?.deviceName ?: runCatching { d.name }.getOrNull().orEmpty()

    private fun pareceAdaptador(d: BluetoothDevice): Boolean {
        val n = nomeDe(d).lowercase()
        return listOf("veepeak", "obd", "elm", "vlink", "v-link", "vgate", "icar").any { n.contains(it) }
    }

    private var aoEncontrar: ((BluetoothDevice) -> Unit)? = null
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(tipo: Int, r: ScanResult) {
            val nome = nomeDe(r.device, r)
            if (nome.isNotEmpty() && encontrados.none { it.endereco == r.device.address }) {
                encontrados = encontrados + DispositivoBle(nome, r.device.address)
            }
            aoEncontrar?.invoke(r.device)
        }
    }

    private fun buscar(duracaoMs: Long, aoAcharDispositivo: ((BluetoothDevice) -> Unit)?) {
        if (buscando) return
        val scanner = adaptador?.bluetoothLeScanner ?: return
        buscando = true
        aoEncontrar = aoAcharDispositivo
        if (aoAcharDispositivo == null) buscandoLista = true else definirReal(EstadoObd.Procurando)
        runCatching { scanner.startScan(scanCallback) }.onFailure { buscando = false; buscandoLista = false }
        principal.postDelayed({ pararBusca() }, duracaoMs)
    }

    private fun pararBusca() {
        if (!buscando) return
        buscando = false
        buscandoLista = false
        runCatching { adaptador?.bluetoothLeScanner?.stopScan(scanCallback) }
        if (estadoReal == EstadoObd.Procurando) definirReal(EstadoObd.Desconectado)
    }

    /** Para o seletor manual: lista os aparelhos BLE próximos por alguns segundos. */
    fun buscarLista() {
        encontrados = emptyList()
        buscar(8_000, null)
    }

    /** Guarda o adaptador escolhido e conecta nele. */
    fun escolher(d: DispositivoBle) {
        pararBusca()
        prefs.edit().putString("endereco", d.endereco).apply()
        desconectar()
        runCatching { conectar(adaptador!!.getRemoteDevice(d.endereco)) }
    }

    // ---------- conexão GATT ----------

    private fun conectar(d: BluetoothDevice) {
        if (estadoReal == EstadoObd.Conectando || estadoReal == EstadoObd.Conectado) return
        definirReal(EstadoObd.Conectando)
        gatt = d.connectGatt(ctx, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun desconectar() {
        sessao?.interrupt()
        sessao = null
        runCatching { gatt?.disconnect(); gatt?.close() }
        gatt = null
        escrita = null
        definirReal(EstadoObd.Desconectado)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, novoEstado: Int) {
            if (novoEstado == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else {
                principal.post {
                    sessao?.interrupt()
                    sessao = null
                    runCatching { g.close() }
                    if (gatt === g) gatt = null
                    escrita = null
                    if (!demo) dados = DadosObd()
                    definirReal(EstadoObd.Desconectado)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            var notificacao: BluetoothGattCharacteristic? = null
            var escritaEncontrada: BluetoothGattCharacteristic? = null
            for (servico in g.services) {
                val id = servico.uuid.toString()
                if (id.startsWith("00001800") || id.startsWith("00001801") || id.startsWith("0000180a")) continue
                for (c in servico.characteristics) {
                    val p = c.properties
                    if (notificacao == null && p and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) notificacao = c
                    if (escritaEncontrada == null && p and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) escritaEncontrada = c
                }
                if (notificacao != null && escritaEncontrada != null) break
            }
            if (notificacao == null || escritaEncontrada == null) {
                g.disconnect()
                return
            }
            escrita = escritaEncontrada
            g.setCharacteristicNotification(notificacao, true)
            val cccd = notificacao.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (cccd == null) {
                iniciarSessao(g)
            } else {
                cccd.value = if (notificacao.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                g.writeDescriptor(cccd)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descritor: BluetoothGattDescriptor, status: Int) {
            iniciarSessao(g)
        }

        @Deprecated("Usado também no Android 13+ (a versão nova chama esta)")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val texto = String(c.value ?: return, Charsets.US_ASCII)
            synchronized(trava) {
                resposta.append(texto)
                if (resposta.contains('>')) {
                    respostaPronta = true
                    trava.notifyAll()
                }
            }
        }
    }

    // ---------- conversa com o ELM327 ----------

    private fun iniciarSessao(g: BluetoothGatt) {
        if (sessao != null) return
        sessao = thread(name = "obd") {
            try {
                for (cmd in listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) enviar(cmd, 4_000)
                principal.post { definirReal(EstadoObd.Conectado) }
                laco()
            } catch (_: InterruptedException) {
            } finally {
                principal.post { if (gatt === g) runCatching { g.disconnect() } }
            }
        }
    }

    private fun enviar(comando: String, timeoutMs: Long): String? {
        if (!escrever(comando)) return null
        return aguardar(timeoutMs)
    }

    /** Manda o comando sem esperar a resposta (limpa o buffer antes). */
    private fun escrever(comando: String): Boolean {
        val c = escrita ?: return false
        val g = gatt ?: return false
        synchronized(trava) {
            resposta.setLength(0)
            respostaPronta = false
        }
        val semResposta = c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0
        c.writeType = if (semResposta) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        c.value = (comando + "\r").toByteArray()
        return g.writeCharacteristic(c)
    }

    /** Espera o ELM327 devolver o prompt ">" e entrega o texto recebido (null = estourou o tempo). */
    private fun aguardar(timeoutMs: Long): String? {
        synchronized(trava) {
            val fim = SystemClock.elapsedRealtime() + timeoutMs
            while (!respostaPronta) {
                val resta = fim - SystemClock.elapsedRealtime()
                if (resta <= 0) return null
                trava.wait(resta)
            }
            return resposta.toString()
        }
    }

    /** Pergunta um PID (ex.: "010C") e devolve os bytes de dados, ou null se o carro não respondeu. */
    private fun consultar(pid: String): IntArray? {
        // PID que falhou 3 vezes seguidas fica 30 s sem ser perguntado (ex.: ignição desligada); o rpm nunca para,
        // porque é ele que mostra quando o motor liga.
        if (pid != "010C" && (falhasPorPid[pid] ?: 0) >= 3 && SystemClock.elapsedRealtime() < (pausaAte[pid] ?: 0L)) return null
        val bruto = enviar(pid, 2_500)
            ?.uppercase()?.replace("\\s".toRegex(), "")?.replace(">", "").orEmpty()
        val prefixo = "41" + pid.substring(2)
        val i = bruto.indexOf(prefixo)
        if (i < 0 || bruto.contains("NODATA")) {
            falhasPorPid[pid] = (falhasPorPid[pid] ?: 0) + 1
            if ((falhasPorPid[pid] ?: 0) >= 3) pausaAte[pid] = SystemClock.elapsedRealtime() + 30_000
            return null
        }
        falhasPorPid[pid] = 0
        return bruto.substring(i + prefixo.length).chunked(2).mapNotNull { it.toIntOrNull(16) }.toIntArray()
    }

    private fun laco() {
        var ciclo = 0
        while (!Thread.currentThread().isInterrupted) {
            var d = dados
            if (pedidoApagar) {
                pedidoApagar = false
                val r = enviar("04", 5_000)?.uppercase().orEmpty()
                val ok = r.contains("44")
                d = lerFalhas(d.copy(codigos = emptyList(), codigosPendentes = emptyList()))
                val msg = if (ok) "Códigos apagados. Se o defeito continuar, eles voltam." else "O carro não confirmou o apagamento. Tente com a ignição ligada e o motor desligado."
                principal.post { ocupadoFalhas = false; situacaoFalhas = msg }
            }
            if (pedidoLer) {
                pedidoLer = false
                d = lerFalhas(d)
                val qtd = d.codigos.size + d.codigosPendentes.size
                principal.post { ocupadoFalhas = false; situacaoFalhas = if (qtd == 0) "Nenhum código de falha no carro." else "Leitura concluída: $qtd código(s)." }
            }
            tarefaCan?.let { ligado ->
                tarefaCan = null
                val quadros = escutarCan(5_000)
                val total = quadros.values.sumOf { it.size }
                principal.post {
                    if (ligado) capturaLigado = quadros else capturaBase = quadros
                    situacaoCan = if (total == 0) "Nenhum quadro CAN recebido: este carro não passa esses sinais pela tomada OBD (ou a ignição está desligada)."
                    else "Capturado com a luz ${if (ligado) "ACESA" else "APAGADA"}: $total quadros de ${quadros.size} IDs."
                }
            }
            if ((d.rpm ?: 0) < 400) {
                ultimoCombustivel = 0L
                // Motor desligado: só tensão e rpm, o mais rápido possível, para registrar a queda de tensão da partida.
                lerTensao()?.let { d = d.copy(tensao = it) }
                val rpm = consultar("010C")?.takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) / 4 }
                d = d.copy(rpm = rpm ?: 0)
                val parado = d
                principal.post { if (!demo) dados = parado }
                continue
            }
            val rpmLido = consultar("010C")?.takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) / 4 }
            // Sem resposta do rpm = provavelmente o motor/ignição desligou: volta para o modo de motor desligado.
            d = d.copy(rpm = rpmLido ?: 0)
            consultar("010D")?.firstOrNull()?.let { d = d.copy(velocidade = it) }
            if (ciclo % 4 == 0) {
                consultar("0105")?.firstOrNull()?.let { d = d.copy(tempMotor = it - 40) }
                consultar("0104")?.firstOrNull()?.let { d = d.copy(carga = it * 100 / 255) }
                consultar("0111")?.firstOrNull()?.let { d = d.copy(acelerador = it * 100 / 255) }
                consultar("010F")?.firstOrNull()?.let { d = d.copy(tempAdmissao = it - 40) }
                if (suportados == null) suportados = lerSuportados()
                d = lerCombustivel(d)
                if (ciclo % 10 == 0 && suporta(0x2F)) consultar("012F")?.firstOrNull()?.let { d = d.copy(nivelCombustivel = it * 100 / 255) }
                // Correção de combustível (fuel trims) e estado da malha da injeção.
                consultar("0106")?.firstOrNull()?.let { d = d.copy(stft = (it - 128) * 100f / 128f) }
                consultar("0107")?.firstOrNull()?.let { d = d.copy(ltft = (it - 128) * 100f / 128f) }
                consultar("0103")?.firstOrNull()?.let { d = d.copy(malhaFechada = it and 0x02 != 0) }
                lerTensao()?.let { d = d.copy(tensao = it) }
            }
            if (ciclo % 60 == 0) d = lerFalhas(d)
            if (ciclo % 8 == 4) {
                val regras = MapaCan.obter(ctx).regras
                if (regras.isNotEmpty()) d = aplicarCan(d, regras)
            }
            val novo = d
            principal.post { if (!demo) dados = novo }
            ciclo++
        }
    }

    // ---------- combustível ----------

    private fun suporta(pid: Int) = suportados?.contains(pid) ?: true

    /** Lê 0100/0120/0140: a lista de PIDs que o carro suporta. Null = o carro não respondeu (tenta de novo depois). */
    private fun lerSuportados(): Set<Int>? {
        val achados = HashSet<Int>()
        var base = 0
        while (base <= 0x40) {
            val b = consultar("01%02X".format(base))?.takeIf { it.size >= 4 } ?: break
            for (i in 0..31) if ((b[i / 8] shr (7 - i % 8)) and 1 == 1) achados += base + i + 1
            if (!achados.contains(base + 0x20)) break
            base += 0x20
        }
        return achados.takeIf { it.isNotEmpty() }
    }

    /**
     * Consumo pela vazão de ar: MAF (0110) se o carro tiver; senão estima pelo MAP (010B), temperatura do ar e rpm.
     * Combustível ≈ ar / 13,2 (gasolina brasileira com ~27% de etanol), densidade 750 g/l. É uma estimativa:
     * pode variar com etanol puro e com enriquecimento em aceleração forte.
     */
    private fun lerCombustivel(d: DadosObd): DadosObd {
        val rpm = d.rpm ?: return d
        var arGs: Double? = null
        if (suporta(0x10)) arGs = consultar("0110")?.takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) / 100.0 }
        if (arGs == null && suporta(0x0B)) {
            val mapKpa = consultar("010B")?.firstOrNull()
            if (mapKpa != null) {
                val cilindrada = if (br.com.civic.launcher.Configuracoes.obter(ctx).imagemCarro == br.com.civic.launcher.ImagemCarro.Fit) 0.0015 else 0.0018
                val kelvin = (d.tempAdmissao ?: 30) + 273.15
                arGs = mapKpa * 1000.0 * cilindrada * 0.80 * (rpm / 120.0) * 28.97 / (8.314 * kelvin) // eficiência volumétrica ~80%
            }
        }
        val agora = SystemClock.elapsedRealtime()
        val dt = if (ultimoCombustivel == 0L) 0.0 else (agora - ultimoCombustivel).coerceIn(0, 10_000) / 1000.0
        ultimoCombustivel = agora
        if (arGs == null || arGs <= 0.0) return d
        val litrosHora = arGs / 13.2 / 750.0 * 3600.0
        val vel = d.velocidade ?: 0
        litrosAcum += litrosHora * dt / 3600.0
        kmAcum += vel * dt / 3600.0
        val medio = if (kmAcum > 0.3 && litrosAcum > 0.01) (kmAcum / litrosAcum).toFloat() else d.consumoMedio
        val inst = if (vel > 3 && litrosHora > 0.05) (vel / litrosHora).toFloat().coerceAtMost(60f) else null
        return d.copy(consumoMedio = medio, consumoInstantaneo = inst)
    }

    // ---------- luzes-espia pelo CAN (experimental) ----------

    /** Pede uma captura de 5 s dos quadros CAN (modo monitor do ELM327). Rodada na próxima volta do laço. */
    fun capturarCan(ligado: Boolean) {
        if (demo) { situacaoCan = "Desligue a demonstração para capturar."; return }
        if (estado != EstadoObd.Conectado) { situacaoCan = "Conecte o adaptador OBD primeiro."; return }
        situacaoCan = "Capturando por 5 s… não mexa na luz."
        tarefaCan = ligado
    }

    /** Liga o modo monitor (ATMA) por [ms] e devolve os quadros recebidos, agrupados por ID de 11 bits. */
    private fun escutarCan(ms: Long): Map<String, List<IntArray>> {
        enviar("ATH1", 2_000); enviar("ATCAF0", 2_000)
        escrever("ATMA")
        Thread.sleep(ms)
        val texto = synchronized(trava) { resposta.toString() }
        escrever("") // qualquer caractere interrompe o monitor
        aguardar(2_000)
        enviar("ATAR", 2_000); enviar("ATH0", 2_000); enviar("ATCAF1", 2_000)
        val linha = Regex("^[0-9A-F]{3}([0-9A-F]{2})+$")
        val quadros = HashMap<String, MutableList<IntArray>>()
        for (bruto in texto.uppercase().split('\r', '\n')) {
            val l = bruto.replace(" ", "").replace(">", "")
            if (!linha.matches(l)) continue
            val lista = quadros.getOrPut(l.substring(0, 3)) { ArrayList() }
            if (lista.size < 200) lista += l.substring(3).chunked(2).map { it.toInt(16) }.toIntArray()
        }
        return quadros
    }

    private fun aplicarCan(d: DadosObd, regras: Map<SinalCan, RegraCan>): DadosObd {
        val quadros = escutarCan(600)
        fun ativo(s: SinalCan): Boolean? = regras[s]?.let { r -> quadros[r.id]?.any { r.ativo(it) } }
        return d.copy(
            farol = ativo(SinalCan.Farol) ?: d.farol, farolete = ativo(SinalCan.Farolete) ?: d.farolete,
            setaEsquerda = ativo(SinalCan.SetaEsquerda) ?: d.setaEsquerda, setaDireita = ativo(SinalCan.SetaDireita) ?: d.setaDireita,
            alerta = ativo(SinalCan.Alerta) ?: d.alerta, pilotoAutomatico = ativo(SinalCan.Piloto) ?: d.pilotoAutomatico,
        )
    }

    private fun lerTensao(): Float? =
        Regex("([0-9]+\\.[0-9]+)").find(enviar("ATRV", 2_000).orEmpty())?.value?.toFloatOrNull()

    private fun lerFalhas(d: DadosObd): DadosObd {
        var novo = d
        consultar("0101")?.firstOrNull()?.let { novo = novo.copy(luzMotor = it and 0x80 != 0, falhas = it and 0x7F) }
        val confirmados = if ((novo.falhas ?: 0) > 0) lerCodigos("03", "43") else emptyList()
        val pendentes = lerCodigos("07", "47")
        return novo.copy(codigos = confirmados ?: novo.codigos, codigosPendentes = pendentes ?: novo.codigosPendentes)
    }

    /**
     * Lê uma lista de códigos (03 = confirmados, 07 = pendentes). Aceita respostas em várias linhas
     * ("0: 43 02 …"); em CAN o primeiro byte depois do marcador é a quantidade. Null = sem resposta.
     */
    private fun lerCodigos(modo: String, marcador: String): List<String>? {
        val bruto = enviar(modo, 3_000)?.uppercase() ?: return null
        if (bruto.contains("NO DATA")) return emptyList()
        val hex = bruto.split('\r', '\n')
            .map { it.trim().replace(">", "").replace(Regex("^[0-9A-F]:"), "").replace(" ", "") }
            .filter { it.isNotEmpty() && it.length != 3 } // descarta a linha só com o tamanho (ex.: "00A")
            .joinToString("")
        val i = hex.indexOf(marcador)
        if (i < 0) return null
        val bytes = hex.substring(i + 2).chunked(2).mapNotNull { it.toIntOrNull(16) }
        return bytes.drop(1).chunked(2).filter { it.size == 2 && (it[0] != 0 || it[1] != 0) }.map { codigoDtc(it[0], it[1]) }.distinct()
    }

    /** Pede uma leitura das falhas agora (na próxima volta do laço de leitura). */
    fun lerFalhasAgora() {
        if (demo) { situacaoFalhas = "Leitura concluída (demonstração)."; return }
        if (estado != EstadoObd.Conectado) { situacaoFalhas = "Conecte o adaptador OBD para ler as falhas."; return }
        ocupadoFalhas = true; situacaoFalhas = "Lendo os códigos…"; pedidoLer = true
    }

    /** Apaga os códigos de falha (modo 04). Também zera os monitores de emissões do carro. */
    fun apagarFalhas() {
        if (demo) {
            demoCodigos = emptyList(); demoPendentes = emptyList()
            situacaoFalhas = "Códigos apagados (demonstração)."; return
        }
        if (estado != EstadoObd.Conectado) { situacaoFalhas = "Conecte o adaptador OBD para apagar as falhas."; return }
        ocupadoFalhas = true; situacaoFalhas = "Apagando…"; pedidoApagar = true
    }

    private fun codigoDtc(a: Int, b: Int): String {
        val letra = "PCBU"[(a shr 6) and 3]
        return "%c%d%X%02X".format(letra, (a shr 4) and 3, a and 0xF, b)
    }

    // ---------- demonstração ----------

    fun alternarDemonstracao() {
        if (demo) {
            demo = false
            principal.removeCallbacks(tique)
            // Parar a simulação = simular o desligamento do carro: manda o fim da viagem e onde estacionou para o Telegram.
            br.com.civic.launcher.ComputadorBordo.obter(ctx).finalizar(simulacao = true)
            br.com.civic.launcher.LocalEstacionamento.obter(ctx).estacionar(manual = false, simulado = true)
            dados = DadosObd()
            estado = estadoReal
        } else {
            demo = true
            inicioDemo = SystemClock.elapsedRealtime()
            demoCodigos = listOf("P0420"); demoPendentes = listOf("P0171")
            estado = EstadoObd.Demonstracao
            tique.run()
        }
    }

    private val tique = object : Runnable {
        override fun run() {
            if (!demo) return
            val t = SystemClock.elapsedRealtime() / 1000.0
            val onda = (sin(t * 2 * PI / 20) + 1) / 2 // 0..1 a cada 20 s
            // Roteiro para mostrar os alertas: motor frio nos primeiros 40 s (com uma acelerada acima de
            // 3.000 rpm entre 15 e 25 s) e, depois, 15 s de superaquecimento a cada minuto.
            val desde = (SystemClock.elapsedRealtime() - inicioDemo) / 1000.0
            val frio = desde < 40
            val ciclo = (desde - 40) % 60
            val temperatura = when {
                frio -> (55 + desde * 0.9).toInt()
                ciclo in 45.0..59.9 -> 107
                else -> 90 + (sin(t / 30) * 2).toInt()
            }
            // Partida simulada: 3 s parado (12,6 V), 1 s de arranque (cai para ~10,3 V) e o motor pega.
            val partida = desde < 4.0
            val rpmDemo = when {
                desde < 3.0 -> 0
                partida -> 220
                frio && desde in 15.0..25.0 -> 3400
                else -> (900 + onda * 2600 + abs(sin(t * 3)) * 150).toInt()
            }
            val tensaoDemo = when {
                desde < 3.0 -> 12.6f
                partida -> 10.3f + ((desde - 3.0) * 0.4).toFloat()
                else -> 14.1f + (sin(t) * 0.1f).toFloat()
            }
            dados = DadosObd(
                rpm = rpmDemo,
                velocidade = if (partida) 0 else (onda * 90).toInt(),
                tempMotor = temperatura,
                tensao = tensaoDemo,
                // Demonstração: mistura pobre só em marcha lenta (caso clássico de entrada falsa de ar).
                stft = (3.0 * sin(t * 1.7)).toFloat() + if (rpmDemo in 401..1099) 4f else 0f,
                ltft = if (rpmDemo in 401..1099) 8f else 2.5f,
                malhaFechada = temperatura >= 70,
                carga = (20 + onda * 45).toInt(),
                tempAdmissao = 34,
                acelerador = (8 + onda * 30).toInt(),
                falhas = demoCodigos.size,
                codigos = demoCodigos,
                codigosPendentes = demoPendentes,
                nivelCombustivel = 62,
                consumoMedio = 11.8f + (sin(t / 15) * 0.4f).toFloat(),
                // Ciclo de 60 s para mostrar as luzes-espia: farol, setas, piloto e alerta.
                farol = (t % 60) < 40,
                farolete = (t % 60) < 50,
                pilotoAutomatico = onda > 0.75,
                setaEsquerda = (t % 60) in 5.0..10.0,
                setaDireita = (t % 60) in 20.0..25.0,
                alerta = (t % 60) in 45.0..50.0,
            )
            principal.postDelayed(this, 300)
        }
    }

    companion object {
        @Volatile private var instancia: ObdMonitor? = null

        fun obter(ctx: Context): ObdMonitor =
            instancia ?: synchronized(this) { instancia ?: ObdMonitor(ctx.applicationContext).also { instancia = it } }
    }
}
