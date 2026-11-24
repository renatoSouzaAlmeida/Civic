-- Correção: o índice único parcial (where event_id is not null) não serve para o ON CONFLICT do PostgREST,
-- e os avisos com event_id deixavam de ser gravados. Uma restrição única normal resolve: NULLs não colidem entre si.
drop index if exists public.civic_eventos_event_id_uq;
alter table public.civic_eventos add constraint civic_eventos_event_id_uq unique (dispositivo, event_id);
