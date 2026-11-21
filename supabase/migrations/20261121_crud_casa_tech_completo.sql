-- ===== Aplicada em 21/11/2026 como "civic_crud_casa_tech_completo" =====
-- CRUD completo da Casa Tech, por veículo (ver docs/DASHBOARD-CASA-TECH.md, "Entrada de dados na Casa Tech").
-- Depende de public.civic_marcar_atualizacao() (migration 20261121_civic_viagens.sql).

-- 1) Configuração do veículo (a Casa Tech escreve "valores"; o app escreve o retrato "valores_app" e lê "valores").
create table public.civic_configuracao (
  dispositivo text primary key references public.civic_dispositivos(token) on delete cascade,
  valores jsonb not null default '{}'::jsonb,
  valores_app jsonb not null default '{}'::jsonb,
  atualizado_em timestamptz not null default now(),
  app_em timestamptz
);
create or replace function public.civic_config_validar() returns trigger language plpgsql set search_path = '' as $$
declare k text; v jsonb;
  permitidas text[] := array['nome_carro','modelo','ano','placa','imagem_carro','capacidade_tanque','endereco_casa','endereco_trabalho','mapa','limite_velocidade','vigia_total','voz_alertas','tpms_ideal_psi','geofence_telegram','geofence_faixa'];
begin
  for k, v in select * from jsonb_each(new.valores) loop
    if not (k = any(permitidas)) then raise exception 'chave de configuração não permitida: %', k; end if;
    if k in ('vigia_total','voz_alertas','geofence_telegram','geofence_faixa') and jsonb_typeof(v) <> 'boolean' then raise exception '% deve ser verdadeiro/falso', k; end if;
    if k in ('capacidade_tanque','limite_velocidade','tpms_ideal_psi') and (jsonb_typeof(v) <> 'number' or (v #>> '{}')::numeric < 0 or (v #>> '{}')::numeric > 300) then raise exception '% deve ser um número entre 0 e 300', k; end if;
    if k = 'imagem_carro' and (v #>> '{}') not in ('Civic','Fit') then raise exception 'imagem_carro deve ser Civic ou Fit'; end if;
    if k = 'mapa' and (v #>> '{}') not in ('Waze','GoogleMaps') then raise exception 'mapa deve ser Waze ou GoogleMaps'; end if;
    if k in ('nome_carro','modelo','ano','placa','endereco_casa','endereco_trabalho') and (jsonb_typeof(v) <> 'string' or length(v #>> '{}') > 200) then raise exception '% deve ser texto de até 200 caracteres', k; end if;
  end loop;
  if tg_op = 'UPDATE' and new.valores is distinct from old.valores then new.atualizado_em := clock_timestamp(); end if;
  return new;
end $$;
create trigger civic_config_validar_t before insert or update on public.civic_configuracao for each row execute function public.civic_config_validar();
alter table public.civic_configuracao enable row level security;
create policy civic_config_app on public.civic_configuracao for all to anon using (dispositivo = public.civic_token()) with check (dispositivo = public.civic_token());
create policy civic_config_dash on public.civic_configuracao for all to authenticated
  using (dispositivo in (select public.civic_meus_dispositivos())) with check (dispositivo in (select public.civic_meus_dispositivos()));

-- 2) Contatos do SOS.
create table public.civic_contatos_sos (
  dispositivo text not null references public.civic_dispositivos(token) on delete cascade,
  id text not null,
  nome text not null check (length(nome) between 1 and 80),
  telefone text not null check (telefone ~ '^\+?[0-9]{8,15}$'),
  ativo boolean not null default true,
  removido boolean not null default false,
  atualizado_em timestamptz not null default now(),
  primary key (dispositivo, id)
);
create trigger civic_contato_marcar before update on public.civic_contatos_sos for each row execute function public.civic_marcar_atualizacao();
alter table public.civic_contatos_sos enable row level security;
create policy civic_contato_app on public.civic_contatos_sos for all to anon using (dispositivo = public.civic_token()) with check (dispositivo = public.civic_token());
create policy civic_contato_dash on public.civic_contatos_sos for all to authenticated
  using (dispositivo in (select public.civic_meus_dispositivos())) with check (dispositivo in (select public.civic_meus_dispositivos()));

-- 3) Áreas do geofence: a Casa Tech também cria, edita e remove (o app puxa).
create trigger civic_area_marcar before update on public.civic_areas for each row execute function public.civic_marcar_atualizacao();
alter table public.civic_areas add constraint civic_area_raio_ok check (raio_m between 20 and 50000) not valid;
create policy civic_dash_areas_escrever on public.civic_areas for all to authenticated
  using (dispositivo in (select public.civic_meus_dispositivos())) with check (dispositivo in (select public.civic_meus_dispositivos()));

-- 4) Despesas do veículo (só Casa Tech).
create table public.civic_despesas (
  dispositivo text not null references public.civic_dispositivos(token) on delete cascade,
  id bigint not null default (extract(epoch from clock_timestamp()) * 1000)::bigint,
  data date not null,
  categoria text not null check (categoria in ('seguro','ipva','licenciamento','multa','pedagio','estacionamento','lavagem','pneus','acessorio','outros')),
  descricao text,
  valor numeric(10,2) not null check (valor > 0 and valor < 1000000),
  km integer check (km is null or km >= 0),
  observacao text,
  removido boolean not null default false,
  atualizado_em timestamptz not null default now(),
  primary key (dispositivo, id)
);
create trigger civic_despesa_marcar before update on public.civic_despesas for each row execute function public.civic_marcar_atualizacao();
alter table public.civic_despesas enable row level security;
create policy civic_despesa_dash on public.civic_despesas for all to authenticated
  using (dispositivo in (select public.civic_meus_dispositivos())) with check (dispositivo in (select public.civic_meus_dispositivos()));

-- 5) Vencimentos e lembretes, com aviso no Telegram (função civic-lembretes, todo dia às 8h de Brasília via pg_cron).
create table public.civic_lembretes (
  dispositivo text not null references public.civic_dispositivos(token) on delete cascade,
  id bigint not null default (extract(epoch from clock_timestamp()) * 1000)::bigint,
  titulo text not null check (length(titulo) between 1 and 120),
  tipo text not null default 'outro' check (tipo in ('ipva','licenciamento','seguro','revisao','cnh','multa','outro')),
  vencimento date not null,
  avisar_dias integer not null default 15 check (avisar_dias between 0 and 365),
  valor numeric(10,2) check (valor is null or valor >= 0),
  repetir_meses integer check (repetir_meses is null or repetir_meses between 1 and 120),
  feito boolean not null default false,
  ultimo_aviso date,
  observacao text,
  removido boolean not null default false,
  atualizado_em timestamptz not null default now(),
  primary key (dispositivo, id)
);
create or replace function public.civic_lembrete_renovar() returns trigger language plpgsql set search_path = '' as $$
begin
  if new.feito and not old.feito and new.repetir_meses is not null then
    new.vencimento := (old.vencimento + make_interval(months => new.repetir_meses))::date;
    new.feito := false; new.ultimo_aviso := null;
  end if;
  return new;
end $$;
create trigger civic_lembrete_renovar_t before update on public.civic_lembretes for each row execute function public.civic_lembrete_renovar();
create trigger civic_lembrete_marcar before update on public.civic_lembretes for each row execute function public.civic_marcar_atualizacao();
alter table public.civic_lembretes enable row level security;
create policy civic_lembrete_dash on public.civic_lembretes for all to authenticated
  using (dispositivo in (select public.civic_meus_dispositivos())) with check (dispositivo in (select public.civic_meus_dispositivos()));
-- Agendamento (aplicado à parte): select cron.schedule('civic-lembretes-diario', '0 11 * * *', $$select public.civic_cron_chamar('civic-lembretes', '{}'::jsonb)$$);

-- 6) Histórico de manutenção com custo e oficina; lançar aqui atualiza o plano (último feito) sozinho.
create table public.civic_manutencao_historico (
  dispositivo text not null references public.civic_dispositivos(token) on delete cascade,
  id bigint not null default (extract(epoch from clock_timestamp()) * 1000)::bigint,
  item_id text not null,
  feito_em date not null,
  km integer check (km is null or km >= 0),
  valor numeric(10,2) check (valor is null or valor >= 0),
  oficina text,
  observacao text,
  removido boolean not null default false,
  atualizado_em timestamptz not null default now(),
  primary key (dispositivo, id)
);
create trigger civic_manut_hist_marcar before update on public.civic_manutencao_historico for each row execute function public.civic_marcar_atualizacao();
create or replace function public.civic_manutencao_historico_aplicar() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if not new.removido then
    update public.civic_manutencao m set ultimo_km = coalesce(new.km, m.ultimo_km), ultima_data = new.feito_em::timestamptz
      where m.dispositivo = new.dispositivo and m.id = new.item_id and (m.ultima_data is null or new.feito_em::timestamptz >= m.ultima_data);
  end if;
  return new;
end $$;
revoke all on function public.civic_manutencao_historico_aplicar() from public, anon, authenticated;
create trigger civic_manut_hist_aplicar after insert or update on public.civic_manutencao_historico for each row execute function public.civic_manutencao_historico_aplicar();
alter table public.civic_manutencao_historico enable row level security;
create policy civic_manut_hist_dash on public.civic_manutencao_historico for all to authenticated
  using (dispositivo in (select public.civic_meus_dispositivos())) with check (dispositivo in (select public.civic_meus_dispositivos()));

-- 7) Anotações e correções em registros que o carro grava (só colunas permitidas).
alter table public.civic_viagens add column observacao text, add column removida boolean not null default false;
alter table public.civic_estacionamentos add column apelido text, add column observacao text;
alter table public.civic_eventos add column resolvido boolean not null default false, add column nota text;
create policy civic_dash_viagens_editar on public.civic_viagens for update to authenticated using (dispositivo in (select public.civic_meus_dispositivos())) with check (dispositivo in (select public.civic_meus_dispositivos()));
create policy civic_dash_estac_editar on public.civic_estacionamentos for update to authenticated using (dispositivo in (select public.civic_meus_dispositivos())) with check (dispositivo in (select public.civic_meus_dispositivos()));
create policy civic_dash_estac_apagar on public.civic_estacionamentos for delete to authenticated using (dispositivo in (select public.civic_meus_dispositivos()));
create policy civic_dash_eventos_editar on public.civic_eventos for update to authenticated using (dispositivo in (select public.civic_meus_dispositivos())) with check (dispositivo in (select public.civic_meus_dispositivos()));
create policy civic_dash_eventos_apagar on public.civic_eventos for delete to authenticated using (dispositivo in (select public.civic_meus_dispositivos()));
revoke update on public.civic_viagens, public.civic_estacionamentos, public.civic_eventos from authenticated;
grant update (observacao, removida) on public.civic_viagens to authenticated;
grant update (apelido, observacao, endereco) on public.civic_estacionamentos to authenticated;
grant update (resolvido, nota) on public.civic_eventos to authenticated;

-- As análises ignoram as viagens removidas.
create or replace view public.civic_viagens_comparativo with (security_invoker = true) as
  select v.id, v.dispositivo, v.inicio, v.fim, v.distancia_km, v.duracao_s, v.em_movimento_s, v.vel_media, v.vel_max, v.rpm_max, v.rpm_alto, v.frenagens, v.aceleracoes, v.excessos,
    v.nota, v.consumo_km_l, v.fonte, v.lat_ini, v.lon_ini, v.lat_fim, v.lon_fim, v.endereco_ini, v.endereco_fim, v.simulado, v.versao_app, v.enviado_em, v.excesso_max_kmh, v.excesso_s,
    round(avg(v.nota) over (partition by v.dispositivo order by v.inicio rows between 10 preceding and 1 preceding), 1) as nota_media_anteriores,
    v.nota - round(avg(v.nota) over (partition by v.dispositivo order by v.inicio rows between 10 preceding and 1 preceding))::int as nota_vs_media,
    rank() over (partition by v.dispositivo order by v.nota desc nulls last) as ranking_nota,
    count(*) over (partition by v.dispositivo) as total_viagens,
    v.observacao
  from public.civic_viagens v where not v.removida;
create or replace view public.civic_viagens_semanal with (security_invoker = true) as
  select dispositivo, date_trunc('week', inicio at time zone 'America/Sao_Paulo')::date as semana,
    count(*) as viagens, round(sum(distancia_km), 1) as km, round(sum(duracao_s) / 3600.0, 1) as horas,
    round(avg(nota), 1) as nota_media, sum(frenagens) as frenagens, sum(aceleracoes) as aceleracoes, sum(excessos) as excessos,
    round(avg(consumo_km_l), 1) as consumo_medio, max(vel_max) as vel_max, bool_and(simulado) as so_simulado
  from public.civic_viagens where not removida group by dispositivo, date_trunc('week', inicio at time zone 'America/Sao_Paulo')::date;

-- 8) Gestão dos veículos: renomear e arquivar (esconde cadastros antigos sem apagar nada).
alter table public.civic_dispositivos add column arquivado boolean not null default false;
create or replace view public.civic_veiculos with (security_invoker = false) as
  select d.token as dispositivo, d.nome, d.criado_em, s.aparelho, s.android_versao, s.nome_carro, s.versao_app, s.atualizado_em as ultimo_sinal, d.arquivado
  from public.civic_dispositivos d left join public.civic_status s on s.dispositivo = d.token
  where d.token in (select public.civic_meus_dispositivos());
create or replace function public.civic_renomear_veiculo(p_dispositivo text, p_nome text) returns void language plpgsql security definer set search_path = '' as $$
begin
  if p_dispositivo not in (select public.civic_meus_dispositivos()) then raise exception 'veículo não permitido'; end if;
  if length(trim(p_nome)) not between 1 and 60 then raise exception 'nome deve ter de 1 a 60 caracteres'; end if;
  update public.civic_dispositivos set nome = trim(p_nome) where token = p_dispositivo;
end $$;
create or replace function public.civic_arquivar_veiculo(p_dispositivo text, p_arquivar boolean default true) returns void language plpgsql security definer set search_path = '' as $$
begin
  if p_dispositivo not in (select public.civic_meus_dispositivos()) then raise exception 'veículo não permitido'; end if;
  update public.civic_dispositivos set arquivado = p_arquivar where token = p_dispositivo;
end $$;
revoke all on function public.civic_renomear_veiculo(text, text), public.civic_arquivar_veiculo(text, boolean) from public, anon;
grant execute on function public.civic_renomear_veiculo(text, text), public.civic_arquivar_veiculo(text, boolean) to authenticated;

-- 9) Custos (combustível + manutenção + despesas) e vencimentos abertos.
create view public.civic_custos_mensal with (security_invoker = true) as
  select dispositivo, date_trunc('month', data at time zone 'America/Sao_Paulo')::date as mes, 'combustivel'::text as categoria, round(sum(total_pago), 2) as valor
    from public.civic_abastecimentos_analise where total_pago > 0 group by dispositivo, date_trunc('month', data at time zone 'America/Sao_Paulo')::date
  union all
  select dispositivo, date_trunc('month', feito_em)::date, 'manutencao', round(sum(valor), 2)
    from public.civic_manutencao_historico where not removido and valor is not null group by dispositivo, date_trunc('month', feito_em)::date
  union all
  select dispositivo, date_trunc('month', data)::date, categoria, round(sum(valor), 2)
    from public.civic_despesas where not removido group by dispositivo, date_trunc('month', data), categoria;
create view public.civic_lembretes_proximos with (security_invoker = true) as
  select l.*, (l.vencimento - (now() at time zone 'America/Sao_Paulo')::date) as dias_restantes,
    case when l.vencimento < (now() at time zone 'America/Sao_Paulo')::date then 'vencido'
         when l.vencimento - (now() at time zone 'America/Sao_Paulo')::date <= l.avisar_dias then 'proximo' else 'em_dia' end as situacao
  from public.civic_lembretes l where not l.feito and not l.removido;
grant select on public.civic_custos_mensal, public.civic_lembretes_proximos to authenticated;
revoke all on public.civic_custos_mensal, public.civic_lembretes_proximos from anon;
