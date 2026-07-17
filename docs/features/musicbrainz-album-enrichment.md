# Enriquecimento de álbum com MusicBrainz

## Status

Primeiro corte implementado. O segundo corte também permite criar um artista a partir do MusicBrainz e importar release groups selecionados de sua discografia.

Esta spec registra a decisão de começar pelo enriquecimento assistido de um álbum já existente. A criação manual de artistas e a importação de discografias ficam para incrementos posteriores.

## Problema

As páginas de álbum e artista não oferecem uma forma de reconciliar seus dados com o MusicBrainz. O enriquecimento existente é operacional, limitado a gêneros e pressupõe que o identificador MusicBrainz armazenado para um álbum seja um `release`.

A tabela `external_identifiers` registra apenas `provider = MUSICBRAINZ` e o UUID. Ela não informa qual entidade do MusicBrainz o UUID representa. Isso torna ambíguos IDs de artista, release group, release e recording e dificulta evoluções como importar discografias ou atualizar tracklists.

No modelo do MusicBrainz:

- `artist` identifica o artista;
- `release-group` representa o disco como conceito editorial;
- `release` representa uma edição específica, como CD, vinil, deluxe ou lançamento de determinado país;
- `recording` representa a gravação de uma faixa.

Consequentemente, a identidade MusicBrainz principal do `Album` canônico deve ser um `release-group`. Um `release` pode coexistir como referência à edição usada para capa, mídia ou tracklist.

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
3. Não buscar nem inserir a discografia neste incremento.

### Persistência

Uma migration será necessária na implementação para registrar o tipo da entidade externa em `external_identifiers`, por exemplo:

```text
provider: MUSICBRAINZ
external_entity_type: ARTIST | RELEASE_GROUP | RELEASE | RECORDING
external_id: UUID
```

Um álbum poderá manter simultaneamente:

```text
ALBUM  MUSICBRAINZ  RELEASE_GROUP  <MBID do disco canônico>
ALBUM  MUSICBRAINZ  RELEASE        <MBID de uma edição conhecida>
```

O desenho final da migration deve preservar os identificadores já armazenados e garantir que o mesmo vínculo tipado não seja duplicado. Não criar providers artificiais como `MUSICBRAINZ_RELEASE`.

## API/UI

A forma exata dos endpoints será definida durante a implementação, mantendo controllers finos e orquestração em services. O contrato precisa representar três operações distintas:

1. pesquisar candidatos sem alterar dados locais;
2. obter ou montar uma prévia do candidato selecionado;
3. confirmar e aplicar o enriquecimento.

As operações devem pertencer ao domínio de música, sob `/api/music`, ainda que reutilizem o cliente de integração MusicBrainz. Endpoints operacionais de enriquecimento em lote não devem ser reutilizados diretamente pela UI.

Na interface, a ação deve integrar-se ao modo de edição e aos componentes existentes das páginas de álbum e artista. A prévia deve deixar claro quais campos serão adicionados e quais dados locais serão preservados. Seguir `DESIGN.md`: superfícies quentes, cantos generosos, pouca elevação e vermelho apenas na ação primária.

## Non-goals

- criar artista manualmente;
- importar a discografia completa de um artista;
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
- `release` identifica somente uma edição e será necessário futuramente para tracklist, formato, país e capas específicas.
- Dados locais preenchidos não devem ser sobrescritos silenciosamente.
- O ano do MusicBrainz só preenche um ano local ausente neste primeiro corte.
- Gêneros e tags devem manter informação de origem para permitir inspeção e correção.
- A confirmação deve ser idempotente: reaplicar a mesma correspondência não cria vínculos nem termos duplicados.
- Trocar uma correspondência existente exige confirmação explícita.

### Identificadores legados

O código atual chama `/ws/2/release/{MBID}` e, portanto, trata o identificador MusicBrainz existente de álbum como `release`.

Durante a migration ou reconciliação:

1. preservar o MBID legado;
2. tentar resolvê-lo como `release`;
3. quando resolvido, armazená-lo como `RELEASE` e obter seu `release-group` para criar o vínculo `RELEASE_GROUP`;
4. não reclassificar como `RELEASE_GROUP` apenas por suposição;
5. se o ID não puder ser resolvido, mantê-lo identificável como legado/indeterminado para revisão, sem descartá-lo.

O enriquecimento atual de gêneros deve ser adaptado para preferir o `release-group`. O fallback por `release` pode continuar enquanto houver dados legados.

## Critérios mínimos de aceite

- A página de um álbum existente permite pesquisar candidatos no MusicBrainz.
- Nenhuma busca ou prévia altera dados locais.
- O owner consegue selecionar, revisar e confirmar um `release-group`.
- A confirmação persiste o tipo do identificador MusicBrainz sem ambiguidade.
- O artista correspondente pode ser vinculado ao MBID de `artist`.
- Ano ausente e gêneros/tags são enriquecidos sem sobrescrever os demais dados canônicos.
- Repetir a confirmação é idempotente.
- IDs legados são preservados e reconciliados sem reclassificação cega.
- A página do artista mostra e permite revisar seu vínculo MusicBrainz.
- Há testes de service para seleção/aplicação, idempotência, troca de vínculo e tratamento de ID legado.
- Migration, endpoints e comportamento são documentados em `README.md`, `docs/music-api.md` e `docs/openapi.yaml` durante a implementação.

## Evolução posterior

Esta base deve permitir a seguinte sequência sem fazer parte desta entrega:

```text
Artista local
  -> MusicBrainz artist
    -> release groups da discografia
      -> criar ou reconciliar álbuns locais
        -> escolher uma release/edição
          -> importar tracklist e capa
```

O fluxo `Adicionar artista` pesquisa e confirma uma identidade MusicBrainz antes de criar o registro local. Em seguida, `Buscar outros discos deste artista` classifica release groups como já vinculados, possível correspondência ou ausentes. Somente ausentes podem ser importados; álbuns e EPs aparecem por padrão e singles são opt-in. A importação cria álbum, ano, vínculo `RELEASE_GROUP` e termos MusicBrainz, sem capa, edição ou tracklist.
