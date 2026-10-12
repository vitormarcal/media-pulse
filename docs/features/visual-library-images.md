# Imagens centralizadas no Visual Library

## Problema e objetivo

Centralizar novas imagens de revistas e artes visuais no Visual Library, mantendo os registros culturais no Media Pulse e preservando a leitura das páginas quando uma imagem estiver indisponível.

## Escopo aprovado

- Flag desligada por padrão; habilitar para novos uploads de capas de revistas, imagens de obras e retratos de artistas.
- Upload pelo Media Pulse, armazenamento no Visual Library e referência estável pelo ID do asset.
- Preservar imagens locais existentes e validações atuais de JPEG/PNG, 10 MB e 40 megapixels.
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

Migração de imagens existentes, seleção de assets já presentes, sincronização contínua de tags, limpeza automática, exclusão externa e integração com outros domínios.

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
