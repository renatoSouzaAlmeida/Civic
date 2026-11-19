-- Aplicada no projeto CasaTechRenatin (Supabase) em 19/11/2026 como "civic_inicial".
-- Civic Renatin (launcher do carro). Tabelas com prefixo civic_ para não misturar com a CasaTech.
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

-- Acesso do dashboard (CasaTech, login OAuth do Supabase): quais aparelhos cada usuário pode ver.
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
