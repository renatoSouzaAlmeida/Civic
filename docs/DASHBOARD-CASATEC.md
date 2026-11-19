# Civic Renatin — tabelas para o dashboard da CasaTech

Banco: projeto Supabase **CasaTechRenatin**, schema `public`. Todas as tabelas do carro têm o prefixo `civic_`.
O dashboard lê com o login OAuth do Supabase (papel `authenticated`) e **só enxerga os aparelhos vinculados ao usuário**
em `civic_acessos`. Tudo é somente leitura para o dashboard. Quem grava é o app do carro (e a função `civic-notificar`).

Chave de ligação entre as tabelas: a coluna **`dispositivo`** (identifica o aparelho/multimídia).

## Tabelas

| Tabela | Para que serve | Colunas principais | Uso sugerido no dash |
|---|---|---|---|
| `civic_veiculos` (view) | Lista os aparelhos/carros do usuário logado, sem expor segredos | `dispositivo`, `nome`, `criado_em` | Seletor de veículo; ponto de partida de todas as consultas |
| `civic_estacionamentos` | **Histórico de onde o carro ficou estacionado** (um registro por vez que parou) | `data`, `lat`, `lon`, `endereco`, `idade_gps_s` (0 = posição fresca; alto = GPS defasado), `origem` (`auto` ou `manual`) | Mapa com o último ponto e trilha de vagas; lista "onde parei" por data; destacar pontos com GPS defasado |
| `civic_eventos` | Alertas e avisos gerados pelo app (motor, bateria, falhas, manutenção, estacionamento) | `tipo`, `mensagem`, `enviado` (foi ao Telegram), `criado_em` | Linha do tempo de alertas; contagem por tipo; filtro por período |
| `civic_manutencao` | Plano de manutenção e situação de cada item | `id`, `nome`, `intervalo_km`, `intervalo_meses`, `ultimo_km`, `ultima_data`, `situacao` (vencido/próximo/ok), `atualizado_em` | Cartões de manutenção com semáforo; itens vencidos no topo |
| `civic_abastecimentos` | Registro de abastecimentos | `data`, `km`, `litros`, `preco_litro`, `combustivel`, `tanque_cheio` | Consumo km/l (tanque cheio a tanque cheio), gasto por mês, custo por km, preço do litro ao longo do tempo |
| `civic_acessos` | Quais aparelhos cada usuário do dashboard pode ver | `user_id`, `dispositivo` | Só administração; o dash não precisa consultar |

Não use `civic_dispositivos` no dash (tem token e chat do Telegram; o usuário logado não tem acesso a ela).

## Como o histórico de estacionamento é gravado
O app avisa quando o carro estaciona (ignição desliga, motor para ou multimídia desliga). A função `civic-notificar`
manda o Telegram e **grava a linha em `civic_estacionamentos` na mesma chamada**. Reenvio do mesmo aviso não duplica
(chave única `dispositivo + data`). Se não houver posição de GPS, só o aviso de texto sai (em `civic_eventos`) e nada é
gravado em `civic_estacionamentos`, pois lat/lon são obrigatórios.

## Liberar o acesso ao dashboard (uma vez)
Entre na CasaTech com o login OAuth, pegue o `id` do usuário em `auth.users` e vincule ao aparelho:

```sql
insert into public.civic_acessos (user_id, dispositivo)
select '<ID_DO_USUARIO>', token from public.civic_dispositivos;  -- todos os aparelhos; filtre por nome se quiser só um
```

## Exemplos de consulta (supabase-js)
```js
// veículos do usuário
const { data: veiculos } = await supabase.from('civic_veiculos').select('*')

// últimas 20 vagas
const { data: vagas } = await supabase.from('civic_estacionamentos')
  .select('data,lat,lon,endereco,idade_gps_s,origem').eq('dispositivo', dispositivo)
  .order('data', { ascending: false }).limit(20)

// alertas dos últimos 30 dias
const { data: alertas } = await supabase.from('civic_eventos')
  .select('tipo,mensagem,criado_em').eq('dispositivo', dispositivo)
  .gte('criado_em', new Date(Date.now() - 30*864e5).toISOString()).order('criado_em', { ascending: false })

// manutenção vencida ou próxima
const { data: manut } = await supabase.from('civic_manutencao')
  .select('*').eq('dispositivo', dispositivo).in('situacao', ['Vencido','Proximo'])
```

## O que ainda vai entrar (backlog)
Posição ao vivo e trajeto das viagens, status online do carro, tensão da bateria, temperatura, rpm, velocidade,
fuel trims, histórico de falhas (DTC) e eventos de ignição. O app grava localmente e envia o último estado ao ligar o
carro. Quando chegar, ganha tabelas próprias e este documento é atualizado.
