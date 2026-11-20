// Civic Renatin — envia notificações do carro para o Telegram.
// A autenticação é feita pelo token do aparelho (cabeçalho x-civic-token), conferido na tabela
// civic_dispositivos. O token do bot fica no segredo TELEGRAM_BOT_TOKEN (nunca no app, que é público).
//
// Ações (POST JSON):
//   { acao: "info" }                    -> nome do bot e se o aparelho já está vinculado
//   { acao: "vincular" }                -> procura o código do aparelho nas mensagens do bot e grava o chat
//   { acao: "enviar", tipo, mensagem }  -> registra o evento e envia ao Telegram (se vinculado)
//   { acao: "localizacao", tipo, mensagem, lat, lon, data?, endereco?, idade_gps_s?, origem? }
//       -> envia o texto e o pino do mapa (onde parei o carro) e grava no histórico civic_estacionamentos

const URL_BASE = Deno.env.get("SUPABASE_URL")!;
const CHAVE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const BOT = Deno.env.get("TELEGRAM_BOT_TOKEN") ?? "";

const cabecalhosDb = { apikey: CHAVE, Authorization: `Bearer ${CHAVE}`, "Content-Type": "application/json" };

function resposta(corpo: unknown, status = 200) {
  return new Response(JSON.stringify(corpo), { status, headers: { "Content-Type": "application/json" } });
}

async function dispositivo(token: string) {
  const r = await fetch(`${URL_BASE}/rest/v1/civic_dispositivos?token=eq.${encodeURIComponent(token)}&select=*`, { headers: cabecalhosDb });
  const lista = await r.json();
  return Array.isArray(lista) && lista.length ? lista[0] : null;
}

async function telegram(metodo: string, corpo: unknown) {
  const r = await fetch(`https://api.telegram.org/bot${BOT}/${metodo}`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(corpo),
  });
  return await r.json();
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return resposta({ erro: "use POST" }, 405);
  const token = req.headers.get("x-civic-token") ?? "";
  if (token.length < 32) return resposta({ erro: "token inválido" }, 401);
  const disp = await dispositivo(token);
  if (!disp) return resposta({ erro: "aparelho não cadastrado" }, 401);

  let corpo: Record<string, unknown> = {};
  try { corpo = await req.json(); } catch { /* corpo vazio */ }
  const acao = String(corpo.acao ?? "");

  if (!BOT) return resposta({ configurado: false, erro: "bot do Telegram não configurado (segredo TELEGRAM_BOT_TOKEN)" });

  if (acao === "info") {
    const eu = await telegram("getMe", {});
    return resposta({ configurado: true, bot: eu?.result?.username ?? null, vinculado: !!disp.telegram_chat_id, codigo: disp.codigo_vinculo });
  }

  // Liga o webhook do bot (comandos /onde, /status...) e o menu de comandos. O app chama isto quando está vinculado.
  if (acao === "registrar_bot") {
    const h = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(BOT + "|civic-bot"));
    const segredo = Array.from(new Uint8Array(h)).map((b) => b.toString(16).padStart(2, "0")).join("");
    const r = await telegram("setWebhook", { url: `${URL_BASE}/functions/v1/civic-bot`, secret_token: segredo, allowed_updates: ["message"] });
    await telegram("setMyCommands", { commands: [
      { command: "onde", description: "Onde o carro está ou onde parou" },
      { command: "status", description: "Resumo do carro agora" },
      { command: "bateria", description: "Tensão e saúde da bateria" },
      { command: "consumo", description: "Consumo, custo e abastecimentos" },
      { command: "manutencao", description: "Itens vencidos e próximos" },
      { command: "resumo", description: "Resumo dos últimos 7 dias" },
      { command: "ajuda", description: "Lista de comandos" },
    ] });
    return resposta({ registrado: !!r?.ok, descricao: r?.description ?? null });
  }

  // Preço médio da ANP (gasolina, aditivada e etanol) do município; se não houver, a média do estado.
  if (acao === "preco_anp") {
    const norm = (t: unknown) => String(t ?? "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toUpperCase().trim();
    const mun = norm(corpo.municipio), uf = norm(corpo.estado);
    if (!uf) return resposta({ erro: "informe o estado" }, 400);
    const buscar = async (m: string) => {
      const r = await fetch(`${URL_BASE}/rest/v1/civic_precos_anp?municipio=eq.${encodeURIComponent(m)}&uf=eq.${encodeURIComponent(uf)}&order=semana_inicio.desc&limit=6&select=*`, { headers: cabecalhosDb });
      const j = await r.json();
      if (!Array.isArray(j) || !j.length) return null;
      return j.filter((x) => x.semana_inicio === j[0].semana_inicio);
    };
    let linhas = mun ? await buscar(mun) : null;
    let fonte = "municipio";
    if (!linhas) { linhas = await buscar("(ESTADO)"); fonte = "estado"; }
    if (!linhas) return resposta({ encontrado: false });
    const p = (nome: string) => linhas!.find((x) => x.produto === nome)?.preco_medio ?? null;
    return resposta({ encontrado: true, fonte, local: fonte === "municipio" ? mun : uf, semana_inicio: linhas[0].semana_inicio, semana_fim: linhas[0].semana_fim,
      gasolina: p("GASOLINA COMUM"), aditivada: p("GASOLINA ADITIVADA"), etanol: p("ETANOL HIDRATADO"), postos: linhas[0].postos });
  }

  if (acao === "vincular") {
    // Com o webhook do bot ligado, a vinculação acontece no civic-bot quando o código chega; aqui só conferimos.
    if (disp.telegram_chat_id) return resposta({ vinculado: true });
    const atualizacoes = await telegram("getUpdates", { limit: 100, timeout: 0 });
    if (atualizacoes && atualizacoes.ok === false) return resposta({ vinculado: false, motivo: "envie o código ao bot e toque em Vincular de novo" });
    const codigo = String(disp.codigo_vinculo).toUpperCase();
    // deno-lint-ignore no-explicit-any
    const achou = (atualizacoes?.result ?? []).reverse().find((u: any) =>
      String(u?.message?.text ?? "").toUpperCase().includes(codigo));
    if (!achou) return resposta({ vinculado: false, motivo: "código não encontrado; envie o código para o bot e tente de novo" });
    const chat = achou.message.chat.id;
    await fetch(`${URL_BASE}/rest/v1/civic_dispositivos?token=eq.${encodeURIComponent(token)}`, {
      method: "PATCH", headers: cabecalhosDb, body: JSON.stringify({ telegram_chat_id: chat }),
    });
    await telegram("sendMessage", { chat_id: chat, text: "✅ Civic Renatin vinculado! Você vai receber aqui os avisos do carro." });
    return resposta({ vinculado: true });
  }

  if (acao === "enviar") {
    const tipo = String(corpo.tipo ?? "aviso").slice(0, 40);
    const mensagem = String(corpo.mensagem ?? "").slice(0, 1000);
    if (!mensagem) return resposta({ erro: "mensagem vazia" }, 400);
    let enviado = false;
    if (disp.telegram_chat_id) {
      const r = await telegram("sendMessage", { chat_id: disp.telegram_chat_id, text: mensagem });
      enviado = !!r?.ok;
    }
    await fetch(`${URL_BASE}/rest/v1/civic_eventos`, {
      method: "POST", headers: cabecalhosDb, body: JSON.stringify({ dispositivo: token, tipo, mensagem, enviado }),
    });
    return resposta({ enviado, vinculado: !!disp.telegram_chat_id });
  }

  if (acao === "localizacao") {
    const lat = Number(corpo.lat);
    const lon = Number(corpo.lon);
    if (!Number.isFinite(lat) || !Number.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
      return resposta({ erro: "coordenadas inválidas" }, 400);
    }
    const tipo = String(corpo.tipo ?? "estacionamento").slice(0, 40);
    const mensagem = String(corpo.mensagem ?? "").slice(0, 1000);
    let enviado = false;
    if (disp.telegram_chat_id) {
      const r = await telegram("sendMessage", { chat_id: disp.telegram_chat_id, text: mensagem, disable_web_page_preview: true });
      await telegram("sendLocation", { chat_id: disp.telegram_chat_id, latitude: lat, longitude: lon });
      enviado = !!r?.ok;
    }
    await fetch(`${URL_BASE}/rest/v1/civic_eventos`, {
      method: "POST", headers: cabecalhosDb, body: JSON.stringify({ dispositivo: token, tipo, mensagem, enviado }),
    });
    // Só o estacionamento (auto, manual ou simulado pela demonstração) entra no histórico; colisão e uso incomum só avisam.
    if (!["auto", "manual", "simulado"].includes(String(corpo.origem))) return resposta({ enviado, vinculado: !!disp.telegram_chat_id, historico: false });
    // Histórico de estacionamentos (alimenta o dashboard da Casa Tech) na mesma chamada do Telegram.
    const quando = Number(corpo.data);
    const idade = Number(corpo.idade_gps_s);
    const gravou = await fetch(`${URL_BASE}/rest/v1/civic_estacionamentos?on_conflict=dispositivo,data`, {
      method: "POST", headers: { ...cabecalhosDb, Prefer: "resolution=ignore-duplicates" }, // reenvio do mesmo aviso não duplica
      body: JSON.stringify({
        dispositivo: token, lat, lon,
        data: Number.isFinite(quando) && quando > 0 ? new Date(quando).toISOString() : new Date().toISOString(),
        endereco: corpo.endereco ? String(corpo.endereco).slice(0, 300) : null,
        idade_gps_s: Number.isFinite(idade) && idade >= 0 ? Math.round(idade) : null,
        origem: ["manual", "simulado"].includes(String(corpo.origem)) ? String(corpo.origem) : "auto",
      }),
    }).then((x) => x.ok).catch(() => false);
    return resposta({ enviado, vinculado: !!disp.telegram_chat_id, historico: gravou });
  }

  return resposta({ erro: "ação desconhecida" }, 400);
});
