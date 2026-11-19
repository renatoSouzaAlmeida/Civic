package br.com.civic.launcher.obd

/**
 * Dicionário de códigos de falha (DTC) em português: os genéricos mais comuns (SAE) e alguns
 * específicos da Honda. Códigos que não estão aqui recebem uma descrição pela estrutura do código.
 */
object CodigosFalha {
    enum class Gravidade(val rotulo: String) { Grave("Grave — evite rodar"), Atencao("Atenção — leve para avaliar"), Leve("Leve") }

    data class Info(val descricao: String, val gravidade: Gravidade)

    fun info(codigo: String): Info {
        val c = codigo.uppercase()
        CONHECIDOS[c]?.let { return Info(it, gravidade(c)) }
        return Info(porEstrutura(c), gravidade(c))
    }

    private fun gravidade(c: String): Gravidade = when {
        // Falha de ignição/combustão (pode danificar o catalisador), superaquecimento e sensores de rotação/fase.
        c.startsWith("P030") || c == "P0217" || c == "P0335" || c == "P0340" || c == "P0016" -> Gravidade.Grave
        c.startsWith("P044") || c.startsWith("P045") || c == "P0457" || c == "P1456" || c == "P1457" -> Gravidade.Leve
        else -> Gravidade.Atencao
    }

    private fun porEstrutura(c: String): String {
        if (c.length != 5) return "Código não reconhecido"
        val area = when (c[0]) {
            'P' -> "Motor/câmbio"; 'B' -> "Carroceria"; 'C' -> "Chassi"; 'U' -> "Rede de comunicação entre módulos"; else -> "Sistema"
        }
        val origem = if (c[1] == '0' || c[1] == '2') "código genérico" else "código específico da Honda"
        val sistema = if (c[0] == 'P') when (c[2]) {
            '1', '2' -> "medição de ar e combustível"
            '3' -> "ignição ou falha de combustão"
            '4' -> "controle de emissões"
            '5' -> "velocidade, marcha lenta e entradas auxiliares"
            '6' -> "computador (ECU) e saídas"
            '7', '8', '9' -> "transmissão automática"
            else -> "sistema do motor"
        } else null
        return "$area${sistema?.let { " — $it" } ?: ""} ($origem)"
    }

    private val CONHECIDOS = mapOf(
        // Comando variável (VTC) e sincronismo
        "P0010" to "Circuito do atuador do comando variável (VTC)",
        "P0011" to "Comando variável (VTC) adiantado demais ou desempenho do sistema",
        "P0012" to "Comando variável (VTC) atrasado demais",
        "P0016" to "Correlação entre virabrequim e comando de válvulas fora do esperado",
        // Ar, temperatura e acelerador
        "P0101" to "Sensor de fluxo de ar (MAF) — desempenho",
        "P0102" to "Sensor de fluxo de ar (MAF) — sinal baixo",
        "P0103" to "Sensor de fluxo de ar (MAF) — sinal alto",
        "P0106" to "Sensor de pressão do coletor (MAP) — desempenho",
        "P0107" to "Sensor de pressão do coletor (MAP) — sinal baixo",
        "P0108" to "Sensor de pressão do coletor (MAP) — sinal alto",
        "P0111" to "Sensor de temperatura do ar de admissão — desempenho",
        "P0112" to "Sensor de temperatura do ar de admissão — sinal baixo",
        "P0113" to "Sensor de temperatura do ar de admissão — sinal alto",
        "P0116" to "Sensor de temperatura do motor — desempenho",
        "P0117" to "Sensor de temperatura do motor — sinal baixo",
        "P0118" to "Sensor de temperatura do motor — sinal alto",
        "P0121" to "Sensor de posição do acelerador (TPS) — desempenho",
        "P0122" to "Sensor de posição do acelerador (TPS) — sinal baixo",
        "P0123" to "Sensor de posição do acelerador (TPS) — sinal alto",
        "P0125" to "Motor demora a atingir a temperatura para o controle de mistura",
        "P0128" to "Temperatura do motor abaixo do esperado (provável válvula termostática)",
        // Sondas lambda
        "P0131" to "Sonda lambda 1 (antes do catalisador) — sinal baixo",
        "P0132" to "Sonda lambda 1 (antes do catalisador) — sinal alto",
        "P0133" to "Sonda lambda 1 (antes do catalisador) — resposta lenta",
        "P0134" to "Sonda lambda 1 (antes do catalisador) — sem atividade",
        "P0135" to "Aquecedor da sonda lambda 1 — circuito",
        "P0137" to "Sonda lambda 2 (depois do catalisador) — sinal baixo",
        "P0138" to "Sonda lambda 2 (depois do catalisador) — sinal alto",
        "P0139" to "Sonda lambda 2 (depois do catalisador) — resposta lenta",
        "P0141" to "Aquecedor da sonda lambda 2 — circuito",
        "P2195" to "Sonda lambda 1 presa em mistura pobre",
        "P2196" to "Sonda lambda 1 presa em mistura rica",
        // Mistura e injeção
        "P0171" to "Mistura pobre (pouco combustível ou entrada falsa de ar)",
        "P0172" to "Mistura rica (excesso de combustível)",
        "P0201" to "Bico injetor do cilindro 1 — circuito",
        "P0202" to "Bico injetor do cilindro 2 — circuito",
        "P0203" to "Bico injetor do cilindro 3 — circuito",
        "P0204" to "Bico injetor do cilindro 4 — circuito",
        "P0217" to "Superaquecimento do motor",
        // Ignição
        "P0300" to "Falhas de ignição/combustão em vários cilindros",
        "P0301" to "Falha de ignição/combustão no cilindro 1",
        "P0302" to "Falha de ignição/combustão no cilindro 2",
        "P0303" to "Falha de ignição/combustão no cilindro 3",
        "P0304" to "Falha de ignição/combustão no cilindro 4",
        "P0325" to "Sensor de detonação — circuito",
        "P0335" to "Sensor de rotação do virabrequim — circuito",
        "P0340" to "Sensor de fase do comando de válvulas — circuito",
        "P0351" to "Bobina de ignição do cilindro 1 — circuito",
        "P0352" to "Bobina de ignição do cilindro 2 — circuito",
        "P0353" to "Bobina de ignição do cilindro 3 — circuito",
        "P0354" to "Bobina de ignição do cilindro 4 — circuito",
        // Emissões
        "P0420" to "Eficiência do catalisador abaixo do limite",
        "P0441" to "Sistema de vapores do tanque (EVAP) — fluxo de purga incorreto",
        "P0442" to "Sistema de vapores do tanque (EVAP) — vazamento pequeno",
        "P0443" to "Válvula de purga do canister — circuito",
        "P0451" to "Sensor de pressão do sistema EVAP — desempenho",
        "P0455" to "Sistema de vapores do tanque (EVAP) — vazamento grande",
        "P0456" to "Sistema de vapores do tanque (EVAP) — vazamento muito pequeno",
        "P0457" to "Tampa do tanque solta ou ausente (vazamento no EVAP)",
        "P0496" to "Fluxo de purga do EVAP com a válvula fechada",
        // Velocidade, marcha lenta e tensão
        "P0500" to "Sensor de velocidade do veículo",
        "P0505" to "Controle da marcha lenta",
        "P0506" to "Marcha lenta abaixo do esperado",
        "P0507" to "Marcha lenta acima do esperado",
        "P0560" to "Tensão do sistema elétrico",
        "P0562" to "Tensão do sistema elétrico baixa",
        "P0563" to "Tensão do sistema elétrico alta",
        // ECU
        "P0600" to "Falha de comunicação interna da ECU",
        "P0601" to "Memória da ECU (checksum)",
        "P0603" to "Memória de manutenção da ECU (KAM)",
        "P0606" to "Processador da ECU",
        "P0657" to "Tensão de alimentação de atuadores",
        // Câmbio automático
        "P0700" to "Câmbio automático pediu a luz de injeção (há falha no módulo do câmbio)",
        "P0705" to "Sensor de posição da alavanca do câmbio",
        "P0715" to "Sensor de rotação de entrada (turbina) do câmbio",
        "P0720" to "Sensor de rotação de saída do câmbio",
        "P0730" to "Relação de marcha incorreta",
        "P0740" to "Embreagem do conversor de torque",
        "P0750" to "Solenoide de troca A do câmbio",
        "P0755" to "Solenoide de troca B do câmbio",
        "P0760" to "Solenoide de troca C do câmbio",
        // VTEC e específicos Honda
        "P2646" to "Sistema VTEC (balancim) — desempenho ou travado desligado",
        "P2647" to "Sistema VTEC (balancim) — travado ligado",
        "P1009" to "Avanço do comando variável (VTC) — Honda",
        "P1259" to "Sistema VTEC — Honda",
        "P1456" to "Vazamento no EVAP do lado do tanque — Honda",
        "P1457" to "Vazamento no EVAP do lado do canister — Honda",
        // Rede
        "U0100" to "Sem comunicação com o módulo do motor (ECU)",
        "U0101" to "Sem comunicação com o módulo do câmbio",
        "U0121" to "Sem comunicação com o módulo do ABS",
        "U0155" to "Sem comunicação com o painel de instrumentos",
    )
}
