# Validação no carro — tudo junto

Roteiro para testar de uma vez as funções novas. Marque cada item. Onde der, o "em casa" não precisa do carro.
Antes de tudo: instale a versão mais nova (a multimídia atualiza sozinha; confira a versão no topo da tela inicial).

## Itens novos desta versão (podem ser testados em casa)
- [ ] **Clima na home pela localização atual:** o cartão mostra a sua cidade (ex.: Barueri) e não Brasília. O app usa GPS, depois a posição pela rede e, sem nada disso, o IP da internet (aparece "(aprox.)"). Se mostrar outra cidade, me diga.
- [ ] **`/onde` do bot:** mostra a posição mais recente (GPS ou pela rede) e a última vaga, a que for mais nova. Deve trazer o dia de hoje se o app estiver aberto.
- [ ] **Spotify:** o cartão tem a cor do cartão do veículo com um degradê azul claro.
- [ ] **Resumo semanal:** mande `/resumo` ao bot. Chega km, usos, consumo, gasto, alertas, manutenção e lugares frequentes. O automático sai aos domingos, às 19h.
- [ ] **Preço da ANP:** Veículo → Abastecimentos → "Etanol ou gasolina?" mostra "ANP · Barueri: gasolina R$ … · aditivada … · etanol …" e o botão "Usar os preços da ANP" preenche os campos. Confira com o levantamento da ANP.
- [ ] **Modo HUD:** Config. → Segurança → "Abrir modo HUD". Velocidade enorme em fundo preto. Toque sai; segure alterna o espelhamento (ligado por padrão, para refletir no para-brisa).
- [ ] **Comando de voz:** toque no microfone da barra do topo (aceite a permissão). Diga "onde parei", "quanto de combustível", "ir para casa", "bateria", "temperatura", "consumo" ou "que horas são". O app responde falando. Se não houver reconhecimento de voz, o Status do app avisa.
- [ ] **Chuva onde o carro está parado:** depois de estacionar, se a previsão indicar chuva nas próximas 3 horas (60% ou mais), chega "🌧️ Deve chover onde seu carro está parado…". Um aviso por vaga, checado a cada 20 min. Só dá para ver em dia de chuva.

## 0. Antes de sair (em casa, ou com a ignição ligada na garagem)
- [ ] **Config. → Status do app**: abrir e conferir os itens. Corrigir os vermelhos com o botão ao lado. Deixe esta tela aberta com o motor ligado para ver "Nível do tanque", "Consumo (fluxo de ar)" e "Monitores de emissões" aparecerem.
- [ ] **Telegram**: item "Telegram" verde. Se não, vincule em "Avisos no celular" e envie o teste.
- [ ] **Demonstração do OBD**: iniciar, deixar rodar 1–2 min e parar. Chegam no Telegram o fim da viagem e o estacionamento, marcados 🧪 SIMULAÇÃO. A viagem aparece em Veículo → Viagem (histórico), a vaga em "Onde parei o carro", e na Casa Tech entram a vaga (origem "simulado"), o resumo e o liga/desliga do motor.
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

## 2. Dirigindo (percurso de 20–30 min) — passo a passo
Não mexa no tablet dirigindo: peça a um passageiro ou faça nas paradas. Antes de sair, confira em Config. → Status do app que o OBD está verde.
1. **Saída (motor frio, de preferência de manhã):** ligue o carro e saia. Nada a fazer: o app mede sozinho o **termostato** (tempo até 80 °C). Para ver depois: OBD → Falhas → "Termostato".
2. **Primeiros 10 min:** dirija normalmente, com uma **frenagem firme** e uma **arrancada forte** (com segurança, via livre). Isso alimenta a nota de condução.
3. **Depois de uns 15 min (motor quente, 80 °C ou mais):** pare em local seguro com o motor ligado, abra OBD → Falhas e toque em **"Testar catalisador (45 s)"**. Pode ficar parado em marcha lenta; dá um resultado, mas é mais confiável com um passageiro tocando o botão em **velocidade constante de 50–80 km/h** (via livre, sem frear). Aguarde o parecer na própria tela.
4. **Parado 30 s com o motor quente** (semáforo ou garagem): em OBD → Falhas → "Marcha lenta" aparece "Estável" ou "Irregular" depois de uns 30 s.
5. **Ouça os alertas falados:** em Config. → Segurança → "Testar alerta falado". Os reais (superaquecimento, falha) só falam se acontecerem.
6. **Direção com mapa:** toque em "Iniciar direção" na home e veja o painel de direção sobre o Maps. Confira que alertas e velocidade aparecem.
7. **Ao chegar:** desligue o carro. Em até 5 min chega no Telegram o resumo da viagem, com "condução NN/100 (X frenagens e Y acelerações bruscas)".
8. **No dia seguinte:** abra o Casa Tech e confira o trajeto da viagem (pontos a cada 15 s) e o status online.

## 3. Ao desligar o carro
- [ ] **Estacionamento**: chega no Telegram "🅿️ Carro estacionado em …" com mapa, em segundos.
- [ ] **Arrastar o app** da lista de recentes com o carro ligado: o aviso de estacionamento também chega.
- [ ] Na **próxima partida**: o Casa Tech recebe o resumo do último uso (estado, bateria, saúde do motor).

## 4. Abastecimento
**Teste sem ir ao posto:** Veículo → Abastecimentos → "Simular abastecimento detectado (teste)". Aparece no histórico um item "detectado pelo tanque (15% → 95%)" e chega no Telegram "🧪 TESTE ⛽ Abastecimento detectado…". Toque no lápis, informe o valor total OU o preço por litro, marque aditivada se quiser e salve. **Depois apague o item** (lixeira), pois ele conta no consumo e no Casa Tech.

**Teste real (no próximo abastecimento):**
1. Chegue ao posto com o tanque baixo (reserva), com o app aberto e o OBD conectado.
2. **Desligue o carro** e abasteça até o desarme da bomba (tanque cheio).
3. **Ligue o carro** e deixe o tablet ligado. Em cerca de 1 a 2 minutos chega no Telegram "⛽ Abastecimento detectado: ~NN l (tanque X% → Y%)". O app precisa de 5 leituras do nível (uma a cada ~8 s).
4. Informe o valor pago no histórico (lápis).
5. **Se não chegar nada em 5 min:** abra Config. → Status do app e veja "Nível do tanque". Se disser que o carro não informa o nível, a detecção automática não funciona neste carro: lance à mão em "Novo abastecimento".
6. No abastecimento seguinte, confira o consumo (km/l) e o custo por km no histórico e no `/consumo` do bot.

## 5. Dias depois
- [ ] **Dreno de bateria**: depois de uma noite parado, OBD → Falhas → Dreno de bateria mostra o registro (tensão na partida e queda por hora).
- [ ] **Uso incomum**: depois de 12+ partidas, ligar o motor em horário em que nunca usa (ou ligar o "Vigia total" e dar a partida): chega o aviso com a localização.
- [ ] **Fadiga**: em viagem longa (2 h sem pausa de 15 min), faixa + voz + Telegram.
- [ ] **Colisão real**: não testar de verdade. Só o botão de teste vale.

## Onde conferir o que não aparece na tela
- **Casa Tech / Supabase**: tabelas e views em `docs/DASHBOARD-CASA-TECH.md`. O veículo simulado ("Civic (simulado)") serve para validar o dash antes dos dados reais.
- **O que pode não funcionar por limitação do carro** (e então vira "descontinuar"): nível do tanque, luzes pelo CAN, modo 06, PID do câmbio, sondas de oxigênio. O Status do app e as mensagens de cada teste dizem qual foi.
