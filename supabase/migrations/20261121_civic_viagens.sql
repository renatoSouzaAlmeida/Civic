-- ===== Aplicada em 21/11/2026 como "civic_viagens" =====
-- Viagens com modo de condução (Driving Score): uma linha por viagem, enviada pelo app ao terminar
-- (funciona com OBD ou só com GPS, inclusive com CarPlay/Android Auto na frente).
create table public.civic_viagens (
  id bigserial primary key,
  dispositivo text not null references public.civic_dispositivos(token) on delete cascade,
  inicio timestamptz not null,
  fim timestamptz not null,
  distancia_km numeric not null default 0,
  duracao_s integer not null default 0,
  em_movimento_s integer not null default 0,
  vel_media numeric, vel_max integer,
  rpm_max integer,                 -- só com OBD
  rpm_alto integer,                -- ocorrências de RPM elevado (só com OBD)
  frenagens integer not null default 0,
  aceleracoes integer not null default 0,
  excessos integer not null default 0,   -- vezes que passou do limite de velocidade configurado
  nota integer,                    -- Driving Score 0 a 100
  consumo_km_l numeric,            -- consumo médio medido (só com OBD)
  fonte text not null default 'gps' check (fonte in ('obd','gps')),
  lat_ini double precision, lon_ini double precision,
  lat_fim double precision, lon_fim double precision,
  endereco_ini text, endereco_fim text,
  simulado boolean not null default false,
  versao_app text,
  enviado_em timestamptz not null default now(),
  unique (dispositivo, inicio)
);
create index civic_viagens_idx on public.civic_viagens(dispositivo, inicio desc);
alter table public.civic_viagens enable row level security;
create policy civic_viagem_inserir on public.civic_viagens for insert to anon with check (dispositivo = public.civic_token());
create policy civic_viagem_atualizar on public.civic_viagens for update to anon using (dispositivo = public.civic_token()) with check (dispositivo = public.civic_token());
create policy civic_dash_viagens on public.civic_viagens for select to authenticated using (dispositivo in (select public.civic_meus_dispositivos()));
alter publication supabase_realtime add table public.civic_viagens;

-- ===== Aplicada em 21/11/2026 como "civic_viagens_excesso" =====
alter table public.civic_viagens
  add column excesso_max_kmh integer,   -- maior velocidade acima do limite configurado
  add column excesso_s integer;         -- tempo total acima do limite (segundos)

-- ===== Aplicada em 21/11/2026 como "civic_areas" =====
-- Áreas do geofence (Casa, Trabalho...), sincronizadas pelo app. Áreas removidas ficam com removida = true.
create table public.civic_areas (
  dispositivo text not null references public.civic_dispositivos(token) on delete cascade,
  id text not null,
  nome text not null,
  lat double precision not null,
  lon double precision not null,
  raio_m integer not null check (raio_m > 0),
  ativa boolean not null default true,
  removida boolean not null default false,
  atualizado_em timestamptz not null default now(),
  primary key (dispositivo, id)
);
alter table public.civic_areas enable row level security;
create policy civic_area_inserir on public.civic_areas for insert to anon with check (dispositivo = public.civic_token());
create policy civic_area_atualizar on public.civic_areas for update to anon using (dispositivo = public.civic_token()) with check (dispositivo = public.civic_token());
create policy civic_dash_areas on public.civic_areas for select to authenticated using (dispositivo in (select public.civic_meus_dispositivos()));
