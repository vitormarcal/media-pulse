# Foto de artista via Wikimedia Commons

## Decisão

Artistas vinculados ao MusicBrainz podem receber automaticamente a imagem principal indicada pelo Wikidata. O Media Pulse resolve o relacionamento `wikidata`, consulta somente a declaração `P18` pela ação Wikibase `wbgetclaims`, consulta o arquivo pela API `imageinfo` do Wikimedia Commons e armazena localmente uma miniatura de até 1200 px.

A imagem é aplicada somente quando `artists.profile_image_url` está vazio. Atualizações posteriores nunca substituem a foto existente. Galeria, upload e escolha de alternativas não fazem parte deste corte.

## Origem e atribuição

O banco preserva a entidade Wikidata, nome do arquivo, URLs original e de descrição, autoria, licença e URL da licença quando o Commons as fornece. Esses dados aparecem no painel de curadoria; o hero permanece visualmente limpo.

Metadados ausentes não são inventados. As imagens continuam sujeitas à licença declarada por seus autores.

## Falhas e validação

A etapa de imagem é independente do snapshot MusicBrainz. Ausência de vínculo, ausência de `P18`, indisponibilidade do provedor, MIME não suportado ou conteúdo inválido registram uma tentativa sem impedir os demais dados do artista.

Respostas HTTP inválidas do Wikidata são registradas com URI, status, `Content-Type`, `x-request-id` e até 2.000 caracteres do corpo normalizado. Esse diagnóstico fica localizado no cliente Wikimedia; a orquestração registra apenas um resumo e continua preservando os demais dados do artista.

Durante a validação em container, `Special:EntityData/{id}.json` devolveu `400` em HTML exclusivamente para a requisição criada pelo WebClient, embora o mesmo endereço respondesse `200` via `curl` no host e no namespace de rede do container. A troca para `/w/api.php` revelou um `401` com `Jwt issuer is not configured`: o builder HTTP compartilhado havia herdado o token do TMDB/Hardcover e o enviava ao Wikidata. Os clientes agora clonam o builder antes de adicionar configuração própria, e o cliente Wikimedia remove defensivamente qualquer `Authorization`. A integração usa `wbgetclaims&property=P18`, evitando transferir ou desserializar declarações não relacionadas cujos valores podem ser objetos em vez de texto.

Para validar:

1. vincule ou atualize individualmente um artista sem foto;
2. confirme que a imagem passa a usar `/covers/wikimedia/artists/{artistId}/...`;
3. confira atribuição e link original no painel MusicBrainz;
4. atualize novamente e confirme que a imagem não é substituída;
5. teste um artista sem imagem no Wikidata e confirme que o restante do enriquecimento permanece saudável.
