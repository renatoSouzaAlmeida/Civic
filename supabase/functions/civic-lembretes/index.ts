// Civic Renatin — avisa no Telegram os vencimentos cadastrados na Casa Tech (IPVA, licenciamento, seguro, CNH...).
// Chamada todo dia pelo pg_cron (cabeçalho x-civic-cron conferido em civic_config). Avisa quando faltam `avisar_dias` ou menos:
// a cada 7 dias até faltarem 3 dias e todo dia de 3 dias antes até 7 dias depois de vencido.

const URL_BASE = Deno.env.get("SUPABASE_URL")!;
const CHAVE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const BOT = Deno.env.get("TELEGRAM_BOT_TOKEN") ?? "";
const db = { apikey: CHAVE, Authorization: `Bearer ${CHAVE}`, "Content-Type": "application/json" };

// deno-lint-ignore no-explicit-any
async function consulta(caminho: string): Promise<any[]> {
  const r = await fetch(`${URL_BASE}/rest/v1/${caminho}`, { headers: db });
  const j = await r.json();
  return Array.isArray(j) ? j : [];
}

const brl = (v: number) => "R$ " + Number(v).toFixed(2).replace(".", ",");
const dm = (iso: string) => iso.slice(8, 10) + "/" + iso.slice(5, 7) + "/" + iso.slice(0, 4);

Deno.serve(async (req) => {
  const cron = req.headers.get("x-civic-cron") ?? "";
  if (req.method !== "POST" || !cron) return new Response("proibido", { status: 403 });
  const seg = await consulta("civic_config?chave=eq.cron_secret&select=valor");
  if (seg[0]?.valor !== cron) return new Response("proibido", { status: 403 });
  if (!BOT) return new Response("sem bot");

  const hoje = new Date(Date.now() - 3 * 3600 * 1000).toISOString().slice(0, 10); // horário de Brasília
  const lembretes = await consulta("civic_lembretes?feito=eq.false&removido=eq.false&select=*");
  const fallback = (await consulta("civic_dispositivos?telegram_chat_id=not.is.null&select=telegram_chat_id&limit=1"))[0]?.telegram_chat_id;
  let enviados = 0;
  for (const l of lembretes) {
    const dias = Math.round((Date.parse(l.vencimento) - Date.parse(hoje)) / 86400000);
    if (dias > l.avisar_dias || dias < -7) continue;
    const intervalo = dias <= 3 ? 1 : 7;
    if (l.ultimo_aviso && (Date.parse(hoje) - Date.parse(l.ultimo_aviso)) / 86400000 < intervalo) continue;
    const disp = (await consulta(`civic_dispositivos?token=eq.${l.dispositivo}&select=telegram_chat_id`))[0];
    const chat = disp?.telegram_chat_id ?? fallback;
    if (!chat) continue;
    const quando = dias < 0 ? `venceu há ${-dias} dia(s)` : dias === 0 ? "vence HOJE" : `vence em ${dias} dia(s) (${dm(l.vencimento)})`;
    const texto = `📅 ${l.titulo}: ${quando}.` + (l.valor ? ` Valor: ${brl(l.valor)}.` : "") + (l.observacao ? `\n${l.observacao}` : "") + "\nMarque como feito na Casa Tech.";
    const r = await fetch(`https://api.telegram.org/bot${BOT}/sendMessage`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ chat_id: chat, text: texto }) }).then((x) => x.json()).catch(() => null);
    if (r?.ok) {
      enviados++;
      await fetch(`${URL_BASE}/rest/v1/civic_lembretes?dispositivo=eq.${l.dispositivo}&id=eq.${l.id}`, { method: "PATCH", headers: db, body: JSON.stringify({ ultimo_aviso: hoje }) });
    }
  }
  return new Response(JSON.stringify({ lembretes: lembretes.length, enviados }), { headers: { "Content-Type": "application/json" } });
});
