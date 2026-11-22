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
- [ ] **Privacidade**: localização, rotas, identificação do veículo e contatos são dados sensíveis — autenticação por veículo/usuário, RLS, transporte seguro, minimização, controles de exclusão/exportação; nunca embutir service-role key no app.
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
- [ ] **Página simples de indicadores, autenticada, para embutir (embed) no launcher** — uma página enxuta da Casa Tech (poucos números grandes e legíveis: estado do carro, última viagem e nota, bateria, estacionamento, alertas) pensada para abrir dentro do launcher (WebView), com **login/autenticação** (sem expor dados a quem tiver o link; nunca embutir chave de serviço no app), layout para a tela da multimídia (paisagem, 2000×1200) e do tablet, modo escuro/claro do launcher e funcionamento tolerante à falta de internet (mostrar "sem conexão" em vez de erro). No launcher: um cartão/atalho que abre a página e mantém a sessão. Antes de pedir, listar quais indicadores o launcher já envia e os que faltam.
