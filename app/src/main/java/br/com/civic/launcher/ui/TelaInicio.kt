package br.com.civic.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.civic.launcher.CondicaoClima
import br.com.civic.launcher.MediaMonitor
import br.com.civic.launcher.Sensores
import br.com.civic.launcher.obd.ObdMonitor

/**
 * Paisagem: carro | mapa + dados rápidos | Spotify + tempo e rede.
 * Retrato (futura virada de tela): tudo empilhado, com rolagem se faltar espaço.
 */
@Composable
fun TelaInicio(monitor: MediaMonitor, sensores: Sensores, obd: ObdMonitor) {
    val dividida = emTelaDividida()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (dividida) {
            // Meia tela (tela dividida com o mapa): versões compactas de cada layout.
            val config = br.com.civic.launcher.Configuracoes.obter(androidx.compose.ui.platform.LocalContext.current)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SeletorLayoutHome()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (config.layoutHome) {
                        br.com.civic.launcher.LayoutHome.Caixas, br.com.civic.launcher.LayoutHome.NovaLinha -> HomeCaixasCompacta(monitor, sensores, obd)
                        br.com.civic.launcher.LayoutHome.Painel, br.com.civic.launcher.LayoutHome.Esportiva -> HomePainelCompacta(monitor, sensores, obd)
                        br.com.civic.launcher.LayoutHome.Padrao, br.com.civic.launcher.LayoutHome.Ousada -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CartaoCarro(obd, Modifier.weight(1.25f).fillMaxWidth())
                                CartaoSinais(sensores, obd, Modifier.weight(1f).fillMaxWidth())
                            }
                            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CartaoSpotify(monitor, Modifier.weight(1.15f).fillMaxWidth())
                                CartaoClima(sensores.clima, Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                    }
                }
            }
        } else if (maxWidth >= maxHeight) {
          val config = br.com.civic.launcher.Configuracoes.obter(androidx.compose.ui.platform.LocalContext.current)
          Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SeletorLayoutHome()
            when (config.layoutHome) {
            br.com.civic.launcher.LayoutHome.Caixas -> HomeCaixas(monitor, sensores, obd)
            br.com.civic.launcher.LayoutHome.Painel -> HomePainel(monitor, sensores, obd)
            br.com.civic.launcher.LayoutHome.Ousada -> HomeOusada(monitor, sensores, obd)
            br.com.civic.launcher.LayoutHome.Esportiva -> HomeEsportiva(monitor, sensores, obd)
            br.com.civic.launcher.LayoutHome.NovaLinha -> HomeNovaLinha(monitor, sensores, obd)
            br.com.civic.launcher.LayoutHome.Padrao ->
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CartaoCarro(obd, Modifier.weight(1f).fillMaxHeight())
                // Mapa do mesmo tamanho do Spotify; embaixo dele, os dados rápidos (km/h, km/l, combustível…).
                Column(Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CartaoWaze(sensores, obd, Modifier.weight(1.15f).fillMaxWidth())
                    CartaoSinais(sensores, obd, Modifier.weight(1f).fillMaxWidth())
                }
                Column(Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CartaoSpotify(monitor, Modifier.weight(1.0f).fillMaxWidth())
                    DuasCaixas(sensores, obd, Modifier.weight(1.2f).fillMaxWidth())
                }
            }
            }
          }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CartaoCarro(obd, Modifier.fillMaxWidth().height(330.dp))
                CartaoWaze(sensores, obd, Modifier.fillMaxWidth().height(260.dp))
                CartaoSinais(sensores, obd, Modifier.fillMaxWidth().height(220.dp))
                CartaoSpotify(monitor, Modifier.fillMaxWidth().height(320.dp))
                DuasCaixas(sensores, obd, Modifier.fillMaxWidth().height(380.dp))
            }
        }
    }
}

/** Tempo (clima) e rede, cada um ocupando a largura toda. */
@Composable
private fun DuasCaixas(sensores: Sensores, obd: ObdMonitor, modifier: Modifier) {
    val d = obd.dados
    val c = sensores.conectividade
    val clima = sensores.clima
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CartaoClima(clima, Modifier.weight(3.4f).fillMaxWidth())
            MiniCartao(
                if (c.rede.isNotEmpty()) "Rede ${c.rede}" else "Rede", Icons.Filled.SignalCellularAlt, Cores.Destaque,
                c.percentual?.toString() ?: "—", if (c.percentual != null) "%" else "",
                listOf(c.operadora, c.dbm?.let { "$it dBm" }).filterNotNull().filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Sem rede" },
                Modifier.weight(0.8f).fillMaxWidth(),
            )

    }
}
