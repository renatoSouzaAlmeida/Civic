# Backlog — Civic Renatin

Itens combinados e ainda não feitos. Ao concluir um item, remova daqui (ou marque como feito).

## OBD
- [ ] **Tela de falhas (DTC) no OBD** — listar os códigos de falha lidos pelo adaptador (modo 03),
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

## Navegação / mapa
- [ ] Decidir o mapa dentro do launcher: A) Google Maps embutido (Maps SDK, precisa de chave e Play Services),
  B) OpenStreetMap embutido, ou C) manter Waze/Maps em tela cheia com o painel de direção por cima.

## Honda Fit
- [ ] Modo leve / perfil do Fit — aguardando dados da multimídia do Fit (versão do Android, RAM, resolução).
