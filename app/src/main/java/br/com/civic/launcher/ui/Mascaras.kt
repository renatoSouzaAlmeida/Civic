package br.com.civic.launcher.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Máscaras de digitação: o campo guarda só os dígitos e mostra o valor formatado.
 * Assim não é preciso digitar ponto, vírgula, "R$" nem barras — e não entram letras.
 */
enum class Mascara(val maxDigitos: Int) {
    /** Texto livre. */
    Nenhuma(0),
    /** Inteiro com separador de milhar: 120000 → 120.000 */
    Milhar(7),
    /** Número com 2 casas (litros): 4052 → 40,52 */
    Decimal2(6),
    /** Dinheiro: 589 → R$ 5,89 */
    Moeda(7),
    /** Data: 19112026 → 19/11/2026 */
    Data(8);

    /** Mantém só os dígitos, no limite do campo. */
    fun limpar(texto: String): String = if (this == Nenhuma) texto else texto.filter(Char::isDigit).take(maxDigitos)

    fun formatar(d: String): String = when (this) {
        Nenhuma -> d
        Milhar -> d.trimStart('0').ifEmpty { if (d.isEmpty()) "" else "0" }.reversed().chunked(3).joinToString(".").reversed()
        Decimal2, Moeda -> if (d.isEmpty()) "" else {
            val n = d.trimStart('0').padStart(3, '0')
            val inteiro = n.dropLast(2).reversed().chunked(3).joinToString(".").reversed()
            (if (this == Moeda) "R$ " else "") + inteiro + "," + n.takeLast(2)
        }
        Data -> buildString {
            d.forEachIndexed { i, c -> if (i == 2 || i == 4) append('/'); append(c) }
        }
    }

    /** Transformação visual com o cursor sempre no fim (o jeito natural de digitar números). */
    val transformacao: VisualTransformation
        get() = if (this == Nenhuma) VisualTransformation.None else VisualTransformation { texto ->
            val f = formatar(texto.text)
            TransformedText(AnnotatedString(f), object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = f.length
                override fun transformedToOriginal(offset: Int) = texto.text.length
            })
        }

    companion object {
        /** Dígitos de valores com 2 casas (litros, preço) → número. */
        fun centavos(d: String): Float? = d.toLongOrNull()?.let { it / 100f }
        /** Converte um número em dígitos de 2 casas (para preencher um campo já existente). */
        fun paraCentavos(v: Float): String = Math.round(v * 100).toString()
    }
}
