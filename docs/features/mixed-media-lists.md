# Listas mistas de filmes e séries

## Problema

Listas como “Abandonados” exigem manutenção manual apesar de as obras já possuírem marcações pessoais.

## Objetivo

Reaproveitar listas existentes, combinando curadoria manual e inclusão automática por Favorito ou Abandonado, em filmes e séries.

## Escopo aprovado

- Duas regras independentes por lista: incluir favoritos e incluir abandonados.
- Ambas ativas usam OU: basta uma marcação corresponder.
- União sem duplicatas dos itens manuais e das obras correspondentes às regras.
- Itens manuais existentes permanecem; desativar regras não apaga esses vínculos.
- Desmarcar remove apenas a inclusão automática.
- Remover um vínculo manual não exclui uma obra que ainda atende à regra.
- Sem regras, o funcionamento permanece manual.

## API/UI

`PATCH /api/movies/lists/{listId}/rules` e `PATCH /api/shows/lists/{listId}/rules` recebem `includeFavorites` e `includeAbandoned` e retornam o resumo atualizado. Listas inexistentes retornam 404.

As respostas de resumo e detalhe expõem as regras. Detalhes também retornam `manualMovieIds` ou `manualShowIds`, para distinguir a curadoria manual. Contagens, previews e conteúdo da lista consideram a união.

Em filmes, “Editar lista” oferece as regras; em séries, elas ficam na página da lista. O visual reutiliza tokens, superfícies quentes, botões de 16px e capas, conforme DESIGN.md.

## Regras de apresentação e persistência

- Migration `V62__add_mixed_list_rules.sql`: booleanos com default false nas duas tabelas de listas e views de leitura para os membros efetivos.
- Views unem vínculos manuais a correspondências automáticas sem duplicar nem persistir itens derivados. A atualização aparece na próxima consulta, incluindo marcações anteriores à configuração da regra.
- Itens manuais vêm primeiro, na ordem salva; automáticos vêm depois, por ID crescente, com desempate estável nos previews.
- Reordenação e capa fixa continuam atuando apenas sobre inclusões manuais. Sem capa fixa, usa-se o primeiro item efetivo.
- Os painéis de listas na página da obra continuam gerenciando seus vínculos manuais; o conteúdo completo está na página da lista.
- As páginas de filmes e séries também exibem listas nas quais a obra entrou automaticamente por uma regra ativa. O retorno identifica `includedAutomatically`; nesses casos a UI não oferece remoção manual.
- Regras são explícitas: nomes como “Favoritos” não ativam regras por inferência.

## Non-goals

Filtros por tags, operadores avançados, exclusões individuais, listas entre domínios e novas integrações externas.

## Critérios de aceite

- Paridade entre filmes e séries.
- Obras previamente marcadas entram ao ativar a regra.
- Sobreposição manual/automática e entre regras não duplica obras ou contagens.
- Desmarcar, remover vínculo manual e desligar regras preservam as fontes restantes de inclusão.
- Listas vazias podem receber regras; listas sem regras preservam o comportamento anterior.
- Ordem e capa manuais continuam válidas.

## Roadmap

1. **Entrega atual — implementada:** regras Favorito/Abandonado, listas mistas nos dois domínios, edição na UI, API, migration e validação.
2. **Evolução candidata — fora desta entrega:** filtros por tags, após definir a origem das tags e a combinação com marcações.
3. **Avaliar apenas se houver necessidade:** exclusões individuais e combinações mais avançadas. Exigem nova descoberta e aprovação de escopo.

## Validação

- Testes de serviço cobrem persistência das regras sem alteração dos vínculos manuais e lista inexistente.
- `MixedListsIntegrationTest` aplica todas as migrations em PostgreSQL isolado e executa as consultas reais de filmes e séries: união, OU, deduplicação, marcações anteriores, remoção manual, desmarcação, desativação, lista vazia, contagens, previews e ordem.
- Frontend: lint, Prettier, typecheck e build estático.

### Docker durante os testes

Neste ambiente, o Testcontainers 1.21.3 inicialmente pulou o teste por não conseguir acessar o Docker 29, cujo mínimo de API é 1.40. A execução passou ao definir `JAVA_TOOL_OPTIONS=-Dapi.version=1.44` apenas no comando de teste. Verifique o XML de resultados: o teste parametrizado deve executar dois casos, com zero pulados. Não é necessário alterar o daemon ou as dependências do projeto.
