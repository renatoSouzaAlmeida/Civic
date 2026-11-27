-- Relatórios da análise do adaptador OBD (varredura de PIDs suportados e captura guiada do CAN: setas, faróis, marcha).
-- O aparelho envia o relatório completo em JSON; serve para descobrir o que o carro informa pela tomada OBD.
create table if not exists public.civic_relatorios (
  id bigint generated always as identity primary key,
  dispositivo text not null references public.civic_dispositivos(token) on delete cascade,
  tipo text not null,
  criado_em timestamptz not null default now(),
  dados jsonb not null
);
create index if not exists civic_relatorios_disp_idx on public.civic_relatorios (dispositivo, criado_em desc);
alter table public.civic_relatorios enable row level security;
create policy civic_relat_inserir on public.civic_relatorios for insert to anon with check (dispositivo = public.civic_token());
create policy civic_relat_ler on public.civic_relatorios for select to anon using (dispositivo = public.civic_token());
revoke update, delete on public.civic_relatorios from anon, authenticated;
comment on table public.civic_relatorios is 'Relatórios técnicos enviados pelo aparelho (ex.: análise do adaptador OBD).';
