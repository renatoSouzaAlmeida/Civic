// Civic Renatin — comandos do bot do Telegram (/onde, /status, /bateria, /consumo, /manutencao, /ajuda).
// O Telegram chama esta função (webhook). A origem é conferida pelo cabeçalho secreto, e só responde a chats
// já vinculados a um aparelho (civic_dispositivos.telegram_chat_id). Também faz a vinculação pelo código CIVIC-XXXXX,
// porque com o webhook ligado o Telegram não entrega mais mensagens pelo getUpdates.

const URL_BASE = Deno.env.get("SUPABASE_URL")!;
const CHAVE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const BOT = Deno.env.get("TELEGRAM_BOT_TOKEN") ?? "";
const db = { apikey: CHAVE, Authorization: `Bearer ${CHAVE}`, "Content-Type": "application/json" };

async function segredo(): Promise<string> {
  const h = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(BOT + "|civic-bot"));
  return Array.from(new Uint8Array(h)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

// deno-lint-ignore no-explicit-any
async function consulta(caminho: string): Promise<any[]> {
  const r = await fetch(`${URL_BASE}/rest/v1/${caminho}`, { headers: db });
  const j = await r.json();
  return Array.isArray(j) ? j : [];
}

async function telegram(metodo: string, corpo: unknown) {
  const r = await fetch(`https://api.telegram.org/bot${BOT}/${metodo}`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(corpo),
  });
  return await r.json();
}

const responder = (chat: number, texto: string) => telegram("sendMessage", { chat_id: chat, text: texto, disable_web_page_preview: true });
const fmtData = (iso: string) => new Date(iso).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo", day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" });
const num = (v: unknown, casas = 1) => (v === null || v === undefined ? "—" : Number(v).toFixed(casas).replace(".", ","));
function ha(iso: string): string {
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  return s < 90 ? `há ${Math.round(s)} s` : s < 5400 ? `há ${Math.round(s / 60)} min` : s < 172800 ? `há ${Math.round(s / 3600)} h` : `há ${Math.round(s / 86400)} dias`;
}

const AJUDA = "🚗 Comandos do Civic Renatin:\n/onde — onde o carro está ou onde parou\n/status — resumo do carro agora\n/bateria — tensão e saúde da bateria\n/consumo — consumo, custo e abastecimentos\n/manutencao — itens vencidos e próximos";

Deno.serve(async (req) => {
  if (req.method !== "POST" || !BOT) return new Response("ok");
  if (req.headers.get("x-telegram-bot-api-secret-token") !== (await segredo())) return new Response("proibido", { status: 403 });
  // deno-lint-ignore no-explicit-any
  let upd: any = {};
  try { upd = await req.json(); } catch { return new Response("ok"); }
  const msg = upd?.message;
  if (!msg?.text) return new Response("ok");
  const chat: number = msg.chat.id;
  const texto = String(msg.text).trim();

  try {
    // Vinculação: o usuário manda o código CIVIC-XXXXX para o bot.
    const achouCodigo = texto.toUpperCase().match(/CIVIC-[A-Z0-9]{5}/);
    if (achouCodigo) {
      const alvo = await consulta(`civic_dispositivos?codigo_vinculo=eq.${achouCodigo[0]}&select=token`);
      if (alvo.length) {
        await fetch(`${URL_BASE}/rest/v1/civic_dispositivos?token=eq.${alvo[0].token}`, { method: "PATCH", headers: db, body: JSON.stringify({ telegram_chat_id: chat }) });
        await responder(chat, "✅ Civic Renatin vinculado! Você vai receber aqui os avisos do carro.\n\n" + AJUDA);
      } else await responder(chat, "Código não encontrado. Confira o código em Config. → Avisos no celular.");
      return new Response("ok");
    }

    const cmd = texto.split(/[\s@]/)[0].toLowerCase();
    const aparelhos = await consulta(`civic_dispositivos?telegram_chat_id=eq.${chat}&select=token,nome`);
    if (!aparelhos.length) { await responder(chat, "Este chat ainda não está vinculado a um carro. Envie o código CIVIC-XXXXX que aparece em Config. → Avisos no celular."); return new Response("ok"); }
    const tokens = aparelhos.map((a) => a.token).join(",");
    // Se houver mais de um aparelho vinculado, usa o que falou com a nuvem por último.
    const status = (await consulta(`civic_status?dispositivo=in.(${tokens})&select=*&order=atualizado_em.desc&limit=1`))[0];
    const tk: string = status?.dispositivo ?? aparelhos[0].token;
    const nome = aparelhos.find((a) => a.token === tk)?.nome ?? "Civic";

    if (cmd === "/start" || cmd === "/ajuda" || cmd === "/help") { await responder(chat, AJUDA); return new Response("ok"); }

    if (cmd === "/onde") {
      if (status?.motor_ligado && status.lat != null) {
        await responder(chat, `📍 O carro está ligado (${num(status.vel, 0)} km/h), posição ${ha(status.atualizado_em)}.\n🗺️ https://maps.google.com/?q=${status.lat},${status.lon}`);
        await telegram("sendLocation", { chat_id: chat, latitude: status.lat, longitude: status.lon });
      } else {
        const v = (await consulta(`civic_estacionamentos?dispositivo=eq.${tk}&order=data.desc&limit=1&select=*`))[0];
        if (!v) await responder(chat, "Ainda não há vaga registrada.");
        else {
          await responder(chat, `🅿️ Parado desde ${fmtData(v.data)} (${ha(v.data)})${v.endereco ? "\n" + v.endereco : ""}${v.idade_gps_s > 600 ? "\n⚠️ A posição pode estar defasada (GPS de " + Math.round(v.idade_gps_s / 60) + " min antes)." : ""}\n🗺️ https://maps.google.com/?q=${v.lat},${v.lon}`);
          await telegram("sendLocation", { chat_id: chat, latitude: v.lat, longitude: v.lon });
        }
      }
    } else if (cmd === "/status") {
      const e = (await consulta(`civic_estados?dispositivo=eq.${tk}&order=registrado_em.desc&limit=1&select=*`))[0];
      const m = await consulta(`civic_manutencao?dispositivo=eq.${tk}&situacao=in.(Vencido,Proximo)&select=situacao`);
      const online = status && Date.now() - new Date(status.atualizado_em).getTime() < (status.heartbeat_s ?? 60) * 2500;
      const dtc = e?.dtc?.length ? e.dtc.join(", ") : "nenhuma";
      await responder(chat,
        `🚗 ${nome}\n${online ? "🟢 Online" : "⚪ Offline"}${status ? " · " + ha(status.atualizado_em) : ""}\n` +
        `Motor: ${status?.motor_ligado ? "ligado, " + num(status.vel, 0) + " km/h" : "desligado"}\n` +
        `Bateria: ${num(status?.tensao ?? e?.tensao)} V${e?.bateria_avaliacao ? " · " + e.bateria_avaliacao : ""}\n` +
        `Combustível: ${e?.nivel_combustivel != null ? e.nivel_combustivel + "%" : "—"}\n` +
        `Falhas: ${dtc}${e?.luz_motor ? " (luz de injeção acesa)" : ""}\n` +
        `Manutenção: ${m.filter((x) => x.situacao === "Vencido").length} vencido(s), ${m.filter((x) => x.situacao === "Proximo").length} próximo(s)` +
        (e ? `\nÚltimo uso: ${fmtData(e.registrado_em)}` : ""));
    } else if (cmd === "/bateria") {
      const e = (await consulta(`civic_estados?dispositivo=eq.${tk}&order=registrado_em.desc&limit=1&select=*`))[0];
      await responder(chat,
        `🔋 Bateria agora: ${num(status?.tensao ?? e?.tensao)} V\n` +
        (e?.bateria_avaliacao ? `Última partida: ${num(e.bateria_repouso)} V em repouso, mínima ${num(e.bateria_minima)} V · ${e.bateria_avaliacao}\n` : "Sem partida registrada ainda.\n") +
        (e?.dreno_v_h != null ? `Queda parado: ${num(Number(e.dreno_v_h) * 1000, 0)} mV/h${Number(e.dreno_v_h) >= 0.02 ? " ⚠️ acima do normal" : ""}` : ""));
    } else if (cmd === "/consumo") {
      const a = await consulta(`civic_abastecimentos_analise?dispositivo=eq.${tk}&order=km.desc&limit=8&select=*`);
      if (!a.length) await responder(chat, "Ainda não há abastecimentos registrados.");
      else {
        const cons = a.map((x) => Number(x.consumo_km_l)).filter((x) => x > 0);
        const custos = a.map((x) => Number(x.custo_por_km)).filter((x) => x > 0);
        const mes = new Date().getMonth();
        const gasto = a.filter((x) => new Date(x.data).getMonth() === mes && x.total_pago != null).reduce((s, x) => s + Number(x.total_pago), 0);
        const media = (v: number[]) => (v.length ? v.reduce((s, x) => s + x, 0) / v.length : null);
        await responder(chat,
          `⛽ Consumo médio: ${num(media(cons))} km/l${cons.length ? ` (último: ${num(cons[0])})` : ""}\n` +
          `Custo por km: ${custos.length ? "R$ " + num(media(custos), 2) : "—"}\n` +
          `Gasto no mês: R$ ${num(gasto, 2)}\n` +
          `Último abastecimento: ${fmtData(a[0].data)} · ${num(a[0].litros)} l${a[0].aguardando_valor ? " · aguardando o valor pago" : ""}\n` +
          `Abastecimentos registrados: ${a.length}${a.length === 8 ? "+" : ""}`);
      }
    } else if (cmd === "/manutencao") {
      const m = await consulta(`civic_manutencao?dispositivo=eq.${tk}&situacao=in.(Vencido,Proximo)&select=nome,situacao,ultimo_km,ultima_data&order=situacao`);
      await responder(chat, m.length ? "🛠️ Manutenção:\n" + m.map((x) => `${x.situacao === "Vencido" ? "🔴" : "🟡"} ${x.nome}${x.ultima_data ? " (última em " + fmtData(x.ultima_data).slice(0, 5) + ")" : ""}`).join("\n") : "🛠️ Nada vencido nem próximo. Tudo em dia.");
    } else {
      await responder(chat, "Não entendi. " + AJUDA);
    }
  } catch (_) {
    await responder(chat, "Não consegui consultar agora. Tente de novo em instantes.");
  }
  return new Response("ok");
});
