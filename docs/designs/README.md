# Direções visuais (propostas)

Seis direções de interface para a Tela Início e a Central do Civic (OBD), desenhadas em 2000×1200
(proporção da multimídia). Servem para escolher o estilo antes de implementar no app.

| Direção | Conceito | Observação para a multimídia (GPU fraca) |
|---|---|---|
| **A — Esportivo Neon** | Azul-marinho com brilho neon, foto do carro, medidores com marcações; o mais próximo da proposta original | Brilhos viram degradês e bordas, sem blur em tempo real |
| **B — Premium Claro** | Cartões brancos de cantos suaves, mapa grande, menu flutuante; limpo e comercial | O mais leve de todos |
| **C — Cockpit Carbono** | Fibra de carbono, vermelho Honda, cantos chanfrados, menu lateral e luzes de troca de marcha | Fibra de carbono vira imagem repetida (barata) |

| **D — HUD Digital** | Números de sete segmentos, barra de giros em degraus (como o HUD) e lista de leituras OBD (PIDs) | Só formas simples e texto: o mais leve em GPU |
| **E — Estilo BYD** | Carro grande em destaque, cartões de vidro de cantos bem arredondados e dock de apps coloridos (inspirado nas multimídias BYD DiLink); já mostra a versão em **retrato**, para a tela que vira | Usa translucidez, mas dá para simular com degradês |
| **F — Escolhida** | Clean da B + disposição e estilo da E + menu da A: carro menor, Waze maior, Spotify com 4 caixas (clima, rede, motor, bateria), em tema claro e escuro | Base da implementação no app |

Atualização da F: a Central do Civic tem o cartão do carro à direita (igual ao da Tela Início) e a imagem do carro passou de azul para **cinza grafite** (`carro-cinza.png`).

Dados mostrados são fictícios. Consumo é uma estimativa; pressão de óleo e marcha (P/D) não existem no OBD padrão do Civic G9.
