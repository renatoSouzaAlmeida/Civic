-- Análise de condução por viagem: série compacta e tempo em marcha lenta, enviados quando a viagem termina.
alter table public.civic_viagens
  add column if not exists serie jsonb,
  add column if not exists marcha_lenta_s int;
comment on column public.civic_viagens.serie is 'Série da viagem (uma amostra a cada ~10 s, no máx. 240 pontos): lista de [t_s, vel_kmh, rpm, acelerador_pct, temp_motor_c, consumo_kml_x10]; -1 = sem leitura (só OBD traz rpm, acelerador, temperatura e consumo). Nulo em viagens antigas.';
comment on column public.civic_viagens.marcha_lenta_s is 'Segundos com o motor ligado e o carro parado (marcha lenta). Só com OBD.';
