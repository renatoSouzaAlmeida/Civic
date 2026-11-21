# Backlog — Civic Renatin

> **Marco 1.0.0 atingido em 21/11/2026.** Regra original: depois da escolha do layout da tela do OBD e quando todos os itens abaixo estiverem concluídos, a versão do app sobe para 1.0.0 (ver `versao.properties` e CLAUDE.md). Nenhuma parte da versão passa de 99.

Itens combinados e ainda não feitos. Ao concluir um item, remova daqui (ou marque como feito).

## OBD
- [x] **Tela de falhas (DTC) no OBD** — listar os códigos de falha lidos pelo adaptador (modo 03),
  com descrição em português, luz de injeção (MIL) acesa ou não e opção de apagar os códigos (modo 04),
  com confirmação. Acessível a partir da tela do OBD.

- [x] **Combustível** (pronto, falta validar no carro) — lê os PIDs suportados (0100/0120/0140); nível do tanque
  pelo PID 012F se o carro informar; consumo médio e instantâneo pela vazão de ar (MAF 0110) ou, sem MAF, estimado
  pelo MAP (010B) + temperatura do ar + rpm. Estimativa com gasolina brasileira (~13,2:1); etanol puro e aceleração
  forte podem desviar. Se o Civic/Fit não informar esses PIDs, os campos ficam em "—" e o item pode ser descontinuado.

- [x] **Luzes-espia reais pelo CAN** (pronto, experimental) — Config. > "Luzes-espia pelo CAN": captura guiada com o
  modo monitor do ELM327 (ATMA), com a luz apagada e acesa, mostra os bits que mudaram e grava a regra por luz.
  Depois o app lê essas regras sozinho. Se a tomada OBD do carro não deixar passar os quadros da carroceria,
  a captura volta vazia e o recurso pode ser descontinuado.

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
8. [x] **Notificações no celular + Supabase** — sincronizar abastecimentos, manutenção e alertas numa base Supabase e
   avisar no celular (manutenção vencendo, alertas do motor, falhas DTC).
9. [x] **Saúde da bateria na partida** — queda de tensão ao dar partida (< 9,6 V = bateria no fim).
10. [x] **Correção de combustível (fuel trims)** — STFT/LTFT altos e persistentes indicam entrada falsa de ar, sonda ou bico.
11. [x] **Volume automático pela velocidade** — compensa o ruído da estrada.
12. [x] **Rotina diária** — sugestões de destino por período, local e dias da semana (Config.), com faixa "Ir agora".
    Também retoma o Spotify de onde parou quando o motor liga (Config. > Música ao ligar o carro).
13. [x] **"Onde parei o carro"** — salva o ponto de GPS ao desligar.

## Propostas de engenharia (por área)
Decididas em conversa. O bloqueio de telas em movimento foi retirado da lista por decisão do dono.

### Segurança e direção (primeira leva — feitas, faltam validar no carro)
- [x] **Alertas falados** — voz em português para alertas de aviso e críticos (superaquecimento, alternador, falha, fadiga). Liga/desliga em Config. > Segurança.
- [x] **Fadiga** — 2 h sem pausa de 15 min, ou dirigindo de madrugada (0h–5h por 30 min): faixa + voz + Telegram.
- [x] **Frenagem e aceleração bruscas** — contadas pela variação da velocidade do OBD; nota de condução 0–100 no resumo da viagem (Telegram).
- [x] **Possível colisão** — queda de 30 km/h ou mais em até 3 s (partindo de 35+) seguida do motor apagando: contagem de 30 s com "Estou bem"; sem resposta, manda aviso e localização ao Telegram. Botão de teste (10 s) em Config.
- [x] **Movimento suspeito** — partida do motor em horário em que o carro nunca é usado (aprende com as últimas 60 partidas) e "Vigia total" opcional (toda partida avisa).

### OBD (engenharia do motor) — feitos, faltam validar no carro (ver docs/VALIDACAO.md)
- [x] **Monitores de prontidão** (modo 01, PID 01) — mostra quantos monitores de emissões estão prontos e quais faltam (aparece em Config. > Status do app). O aviso de "códigos apagados" também foi feito (PID 0131).
- [x] **Freeze frame** — pronto, falta validar no carro: guardado automaticamente quando surge um código novo (modo 02), com aviso no Telegram; lista em OBD > Falhas.
- [x] **Saúde do termostato** — pronto, falta validar no carro: mede sozinho o tempo até 80 °C em partidas a frio (até 45 °C); avisa se demorar mais de 1,6x o normal ou não chegar a 80 °C em 25 min.
- [x] **Sondas lambda e catalisador** — pronto, falta validar no carro: botão "Testar catalisador (45 s)" em OBD > Falhas; compara a oscilação da sonda de antes e de depois (usa 0114 ou 0124 e 0115).
- [x] **Dreno de bateria** — pronto, falta validar no carro: compara a tensão ao desligar (1 h depois) com a da próxima partida; avisa se a queda passar de 20 mV/h ou se estiver abaixo de 12,2 V depois de 6 h parado.
- [x] **Marcha lenta irregular** — pronto, falta validar no carro: mede sozinha com o carro parado e quente (desvio acima de 45 rpm ou faixa acima de 180 rpm, 3 vezes seguidas avisa). Modo 06 em botão, com resultado bruto por teste.
- [x] **Temperatura do fluido do câmbio** — pronto, falta validar no carro: PID personalizado (cabeçalho, comando, bytes, multiplicador e deslocamento) em OBD > Falhas; alerta a partir de 110 °C. Depende de descobrir o PID certo do Civic.

### Integrações e notificações
- [x] **Comandos no bot do Telegram** — pronto, falta validar: /onde, /status, /bateria, /consumo, /manutencao e /ajuda, respondendo com dados do Supabase (função civic-bot). Só responde a chats vinculados.
- [x] **Resumo semanal no Telegram** — pronto, falta validar: automático aos domingos às 19h (pg_cron) e sob demanda com /resumo.
- [x] **Chuva onde o carro está parado** — pronto, falta validar: checagem a cada 20 min na nuvem (pg_cron + Open-Meteo); um aviso por vaga.
- [x] **Preço da ANP** — pronto, falta validar: planilha semanal da ANP lida todo dia às 11h (nuvem); aparece em Veículo > "Etanol ou gasolina?".

### Interface e usabilidade
- [x] **Tela "Status do app"** — em Config., no topo: OBD, nível do tanque e MAF suportados, monitores de emissões, GPS, localização o tempo todo, nuvem, Telegram, Vigia, painel, Spotify, Bluetooth e voz, com botão para corrigir cada permissão.
- [x] **Modo HUD** — pronto, falta validar: velocímetro da barra do topo ou Config. > Segurança; espelhado por padrão.
- [x] **Comando de voz curto** — pronto, falta validar: microfone da barra do topo; depende de reconhecimento de voz no aparelho.

## Layout da tela do OBD (decidido)
- [x] **Decidido: todos os layouts ficam** (Esportivo, Cockpit e Modular), escolhidos pelo botão "Layout". Proposta original: **Comparar 3 propostas novas com a atual** — OBD > botão "Layout": Esportivo (o atual), Cockpit e Modular (só o corpo das imagens; sem cabeçalho nem menu). Órbitas foi descartado.
  Testar com a demonstração do OBD e escolher quais ficam; as demais serão removidas. A versão segue 0.x até essa decisão.

## Layout da Tela Início (decidido)
- [x] **Decidido: todos os layouts ficam** (Padrão, Caixas e Painel), escolhidos pela pílula "Início". Proposta original: **Comparar 3 propostas** — pílula "Início: …" no alto da Tela Início: Padrão (a atual), Caixas (clara, caixas separadas e mapa menor)
  e Painel (proposta escura, segue o tema). Escolher qual fica; as demais e a pílula serão removidas.

## Abastecimento automático
- [x] **Pelo nível do tanque** — pronto, falta validar no carro: o app cadastra o abastecimento quando o nível sobe 10 pontos ou mais
  (gasolina, tanque cheio, litros pela capacidade do tanque em Config.), avisa no Telegram, e o valor pago (total OU preço por litro)
  e a opção aditivada são informados depois no histórico. Depende de o Civic informar o nível pelo OBD (PID 012F); se não informar,
  o lançamento continua manual.

## Integração com a Casa Tech (painel fora do carro)
- [x] **Dashboard do veículo dentro da Casa Tech** (pronto, montado na Casa Tech) — uma tela dedicada, isolada do resto da Casa Tech, para consultar
  de fora do carro tudo que o launcher captura. Acesso próprio (login/senha ou autorização separada da conta
  principal) e **somente leitura**, sem acesso aos demais módulos da Casa Tech.
  - Dados a mostrar: dados do veículo (nome, modelo, ano, placa, odômetro do app); manutenção (plano, vencidos e
    próximos, histórico); alertas gerados (motor, bateria, falhas DTC); pontos onde o carro ficou estacionado
    (mapa e histórico, hoje só o último fica guardado); abastecimentos e consumo; viagens (computador de bordo);
    saúde da bateria e correção de combustível.
  - Possível caminho: o launcher já sincroniza com o Supabase (`supabase/`); a Casa Tech lê dessa base por uma
    função/API própria com chave de leitura restrita (RLS), sem expor as tabelas diretamente.
  - **Decidido:** autenticação da Casa Tech via OAuth do Supabase (login dedicado, só leitura, políticas RLS por usuário).
  - **Decidido:** guardar na nuvem o **histórico completo de estacionamentos** (hoje só o último fica no aparelho):
    data, posição, endereço, idade do sinal de GPS.
  - **Decidido:** capturar e salvar **todo indicador possível** — quanto mais dados, melhor. Além do que já existe:
    posição ao vivo e trajeto das viagens, status online/última comunicação do carro, tensão da bateria ao longo do
    tempo, temperatura, rpm, velocidade, fuel trims, falhas DTC com histórico, eventos de ignição (ligou/desligou),
    consumo, versão do app. Respeitar tamanho do app e uso de dados (envio em lote, só com internet).
  - **Decidido — como enviar (pouco dado, sem lote):**
    - Tudo é gravado **localmente** no aparelho durante o uso (sem depender de internet).
    - Ao **ligar o carro**, o app envia ao Supabase só o **último estado salvo** (resumo da viagem anterior,
      indicadores finais, status), um envio pequeno e único, sem fila em lote.
    - **Estacionamento:** a função da nuvem que já manda o Telegram (`civic-notificar`) também **grava a linha no
      histórico de estacionamentos** na mesma chamada. Um envio só serve aos dois; o app não precisa mandar duas vezes.
    - Se estiver sem internet na hora, o dado fica guardado e sai no próximo envio.
  - **Feito:** envio do último estado ao ligar o carro (tabela `civic_estados`: motor, bateria, combustível, falhas, viagem e
    última posição). Também feito: posição ao vivo e trajeto (`civic_posicoes`), status online (`civic_status_atual`) e eventos de ignição (`civic_ignicao`),
    com dados simulados para validar o dash.
  - A definir: retenção do histórico e o desenho das telas do dashboard.

## Honda Fit
- [x] Modo leve / perfil do Fit — chave "Desempenho" em Config.: sem animações contínuas nem vibração ao tocar.
  (Fit: Android 12, 1280x720, 4 GB de RAM, 8 núcleos; o launcher já rodava bem, o modo leve é reserva para aparelhos mais simples.)

## Novas propostas (pós-1.0.0, a implementar) — detalhes em `docs/RECURSOS.md`
- [x] **Driving Score completo** (feito em 21/11/2026; falta validar no carro e montar a tela no dash) — cartão "Resumo da condução" (anel com a nota, selo, km, minutos, frenagens, acelerações, RPM elevado, consumo médio), nota guardada por viagem, comparação entre viagens e envio à Casa Tech.
- [ ] **Geofence** — áreas permitidas (raio em torno de Casa, Trabalho ou ponto escolhido) com aviso ao sair e ao entrar; funciona por GPS (vale para o Fit).
- [ ] **Alerta de excesso de velocidade** — a contagem por viagem e o limite já existem (21/11/2026); falta o aviso na hora (voz + faixa + Telegram).
- [ ] **Alerta de desconexão do OBD** — hoje o app detecta a queda mas não avisa; falta faixa, voz, Telegram em movimento e reconexão visível.
- [ ] **Botão SOS** — localização e dados do veículo para vários contatos cadastrados, com confirmação de 5 s.
- [ ] **Aviso de local desconhecido** — aprender as vagas de costume e avisar quando o carro parar ou andar fora delas (GPS).
