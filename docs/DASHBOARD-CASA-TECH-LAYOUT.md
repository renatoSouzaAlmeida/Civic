# Dashboard do veículo na Casa Tech — proposta de layout

Referência visual: protótipo com dados de exemplo (link no chat). Este documento descreve o layout e de onde vem cada dado.
As tabelas e colunas estão em `DASHBOARD-CASA-TECH.md`.

## Princípios
- **Leitura rápida primeiro:** a aba inicial mostra só o que importa numa olhada (estado, alertas, última viagem, vagas, tendências).
- **Cor com significado:** verde = ok, âmbar = atenção, vermelho = ação necessária, cinza = informativo. Uma cor de destaque só (verde-azulado).
- **Números em fonte monoespaçada** com `tabular-nums`; rótulos pequenos em cinza; títulos em peso 600.
- Cartões brancos com borda fina e raio 12 px; sem sombras pesadas. Tema claro e escuro.
- Responsivo: 4 colunas de indicadores → 2 → 1; mapas e gráficos em SVG escalável; tabelas com rolagem horizontal própria.

## Estrutura
**Cabeçalho:** nome do carro, modelo · ano · placa, selo **Online/Offline** (`civic_status_atual.online`, com "há X min" quando offline) e seletor de veículo.
**Abas:** Visão geral · Viagens · Estacionamento · **Abastecimentos** · **Manutenção** (a manutenção sai da visão geral).

### Visão geral
1. **Faixa de 4 indicadores:** Estado agora (motor ligado/desligado + há quanto tempo, selo de falhas) · Bateria (tensão + selo Boa/Atenção/Fraca) · Combustível (% com barra, média km/l) · Últimos 7 dias (km, nº de viagens, tempo, variação vs. semana anterior).
2. **Linha 7/5:** **Última viagem** (mapa do trajeto, início vazado e fim cheio, quatro mini-indicadores: distância, duração, média/máx., consumo) | **Atenção** (lista ordenada por gravidade: manutenção vencida/próxima com barra de uso do intervalo, falhas DTC, alertas recentes).
3. **Linha 7/5:** **Quilômetros por dia** (barras dos últimos 14 dias, hoje em destaque) + tabela curta das últimas viagens | **Onde parou** (mapa com a última vaga em vermelho e as anteriores em verde, 3 últimas vagas com selo de precisão do GPS).
4. **Três tendências (30 dias):** tensão da bateria (repouso e mínima na partida), consumo médio km/l, temperatura máxima do motor.

### Viagens
Filtro 7/14/30 dias, gráfico de km por dia e **tabela completa**: data, trajeto, saída, duração, distância com barra proporcional, média, máxima, consumo, situação (Normal / Falha). Cabeçalho fixo, linha com hover.

### Estacionamento
Mapa com as vagas dos últimos 14 dias, ranking de **locais mais frequentes** (contagem + barra) e tabela: quando, endereço, **precisão do GPS** (Exata / há N s / Defasada), origem (automática/manual), link "Abrir no mapa".

### Abastecimentos
- **Quatro indicadores:** nº de abastecimentos (90 dias, intervalo médio em dias, selo "N aguardando valor") · consumo médio km/l (faixa mín–máx) · custo por km (com preço médio do litro) · gasto no mês (com km por tanque).
- **Três gráficos:** consumo por abastecimento (barras, o mais recente em destaque) · preço pago por litro (linha) · gasto por mês (barras).
- **Insights em texto:** nível médio do tanque ao abastecer (reserva), aditivada x comum (km/l e custo por km, com aviso de poucas amostras), abastecimentos aguardando valor, autonomia estimada por tanque. Gráfico do nível do tanque antes de cada abastecimento.
- **Tabela:** data, km, litros, tanque antes → depois, preço/l, total pago (ou selo **Aguardando valor**), km rodados, dias, consumo, tipo (Comum/Aditivada), registro (Automático/Manual). Fonte: view `civic_abastecimentos_analise`.

### Saúde do motor (sugestão: aba "Saúde" ou bloco na visão geral)
Cartões com semáforo (verde/âmbar/vermelho) e tendência, a partir da série de `civic_estados`:
**Termostato** (`termostato_s` em minutos; alerta se subir muito acima da mediana) · **Catalisador** (`catalisador_razao`, faixas 0,35 e 0,65) · **Marcha lenta** (`marcha_lenta_desvio`, limite 45 rpm) · **Dreno de bateria** (`dreno_v_h`, limite 0,02 V/h) · **Câmbio** (`temp_cambio`, alerta a 110 °C) · **Códigos apagados** (`km_desde_apagar`; valor baixo = apagado há pouco, sem vistoria) · **Freeze frames** (`freeze_frames`: lista por código com rpm, velocidade, temperatura e carga no instante da falha).

### Manutenção
Tabela do plano: item, intervalo, última vez (data · km), situação (Em dia / Próximo / Vencido / Sem registro) e **barra de uso do intervalo**. Itens vencidos e sem registro no topo.

## De onde vem cada dado
| Bloco | Fonte |
|---|---|
| Online/Offline, motor, posição ao vivo | `civic_status_atual` (Realtime) |
| Bateria, combustível, falhas, consumo, temperatura | último registro de `civic_estados`; tendências = série de `civic_estados` (30 dias) |
| Últimos 7 dias (km, nº de viagens, tempo) | `civic_estados` (`viagem_km`, `viagem_duracao_s`) ou agrupando `civic_posicoes` por `viagem` |
| Mapa e trajeto da última viagem | `civic_posicoes` filtrado pela `viagem` mais recente, ordenado por `em` |
| Viagens (lista) | `civic_ignicao` (`motor_ligou` → `motor_desligou` da mesma `viagem`) combinado com `civic_estados` por horário |
| Vagas e precisão do GPS | `civic_estacionamentos` (`idade_gps_s`: < 10 s exata; < 120 s ok; maior = defasada) |
| Atenção: alertas e falhas | `civic_eventos` (30 dias) e `dtc`/`dtc_pendentes` de `civic_estados` |
| Aba Abastecimentos | `civic_abastecimentos_analise` (view com consumo, custo por km e dias entre abastecimentos) |
| Atenção e aba Manutenção | `civic_manutencao` (`situacao`, `ultimo_km`, `ultima_data`, intervalos) |

## Dados para validar
Use o veículo **"Civic (simulado)"** (ver `DASHBOARD-CASA-TECH.md`): 7 dias de viagens, vagas, estados e alertas. Regerar: `select public.civic_simular(30);`.
