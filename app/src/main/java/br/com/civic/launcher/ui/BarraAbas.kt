package br.com.civic.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Uma aba: nome e ícone (vetor do Material ou desenho próprio em res/drawable). */
class Aba(val rotulo: String, val icone: ImageVector? = null, val iconeRes: Int? = null)

private val DestaqueAba = Brush.verticalGradient(listOf(Color(0xFF4D9BFF), Color(0xFF1F66E0)))

/**
 * Barra de abas no mesmo estilo do menu do app: cada opção com ícone e nome; a ativa em azul.
 * Usada em Veículo (Viagem, Abastecimentos, Manutenção) e no Diagnóstico do OBD.
 */
@Composable
fun BarraAbas(abas: List<Aba>, selecionada: Int, modifier: Modifier = Modifier, aoSelecionar: (Int) -> Unit) {
    val forma = RoundedCornerShape(18.dp)
    val formaItem = RoundedCornerShape(14.dp)
    Row(
        modifier.height(60.dp).clip(forma).background(Cores.Cartao).border(1.dp, Cores.Borda, forma).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        abas.forEachIndexed { i, aba ->
            val ativa = i == selecionada
            val cor = if (ativa) Color.White else Cores.TextoSuave
            Row(
                Modifier.weight(1f).fillMaxHeight()
                    .toqueSuave { aoSelecionar(i) }
                    .clip(formaItem)
                    .then(if (ativa) Modifier.background(DestaqueAba).border(1.dp, Color(0xE6AAD2FF), formaItem) else Modifier),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    aba.iconeRes != null -> Icon(painterResource(aba.iconeRes), null, tint = cor, modifier = Modifier.size(26.dp))
                    aba.icone != null -> Icon(aba.icone, null, tint = cor, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(aba.rotulo, color = cor, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}
