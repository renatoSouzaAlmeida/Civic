# Casa Tech — Análise de condução por viagem (pedido de tela, 22/11/2026)

**Para colar no chat da Casa Tech.** Proposta visual: `docs/designs/analise-conducao.png` (a mesma já feita no launcher, em Veículo > Viagem). Na aba **Viagens**, ao escolher uma viagem na lista, mostrar a **Análise de condução** dessa viagem, abaixo do cabeçalho dela e acima da linha do tempo.

## Dados (tabela `civic_viagens`, uma linha por viagem)
| Campo | Significado |
|---|---|
| `nota` (0–100), `frenagens`, `aceleracoes`, `excessos`, `excesso_max_kmh`, `rpm_alto`, `rpm_max` | condução da viagem (já existiam) |
| `vel_media`, `vel_max`, `distancia_km`, `duracao_s`, `em_movimento_s`, `consumo_km_l` | resumo (já existiam; `consumo_km_l` só com OBD) |
| **`marcha_lenta_s`** (novo) | segundos com o motor ligado e o carro parado. Só com OBD; nulo no GPS |
| **`serie`** (novo, jsonb) | série da viagem, uma amostra a cada ~10 s (no máx. 240 pontos; em viagens muito longas o passo dobra): lista de `[t_s, vel_kmh, rpm, acelerador_pct, temp_motor_c, consumo_kml_x10]`. **`-1` = sem leitura.** `rpm` vem só com OBD e motor ligado; `acelerador_pct`, `temp_motor_c` e `consumo` só com OBD. Dividir o consumo por 10. Nulo em viagens antigas |
| `eventos` (jsonb) | linha do tempo (já descrita em `CASA-TECH-EVOLUCAO.md`) |
| `fonte` | `obd` ou `gps`: com `gps` não há rpm, aceleração, temperatura nem consumo — mostrar "—" e a explicação "Sem OBD nesta viagem", nunca zero |

## Layout (como na imagem)
1. **Faixa de indicadores** (3 cartões): *Aceleração média* (média de `acelerador_pct` ≥ 0, com barra), *RPM médio* (média de `rpm` > 400; barra verde até 3.000, amarela até 4.000, vermelha acima), *Consumo médio* (`consumo_km_l` e mini gráfico de linha da série de consumo).
2. **Cartão da nota** ("Condução eficiente"): barra grande com a `nota`, selo (Muito bom ≥ 90, Bom ≥ 75, Atenção ≥ 55, Precisa melhorar), a nota em % e uma frase ("Ritmo suave, sem eventos" quando não houver evento; senão a contagem de frenagens, acelerações, excessos e RPM alto). Abaixo: Velocidade média, Distância, Duração e Marcha lenta (`marcha_lenta_s` em mm:ss).
3. **Três gráficos:** *Aceleração (pedal)* (linha de 0 a 100 %), *RPM · tempo em cada faixa* (histograma de 500 em 500 rpm, de 500 a 6.000; **verde na zona eficiente 1.500–2.500**, vermelho a partir de 4.000) e *Temperatura do motor* (linha, com o máximo; sem OBD mostrar a velocidade).
4. **Coluna escura à direita — "Eventos da viagem":** frenagens, acelerações, excessos (com a máxima), RPM elevado, velocidade máxima, RPM máximo, temperatura máxima e quantidade de registros da linha do tempo.
5. Tela de 1000 px ou mais em duas colunas (como na imagem); abaixo disso, empilhar. Claro/escuro automático.

## Regras
- Somente leitura. Onde não houver dado, "—" e explicação; nunca zero.
- Viagens antigas (sem `serie`) mostram só o resumo e a nota, sem gráficos, com a nota "Gráficos disponíveis nas viagens novas".
- Mesma lógica do launcher (`Viagem.nota`): `100 − (6·frenagens + 4·acelerações + 3·excessos + 2·rpm_alto)`, diluída em viagens longas (a partir de 10 km).
