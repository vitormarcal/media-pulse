# Renovação periódica de snapshots externos

## Problema

Snapshots externos concluídos deixam de entrar na fila automática. Com o tempo, perfis, filmografias, coleções e catálogos de empresas podem ficar desatualizados, mesmo que as páginas continuem lendo corretamente os dados locais.

O reparo explícito já existe para filmografias, coleções e empresas por meio da API administrativa, mas ainda não há uma interface administrativa para essas ações nem refresh manual do perfil TMDb de uma pessoa.

## Objetivo

Renovar automaticamente snapshots externos antigos, sem transformar provedores externos na fonte de leitura das páginas e sem bloquear ingestões, navegação ou autenticação.

Esta é uma feature desejada para depois da autenticação e autorização. Ela não bloqueia esse próximo passo.

## Escopo

- perfil TMDb de pessoas;
- filmografia de filmes de pessoas;
- filmografia de séries de pessoas;
- membros de coleções de filmes;
- filmes associados a empresas;
- ações de refresh na futura área autenticada de administração;
- refresh administrativo do perfil TMDb de uma pessoa.

## API e UI

Continuam disponíveis como mecanismos de reparo explícito:

- `POST /api/admin/people/{personId}/tmdb-filmography`;
- `POST /api/admin/people/{personId}/tmdb-show-filmography`;
- `POST /api/admin/movies/collections/{collectionId}/tmdb-members`;
- `POST /api/admin/movies/companies/{companyId}/tmdb-members`.

A implementação futura deve adicionar uma ação equivalente para o perfil TMDb da pessoa. Depois da autenticação, essas ações podem ser expostas somente em áreas autenticadas de curadoria ou administração; não devem aparecer como fluxo principal das páginas públicas.

## Regras

- Uma falha temporária continua elegível para nova tentativa depois de um dia.
- Um snapshot concluído volta a ser elegível inicialmente depois de 30 dias.
- O intervalo de renovação deve usar uma única configuração compartilhada enquanto não houver necessidade real de políticas por entidade.
- Uma renovação com falha preserva integralmente o último snapshot bem-sucedido.
- Páginas continuam lendo apenas dados locais; a abertura de uma página não dispara chamadas ao provedor.
- A ação administrativa explícita ignora os intervalos automáticos.
- Workers continuam usando lotes pequenos, proteção contra concorrência e limites do provedor.

## Non-goals

- Implementar a renovação antes da autenticação.
- Atualizar snapshots a cada acesso de página.
- Criar uma fila externa ou infraestrutura distribuída.
- Definir intervalos diferentes por entidade sem uma necessidade observada.
- Remover ou substituir ações administrativas de reparo.

## Critérios de aceitação

- Snapshots concluídos há pelo menos 30 dias tornam-se novamente elegíveis.
- Snapshots mais recentes não são consultados novamente.
- Falha de renovação mantém os dados locais anteriores visíveis e registra a tentativa.
- Sucesso substitui atomicamente o snapshot e atualiza seu timestamp.
- Refresh administrativo funciona imediatamente, independentemente do último sucesso ou falha.
- O perfil TMDb de pessoa possui refresh administrativo equivalente aos demais snapshots.
- As ações visuais de refresh são acessíveis somente depois da autenticação.
- Testes cobrem elegibilidade temporal, preservação após falha e atualização após sucesso.
