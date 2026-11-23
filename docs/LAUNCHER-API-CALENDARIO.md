# API do Launcher Android — Calendário do Ponte Aérea

Documento para o projeto **Launcher Android** (multimídia do carro). Explica como o Launcher consome o calendário do Ponte Aérea, como a autenticação funciona e como configurar tudo (versão da API: **1**).

## Visão geral

```
Launcher (Android, no carro)                       Ponte Aérea (Vercel)                     Supabase
  GET /api/launcher/calendario  ───────────────►  valida o token (só o hash fica no servidor)
  Authorization: Bearer pa_launcher_…             limita requisições por token             ◄── lê voos, viagens,
  ◄──────────────  JSON com eventos  ────────────  monta a lista simples de eventos              eventos, filhas, config
```

- **Somente leitura.** Não existe nenhuma rota de escrita para o Launcher.
- **Uma rota só:** `GET /api/launcher/calendario`.
- **Não usa login do app.** O Launcher não tem usuário/senha do Supabase; usa um **token de dispositivo** próprio, revogável.
- **Dados mínimos.** A resposta não traz valores em R$, pontos, voucher, código de reserva, nem eventos marcados como "pessoais" no calendário (a tela do carro pode ser vista por outras pessoas).

## Autenticação

### Como funciona

1. Você gera um token (`pa_launcher_` + 43 caracteres aleatórios, 256 bits) com o script do repositório.
2. O **token** fica só no Launcher. O servidor guarda apenas o **SHA-256** dele na variável de ambiente `LAUNCHER_TOKEN_SHA256` (Vercel). Se o ambiente vazar, o token não vaza.
3. Toda chamada manda `Authorization: Bearer <token>`. O servidor calcula o SHA-256 do que chegou e compara em tempo constante com os hashes configurados.
4. **Fail-closed:** sem a env configurada (ou com ela vazia/inválida), toda chamada devolve `401`.
5. **Rate limit:** 60 requisições por 10 minutos por token (`429` + `Retry-After`).
6. O token vai **só no header**. Token em query string é ignorado (aparece em logs e histórico) e resulta em `401`.

### Configurar (uma vez)

```bash
node scripts/gerar-token-launcher.mjs
```

Sem o projeto local? Veja "Gerar o token sem baixar o projeto" abaixo — o resultado é o mesmo. O script imprime:

- `TOKEN` → coloque no Launcher (veja "Guardando o token no Android"). **Não commite e não cole em chat.**
- `HASH` → na Vercel, em *Settings → Environment Variables*, crie `LAUNCHER_TOKEN_SHA256` (Production) com esse valor e faça um **redeploy**.

### Gerar o token sem baixar o projeto

Qualquer um destes gera exatamente o mesmo formato. **Gere sempre na sua máquina/navegador, nunca em site de terceiros** (quem gera o token o conhece).

**No navegador (qualquer um, sem instalar nada):** abra uma aba qualquer, tecle `F12` → aba **Console**, cole e dê Enter. (O Chrome pode pedir que você digite `allow pasting` antes de colar.)

```js
(async () => {
  const b = crypto.getRandomValues(new Uint8Array(32));
  const token = "pa_launcher_" + btoa(String.fromCharCode(...b)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  const h = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token));
  const hash = [...new Uint8Array(h)].map(x => x.toString(16).padStart(2, "0")).join("");
  console.log("TOKEN:\n" + token + "\n\nHASH:\n" + hash);
})();
```

**Terminal com Node.js (sem o repositório):** `node -e` com o mesmo código, ou o script do projeto.

**Terminal Linux/macOS/Git Bash:**

```bash
t="pa_launcher_$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n')"
echo "TOKEN: $t"; printf '%s' "$t" | sha256sum   # no macOS: shasum -a 256
```

O **TOKEN** vai para o Launcher; o **HASH** (64 caracteres hex) vai para a env `LAUNCHER_TOKEN_SHA256`.

### Vários dispositivos e rotação

`LAUNCHER_TOKEN_SHA256` aceita vários hashes separados por vírgula (um por dispositivo, ou o antigo + o novo durante a troca):

```
LAUNCHER_TOKEN_SHA256=<hash_celular>,<hash_multimidia>
```

- **Revogar um dispositivo:** remova o hash dele da env e faça redeploy.
- **Trocar o token:** gere um novo, adicione o hash novo, atualize o Launcher, depois remova o hash antigo.
- Perdeu ou suspeita de vazamento do aparelho? Remova o hash e gere outro.

### Guardando o token no Android

**Decisão deste projeto:** o token fica no **arquivo de configuração local do projeto Launcher** (fora do git) e é embutido no build via `BuildConfig`. O aparelho que roda esse build é só a multimídia do carro, então o risco é baixo e aceito. Isto vale **apenas** para esse uso: o APK com o token não deve ser distribuído nem publicado.

**1. Arquivo local `local.properties`** (na raiz do projeto Android — o Android Studio já o coloca no `.gitignore`; confirme):

```properties
PONTE_AEREA_BASE_URL=https://ponte-area.vercel.app
PONTE_AEREA_TOKEN=pa_launcher_COLE_O_SEU_TOKEN_AQUI
```

**2. `.gitignore`** — garanta que o arquivo nunca vá para o git:

```
local.properties
```

**3. `app/build.gradle.kts`** — lê o arquivo e gera as constantes do build:

```kotlin
import java.util.Properties

val local = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "PONTE_AEREA_BASE_URL", "\"${local.getProperty("PONTE_AEREA_BASE_URL", "")}\"")
        buildConfigField("String", "PONTE_AEREA_TOKEN", "\"${local.getProperty("PONTE_AEREA_TOKEN", "")}\"")
    }
}
```

**4. No código**, use as constantes (nunca escreva o token no código-fonte):

```kotlin
val base = BuildConfig.PONTE_AEREA_BASE_URL
val token = BuildConfig.PONTE_AEREA_TOKEN
```

**Regras que continuam valendo**
- O token **nunca** vai para arquivo versionado: nem este MD, nem o README, nem código, nem `strings.xml`, nem commit. No MD do Launcher use sempre um placeholder (`<TOKEN_DO_LAUNCHER>`).
- Não imprima o token em logs (`Log.d`, interceptors do OkHttp com nível `BODY`/`HEADERS`).
- Gere o build com o token só na máquina onde você compila e instale o APK direto na multimídia.
- **Limitação aceita:** o token fica dentro do APK e pode ser extraído por quem tiver o arquivo ou acesso ao aparelho. Por isso a API é só leitura e o token é revogável: se o aparelho ou o APK for parar em mãos erradas, gere outro e troque o hash na Vercel (veja "Vários dispositivos e rotação"). Se um dia o Launcher for distribuído a mais gente, troque para um campo de configuração salvo em `EncryptedSharedPreferences`.

## Endpoint

### `GET /api/launcher/calendario`

Base: `https://<seu-dominio-da-vercel>` (HTTPS obrigatório).

| Parâmetro | Obrigatório | Padrão | Descrição |
|---|---|---|---|
| `de` | não | hoje (GMT-3) | Início do intervalo, `YYYY-MM-DD` |
| `ate` | não | `de` + 60 dias | Fim do intervalo (inclusive), `YYYY-MM-DD` |
| `passageiro` | não | sem filtro | Trecho do nome (sem diferenciar maiúsculas) para filtrar **voos**, ex.: `renato`. Sem o parâmetro (ou `todos`) vêm os voos de todos os passageiros. Só afeta voos: viagens, feriados, eventos, presencial e dias com as filhas vêm sempre. |

Regras: o intervalo máximo é de **400 dias**; `ate` não pode ser menor que `de`; datas inválidas devolvem `400`.

### Resposta `200`

```json
{
  "ok": true,
  "versao_api": 1,
  "fuso": "America/Sao_Paulo",
  "hoje": "2026-11-23",
  "periodo": { "de": "2026-11-23", "ate": "2027-01-22" },
  "filtro_passageiro": null,
  "passageiros": [{ "id": "…", "nome": "Renato" }, { "id": "…", "nome": "Alcilene" }],
  "total": 3,
  "proximo_voo": { "id": "voo-9c1…", "tipo": "voo", "titulo": "BSB → CGH", "data": "2026-11-25", "hora_inicio": "07:10", "…": "…" },
  "eventos": [
    {
      "id": "voo-9c1…",
      "tipo": "voo",
      "titulo": "BSB → CGH",
      "subtitulo": "Azul AD1234 · Renato",
      "data": "2026-11-25",
      "data_fim": null,
      "hora_inicio": "07:10",
      "hora_fim": "09:00",
      "passageiro": "Renato",
      "dia_inteiro": false,
      "cor": "#0058D4",
      "detalhes": { "origem": "BSB", "destino": "CGH", "companhia": "Azul", "numero_voo": "AD1234", "passageiro": "Renato", "situacao": "pendente", "objetivo": "Trabalho" }
    },
    {
      "id": "feriado-…-2026-12-25",
      "tipo": "feriado",
      "titulo": "Natal",
      "subtitulo": "Feriado com as filhas",
      "data": "2026-12-25",
      "data_fim": null,
      "hora_inicio": null,
      "hora_fim": null,
      "passageiro": null,
      "dia_inteiro": true,
      "cor": "#EF4444",
      "detalhes": { "feriado_com_filhas": true }
    }
  ],
  "gerado_em": "2026-11-23T13:05:41.000Z"
}
```

Os eventos vêm **ordenados** por `data`, depois `hora_inicio`. Campos sempre presentes em cada evento: `id`, `tipo`, `titulo`, `data`, `dia_inteiro`, `cor` (hex `#RRGGBB`) e `detalhes` (objeto, pode vir vazio). `subtitulo`, `data_fim`, `hora_inicio`, `hora_fim` e `passageiro` podem ser `null`. `passageiro` traz o nome de quem voa e só vem preenchido nos eventos `voo`.

No topo da resposta, `filtro_passageiro` repete o filtro aplicado (`null` = todos) e `passageiros` lista os passageiros ativos (`id` e `nome`), útil para montar um seletor no Launcher. Horários são `HH:MM`, no fuso de Brasília. Trate campos desconhecidos como opcionais (a API pode ganhar campos sem mudar `versao_api`).

### Tipos de evento

| `tipo` | Origem | Observações |
|---|---|---|
| `voo` | tabela `voos` | Sem cancelados. Tem `hora_inicio` (saída) e `hora_fim` (chegada). Filtrado por `passageiro`. |
| `viagem` | tabela `viagens` | Período (`data` → `data_fim`), `dia_inteiro: true`. Aparece se se sobrepõe ao intervalo. |
| `feriado` | `eventos_calendario` | Recorrentes já vêm projetadas para o ano certo. `detalhes.feriado_com_filhas` indica se é feriado com as filhas. |
| `evento` | `eventos_calendario` | Eventos que não são feriado. Eventos "pessoais" (ocultáveis) **não** são enviados. |
| `presencial` | rodízio configurado | Dia presencial da Alci (ida a trabalho). |
| `familia` | tabela `filhas` | Dias marcados manualmente com as filhas. |
| `fds_filhas` | configuração de FDS alternados | Fins de semana com as filhas, mesma conta da tela Calendário do app. |

`proximo_voo` é o primeiro evento `voo` com `data >= hoje` dentro do intervalo, ou `null` — útil para um widget "próximo voo".

### Erros

| Status | Corpo | Quando |
|---|---|---|
| `304` | vazio | `If-None-Match` igual ao `ETag` atual (nada mudou) |
| `400` | `{ "ok": false, "error": "…" }` | `de`/`ate` inválidos, invertidos ou intervalo > 400 dias |
| `401` | `{ "ok": false, "error": "Não autorizado" }` | Token ausente, errado, revogado ou env não configurada. Header `WWW-Authenticate: Bearer`. |
| `405` | — | Qualquer método diferente de `GET` |
| `429` | `{ "ok": false, "error": "…" }` | Passou de 60 req/10 min. Respeite `Retry-After` (segundos). |
| `502` / `500` | `{ "ok": false, "error": "…" }` | Falha ao consultar o banco / erro interno. Tente de novo mais tarde. |

Mensagens de `401` são sempre iguais: a API não revela se o token existe, expirou ou foi revogado.

## Como consumir bem (recomendado)

- **Atualizar a cada 15–30 minutos** com o app em primeiro plano, e uma vez ao ligar a multimídia. O calendário muda pouco; 60 req/10 min é só proteção.
- **Use `ETag`:** guarde o `ETag` da última resposta e mande `If-None-Match`. Se nada mudou, recebe `304` sem corpo (economiza dados móveis).
- **Cache local:** guarde a última resposta boa e mostre-a quando estiver sem internet (carro em garagem/túnel). Indique "atualizado às HH:MM" na tela.
- **Intervalo:** peça o que a tela mostra (ex.: hoje → +30 dias). Para uma visão mensal, `de` = primeiro dia do mês, `ate` = último.
- **Timeout** de rede de ~10 s e repetição com espera crescente (backoff) em `5xx`; **não** repita imediatamente em `401`/`429`.
- **Datas são strings** `YYYY-MM-DD` no fuso de Brasília. Não converta para UTC; use `LocalDate`.

### Exemplo Kotlin (OkHttp + kotlinx.serialization)

```kotlin
@Serializable
data class Evento(
    val id: String, val tipo: String, val titulo: String, val subtitulo: String? = null,
    val data: String, @SerialName("data_fim") val dataFim: String? = null,
    @SerialName("hora_inicio") val horaInicio: String? = null,
    @SerialName("hora_fim") val horaFim: String? = null,
    @SerialName("dia_inteiro") val diaInteiro: Boolean, val cor: String,
)

@Serializable
data class RespostaCalendario(val ok: Boolean, val hoje: String, val total: Int, @SerialName("proximo_voo") val proximoVoo: Evento? = null, val eventos: List<Evento>)

private val json = Json { ignoreUnknownKeys = true }

// base e token vêm de BuildConfig (veja "Guardando o token no Android")
fun buscarCalendario(client: OkHttpClient, base: String = BuildConfig.PONTE_AEREA_BASE_URL, token: String = BuildConfig.PONTE_AEREA_TOKEN, etagAnterior: String?): Pair<RespostaCalendario?, String?> {
    val req = Request.Builder()
        .url("$base/api/launcher/calendario?de=${LocalDate.now()}&ate=${LocalDate.now().plusDays(30)}")
        .header("Authorization", "Bearer $token")
        .apply { etagAnterior?.let { header("If-None-Match", it) } }
        .build()
    client.newCall(req).execute().use { r ->
        return when (r.code) {
            304 -> null to etagAnterior                       // nada mudou: mantém o cache
            200 -> json.decodeFromString<RespostaCalendario>(r.body!!.string()) to r.header("ETag")
            401 -> throw TokenInvalidoException()            // pedir novo token ao usuário
            429 -> throw LimiteException(r.header("Retry-After")?.toLongOrNull() ?: 300)
            else -> throw ErroServidorException(r.code)
        }
    }
}
```

### Teste rápido com `curl`

```bash
curl -s -H "Authorization: Bearer $LAUNCHER_TOKEN" \
  "https://<seu-dominio>/api/launcher/calendario?de=2026-11-23&ate=2026-12-31&passageiro=renato" | jq .
```

## Segurança — resumo

| Risco | Proteção |
|---|---|
| Vazamento do banco/env | Só o SHA-256 do token fica no servidor; token de 256 bits não é adivinhável |
| Token roubado do aparelho/APK | Revogação por dispositivo (remover o hash + redeploy); token só dá acesso de leitura ao calendário; APK com token não é distribuído |
| Token em logs/histórico | Aceito só no header `Authorization`; query string é ignorada |
| Força bruta / abuso | 256 bits de entropia + rate limit de 60 req/10 min por token |
| Timing attack | Comparação com `timingSafeEqual`, percorrendo todos os hashes |
| Configuração esquecida | Fail-closed: sem `LAUNCHER_TOKEN_SHA256`, tudo é `401` |
| Dados sensíveis na tela do carro | Resposta sem valores, pontos, localizador nem eventos pessoais |
| Navegador/terceiros | Sem CORS: o endpoint é para app nativo, não para páginas web |
| Interceptação | Apenas HTTPS (Vercel força TLS) |

## Referência técnica (para quem mantém o Ponte Aérea)

- Rota: `src/app/api/launcher/calendario/route.ts`
- Autenticação (pura, testada): `src/lib/launcherAuth.ts`
- Montagem do calendário (pura, testada): `src/lib/launcherCalendario.ts` — `fdsComigoEntre` replica a conta de FDS alternados de `TelaCalendario.tsx`; se uma mudar, mude a outra.
- Gerador de token: `scripts/gerar-token-launcher.mjs`
- Testes: `tests/launcher.test.ts`
- Env: `LAUNCHER_TOKEN_SHA256` (Vercel). Reaproveita `NEXT_PUBLIC_SUPABASE_URL` e `SUPABASE_SERVICE_ROLE_KEY` (a rota lê com a service role depois de autenticar o token, nunca expõe colunas fora da lista explícita).
- Rate limit: `dentroDoLimite("launcher", …)` (`src/lib/apiSeguranca.ts`, contador em `configuracoes`, chave `ratelimit_launcher_<prefixo do hash>`). É best-effort, como nas demais rotas.
- Sem mudança de schema (não há nada a rodar no Supabase).
