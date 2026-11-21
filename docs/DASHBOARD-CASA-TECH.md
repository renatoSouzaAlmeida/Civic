# Civic Renatin — tabelas para o dashboard da Casa Tech

Banco: projeto Supabase **CasaTechRenatin**, schema `public`. Todas as tabelas do carro têm o prefixo `civic_`.
O dashboard lê com o login OAuth do Supabase (papel `authenticated`) e **só enxerga os aparelhos vinculados ao usuário**
em `civic_acessos`. Tudo é somente leitura para o dashboard. Quem grava é o app do carro (e a função `civic-notificar`).

Chave de ligação entre as tabelas: a coluna **`dispositivo`** (identifica o aparelho/multimídia).

## Tabelas

| Tabela | Para que serve | Colunas principais | Uso sugerido no dash |
|---|---|---|---|
| `civic_veiculos` (view) | Lista os aparelhos/carros do usuário logado, sem expor segredos. **Cada aparelho se identifica**: modelo, Android e nome do carro | `dispositivo`, `nome`, `criado_em`, **`aparelho`** (ex.: `Samsung SM-T295`), **`android_versao`** (ex.: `Android 12 (API 31)`), **`nome_carro`** (nome · modelo · ano configurados no app, ex.: `Dona Quiqui · Fit · 2018`), `versao_app`, `ultimo_sinal` | Seletor de veículo mostrando "nome do carro · aparelho · Android" para distinguir Civic e Fit; selo "sem sinal há X" por `ultimo_sinal` |
| `civic_estacionamentos` | **Histórico de onde o carro ficou estacionado** (um registro por vez que parou) | `data`, `lat`, `lon`, `endereco`, `idade_gps_s` (0 = posição fresca; alto = GPS defasado), `origem` (`auto`, `manual` ou `simulado` = teste com a demonstração do OBD) | Mapa com o último ponto e trilha de vagas; lista "onde parei" por data; destacar pontos com GPS defasado |
| `civic_eventos` | Alertas e avisos gerados pelo app (motor, bateria, falhas, manutenção, estacionamento) | `tipo`, `mensagem`, `enviado` (foi ao Telegram), `criado_em` | Linha do tempo de alertas; contagem por tipo; filtro por período |
| `civic_manutencao` | Plano de manutenção e situação de cada item | `id`, `nome`, `intervalo_km`, `intervalo_meses`, `ultimo_km`, `ultima_data`, `situacao` (vencido/próximo/ok), `atualizado_em` | Cartões de manutenção com semáforo; itens vencidos no topo |
| `civic_abastecimentos` | Registro de abastecimentos | `data`, `km`, `litros`, `preco_litro`, `combustivel`, `tanque_cheio` | Consumo km/l (tanque cheio a tanque cheio), gasto por mês, custo por km, preço do litro ao longo do tempo |
| `civic_estados` | **Resumo do último uso do carro**, enviado quando o carro liga de novo (um registro por uso) | `registrado_em` (quando foi o último uso), `enviado_em`, `versao_app`; motor: `tensao`, `temp_motor`, `temp_admissao`, `vel_max`, `rpm_max`, `stft`, `ltft`; combustível: `nivel_combustivel`, `consumo_medio`; falhas: `luz_motor`, `dtc`, `dtc_pendentes` (listas de códigos); viagem: `viagem_km`, `viagem_duracao_s`, `viagem_vel_media`; posição: `lat`, `lon`, `gps_em`; bateria: `bateria_repouso`, `bateria_minima`, `bateria_avaliacao` (Boa/Atencao/Fraca); **saúde do motor (OBD avançado):** `termostato_s` (segundos até 80 °C a frio), `catalisador_razao` (0 a 1; abaixo de 0,35 bom, acima de 0,65 suspeito), `marcha_lenta_desvio` (rpm; acima de 45 irregular), `dreno_v_h` (V/h de queda parado; acima de 0,02 suspeito), `km_desde_apagar` (km desde que os códigos foram apagados), `temp_cambio` (°C, se configurado), `freeze_frames` (jsonb: lista de {codigo, em, itens}), **`pneus` (TPMS, jsonb, só quando o dono ativa os sensores; nulo se não houver)**: `{"DE":{"psi":32.1,"c":28.5,"bat":90,"em":ms}, "DD":…, "TE":…, "TD":…}` (dianteiro/traseiro × esquerdo/direito; `psi` pressão, `c` °C, `bat` % da bateria do sensor) | Cartão "Estado atual" com o último registro; gráficos de tensão, temperatura, consumo e bateria ao longo do tempo; lista de falhas (DTC) por data; mapa da última posição. Campos podem vir vazios (o carro nem sempre informa tudo) |
| `civic_status_atual` (view) | **Status online do carro**: uma linha por veículo, atualizada a cada 60 s com o motor ligado (5 min desligado) | `atualizado_em`, `online` (true se avisou há menos de 2,5 batidas), `motor_ligado`, `lat`, `lon`, `vel` (km/h), `tensao`, `viagem`, `versao_app`, `heartbeat_s` | Selo "Online / Offline há X min"; mapa com a posição ao vivo; assine por Realtime em `civic_status` para atualizar sem recarregar |
| `civic_status_atual.posicao_fonte` | `gps` ou `rede`: de onde veio a posição do status. Com `rede` a posição é aproximada (aparelho sem GPS) | — | Mostrar "posição aproximada" no mapa quando for `rede` |
| `civic_posicoes` | **Trajeto**: um ponto a cada 15 s com o motor ligado ou andando | `em`, `lat`, `lon`, `vel` (km/h), `viagem` (id da viagem = hora em que o motor ligou, em ms; agrupa os pontos) | Desenhar a linha da viagem no mapa (filtre por `viagem`, ordene por `em`); velocidade ao longo do trajeto; replay da viagem. Realtime em `civic_posicoes` acompanha a viagem em curso |
| `civic_ignicao` | **Eventos de ignição**: linha do tempo de liga/desliga | `tipo` (`motor_ligou`, `motor_desligou`, `multimidia_ligou`, `multimidia_desligou`), `em`, `lat`, `lon`, `tensao`, `viagem` | Linha do tempo do dia; duração do uso (`motor_ligou` até `motor_desligou` da mesma `viagem`); horários típicos de saída e chegada |
| `civic_viagens` | **Viagens com modo de condução (Driving Score)**: uma linha por viagem, enviada quando ela termina. **Funciona com OBD ou só com GPS** (Honda Fit com CarPlay/Android Auto na frente, sem OBD). Sem internet, o app guarda e envia depois | `inicio`, `fim`, `duracao_s`, `em_movimento_s`, `distancia_km`, `vel_media`, `vel_max`; **condução:** `nota` (0 a 100: 90+ Muito bom, 75+ Bom, 55+ Atenção, abaixo Precisa melhorar), `frenagens`, `aceleracoes`, `excessos` (vezes acima do limite de velocidade configurado no app, por pelo menos 5 s), `excesso_max_kmh` (maior velocidade acima do limite) e `excesso_s` (tempo total acima do limite), `rpm_alto` e `rpm_max` e `consumo_km_l` (**só com OBD**; nulos quando `fonte = 'gps'`); **por onde andou:** `lat_ini`, `lon_ini`, `lat_fim`, `lon_fim`, `endereco_ini`, `endereco_fim`; `fonte` (`obd` ou `gps`), `simulado`, `versao_app` | **Aba Viagens:** lista com origem → destino, nota em anel colorido, km, duração; cartão "Resumo da condução" da viagem escolhida (anel com a nota, selo, frenagens, acelerações, excessos, RPM elevado, consumo); evolução da nota ao longo dos dias; ranking das melhores e piores viagens; **por onde o carro anda quando o dono não está**: trajeto no mapa (junte com `civic_posicoes` pela faixa `inicio`–`fim`) e lugares mais frequentes. Mostre "—" nos campos nulos e o selo "Só GPS" quando `fonte = 'gps'` |
| `civic_areas` | **Áreas do geofence** cadastradas no app (Casa, Trabalho...). Quem sai/entra gera um aviso em `civic_eventos` com `tipo = 'geofence'` | `id`, `nome`, `lat`, `lon`, `raio_m`, `ativa`, `removida` (true = apagada no app; ignore), `atualizado_em` | Mapa com os círculos das áreas; linha do tempo de entradas e saídas (eventos `geofence`); destacar viagens que saem das áreas fora do horário |
| `civic_eventos` (tipos novos) | Alem dos alertas já listados: `geofence` (saiu/entrou de área), `sos` e `sos_teste` (botão SOS acionado ou testado, com localização e dados do carro), `local_desconhecido` aparece dentro do texto do estacionamento | `tipo`, `mensagem`, `criado_em` | Cartão "Segurança": últimos SOS (em vermelho), saídas de área e estacionamentos em local desconhecido |
| `civic_abastecimentos_analise` (view) | **Histórico de abastecimentos com análise pronta** (use esta view em vez da tabela). Um abastecimento é criado **automaticamente** quando o nível do tanque sobe; o valor pago entra depois | `data`, `km`, `litros`, `combustivel`, `aditivada`, `tanque_cheio`, `automatico`, `nivel_antes`, `nivel_depois`; valores: `total_pago`, `preco_litro` (pode ser calculado do total), `aguardando_valor` (true = o app ainda não recebeu o valor pago); análise: `km_rodados`, `dias_desde_anterior`, `consumo_km_l`, `custo_por_km` | Nº de abastecimentos; consumo médio e por abastecimento; custo por km; preço pago por litro ao longo do tempo; gasto por mês; km por tanque; dias entre abastecimentos; nível do tanque ao abastecer; comparar aditivada x comum; selo "Aguardando valor" |
| `civic_abastecimentos` | Tabela base (as mesmas linhas, sem a análise). Prefira a view acima | `preco_litro` e `valor_total` podem ser nulos (o dono informa só um dos dois, ou nenhum ainda) | Só se precisar de algum campo cru |
| `civic_acessos` | Quais aparelhos o usuário do dashboard pode ver (preenchida sozinha para aparelhos novos) | `user_id`, `dispositivo` | Só administração; o dash não precisa consultar |

Não use `civic_dispositivos` no dash (tem token e chat do Telegram; o usuário logado não tem acesso a ela).

## Visões de análise e ajustes de 21/11/2026 (para a Casa Tech usar)
- **`civic_viagens_comparativo`** (view): todas as colunas de `civic_viagens` + `nota_media_anteriores` (média das 10 viagens anteriores do mesmo aparelho), `nota_vs_media` (diferença), `ranking_nota` e `total_viagens`. Serve para "esta viagem ficou X pontos acima/abaixo da média" e para o ranking de melhores e piores.
- **`civic_viagens_semanal`** (view): por aparelho e semana (segunda a domingo, horário de São Paulo): `viagens`, `km`, `horas`, `nota_media`, `frenagens`, `aceleracoes`, `excessos`, `consumo_medio`, `vel_max`, `so_simulado`. Serve para relatórios e a evolução semanal.
- **`civic_estados_diario`** (view): por aparelho e dia: `usos`, `tensao_media`, `bateria_minima`, `temp_motor_max`, `consumo_medio`, `stft_medio`, `ltft_medio`, `termostato_s`, `marcha_lenta_desvio`, `dreno_v_h`. Serve para as tendências de diagnóstico (alterações persistentes).
- **`civic_estacionamentos.desconhecido`** (boolean): true quando o carro estacionou fora dos lugares de costume (depois de 5 vagas aprendidas e fora das áreas do geofence). Destacar na lista e no mapa.
- **Vínculo automático:** todo aparelho novo é vinculado sozinho aos usuários do dashboard (antes 8 de 11 aparelhos ficavam invisíveis). A Casa Tech passa a ver todos os aparelhos reais.
- **Tempo real:** `civic_eventos` entrou no Realtime (SOS, geofence, colisão, uso incomum chegam na hora). Assine com filtro por `dispositivo` e destaque `tipo = 'sos'`.

## Como o modo de condução (viagens) é gravado
O launcher mede a condução **pelo GPS do serviço Vigia**, então vale mesmo com CarPlay ou Android Auto na frente e sem OBD:
frenagem brusca (queda de 11 km/h por segundo ou mais), aceleração brusca (subida de 9 km/h por segundo ou mais), excesso de velocidade
(acima do limite escolhido em Config. > Segurança; padrão 110 km/h) e fadiga. Com OBD entram também o RPM elevado (4.500 rpm ou mais) e o consumo.
A nota é `100 − (6 × frenagens + 4 × acelerações + 3 × excessos + 2 × RPM elevado)`, diluída em viagens longas (a partir de 10 km), de 0 a 100.
A viagem começa quando o carro anda (ou o motor liga) e termina após 5 min parado; ao terminar o app grava uma linha em `civic_viagens`
(origem e destino com endereço, sem duplicar: chave `dispositivo + inicio`). O trajeto ponto a ponto continua em `civic_posicoes` e a ignição em `civic_ignicao`.
Quando outra pessoa usa o carro: a partida detectada pelo GPS passa pela checagem de "uso incomum" (aviso no Telegram) e o status online mostra a posição ao vivo.

## Como o histórico de estacionamento é gravado
O app avisa quando o carro estaciona (ignição desliga, motor para ou multimídia desliga). A função `civic-notificar`
manda o Telegram e **grava a linha em `civic_estacionamentos` na mesma chamada**. Reenvio do mesmo aviso não duplica
(chave única `dispositivo + data`). Se não houver posição de GPS, só o aviso de texto sai (em `civic_eventos`) e nada é
gravado em `civic_estacionamentos`, pois lat/lon são obrigatórios.

## Acesso ao dashboard
A Casa Tech é **só do dono** (um único usuário, login OAuth do Supabase). Todo aparelho real novo é **vinculado automaticamente** a esse usuário (gatilho no banco), então não há mais vínculo manual:
os aparelhos aparecem em `civic_veiculos` assim que se cadastram. Só o aparelho simulado precisa de vínculo manual (SQL na seção "Dados simulados").

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

## Dados de simulação (demonstração do OBD)
Ao parar a demonstração do OBD no app, ele grava como teste: uma vaga em `civic_estacionamentos` com `origem = 'simulado'`,
um resumo em `civic_estados` com `versao_app` terminando em "(simulação)" e o par `motor_ligou`/`motor_desligou` em
`civic_ignicao`. O dash pode mostrar esses itens com um selo "Simulação" ou filtrá-los.

## Como o estado do carro é gravado
Durante o uso o app guarda o resumo no aparelho (a cada 30 s e quando o motor desliga). **Quando o carro liga de novo**, envia
o último resumo ao Supabase (uma linha em `civic_estados`). Sem internet, fica pendente e tenta de novo a cada 5 min com o
motor ligado. Então o dash mostra o estado **do último uso**, não ao vivo: `registrado_em` diz de quando é. Reenvio não duplica.

## Como o abastecimento é registrado
O app lê o nível do tanque pelo OBD (se o carro informar). O nível só desce com o uso: uma **subida firme (10 pontos ou mais,
medida pela mediana de 5 leituras)** significa abastecimento. O app cadastra na hora: gasolina, tanque cheio, litros estimados
(`(nível depois − antes) × capacidade do tanque`, capacidade ajustável em Config.) e km do odômetro do app, e avisa no Telegram.
Depois o dono informa o **valor total pago** OU o **preço por litro** (basta um) e marca se foi **aditivada**. Enquanto isso,
`aguardando_valor = true` e `total_pago`/`preco_litro` vêm nulos: o dash deve mostrar o selo "Aguardando valor" e não somar o item
nos gastos. O consumo usa o método tanque cheio a tanque cheio: `km_rodados ÷ litros`.

## Como a telemetria é enviada
Posição, ignição e status ficam primeiro no aparelho e saem em pacotes pequenos a cada 30 s (sem internet, ficam na fila e saem
depois). Cada ponto é gravado uma única vez (chave única). "Online" não é um campo gravado: é calculado na view
`civic_status_atual` pelo horário do último aviso.

## Dados simulados para validar o dash
O banco tem um veículo fictício **"Civic (simulado)"** com 30 dias de uso (59 viagens, **cada uma com nota de condução em `civic_viagens`**, ~7.600 pontos de trajeto, 3 abastecimentos, ignição, estacionamentos,
estados, alertas, abastecimentos e manutenção). Para vê-lo no dash, vincule o seu usuário a ele:

```sql
insert into public.civic_acessos (user_id, dispositivo)
select '<ID_DO_USUARIO>', token from public.civic_dispositivos where nome = 'Civic (simulado)';
```

Regerar (dados sempre recentes, já com os campos de saúde do motor): `select public.civic_simular_completo(30);` — apagar: `delete from public.civic_dispositivos where nome = 'Civic (simulado)';`.
O script está em `supabase/simulacao.sql`. O veículo simulado aparece em `civic_veiculos` com o nome "Civic (simulado)", separado do carro real.
