# Imagens de filmes no Visual Library

Entrega incluída na versão 1.13.0, com a migration aditiva V77. A migração de arquivos continua opcional pela flag existente.

## Problema

Filmes ainda usam armazenamento local e um mosaico sem visualizador. É necessário centralizar imagens novas e antigas, com curadoria e identificação no acervo visual.

## Objetivo e escopo aprovado

Integrar imagens de filmes vindas de Plex/TMDb ao armazenamento existente e ampliar a migração opcional do acervo local, preservando capa, vínculos e metadados. Enviar sugestões `filme` e título original canônico; sincronizar coleção/franquia no grupo gerenciado `movie-collection`, preservando contribuições pessoais e de outras referências.

## API/UI

Cabeçalho com capa maior, até três prévias e “Ver todas as N imagens”. Galeria pública sob demanda com imagem inteira, miniaturas, contador, setas e Escape. O owner pode definir capa e remover a imagem atual com confirmação, usando as mesmas regras de jogos. A remoção de capa escolhe a primeira imagem restante; a última deixa sem capa. Estado de tags pendente/erro e retry no detalhe.

`POST /api/movies/{movieId}/images/primary` e `DELETE /api/movies/{movieId}/images` recebem `{url}` de um vínculo existente e retornam `{coverUrl}` anulável. `GET/POST /api/movies/{movieId}/collection-tags/sync` consultam estado/solicitam retry. Alterações exigem owner e CSRF.

## Regras

Reutilizar flag, proxy, validação, deduplicação, retenção e migração já existentes. Novas imagens usam `sourceReference={entityType: movie, entityId: ID real}`. Upload não depende do sucesso da classificação de coleção. V77 persiste revisão, pendência e lease; mudanças de coleção, nome e imagens invalidam a fila na mesma transação. Uploads e migrações recebem tags de coleção depois do commit. Sem chamadas ao provedor para derivar tags. Sugestões longas não são truncadas; nomes de coleção inválidos geram pendência e retry.

A migração verifica bytes remotos e vínculos antes de apagar arquivos locais, protege edições concorrentes, consolida vínculos deduplicados preservando principal e permite retomada. Novos imports preservam capas salvas; apenas uma escolha explícita pode substituí-las. Exclusão na galeria remove somente o vínculo local, mantendo o asset e sua referência histórica no Visual Library.

## Non-goals

Séries, música, elenco, diretor, gêneros e palavras-chave como tags. Capas de coleções, pessoas e empresas continuam fora do armazenamento/migração desta entrega. Sem seleção de todo o catálogo de imagens TMDb: reutilizar as opções já disponíveis no enriquecimento.

## Aceite

Imagens novas e antigas centralizadas sem perda; tags genéricas e coleção corretas, preservando tags pessoais; fila recuperável; migração e importações não sobrescrevem capa editada; curadoria persiste após recarregar; galeria e retry funcionam em desktop e celular, com teclado e foco preservados. A imagem anterior desaparece imediatamente ao navegar.

## Validação

Suíte completa do backend aprovada com 620 testes, zero falhas/erros/ignorados, usando `JAVA_TOOL_OPTIONS=-Dapi.version=1.44`. Inclui migração V77 e backfill de assets existentes, invalidação por nome/vínculo/imagens, revisão e lease concorrentes, rollback, falha parcial e retry, remoção da contribuição de coleção, preservação da capa em imports, curadoria JPA e migração compartilhada com consolidação de duplicatas. O cliente HTTP valida referência `movie`, grupo `movie-collection`, revisão numérica e autenticação Bearer mesmo com novos uploads desligados. O contrato de grupos nomeados também foi conferido no código local do Visual Library, sem alterações naquele projeto.

Lint, Prettier, tipos e build estático aprovados. Chromium com APIs simuladas em 1440×1000, 768×1000, 390×844 e 844×390 verificou acesso ao acervo inteiro, capa sem alterar a imagem em visualização, confirmação/cancelamento, erro e retry, última remoção, foco e ausência de overflow. Visões pública/owner foram conferidas com zero a quatro imagens. A miniatura da imagem atual permanece visível ao navegar ou reordenar pela troca de capa. Regressão do seletor de jogos aprovada em 1440, 768, 390 e 320px; carregamento de originais na seleção/galeria conferido em 1440 e 390px.

Smoke com `bootRun`, PostgreSQL e servidor HTTP temporários confirmou inicialização com V77, migração automática com novos uploads desligados, referência e sugestões corretas, principal preservada, verificação/exclusão do arquivo local, aplicação de coleção após commit e leitura PNG pelo proxy. O acervo real não foi alterado.
