# Revisão da página Filmes

Status: implementado e validado. Release: v1.31.0.

## Problema e objetivo

A página repete filmes em quatro composições e adia a busca em mais de 5.000px no desktop e 15.000px no celular. Resumos misturam totais com limites de resposta e a linguagem repete estados. Priorizar exploração do acervo, preservando a presença visual dos pôsteres e o histórico pessoal.

## Escopo e regras

- Cabeçalho compacto branco, título Filmes, links Listas/Coleções e cadastro do owner.
- Busca e filtros imediatamente abaixo. Ano significa ano da sessão; busca por nome considera todo o acervo e sai do recorte anual explicitamente. Sem sessão registrada não presume que a pessoa nunca assistiu ao filme.
- Um bloco compacto Últimos filmes vistos, até seis títulos, com última sessão por filme, conforme o contrato existente. Não é uma lista de todas as sessões/releituras.
- Acervo em masonry, pôster integral, título/ano e memória pertinente, sem rótulos repetidos. Novos estilos opt-in, preservando cartões em outras rotas.
- Resumo do histórico recolhido, contagens globais ou anuais com origem explícita. Não usar tamanho de página como total. Ano mostra filmes com sessão no ano; filmes nunca registrados continuam no filtro próprio.
- Falhas independentes de catálogo, resumo e recentes, com retry contextual. Erro de paginação preserva itens e cursor; resposta antiga não pode entrar após troca de filtro.
- Cadastro junto do controle que abre, com rascunho preservado ao recolher. Busca vazia oferece adicionar ao owner, sem abrir formulário sem solicitação.

## API/UI

Reutilizar library, recent, search, stats e year. Busca tem limite de 40 correspondências, ano até 1.000 filmes; identificar recortes quando atingidos. Catálogo mantém cursor do backend e ordem recebida. Não combinar busca e ano mediante filtro local incompleto. Sem migração, provedor ou dependência nova.

Principais arquivos: movies/index.vue, MoviesLibraryFilters, MoviesLibraryGrid, MovieLibraryCard e composable/utilitário de navegação. DESIGN seções 3–8 e 17 orientam tokens, superfícies, densidade e revisão incremental.

## Fora do escopo

Redesenho de listas, coleções, detalhes e outros domínios; novos filtros, relatório completo de sessões ou ordenações não suportadas pela API.

## Aceite

Desktop/celular e owner/visitante; busca, ano da sessão, sem sessão, limpeza e voltar/avançar; paginação, falha/retry e troca de filtro durante resposta pendente; imagens ausentes/falhas/integrais; cadastro e rascunho; falhas independentes e totais sem confundir limites. Lint, tipos, build e navegador. Sem mutação em produção.


## Validação realizada

Lint/formatação, tipos e build do frontend. Navegador com APIs simuladas em 1440px e 390px, owner e visitante: busca, ano, sem sessão, limpar/voltar/avançar, recortes, resumo, imagens integrais/ausentes/em falha, erros independentes e retry, paginação com deduplicação e descarte de resposta antiga, estados vazios, cadastro sob demanda e rascunho preservado após erro/reabertura. Conferência local com leitura dos dados reais: busca próxima de 300px do topo nas duas larguras, sem transbordamento; lista de filmes não recebe os estilos opt-in. Nenhuma mutação ou deploy em produção.


## Refinamento visual após v1.31.0

Aprovado pelo owner: a simplificação melhorou a navegação, mas perdeu presença visual ao usar dois painéis grandes antes das imagens e recentes com miniaturas. Compactar cabeçalho/busca/filtros em uma superfície e apresentar recentes como faixa de pôsteres integrais maiores: seis no desktop amplo, rolagem horizontal nativa em telas menores, títulos e datas abaixo. Preservar limite, semântica, estados, filtros, cadastro e paginação da revisão publicada. Arquivos: movies/index.vue e MoviesLibraryFilters.vue; sem migração ou alteração de API. Refinamento publicado na release v1.31.1.


Validação do refinamento: lint/formatação, tipos e build; regressão em 1440px/390px, owner/visitante, incluindo filtros, paginação com falha e resposta antiga, cadastro/rascunho e estados vazios. Prévia local com dados reais somente por leitura: seis pôsteres lado a lado no desktop, faixa horizontal sem overflow da página no celular e rolagem por teclado. Busca próxima de 192px/221px do topo nas capturas desktop/celular. Revisão visual das duas larguras concluída; foco da faixa usa o token existente.


## Grade regular, carregamento automático e último filme visto

Escopo solicitado pelo owner: usar grade regular apenas na /movies, com área 2:3 e pôster integral; destacar o primeiro item de recent como Último filme visto, sem repeti-lo na faixa dos demais recentes; substituir o botão de próxima página por carregamento ao aproximar o fim dos itens. Manter cursor, ordem, deduplicação, descarte de respostas antigas, filtros e erros independentes. Interromper tentativas automáticas em falha e oferecer retry explícito, sem loop; desconectar o observador ao sair. Não inferir sinopse, nota ou dados ausentes no endpoint recente.

Arquivos principais: movies/index.vue, MoviesLibraryGrid.vue, MovieLibraryCard.vue e DESIGN.md. Nenhuma migração/API/dependência. Aceite: desktop/celular e teclado, zero/um/vários recentes, grade com títulos longos e imagens de proporções diferentes, carregamento sem clique, erro/retry, troca de filtro durante requisição e fim do cursor; lint/tipos/build e navegador.

Validação desta etapa: lint/formatação, tipos e build; navegador owner/visitante em 1440px/390px com grade por linhas, carregamento automático, deduplicação, erro sem loop/retry e descarte de resposta antiga após filtro, além de busca/ano/cadastro/rascunho. Prévia com leitura dos dados reais em 1440px, 768px, 390px e 320px, sem transbordamento, com destaque e demais recentes separados; listas continuam sem os novos estilos. Etapa publicada na release v1.32.0.


## Contexto junto ao acervo

Aprovado pelo owner: remover Resumo do histórico do fim da grade com carregamento contínuo. Exibir junto do título apenas o total global de filmes, o total sem sessão no filtro correspondente, ou filmes/sessões do ano selecionado. Busca não mostra totais globais nem usa tamanho da página como total. Em falha de stats, omitir números indisponíveis e apresentar retry junto dos filtros. Remover estilos e formatação de datas sem uso. Sem nova página, migração ou alteração de API; alteração em movies/index.vue e apresentação refined de MoviesLibraryGrid. Implementação publicada na release v1.32.1.

Validação do contexto: lint/formatação, tipos e build passaram. Navegador em 1440px/390px, owner/visitante, conferiu ausência do resumo, total do acervo independente da página carregada, contexto anual, ausência de totais globais na busca e ocultação/recuperação dos números quando stats falha. Regressão de filtros, carregamento automático, falha/retry, resposta antiga, cadastro/rascunho e estados vazios passou.
