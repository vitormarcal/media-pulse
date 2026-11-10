# Detalhe de séries e temporadas

## Problema

No detalhe de série, curadoria, listas e créditos aparecem antes do progresso. A contagem de episódios se repete no cabeçalho e no painel. No detalhe de temporada, a contagem volta a se repetir, o enriquecimento vem antes dos episódios e as ações “Data”, “Marcar” e “Até aqui” exigem que o usuário descubra o que cada uma faz.

## Objetivo

Facilitar a retomada da série, a consulta dos episódios e o registro do que foi assistido. Aplicar a linguagem visual aprovada para filmes às necessidades de séries.

## Escopo

- Detalhe de série e detalhe de temporada, em desktop e celular.
- Hierarquia, espaçamento, rótulos, estados vazios e posição dos controles existentes.

## UI e API

- **Série:** capa, título, ano, sinopse expansível e marcações pessoais. Progresso e temporadas vêm a seguir, com uma contagem geral e uma linha ou cartão navegável por temporada. “Atualizar episódios” fica junto desse bloco. Nota, comentários e sessões ficam agrupados, com formulário de registro sob demanda. Créditos, gêneros, tags, listas e edição contextual ficam em “Sobre a série”.
- **Temporada:** identificação, retorno à série e progresso em um cabeçalho compacto. Episódios em ordem, com título, estado de visualização, sinopse, metadados e nota quando disponíveis. “Marcar como assistido” registra o episódio. A data personalizada e “Marcar até este episódio” ficam como ações secundárias. O enriquecimento da temporada abre sob demanda.
- Usar superfícies brancas sobre fundo quente, divisórias discretas e títulos diretos. Reutilizar os contratos e as operações existentes.

## Fora do escopo

Migrações, mudanças no cálculo de progresso, novos dados de catálogo, sugestão de próximo episódio, novos endpoints e revisão da biblioteca de séries.

## Regras

- Exibir o progresso geral uma vez por página. Cada temporada apresenta seu próprio andamento.
- A ação “Marcar até este episódio” segue a ordem e as regras existentes. Se houver falha parcial, atualizar a lista para mostrar o que foi salvo e informar o que ficou pendente.
- Controles de edição e registro aparecem para o owner. Temporadas sem rota não simulam links.
- Preservar links, notas, comentários, sessões e as opções de enriquecimento.

## Critérios de aceite

- Em desktop e celular, progresso e temporadas aparecem antes de curadoria, e episódios aparecem antes do formulário de enriquecimento.
- Navegar da série à temporada e voltar por teclado ou toque. Conferir séries sem temporadas, temporadas sem episódios e progresso parcial ou completo.
- Conferir registro imediato, data personalizada e marcação até um episódio com APIs simuladas. Validar lint, formatação, tipos e build.
