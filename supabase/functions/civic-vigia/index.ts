// Civic Renatin — vigia do silêncio (chamada pelo pg_cron a cada minuto).
// A multimídia apaga na hora em que a chave é desligada: sem energia e sem internet, ela não consegue avisar onde o carro ficou.
// Enquanto anda, o carro manda posição e status (e, ao parar, o endereço resolvido no aparelho). Se o carro para de dar sinal
// parado, com o motor ligado no último status, esta função avisa no Telegram onde ele ficou e grava a vaga (origem "nuvem").
// O aviso atrasado que a multimídia manda ao religar é reconhecido como repetido pela civic-notificar.

const URL_BASE = Deno.env.get("SUPABASE_URL")!;
const CHAVE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const BOT = Deno.env.get("TELEGRAM_BOT_TOKEN") ?? "";
const db = { apikey: CHAVE, Authorization: `Bearer ${CHAVE}`, "Content-Type": "application/json" };
const SILENCIO_MS = 3 * 60_000;      // sem sinal há mais de 3 min...
const MAX_MS = 3 * 3600_000;          // ...e há menos de 3 h (mais velho que isso, não é mais "acabou de estacionar")

// deno-lint-ignore no-explicit-any
async function consulta(caminho: string): Promise<any[]> {
  const r = await fetch(`${URL_BASE}/rest/v1/${caminho}`, { headers: db });
  const j = await r.json().catch(() => null);
  return Array.isArray(j) ? j : [];
}

async function telegram(metodo: string, corpo: unknown) {
  const r = await fetch(`https://api.telegram.org/bot${BOT}/${metodo}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(corpo) });
  return await r.json().catch(() => null);
}

function distancia(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const r = 6371000, p = Math.PI / 180;
  const a = Math.sin((lat2 - lat1) * p / 2) ** 2 + Math.cos(lat1 * p) * Math.cos(lat2 * p) * Math.sin((lon2 - lon1) * p / 2) ** 2;
  return 2 * r * Math.asin(Math.sqrt(a));
}

const hora = (iso: string) => new Date(iso).toLocaleTimeString("pt-BR", { timeZone: "America/Sao_Paulo", hour: "2-digit", minute: "2-digit" });

async function marcar(tk: string) {
  await fetch(`${URL_BASE}/rest/v1/civic_status?dispositivo=eq.${encodeURIComponent(tk)}`, { method: "PATCH", headers: db, body: JSON.stringify({ vaga_avisada_em: new Date().toISOString() }) });
}

async function vigiar(): Promise<number> {
  const agora = Date.now();
  let avisos = 0;
  const disps = await consulta("civic_dispositivos?telegram_chat_id=not.is.null&select=token,telegram_chat_id");
  for (const d of disps) {
    const tk = encodeURIComponent(d.token);
    const st = (await consulta(`civic_status?dispositivo=eq.${tk}&select=*`))[0];
    if (!st || !st.motor_ligado || st.lat == null || st.lon == null) continue;
    const t = Date.parse(st.atualizado_em);
    if (!(agora - t >= SILENCIO_MS && agora - t <= MAX_MS)) continue;
    if (st.vaga_avisada_em && Date.parse(st.vaga_avisada_em) >= t) continue;
    // A posição mais recente: o último ponto do trajeto, se for mais novo que o status.
    let lat = Number(st.lat), lon = Number(st.lon), quando = st.atualizado_em as string, vel = st.vel;
    const p = (await consulta(`civic_posicoes?dispositivo=eq.${tk}&select=lat,lon,em,vel&order=em.desc&limit=1`))[0];
    if (p && Date.parse(p.em) > t) { lat = Number(p.lat); lon = Number(p.lon); quando = p.em; vel = p.vel; }
    // Sumiu andando: mais provável que tenha perdido a internet do que estacionado.
    if (vel != null && Number(vel) > 8) continue;
    // O próprio aparelho já avisou essa vaga (ex.: botão Estacionado)?
    const desde = new Date(t - 30 * 60_000).toISOString();
    const ja = await consulta(`civic_estacionamentos?dispositivo=eq.${tk}&data=gte.${desde}&select=lat,lon`);
    if (ja.some((v) => distancia(Number(v.lat), Number(v.lon), lat, lon) < 150)) { await marcar(d.token); continue; }
    const endereco = st.endereco && distancia(Number(st.lat), Number(st.lon), lat, lon) < 120 ? String(st.endereco) : null;
    const texto = `🅿️ Carro estacionado (a multimídia desligou por volta das ${hora(quando)})\n📍 ${lat.toFixed(5)}, ${lon.toFixed(5)}` +
      (endereco ? `\n🏠 ${endereco}` : "") + `\n🗺️ https://maps.google.com/?q=${lat.toFixed(6)},${lon.toFixed(6)}` +
      `\nℹ️ Aviso da nuvem: o carro parou de dar sinal parado neste ponto.`;
    const r = await telegram("sendMessage", { chat_id: d.telegram_chat_id, text: texto, disable_web_page_preview: true });
    await telegram("sendLocation", { chat_id: d.telegram_chat_id, latitude: lat, longitude: lon });
    await fetch(`${URL_BASE}/rest/v1/civic_estacionamentos?on_conflict=dispositivo,data`, {
      method: "POST", headers: { ...db, Prefer: "resolution=ignore-duplicates" },
      body: JSON.stringify({ dispositivo: d.token, lat, lon, data: new Date(quando).toISOString(), endereco, idade_gps_s: 0, origem: "nuvem", desconhecido: false }),
    });
    await fetch(`${URL_BASE}/rest/v1/civic_eventos`, {
      method: "POST", headers: db,
      body: JSON.stringify({ dispositivo: d.token, tipo: "estacionamento", mensagem: texto, enviado: !!r?.ok, fonte: "nuvem", ocorreu_em: new Date(quando).toISOString() }),
    });
    await marcar(d.token);
    avisos++;
  }
  return avisos;
}

Deno.serve(async (req) => {
  const cron = req.headers.get("x-civic-cron");
  if (req.method !== "POST" || !cron) return new Response("proibido", { status: 403 });
  const segredo = (await consulta("civic_config?chave=eq.cron_secret&select=valor"))[0]?.valor;
  if (!segredo || segredo !== cron) return new Response("proibido", { status: 403 });
  if (!BOT) return new Response(JSON.stringify({ erro: "bot não configurado" }), { status: 200 });
  const avisos = await vigiar();
  return new Response(JSON.stringify({ ok: true, avisos }), { headers: { "Content-Type": "application/json" } });
});
