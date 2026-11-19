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
