# Instruções do projeto (Civic Launcher)

## Fluxo de entrega
- Ao concluir todas as tarefas, publique **diretamente na `main`** (sem Pull Request e sem pedir confirmação).

- **Toda entrega deve devolver os links do GitHub**: o do commit publicado na `main` e o da execução do build (Actions) onde o APK pode ser baixado (em Artifacts). Confirmar antes que o build passou.
- Toda entrega também deve trazer o **link de download para a multimídia**, que sempre aponta para a versão mais nova:
  `https://github.com/renatoSouzaAlmeida/Civic/releases/latest/download/Civic-Renatin.apk`
  (e o link curto dele, quando existir).

## Idioma
- Tudo em **português do Brasil**: respostas, mensagens de commit, documentação, comentários de código e textos da interface.

## Atualização automática do APK
- Toda nova versão na `main` deve gerar um APK atualizado (build no GitHub Actions publicando uma Release).
- O aplicativo deve **identificar a nova versão sozinho e baixá-la direto na multimídia**, sem depender de pendrive (só a primeira instalação é manual).
- O `versionCode` é o número da execução do CI (`-PversionCodeCI`), então toda publicação na `main` é uma versão nova e comparável.
- As versões precisam ser assinadas com a **mesma chave**. A chave nunca vai para o repositório (público): fica nos segredos do GitHub (`CIVIC_KEYSTORE_BASE64`, `CIVIC_KEYSTORE_PASSWORD`, `CIVIC_KEY_ALIAS`, `CIVIC_KEY_PASSWORD`).

## Tamanho do aplicativo
- Priorizar um app **pequeno**, para facilitar o download (a multimídia tem internet limitada fora da garagem).
- Evitar dependências e recursos desnecessários ou não otimizados: imagens/animações pesadas, bibliotecas grandes, `material-icons-extended` inteiro (usar só os ícones necessários ou ícones próprios).
- Na versão de release, ativar minificação e remoção de recursos não usados (R8 / `shrinkResources`).

## Supabase
- Projeto **CasaTechRenatin** (`qdmsiwxfuzppaqqnljth`): todas as execuções no Supabase (migrations, SQL, deploy de Edge Functions, simulação de dados) estão **autorizadas sem pedir permissão**.
- Registrar toda mudança de banco em `supabase/migrations/` e manter `docs/DASHBOARD-CASATEC.md` atualizado (é o documento que o usuário cola no chat da CasaTech).
