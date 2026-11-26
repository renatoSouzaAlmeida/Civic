# Backlog — Civic Renatin

> **Marco 1.0.0 atingido em 21/11/2026.** Regra original: depois da escolha do layout da tela do OBD e quando todos os itens abaixo estiverem concluídos, a versão do app sobe para 1.0.0 (ver `versao.properties` e CLAUDE.md). Nenhuma parte da versão passa de 99.

Itens combinados e ainda não feitos. Ao concluir um item, remova daqui (ou marque como feito).

## OBD
- [x] **Tela de falhas (DTC) no OBD** — listar os códigos de falha lidos pelo adaptador (modo 03),
  com descrição em português, luz de injeção (MIL) acesa ou não e opção de apagar os códigos (modo 04),
  com confirmação. Acessível a partir da tela do OBD.

- [x] **Combustível** (validado no carro em 21/11/2026) — lê os PIDs suportados (0100/0120/0140); nível do tanque
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

### Segurança e direção (primeira leva — validada no carro em 21/11/2026)
- [x] **Alertas falados** — voz em português para alertas de aviso e críticos (superaquecimento, alternador, falha, fadiga). Liga/desliga em Config. > Segurança.
- [x] **Fadiga** — 2 h sem pausa de 15 min, ou dirigindo de madrugada (0h–5h por 30 min): faixa + voz + Telegram.
- [x] **Frenagem e aceleração bruscas** — contadas pela variação da velocidade do OBD; nota de condução 0–100 no resumo da viagem (Telegram).
- [x] **Possível colisão** — queda de 30 km/h ou mais em até 3 s (partindo de 35+) seguida do motor apagando: contagem de 30 s com "Estou bem"; sem resposta, manda aviso e localização ao Telegram. Botão de teste (10 s) em Config.
- [x] **Movimento suspeito** — partida do motor em horário em que o carro nunca é usado (aprende com as últimas 60 partidas) e "Vigia total" opcional (toda partida avisa).

### OBD (engenharia do motor) — validado no carro em 21/11/2026 (ver docs/VALIDACAO.md)
- [x] **Monitores de prontidão** (modo 01, PID 01) — mostra quantos monitores de emissões estão prontos e quais faltam (aparece em Config. > Status do app). O aviso de "códigos apagados" também foi feito (PID 0131).
- [x] **Freeze frame** — validado no carro em 21/11/2026: guardado automaticamente quando surge um código novo (modo 02), com aviso no Telegram; lista em OBD > Falhas.
- [x] **Saúde do termostato** — validado no carro em 21/11/2026: mede sozinho o tempo até 80 °C em partidas a frio (até 45 °C); avisa se demorar mais de 1,6x o normal ou não chegar a 80 °C em 25 min.
- [x] **Sondas lambda e catalisador** — validado no carro em 21/11/2026: botão "Testar catalisador (45 s)" em OBD > Falhas; compara a oscilação da sonda de antes e de depois (usa 0114 ou 0124 e 0115).
- [x] **Dreno de bateria** — validado no carro em 21/11/2026: compara a tensão ao desligar (1 h depois) com a da próxima partida; avisa se a queda passar de 20 mV/h ou se estiver abaixo de 12,2 V depois de 6 h parado.
- [x] **Marcha lenta irregular** — validado no carro em 21/11/2026: mede sozinha com o carro parado e quente (desvio acima de 45 rpm ou faixa acima de 180 rpm, 3 vezes seguidas avisa). Modo 06 em botão, com resultado bruto por teste.
- [x] **Temperatura do fluido do câmbio** — validado no carro em 21/11/2026: PID personalizado (cabeçalho, comando, bytes, multiplicador e deslocamento) em OBD > Falhas; alerta a partir de 110 °C. Depende de descobrir o PID certo do Civic.

### Integrações e notificações
- [x] **Comandos no bot do Telegram** — validado no carro em 21/11/2026: /onde, /status, /bateria, /consumo, /manutencao e /ajuda, respondendo com dados do Supabase (função civic-bot). Só responde a chats vinculados.
- [x] **Resumo semanal no Telegram** — validado no carro em 21/11/2026: automático aos domingos às 19h (pg_cron) e sob demanda com /resumo.
- [x] **Chuva onde o carro está parado** — validado no carro em 21/11/2026: checagem a cada 20 min na nuvem (pg_cron + Open-Meteo); um aviso por vaga.
- [x] **Preço da ANP** — validado no carro em 21/11/2026: planilha semanal da ANP lida todo dia às 11h (nuvem); aparece em Veículo > "Etanol ou gasolina?".

### Interface e usabilidade
- [x] **Tela "Status do app"** — em Config., no topo: OBD, nível do tanque e MAF suportados, monitores de emissões, GPS, localização o tempo todo, nuvem, Telegram, Vigia, painel, Spotify, Bluetooth e voz, com botão para corrigir cada permissão.
- [x] **Modo HUD** — validado no carro em 21/11/2026: velocímetro da barra do topo ou Config. > Segurança; espelhado por padrão.
- [x] **Comando de voz curto** — validado no carro em 21/11/2026: microfone da barra do topo; depende de reconhecimento de voz no aparelho.

## Layout da tela do OBD (decidido)
- [x] **Decidido: todos os layouts ficam** (Esportivo, Cockpit e Modular), escolhidos pelo botão "Layout". Proposta original: **Comparar 3 propostas novas com a atual** — OBD > botão "Layout": Esportivo (o atual), Cockpit e Modular (só o corpo das imagens; sem cabeçalho nem menu). Órbitas foi descartado.
  Testar com a demonstração do OBD e escolher quais ficam; as demais serão removidas. A versão segue 0.x até essa decisão.

## Layout da Tela Início (decidido)
- [x] **Decidido: todos os layouts ficam** (Padrão, Caixas e Painel), escolhidos pela pílula "Início". Proposta original: **Comparar 3 propostas** — pílula "Início: …" no alto da Tela Início: Padrão (a atual), Caixas (clara, caixas separadas e mapa menor)
  e Painel (proposta escura, segue o tema). Escolher qual fica; as demais e a pílula serão removidas.

## Abastecimento automático
- [x] **Pelo nível do tanque** — validado no carro em 21/11/2026: o app cadastra o abastecimento quando o nível sobe 10 pontos ou mais
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
  - **Decidido (21/11/2026): o histórico fica permanente no banco; nada é apagado.** (Trajeto ponto a ponto ≈ 30 MB por ano com uso diário.)
  - A definir: o desenho das telas do dashboard (aba Viagens ainda a montar na Casa Tech).

## Honda Fit
- [x] Modo leve / perfil do Fit — chave "Desempenho" em Config.: sem animações contínuas nem vibração ao tocar.
  (Fit: Android 12, 1280x720, 4 GB de RAM, 8 núcleos; o launcher já rodava bem, o modo leve é reserva para aparelhos mais simples.)

> **Validação (21/11/2026):** o dono confirmou que tudo que estava marcado como "falta validar no carro" (inclusive condução por GPS no Fit, Driving Score, excesso de velocidade e aviso de OBD sem resposta) foi validado.

## Correções de 21/11/2026 (relato do Fit: aviso de estacionamento não chegou)
- **Início automático (Fit/CarPlay):** o Vigia (GPS, estacionamento, telemetria) só iniciava ao abrir a tela do launcher; no Fit o CarPlay abre direto e nada era monitorado. Agora um `BootReceiver` liga o Vigia ao ligar o aparelho e depois de atualizar o app, e o Vigia também inicia o OBD.
- **Fila de avisos sem internet:** avisos (estacionamento, alertas, SOS) que falham por falta de internet ficam guardados e são reenviados (a cada 30 s), com a observação "enviado com atraso".
- **Banco (RLS):** gravações de `civic_ignicao`, `civic_posicoes`, `civic_estados`, `civic_viagens` e `civic_areas` voltavam 401 (faltava a regra de leitura das próprias linhas); por isso a Casa Tech só tinha dados simulados. Corrigido; os dados guardados nos aparelhos sobem na próxima conexão.
- **Atualização (o dono informou que o launcher abre primeiro e depois o CarPlay):** a causa mais provável é o Android cortar o GPS do app em segundo plano. Correções: serviço do Vigia declarado como tipo *location*; aviso no app pedindo a permissão "localização o tempo todo"; lotes de ignição/viagens com os mesmos campos (o banco recusava com erro 400).
- **Identificação dos aparelhos (21/11/2026):** cada aparelho envia modelo, versão do Android e nome do carro a cada sinal de vida (`civic_status`, views `civic_veiculos` e `civic_status_atual`); Config. > Status do app mostra "Este aparelho" com o ID de 8 letras, o mesmo que aparece na Casa Tech.
- **Pendente de apuração:** o Fit (versão 1.1.4, segundo o dono) não aparece no banco com versão 1.1.x; verificar se tem internet própria. A junção dos aparelhos duplicados no banco (script de limpeza) aguarda confirmação.

## Modo Direção em tela dividida (pedido do dono em 21/11/2026; a multimídia do Civic tem chip e aceita tela dividida)
- [~] **Mapa de um lado e cockpit do outro** — *feito, experimental, a validar na multimídia do Civic:* Config. > Painel de direção > Modo Direção: *Painel flutuante* (padrão, como sempre) ou *Tela dividida*. Em tela dividida, Direção, o cartão do mapa, Casa/Trabalho e a rotina abrem o **cockpit** (velocímetro do OBD Modular com setas, farol e RPM, leituras de motor/bateria/tanque/consumo e Spotify) e o mapa **ao lado** (`FLAG_ACTIVITY_LAUNCH_ADJACENT`). Para dividir sozinho existe um serviço de **Acessibilidade opcional** que só pede ao Android "alternar tela dividida" (não lê a tela); sem ele, o dono divide pelo botão da multimídia e toca em Direção.
  **Validado em 21/11/2026 (fotos do dono): a divisão funciona, mapa à esquerda e launcher à direita.** Ajustes feitos na 1.1.6: velocímetro grande com RPM em faixa fina no cockpit; tela do OBD compacta para meia tela (os três layouts quebravam).
  A validar: se a multimídia usa a tela dividida padrão do Android; de que lado fica cada painel; se o Waze e o Google Maps aceitam meia tela; sair da divisão (botão Fechar).

## Entrada de dados na Casa Tech (pedido do dono em 21/11/2026)
- [x] **CRUD completo da Casa Tech, por veículo** — *app e banco prontos (1.1.11), testados no banco como usuário da Casa Tech:* abastecimentos e valores de combustível, plano e **histórico de manutenção** (custo e oficina; lançar atualiza o plano), quilometragem, **despesas** (seguro, IPVA, multas...), **vencimentos e lembretes** com aviso diário no Telegram (renovam sozinhos), **áreas do geofence**, **contatos do SOS**, **configuração do veículo** (nome, modelo, placa, tanque, endereços, limite de velocidade, vigia, pressão ideal...), anotações em viagens, estacionamentos e eventos, e renomear/arquivar veículo. Sincronização de mão dupla; vale o editado por último; apagar = marcar removido. Views `civic_custos_mensal` e `civic_lembretes_proximos`. Catálogo completo em `docs/DASHBOARD-CASA-TECH.md`. **Falta a tela da central de lançamentos e o ditado na Casa Tech** (tarefa 9 do pedido único).

## Decisões do dono (21/11/2026)
- **Sem perfis de motorista:** o launcher se comporta igual para qualquer pessoa que o use.
- **A Casa Tech (painel do carro) é só do dono:** um único usuário tem acesso; por isso o vínculo automático de todos os aparelhos ao usuário do dashboard é adequado e não precisa de controle por pessoa.

## Decisões tomadas por padrão (21/11/2026, "execute o restante")
O dono pediu para executar o que faltava sem responder às perguntas; foram adotadas as recomendações abaixo (reversíveis; diga se quiser outra):
- **P-1 CI:** mudanças só em `docs/`, `supabase/` e `.md` **não geram versão** (feito em `build-apk.yml` e CLAUDE.md).
- **P-2 Limite de velocidade:** mantido o limite único configurável (Desligado/80/100/110/120). Perfis por via ficam para depois (exigem mapa com licença).
- **P-3 SOS:** Telegram vinculado + SMS aos contatos **se a multimídia tiver telefonia**; sem telefonia o app avisa e usa só o Telegram. Contatos por Telegram além do dono: colocar o bot num grupo com eles.
- **P-4 Geofence:** áreas livres (Casa, Trabalho ou nome próprio), raios 100/300/500/1000 m, **avisa saída e entrada**, Telegram e faixa ligáveis separadamente.
- **Retenção do histórico:** permanente (decidido pelo dono em 21/11/2026).
- **TPMS:** voltou a pedido do dono, **desligado por padrão** (ver "TPMS" abaixo).
- **P-5 Room/SQLite:** **não migrado**; reavaliar na 1.3.0 (caixa-preta).

## Roadmap pós-1.0.0 — do documento "Projeto Civic Renatin: Roadmap de Desenvolvimento" (21/11/2026)

Diretriz central: **plataforma automotiva independente do OBD** (o adaptador é uma fonte de dados; GPS, sensores Android e nuvem continuam úteis sem ele — caso do Fit com CarPlay).
Pilares: Condução · Segurança · Diagnóstico · Inteligência. Princípios: segurança e legibilidade na condução (sem interação complexa em movimento), funções essenciais locais e tolerantes à falta de internet,
**mostrar a origem e a confiança de cada dado** (OBD, GPS, sensor Android, nuvem), não apresentar estimativa como medição, projetar para várias resoluções e paisagem/retrato.
Observação: o documento foi escrito com base no inventário textual (RECURSOS.md de antes do Driving Score); o estado atual de cada item está marcado abaixo. Prioridade: P0 = essencial para a release; P1 = importante.

### Release 1.1.0 — Segurança (entregue em 21/11/2026; ver o que falta validar no carro em cada item)
- [x] **CR-101 (P0) Geofence: cadastro e persistência (validado pelo dono em 21/11/2026)** *(feito; criar na posição atual, ativar/pausar, remover; guardado no aparelho e em `civic_areas`; editar nome/raio e mover o centro sem recriar: feito em 21/11/2026 — toque no nome da área em Config. > Geofence)* — criar, editar, ativar/desativar e remover áreas circulares (nome, centro e raio; casos iniciais Casa, Trabalho e local personalizado); guardar no aparelho e sincronizar com o Supabase quando houver internet. Funciona por GPS, sem OBD.
- [x] **CR-102 (P0) Geofence: entrada e saída** *(feito)* — detectar transições reais com tolerância à imprecisão e à oscilação do GPS (histerese, confirmação por mais de uma leitura, intervalo mínimo); **perda de sinal de GPS não conta como saída**; um evento por transição.
- [x] **CR-103 (P1) Geofence: avisos no app e no Telegram** *(feito; falta o registro de entrega na tela)* — faixa visual e Telegram conforme preferências; cooldown; registro da entrega.
- [x] **CR-104 (P0) Excesso de velocidade** — *feito e validado em 21/11/2026:* limite configurável (Config. > Segurança), tolerância de 2 km/h, **5 s mínimos** acima do limite, cooldown de 30 s, faixa + voz (sem Telegram), registro por viagem de contagem, **maior velocidade e tempo acima do limite**, fonte (OBD ou GPS) em `civic_viagens`. Pendente: limite manual por viagem e limite por tipo de via (só com fonte confiável e licença adequada).
- [~] **CR-105 (P0) Desconexão do OBD e reconexão** — *parcial (21/11/2026):* alerta (faixa + voz + Telegram a cada 30 min) quando o adaptador para de responder **com o carro andando a 15 km/h ou mais** (isso descarta ignição desligada); só vale para quem já usou o OBD (o Fit não recebe); a reconexão automática já existia (tentativa a cada 15 s). *Feito depois (21/11/2026): Status do app mostra os estados "conectado", "sem resposta há X (ignição desligada ou adaptador sem energia)" e "indisponível/desconectado", com a idade e as últimas leituras (motor, tensão, rpm) em vez de "—"; backoff na reconexão (15 s, 30 s, 60 s, máx. 2 min, volta a 15 s ao conectar).* **Falta:** levar os estados e a idade às telas de OBD (hoje só no Status). Texto original do item: hoje o app detecta a queda mas não avisa. Estados explícitos: conectado, conectando, desconectado, **sem resposta** e **indisponível por ignição/energia**; detectar por timeout e falhas consecutivas (não por uma leitura perdida); em movimento, faixa e voz (Telegram conforme configuração); **reconexão automática com backoff** e estado visível; marcar leituras antigas como **desatualizadas** (com idade) em vez de zerá-las. Aceite: falha do adaptador não é confundida com motor desligado.
- [x] **CR-106 (P1) SOS multi-contato** *(Telegram VALIDADO pelo dono em 21/11/2026; SMS ainda não testado, só dá para testar na multimídia do Civic, que tem chip; feito: botão SOS, contagem 5 s, SMS + Telegram, resultado com confirmação, teste e nova tentativa; o SMS só funciona se a multimídia tiver telefonia)* — contatos de emergência (Telegram e/ou SMS, conforme permissões); botão acessível com **confirmação curta** contra acionamento acidental; envia localização, horário, link do mapa, identificação do veículo e dados disponíveis; anexa DTCs e eventos recentes **sem afirmar colisão sem evidência**; registra acionamento, destinatários, resultado do envio e cancelamento; avisa quando o canal estiver indisponível e **não indica envio concluído antes da confirmação**; oferece teste seguro dos contatos.
- [x] **CR-107 (P1) Driving Score persistido** — *feito em 21/11/2026:* cartão visual, nota e fatores por viagem (frenagens, acelerações, excessos, RPM elevado, consumo), persistência local e envio à Casa Tech (`civic_viagens`). Validado no carro em 21/11/2026. Falta só a **tela do dashboard na Casa Tech** (pedido já enviado).
- [~] **CR-108 (P0) Testes no tablet e na multimídia TS18** *(validação anterior confirmada pelo dono; a 1.1.0 nova — geofence, SOS e local desconhecido — precisa ser validada: GPS oscilando na borda de uma área, SOS com e sem rede, teste dos contatos)* — energia (ACC desligando, suspensão profunda, retomada), rede, OBD, GPS, tela e regressão; ver "Plano de testes" abaixo.

### Release 1.2.0 — Análise
- [x] **Comparador de viagens** (feito em 21/11/2026) — no app: Veículo > Viagem, toque em duas viagens e veja lado a lado distância, duração, velocidades, consumo, nota e eventos, com o melhor em verde. No banco: view `civic_viagens_comparativo` (nota vs média das 10 anteriores e ranking). A tela no dashboard fica na Casa Tech.
- [~] **Relatórios** e **tendências de consumo e condução** — banco pronto (21/11/2026): views `civic_viagens_semanal` e `civic_estados_diario`; faltam as telas na Casa Tech.
- [~] **Tendências de diagnóstico** (Muito alta; view `civic_estados_diario` pronta, falta a tela e depender dos primeiros estados reais) — detectar alterações persistentes em temperatura, fuel trims, tensão, marcha lenta e consumo (base: `civic_estados` e séries do OBD).
- [x] **Análise de carga elétrica** (Alta) — *feito em 21/11/2026 (a validar no carro):* Bateria > "Análise de carga elétrica" procura padrões repetidos nas partidas reais: alternador com carga baixa/alta (últimas 6 partidas), queda de tensão na partida piorando (3 recentes contra 3 anteriores), bateria em repouso baixa; cada achado mostra a confiança (baixa/média/alta pelo nº de partidas) e a origem (OBD). Alternador fraco persistente avisa no Telegram (no máx. 1 vez por semana). É indício, não diagnóstico. Falta enviar à Casa Tech.

### Release 1.3.0 — Caixa-preta
- [x] **Caixa-preta digital** (feito em 21/11/2026, versão 1.1.12; falta validar no carro e a tela na Casa Tech — tarefa 10 do pedido único). Contínuo de 1 amostra/s só com o carro em uso (velocidade OBD ou GPS, rpm, posição, pico de aceleração do sensor, temperatura e tensão); dispara em frenagem brusca, possível colisão, SOS e alerta crítico; salva antes e depois (15/30/60 s e 10/15/30 s configuráveis), mantém 30 eventos no aparelho (exportar e apagar) e envia à Casa Tech (`civic_caixa_preta`) com fila sem internet; registra origem, frequência e lacunas. Texto original do item: — buffer circular de telemetria com janela configurável (velocidade, aceleração, GPS, RPM e parâmetros disponíveis); captura disparada por frenagem brusca, possível colisão, falha crítica ou SOS; persistir janela **pré e pós-evento** com timestamp e horário local; registrar origem, frequência e qualidade de cada sinal (aceitar lacunas quando o OBD desconectar); definir retenção, exportação explícita e política de localização. **Limite:** diário técnico de eventos, não é caixa-preta certificada nem EDR homologado.
- [~] **Linha do tempo da viagem** — *feito no app (21/11/2026):* Veículo > Viagem mostra, com a hora de cada um, partida, frenagens e acelerações bruscas, excessos de velocidade, RPM elevado, possível colisão, SOS, alertas críticos e estacionamento (até 80 eventos por viagem; viagens antigas não têm). *Também enviada à Casa Tech (coluna `civic_viagens.eventos`, migration aplicada; falta a tela no dashboard).* **Falta:** a tela de **Segurança** com mapa, geofence e eventos.
- [x] **Mapa de saúde do veículo** (Alta) — *feito em 21/11/2026 (a validar no carro):* Diagnóstico > aba **Saúde**: um cartão por subsistema (injeção e falhas, arrefecimento, mistura, bateria e alternador, câmbio, marcha lenta e pneus quando o TPMS está ativo) com estado (Normal, Atenção, Alerta ou **Sem dados**, que nunca é tratado como normal), dado observado, fonte, confiança e idade da última leitura se o adaptador caiu. É indício, não diagnóstico. Falta enviar à Casa Tech.

### Release 1.4.0 — Assistente
- [ ] **Assistente mecânico (IA)** (Alta, posterior) — explicar DTCs e tendências com ressalvas, fontes e recomendações **não prescritivas**; resumo de anomalias. A IA explica; nunca comanda sistemas críticos nem dá diagnóstico definitivo sem validação.

### Release 2.0.0 — Plataforma
- [—] **Perfis de motorista** — **descartado pelo dono (21/11/2026):** todo mundo que usar o launcher tem o mesmo comportamento; não haverá estrutura de perfis nem Driving Score por condutor.
- [ ] Suporte ampliado a outros veículos e adaptadores; sensores opcionais.
- [x] **TPMS** (feito em 21/11/2026, **experimental e desligado por padrão**; o dono ativa quando tiver os sensores). Config. > "TPMS (pressão dos pneus)": chave Ativado/Desativado, varredura BLE, lista de sensores achados com **dados brutos (hex)**, atribuição a DE/DD/TE/TD, pressão ideal (padrão 32 psi) e unidade psi/bar; alerta (faixa + voz + Telegram a cada 6 h) abaixo de 85% / acima de 125% do ideal ou acima de 85 °C; cartão dos 4 pneus em Veículo; último estado em `civic_estados.pneus`. **Pendente quando os sensores chegarem:** conferir/ajustar o leitor ao formato real do sensor (o leitor atual cobre o formato BLE mais comum e não foi testado com hardware). Texto original do item: — só com sensores compatíveis. *Atenção: o TPMS tinha sido retirado do backlog por decisão do dono; só volta se você decidir.*

### Arquitetura e dados (orientações do documento)
- [~] **Camada de eventos confiável** — *1ª etapa feita em 22/11/2026:* todo aviso (Telegram, sininho, estacionamento, alertas, SOS) nasce no aparelho com `event_id` (UUID), `ocorreu_em`, `fonte`, `versao_app` e `payload_versao`; a função `civic-notificar` (v11) reconhece o reenvio (`duplicado: true`) e **não repete o Telegram nem a linha em `civic_eventos`** (índice único `dispositivo + event_id`; fila sem internet leva o mesmo ID). Telemetria, trajeto e viagens já eram idempotentes (chaves únicas). **Falta:** `confidence`, `sync_status` e tipos de evento padronizados (`geofence_enter/exit`, `overspeed_*`, `obd_*`, `sos_*`...), e levar o `event_id` à ignição, caixa-preta e linha do tempo. Texto original:  (fazer antes da caixa-preta): evento com `event_id` idempotente (gerado no aparelho), `vehicle_id`, `event_type`, `occurred_at` (UTC), `source` (OBD, GPS, accelerometer, user_action, cloud), `confidence`, `payload` versionado, `sync_status` (pending, synced, failed) e `app_version`. Tipos iniciais: `geofence_enter/exit`, `overspeed_started/ended`, `obd_disconnected/reconnected`, `sos_triggered/cancelled/delivery_result`, `driving_score_calculated`, `blackbox_capture_started/saved`.
  Estender as tabelas atuais (`civic_eventos`, `civic_viagens`...) de forma retrocompatível, com migrações versionadas e RLS.
- [~] **Camada de qualidade/origem dos dados** — *feita para o OBD em 22/11/2026:* cada leitura tem valor, unidade, **fonte** (OBD, GPS, sensor, calculado, informado), hora da última resposta do PID, validade (10 s, 90 s ou 5 min conforme o PID) e estado **Atual / Atrasada / Indisponível** (Diagnóstico > Saúde > "Qualidade dos dados do OBD"); valores impossíveis (ex.: 300 °C no motor, 40 V) são **descartados e contados** antes de chegar aos alertas e às telas. **Falta:** aplicar o mesmo modelo ao GPS e ao sensor de movimento, mostrar a idade do dado nas telas do OBD (não só no Saúde) e enviar fonte/qualidade à Casa Tech. Texto original: valor, unidade, origem, timestamp e validade em cada leitura; separar OBD, GPS e sensores.
- [ ] **Persistência local**: o documento recomenda Room/SQLite; hoje o app usa SharedPreferences com JSON. Avaliar a migração pesando o **tamanho do app** (regra do projeto) e a necessidade (eventos, buffer da caixa-preta, fila offline).
- [~] **Sincronização** — *feita em 22/11/2026:* IDs gerados no aparelho (`event_id` nos avisos; ignição, trajeto, viagens e caixa-preta já tinham chave única) e gravações idempotentes; **tentativas graduais** por canal (30 s, 1, 2, 4 e no máx. 5 min; qualquer envio que dá certo zera a espera de todos); **estado visível** em Status > Sincronização (último envio, falhas seguidas, o que está na fila e quando tenta de novo); **retenção** (pontos do trajeto 24 h, eventos de ignição 7 dias, avisos 48 h, viagens e caixa-preta até subirem). A telemetria frequente (pontos) e os eventos importantes já saem em pedidos separados. **Falta:** limitar também o tamanho em bytes da fila e um botão "enviar agora"; migrar a fila para um armazenamento próprio se o tamanho do app permitir (item de persistência local). Texto original: IDs gerados no aparelho, gravações idempotentes, fila local com tentativas graduais e estado visível, separar telemetria frequente de eventos importantes, política de retenção para trajetos, coordenadas e eventos.
- [x] **Privacidade** — *decisão do dono (22/11/2026):* o **administrador (dono) tem acesso a todos os dados**, e só pela página da **Minha Casa Tech**, com **autenticação separada do próprio módulo** (já criada); não se restringe esse acesso. **Ninguém mais vai acessar esse módulo: existe um único acesso, o do dono.** Por isso não há perfis, convites nem permissões por usuário a construir (o que já existir no banco para isso fica como está, sem uso). O objetivo da privacidade é só garantir que **ninguém além do aparelho e do administrador** alcance os dados. *Auditoria de 22/11/2026 (nada a corrigir no app):*
  - **Banco:** as 22 tabelas `civic_*` têm RLS ligado. O aparelho (papel `anon`) só lê e grava as linhas do próprio token (`civic_token()`); a Casa Tech (papel `authenticated`) só vê os aparelhos do próprio administrador (`civic_meus_dispositivos()`). `civic_avisos_chuva` e `civic_config` têm RLS sem política de propósito (só a função da nuvem, com a chave de serviço, acessa). A view `civic_veiculos` é *security definer* de propósito (esconde token e chat do Telegram) e filtra pelos aparelhos do administrador; o `anon` não tem acesso. As funções *security definer* só são executáveis pelo `authenticated`, nunca pelo `anon`.
  - **App:** nenhuma chave de serviço, segredo ou token do bot no código (só a chave pública, como deve ser); `allowBackup="false"` (o token do aparelho não vai para backup); nenhum tráfego sem criptografia (HTTP) e nenhum registro (log) de posição ou token; só o serviço de notificações é exportado e ele exige a permissão do sistema.
  - **Pendente só do lado da Casa Tech (fora do launcher):** ativar a *proteção contra senhas vazadas* no Supabase Auth (Authentication > Providers/Password; é um ajuste de painel) para o login do administrador.
  - Controles de exportação e exclusão no launcher ficam como estão (limpar histórico em cada tela); a exportação e a exclusão completas dos dados são do administrador na Casa Tech.
- [ ] Separação em camadas (UI, núcleo automotivo, serviço OBD, localização e sensores, dados locais, nuvem) para o núcleo servir ao Civic e ao Fit.

### Requisitos da multimídia e cuidados automotivos (valem para todas as entregas)
- Validar: inicialização como HOME e retomada após reinício/atualização; **energia** (ACC desligando, suspensão profunda, evitar serviços desnecessários); OBD (compatibilidade, reconexão, latência, timeouts, taxas de consulta); GPS (precisão em movimento, perda em garagem/túnel, rede como reserva); tela (2000×1200 paisagem, retrato, brilho, modo noturno, áreas de toque); áudio (foco, alertas falados, Spotify e navegação juntos, volume automático); permissões (Bluetooth, localização, notificações, microfone e SMS se adotado); estabilidade (teste prolongado, aquecimento, memória, vazamentos, perda de internet).
- **Matriz por fonte:** velocidade (OBD ou GPS), RPM/temperatura do motor (só OBD, se o PID existir), viagem/distância (GPS; OBD opcional), geofence (GPS; nuvem opcional), movimento (OBD opcional, GPS, sensor Android), DTC/freeze frame (OBD, conforme ECU), Telegram/dashboard (internet; localização pode compor).
- **Cuidados:** não escrever na ECU nem apagar DTC ou executar comandos proprietários automaticamente; não assumir que o adaptador OBD fica alimentado com o carro desligado; pressão de óleo, TPMS e PIDs proprietários da Honda dependem de hardware/PIDs específicos; alertas críticos exigem limites validados e não concluem diagnóstico com um único sensor; não induzir o motorista a usar menus complexos dirigindo.

### Plano de testes e qualidade
- Cenários: sem internet durante a viagem (eventos ficam em fila e sincronizam depois **sem duplicar**); OBD desconecta em movimento (dados marcados como desatualizados, alerta e reconexão); GPS oscila na borda da geofence (sem entradas/saídas falsas); velocidade oscila perto do limite (sem alertas repetidos); SOS sem rede móvel (informa a falha, mantém registro local e permite nova tentativa); multimídia entra em suspensão (serviços respeitam a energia e o estado é recuperado); app atualizado (assinatura preservada, banco migra sem perda); **veículo sem OBD** (viagem, localização, geofence e GPS seguem disponíveis).
- Métricas: taxa de reconexão do OBD e tempo médio para recuperar a sessão; falsos positivos de geofence, excesso de velocidade e colisão; **eventos duplicados após sincronização offline = zero**; tempo de inicialização e retomada na TS18; memória e estabilidade em execução prolongada; percentual de eventos com fonte, timestamp e qualidade registrados.

### Outros itens ainda abertos (fora do documento)
- [x] **Aviso de local desconhecido** *(feito em 21/11/2026: depois de 5 vagas aprendidas, estacionar a mais de 250 m de todas e fora das áreas do geofence avisa no Telegram)* — — aprender as vagas de costume e avisar quando o carro parar ou andar fora delas (GPS). Combina com a geofence (CR-101 a CR-103).
- [ ] **Versionamento:** o documento chama as entregas de 1.1.0, 1.2.0... Hoje a versão é calculada pelo CI (1.0.x a cada build). Para marcar uma release, basta subir `MENOR` em `versao.properties` (e `RUN_BASE`) na entrega correspondente.

**Ordem recomendada pelo documento:** consolidar a 1.1.0 nas lacunas identificadas, estabelecer a **camada de eventos confiável** e só então avançar para caixa-preta, tendências e assistente. Passos: inspecionar o repositório, confirmar esquema/RLS/Edge Functions, implementar o modelo comum de eventos e a camada de qualidade/origem, construir geofence e excesso de velocidade como regras independentes do OBD, completar o estado de conexão OBD, adicionar SOS multi-contato, e testar em tablet e TS18.

## Casa Tech — página de indicadores para embutir no launcher (pedido do dono em 21/11/2026)
Decisão do dono: **a Casa Tech fica por último, quando todo o resto do launcher estiver pronto.**
- [~] **Página simples de indicadores, autenticada, para embutir (embed) no launcher** — *lado do launcher feito em 22/11/2026:* Veículo > aba **Painel** abre `https://casa-tech-renatin.vercel.app/veiculo/launcher` numa WebView travada nesse endereço, com a ponte nativa `CivicLauncher.acesso()` que troca o token do aparelho por um acesso curto (15 min, só leitura) pela função `civic-painel`, renova antes de vencer e mostra "Sem conexão" (tentando de novo a cada 15 s) sem internet; guia em `docs/LAUNCHER-PAINEL.md`. O que falta evoluir na Casa Tech está em `docs/CASA-TECH-EVOLUCAO.md`. Texto original do pedido: — uma página enxuta da Casa Tech (poucos números grandes e legíveis: estado do carro, última viagem e nota, bateria, estacionamento, alertas) pensada para abrir dentro do launcher (WebView), com **login/autenticação** (sem expor dados a quem tiver o link; nunca embutir chave de serviço no app), layout para a tela da multimídia (paisagem, 2000×1200) e do tablet, modo escuro/claro do launcher e funcionamento tolerante à falta de internet (mostrar "sem conexão" em vez de erro). No launcher: um cartão/atalho que abre a página e mantém a sessão. Antes de pedir, listar quais indicadores o launcher já envia e os que faltam.

## Avisos com endereço completo (pedido do dono em 22/11/2026)
- [x] **Todo aviso do Telegram que leva coordenadas (link do mapa) leva também o endereço completo** (linha 🏠: rua, número, bairro, cidade, estado e CEP) — estacionamento, geofence, SOS, colisão, uso incomum e local desconhecido. O app tenta o geocodificador do aparelho (até 4 s, para não atrasar um SOS); se o aparelho não tiver ou não achar, a função `civic-notificar` (v12) busca no OpenStreetMap (Nominatim, até 3,5 s). Se nenhum responder, o aviso sai só com as coordenadas, sem atraso. O endereço também vai para `civic_estacionamentos.endereco` e `civic_eventos.mensagem`. Os coordenadas dos avisos passam a ser consultadas no OpenStreetMap só quando o aparelho não resolve o endereço sozinho.

## Botão "Estacionado" (pedido do dono em 22/11/2026)
- [x] **Botão "Estacionado" na Home (todas as opções), no OBD e na Direção** — *feito, a validar no carro:* marca a posição de agora como a vaga, guarda o endereço em "Onde parei" e manda o aviso ao Telegram com as coordenadas e o endereço completo. **Só aparece com o carro parado** (velocidade abaixo de 1 km/h pelo OBD ou, sem ele, pelo GPS; sem velocidade conhecida não aparece). Fica no canto inferior direito da Home e do OBD, na Direção em tela dividida e no painel flutuante da Direção (na faixa fina de 64 dp não cabe). Toques repetidos em menos de 10 s são ignorados; sem posição de GPS avisa na tela.

## Home em tela dividida e placa discreta (pedido do dono em 22/11/2026)
- [x] **Itens cortados na Home em tela dividida** — *feito, a validar na multimídia:* os cartões (carro, resumo do OBD, leituras, Spotify, clima e resumo da Ousada) agora são montados numa altura de projeto e, se o espaço real for menor (meia tela), **encolhem por igual para caber inteiros** em vez de serem cortados embaixo; com espaço de sobra nada muda. Vale para os layouts Padrão, Caixas, Painel, Ousada, Esportiva e Nova linha na versão dividida e na tela cheia (`Modifier.escalarSeMenorQue`).
- [x] **Placa discreta e escura** — a placa configurada deixou de ser branca com barra azul: agora tem fundo grafite quase opaco, borda fina, faixa "BRASIL" apagada e caracteres em cinza-claro, integrada à foto do carro, em todas as telas da Home (cena escura, recorte e foto cinza), na tela dividida e na cheia.

## Análise de condução por viagem (pedido do dono em 22/11/2026)
- [x] **Tela de viagem com a proposta "Análise de Condução"** — *feito, a validar no carro:* Veículo > Viagem mostra, para a **viagem escolhida na lista** (toque; por padrão a em andamento ou a última), os indicadores de aceleração média, RPM médio e consumo, o cartão da nota com barra e selo, o resumo (velocidade média, distância, duração, marcha lenta), os gráficos (aceleração, tempo em cada faixa de rpm com a zona eficiente, temperatura) e a coluna "Eventos da viagem"; a linha do tempo da viagem escolhida vem logo abaixo. "Comparar" virou um botão na linha de cada viagem. Para isso cada viagem passou a guardar uma **série** (uma amostra a cada ~10 s, até 240 pontos) e o **tempo em marcha lenta**; vão para `civic_viagens.serie` e `marcha_lenta_s` (migration aplicada). Viagens antigas não têm gráficos. Sem OBD (só GPS) aceleração, rpm, temperatura e consumo ficam "—" e o gráfico mostra a velocidade.
- [ ] **Mesma análise na Casa Tech** — pedido pronto em `docs/CASA-TECH-ANALISE-CONDUCAO.md` (com a imagem em `docs/designs/analise-conducao.png`); o dono vai pedir na Casa Tech.

## Calendário com o Ponte Aérea (pedido do dono em 23/11/2026)
- [x] **Ícone Calendário** no menu (Início, OBD, Favorito, Spotify, Veículo, **Calendário**, Apps, Config.): abre um calendário padrão (grade do mês, domingo a sábado, hoje destacado, navegar por mês, botão Hoje). O botão **Ponte Aérea** liga os eventos do calendário do Ponte Aérea por cima da grade (pontos coloridos por tipo e a lista do dia escolhido: voos, viagens, feriados, eventos, presencial e dias com as filhas), com o próximo voo, filtro de passageiro (só voos), "atualizado às HH:MM" e mensagens claras para token recusado, limite de consultas e falta de internet (mostra o que foi guardado). API somente leitura `GET /api/launcher/calendario` (versão 1; guia em `docs/LAUNCHER-API-CALENDARIO.md`), com ETag (304), cache da última resposta, atualização a cada 15 min e sem repetir na hora em 401/429.
- **Decisão de segurança:** o guia sugere embutir o token no APK (`BuildConfig`), mas o APK deste projeto sai numa **Release pública do GitHub**, e o próprio guia diz que o APK com o token não pode ser distribuído. Por isso o token **não vai no build**: é informado uma vez em Config. > Calendário (campo ou "Colar") e fica só no aparelho (SharedPreferences, como o token do aparelho). Limitação aceita: quem tiver acesso físico ao aparelho desbloqueado pode ver os dados do calendário; se o aparelho se perder, remova o hash na Vercel. Cada aparelho (Civic, Fit, tablet) precisa do token informado uma vez.

### Calendário: ajustes de 23/11/2026
- [x] **Filtro de passageiro** — mostra **todos** os passageiros (os da API e os que aparecem nos voos), em linhas que quebram, em vez de só três espremidos.
- [x] **Dia escolhido** — tocar num dia mostra os eventos dele; tocar de novo no mesmo dia limpa a escolha. **Trocar de filtro ou de mês limpa a escolha** e a lista mostra todos os eventos do mês (agrupados por data), então o filtro nunca mostra "nada" só porque o dia escolhido não tem aquela pessoa.
- [x] **Cartão de embarque** — tocar num voo abre o cartão no padrão do Ponte Aérea, com o que a API do Launcher entrega (guia atualizado em `docs/LAUNCHER-API-CALENDARIO.md`): companhia, data e reserva, origem e destino (com nome do aeroporto e cidade dos códigos mais comuns), número do voo e horários, situação, trecho, objetivo, nacional/internacional, passageiro, escala, reserva (com copiar), assento, vaga de garagem, bilhete e observações. **Sem** valor pago, pontos, custos, clima nem código da compra (a API não envia), **sem Editar** e **sem adicionar ao calendário**. Atenção: o localizador (reserva) passa a aparecer na tela do carro, como o guia avisa.

## 4 layouts novos do OBD (pedido do dono em 23/11/2026; proposta em `docs/designs/obd-4-propostas-novas.png`)
- [x] **Somados aos atuais** (Esportivo, Cockpit, Modular), na lista "Layout" do OBD e no "Mais" das abas: **01 · Esportivo (novo)** (conta-giros neon central com a velocidade, estrada com luzes azuis, traseira do carro, leituras dos dois lados, farol e setas verdes, ECO Assist), **02 · Cockpit digital** (conta-giros e velocímetro neon, Civic no centro com aura azul no piso, marcha, ECO/NORMAL/SPORT, 7 leituras embaixo), **03 · Técnico / Diagnóstico** (menu, carro em raio-X com o motor aceso e os sensores, tabela de 12 leituras, Sistema OK / Monitores / Readiness / MIL) e **04 · Direção simplificada** (velocidade enorme sob arco neon, cidade e estrada ao fundo, ECO Assist, 6 leituras grandes). Tema escuro e claro seguem o tema do app.
- Cada layout é montado numa **prancheta de 1280 dp** (como a proposta) e escalado por igual para caber inteiro, em tela cheia ou dividida.
- Abas: Direção (este layout), Viagem e Gráficos → Veículo > Viagem (análise de condução), Sensores → layout Técnico, Diagnóstico → tela de diagnóstico, Mais → lista de layouts; a engrenagem do cabeçalho escolhe o adaptador.
- **Limites honestos:** o OBD não informa a marcha: o selo mostra "D" com o motor ligado e o carro andando e "P" parado (estimativa). ECO/NORMAL/SPORT e o ECO Assist seguem o nível de consumo calculado pelo app (não são modos do carro). Não há logo da Honda nem foto em raio-X: o "H" é desenhado e o raio-X é o recorte do Civic com filtro azul e o motor aceso desenhado. O ícone de assistente de faixa do layout 04 não foi incluído (o carro não informa).

### Ajustes de 23/11/2026 (pedido do dono)
- [x] **Calendário:** o filtro de passageiros virou um **combo** (uma linha, com todos os nomes na lista).
- [x] **4 layouts novos do OBD:** sem o cabeçalho próprio (o app já tem) e **ocupando a tela toda** (a prancheta agora estica para preencher; a linha de botões Layout/Demo/Adaptador saiu e foi para o "Mais"). **Controle do Spotify** nos quatro. Esportivo e Direção simplificada: farol, setas e ECO Assist maiores. Cockpit digital: setas entre o conta-giros e o velocímetro. Técnico: setas acima do carro e o menu com as opções da tela de diagnóstico (**Falhas, Bateria, Mistura e Saúde**, abrindo direto na aba certa), além de "Mais". Direção simplificada ganhou o botão "⋯" (layout, demonstração e adaptador).
- [x] **Ajuste (23/11/2026):** os 4 layouts novos voltaram a ter a linha de troca de layout (Layout, Demo e Adaptador) em cima e ocupam o mesmo espaço dos demais (Esportivo, Cockpit e Modular).
- [x] **Ajuste (23/11/2026):** o controle do Spotify dos 4 layouts novos ficou maior, com a **capa do álbum em 72 dp** (sem capa, o logo do Spotify no mesmo espaço), texto e botões maiores; o rodapé do Cockpit digital ficou mais alto para caber.

### Endereço nos avisos — correção de 24/11/2026
- **Causa:** o endereço de reserva era buscado no servidor (função `civic-notificar`), mas os serviços gratuitos recusam chamadas de servidor: OpenStreetMap/Nominatim e Photon respondem 403 e BigDataCloud 400 (conferido nos logs da função). A multimídia também não tem o geocodificador do Android. Além disso, o aviso que ficava na fila sem internet era reenviado sem tentar o endereço de novo.
- **Correção:** a busca agora é feita **no aparelho** (geocodificador do Android → Nominatim → Photon, no máximo ~7 s no total) e é **refeita quando o aviso sai da fila sem internet**. A função continua tentando no servidor como última reserva (falha rápido, sem atrasar).
- **Bug corrigido junto:** desde a camada de eventos confiável, os avisos com `event_id` não eram gravados em `civic_eventos` (o índice único parcial não serve para o `on_conflict`); trocado por restrição única normal (migration `20261124_eventos_event_id_unico.sql`). Testado: grava e reconhece o reenvio como duplicado.
- **Fora desta entrega:** a resposta do comando `/onde` do bot mostra a posição atual só com coordenadas (a última vaga já vem com endereço).
- [x] **Ajuste (26/11/2026):** Cockpit em tela dividida com o Spotify maior (capa do álbum em 80 dp); a mini música de todos os layouts antigos passou a mostrar a capa quando o Spotify informa.

- [x] **Ajuste (26/11/2026):** Modular em tela dividida com o Spotify maior (capa do álbum em 80 dp), igual ao Cockpit.

- [x] **Validação (26/11/2026):** o dono validou o launcher no Civic, na multimídia de tela maior.


## Civic com a multimídia nova (validação do dono em 26/11/2026)
- [x] **Estacionamento avisado pela nuvem (vigia do silêncio).** *Causa:* a multimídia nova (`sprd s9863a1h10_Natv`) apaga na hora em que a chave é desligada: sem energia e sem internet, nenhum aviso consegue sair; o "Carro estacionado" só saía 15 s depois de ela religar (confirmado no banco). *Solução:* ao parar, o app manda na hora o ponto e o status com o **endereço resolvido no aparelho** (`civic_status.endereco`); a função **`civic-vigia`** (pg_cron a cada minuto) vê o carro que **parou de dar sinal parado por mais de 3 min** com o motor ligado no último status e manda no Telegram "🅿️ Carro estacionado (a multimídia desligou por volta das HH:MM)" com coordenadas, endereço, link e pino; grava a vaga com `origem = 'nuvem'`. Se sumiu andando (acima de 8 km/h), não avisa (mais provável falta de internet). O aviso atrasado que a multimídia manda ao religar é reconhecido pela `civic-notificar` (v14) e **não repete** no Telegram. Aviso em até ~4 min depois de desligar.
- [x] **Launcher abre sozinho ao ligar a multimídia.** A multimídia abre a tela do fabricante por cima mesmo com o launcher como padrão do Android. Agora o app traz o launcher para a frente ao ligar (aos 3 s e aos 10 s) e ao acordar da suspensão (tela apagada por mais de 1 min). Precisa da permissão "Sobrepor a outros apps"; chave em Config. > "Ao ligar a multimídia" (no Fit com CarPlay, desligar).
