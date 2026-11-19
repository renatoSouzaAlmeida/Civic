-- Aplicada no projeto CasaTechRenatin (Supabase) em 19/11/2026 como "civic_inicial".
-- Civic Renatin (launcher do carro). Tabelas com prefixo civic_ para não misturar com a Casa Tech.
-- Cada aparelho tem um token secreto aleatório (gerado no próprio aparelho) e enviado no cabeçalho
-- x-civic-token; as políticas de RLS só deixam o aparelho ver/alterar as próprias linhas.

create or replace function public.civic_token() returns text
language sql stable
set search_path = ''
as $$ select coalesce((current_setting('request.headers', true)::json ->> 'x-civic-token'), '') $$;

create table public.civic_dispositivos (
  token text primary key check (length(token) >= 32),
  nome text,
  codigo_vinculo text not null unique,
  telegram_chat_id bigint,
  criado_em timestamptz not null default now()
);

create table public.civic_abastecimentos (
  dispositivo text not null references public.civic_dispositivos(token) on delete cascade,
  id bigint not null,
  data timestamptz not null,
  km integer not null,
  litros numeric not null,
  preco_litro numeric not null,
  combustivel text not null,
  tanque_cheio boolean not null,
  primary key (dispositivo, id)
);

create table public.civic_manutencao (
  dispositivo text not null references public.civic_dispositivos(token) on delete cascade,
  id text not null,
  nome text not null,
  intervalo_km integer,
  intervalo_meses integer,
  ultimo_km integer,
  ultima_data timestamptz,
  situacao text,
  atualizado_em timestamptz not null default now(),
  primary key (dispositivo, id)
);

create table public.civic_eventos (
  id bigserial primary key,
  dispositivo text not null references public.civic_dispositivos(token) on delete cascade,
  tipo text not null,
  mensagem text not null,
  enviado boolean not null default false,
  criado_em timestamptz not null default now()
);
create index civic_eventos_dispositivo_idx on public.civic_eventos(dispositivo, criado_em desc);

alter table public.civic_dispositivos enable row level security;
alter table public.civic_abastecimentos enable row level security;
alter table public.civic_manutencao enable row level security;
alter table public.civic_eventos enable row level security;

create policy civic_disp_inserir on public.civic_dispositivos for insert to anon with check (token = public.civic_token());
create policy civic_disp_ler on public.civic_dispositivos for select to anon using (token = public.civic_token());
revoke update, delete on public.civic_dispositivos from anon, authenticated;

create policy civic_abast_tudo on public.civic_abastecimentos for all to anon
  using (dispositivo = public.civic_token()) with check (dispositivo = public.civic_token());
create policy civic_manut_tudo on public.civic_manutencao for all to anon
  using (dispositivo = public.civic_token()) with check (dispositivo = public.civic_token());
create policy civic_evento_inserir on public.civic_eventos for insert to anon with check (dispositivo = public.civic_token());
create policy civic_evento_ler on public.civic_eventos for select to anon using (dispositivo = public.civic_token());

-- ===== Aplicada em 19/11/2026 como "civic_manutencao_preserva_mais_recente" =====
-- Um registro de manutenção nunca é trocado por um mais antigo ou vazio.
create or replace function public.civic_manutencao_preserva()
returns trigger language plpgsql set search_path = '' as $$
begin
  if old.ultima_data is not null and (new.ultima_data is null or new.ultima_data < old.ultima_data) then
    new.ultima_data := old.ultima_data;
    new.ultimo_km := old.ultimo_km;
  elsif old.ultimo_km is not null and new.ultimo_km is null and new.ultima_data is not distinct from old.ultima_data then
    new.ultimo_km := old.ultimo_km;
  end if;
  return new;
end;
$$;
create trigger civic_manutencao_preserva before update on public.civic_manutencao
for each row execute function public.civic_manutencao_preserva();

-- ===== Aplicada em 19/11/2026 como "civic_novo_aparelho_herda_manutencao" =====
-- Todo aparelho novo (ex.: a multimídia na primeira instalação) herda a manutenção já lançada,
-- copiando os registros do aparelho que tem mais itens preenchidos (os dados são de um só dono).
create or replace function public.civic_herdar_manutencao()
returns trigger language plpgsql security definer set search_path = '' as $$
declare modelo text;
begin
  select dispositivo into modelo from public.civic_manutencao
  where ultima_data is not null and dispositivo <> new.token
  group by dispositivo order by count(*) desc, max(atualizado_em) desc limit 1;
  if modelo is not null then
    insert into public.civic_manutencao (dispositivo, id, nome, intervalo_km, intervalo_meses, ultimo_km, ultima_data, situacao, atualizado_em)
    select new.token, id, nome, intervalo_km, intervalo_meses, ultimo_km, ultima_data, situacao, now()
    from public.civic_manutencao where dispositivo = modelo
    on conflict (dispositivo, id) do nothing;
  end if;
  return new;
end;
$$;
revoke all on function public.civic_herdar_manutencao() from public, anon, authenticated;
create trigger civic_herdar_manutencao after insert on public.civic_dispositivos
for each row execute function public.civic_herdar_manutencao();

-- ===== Aplicada em 19/11/2026 como "civic_historico_estacionamentos_e_acesso_dashboard" =====
-- Histórico de onde o carro ficou estacionado. Quem grava é a função civic-notificar (service role),
-- na mesma chamada que manda o Telegram; o aparelho não tem acesso direto (sem política para anon).
create table public.civic_estacionamentos (
  id bigserial primary key,
  dispositivo text not null references public.civic_dispositivos(token) on delete cascade,
  data timestamptz not null default now(),
  lat double precision not null check (lat between -90 and 90),
  lon double precision not null check (lon between -180 and 180),
  endereco text,
  idade_gps_s integer,          -- há quantos segundos foi o último sinal de GPS (0 = posição fresca)
  origem text not null default 'auto',  -- 'auto' (ignição/motor) ou 'manual' (botão em Veículo)
  criado_em timestamptz not null default now()
);
create index civic_estacionamentos_idx on public.civic_estacionamentos(dispositivo, data desc);
alter table public.civic_estacionamentos enable row level security;

-- Acesso do dashboard (Casa Tech, login OAuth do Supabase): quais aparelhos cada usuário pode ver.
-- O vínculo é criado por quem administra (insert manual); o usuário só lê o próprio.
create table public.civic_acessos (
  user_id uuid not null references auth.users(id) on delete cascade,
  dispositivo text not null references public.civic_dispositivos(token) on delete cascade,
  criado_em timestamptz not null default now(),
  primary key (user_id, dispositivo)
);
alter table public.civic_acessos enable row level security;
create policy civic_acessos_ler on public.civic_acessos for select to authenticated using (user_id = auth.uid());

-- Leitura (somente) para o usuário logado, só dos aparelhos vinculados a ele.
create or replace function public.civic_meus_dispositivos() returns setof text
language sql stable security definer set search_path = '' as $$
  select dispositivo from public.civic_acessos where user_id = (select auth.uid())
$$;
revoke all on function public.civic_meus_dispositivos() from public, anon;
grant execute on function public.civic_meus_dispositivos() to authenticated;

create policy civic_dash_estacionamentos on public.civic_estacionamentos for select to authenticated
  using (dispositivo in (select public.civic_meus_dispositivos()));
create policy civic_dash_abastecimentos on public.civic_abastecimentos for select to authenticated
  using (dispositivo in (select public.civic_meus_dispositivos()));
create policy civic_dash_manutencao on public.civic_manutencao for select to authenticated
  using (dispositivo in (select public.civic_meus_dispositivos()));
create policy civic_dash_eventos on public.civic_eventos for select to authenticated
  using (dispositivo in (select public.civic_meus_dispositivos()));

-- Dados do aparelho para o dashboard, sem expor o token nem o chat do Telegram.
create view public.civic_veiculos with (security_invoker = false) as
  select d.token as dispositivo, d.nome, d.criado_em
  from public.civic_dispositivos d where d.token in (select public.civic_meus_dispositivos());
grant select on public.civic_veiculos to authenticated;
revoke all on public.civic_veiculos from anon;

-- ===== Aplicada em 19/11/2026 como "civic_estacionamentos_sem_duplicata" =====
create unique index civic_estacionamentos_unico on public.civic_estacionamentos(dispositivo, data);

-- ===== Aplicada em 19/11/2026 como "civic_estados" =====
-- Resumo do último uso do carro, enviado pelo app quando o carro liga de novo.
create table public.civic_estados (
  id bigserial primary key,
  dispositivo text not null references public.civic_dispositivos(token) on delete cascade,
  registrado_em timestamptz not null,
  enviado_em timestamptz not null default now(),
  versao_app text,
  tensao numeric, temp_motor integer, temp_admissao integer, nivel_combustivel integer,
  consumo_medio numeric, stft numeric, ltft numeric,
  luz_motor boolean, dtc jsonb, dtc_pendentes jsonb,
  vel_max integer, rpm_max integer,
  viagem_km numeric, viagem_duracao_s integer, viagem_vel_media numeric,
  lat double precision, lon double precision, gps_em timestamptz,
  bateria_repouso numeric, bateria_minima numeric, bateria_avaliacao text
);
create unique index civic_estados_unico on public.civic_estados(dispositivo, registrado_em);
create index civic_estados_idx on public.civic_estados(dispositivo, registrado_em desc);
alter table public.civic_estados enable row level security;
create policy civic_estado_inserir on public.civic_estados for insert to anon with check (dispositivo = public.civic_token());
create policy civic_dash_estados on public.civic_estados for select to authenticated using (dispositivo in (select public.civic_meus_dispositivos()));

-- ===== Aplicada em 19/11/2026 como "civic_telemetria" =====
-- Posição ao vivo/trajeto, eventos de ignição e status online do carro.
create table public.civic_posicoes (
  dispositivo text not null references public.civic_dispositivos(token) on delete cascade,
  em timestamptz not null,
  lat double precision not null check (lat between -90 and 90),
  lon double precision not null check (lon between -180 and 180),
  vel numeric,            -- km/h
  viagem bigint,          -- identifica a viagem (hora em que o motor ligou, em ms); agrupa os pontos do trajeto
  primary key (dispositivo, em)
);
create index civic_posicoes_viagem_idx on public.civic_posicoes(dispositivo, viagem, em);

create table public.civic_ignicao (
  id bigserial primary key,
  dispositivo text not null references public.civic_dispositivos(token) on delete cascade,
  tipo text not null check (tipo in ('motor_ligou','motor_desligou','multimidia_ligou','multimidia_desligou')),
  em timestamptz not null,
  lat double precision, lon double precision, tensao numeric,
  viagem bigint,
  unique (dispositivo, tipo, em)
);
create index civic_ignicao_idx on public.civic_ignicao(dispositivo, em desc);

create table public.civic_status (
  dispositivo text primary key references public.civic_dispositivos(token) on delete cascade,
  atualizado_em timestamptz not null,
  motor_ligado boolean not null default false,
  lat double precision, lon double precision, vel numeric, tensao numeric,
  viagem bigint,
  versao_app text,
  heartbeat_s integer not null default 60
);

alter table public.civic_posicoes enable row level security;
alter table public.civic_ignicao enable row level security;
alter table public.civic_status enable row level security;

create policy civic_pos_inserir on public.civic_posicoes for insert to anon with check (dispositivo = public.civic_token());
create policy civic_ign_inserir on public.civic_ignicao for insert to anon with check (dispositivo = public.civic_token());
create policy civic_status_ins on public.civic_status for insert to anon with check (dispositivo = public.civic_token());
create policy civic_status_upd on public.civic_status for update to anon using (dispositivo = public.civic_token()) with check (dispositivo = public.civic_token());
create policy civic_status_ler on public.civic_status for select to anon using (dispositivo = public.civic_token());

create policy civic_dash_posicoes on public.civic_posicoes for select to authenticated using (dispositivo in (select public.civic_meus_dispositivos()));
create policy civic_dash_ignicao on public.civic_ignicao for select to authenticated using (dispositivo in (select public.civic_meus_dispositivos()));
create policy civic_dash_status on public.civic_status for select to authenticated using (dispositivo in (select public.civic_meus_dispositivos()));

-- Status com "online" calculado: online = o carro avisou há menos de 2,5 batidas de coração.
create view public.civic_status_atual with (security_invoker = true) as
  select s.*, (s.atualizado_em > now() - (s.heartbeat_s * 2.5) * interval '1 second') as online
  from public.civic_status s;
grant select on public.civic_status_atual to authenticated;
revoke all on public.civic_status_atual from anon;

-- Dashboard ao vivo (Realtime respeita as políticas acima).
alter publication supabase_realtime add table public.civic_status, public.civic_posicoes, public.civic_ignicao;

-- ===== Aplicada em 19/11/2026 como "civic_simulacao" =====
-- Simulação de dados para validar o dashboard (Civic Renatin).
-- Cria/recria um aparelho FICTÍCIO "Civic (simulado)" com N dias de uso: viagens (trajeto, ignição), estacionamentos,
-- estados do carro, alertas, abastecimentos e status online. Não mexe nos dados do carro real.
--   Gerar:   select public.civic_simular(7);
--   Apagar:  delete from public.civic_dispositivos where nome = 'Civic (simulado)';
-- Para ver no dash, vincule o seu usuário a ele (ver docs/DASHBOARD-CASA-TECH.md).
create or replace function public.civic_simular(p_dias integer default 7) returns text
language plpgsql security definer set search_path = '' as $$
declare
  tk text := 'simulado-' || md5('civic-simulado');
  d int; t int; i int; n int; dur int; vid bigint; ini timestamptz;
  lat0 double precision; lon0 double precision; lat1 double precision; lon1 double precision;
  f double precision; la double precision; lo double precision; v double precision; km numeric;
  viagens int := 0; pontos int := 0;
begin
  delete from public.civic_dispositivos where token = tk;
  insert into public.civic_dispositivos (token, nome, codigo_vinculo) values (tk, 'Civic (simulado)', 'SIMUL1');

  for d in reverse (p_dias - 1)..0 loop
    for t in 1..2 loop
      ini := date_trunc('day', now() - make_interval(days => d)) + case t when 1 then interval '10 hours 30 minutes' else interval '21 hours 10 minutes' end
             + make_interval(mins => (d * 7 + t * 3) % 25);
      continue when ini > now() - interval '40 minutes';
      -- t=1: casa -> trabalho; t=2: trabalho -> casa (São Paulo, coordenadas fictícias)
      if t = 1 then lat0 := -23.5870; lon0 := -46.6820; lat1 := -23.5505; lon1 := -46.6333;
      else lat0 := -23.5505; lon0 := -46.6333; lat1 := -23.5870; lon1 := -46.6820; end if;
      dur := 1500 + ((d * 37 + t * 211) % 900);
      n := dur / 15;
      vid := (extract(epoch from ini) * 1000)::bigint;
      km := round((dur / 3600.0 * 34)::numeric, 1);

      insert into public.civic_ignicao (dispositivo, tipo, em, lat, lon, tensao, viagem) values
        (tk, 'multimidia_ligou', ini - interval '25 seconds', lat0, lon0, 12.5, vid),
        (tk, 'motor_ligou', ini, lat0, lon0, 12.4, vid),
        (tk, 'motor_desligou', ini + make_interval(secs => dur), lat1, lon1, 13.9, vid),
        (tk, 'multimidia_desligou', ini + make_interval(secs => dur + 20), lat1, lon1, 13.4, vid);

      for i in 0..n loop
        f := i::double precision / n;
        la := lat0 + (lat1 - lat0) * f + 0.006 * sin(f * pi() * 3) * (1 - f) * f * 4;
        lo := lon0 + (lon1 - lon0) * f + 0.006 * cos(f * pi() * 2) * f * (1 - f) * 4;
        v := case when i = 0 or i = n then 0 else greatest(0, 22 + 38 * abs(sin(f * pi() * 5)) + (i % 5)) end;
        insert into public.civic_posicoes (dispositivo, em, lat, lon, vel, viagem)
          values (tk, ini + make_interval(secs => i * 15), la, lo, round(v::numeric, 1), vid);
        pontos := pontos + 1;
      end loop;

      insert into public.civic_estacionamentos (dispositivo, data, lat, lon, endereco, idade_gps_s, origem)
        values (tk, ini + make_interval(secs => dur + 5), lat1, lon1,
                case t when 1 then 'Av. Paulista, 1000, Bela Vista, São Paulo (simulado)' else 'Rua Augusta, 200, Consolação, São Paulo (simulado)' end, 2, 'auto');

      insert into public.civic_estados (dispositivo, registrado_em, versao_app, tensao, temp_motor, temp_admissao, nivel_combustivel,
          consumo_medio, stft, ltft, luz_motor, dtc, dtc_pendentes, vel_max, rpm_max, viagem_km, viagem_duracao_s, viagem_vel_media,
          lat, lon, gps_em, bateria_repouso, bateria_minima, bateria_avaliacao)
        values (tk, ini + make_interval(secs => dur), '0.3.simulado', 13.9 + (d % 3) * 0.1, 88 + (d + t) % 6, 31 + d % 5,
          greatest(8, 98 - (((13 - d) % 12 + 12) % 12) * 7 - t * 2), round((11.2 + ((d * 3 + t) % 8) * 0.25)::numeric, 1), round((((d + t) % 5) - 2)::numeric, 1), round((1.5 + (d % 4))::numeric, 1),
          d = 3, case when d = 3 then '["P0420"]'::jsonb else '[]'::jsonb end, case when d = 2 then '["P0171"]'::jsonb else '[]'::jsonb end,
          70 + (d * 5) % 25, 2800 + (d * 130) % 900, km, dur, round((km / (dur / 3600.0))::numeric, 1),
          lat1, lon1, ini + make_interval(secs => dur), 12.5 + (d % 3) * 0.05, 10.2 + (d % 4) * 0.1, 'Boa');
      viagens := viagens + 1;
    end loop;
  end loop;

  insert into public.civic_eventos (dispositivo, tipo, mensagem, enviado, criado_em) values
    (tk, 'motor_frio', 'Simulado: motor frio com giro alto (3.400 rpm a 62 °C).', false, now() - interval '5 days'),
    (tk, 'falha', 'Simulado: código P0420 (catalisador) detectado.', false, now() - interval '3 days'),
    (tk, 'bateria', 'Simulado: bateria com carga baixa na partida (10,1 V).', false, now() - interval '1 day');

  insert into public.civic_abastecimentos (dispositivo, id, data, km, litros, preco_litro, combustivel, tanque_cheio) values
    (tk, 1, now() - interval '6 days', 120000, 38.5, 5.89, 'Gasolina', true),
    (tk, 2, now() - interval '1 days', 120410, 36.2, 4.29, 'Etanol', true);

  insert into public.civic_status (dispositivo, atualizado_em, motor_ligado, lat, lon, vel, tensao, viagem, versao_app, heartbeat_s)
    values (tk, now(), false, -23.5870, -46.6820, 0, 12.5, null, '0.3.simulado', 300)
    on conflict (dispositivo) do update set atualizado_em = now();

  return format('Simulado: %s viagens, %s pontos de trajeto. Dispositivo: %s', viagens, pontos, tk);
end;
$$;
revoke all on function public.civic_simular(integer) from public, anon, authenticated;

-- ===== Aplicada em 19/11/2026 como "civic_abastecimento_automatico" =====
alter table public.civic_abastecimentos alter column preco_litro drop not null;
alter table public.civic_abastecimentos
  add column valor_total numeric,
  add column aditivada boolean not null default false,
  add column automatico boolean not null default false,
  add column nivel_antes integer,
  add column nivel_depois integer;

-- Análise de abastecimentos para o dashboard: km rodados, consumo (tanque cheio a tanque cheio), custo por km e dias entre abastecimentos.
create view public.civic_abastecimentos_analise with (security_invoker = true) as
select a.dispositivo, a.id, a.data, a.km, a.litros, a.combustivel, a.tanque_cheio, a.aditivada, a.automatico, a.nivel_antes, a.nivel_depois,
  coalesce(a.valor_total, a.litros * a.preco_litro) as total_pago,
  coalesce(a.preco_litro, a.valor_total / nullif(a.litros, 0)) as preco_litro,
  (a.valor_total is null and a.preco_litro is null) as aguardando_valor,
  a.km - lag(a.km) over w as km_rodados,
  round(extract(epoch from (a.data - lag(a.data) over w)) / 86400.0, 1) as dias_desde_anterior,
  case when a.tanque_cheio and a.litros > 0 and lag(a.km) over w is not null
       then round((a.km - lag(a.km) over w) / a.litros, 2) end as consumo_km_l,
  case when coalesce(a.valor_total, a.litros * a.preco_litro) > 0 and lag(a.km) over w is not null and a.km > lag(a.km) over w
       then round(coalesce(a.valor_total, a.litros * a.preco_litro) / (a.km - lag(a.km) over w), 3) end as custo_por_km
from public.civic_abastecimentos a
window w as (partition by a.dispositivo order by a.km);
grant select on public.civic_abastecimentos_analise to authenticated;
revoke all on public.civic_abastecimentos_analise from anon;

-- ===== Reaplicada em 19/11/2026 como "civic_simulacao_abastecimentos" (supabase/simulacao.sql) =====
-- Simulação de dados para validar o dashboard (Civic Renatin).
-- Cria/recria um aparelho FICTÍCIO "Civic (simulado)" com N dias de uso: viagens (trajeto, ignição), estacionamentos,
-- estados do carro, alertas, abastecimentos e status online. Não mexe nos dados do carro real.
--   Gerar:   select public.civic_simular(30);
--   Apagar:  delete from public.civic_dispositivos where nome = 'Civic (simulado)';
-- Para ver no dash, vincule o seu usuário a ele (ver docs/DASHBOARD-CASA-TECH.md).
create or replace function public.civic_simular(p_dias integer default 30) returns text
language plpgsql security definer set search_path = '' as $$
declare
  tk text := 'simulado-' || md5('civic-simulado');
  d int; t int; i int; n int; dur int; vid bigint; ini timestamptz;
  lat0 double precision; lon0 double precision; lat1 double precision; lon1 double precision;
  f double precision; la double precision; lo double precision; v double precision; km numeric;
  viagens int := 0; pontos int := 0; odo numeric := 119400; ab int := 0; lit numeric; pre numeric;
begin
  delete from public.civic_dispositivos where token = tk;
  insert into public.civic_dispositivos (token, nome, codigo_vinculo) values (tk, 'Civic (simulado)', 'SIMUL1');

  for d in reverse (p_dias - 1)..0 loop
    for t in 1..2 loop
      ini := date_trunc('day', now() - make_interval(days => d)) + case t when 1 then interval '10 hours 30 minutes' else interval '21 hours 10 minutes' end
             + make_interval(mins => (d * 7 + t * 3) % 25);
      continue when ini > now() - interval '40 minutes';
      -- t=1: casa -> trabalho; t=2: trabalho -> casa (São Paulo, coordenadas fictícias)
      if t = 1 then lat0 := -23.5870; lon0 := -46.6820; lat1 := -23.5505; lon1 := -46.6333;
      else lat0 := -23.5505; lon0 := -46.6333; lat1 := -23.5870; lon1 := -46.6820; end if;
      dur := 1500 + ((d * 37 + t * 211) % 900);
      n := dur / 15;
      vid := (extract(epoch from ini) * 1000)::bigint;
      km := round((dur / 3600.0 * 34)::numeric, 1);
      odo := odo + km;

      insert into public.civic_ignicao (dispositivo, tipo, em, lat, lon, tensao, viagem) values
        (tk, 'multimidia_ligou', ini - interval '25 seconds', lat0, lon0, 12.5, vid),
        (tk, 'motor_ligou', ini, lat0, lon0, 12.4, vid),
        (tk, 'motor_desligou', ini + make_interval(secs => dur), lat1, lon1, 13.9, vid),
        (tk, 'multimidia_desligou', ini + make_interval(secs => dur + 20), lat1, lon1, 13.4, vid);

      for i in 0..n loop
        f := i::double precision / n;
        la := lat0 + (lat1 - lat0) * f + 0.006 * sin(f * pi() * 3) * (1 - f) * f * 4;
        lo := lon0 + (lon1 - lon0) * f + 0.006 * cos(f * pi() * 2) * f * (1 - f) * 4;
        v := case when i = 0 or i = n then 0 else greatest(0, 22 + 38 * abs(sin(f * pi() * 5)) + (i % 5)) end;
        insert into public.civic_posicoes (dispositivo, em, lat, lon, vel, viagem)
          values (tk, ini + make_interval(secs => i * 15), la, lo, round(v::numeric, 1), vid);
        pontos := pontos + 1;
      end loop;

      insert into public.civic_estacionamentos (dispositivo, data, lat, lon, endereco, idade_gps_s, origem)
        values (tk, ini + make_interval(secs => dur + 5), lat1, lon1,
                case t when 1 then 'Av. Paulista, 1000, Bela Vista, São Paulo (simulado)' else 'Rua Augusta, 200, Consolação, São Paulo (simulado)' end, 2, 'auto');

      insert into public.civic_estados (dispositivo, registrado_em, versao_app, tensao, temp_motor, temp_admissao, nivel_combustivel,
          consumo_medio, stft, ltft, luz_motor, dtc, dtc_pendentes, vel_max, rpm_max, viagem_km, viagem_duracao_s, viagem_vel_media,
          lat, lon, gps_em, bateria_repouso, bateria_minima, bateria_avaliacao)
        values (tk, ini + make_interval(secs => dur), '0.3.simulado', 13.9 + (d % 3) * 0.1, 88 + (d + t) % 6, 31 + d % 5,
          greatest(8, 98 - (((13 - d) % 12 + 12) % 12) * 7 - t * 2), round((11.2 + ((d * 3 + t) % 8) * 0.25)::numeric, 1), round((((d + t) % 5) - 2)::numeric, 1), round((1.5 + (d % 4))::numeric, 1),
          d = 3, case when d = 3 then '["P0420"]'::jsonb else '[]'::jsonb end, case when d = 2 then '["P0171"]'::jsonb else '[]'::jsonb end,
          70 + (d * 5) % 25, 2800 + (d * 130) % 900, km, dur, round((km / (dur / 3600.0))::numeric, 1),
          lat1, lon1, ini + make_interval(secs => dur), 12.5 + (d % 3) * 0.05, 10.2 + (d % 4) * 0.1, 'Boa');
      -- abastecimento a cada ~12 dias (entrando na reserva), sempre gasolina e tanque cheio; o mais recente fica sem valor (aguardando o valor pago)
      if t = 2 and d % 12 = 1 then
        lit := round((33 + (d % 5) * 1.3)::numeric, 1);
        pre := round((5.79 + (d % 3) * 0.1)::numeric, 2);
        insert into public.civic_abastecimentos (dispositivo, id, data, km, litros, preco_litro, valor_total, combustivel, tanque_cheio, aditivada, automatico, nivel_antes, nivel_depois)
          values (tk, p_dias - d, ini + make_interval(secs => dur + 600), odo::int, lit,
                  case when d = 1 then null else pre end, case when d = 1 then null else round(lit * pre, 2) end,
                  'Gasolina', true, d % 8 = 1, true, 12 + d % 6, 99 - d % 3);
        ab := ab + 1;
      end if;
      viagens := viagens + 1;
    end loop;
  end loop;

  insert into public.civic_eventos (dispositivo, tipo, mensagem, enviado, criado_em) values
    (tk, 'motor_frio', 'Simulado: motor frio com giro alto (3.400 rpm a 62 °C).', false, now() - interval '5 days'),
    (tk, 'falha', 'Simulado: código P0420 (catalisador) detectado.', false, now() - interval '3 days'),
    (tk, 'bateria', 'Simulado: bateria com carga baixa na partida (10,1 V).', false, now() - interval '1 day');

  insert into public.civic_status (dispositivo, atualizado_em, motor_ligado, lat, lon, vel, tensao, viagem, versao_app, heartbeat_s)
    values (tk, now(), false, -23.5870, -46.6820, 0, 12.5, null, '0.3.simulado', 300)
    on conflict (dispositivo) do update set atualizado_em = now();

  return format('Simulado: %s viagens, %s pontos de trajeto, %s abastecimentos. Dispositivo: %s', viagens, pontos, ab, tk);
end;
$$;
revoke all on function public.civic_simular(integer) from public, anon, authenticated;
