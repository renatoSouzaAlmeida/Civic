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
