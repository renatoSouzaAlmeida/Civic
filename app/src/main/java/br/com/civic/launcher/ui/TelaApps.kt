package br.com.civic.launcher.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import br.com.civic.launcher.Atalhos
import br.com.civic.launcher.abrir

private class AppInfo(val nome: String, val pacote: String, val icone: ImageBitmap)

private fun listarApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm).toBitmap(96, 96).asImageBitmap()) }
        .filter { it.pacote != ctx.packageName }
        .distinctBy { it.pacote }
        .sortedBy { it.nome.lowercase() }
}

/** Todos os apps instalados. Toque abre; segurar permite fixar como Telefone, Rádio ou Câmera. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelaApps() {
    val ctx = LocalContext.current
    val apps = remember { listarApps(ctx) }
    var menuDe by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Cores.Cartao).padding(16.dp)) {
        Text("Segure um app para fixá-lo como Telefone ou Câmera", color = Cores.TextoSuave, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(GridCells.Adaptive(130.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(apps, key = { it.pacote }) { app ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(onClick = { ctx.abrir(app.pacote) }, onLongClick = { menuDe = app.pacote })
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(app.icone, null, Modifier.size(72.dp))
                    Text(app.nome, color = Cores.Texto, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    DropdownMenu(expanded = menuDe == app.pacote, onDismissRequest = { menuDe = null }) {
                        listOf("Telefone" to Atalhos.TELEFONE, "Câmera" to Atalhos.CAMERA).forEach { (nome, chave) ->
                            DropdownMenuItem(
                                text = { Text("Fixar como $nome", fontSize = 20.sp) },
                                onClick = {
                                    Atalhos.fixar(ctx, chave, app.pacote)
                                    Toast.makeText(ctx, "${app.nome} fixado como $nome", Toast.LENGTH_SHORT).show()
                                    menuDe = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
