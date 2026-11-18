# Civic Launcher

Launcher Android pessoal para a multimídia do Honda Civic G9 2012 (1.8).
Abre direto ao ligar o carro, com **Waze e Spotify lado a lado** como prioridade.

Uso próprio, sem fins comerciais.

## Como instalar

1. Em **Actions → Build APK**, abra a execução mais recente e baixe o artefato `civic-launcher-debug` (contém o `.apk`).
2. Instale no tablet/multimídia (permita "fontes desconhecidas").
3. Aperte o botão Home e escolha **Civic Launcher** → "Sempre".

## Estado atual (v0.2)
- Layout adaptativo: paisagem (mapa à esquerda, painel à direita) e retrato (painel embaixo).
- Cartão Waze com atalhos **Casa** e **Trabalho** (`waze://?favorite=...` — a validar no aparelho).
- Cartão Spotify **com capa, música, artista e controles reais** (anterior, play/pausa, próxima). Na primeira vez, toque em "Ativar controle" e libere o Civic Launcher em *Acesso às notificações*.
- Relógio e botões grandes (alvos de toque de 80dp).
- Registrado como app de tela inicial (HOME).

## Próximas etapas
1. ~~Controles reais do Spotify~~ — feito na v0.2 (precisa conceder "Acesso às notificações" ao app).
2. Waze + Spotify simultâneos: tela dividida nativa, com painel flutuante como alternativa.
3. Início automático ao ligar o carro e retomada da música.
4. Atualização automática do APK pelo próprio app (via GitHub Releases).
5. OBD-II (ELM327 Bluetooth): velocidade, RPM, temperatura, bateria, falhas.
6. Animações do painel (leves para a GPU PowerVR GE8322).
7. Virada de tela (retrato) — a base já é adaptativa.

Detalhes das decisões em [docs/DECISOES.md](docs/DECISOES.md).
