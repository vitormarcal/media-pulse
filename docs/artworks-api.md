# Artes visuais API

Artes visuais é um domínio local de obras descobertas e experiências de contemplação. Não usa progresso, conclusão ou catálogo externo. A migration `V65__add_artworks.sql` cria `artwork_artists`, `artworks`, `artwork_experiences`, `artwork_images`, `artwork_lists` e `artwork_list_items`.

## Contratos

| Método e caminho | Comportamento |
| --- | --- |
| `GET /api/artworks` | Biblioteca paginada; `q`, `artistId`, `type`, `movement`, `favorite`, `listId`, `page=0`, `limit=24` (1–100), `sort=recent` ou `sort=year`. Retorna `{items, nextPage}`. |
| `GET /api/artworks/filters` | Tipos e movimentos existentes: `{types, movements}`. |
| `POST /api/artworks` | Cadastra obra e primeira experiência parcial; retorna detalhe, com status 201. |
| `GET /api/artworks/{id}` | `{artwork, artist, experiences, images, lists, rating}`. |
| `PUT /api/artworks/{id}` | Substitui metadados, preservando favorito, avaliação, imagens, listas e experiências. |
| `DELETE /api/artworks/{id}` | Exclui obra, experiências, imagens, vínculos e avaliação; preserva artistas e listas. |
| `PUT /api/artworks/{id}/favorite` | Body `{favorite: boolean}`; retorna detalhe atualizado. |
| `GET /api/artworks/artists` | Artistas locais em ordem de nome e ID. |
| `GET /api/artworks/artists/{id}` | Dados do artista; obras consultadas na biblioteca com `artistId`. |
| `POST /api/artworks/artists` | Cria artista manualmente; retorna artista, status 201. |
| `PUT /api/artworks/artists/{id}` | Substitui dados, preservando retrato e vínculos. |
| `DELETE /api/artworks/artists/{id}` | Retorna 409 quando houver obras vinculadas; `detach=true` autoriza desvinculação e preserva as obras. |
| `POST /api/artworks/artists/{id}/portrait` | Multipart, campo `image`; retorna artista com retrato local. |
| `POST /api/artworks/artists/{id}/portrait/url` | Body `{url}`; baixa retrato e retorna artista. |
| `DELETE /api/artworks/artists/{id}/portrait` | Remove associação do retrato; retorna artista. |
| `POST /api/artworks/{id}/experiences` | Cria experiência independente, status 201. |
| `PUT /api/artworks/{id}/experiences/{experienceId}` | Substitui experiência pertencente à obra. |
| `DELETE /api/artworks/{id}/experiences/{experienceId}` | Exclui somente essa experiência. |
| `POST /api/artworks/{id}/images` | Multipart, campo `image`, query `caption` opcional; retorna imagem, status 201. |
| `POST /api/artworks/{id}/images/url` | Body `{url, caption?}`; baixa e armazena a imagem, status 201. |
| `PUT /api/artworks/{id}/images/{imageId}/primary` | Escolhe principal; retorna 204. |
| `DELETE /api/artworks/{id}/images/{imageId}` | Remove associação da imagem; promove outra quando a principal é removida; retorna 204. |
| `GET /api/artworks/lists` | Listas com ID, nome, descrição e contagem de obras. |
| `GET /api/artworks/lists/{id}` | Dados da lista; obras consultadas na biblioteca com `listId`. |
| `POST /api/artworks/lists` | Body `{name, description?}`; cria lista, status 201. |
| `PUT /api/artworks/lists/{id}` | Substitui nome e descrição. |
| `DELETE /api/artworks/lists/{id}` | Remove lista e vínculos, preservando obras; retorna 204. |
| `PUT /api/artworks/{id}/lists/{listId}` | Inclusão manual idempotente; retorna 204. |
| `DELETE /api/artworks/{id}/lists/{listId}` | Remove vínculo, preservando obra e lista; retorna 204. |
| `POST /api/ratings/artworks/{id}` | Body `{rating}` entre 1 e 5 pelo contrato compartilhado de avaliação. |
| `DELETE /api/ratings/artworks/{id}` | Remove avaliação sem afetar a obra. |

Deletes de obra, artista e experiência retornam 204. Entidades ausentes e experiências/imagens de outra obra retornam 404; validações de domínio retornam 400 com `detail` legível.

## Cadastro e experiências

Obra exige somente `title`. Metadados opcionais: `artistId` **ou** `artist` (novo artista com `name`), `year`, `type`, `movement`, `technique`, `description`. Tipos e movimentos são textos livres; os filtros sugerem os valores locais. Não deduplicar artistas ou obras automaticamente por nomes. Atualização de metadados não aceita `experience`; usar o histórico.

`experience` no cadastro e o body dos endpoints de experiências têm `experiencedAt` (data ISO), `experienceType` (`DIGITAL_VIEWING` ou `IN_PERSON`), `notes`, `sourceName` e `sourceUrl`, todos opcionais. Cadastro omite a experiência quando todos os campos estiverem vazios. Registro independente exige ao menos um campo. Não inferir data ou tipo; link deve ser HTTP ou HTTPS, sem credenciais. Experiências são ordenadas por data decrescente, sem data ao final, depois por ID decrescente. Cadastro técnico não é data de contemplação.

Artista tem `name`, `biography`, `countryOfOrigin`, `bornAt`, `diedAt`; datas opcionais, falecimento não pode anteceder nascimento. Limites: título 300; nome de artista/lista, país de origem, movimento e técnica 200; tipo 100; origem 300; legenda 500; notas, biografia e descrições 20.000 caracteres. Favorito e avaliação pertencem à obra. Listas não modificam movimento artístico e cada obra aparece uma vez na biblioteca, mesmo com múltiplas experiências/listas. Biblioteca usa `sort=recent` por padrão (ID decrescente). `sort=year` ordena pelo ano de criação crescente, com ID crescente para desempate e anos não informados ao final; a ordenação ocorre antes da paginação. Outras ordenações retornam 400.

## Imagens e operação

Com `VISUAL_LIBRARY_ENABLED=true`, imagens de obras e retratos enviados por arquivo ou baixados de URL vão para o Visual Library, mantendo a URL de origem no Media Pulse. Referência `/api/image-assets/{uuid}/content`, leitura pelo backend; falha de envio retorna 503 e preserva registros e imagens anteriores. Remoções preservam o asset central. As validações abaixo continuam aplicáveis. Veja [configuração e operação](visual-library-images.md).

No modo local (default), usar o armazenamento de capas existente, em `/covers/artworks/{uuid}.{jpg|png}`. Não há provedor novo nem novas variáveis obrigatórias. Arquivos e URLs aceitam JPEG/PNG reais, até 10 MB e 40 megapixels, preservando os bytes e proporções. Downloads exigem URL pública direta HTTP/HTTPS, sem credenciais ou redirecionamentos; tempo de conexão/leitura limitado a 10 segundos. A URL de origem é preservada nas imagens baixadas.

Cada adição de imagem é independente do cadastro da obra. Uma falha preserva obra e imagens já salvas. A primeira imagem é principal; a seleção posterior pode ser alterada. Sem imagens, a UI usa fallback. Exclusões removem associações do banco; arquivos de imagem permanecem no storage, como arquivos não referenciados, sem rotina automática de limpeza nesta entrega.

O frontend oferece `/artworks`, `/artworks/{id}`, `/artworks/artists`, `/artworks/artists/{id}`, `/artworks/lists` e `/artworks/lists/{id}`. Cadastro reúne metadados, artista, primeira experiência e múltiplos arquivos/URLs. URLs são enviadas uma por linha. Falhas de imagens mantêm apenas itens pendentes para nova tentativa; há acesso direto à obra já salva. Galeria e cards preservam a composição integral, sem `object-fit: cover`. Busca no Wikimedia fica no roadmap, com seleção explícita antes de adicionar imagens.

## Validação

Testes de serviço/HTTP cobrem captura parcial, isolamento de experiências, exclusão com desvinculação, imagens inválidas e avaliação compartilhada. `ArtworkIntegrationTest` valida migration/JPA em PostgreSQL, experiências repetidas, filtros, paginação, múltiplas listas, troca de principal e exclusões em cascata.

Em Docker recente com API mínima 1.44, a versão atual do cliente de Testcontainers pode não detectar o daemon com a API padrão. Sem alterar dependências ou configurações locais, validar com `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew test --tests '*ArtworkIntegrationTest'` dentro de `server/`; conferir que o relatório não registra testes ignorados.

## Revisão de cadastro e detalhes

A migration `V66__rename_artwork_artist_country.sql` renomeia `artwork_artists.nationality` para `country_of_origin`. O contrato de artistas passa a usar `countryOfOrigin`. Os valores existentes são preservados literalmente para revisão manual; a migração não converte nacionalidades em países nem modifica obras ou vínculos.

Tipo usa opções Pintura, Escultura, Fotografia artística, Desenho, Gravura e Outra. Outra permite texto próprio; tipos personalizados existentes continuam editáveis. Movimento e técnica têm exemplos nos campos. No cadastro, descrição, biografia e dados adicionais do artista são apresentados sob demanda.

Nos detalhes, a galeria ampla reúne todas as imagens com navegação, miniaturas e ampliação na página. A seleção começa pela principal e navegar não altera essa preferência salva. O visualizador ocupa toda a tela com imagem ajustada à maior área disponível, sem recorte, e controles compactos sobrepostos nas bordas. Aceita Escape e setas e devolve o foco ao controle de origem. Descrição e biografia curtas aparecem completas; acima de 700 caracteres, um trecho inicial pode ser expandido e recolhido. Artista tem retrato opcional ao lado de nome, país, datas e biografia, com suas obras locais agrupadas por ano de criação e obras sem ano ao final; filtros ficam sob demanda. Gerenciar imagens abre a seleção de principal, remoção e adição por arquivo/URL; a visualização das imagens fica na galeria superior. Gerenciar listas abre os controles de vínculos, mantendo as listas atuais visíveis. Excluir obra/artista aparece ao abrir a edição. O cadastro permanece manual.

## Metadados de imagem no Visual Library 0.6.0

Uploads remotos incluem sugestões de tags derivadas dos metadados existentes e referência à entidade com tipo e ID real em string. A referência é enviada também em substituições e novas tentativas, evitando reaplicar tags da mesma associação. Em revistas o tipo é `magazine_issue`; obras usam `artwork` e retratos `artwork_artist`. Omitir sugestões longas ou vazias não remove dados do Media Pulse. Ver [mapeamento, limites e tratamento de recusas](visual-library-images.md). Não há backfill automático nem sincronização contínua de tags.
