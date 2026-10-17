# Séries de livros e universos

## Problema

O acervo não permite organizar livros em séries nem relacionar séries de um mesmo universo com uma ordem de leitura pessoal.

## Objetivo

Criar uma organização local e editável para navegar entre livros, séries e universos, usando o Hardcover como fonte opcional de sugestões.

## Escopo

- Criar, renomear e excluir séries e universos locais.
- Associar somente livros já cadastrados no Media Pulse a séries; a ingestão de novos livros continua pelo Hardcover.
- Permitir um livro em várias séries e uma série em vários universos.
- Manter uma ordem de leitura independente dos livros em cada série e das séries em cada universo.
- Oferecer busca explícita de sugestões de séries no Hardcover, com revisão antes de aplicar e organização manual sempre disponível.

## API/UI

Contratos sob `/api/books` para manter séries, universos, associações e ordem, além de buscar e aplicar sugestões revisadas. A implementação exigirá migration Flyway para entidades, vínculos e posições locais.

Na área de Livros, oferecer acesso às séries e aos universos. O detalhe do livro mostra suas séries; o detalhe da série apresenta os livros na ordem de leitura e seus universos; o detalhe do universo apresenta as séries na ordem escolhida. Controles de gerenciamento permitem associar, remover e reordenar itens, inclusive por teclado.

A ação “Buscar sugestões de séries” parte do livro e apresenta os dados disponíveis para revisão e seleção antes de gravar. Confirmar os campos disponíveis na API do Hardcover durante a implementação; não presumir suporte a universos ou ordens de leitura. Ausência de sugestões ou indisponibilidade do provedor permite continuar a curadoria manual.

Seguir `DESIGN.md`, reutilizando capas, cards, tokens, tipografia, superfícies quentes e padrões de gerenciamento existentes em Livros.

## Non-goals

- Cadastro manual de livros ou importação de livros ausentes para completar séries.
- Aplicação automática de sugestões durante a sincronização do Hardcover.
- Relações genéricas, hierarquias de universos ou dependências entre séries.
- Múltiplas ordens alternativas dentro da mesma série ou universo.
- Intercalar livros de séries diferentes em uma sequência própria do universo.

## Heurística/regras

- Séries e universos têm nome obrigatório e identidade local independente do provedor.
- Novos vínculos entram ao final, em ordem de cadastro da associação; a ordem pode ser alterada manualmente.
- A posição pertence ao vínculo: mover um livro em uma série ou uma série em um universo não altera outras associações.
- Não duplicar o mesmo livro na mesma série nem a mesma série no mesmo universo.
- Remover vínculos ou excluir agrupamentos preserva livros, leituras e os demais agrupamentos.
- Sugestões só alteram o acervo após seleção e confirmação explícitas; apenas livros locais podem ser associados.
- A aplicação de sugestões deve permitir reutilizar uma série local ou criar outra, sem consolidar entidades apenas por igualdade de nome.
- Sincronizações posteriores do Hardcover preservam os vínculos e as ordens locais.
- A navegação e a organização salvas funcionam sem consultar o Hardcover.

## Acceptance criteria mínimos

- Criar séries e universos, corrigir seus nomes e excluí-los sem perder livros ou leituras.
- Associar o mesmo livro a duas séries e a mesma série a dois universos, com ordens independentes.
- Adicionar itens ao final por padrão e persistir a reordenação manual após recarregar.
- Navegar do livro às suas séries e delas aos universos, respeitando a ordem de leitura em cada agrupamento.
- Buscar sugestões explicitamente, revisar e aplicar somente as selecionadas, sem cadastrar livros ausentes.
- Preservar a organização local ao repetir sugestões ou sincronizar o Hardcover.
- Tratar ausência de sugestões e falha do provedor com feedback e nova tentativa, mantendo a organização manual utilizável.
