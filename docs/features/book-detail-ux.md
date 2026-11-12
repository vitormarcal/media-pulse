# Revisão do detalhe de livro

Status: aprovado, implementado e validado localmente.

## Problema

A revisão editorial existente já oferece título/autores claros, sinopse expansível, leituras compactas e comentários sem contagens redundantes. Contudo, séries e manutenção ocupam o espaço anterior às leituras e impressões pessoais. Visitantes veem séries vazias e um título de comentário sem conteúdo. No celular, a capa pequena aparece antes da identificação e não pode ser ampliada. A ficha de edição omite ISBNs disponíveis e não deixa explícito que representa a leitura mais recente. O estado atual aparece no cabeçalho e novamente na leitura.

Amostra inspecionada em produção: `/books/916_les_liaisons_dangereuses`, em desktop e celular. O título canônico e o título da edição lida diferem; ambos devem ser preservados. O contrato confirma capa canônica do livro, independente das capas de edições.

## Objetivo

Preservar a composição editorial aprovada e priorizar o livro e a memória pessoal, adaptando superfícies e manutenção à linguagem atual de DESIGN.md. Não transformar a revisão em novo sistema de registro de leitura.

## Escopo e experiência

1. Cabeçalho em superfície branca uniforme sobre o fundo quente. Capa à direita no desktop, com título, autores navegáveis, ano quando disponível e sinopse expansível à esquerda, conforme ajuste aprovado pelo owner. A capa ganha presença sem recorte ou fundos artificiais. No celular, identificação, capa e sinopse, com largura confortável de leitura. Sem degradê ou arte duplicada de fundo.
2. Clicar na capa ou em Ver capa abre o visualizador compartilhado com composição integral, Escape, foco e scroll preservados. Alterar capa fica junto dela para o owner, mantendo upload/prévia/retry existentes e efeito apenas na capa canônica. Não introduzir seletor de novas imagens.
3. Leituras aparecem imediatamente após o cabeçalho: status e progresso pertinentes à jornada, datas de início/fim identificadas e releituras preservadas. O estado atual não se repete no cabeçalho. Não mostrar 0% para Quero ler nem 100% para uma leitura já concluída. Não inferir datas ausentes. Quando jornadas usarem edições diferentes, identificar a edição em cada leitura com os dados existentes.
4. Comentários vêm depois das leituras, em superfície branca com texto editorial, sem caixas internas por comentário. Preservar composição sob demanda, criação e edição de comentários locais e identificação da resenha importada do Hardcover como conteúdo importado. Uma data absoluta por registro; edição pode ser indicada discretamente. Não ocultar texto importado só por faltar data nem inventar timestamp. Ocultar a seção vazia para visitantes, mantendo inclusão para o owner.
5. Nota existente é mostrada uma vez, compacta junto da memória pessoal, preservando decimais e escala do dado importado. Não substituir por controles de avaliação inteira de outros domínios.
6. Sobre o livro reúne Séries e Edições. Séries existentes permanecem navegáveis, com gestão junto da seção; ocultar a seção vazia para visitantes e manter acesso à inclusão pelo owner. Pendências e retry de tags das capas ficam no contexto de manutenção do owner, sem ocupar a leitura pública.
7. Identificar Edição da leitura mais recente, mantendo outras edições recolhidas. Mostrar editora, formato, idioma legível quando reconhecido, páginas e ISBNs existentes sob detalhes. Preservar títulos diferentes da obra e da edição, sem deduzir tradução/autoria ou primeira publicação a partir deles.

## API e arquivos principais

Reutilizar BookDetailsResponse: autores, sinopse, nota, resenha importada, comentários, jornadas e edições. ISBN-10/13, progresso e vínculo edição/jornada já estão no contrato; o modelo de apresentação precisa conservar os campos que hoje descarta. Manter as APIs existentes de upload, comentários, séries e retry de tags. Sem migração, dependência ou alteração de API prevista.

Arquivos principais: pages/books/[slug].vue; BookPageHero, BookReadTimeline, BookEditorialPanel, BookContextPanel, BookSeriesPanel e BookSeriesTagSyncStatus; types/books.ts e utils/books.ts. Reutilizar MediaImageGallery e MediaCoverUpload. Registrar decisões em DESIGN.md e observações em docs/books-api.md quando necessário.

BookPageHero também é usado no detalhe de revistas; BookReadTimeline tem uso não compacto. A composição nova deve ser específica do detalhe do livro ou opt-in, sem alterar silenciosamente revistas, autores ou biblioteca. Preservar tipografia, tokens, foco visível, padding de 20–32px e cantos de 28px dos painéis. Manter o caráter editorial das leituras, releituras, comentários e edições, com posição da capa consistente com os demais detalhes.

## Regras e fora do escopo

- Livro canônico, edição e jornada de leitura têm papéis distintos. Upload troca a capa do livro, sem reescrever capas ou dados das edições.
- Ordem proposta: identificação/capa/sinopse → leituras → comentários/nota → séries/edições.
- Estados vazios não devem parecer conteúdo ou manutenção pendente; falhas reais preservam feedback e retry junto da seção.
- Preservar leituras e resenhas importadas como dados do arquivo, sem simular edição local da integração.
- Novos fluxos de iniciar/concluir/editar leituras, exclusão de comentários, edição de nota, tradução de sinopse, novas integrações, imagens adicionais, troca de edição canônica, revisão de autor/séries/universos e redesign da biblioteca ficam fora desta entrega.

## Critérios de aceite

- Desktop/celular: título/autores longos, sinopse curta/longa/ausente, sem capa e falha de imagem, ausência de leituras/comentários/séries/edições.
- Estados de leitura, progresso em porcentagem/páginas, releituras e jornadas com edições diferentes; datas e nota sem repetição nem inferências.
- Resenha Hardcover e comentários locais, inclusive texto importado sem data; criação/edição com erro e retry, sem perder conteúdo ou feedback.
- Ampliar capa por mouse/teclado, Escape e retorno do foco; upload mantém prévia em erro e atualiza a capa/visualizador no sucesso.
- Preservar associação/criação/remoção de séries, sugestões e aplicação seletiva do Hardcover, vínculos com universos e retry de tags.
- Conferir regressão de revistas e demais usos compartilhados; lint, formatação, tipos, build e verificação de interações com APIs simuladas.

## Validação realizada

- Frontend: lint, formatação, tipos e build. O lint mantém um aviso preexistente de ordem de atributos em ArtistPageHero.vue, sem erros ou novas alterações naquele componente.
- 14 verificações dos dados de apresentação: capa canônica independente da edição, ISBN com zero inicial, edição por jornada, nota decimal, resenha sem data, estados concluído/quero ler/pausado/desconhecido, progresso em páginas e preservação do uso por autor.
- Chromium em 1440px e 390px, owner e visitante, com APIs simuladas: posição da capa, autores, expansão da sinopse, visualizador/Escape/foco/scroll; releituras e edições diferentes, ISBNs e edições recolhidas; resenha sem data e nota sem repetição; criação/edição de comentário com falha e retry; upload com falha/prévia preservada e sucesso; associação/remoção de série e busca de sugestões sem resultados; ausência de capa/conteúdo e falha da imagem; regressão do cabeçalho de revista e da timeline não compacta do autor.
- O teste de upload identificou uma atualização não reativa da capa no estado do Nuxt. O handler agora substitui o objeto de apresentação, atualizando a capa e o visualizador sem remontar a página ou descartar comentários/formulários.
- Sem migração ou alteração funcional de backend. Publicação solicitada posteriormente na versão 1.23.0, com tag Git v1.23.0, imagens 1.23.0/latest e implantação no Mordor pelo serviço Compose existente.
