# Painel do carro dentro da launcher — guia para a launcher

**Objetivo:** mostrar, dentro da launcher (multimídia Android), uma página **somente leitura** com poucos dados do carro. A página só funciona dentro da launcher: a autorização é **nativa** (ponte da launcher + token do aparelho).

| Item | Valor |
|---|---|
| Página (carregar na WebView) | `https://casa-tech-renatin.vercel.app/veiculo/launcher` |
| Função de dados | `POST https://qdmsiwxfuzppaqqnljth.supabase.co/functions/v1/civic-painel` |
| Nome da ponte nativa | `CivicLauncher` (método `acesso()`) |
| Token usado | o **token do aparelho** que a launcher já tem (o mesmo da telemetria) |
| Tela | feita para 800×480 ou mais, sem rolagem, claro/escuro automático |

## O que a launcher precisa fazer (resumo)
1. Criar uma tela com uma **WebView** (JavaScript ligado) e carregar a página acima.
2. Registrar na WebView a **ponte nativa** `CivicLauncher` com um método `acesso()`.
3. Em `acesso()`, trocar o token do aparelho por um **acesso curto** (função `civic-painel`, ação `entrar`) e devolvê-lo como texto. Guardar em memória e renovar quando faltarem menos de 2 minutos.
4. Travar a navegação da WebView nessa página (a ponte nunca pode ficar exposta a outro endereço).

Nada além disso: a página faz o resto (busca os dados a cada 30 s e se desenha).

## Como a autorização funciona
- O **token do aparelho nunca vai para a página nem para a URL**: fica só no código nativo (Keystore / EncryptedSharedPreferences).
- A launcher troca o token por um **acesso curto**: opaco (criptografado, não revela o token), vale **15 minutos** e **só lê**.
- A página pede o acesso à ponte (`window.CivicLauncher.acesso()`), manda-o em `x-civic-acesso` e recebe os dados. Se receber 401, pede outro acesso uma vez; se ainda negar, mostra "Sem autorização".
- **Sem a ponte** (navegador comum), a página só mostra "Este painel só abre dentro da launcher do carro" e **não pede dado nenhum**.
- A resposta tem poucos campos e **não traz localização**.

### Trocar o token por um acesso curto (chamada nativa)
```
POST https://qdmsiwxfuzppaqqnljth.supabase.co/functions/v1/civic-painel
Content-Type: application/json
x-civic-token: <token do aparelho>

{"acao":"entrar"}
```
Resposta `200`:
```json
{"acesso":"TEL_Qa2j4f…","expira_em":1795303961,"validade_s":900}
```
`expira_em` é um instante Unix em segundos. Resposta `401` `{"erro":"não autorizado"}` se o token não existir. Limite: 30 trocas por minuto por aparelho.

## Código da launcher (Kotlin)
```kotlin
class PonteCivic(private val tokenDoAparelho: () -> String) {
    @Volatile private var acesso: String? = null
    @Volatile private var expiraEm = 0L

    // O WebView chama isto em thread própria: pode bloquear na rede.
    @JavascriptInterface
    fun acesso(): String {
        val agora = System.currentTimeMillis() / 1000
        if (acesso == null || agora > expiraEm - 120) renovar()
        return acesso ?: ""                       // vazio = a página mostra "Sem autorização"
    }

    private fun renovar() {
        runCatching {
            val c = URL("https://qdmsiwxfuzppaqqnljth.supabase.co/functions/v1/civic-painel")
                .openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-civic-token", tokenDoAparelho())
            c.connectTimeout = 8000; c.readTimeout = 8000; c.doOutput = true
            c.outputStream.use { it.write("""{"acao":"entrar"}""".toByteArray()) }
            if (c.responseCode == 200) {
                val j = JSONObject(c.inputStream.bufferedReader().readText())
                acesso = j.getString("acesso"); expiraEm = j.getLong("expira_em")
            }
        }
    }
}

// Na tela do painel:
webView.settings.javaScriptEnabled = true
webView.settings.allowFileAccess = false
webView.addJavascriptInterface(PonteCivic { tokenDoAparelho }, "CivicLauncher")
webView.webViewClient = object : WebViewClient() {
    // só o painel pode ser aberto; qualquer outro endereço é bloqueado
    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) =
        !r.url.toString().startsWith("https://casa-tech-renatin.vercel.app/veiculo/launcher")
}
webView.loadUrl("https://casa-tech-renatin.vercel.app/veiculo/launcher")
```
Mais seguro (opcional): trocar `addJavascriptInterface` por `WebViewCompat.addWebMessageListener` com a origem `https://casa-tech-renatin.vercel.app` permitida. Não registrar o acesso nem o token em log.

## O que a página mostra
| Bloco | Dados |
|---|---|
| Estado | online, motor ligado/desligado, velocidade, ou "sem sinal há X" |
| Bateria | tensão (V) e avaliação (Boa/Atenção/Fraca) |
| Combustível | % com barra e autonomia estimada |
| Última viagem | nota (anel colorido), km, duração, origem → destino, "só GPS"; selo "Simulação" no carro de teste |
| Avisos | SOS (vermelho, no topo), luz do motor ou código de falha, manutenção vencida |
| Pneus | pressão (psi) dos quatro pneus, só se houver sensores |

Onde não há dado, a página mostra "aguardando o primeiro uso" ou "—", nunca zero.

## Como conferir que funcionou
1. Abra o painel na launcher: em até alguns segundos aparecem Estado, Bateria e Combustível.
2. Veja o rodapé: "Atualizado hh:mm · v2.x" deve mudar a cada 30 s.
3. Abra a mesma URL num navegador comum: deve aparecer "Este painel só abre dentro da launcher do carro" (prova de que a página não funciona fora da launcher).
4. Faça `acesso()` devolver texto vazio (ou apague o token): a página deve mostrar "Sem autorização".

## Problemas comuns
| Sintoma | Causa provável |
|---|---|
| "Este painel só abre dentro da launcher" | a ponte não foi registrada com o nome exato `CivicLauncher`, ou a WebView está sem JavaScript |
| "Sem autorização" | `acesso()` devolveu vazio (a troca falhou: sem internet, token inexistente) ou o acesso expirou e não renovou |
| "Sem conexão" | WebView sem internet; a página tenta de novo sozinha |
| Tela em branco | a navegação foi bloqueada por URL diferente da permitida (confira o endereço) |

## Limites
- Um acesso copiado vale até expirar (15 min) e lê só os dados desse aparelho; não dá para revogar um acesso já emitido (para cortar de vez, apagar o aparelho do banco).
- A função aceita no máximo 30 trocas e 90 leituras por minuto por aparelho (melhor esforço).
- A página é do módulo Veículo, independente da Casa Tech (nada compartilhado).
