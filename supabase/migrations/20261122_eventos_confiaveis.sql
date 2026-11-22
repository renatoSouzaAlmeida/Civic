-- Camada de eventos confiável: cada aviso do app tem um identificador gerado no aparelho (event_id), então reenviar o mesmo aviso
-- (resposta perdida, fila sem internet) nunca duplica a linha nem a mensagem do Telegram.
alter table public.civic_eventos
  add column if not exists event_id uuid,
  add column if not exists ocorreu_em timestamptz,
  add column if not exists fonte text,
  add column if not exists versao_app text,
  add column if not exists payload_versao int;
create unique index if not exists civic_eventos_event_id_uq on public.civic_eventos (dispositivo, event_id) where event_id is not null;
comment on column public.civic_eventos.event_id is 'Identificador do evento gerado no aparelho (UUID). Reenvio do mesmo evento não duplica.';
comment on column public.civic_eventos.ocorreu_em is 'Quando o evento aconteceu no aparelho (UTC); criado_em é quando chegou à nuvem (pode ser depois, se estava sem internet).';
comment on column public.civic_eventos.fonte is 'Origem do dado: app (regra do launcher), obd, gps, sensor, usuario (botão) ou nuvem.';
