-- Linha do tempo da viagem: lista de eventos [{em, tipo, texto}] enviada pelo app quando a viagem termina.
alter table public.civic_viagens add column if not exists eventos jsonb;
comment on column public.civic_viagens.eventos is 'Linha do tempo da viagem: [{em (ISO UTC), tipo (partida, frenagem, aceleracao, excesso, rpm, colisao, sos, alerta, estacionou), texto}], em ordem de hora. Nulo em viagens antigas.';
