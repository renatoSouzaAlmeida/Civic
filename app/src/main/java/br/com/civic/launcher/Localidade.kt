package br.com.civic.launcher

import android.content.Context
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import java.util.Locale
import kotlin.concurrent.thread

/** Onde o aparelho está, em texto (município e estado), para consultas como o preço médio da ANP. */
object Localidade {
    /** Resolve em segundo plano e devolve (município, estado) na thread principal; null se não souber a posição. */
    @Suppress("DEPRECATION")
    fun municipioEstado(ctx: Context, aoTerminar: (Pair<String, String>?) -> Unit) {
        val app = ctx.applicationContext
        thread(name = "localidade") {
            val par = runCatching {
                val pos = LocalEstacionamento.obter(app).posicaoRecente()
                val prefs = app.getSharedPreferences("sensores", Context.MODE_PRIVATE)
                val lat = pos?.latitude ?: prefs.getString("clima_lat", null)?.toDoubleOrNull() ?: return@runCatching null
                val lon = pos?.longitude ?: prefs.getString("clima_lon", null)?.toDoubleOrNull() ?: return@runCatching null
                if (!Geocoder.isPresent()) return@runCatching null
                Geocoder(app, Locale("pt", "BR")).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { a ->
                    val cidade = a.subAdminArea ?: a.locality
                    val estado = a.adminArea
                    if (cidade != null && estado != null) cidade to estado else null
                }
            }.getOrNull()
            Handler(Looper.getMainLooper()).post { aoTerminar(par) }
        }
    }
}
