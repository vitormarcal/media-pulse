# Revisão do detalhe de artista musical

Status: aprovado, implementado e validado localmente.

## Problema

O detalhe coloca estatísticas e manutenção antes das obras. Repete acervo, reproduções e gêneros, mistura “library”/“plays” com português e usa títulos redundantes. A imagem tem fundo duplicado, altura fixa e recorte; na ausência de retrato, uma capa de álbum é exibida como se representasse o artista. Datas de nascimento/falecimento podem aparecer como período de atividade. Ferramentas de importar discografia e gêneros ocultos também ocupam a leitura pública. Não há biografia no contrato atual.

## Objetivo

Valorizar identidade, obras e relação pessoal com o artista, seguindo PRODUCT_CONSTITUTION.md e as seções 17/19 de DESIGN.md. Compartilhar superfícies, tipografia e navegação com álbuns, preservando as particularidades de pessoas e grupos.

## Escopo e experiência

1. Cabeçalho branco compacto sobre fundo quente: nome, origem e identificação quando disponíveis. Pessoa usa nascimento/falecimento; grupos usam formação/encerramento, sem inferir atividade a partir dessas datas. Outros nomes ficam recolhidos. Retrato real integral, ampliável pelo visualizador compartilhado, com autoria/fonte/licença públicas. Sem retrato, não usar capa de álbum nem reservar coluna vazia.
2. Introdução biográfica breve, com expansão, pt prioritário/en identificado, vinculada à entidade correta MusicBrainz/Wikidata/Wikipedia. Reaproveitar a abordagem de texto dos álbuns, preservando contextos de domínio distintos. Guardar texto e proveniência localmente; prévia, comparação e confirmação antes de aplicar. Não tratar desambiguação MusicBrainz como biografia.
3. Álbuns no acervo imediatamente após a identidade. Capas quadradas, títulos e créditos legíveis, poucos metadados. Exibir a contagem de álbuns uma vez e oferecer ordenação Mais ouvidos (padrão, preservando a prioridade pessoal atual) / Ano de lançamento (crescente, sem ano ao fim). Usar grid responsivo por linhas para respeitar a ordenação, restrito ao detalhe do artista, sem modificar a biblioteca inteira. Preservar colaborações e links para todos os artistas creditados. Chamar a coleção local de Álbuns no acervo, sem apresentá-la como discografia completa.
4. Mais ouvidas por você: lista compacta das até 12 faixas retornadas, omitindo itens sem reproduções do ranking. Álbum de referência navegável e última escuta identificada. Sem adicionar 🔥 a um ranking que já comunica esse destaque.
5. Histórico de escuta: total de reproduções de faixas, primeira data registrada e última escuta; dias recentes sob demanda. Usar datas legíveis e evitar percentuais de cobertura: álbuns creditados e faixas atribuídas usam recortes distintos no contrato atual.
6. Sobre o artista: gêneros ativos navegáveis, links e nomes alternativos sob demanda. Edição contextual, com ocultos/origem/controles somente durante gerenciamento pelo owner. Atualizar dados revela o painel imediatamente junto do botão; inclui busca e entrada manual de link /artist/{UUID} ou UUID, com correspondência visível antes da confirmação. Preservar feedback e estado nas atualizações.
7. Explorar discografia fica junto de Álbuns no acervo, sob demanda e somente para o owner. Preservar classificação de já vinculado/possível correspondência/ausente e confirmação da importação. Revisar álbuns duplicados fica nessa mesma área, como ferramenta secundária.

## API e arquivos principais

Reutilizar GET /api/music/artists/{artistId}, perfis, gêneros e operações MusicBrainz/discografia existentes. O contrato já fornece retrato com proveniência, identidade, aliases, links, álbuns e histórico diário. A primeira data registrada é derivável dos dias existentes; duração e eventos de sessões não serão adicionados.

A biografia exige extensão aditiva do contrato e persistência opcional via Flyway, com consulta explícita e confirmação de snapshot. Reutilizar o cliente Wikimedia e o padrão de prévia do álbum, sem dependência nova e sem chamadas remotas ao abrir a página. Não alterar silenciosamente a semântica de vínculos/créditos existentes.

Arquivos principais: pages/music/artists/[id].vue; ArtistPageHero, ArtistContextPanel, ArtistTrackList, ArtistGenresPanel, ArtistMusicBrainzPanel, ArtistMusicBrainzDiscography; componente de álbuns específico do detalhe se necessário; types/music.ts e utils/music.ts. Backend: DTO/query de artista e serviços/controller de enriquecimento, cliente Wikimedia e persistência da biografia, migração e testes. Atualizar DESIGN.md, docs/music-api.md e docs/openapi.yaml.

## Regras e fora do escopo

- Não confundir popularidade global, acervo local e histórico pessoal, nem álbuns com alguma reprodução e audições completas.
- Preservar atribuição da foto, licenças e fonte da biografia; falha externa mantém o texto salvo e o restante da página utilizável.
- Sem texto/imagem, não acrescentar grandes placeholders. Nomes longos e scripts diversos devem quebrar corretamente.
- Ferramentas de manutenção ficam próximas de seus gatilhos, acessíveis ao owner, sem blocos vazios para visitantes.
- Novas fotos, Visual Library para artistas, membros de bandas, créditos de produção, favoritos/notas/comentários de artista, player, recomendações e mudanças globais de cards/biblioteca ficam fora do escopo.

## Critérios de aceite

- Conferir pessoa e grupo, nomes longos, colaboração, ausência de foto/biografia, nenhum álbum, álbuns sem ano, nenhuma escuta e gêneros ocultos, em desktop e celular.
- Verificar ordenações, links, retrato ampliável, Escape, foco, atribuição, expansão de biografia e ausência de overflow.
- Validar vínculo manual, prévia/confirmacão de biografia, falha de provedor, retry e leitura com dados locais; nenhum dado é gravado apenas pela busca.
- Preservar edição/restauração de gêneros, atualização de perfil, importação de discografia e revisão de duplicatas, incluindo feedback após refresh.
- Checks/build do frontend; ktlintFormat, build e testes proporcionais do backend, incluindo migração/persistência e resolução da biografia.

## Implementação e validação

- Flyway V79: snapshot opcional em artist_biographies, com vínculo MusicBrainz e proveniência completa. Leitura pública oculta o texto de um vínculo anterior. Prévias duram 15 minutos, limitadas a 100 na instância; confirmação verifica vínculo e conteúdo atual sob bloqueio antes de gravar o snapshot revisado. Ausência de texto não gera token nem remove conteúdo salvo.
- A consulta Wikimedia e o componente de introdução foram extraídos para compartilhamento; wrappers/aliases mantêm o contrato e o comportamento dos álbuns.
- Backend: ktlintFormat, bootJar e 39 testes sem falhas ou skips, incluindo PostgreSQL com todas as migrações, round trip de JSON/proveniência, biografia com vínculo alterado, exclusão em cascata, prévia sem escrita, confirmação exata, falhas e regressão dos álbuns. Neste daemon Docker, Testcontainers foi executado com JAVA_TOOL_OPTIONS=-Dapi.version=1.44.
- Frontend: lint, formatação, tipos e build; validação das referências manuais por formato/host/tipo.
- Chromium em 1440px e 390px, owner/visitante, com APIs simuladas: pessoa/grupo, datas identificadas, ordenação de álbuns com colaboração e sem ano, ranking sem itens não ouvidos, retrato integral/ampliável, Escape/foco/scroll e atribuição; biografia expansível, consulta sem artigo/falha externa, prévia sem escrita, erro na confirmação/retry, atualização com feedback preservado; inclusão/restauração de gêneros, discografia e importação; ausência de retrato/biografia/perfil/acervo/escutas e ausência de overflow.
- Regressão de álbum: visualizador, contexto, vínculo manual MusicBrainz e preservação de estados em desktop/celular.
- Publicação solicitada posteriormente na versão 1.22.0, com tag Git v1.22.0 e imagens 1.22.0/latest. Implantação no Mordor pelo serviço Compose existente; Flyway aplica V79 na inicialização.
