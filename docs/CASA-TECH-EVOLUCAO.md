# Casa Tech — o que precisa evoluir (22/11/2026, app 1.1.x)

Documento para colar no chat da Casa Tech **junto com** `CASA-TECH-PEDIDO-UNICO.md` (que continua valendo: tarefas 1 a 10, dados e layout). Aqui está só o que **mudou ou ficou pendente** depois dele. Regras que não mudam: área "Veículo" isolada, **um único acesso (o do dono)**, login separado do módulo, sem perfis, histórico permanente, onde não houver dado mostrar "aguardando" e nunca zero.

## A. Já pronto no launcher e no banco, **falta a tela** na Casa Tech (por prioridade)
| # | Tela | Dados (já chegando) | O que mostrar |
|---|---|---|---|
| 1 | **Linha do tempo da viagem** (aba Viagens, ao abrir uma viagem) | `civic_viagens.eventos` (jsonb): `[{em, tipo, texto}]`, `tipo` = `partida`, `frenagem`, `aceleracao`, `excesso`, `rpm`, `colisao`, `sos`, `alerta`, `estacionou`. Nulo em viagens antigas | Lista vertical ordenada com hora, ícone e texto; `colisao` e `sos` em vermelho, `alerta` e excessos em amarelo; junto do mapa do trajeto (`civic_posicoes` pela faixa `inicio`–`fim`) |
| 2 | **Segurança** (tarefa 4 do pedido único) | `civic_eventos`, `civic_areas`, `civic_estacionamentos`, `civic_caixa_preta*` | Linha do tempo única de SOS, colisão, geofence, local desconhecido, alerta de motor e caixa-preta, com mapa e os círculos das áreas. **Novo:** `civic_eventos` agora tem `event_id`, `ocorreu_em` (quando aconteceu no carro) e `fonte`; ordenar por `ocorreu_em` (quando nulo, `criado_em`) e mostrar "enviado com atraso" quando `criado_em − ocorreu_em` > 2 min |
| 3 | **Relatórios e tendências** (tarefas 5 e 6) | views `civic_viagens_semanal`, `civic_estados_diario`, `civic_viagens_comparativo` | Evolução semanal de nota, consumo e km; tendências de diagnóstico (tensão, fuel trims, temperatura, marcha lenta, dreno) com alerta de mudança **persistente**; comparar duas viagens |
| 4 | **Central de lançamentos com ditado** (tarefa 9) | tabelas do CRUD (ver seção "Entrada de dados") | Combustível, manutenção, km, despesas, vencimentos, geofence, contatos do SOS e configuração do veículo |
| 5 | **Caixa-preta** (tarefa 10) | `civic_caixa_preta_resumo`, `civic_caixa_preta` | Gráfico de velocidade e rpm antes e depois, mapa, origem e qualidade de cada sinal, anotação; aviso de que é diário técnico, não EDR |
| 6 | **Mapa ao vivo, pneus e visão geral** (tarefas 2, 7 e 8) | `civic_status_atual`, `civic_posicoes`, `civic_estados` | Como no pedido único |

## B. O launcher já calcula, **mas ainda não envia** (precisa de entrega do launcher antes da tela)
Estes itens existem só no aparelho. Quando você quiser a tela, peço o envio (migration + app) em uma entrega:
1. **Análise de carga elétrica** (alternador, queda de tensão na partida, bateria em repouso): hoje só local. Proposta: tabela `civic_partidas` (data, repouso, mínima, carga do alternador) + achados.
2. **Mapa de saúde do veículo** (injeção, arrefecimento, mistura, bateria, câmbio, marcha lenta, pneus, cada um com estado, fonte e confiança): hoje só local. Proposta: guardar o resumo em `civic_estados.saude` (jsonb).
3. **Qualidade dos dados** (fonte, idade e estado Atual/Atrasada/Indisponível de cada leitura do OBD, e quantas leituras impossíveis foram descartadas): hoje só local.
4. **Estado da sincronização** (fila pendente, falhas, último envio): hoje só local. Proposta: campos em `civic_status`.

## C. Painel resumido dentro do launcher (já criado por vocês) — o que o launcher entrega
O launcher agora abre `https://casa-tech-renatin.vercel.app/veiculo/launcher` numa WebView (Veículo > aba **Painel**), com a ponte `CivicLauncher.acesso()` conforme `LAUNCHER-PAINEL.md`. Pedidos para a página:
- Mostrar a **idade do dado** ("atualizado há X") e "sem sinal há X" quando `civic_status.atualizado_em` for antigo.
- Quando o item B1 e B2 estiverem enviados, acrescentar à página: avaliação da bateria e do alternador e os subsistemas em atenção/alerta (sem localização, como hoje).
- Manter **somente leitura** e sem localização na resposta de `civic-painel`.

## D. Ajustes de conta e banco (lado Casa Tech/Supabase)
- Ligar a **proteção contra senhas vazadas** do Supabase Auth (painel: Authentication > Providers > Email/Password) para o seu login.
- A view `civic_veiculos` é *security definer* de propósito (esconde token e chat do Telegram) e já filtra pelos seus aparelhos; manter assim (o aviso vermelho do linter do Supabase sobre ela é esperado).
- Não há nada a construir de perfis, convites ou permissões por pessoa.

## E. Depois de tudo (release 1.4.0)
**Assistente mecânico (IA)**: explicar códigos de falha e tendências com ressalvas e fontes, sem prescrever, nunca comandar sistemas do carro. Só depois que A, B e C estiverem prontos e com dados reais.

## Ordem sugerida
A1 → A2 → A3 → A4 → A5 → A6 → (peço B1 a B4) → C → E.
