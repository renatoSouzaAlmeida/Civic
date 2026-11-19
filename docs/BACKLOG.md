# Backlog — Civic Renatin

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

## Abastecimento automático
- [x] **Pelo nível do tanque** — pronto, falta validar no carro: o app cadastra o abastecimento quando o nível sobe 10 pontos ou mais
  (gasolina, tanque cheio, litros pela capacidade do tanque em Config.), avisa no Telegram, e o valor pago (total OU preço por litro)
  e a opção aditivada são informados depois no histórico. Depende de o Civic informar o nível pelo OBD (PID 012F); se não informar,
  o lançamento continua manual.

## Integração com a Casa Tech (painel fora do carro)
- [ ] **Dashboard do veículo dentro da Casa Tech** — uma tela dedicada, isolada do resto da Casa Tech, para consultar
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

## Navegação / mapa
- [ ] **Adiado — só fazer se realmente impactar o uso.** Waze fora de cogitação para embutir (não há SDK).
  Caminho recomendado, se um dia valer: navegar no Google Maps em tela cheia com o painel de direção por cima
  (já existe) + aba "Mapa" no launcher com o Maps SDK (posição, trânsito, vagas do histórico). Exige Google Play
  Services na multimídia e chave de API. O Navigation SDK (rota embutida) exige contrato com o Google: descartado.

## Honda Fit
- [x] Modo leve / perfil do Fit — chave "Desempenho" em Config.: sem animações contínuas nem vibração ao tocar.
  (Fit: Android 12, 1280x720, 4 GB de RAM, 8 núcleos; o launcher já rodava bem, o modo leve é reserva para aparelhos mais simples.)
