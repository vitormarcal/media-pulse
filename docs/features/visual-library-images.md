# Imagens centralizadas no Visual Library

## Problema e objetivo

Centralizar novas imagens de revistas e artes visuais no Visual Library, mantendo os registros culturais no Media Pulse e preservando a leitura das páginas quando uma imagem estiver indisponível.

## Escopo aprovado

- Flag desligada por padrão; habilitar para novos uploads de capas de revistas, imagens de obras e retratos de artistas.
- Upload pelo Media Pulse, armazenamento no Visual Library e referência estável pelo ID do asset.
- Preservar imagens locais existentes e validações atuais de JPEG/PNG/WebP, 10 MB e 40 megapixels.
- Salvar dados válidos da revista mesmo se o upload remoto falhar; permitir tentar apenas a capa novamente.
- Preservar obras, artistas e imagens anteriores em falhas de envio.
- Exibir placeholders quando imagens não carregarem, sem bloquear dados e ações das páginas.

## API/UI

Os campos de imagem existentes recebem `/api/image-assets/{uuid}/content`. A referência relativa contém o ID retornado pelo Visual Library; não contém seu hostname. O backend lê os bytes do endereço configurado e os entrega ao navegador. As consultas de domínio continuam locais e não fazem chamadas ao Visual Library.

Revistas retornam `coverUploadError` em sucessos parciais de criação/edição. O formulário conserva o arquivo e o ID salvo, bloqueia os metadados já gravados e permite repetir o envio por `POST /api/magazines/{id}/cover`, sem criar outro número ou alterar suas leituras. Oferece acesso direto ao número salvo.

## Regras

- Flag controla novos uploads; referências remotas continuam legíveis com a flag desligada, desde que a URL base permaneça configurada.
- Falhas de persistência continuam sendo erros; não apresentar sucesso quando o registro não foi salvo.
- Validações locais de imagens inválidas continuam retornando 400; limites globais de multipart continuam aplicáveis.
- Remoção/substituição de imagem remota remove apenas o vínculo. O asset permanece centralizado, inclusive após rollback do banco do Media Pulse.
- Visual Library é infraestrutura própria do arquivo pessoal, não uma fonte externa de metadados. Sua base e arquivos passam a integrar o backup canônico. Não manter cópias locais duplicadas por padrão.

## Fora do escopo

Migração de imagens existentes, seleção de assets já presentes, sincronização contínua de tags, limpeza automática, exclusão externa e integração com domínios além dos descritos nesta spec.

## Critérios de aceite

- Flag desligada mantém uploads locais; ligada envia imagens ao Visual Library sem gravar cópia local.
- Referências locais e remotas coexistem; trocar a URL base não exige regravar referências.
- Falha de upload remoto preserva o cadastro de revista e oferece nova tentativa somente da capa.
- Falha de substituição preserva a imagem anterior.
- Falha de leitura apresenta placeholder e mantém o restante do conteúdo acessível.
- Exclusões no Media Pulse preservam assets compartilhados.

Detalhes de configuração, retenção, erros e validação: [operação](../visual-library-images.md).

## Evolução para Visual Library 0.6.0

Novos uploads incluem sugestões de tags dos metadados locais e `sourceReference` com tipo e ID real como string: `magazine_issue`, `artwork` ou `artwork_artist`. Referências são enviadas em criação, substituição e retry de capa/retrato, por arquivo ou URL. O reenvio da mesma associação respeita edições pessoais de tags no Visual Library. Sugestões vazias/longas são omitidas; recusas HTTP 400 com sugestões recebem uma tentativa sem tags, mantendo a referência. Sem backfill ou novos domínios. Não altera configuração, frontend, contratos públicos do Media Pulse ou schema.

## Extensão aprovada: livros e jogos

### Problema e objetivo

Livros e jogos precisam centralizar novas imagens dos provedores e permitir escolher uma capa própria, usando o mesmo armazenamento de revistas e artes visuais.

### Escopo e API/UI

- Capas de livros e edições novas/ausentes do Hardcover e imagens novas do SteamGridDB/IGDB usam Visual Library quando habilitado; com a flag desligada, ficam no storage local.
- “Alterar capa” nos detalhes de livro/jogo revela upload manual, prévia e feedback. Em erro, mantém o arquivo para nova tentativa e preserva a capa anterior.
- `POST /api/books/{bookId}/cover` e `POST /api/games/{gameId}/cover`, multipart `cover`, retornam `{coverUrl}`. Capa de livro altera o livro canônico; capas de edições permanecem independentes. Em jogos, a nova capa torna-se principal e imagens anteriores continuam disponíveis.
- Sugestões de tags: `livro` + título para `book`/`book_edition`; `jogo` + título para `game`. Referências usam IDs reais como strings.

### Regras e fora do escopo

Reutilizar flag, proxy, validações, deduplicação e retenção remota existentes. Não fazer backfill/migração do acervo, sincronização de tags ou exclusão remota. Sem novas dependências ou migration. Capas já salvas não são substituídas automaticamente pelas importações; imagens ausentes são elegíveis à nova tentativa. Upload manual altera apenas a capa, sem modificar leituras ou sessões.

### Critérios de aceite

- Novas imagens dos provedores e uploads manuais usam o destino definido pela flag, sem cópia local de novos assets remotos.
- Imagens existentes permanecem válidas e capas manuais sobrevivem à importação, inclusive quando ela começou antes do upload.
- Falhas não apagam registros, capas nem a seleção de principal; retry manual salva somente a capa.
- JPEG/PNG/WebP válidos preservam bytes; dados inválidos são rejeitados; leitura indisponível mostra placeholder.

### Nova tentativa na página do jogo

Ação autorizada: oferecer “Buscar imagens” no detalhe do jogo, usando o card de Catálogo de filmes/séries. Reutilizar os provedores, vínculos e armazenamento existentes; acrescentar imagens, preservar a capa atual, informar carregamento e falhas e atualizar a galeria após sucesso. Endpoint `POST /api/games/{gameId}/images/import`; sem migration, sem alterar sessões/metadados e sem migração do acervo. Aceite: funciona com e sem capa, preserva principal manual, deduplica vínculos e permite retry de falha no mesmo botão.

## Extensão aprovada: migração local dos domínios integrados

A restrição original de não migrar o acervo é ampliada para todos os domínios já integrados: revistas, jogos, obras de arte, retratos de artistas, livros e edições, por rotina opcional configurada por propriedade. Remoção local depende de upload/leitura verificados, commit dos vínculos e ausência de referências locais restantes. Falhas e interrupções permitem retomada. Música, filmes, séries e treinos permanecem fora da migração enquanto não tiverem integração de armazenamento com o Visual Library. Consulte a [spec da migração](visual-library-local-migration.md) e a [operação](../visual-library-images.md).
