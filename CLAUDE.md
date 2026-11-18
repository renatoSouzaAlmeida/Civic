# Instruções do projeto (Civic Launcher)

## Fluxo de entrega
- Ao concluir todas as tarefas, publique **diretamente na `main`** (sem Pull Request e sem pedir confirmação).

## Idioma
- Tudo em **português do Brasil**: respostas, mensagens de commit, documentação, comentários de código e textos da interface.

## Atualização automática do APK
- Toda nova versão na `main` deve gerar um APK atualizado (build no GitHub Actions publicando uma Release).
- O aplicativo deve **identificar a nova versão sozinho e baixá-la direto na multimídia**, sem depender de pendrive (só a primeira instalação é manual).
- Incrementar `versionCode` e `versionName` a cada versão publicada, para o app conseguir comparar.

## Tamanho do aplicativo
- Priorizar um app **pequeno**, para facilitar o download (a multimídia tem internet limitada fora da garagem).
- Evitar dependências e recursos desnecessários ou não otimizados: imagens/animações pesadas, bibliotecas grandes, `material-icons-extended` inteiro (usar só os ícones necessários ou ícones próprios).
- Na versão de release, ativar minificação e remoção de recursos não usados (R8 / `shrinkResources`).
