# Imagens de séries no Visual Library

## Problema

Capas e imagens de séries importadas pelo Plex ou TMDb são gravadas no armazenamento local. Filmes já podem usar o Visual Library.

## Objetivo

Usar o mesmo armazenamento de imagens para séries, preservando a capa escolhida e os vínculos da galeria.

## Escopo

Integrar os fluxos existentes de importação e atualização de imagens de séries ao Visual Library. Incluir as referências locais de capa e galeria de séries na migração opcional de imagens.

## API e UI

Reutilizar a configuração e a leitura de imagens do Visual Library. O detalhe de série continua recebendo as URLs pelo contrato atual. A mudança não requer novos controles nem migração de banco.

## Fora do escopo

Seletor de imagens alternativas do TMDb, edição da galeria e imagens de episódios ou temporadas.

## Regras

- Usar o Visual Library quando habilitado e o armazenamento local quando desabilitado.
- Registrar o ID e o título da série na referência de origem, seguindo a convenção usada em filmes.
- Preservar a capa e as referências atuais se o envio falhar. Na migração, confirmar o envio e atualizar as referências antes de remover o arquivo local, conforme o mecanismo existente.
- Usar a configuração de migração existente. A implantação por si só não solicita uma migração manual.

## Critérios de aceite

- Imagens do Plex e TMDb aparecem no detalhe de série quando armazenadas no Visual Library.
- O modo local continua funcionando e falhas de envio preservam o acervo existente.
- A migração opcional inclui capa e galeria de séries, mantém a imagem principal e pode ser retomada após falha.
