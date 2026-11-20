# Validação no carro — tudo junto

Roteiro para testar de uma vez as funções novas. Marque cada item. Onde der, o "em casa" não precisa do carro.
Antes de tudo: instale a versão mais nova (a multimídia atualiza sozinha; confira a versão no topo da tela inicial).

## 0. Antes de sair (em casa, ou com a ignição ligada na garagem)
- [ ] **Config. → Status do app**: abrir e conferir os itens. Corrigir os vermelhos com o botão ao lado. Deixe esta tela aberta com o motor ligado para ver "Nível do tanque", "Consumo (fluxo de ar)" e "Monitores de emissões" aparecerem.
- [ ] **Telegram**: item "Telegram" verde. Se não, vincule em "Avisos no celular" e envie o teste.
- [ ] **Demonstração do OBD**: iniciar e parar. Chegam no Telegram o fim da viagem e o estacionamento, marcados 🧪 SIMULAÇÃO.
- [ ] **Config. → Segurança**: "Testar alerta falado" (ouvir a voz). "Testar possível colisão (10 s)": a contagem aparece e, sem tocar, chega o aviso 🧪 com mapa; repetir tocando "Estou bem" (cancela).
- [ ] **Modo leve** (Config. → Desempenho): ligar e navegar; as setas e alertas ficam parados.
- [ ] **Clima na home**: temperatura, "atualizado HH:mm" e a linha de chuva.

### Comandos do bot do Telegram (podem ser testados em casa)
O app liga os comandos sozinho ao abrir, com o Telegram já vinculado (uma vez por dia). Abra o app uma vez antes de testar.
- [ ] No Telegram, abra a conversa com o bot e toque no botão de menu "/": devem aparecer os comandos.
- [ ] `/ajuda`: lista os comandos.
- [ ] `/status`: online/offline, motor, bateria, combustível, falhas, manutenção e último uso.
- [ ] `/onde`: com o carro parado, mostra onde parou (endereço, link e pino do mapa). Com o motor ligado e andando, mostra a posição ao vivo.
- [ ] `/bateria`: tensão agora, última partida e queda por hora parado.
- [ ] `/consumo`: consumo médio, custo por km, gasto no mês e último abastecimento (aguardando valor, se for o caso).
- [ ] `/manutencao`: itens vencidos e próximos.
- [ ] De outro chat (ou um amigo) mande `/status` para o bot: ele deve recusar ("este chat ainda não está vinculado").
- [ ] Vincular de novo: com o webhook ligado, o vínculo se faz mandando o código CIVIC-XXXXX ao bot (o app não precisa consultar). Confira em Config. → Avisos no celular que aparece "Vinculado".

## 1. Com o motor ligado, parado (garagem)
- [ ] **Status do app**: "Nível do tanque" informa ou não (se não informar, o abastecimento automático não funciona; lance à mão).
- [ ] **Monitores de emissões** (Status e OBD → Falhas): quantos prontos e quais faltam.
- [ ] **OBD → Falhas → Códigos apagados**: mostra km desde o apagamento (PID 0131); anote o valor.
- [ ] **Luzes-espia pelo CAN** (Config.): capturar uma luz apagada e acesa (ex.: farol). Se vier "Nenhum quadro CAN", o carro não passa esses sinais: pode descontinuar.
- [ ] **Temperatura do câmbio**: em OBD → Falhas, preencher cabeçalho/comando do PID do câmbio (se descobrir) e "Salvar e testar". Se "sem resposta", anotar o texto recebido.
- [ ] **Modo 06**: "Ler modo 06": deve listar testes (MID). Se "não respondeu", o carro não suporta.

## 2. Dirigindo (percurso de 20–30 min, com parada no fim)
- [ ] **Termostato**: se o motor partiu frio (até 45 °C), ver em OBD → Falhas → Termostato o tempo até 80 °C.
- [ ] **Catalisador**: com o motor quente, em velocidade constante (50–80 km/h), tocar "Testar catalisador (45 s)". Ler o parecer.
- [ ] **Marcha lenta**: parar no semáforo/garagem com o motor quente por 30 s; ver "Marcha lenta" (estável ou irregular).
- [ ] **Frenagem/aceleração bruscas**: uma frenagem firme e uma arrancada forte (com segurança). No fim, o resumo da viagem no Telegram traz "condução NN/100 (X frenagens e Y acelerações bruscas)".
- [ ] **Alertas falados**: quando aparecer um alerta (ex.: motor frio acima de 3.000 rpm não fala por ser dica; superaquecimento e falha falam), ouvir a voz.
- [ ] **Painel de direção** (sobre o mapa): confira que os alertas e a nota seguem aparecendo.
- [ ] **Posição e trajeto**: no fim, o Casa Tech mostra o trajeto (pontos a cada 15 s) e o status online.

## 3. Ao desligar o carro
- [ ] **Estacionamento**: chega no Telegram "🅿️ Carro estacionado em …" com mapa, em segundos.
- [ ] **Arrastar o app** da lista de recentes com o carro ligado: o aviso de estacionamento também chega.
- [ ] Na **próxima partida**: o Casa Tech recebe o resumo do último uso (estado, bateria, saúde do motor).

## 4. Abastecimento (no próximo abastecimento)
- [ ] Encher o tanque. Ao ligar o carro, chega "⛽ Abastecimento detectado" (se o carro informa o nível). Informar o valor pago ou o preço por litro em Veículo → Abastecimentos (lápis) e marcar aditivada se for o caso. Conferir o consumo (km/l) no próximo.

## 5. Dias depois
- [ ] **Dreno de bateria**: depois de uma noite parado, OBD → Falhas → Dreno de bateria mostra o registro (tensão na partida e queda por hora).
- [ ] **Uso incomum**: depois de 12+ partidas, ligar o motor em horário em que nunca usa (ou ligar o "Vigia total" e dar a partida): chega o aviso com a localização.
- [ ] **Fadiga**: em viagem longa (2 h sem pausa de 15 min), faixa + voz + Telegram.
- [ ] **Colisão real**: não testar de verdade. Só o botão de teste vale.

## Onde conferir o que não aparece na tela
- **Casa Tech / Supabase**: tabelas e views em `docs/DASHBOARD-CASA-TECH.md`. O veículo simulado ("Civic (simulado)") serve para validar o dash antes dos dados reais.
- **O que pode não funcionar por limitação do carro** (e então vira "descontinuar"): nível do tanque, luzes pelo CAN, modo 06, PID do câmbio, sondas de oxigênio. O Status do app e as mensagens de cada teste dizem qual foi.
