# Enriquecimento de álbum com MusicBrainz

## Status

Implementado. O fluxo também permite criar um artista a partir do MusicBrainz e importar release groups selecionados de sua discografia. O modelo persistente atual usa estruturas específicas por entidade, descritas em `docs/music-api.md`.

Esta spec registra o enriquecimento assistido de álbuns e sua evolução para criação de artistas e importação seletiva de discografias.

## Problema

Álbuns e artistas precisam ser reconciliados com o MusicBrainz sem entregar ao provedor a identidade do catálogo local. A correspondência externa deve ser explícita, revisável e capaz de distinguir o trabalho canônico das edições conhecidas usadas durante a ingestão.

No modelo do MusicBrainz:

- `artist` identifica o artista;
- `release-group` representa o disco como conceito editorial;
- `release` representa uma edição específica, como CD, vinil, deluxe ou lançamento de determinado país;
- `recording` representa a gravação de uma faixa.

Consequentemente, a identidade MusicBrainz principal do `Album` canônico é um `release-group`. Um ou mais `release` podem coexistir como aliases técnicos de edições conhecidas usadas na ingestão.

Referências oficiais:

- [MusicBrainz: Release Group](https://musicbrainz.org/doc/Release_Group)
- [MusicBrainz: Release](https://musicbrainz.org/doc/Release)
- [MusicBrainz API](https://musicbrainz.org/doc/MusicBrainz_API)

## Objetivo

Permitir que o owner, a partir da página de um álbum existente, encontre e confirme sua correspondência no MusicBrainz e aplique um conjunto pequeno e seguro de dados à visão canônica local.

Na página do artista, o primeiro incremento deve apenas permitir estabelecer ou revisar a identidade MusicBrainz do artista. Importar sua discografia não faz parte deste corte.

## Escopo

### Álbum

1. Exibir a ação `Enriquecer dados` na página do álbum.
2. Pesquisar candidatos de `release-group` usando o título e o artista locais.
3. Mostrar poucos candidatos com título, artista, primeiro ano, tipo e comentário de desambiguação, quando houver.
4. Permitir que o owner selecione um candidato.
5. Mostrar uma prévia das mudanças antes de persistir.
6. Após confirmação:
   - vincular o álbum ao `release-group` escolhido;
   - vincular o artista local ao `artist` correspondente, quando a correspondência for inequívoca no candidato;
   - preencher o ano somente se estiver ausente localmente;
   - adicionar gêneros e tags provenientes do MusicBrainz, preservando sua origem;
   - manter intactos título, artista, capa e tracklist.

### Artista

1. Indicar se o artista está vinculado ao MusicBrainz.
2. Permitir vincular, revisar ou trocar a correspondência do artista.
3. Permitir criar um artista local somente após confirmar sua identidade MusicBrainz.
4. Exibir release groups da discografia classificados como já vinculados, possíveis correspondências ou ausentes.
5. Importar apenas os release groups ausentes selecionados pelo owner.

### Persistência

- `artists.musicbrainz_artist_id` armazena a identidade MusicBrainz `artist` do artista local;
- `albums.musicbrainz_release_group_id` armazena a identidade canônica `release-group` do álbum;
- `album_musicbrainz_release_ids` preserva aliases `release` `0..N` usados para reconhecer edições durante a ingestão;
- `track_musicbrainz_recording_ids` preserva aliases `recording` `0..N` das faixas.

O release group representa o trabalho geral acompanhado pelo produto. Releases são referências técnicas: preservá-los não transforma o álbum local em uma edição comercial específica.

## API/UI

A forma exata dos endpoints será definida durante a implementação, mantendo controllers finos e orquestração em services. O contrato precisa representar três operações distintas:

1. pesquisar candidatos sem alterar dados locais;
2. obter ou montar uma prévia do candidato selecionado;
3. confirmar e aplicar o enriquecimento.

As operações devem pertencer ao domínio de música, sob `/api/music`, ainda que reutilizem o cliente de integração MusicBrainz. Endpoints operacionais de enriquecimento em lote não devem ser reutilizados diretamente pela UI.

Na interface, a ação deve integrar-se ao modo de edição e aos componentes existentes das páginas de álbum e artista. A prévia deve deixar claro quais campos serão adicionados e quais dados locais serão preservados. Seguir `DESIGN.md`: superfícies quentes, cantos generosos, pouca elevação e vermelho apenas na ação primária.

## Non-goals

- criar álbuns automaticamente;
- escolher automaticamente uma edição específica;
- substituir título, artista ou capa;
- criar ou substituir tracklist;
- integrar Cover Art Archive, Wikimedia, Last.fm ou outros provedores;
- criar um framework genérico de enriquecimento multi-provider;
- alterar o enriquecimento em lote além do necessário para compreender IDs tipados.

## Heurísticas e regras

- O vínculo nunca deve ser aplicado automaticamente apenas por semelhança de texto; o owner confirma o candidato.
- A busca deve priorizar título e artista e retornar uma lista curta e compreensível.
- `release-group` é a identidade principal MusicBrainz do álbum canônico.
- `release` identifica somente uma edição e pode orientar resolução de release group, tracklist, formato, país ou capas específicas.
- Dados locais preenchidos não devem ser sobrescritos silenciosamente.
- O ano do MusicBrainz só preenche um ano local ausente.
- Gêneros e tags devem manter informação de origem para permitir inspeção e correção.
- A confirmação deve ser idempotente: reaplicar a mesma correspondência não cria vínculos nem termos duplicados.
- Trocar uma correspondência existente exige confirmação explícita.

O enriquecimento de gêneros prefere o release group canônico e pode usar um release técnico conhecido como fallback. Ao receber um release novo, a ingestão resolve seu release group antes de associá-lo ao álbum; divergências com a identidade canônica já vinculada são rejeitadas.

## Critérios mínimos de aceite

- A página de um álbum existente permite pesquisar candidatos no MusicBrainz.
- Nenhuma busca ou prévia altera dados locais.
- O owner consegue selecionar, revisar e confirmar um `release-group`.
- A confirmação persiste o release group na coluna canônica sem ambiguidade.
- O artista correspondente pode ser vinculado ao MBID de `artist`.
- Ano ausente e gêneros/tags são enriquecidos sem sobrescrever os demais dados canônicos.
- Repetir a confirmação é idempotente.
- Releases conhecidos são preservados como aliases técnicos sem substituir o release group canônico.
- A página do artista mostra e permite revisar seu vínculo MusicBrainz.
- Há testes de service para seleção/aplicação, idempotência, troca de vínculo e resolução de release.
- Endpoints e comportamento permanecem alinhados em `docs/music-api.md` e `docs/openapi.yaml`.

## Discografia e evolução posterior

O fluxo implementado cobre a criação ou reconciliação até a importação seletiva de release groups. A escolha de uma release e a importação de tracklist e capa permanecem como evolução posterior:

```text
Artista local
  -> MusicBrainz artist
    -> release groups da discografia
      -> criar ou reconciliar álbuns locais
        -> escolher uma release/edição
          -> importar tracklist e capa
```

O fluxo `Adicionar artista` pesquisa e confirma uma identidade MusicBrainz antes de criar o registro local. Em seguida, `Buscar outros discos deste artista` classifica release groups como já vinculados, possível correspondência ou ausentes. Somente ausentes podem ser importados; álbuns e EPs aparecem por padrão e singles são opt-in. A importação cria álbum, ano, vínculo `RELEASE_GROUP` e termos MusicBrainz, sem capa, edição ou tracklist.
