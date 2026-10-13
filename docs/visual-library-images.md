# Armazenamento de imagens no Visual Library

O Visual Library pode ser usado como armazenamento próprio do arquivo pessoal. Novas capas de revistas, imagens de obras, retratos de artistas, capas de livros/edições e imagens de jogos participam. Metadados, leituras, experiências, legendas, origem e imagem principal continuam no banco do Media Pulse.

## Configuração

| Variável | Default | Comportamento |
| --- | --- | --- |
| `VISUAL_LIBRARY_ENABLED` | `false` | Usa Visual Library para novos uploads nos domínios acima. |
| `VISUAL_LIBRARY_BASE_URL` | vazio | URL HTTP/HTTPS alcançável pelo backend, sem credenciais, query ou fragmento. Obrigatória quando a flag está ligada. |

Exemplo neutro em rede Docker:

```env
VISUAL_LIBRARY_ENABLED=true
VISUAL_LIBRARY_BASE_URL=http://visual-library:3000
```

Não é necessário expor esse hostname ao navegador: ele consulta o backend do Media Pulse. Nenhuma credencial é enviada; a API atual do Visual Library não tem autenticação. Configuração inválida impede inicialização. O armazenamento de imagens não exigiu migration; movimentos múltiplos acrescentam a V67 no Media Pulse. O backend inclui o [plugin ImageIO WebP do TwelveMonkeys](https://github.com/haraldk/TwelveMonkeys) para validar esse formato.

## Upload e leitura

A integração partiu do contrato de upload do Visual Library **0.6.0**. Movimentos múltiplos exigem a extensão de grupos vinculados descrita ao final.

O backend preserva as validações existentes de JPEG/PNG/WebP, 10 MB e 40 megapixels, então envia multipart para `POST /api/assets`, com `image`, `source=media-pulse`, `hiddenFromGallery=false`, `tags` (array JSON em campo texto) e `sourceReference` (objeto JSON em campo texto). As imagens ficam disponíveis na galeria central. Deduplicação aceita tanto 201 quanto 200; a origem e visibilidade retornadas podem ser as do primeiro envio dos mesmos bytes.

As referências usam o ID real do Media Pulse convertido em **string**. O tipo separa entidades de tabelas diferentes, mesmo quando seus IDs numéricos coincidem:

| Imagem | `entityType` | Sugestões de tags, em ordem |
| --- | --- | --- |
| Capa de revista | `magazine_issue` | `revista`, nome da publicação |
| Imagem de obra | `artwork` | movimentos em grupo próprio; `arte visual`, título, artista, tipo e técnica como sugestões complementares |
| Retrato de artista | `artwork_artist` | `artista`, nome, país de origem |
| Capa de livro | `book` | `livro`, título |
| Capa de edição | `book_edition` | `livro`, título da edição |
| Imagem/capa de jogo | `game` | `jogo`, título |

Não enviar `url` na referência: a integração não exige uma URL pública do Media Pulse e não deve usar o endereço interno do backend como link público. O campo é opcional na API. Exemplo de referência: `{"entityType":"artwork","entityId":"123"}`.

Sugestões omitem valores vazios e nomes acima de 48 caracteres UTF-16, normalizam espaços, deduplicam sem diferenciar caixa e limitam a oito tags. Não cortar títulos ou nomes para produzir tags incompletas. Dados completos permanecem no Media Pulse.

Na 0.6.0, tags são aditivas; origem e visibilidade do primeiro upload continuam preservadas. Reenvio da mesma associação asset + aplicação + tipo + ID não reaplica tags nem altera a referência, respeitando remoções pessoais. Nova referência pode acrescentar sugestões; não há sincronização automática de edições de metadados ou remoções de vínculos. Assets anteriores não recebem backfill automático.

**Sintoma:** HTTP 400 ao enviar sugestões para um asset já existente. **Hipótese:** a união com tags pessoais ultrapassa as oito vagas; mensagens de erro não são usadas como contrato. **Decisão:** somente para 400 e sugestões não vazias, repetir uma vez com `tags=[]` e a mesma referência. Isso preserva tags existentes e permite registrar a associação sem sugestões. Falha da segunda tentativa usa o tratamento de erro normal. Não repetir outros status por essa regra. Cada chamada continua limitada a 30 segundos; esse caminho pode fazer duas chamadas.

Em revistas novas, validar e persistir o número e a jornada dentro da transação antes do upload para obter um ID real. A transação confirma mesmo se o upload remoto falhar, retornando `coverUploadError`; erros de validação/banco continuam revertendo dados locais. Capas locais continuam sendo compensadas após rollback, e referências remotas podem permanecer sem vínculo após rollback.

Os campos de imagem guardam `/api/image-assets/{uuid}/content`, uma referência relativa que identifica o provedor e o ID estável, sem hostname. A URL base pode mudar sem alterar o banco. Imagens antigas `/covers/...` permanecem locais. Não há migração automática do acervo. Livros/edições que já possuem capa preservam o vínculo atual, inclusive capas locais e externas, sem baixar novamente durante a sincronização.

`GET /api/image-assets/{id}/content` recupera os bytes pelo backend. Preserva Content-Type, Cache-Control, ETag, Last-Modified e respostas condicionais 304. Encaminha If-None-Match e If-Modified-Since. UUID inválido retorna 400; asset ausente retorna 404; serviço sem configuração, indisponível ou resposta ilegível retorna 503. Download é limitado a 10 MB em memória por requisição, adequado às imagens aceitas nesta entrega; sem transformações ou miniaturas. Cada chamada usa o cliente de imagens existente e um limite de 30 segundos.

A flag controla somente novos uploads. Ao desligá-la, mantenha `VISUAL_LIBRARY_BASE_URL` para continuar exibindo assets já vinculados. Não há fallback automático de upload para o disco local.

## Falhas e novas tentativas

**Sintoma:** Visual Library indisponível, recusando upload ou retornando resposta sem ID válido. **Causa:** gravação externa não participa da transação PostgreSQL. **Decisão:** preservar dados válidos e explicitar a falha da imagem.

- Revistas: criação/edição retorna 201/200 com dados persistidos e `coverUploadError` legível. Nova revista fica sem capa; substituição malsucedida preserva a capa anterior. O formulário mantém o arquivo, mostra o erro e o link do número salvo, e oferece nova tentativa apenas da capa. `POST /api/magazines/{id}/cover`, multipart `cover`, retorna detalhe atualizado e `coverUploadError` nulo ou preenchido. Essa operação não altera metadados ou jornadas.
- Artes visuais/retratos: upload retorna 503 com erro legível. Obra/artista e imagens anteriores permanecem. Os formulários existentes conservam itens pendentes para nova tentativa.
- Leitura: APIs de domínio consultam apenas o banco. Falha no download de uma imagem mostra placeholder nos componentes usados por revistas e artes visuais e não bloqueia textos, históricos ou ações.
- Validação local: arquivo inválido continua retornando 400 antes do envio. Multipart acima do limite global retorna 413. Falhas do banco continuam retornando erro e não são tratadas como sucesso parcial.

## Retenção e backup

No Media Pulse, remover/substituir uma imagem remota ou excluir seu registro remove apenas a associação. Não excluir o asset: ele pode ser compartilhado pela deduplicação e a API externa não oferece exclusão. Rollback após upload pode deixar assets sem vínculo; sem rotina automática de limpeza nesta entrega. Capas locais de revistas continuam sendo removidas após commit e compensadas após rollback.

Excluir um asset na interface do Visual Library quebra os vínculos do Media Pulse. ID estável não assegura retenção. O cache do navegador pode continuar exibindo uma imagem por até uma hora depois de excluída.

O backup canônico passa a incluir PostgreSQL do Media Pulse, volumes locais de covers existentes, e banco SQLite **e** arquivos de imagens do Visual Library. Centralizar armazenamento não dispensa o backup do serviço próprio.

## Validação

Testes de contrato com servidor HTTP local validam JSON UTF-8 em multipart, IDs como strings, normalização/limites de sugestões, retry sem tags em HTTP 400, respostas 200/201, ID inválido, bytes, cache condicional, 404/503 e leitura com flag desligada. Testes de serviço validam envio sem cópia local, conteúdo inválido e retenção remota. Integração PostgreSQL valida cadastro com falha de upload, persistência da leitura, retry sem duplicação e preservação da capa anterior.

```bash
JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./server/gradlew -p server test \
  --tests '*VisualLibraryClientTest' --tests '*Magazine*Test' \
  --tests '*ArtworkImageServiceTest' --tests '*ArtworksControllerTest' \
  --tests '*ArtworkIntegrationTest'
```

Também foi validado `bootRun` com PostgreSQL e Visual Library 0.6.0 temporários: upload de capa, obra e retrato com tags e referências corretas, deduplicação, recuperação dos bytes sem cópia local, e preservação dos dados após desligar o serviço de imagens. Reenvio da mesma associação preservou tags editadas manualmente; uma nova associação a um asset com oito tags pessoais confirmou o retry sem sugestões e a preservação dessas oito tags. O frontend foi conferido em Chromium, com retry de capa e placeholders em 390 e 1280 pixels.

No navegador, interromper uploads/leitura de imagens e conferir placeholders, metadados e histórico. Em revistas, criar com upload indisponível, tentar a capa novamente e verificar que há apenas um número e uma jornada. Conferir também imagens locais e referências remotas com a flag desligada.

## WebP

Revistas, obras e retratos aceitam JPEG, PNG e WebP nos dois destinos de armazenamento. Obras/retratos também aceitam WebP por URL pública direta. A validação identifica o formato pelos bytes, lê dimensões e decodifica pixels com ImageIO, usando `com.twelvemonkeys.imageio:imageio-webp:3.15.0` para WebP. Essa dependência permite manter a validação real de imagem sem escrever um decodificador próprio ou adicionar bibliotecas nativas. Limites continuam em 10 MB e 40 megapixels. MIME e extensão do cliente não determinam o formato. Os bytes são preservados; sem conversão para JPEG/PNG. Capas WebP locais usam `.webp` e participam da compensação e remoção existentes.

Validação de WebP cobre imagens estáticas e animadas, MIME/extensão de cliente incorretos, dados truncados, dimensões declaradas acima de 40 megapixels, preservação dos bytes e remoção local. O smoke com `bootRun`, PostgreSQL e Visual Library confirmou envio de capa/obra/retrato, MIME `image/webp` na leitura pelo proxy e funcionamento local com a flag desligada, incluindo exclusão da capa.

## Correções de movimentos

Obras usam o novo contrato de tags vinculadas por referência (`managedTags.movements` no upload e `PUT /api/assets/{id}/references/tags` na edição), com revisão monotônica. A lista inteira é enviada e recebe prioridade sobre sugestões genéricas. Editar os movimentos atualiza todas as imagens remotas após commit, sem bloquear a leitura local. Pendência/mensagem ficam na obra e um endpoint de retry permite recuperar falhas. Consulte [contrato de obras](artworks-api.md) e [spec aprovada](features/artwork-movements.md). Esse contrato exige atualizar também o Visual Library; a 0.6.0 anterior não o possui.

A regra anterior de não atualizar associações permanece para sugestões genéricas de título, artista, técnica e tipo; apenas movimentos têm sincronização nesta entrega. Tags pessoais e antigas sem procedência permanecem protegidas.

## Livros e jogos

Hardcover encaminha capas ausentes de livros e edições ao armazenamento compartilhado depois de persistir os registros. Falhas de download/validação/upload são registradas no log e não revertem metadados ou leituras; uma sincronização posterior tenta preencher capas ainda ausentes. A atualização condicional só preenche `cover_url IS NULL`, preservando uma capa manual salva enquanto o download ocorria.

Jogos usam até três grids do SteamGridDB; sem grid salvo, tentam a capa do IGDB. Os bytes de ambos passam pelas validações de JPEG/PNG/WebP, 10 MB e 40 megapixels. Mesmo com a flag desligada, a capa do IGDB passa a ser armazenada localmente. Uma falha deixa a capa ausente, preserva o cadastro e não grava a URL externa como fallback. Repetir `POST /api/games/catalog` com os mesmos dados tenta novamente se não houver capa/principal; a deduplicação existente mantém o mesmo jogo. Sugestões de catálogo do IGDB continuam usando URLs transitórias do provedor, pois ainda não representam entidades locais.

Uploads manuais usam `POST /api/books/{bookId}/cover` ou `POST /api/games/{gameId}/cover` com multipart `cover`. Sucesso retorna 200 e `{coverUrl}`; inválidos retornam 400, entidade ausente 404, limite multipart 413 e falha remota 503. A imagem é validada/enviada antes da transação de troca do vínculo. Falha preserva a imagem anterior; o formulário mantém arquivo e prévia para repetir a mesma operação. A capa de livro altera apenas `books.cover_url`, não as capas independentes das edições. Em jogos, a transação trava a linha do jogo e troca a seleção de principal junto com a capa. Não modifica histórico, comentários ou notas.

Substituições manuais não excluem imagens anteriores: assets remotos podem ser compartilhados; arquivos locais ficam retidos. Uma falha de banco depois do envio pode deixar uma imagem sem vínculo, como nos demais domínios; sem limpeza automática. Os backups devem continuar incluindo os volumes locais e o Visual Library.

Validação adicional: `LibraryImageStorageServiceTest`, `HardcoverArtworkServiceTest`, `ManualGameCatalogServiceTest`, `LibraryCoversControllerTest` e `LibraryCoversIntegrationTest` (PostgreSQL) cobrem os dois destinos, referências, validação, falhas, retries e seleção única de principal.

### Limite de download do SteamGridDB

**Sintoma:** `DataBufferLimitException: Exceeded limit on max bytes to buffer : 262144` no download de grids, antes do armazenamento. **Causa:** o cliente de metadados era reutilizado para imagens com o limite padrão de 256 KiB; a propriedade global do codec não configura esse WebClient criado manualmente. **Decisão:** cliente próprio de imagens com `imagesWebClientBuilder` e limite explícito de 10 MiB, mantendo o cliente de metadados separado. Respostas HTTP de erro não são tratadas como imagem. Sem nova variável ou migration. **Validação:** `SteamGridDbApiClientTest` usa servidor HTTP local para respostas de 512 KiB, exatamente 10 MiB, acima de 10 MiB e 404; validação de formato e pixels continua no armazenamento compartilhado. Após atualizar a aplicação, “Buscar imagens” no detalhe do jogo permite tentar novamente. A criação deduplicada de um jogo sem capa/principal também tenta as imagens novamente.
