package br.com.civic.launcher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

data class AgoraDet(val temp: Int, val sensacao: Int, val umidade: Int, val vento: Int, val rajada: Int, val cond: CondicaoClima, val mm: Double)
data class HoraDet(val rotulo: String, val temp: Int, val cond: CondicaoClima, val chuva: Int, val mm: Double, val vento: Int)
data class DiaDet(val rotulo: String, val max: Int, val min: Int, val cond: CondicaoClima, val chuva: Int, val mm: Double, val uv: Double, val nascer: String, val por: String, val vento: Int)
data class ClimaDet(val agora: AgoraDet, val dias: List<DiaDet>, val horas: List<HoraDet>)

/** Previsão completa (Open-Meteo, sem chave) para o modal do tempo e a chuva no destino. */
object ClimaDetalhe {
    private fun condicao(c: Int) = when (c) {
        0, 1 -> CondicaoClima.Limpo
        2 -> CondicaoClima.Parcial
        3, 45, 48 -> CondicaoClima.Nublado
        95, 96, 99 -> CondicaoClima.Tempestade
        else -> CondicaoClima.Chuva
    }

    private fun baixar(url: String): JSONObject? = runCatching {
        val con = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 15_000 }
        if (con.responseCode != 200) null else JSONObject(con.inputStream.bufferedReader().use { it.readText() })
    }.getOrNull()

    private fun rotuloDia(iso: String, i: Int): String = runCatching {
        val d = LocalDate.parse(iso)
        val nome = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale("pt", "BR")).substringBefore('-').replaceFirstChar { it.uppercase() }
        (if (i == 0) "Hoje" else if (i == 1) "Amanhã" else nome) + " · %02d/%02d".format(d.dayOfMonth, d.monthValue)
    }.getOrDefault(iso)

    private fun horas(h: JSONObject, limite: Int): List<HoraDet> {
        val t = h.getJSONArray("time"); val temp = h.getJSONArray("temperature_2m"); val cod = h.getJSONArray("weather_code")
        val prob = h.optJSONArray("precipitation_probability"); val mm = h.optJSONArray("precipitation"); val vento = h.optJSONArray("wind_speed_10m")
        return (0 until minOf(limite, t.length())).map { i ->
            HoraDet(if (i == 0) "Agora" else t.getString(i).substring(11, 13) + "h", temp.getDouble(i).roundToInt(), condicao(cod.getInt(i)),
                prob?.optInt(i, 0) ?: 0, mm?.optDouble(i, 0.0) ?: 0.0, vento?.optDouble(i, 0.0)?.roundToInt() ?: 0)
        }
    }

    /** Previsão completa do ponto: agora, próximas 24 h por hora e 3 dias. Resultado na thread de chamada de [aoTerminar] via [post]. */
    fun buscar(lat: Double, lon: Double, post: (() -> Unit) -> Unit, aoTerminar: (ClimaDet?) -> Unit) {
        thread(name = "clima-detalhe") {
            val r = runCatching {
                val j = baixar(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,wind_gusts_10m,weather_code,precipitation" +
                        "&hourly=temperature_2m,weather_code,precipitation_probability,precipitation,wind_speed_10m&forecast_hours=24" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_sum,precipitation_probability_max,wind_speed_10m_max" +
                        "&timezone=auto&forecast_days=3"
                ) ?: return@runCatching null
                val c = j.getJSONObject("current"); val d = j.getJSONObject("daily")
                val agora = AgoraDet(c.getDouble("temperature_2m").roundToInt(), c.optDouble("apparent_temperature").roundToInt(), c.optInt("relative_humidity_2m"),
                    c.optDouble("wind_speed_10m").roundToInt(), c.optDouble("wind_gusts_10m").roundToInt(), condicao(c.getInt("weather_code")), c.optDouble("precipitation", 0.0))
                val t = d.getJSONArray("time")
                val dias = (0 until t.length()).map { i ->
                    DiaDet(rotuloDia(t.getString(i), i), d.getJSONArray("temperature_2m_max").getDouble(i).roundToInt(), d.getJSONArray("temperature_2m_min").getDouble(i).roundToInt(),
                        condicao(d.getJSONArray("weather_code").getInt(i)), d.getJSONArray("precipitation_probability_max").optInt(i, 0), d.getJSONArray("precipitation_sum").optDouble(i, 0.0),
                        d.getJSONArray("uv_index_max").optDouble(i, 0.0), d.getJSONArray("sunrise").getString(i).substring(11, 16), d.getJSONArray("sunset").getString(i).substring(11, 16),
                        d.getJSONArray("wind_speed_10m_max").optDouble(i, 0.0).roundToInt())
                }
                ClimaDet(agora, dias, horas(j.getJSONObject("hourly"), 24))
            }.getOrNull()
            post { aoTerminar(r) }
        }
    }

    /** Chuva nas próximas 12 h no ponto (destino): lista por hora. */
    fun chuvaNoPonto(lat: Double, lon: Double, post: (() -> Unit) -> Unit, aoTerminar: (List<HoraDet>?) -> Unit) {
        thread(name = "clima-destino") {
            val r = runCatching {
                baixar("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&hourly=temperature_2m,weather_code,precipitation_probability,precipitation,wind_speed_10m&forecast_hours=12&timezone=auto")
                    ?.let { horas(it.getJSONObject("hourly"), 12) }
            }.getOrNull()
            post { aoTerminar(r) }
        }
    }

    /** Resumo da chuva numa lista de horas: primeira hora com chance de 50% ou mais (ou 0,3 mm), ou "sem chuva". */
    fun resumoChuva(h: List<HoraDet>): String {
        val i = h.indexOfFirst { it.chuva >= 50 || it.mm >= 0.3 }
        return if (i < 0) "Sem chuva prevista nas próximas ${h.size} h (máx. ${h.maxOfOrNull { it.chuva } ?: 0}%)"
        else if (i == 0) "Chovendo ou chuva iminente (${h[0].chuva}%)" else "Chuva prevista às ${h[i].rotulo} (${h[i].chuva}%)"
    }
}
