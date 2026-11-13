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
