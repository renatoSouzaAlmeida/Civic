# Decisões do projeto

## Hardware alvo
- Multimídia TS18, Unisoc UIS8581A (8× Cortex-A55 1,6 GHz), GPU PowerVR GE8322 (fraca), 4 GB RAM, 64 GB.
- Tela 2000×1200 (5:3), paisagem, com recurso de virar a tela.
- Android reportado como 13, mas build `QP1A.190711.020` (Android 10) → `minSdk 26`, testar sempre em Android 10.
- Barramento CAN V1.13 (módulo do fabricante; sem API pública conhecida).
- Veículo: Honda Civic G9 2012 1.8 (OBD-II padrão).
- Sem internet na garagem do subsolo.

## Prioridades definidas pelo dono
1. Waze e Spotify simultâneos e fluidos (principal).
2. Atalhos e interface bonita para os dois.
3. Visual profissional, com animações (leves).
4. Dados do carro via OBD (fase posterior).
- Fora de escopo: notificações.

## Tecnologia
- App Android nativo, Kotlin + Jetpack Compose, registrado como HOME (launcher).
- Layout por proporção (`BoxWithConstraints`), sem larguras fixas; `configChanges` evita recriar a Activity ao girar/redimensionar.
- Build no GitHub Actions (o ambiente de desenvolvimento não alcança o SDK do Android).

## Testes
- Banco de testes: tablet Android de 9". A multimídia é validação final.
- Instalação por pendrive só na primeira vez; depois, auto-atualização pelo próprio app.

## Pontos a validar no aparelho
- Tela dividida nativa na multimídia (Waze + painel)?
- Deep link `waze://?favorite=Home|Work`.
- Menu Fábrica / opção "App de tela inicial".
