// Civic Renatin — atualiza os preços médios da ANP (levantamento semanal de preços de combustíveis).
// Chamada todo dia pelo agendador do banco (pg_cron). Baixa a planilha mais recente de "resumo semanal"
// (gov.br/anp), lê as abas MUNICIPIOS e ESTADOS e grava gasolina comum, gasolina aditivada e etanol em civic_precos_anp.
// Autenticação: cabeçalho x-civic-cron igual ao segredo guardado em civic_config.

import { unzipSync } from "npm:fflate@0.8.2";

const URL_BASE = Deno.env.get("SUPABASE_URL")!;
const CHAVE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const db = { apikey: CHAVE, Authorization: `Bearer ${CHAVE}`, "Content-Type": "application/json" };
const PAGINA = "https://www.gov.br/anp/pt-br/assuntos/precos-e-defesa-da-concorrencia/precos/levantamento-de-precos-de-combustiveis-ultimas-semanas-pesquisadas";
const PRODUTOS = new Set(["GASOLINA COMUM", "GASOLINA ADITIVADA", "ETANOL HIDRATADO"]);

async function cronOk(req: Request): Promise<boolean> {
  const h = req.headers.get("x-civic-cron");
  if (!h) return false;
  const r = await fetch(`${URL_BASE}/rest/v1/civic_config?chave=eq.cron_secret&select=valor`, { headers: db });
  const j = await r.json();
  return Array.isArray(j) && j[0]?.valor === h;
}

const texto = (b: Uint8Array) => new TextDecoder().decode(b);
const serial = (n: number) => new Date(Date.UTC(1899, 11, 30) + n * 86400000).toISOString().slice(0, 10);
const colunaDe = (ref: string) => { let n = 0; for (const ch of ref.replace(/\d/g, "")) n = n * 26 + ch.charCodeAt(0) - 64; return n - 1; };

function lerPlanilha(xml: string, ss: string[]): string[][] {
  const linhas: string[][] = [];
  for (const r of xml.matchAll(/<row [^>]*>([\s\S]*?)<\/row>/g)) {
    const cel: string[] = [];
    for (const c of r[1].matchAll(/<c ([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
      const ref = /r="([A-Z]+\d+)"/.exec(c[1])?.[1] ?? "";
      const t = /t="(\w+)"/.exec(c[1])?.[1];
      const v = /<v>([\s\S]*?)<\/v>/.exec(c[2] ?? "")?.[1] ?? "";
      cel[colunaDe(ref)] = t === "s" ? (ss[Number(v)] ?? "") : v;
    }
    linhas.push(cel);
  }
  return linhas;
}

Deno.serve(async (req) => {
  if (!(await cronOk(req))) return new Response("proibido", { status: 403 });
  const html = await (await fetch(PAGINA)).text();
  const link = [...html.matchAll(/href="([^"]*resumo_semanal_lpc[^"]*\.xlsx)"/gi)].map((m) => m[1])[0];
  if (!link) return Response.json({ erro: "link da planilha não encontrado" }, { status: 502 });

  const buf = new Uint8Array(await (await fetch(link)).arrayBuffer());
  const zip = unzipSync(buf);
  const ss: string[] = [];
  for (const m of texto(zip["xl/sharedStrings.xml"]).matchAll(/<si>([\s\S]*?)<\/si>/g)) {
    ss.push([...m[1].matchAll(/<t[^>]*>([\s\S]*?)<\/t>/g)].map((x) => x[1]).join("").replaceAll("&amp;", "&"));
  }
  const wb = texto(zip["xl/workbook.xml"]);
  const rels = texto(zip["xl/_rels/workbook.xml.rels"]);
  const alvo = (id: string) => /Target="([^"]+)"/.exec([...rels.matchAll(/<Relationship [^>]*>/g)].map((x) => x[0]).find((x) => x.includes(`Id="${id}"`)) ?? "")?.[1];
  const aba = (nome: string) => {
    const tag = [...wb.matchAll(/<sheet [^>]*>/g)].map((m) => m[0]).find((t) => t.includes(`name="${nome}"`));
    const id = /r:id="([^"]+)"/.exec(tag ?? "")?.[1];
    const t = id ? alvo(id) : undefined;
    return t ? zip[("xl/" + t.replace(/^\/?(xl\/)?/, ""))] : undefined;
  };

  const linhasBanco: Record<string, unknown>[] = [];
  const municipios = aba("MUNICIPIOS");
  const estados = aba("ESTADOS");
  if (!municipios) return Response.json({ erro: "aba MUNICIPIOS não encontrada" }, { status: 502 });
  const add = (l: string[], uf: string, mun: string, prodIdx: number, postosIdx: number) => {
    const produto = (l[prodIdx] ?? "").trim();
    if (!PRODUTOS.has(produto)) return;
    const medio = Number(l[postosIdx + 2]);
    if (!Number.isFinite(medio) || medio <= 0) return;
    linhasBanco.push({
      uf, municipio: mun, produto, semana_inicio: serial(Number(l[0])), semana_fim: serial(Number(l[1])),
      preco_medio: medio, preco_min: Number(l[postosIdx + 4]) || null, preco_max: Number(l[postosIdx + 5]) || null, postos: Number(l[postosIdx]) || null,
    });
  };
  // MUNICIPIOS: DATA INICIAL, DATA FINAL, ESTADO, MUNICÍPIO, PRODUTO, POSTOS, UNIDADE, MÉDIO, DESVIO, MÍN, MÁX
  for (const l of lerPlanilha(texto(municipios), ss)) if (/^\d+$/.test(l[0] ?? "")) add(l, (l[2] ?? "").trim(), (l[3] ?? "").trim(), 4, 5);
  // ESTADOS: DATA INICIAL, DATA FINAL, REGIAO, ESTADO, PRODUTO, POSTOS, UNIDADE, MÉDIO, DESVIO, MÍN, MÁX
  if (estados) for (const l of lerPlanilha(texto(estados), ss)) if (/^\d+$/.test(l[0] ?? "")) add(l, (l[3] ?? "").trim(), "(ESTADO)", 4, 5);
  if (!linhasBanco.length) return Response.json({ erro: "nenhuma linha lida" }, { status: 502 });

  let gravadas = 0;
  for (let i = 0; i < linhasBanco.length; i += 500) {
    const r = await fetch(`${URL_BASE}/rest/v1/civic_precos_anp?on_conflict=uf,municipio,produto,semana_inicio`, {
      method: "POST", headers: { ...db, Prefer: "resolution=merge-duplicates,return=minimal" }, body: JSON.stringify(linhasBanco.slice(i, i + 500)),
    });
    if (r.ok) gravadas += Math.min(500, linhasBanco.length - i);
  }
  const semana = String(linhasBanco[0].semana_inicio);
  const corte = new Date(Date.parse(semana) - 60 * 86400000).toISOString().slice(0, 10);
  await fetch(`${URL_BASE}/rest/v1/civic_precos_anp?semana_inicio=lt.${corte}`, { method: "DELETE", headers: db });
  return Response.json({ semana, linhas: linhasBanco.length, gravadas, arquivo: link });
});
