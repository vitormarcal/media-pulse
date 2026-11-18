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

## Evolução da abertura editorial

Escopo aprovado na retomada do detalhe de séries: incorporar os refinamentos
recentes da abertura de filmes, incluindo o preenchimento com as imagens da
própria série que já estejam salvas. O componente `MoviePageHero` já é usado pelos
dois detalhes; reaproveitar sua composição, sem criar outro cabeçalho ou duplicar
os estilos. O detalhe de série deixa de limitar a galeria às quatro primeiras
imagens e informa explicitamente o domínio para os rótulos acessíveis.

A capa permanece em destaque, com fundo difuso discreto. As complementares
preservam a proporção natural em uma faixa horizontal com todas as imagens
disponíveis e alturas iguais às de filmes. O clique de cada prévia corresponde
ao mesmo índice no visualizador completo; Escape devolve o foco ao controle de
origem. Com duas imagens, a complementar já aparece. Sem imagens ou com capa
em falha, não deixar uma coluna vazia nem impedir a leitura ou a seleção pelo
owner. No celular, seguir identificação, capa, sinopse e complementares, usando
os mesmos limites de altura e largura de página de filmes.

Preservar Atividade com nota, comentários e sessões, assim como progresso,
temporadas, créditos, listas e edição contextual. Não alterar temporada,
cálculos de progresso, biblioteca de séries, contratos, importação ou imagens
armazenadas. Não há migração, dependência ou mudança de API.

Validar mais de quatro imagens, ausência de imagens, capa única, duas imagens,
capa com falha, título e sinopse longos, proporções, teclado e ausência de
overflow em desktop, tablet e celular. Conferir visitante e owner com APIs
simuladas, sem gravar no acervo real, além de lint, formatação, tipos e build.

### Validação da abertura

- Lint (sem erros; permanece o aviso anterior de ordem de atributos em
  `ArtistPageHero.vue`), formatação, tipos e build estático concluídos.
- Playwright no build estático: 49 verificações com APIs simuladas e imagens de
  teste, em 1920, 1440, 1024, 768, 390 e 320px, sem gravações no acervo real.
- Galeria com oito imagens e entradas duplicadas, clique na última prévia,
  navegação por setas, Escape e retorno de foco; rolagem horizontal por teclado
  e proporção de imagens verticais e horizontais.
- Sinopse longa expansível, título longo, progresso e rota da temporada;
  visitante sem controles de edição e owner com seleção de imagens acessível.
- Nenhuma, uma ou duas imagens, sinopse ausente e capa com falha, sem overflow
  horizontal da página ou erros JavaScript.

### Conferência com séries reais

Validação local da alteração com leitura dos dados de produção de Monogatari,
Bleach e The Flowers of Evil, sem gravações no acervo. Foram conferidas as três
séries em 1440, 768, 390 e 320px: sem overflow horizontal, rótulos de série e
ordem móvel com capa antes da sinopse. Monogatari apresentou 29 imagens e quatro
links de temporada; Bleach, seis imagens e dois links; The Flowers of Evil, oito
imagens e um link. Todas as complementares ficaram acessíveis por rolagem. Em
cada largura, a última prévia abriu no índice correto (29/29, 6/6 e 8/8), e
Escape devolveu o foco ao controle de origem. Progresso e links de temporadas
permaneceram disponíveis, sem erros JavaScript nos fluxos conferidos.
