# Civic Renatin 1.0.0 — tudo que o launcher já tem

Inventário do que existe hoje no aplicativo e na nuvem (Supabase / Casa Tech). A parte final compara com as novas ideias
(Driving Score, Geofence, excesso de velocidade, desconexão do OBD e botão SOS) e diz o que falta implementar.

Legenda: **OBD** = precisa do adaptador ELM327 · **GPS** = funciona sem OBD (ex.: Honda Fit com CarPlay) · **Nuvem** = precisa de internet.

---

## 1. Resumo: o que falta das novas ideias

| Ideia | Situação | O que já existe | O que falta implementar |
|---|---|---|---|
| **Driving Score** | **Feito (21/11/2026)** | Cartão "Resumo da condução" em Veículo > Viagem (anel com a nota, selo, km, tempo, frenagens, acelerações, excessos de velocidade, RPM elevado, consumo, comparação com as últimas viagens); nota guardada por viagem; **funciona só com GPS** (CarPlay/Android Auto, sem OBD); cada viagem sobe para `civic_viagens` na Casa Tech | Validar no carro; tela do dashboard na Casa Tech |
| **Geofence** | **Não existe** | Só o histórico de estacionamentos e as vagas | Áreas permitidas (círculo com raio em torno de Casa, Trabalho ou ponto escolhido); aviso no Telegram e faixa no app ao sair (e ao entrar); funciona por GPS, inclusive no Fit; áreas guardadas no aparelho e na nuvem |
| **Excesso de velocidade** | **Feito (21/11/2026)** | Limite configurável, 5 s mínimos, faixa + voz, contagem, maior velocidade e tempo acima do limite por viagem (na nota e no banco) | Validar no carro; limite por tipo de via |
| **Alerta de desconexão OBD** | **Parcial (alerta em movimento feito em 21/11/2026)** | O app detecta a queda (estado vai para Desconectado e os dados zeram) | **Não avisa ninguém:** falta faixa e voz "Adaptador OBD desconectado", Telegram se acontecer com o carro em movimento, tentativa de reconexão automática visível e diferenciar "motor desligou" de "adaptador caiu" |
| **Botão SOS** | **Não existe** | Detecção de possível colisão com contagem de 30 s e "Estou bem", que manda localização ao Telegram vinculado (um chat só) | Botão SOS na barra do topo (com confirmação de 5 s para evitar toque sem querer); envia localização, endereço, dados do carro (placa, modelo, combustível, falhas) para **vários contatos cadastrados**; contatos em Config. (Telegram e/ou SMS); registro do evento na Casa Tech |

---

## 2. Telas e navegação

- **Menu inferior:** Início · Direção · OBD · Favorito · Spotify · Veículo · Apps · Config. (modelo do menu configurável).
- **Barra do topo:** hora e data, nome do carro, versão do app com selo (verde = atualizado, laranja = nova versão), estado do OBD, Bluetooth, Wi-Fi, rede móvel, alto-falante (liga/desliga avisos sonoros), microfone (comando de voz), sino de avisos, botão do Modo HUD.
- **Temas claro e escuro**, com modo automático (claro de 6h às 18h). Tablet de teste e multimídia (2000×1200, paisagem; também retrato).
- **Modal de "Sem internet"** (Wi-Fi ou 3G/4G), com atalhos para as configurações de rede.
- **Modal de nova versão**, com a versão instalada e a nova.

### Tela Início — 3 layouts (pílula "Início")
- **Padrão:** carro, mapa e sinais, Spotify, clima e rede.
- **Caixas:** caixas separadas com sombra, mapa menor, resumo do OBD em grade, setas e farol.
- **Painel:** foto do carro e leituras, velocímetro do OBD Modular no centro (setas, farol e RPM), mapa compacto, Spotify e clima.
- Em todos: placa Mercosul desenhada no para-choque da foto (Civic e Fit).

### Tela OBD — 3 layouts (botão "Layout")
- **Esportivo, Cockpit e Modular**, com mostradores premium, aura branca (tema escuro) ou azulada (tema claro), setas e farol.
- Demonstração do OBD (simula motor, viagem e estacionamento) e escolha do adaptador Bluetooth.
- Gráficos ao vivo (séries de temperatura, tensão, MAP etc.), tela de mistura, tela de bateria.

### Tela Veículo
- Abas **Viagem** (computador de bordo e histórico), **Abastecimentos** e **Manutenção**.
- Etanol ou gasolina, custo por km, gasto no mês.

### Tela Apps e Config.
- Apps instalados, favoritos fixados, atalhos.
- Config. organizada em seções (ver item 8).

---

## 3. Mapa, música e clima

- **Navegação:** Google Maps ou Waze (configurável); Casa e Trabalho; "Iniciar direção"; painel de direção por cima do app de navegação (música, volume, velocidade).
- **Spotify:** capa, título, artista, progresso, anterior/play/próxima; retoma de onde parou quando o carro liga.
- **Clima:** temperatura, condição, máxima/mínima, sensação, umidade, vento, chuva nas próximas horas; localização por GPS, rede ou IP (nunca uma cidade fixa).
- **Modal do clima:** agora, próximas 24 h, próximos dias, sol, UV, vento; **chuva no destino** com busca por bairro, cidade ou endereço (sugestões enquanto digita, endereço completo, distância e "Ver no mapa").
- **Rotina diária:** sugestões de destino por período, local e dia da semana, com faixa "Ir agora".
- **Volume automático pela velocidade** (compensa o ruído da estrada).

---

## 4. Dados do carro (OBD)

- **Leituras ao vivo:** RPM, velocidade, temperatura do motor e do ar, tensão, carga, acelerador, MAP, avanço, fuel trims (STFT/LTFT), malha fechada, nível do tanque, consumo médio e instantâneo, câmbio (PID configurável).
- **Combustível:** nível pelo PID 012F; consumo por MAF ou estimado por MAP, temperatura do ar e RPM; autonomia; capacidade do tanque configurável.
- **Falhas (DTC):** lista com descrição em português, luz de injeção, apagar códigos com confirmação, histórico de falhas, **freeze frame** (dados no instante da falha) com aviso no Telegram.
- **Monitores de prontidão** e aviso de "códigos apagados" (km desde que apagou).
- **Saúde do motor (OBD avançado):** termostato (tempo até 80 °C a frio), catalisador (sondas lambda), marcha lenta irregular, dreno de bateria, modo 06, temperatura do fluido do câmbio.
- **Saúde da bateria na partida** (queda de tensão ao dar partida; Boa / Atenção / Fraca).
- **Luzes-espia pelo CAN** (experimental): captura guiada, setas, farol, pisca-alerta.
- **ECO Assist:** anel econômico / normal / alto consumo a partir da carga, do RPM e da aceleração.

---

## 5. Segurança e direção

- **Alertas inteligentes** (faixa sobre qualquer tela, voz opcional e Telegram nos graves): superaquecimento, luz de injeção, alternador sem carga, bateria fraca, farol desligado, motor frio com giro alto, câmbio quente, fadiga.
- **Alertas falados** em português (liga/desliga no topo e em Config. > Avisos sonoros).
- **Fadiga:** 2 h sem pausa de 15 min, ou dirigindo de madrugada.
- **Modo de condução (Driving Score):** frenagens e acelerações bruscas, excessos de velocidade e RPM elevado contados por viagem, nota 0–100 no resumo (Telegram, Veículo > Viagem e Casa Tech). Medido pelo GPS do Vigia, então vale com CarPlay/Android Auto e sem OBD.
- **Possível colisão:** queda de 30 km/h ou mais em até 3 s seguida do motor apagando; contagem de 30 s com "Estou bem"; sem resposta, manda aviso e localização ao Telegram (botão de teste em Config.).
- **Movimento suspeito:** partida em horário fora do padrão (aprende com as últimas 60 partidas) e "Vigia total" (toda partida avisa).
- **Vigia (serviço em segundo plano):** mantém GPS e avisos mesmo com o launcher fechado ou com o CarPlay na frente.
- **Modo HUD:** velocímetro grande e espelhado para o para-brisa.
- **Comando de voz curto** (microfone do topo).

---

## 6. Viagens, estacionamento e consumo

- **Computador de bordo:** viagem atual (distância, tempo, média, máxima, consumo, custo) e histórico; **funciona só com GPS** (Fit sem OBD).
- **Odômetro do app:** km informado + distância somada pela velocidade.
- **Onde parei o carro:** posição, hora, idade do sinal de GPS e endereço ao desligar; aviso no Telegram com pino do mapa; reserva por tempo parado quando a multimídia não desliga com a chave.
- **Registro de abastecimentos:** consumo real tanque cheio a tanque cheio, gasto no mês, custo por km; **abastecimento automático** pela subida do nível do tanque, com valor pago (total ou por litro) e aditivada informados depois.
- **Etanol ou gasolina?** com a proporção do consumo real e o preço médio da ANP.
- **Lembretes de manutenção:** plano do Civic G9 1.8 (óleo, filtros, rodízio, alinhamento, fluido de freio, pastilhas, arrefecimento, velas, ATF), vence por km ou tempo.

---

## 7. Nuvem: Telegram, Supabase e Casa Tech

### Telegram (bot vinculado por código)
- Avisos: alertas do motor, falhas, manutenção, estacionamento, chuva na vaga, fim de viagem, colisão, uso incomum, abastecimento.
- **Comandos:** /onde, /status, /bateria, /consumo, /manutencao, /resumo, /ajuda.
- **Resumo semanal** aos domingos; **chuva onde o carro está parado** (checagem a cada 20 min).

### Supabase (projeto CasaTechRenatin)
- **Funções:** `civic-notificar` (avisos, localização, preço ANP, registro do bot), `civic-bot` (comandos e rotinas), `civic-anp` (preços semanais).
- **Tabelas:** estacionamentos, eventos, manutenção, abastecimentos (view com análise), estados do último uso, status atual (online), posições (trajeto), ignição, acessos.
- **Telemetria:** envio em pacotes pequenos, fila local sem internet, status online a cada 60 s (5 min desligado), posição pela rede quando o GPS não tem sinal.
- **Sem OBD (Fit):** uso do carro detectado pelo GPS (andou = ligou; 5 min parado = desligou).

### Casa Tech (dashboard, só leitura)
- Visão geral, Viagens, Estacionamento, Abastecimentos, Manutenção e Saúde do motor, com login OAuth do Supabase e veículo simulado para testes.
- Documentos: `docs/DASHBOARD-CASA-TECH.md` e `docs/DASHBOARD-CASA-TECH-LAYOUT.md`.

---

## 8. Configurações

Status do app · Identidade · Tanque de combustível · Imagem do carro (Civic ou Fit) · Modelo do menu · Desempenho (modo leve) · Painel de direção · Mapa de navegação · Rotina · Música ao ligar o carro · Volume automático · Avisos sonoros · Segurança (vigia, testar voz, HUD, teste de colisão) · Avisos no celular (Telegram) · Luzes-espia pelo CAN · Diagnóstico avançado (códigos apagados, termostato, catalisador, marcha lenta, dreno, freeze frame, modo 06, câmbio) · Sistema.

- **Status do app:** mostra o que está funcionando (OBD, tanque, MAF, GPS, localização o tempo todo, nuvem, Telegram, Vigia, painel, Spotify, Bluetooth, voz), com botão para corrigir cada permissão.

---

## 9. Plataforma

- Kotlin + Jetpack Compose, `minSdk 26`, registrado como tela inicial (HOME).
- **Atualização automática:** build no GitHub Actions publica a Release; o app baixa a nova versão sozinho e pede para instalar.
- Versão MAIOR.MENOR.PATCH (nenhuma parte passa de 99), assinada sempre com a mesma chave (guardada nos segredos do GitHub).
- App pequeno: R8 e remoção de recursos não usados; sem bibliotecas pesadas.
- Hardware alvo: multimídia TS18 (Civic G9 2012) e tablet do Honda Fit (Android 12, CarPlay, sem OBD).
