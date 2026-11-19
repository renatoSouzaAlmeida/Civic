# Backlog — Civic Renatin

Itens combinados e ainda não feitos. Ao concluir um item, remova daqui (ou marque como feito).

## OBD
- [x] **Tela de falhas (DTC) no OBD** — listar os códigos de falha lidos pelo adaptador (modo 03),
  com descrição em português, luz de injeção (MIL) acesa ou não e opção de apagar os códigos (modo 04),
  com confirmação. Acessível a partir da tela do OBD.

- [ ] **Combustível (quando o adaptador chegar)** — ler os PIDs suportados (0100/0120/0140) no Civic e então:
  nível do tanque se o carro informar o PID 012F; consumo instantâneo e médio (km/l) calculado pela vazão de ar
  (MAF, 0110) ou, se não houver MAF, estimado pelo MAP (010B) + temperatura do ar + rpm. O Civic 2012 é flex:
  o cálculo precisa saber se é gasolina ou etanol (opção em Config. e ajuste fino comparando com abastecimentos).

- [ ] **Luzes-espia reais (farol, farolete, setas, pisca-alerta, piloto automático)** — a tela já está pronta
  (aparecem esmaecidas enquanto o dado é desconhecido; no modo Demo acendem). Esses sinais NÃO fazem parte do
  OBD-II padrão: ficam na rede CAN do Honda. Com o adaptador em mãos, testar o modo monitor do ELM327 (ATMA)
  para ver se esses quadros chegam pela tomada OBD do Civic 2012 e, se chegarem, descobrir os IDs/bits de cada
  sinal. Pode não ser possível (muitos Honda deixam a carroceria numa rede separada da tomada).
  A luz de injeção (MIL) e a temperatura alta já vêm de dados reais do OBD.

## Funcionalidades de especialista (ordem de prioridade)
1. [x] **Alertas inteligentes** (sobre qualquer tela): motor frio (< 70 °C) com giro alto (> 3.000 rpm);
   superaquecimento (> 105 °C); alternador sem carga (< 13,2 V com motor ligado); bateria fraca (< 12,2 V desligado).
2. [x] **Tela de falhas (DTC)** — ver item acima, em OBD.
3. [x] **Registro de abastecimentos** — consumo real (tanque cheio a tanque cheio), gasto no mês, custo por km.
4. [x] **Etanol ou gasolina?** — com a proporção do consumo real deste carro (ou 70%).
5. [x] **Computador de bordo** — viagem atual (distância, tempo, média, máxima, consumo e custo) e histórico de viagens.
6. [x] **Odômetro do app** — km informado + distância somada pela velocidade (o Civic 2012 não informa o odômetro no OBD).
   Hoje o km vem do último abastecimento ou do valor digitado na tela de manutenção.
7. [x] **Lembretes de manutenção** — plano padrão do Civic G9 1.8 (óleo, filtros, rodízio, alinhamento, fluido de freio,
   pastilhas, arrefecimento, velas, ATF), vence pelo que chegar primeiro (km ou tempo); histórico do último feito.
8. [ ] **Notificações no celular + Supabase** — sincronizar abastecimentos, manutenção e alertas numa base Supabase e
   avisar no celular (manutenção vencendo, alertas do motor, falhas DTC).
9. [x] **Saúde da bateria na partida** — queda de tensão ao dar partida (< 9,6 V = bateria no fim).
10. [ ] **Correção de combustível (fuel trims)** — STFT/LTFT altos e persistentes indicam entrada falsa de ar, sonda ou bico.
11. [ ] **Volume automático pela velocidade** — compensa o ruído da estrada.
12. [ ] **Rotina de partida** — retoma o Spotify e sugere Trabalho de manhã / Casa no fim do dia.
13. [ ] **"Onde parei o carro"** — salva o ponto de GPS ao desligar.
14. [ ] **Brilho da tela** — mais baixo à noite ou com farol ligado.
15. [ ] **Calibragem dos pneus (TPMS)** — sensores Bluetooth de tampinha de válvula (hardware extra).

## Navegação / mapa
- [ ] Decidir o mapa dentro do launcher: A) Google Maps embutido (Maps SDK, precisa de chave e Play Services),
  B) OpenStreetMap embutido, ou C) manter Waze/Maps em tela cheia com o painel de direção por cima.

## Honda Fit
- [ ] Modo leve / perfil do Fit — aguardando dados da multimídia do Fit (versão do Android, RAM, resolução).
