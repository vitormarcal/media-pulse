# Revisão de revistas: número, publicação e acervo

Status: proposta aceita pela solicitação de continuidade; implementado e validado localmente.

## Problema

O domínio já oferece cadastro local, progresso, releituras e correção de jornadas, mas a composição ainda usa cabeçalhos antigos de livros/coleções, largura de 1480px, fundos com arte duplicada, manutenção concentrada no topo e metadados repetidos. No número 3, Quero ler aparece no cabeçalho e no histórico; Comentários permanece como título vazio para visitantes. O arquivo de publicação repete o nome na identificação, no destaque e em todos os cartões. Adicionar número aparece na navegação pública mesmo sendo uma ação do owner. O arquivo geral tem um seletor de ordenação com uma única opção.

Amostras reais inspecionadas em 1440px e 390px: /magazines/3, /magazines/publications/3 e /magazines/publications. Acervo atual com três publicações, incluindo publicação com um número e outras com cinco. Não foi observado transbordamento nessas amostras.

## Objetivo

Valorizar capas e memória de leitura, alinhando superfícies e hierarquia ao DESIGN.md, sem transformar revistas em livros: publicação, número e jornada continuam entidades distintas; o cadastro e a leitura são locais e editáveis.

## Escopo

1. Detalhe do número com painel branco, largura de até 1120px e capa integral à direita no desktop. No celular, identificação antes da capa e do contexto de leitura. Nome da publicação navegável, número e mês/ano identificados uma vez, sem inventar dia ou título de edição ausente. Capa com presença adequada ao formato, sem recorte, arte duplicada ou fundo artificial. Clique/Ver capa abre o visualizador compartilhado; sem imagem ou em falha, não oferecer ampliação.
2. Leituras imediatamente após a identificação, em um painel único com linhas compactas. Estado e progresso aparecem na jornada, sem repetição no cabeçalho; não mostrar 100% em uma conclusão nem 0% em Quero ler. Distinguir início e término, preservar dias sem conversão de fuso. Página atual e total só quando disponíveis e pertinentes.
3. Manter Ler/Atualizar leitura/Reler junto das leituras, conforme a jornada em aberto. Edição aparece junto da jornada correspondente, com foco e feedback próximos. Preservar todos os estados, correção por página/porcentagem, conclusão, releituras e exclusão confirmada. Comentários vêm depois, com texto e uma data por registro, sem contagem; ocultar a seção vazia para visitantes, mantendo composição, edição e exclusão para o owner.
4. Sobre o número reúne páginas e manutenção da edição, sem repetir metadados já visíveis. Editar número fica nesse contexto; preservar correção de publicação/número/mês/total, remoção/substituição de capa e exclusão confirmada do número com indicação de que remove leituras e comentários. Não substituir esse comportamento pelo de exclusão de jornada, que preserva comentários.
5. Detalhe da publicação com identificação compacta, contagem uma vez, acesso a todos os números e ISSN em detalhes. Último número publicado pode fornecer capa representativa, identificada como tal, sem repetir nome e ficha em um cartão gigante; sua escolha não muda com filtros. Editar publicação e Adicionar número ficam nesse contexto, para o owner, sob demanda. Edição de nome/ISSN deve explicitar que afeta todos os números; o detalhe do número mantém acesso ao contexto da publicação.
6. Acervo e listagem de publicações com cabeçalhos contidos, painéis brancos e grade regular, sem masonry. Capas integrais e proporções preservadas. Dentro de uma publicação, cartões priorizam número e mês/ano, sem repetir o nome da publicação em todos os itens. Fora desse contexto, manter a identificação da publicação. Tratar um único item sem coluna estreita ou ampliação exagerada.
7. Preservar Em andamento e Últimas leituras no início geral quando pertinentes; filtros ativos priorizam resultados. Manter busca, filtro por publicação/leitura, Não lida, agrupamento anual e Carregar mais. Remover o seletor com uma só ordenação, mantendo a ordem de publicação existente. Limpar filtros conserva o recorte de publicação e números sem data continuam ao final. Preservar rotas legadas e estado dos filtros.
8. Visitantes não veem ações de manutenção ou inclusão; owner mantém formulários sob demanda com entrada preservada em erro. Preservar o caso de dados salvos com upload de capa pendente: permitir retry só da capa, sem duplicar número/jornada, e atualizar o visualizador após o sucesso. Usar foco visível, estados claros de carregamento/erro/recuperação e layouts sem transbordamento.

## API/UI e arquivos principais

Reutilizar contratos e endpoints de docs/magazines-api.md, incluindo PUT da publicação, operações de jornada, multipart do número, retry da capa e comentários compartilhados. Sem novos campos, endpoints, migração ou dependência.

Arquivos principais: pages/magazines/index.vue, [id].vue, publications/index.vue e publications/[id].vue; MagazineArchive, MagazineCollectionNav, MagazineIssueForm e MagazineReadEditor; utils/magazines.ts e assets/css/magazines.css. Avaliar composição específica para revista em vez de mudar globalmente BookPageHero, BooksLibraryHero ou os cartões de livro. Reutilizar MediaImageGallery e o tratamento editorial de comentários. Acrescentar componentes locais se necessários para evitar duplicar os formulários.

Direção visual: seções 17 e 21 do DESIGN.md, com branco sobre fundo quente, até 1120px, 32px entre seções, padding de 20–32px e cantos de 28px. Reservar vermelho às ações principais. Não copiar as regras de importação/edições canônicas de livros para revistas.

## Fora do escopo e regras

Sem provedor externo, artigos internos, OCR, sinopse gerada, upload de imagens adicionais, nova galeria persistida, nova avaliação ou progresso agregado de publicação. Quantidade de jornadas concluídas não equivale a números distintos lidos. Não deduzir falhas na coleção pela numeração, que é textual e pode não ser sequencial. Publicação vazia continua reutilizável. Preservar as regras de unicidade do número e de uma jornada em aberto por número.

## Critérios de aceite

- Desktop/celular: publicações com um e vários números; identificação longa, número sem mês e mês sem número, capa ausente/com falha, sem páginas e acervo vazio.
- Ampliação, Escape, retorno do foco, proporção e atualização da capa após salvar/retry; nenhum controle de imagem quando indisponível.
- Ler, progresso por página/porcentagem, conclusão, abandono, releitura e correção; datas/progresso sem inferência e jornada aberta preservada em falhas.
- Cadastro/edição com erro, duplicata e capa pendente sem duplicar número ou jornada; renomear publicação/ISSN e exclusões com consequências corretas.
- Comentários vazios/preenchidos e criação/edição/exclusão; owner/visitante, teclado e estados de recuperação.
- Filtros, Não lida, rotas legadas, recorte de publicação, agrupamento anual e paginação, sem perder estado nem repetir itens.
- Regressão de livro e demais componentes compartilhados; lint, formatação, tipos, build e verificação das interações com APIs simuladas. Nenhuma mutação em produção durante a análise.


## Validação realizada

Lint, formatação, tipos e build do frontend passaram. O lint mantém apenas o aviso preexistente de ordem de atributos em ArtistPageHero.vue.

Chromium em 1440px e 390px, owner e visitante, com APIs simuladas: capa integral e posição, galeria por teclado/Escape/retorno do foco, estado sem repetição, progresso por página/porcentagem, correção com falha/retry sem perda de entrada, conclusão e abertura de releitura, exclusão de jornada preservando comentário, cadastro e retry de capa sem repetir POST de criação, capa salva disponível na galeria, edição de número com duplicata e rascunho preservado ao recolher, edição de publicação com falha/sucesso, filtro Não lida e limpar mantendo publicação, agrupamento/paginação, composição de um item, ausência/falha de capa e conteúdo vazio.

Regressão de livro e autor em owner/visitante e desktop/celular passou, preservando galeria/upload, comentários, associação de séries, retry de tags e composição dos componentes compartilhados. Nenhuma mutação em produção durante a implementação. Sem alteração de backend ou migração.

Publicação solicitada na versão 1.29.0, tag v1.29.0 e imagens 1.29.0/latest, para implantação no Mordor pelo serviço Compose existente. Verificação pública de número, publicação e acervo em desktop/celular, sem mutações de conteúdo em produção.
