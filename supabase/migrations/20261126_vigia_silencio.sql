-- Vigia do silêncio: a multimídia apaga na hora em que a chave é desligada (sem internet, sem aviso).
-- A nuvem percebe que o carro parou de dar sinal parado (status com motor ligado e sem atualização há mais de 3 min)
-- e avisa no Telegram onde ele ficou (função civic-vigia, chamada a cada minuto).
alter table public.civic_status
  add column if not exists endereco text,
  add column if not exists vaga_avisada_em timestamptz;
comment on column public.civic_status.endereco is 'Endereço da posição atual, resolvido no aparelho quando o carro para (a nuvem não consegue resolver endereço).';
comment on column public.civic_status.vaga_avisada_em is 'Quando a nuvem avisou o estacionamento por silêncio (evita repetir o aviso).';
-- Agendamento (aplicado direto no banco):
-- select cron.schedule('civic-vigia-vaga', '* * * * *', $$select public.civic_cron_chamar('civic-vigia', '{}'::jsonb)$$);
