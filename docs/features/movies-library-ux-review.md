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


## Abertura editorial com busca integrada

Proposta aprovada: identificação discreta fora de painel; destaque amplo do último filme antes dos controles, pôster integral até 420px no desktop e título editorial. Busca na base da mesma superfície; cinco recentes seguintes em faixa menor abaixo. Filtros junto do acervo. Busca/filtro/cadastro ativo entra diretamente nos controles e resultados sem a abertura editorial. No celular, imagem de altura controlada antes de título/contexto/busca. Sem imagem ou em falha, não reservar coluna vazia. Sem duplicar o destaque nos demais recentes, fundo com capa repetida, sinopse/nota inventada ou chamada extra ao detalhe. Falha/ausência de recentes não impede busca. Preservar grade, cursor automático, rascunho e retry. Arquivos: movies/index.vue e MoviesLibraryFilters.vue, documentação; sem migração/API/dependência. Aceite: desktop/celular, título longo, com/sem imagem e recentes, navegação/teclado, busca direta/default, filtros e carregamento automático. Implementação publicada na release v1.33.0.

Validação da abertura editorial: lint/formatação, tipos e build passaram (permanece somente o aviso de ordem de atributos já existente em ArtistPageHero). Prévia local com dados reais em 1440px, 768px, 390px e 320px, sem transbordamento; capa maior no desktop, imagem antes dos controles no celular e busca única integrada. Navegador owner/visitante em desktop/celular validou busca direta/integrada, filtro anual/sem sessão, contagens, carregamento automático, erro/retry, descarte de resposta antiga, rascunho e estados vazio/um recente/sem imagem/imagem em falha. Nenhuma mutação ou deploy em produção.


## Composição integrada inspirada na home

Proposta aprovada: recuperar a coesão visual da home na abertura de filmes. Último filme à esquerda, pôster integral próximo do título, e três anteriores em coluna à direita, dentro de um único bloco editorial. Busca na base; remover a faixa externa de recentes. Fundo suave derivado da capa com baixa opacidade e cartões claros garante contraste. Essa é uma exceção explícita ao branco uniforme do destaque anterior, limitada à abertura padrão de filmes; não altera a home ou os detalhes. No celular, destaque primeiro e secundários em faixa horizontal de cartões compactos; sem carrossel automático. Zero/um/quatro recentes e imagens ausentes/em falha mantêm busca e leitura. Preservar controles/resultados diretos, grade regular, totais e carregamento automático. Arquivo principal: movies/index.vue; documentação de design/API. Sem migração, dependência ou provedor novo. Implementação publicada na release v1.34.0.

Validação da composição integrada: prévia local com dados reais em 1440px, 768px, 390px e 320px, sem transbordamento; destaque e três secundários dentro do mesmo bloco, com faixa horizontal por teclado nas telas menores. Lint/formatação, tipos e build passaram. Navegador owner/visitante em desktop/celular validou busca/filtros, totais, carregamento automático, retry, resposta antiga, rascunho e zero/um/vários recentes, sem imagem e imagem em falha. Capa e fundo compartilham registro de falha estável por URL: eventos de erro concorrentes não devem limpar o estado nem recriar imagens quebradas. Home e outras rotas permanecem independentes; nenhuma mutação ou publicação em produção nesta etapa.
