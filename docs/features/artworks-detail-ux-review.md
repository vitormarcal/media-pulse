# Revisão incremental de obra e artista visual

Status: implementado e validado. Release: v1.30.0.

## Problema

Artes visuais já recebeu uma revisão orientada à contemplação, com galeria integral e cronologia local. Essa base deve ser preservada. A inspeção atual encontrou nota vazia para visitantes, contador 1 / 1 desnecessário, Listas 0, mensagens técnicas de sincronização públicas, manutenção entre identificação e imagem e repetição de anos/datas na cronologia. Biografia e descrição exibem literalmente marcadores como ** e *. No artista, o retrato precede o nome no celular e fica à esquerda no desktop, destoando dos detalhes revisados.

Amostras em produção, desktop/celular: /artworks/5 (Madalena com a Chama Fumegante) e /artworks/artists/2 (Gustav Klimt). Nenhum transbordamento nas amostras. As imagens precisam ser aguardadas/roladas durante a inspeção, pois a captura antes do carregamento não prova falha de imagem.

## Objetivo

Refinar UI, UX e copy sem desfazer a composição já aprovada do domínio. A imagem da obra continua protagonista, com memória pessoal próxima e manutenção discreta. Preservar a diferença entre obra, imagem de representação e experiência presencial/digital.

## Escopo

1. Obra mantém identificação compacta e galeria ampla antes do restante do conteúdo, com imagem integral, incluindo panorâmicas. Não converter a obra em pôster pequeno numa coluna à direita. Identificação mostra título, artista navegável e ano uma vez; origem do artista não deve parecer proveniência ou local de criação da obra.
2. Preservar navegação entre imagens, miniaturas, legenda, origem e ampliação existentes. Com uma imagem, ocultar contador 1 / 1 e controles redundantes. Navegar não altera a principal salva. Ampliação continua usando a maior área disponível, com teclado, Escape, foco e scroll corretos. Falha/ausência de imagem deve ter recuperação legível, sem navegação bloqueada.
3. Após a galeria, reunir memória pessoal: nota compacta e experiências, com data e tipo presencial/digital, origem e anotações existentes. Ocultar nota vazia e experiências vazias para visitantes; owner mantém acesso a incluir/editar/excluir junto da seção, com entrada preservada em erro. Não acrescentar progresso, conclusão ou contagem de experiências no cabeçalho. Favorito continua junto da identificação.
4. Sobre a obra reúne ficha técnica, descrição expansível e listas navegáveis. Tipo, técnica e movimentos aparecem uma vez, sem cartões aninhados desnecessários. Ocultar listas vazias para visitantes e retirar contagem irrelevante do título. Editar obra e gerenciar imagens/listas ficam junto das seções correspondentes; pendência/retry de sincronização de movimentos aparece apenas para o owner. Exclusões preservam confirmação e consequências atuais.
5. Artista usa identificação e biografia à esquerda e retrato integral/ampliável à direita no desktop, com limite próximo aos detalhes de autores (até 280px de largura/360px de altura). No celular, nome/origem/datas, retrato e biografia. Sem retrato ou em falha, não reservar coluna vazia. Imagem escolhida pelo owner não deve ser rotulada como fotografia documental nem ter autoria/licença inventada.
6. Obras no acervo vêm após a identificação do artista, agrupadas pelo ano de criação e com ordem global/paginação existentes. Preservar grade cronológica por linhas; retirar ano repetido no cartão quando já é o título do grupo. Datas de vida aparecem uma vez na identificação; a cronologia não precisa repeti-las como eventos. Obras sem ano continuam disponíveis ao final, inclusive ao carregar mais e filtrar. Não apresentar o acervo como catálogo completo do artista.
7. Biografia e descrição preservam o texto integral e expansão acessível. Tratar apenas negrito/itálico já usados no conteúdo, sem mostrar marcadores literais onde formam pares válidos; texto comum e marcadores incompletos continuam legíveis. HTML remoto permanece escapado. Não acrescentar parser geral, HTML arbitrário ou dependência para Markdown. Não reescrever/traduzir conteúdo salvo. Ajustar o trecho inicial somente se a conferência móvel justificar, sem ocultar todo o contexto.
8. Aplicar branco sobre fundo quente aos painéis de identificação do artista, memória pessoal e ficha/manutenção, com largura confortável para texto, padding de 20–32px, cantos de 28px e espaçamento de 32px. A galeria pode manter a largura ampla existente: as proporções das obras justificam essa particularidade. Consultar seções 12 e 17 do DESIGN.md; registrar explicitamente a evolução do tratamento de superfícies da seção 12. Não alterar em massa a biblioteca, suas páginas de lista ou formulários.

## API/UI e arquivos principais

Reutilizar /api/artworks e os contratos atuais: artista, descrição/biografia, imagens, nota, favorito, experiências, movimentos e listas. Não há campo de proveniência da obra, curadoria de museu ou fonte/licença de retrato a acrescentar por dedução.

Arquivos principais: pages/artworks/[id].vue e artists/[id].vue; ArtworkGallery, ArtworkLongText, ArtworkExperienceHistory, ArtworkListMembership, ArtworkImageManager e ArtworkLibrary/ArtworkCard por opções específicas do detalhe; assets/css/artworks.css. Reutilizar controles e visualizador existentes, sem substituir globalmente o visualizador de obras pelo de capas.

Sem novos endpoints, migração ou dependência prevista. Preservar sort=year no backend antes da paginação; não reordenar só os itens já carregados. Documentar a composição final em DESIGN.md e docs/artworks-api.md.

## Fora do escopo

Novas integrações, catálogo externo, textos gerados, tradução, restauração/edição de imagens, novos metadados, revisão das bibliotecas/listas gerais, comentários adicionais paralelos às experiências e novas escalas de avaliação.

## Critérios de aceite

- Desktop/celular: obra vertical, horizontal e panorâmica; zero/uma/várias imagens; artista com/sem retrato, datas e biografia; textos longos e marcadores incompletos/HTML literal, sem recorte ou transbordamento.
- Navegar/ampliar não altera a imagem principal; foco/Escape/scroll, fonte e legenda; trocar principal, upload/URL, remoção e retry continuam funcionando.
- Nota/favorito e experiências presenciais/digitais com data ausente ou conhecida: criação, edição, exclusão, erro e retry, sem blocos pessoais vazios para visitantes.
- Listas atuais navegáveis e gerenciamento preservado; movimentos e tags com pendência/recuperação no owner, sem perda da obra.
- Cronologia correta antes/depois da paginação, filtro e obras sem ano; ano e datas de vida sem repetição; regressão da biblioteca masonry fora do artista.
- Lint, formatação, tipos e build; navegador com APIs simuladas, owner/visitante e verificação dos controles existentes. Nenhuma mutação em produção na análise.


## Resultado e validação

Implementação mantém a galeria ampla da obra e aproxima nota/experiências da imagem. Sobre a obra concentra ficha, descrição, listas e manutenção. Artista tem painel branco, retrato integral à direita e identificação antes da imagem no celular. Cronologia local mantém paginação e anos ausentes ao final, sem repetir anos nos cartões ou datas de vida.

Biografia e descrição usam prévia de 420 caracteres visíveis nestes detalhes, após inspeção móvel; o componente compartilhado mantém o padrão de 700. Ênfase simples é renderizada com elementos seguros, sem HTML remoto. Falhas de imagem permitem recuperação; visualização não muda a principal salva.

Validação local: lint/formatação, tipos e build do frontend; navegador com APIs simuladas em 1440px e 390px, owner e visitante. Conferidos galeria, ampliação/Escape/foco, imagem única e indisponível, principal/adição por URL/remoção, retrato e atualização do visualizador, favorito com erro/retry, experiências, listas, edição com rascunho preservado, texto seguro, cronologia paginada e estados vazios. Não houve mutações em produção. Não há alteração de contrato, dependência ou migração.
