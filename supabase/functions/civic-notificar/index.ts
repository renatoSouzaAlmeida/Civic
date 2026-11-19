// Civic Renatin — envia notificações do carro para o Telegram.
// A autenticação é feita pelo token do aparelho (cabeçalho x-civic-token), conferido na tabela
// civic_dispositivos. O token do bot fica no segredo TELEGRAM_BOT_TOKEN (nunca no app, que é público).
//
// Ações (POST JSON):
//   { acao: "info" }                    -> nome do bot e se o aparelho já está vinculado
//   { acao: "vincular" }                -> procura o código do aparelho nas mensagens do bot e grava o chat
//   { acao: "enviar", tipo, mensagem }  -> registra o evento e envia ao Telegram (se vinculado)

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

  if (acao === "vincular") {
    const atualizacoes = await telegram("getUpdates", { limit: 100, timeout: 0 });
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

  return resposta({ erro: "ação desconhecida" }, 400);
});
