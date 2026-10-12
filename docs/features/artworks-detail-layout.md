# Artes visuais: detalhes de artista e obra

Status: aprovada e implementada.

## Problema

Os detalhes usam muitos painéis e oferecem pouco contexto imediato de leitura. As referências do Historia Arte mostram uma organização mais próxima da contemplação das imagens e da exploração das obras de um artista.

## Objetivo

Adaptar a composição das páginas de artista e obra a essas referências, usando os dados locais e respeitando a identidade de `DESIGN.md`.

## Escopo

- Artista com retrato ao lado do nome, país de origem e período de vida; informações ausentes não deixam uma coluna vazia.
- Biografia com largura confortável de leitura e obras locais organizadas pelo ano de criação. Datas de nascimento/falecimento podem contextualizar a cronologia quando conhecidas; obras sem ano aparecem separadamente ao final.
- Obra com título, artista e ano em cabeçalho compacto; retrato do artista quando disponível, com acesso ao seu detalhe.
- Galeria principal com imagens grandes, proporções originais, navegação entre imagens e ampliação na própria página.
- Ficha técnica compacta e descrição próximas à galeria, com menos painéis de fundo na área de leitura.
- Favorito, avaliação, experiências e listas acessíveis como recursos pessoais; formulários de manutenção continuam sob demanda.
- Textos curtos visíveis por inteiro. Textos longos exibem um trecho inicial com Continuar leitura e Recolher; limite de apresentação compartilhado entre descrição e biografia, ajustável sem alterar dados.

## API/UI

Reutilizar os contratos e dados de artes visuais. Se necessário, estender a consulta de obras do artista com ordenação por ano para manter a cronologia correta entre páginas; não ordenar apenas os itens já carregados. Nenhuma migration ou nova dependência prevista.

Principais arquivos: páginas `frontend/app/pages/artworks/{[id],artists/[id]}.vue`, `ArtworkLongText`, componentes de galeria/cronologia, `artworks.css` e documentação de domínio. Consulta e testes de `ArtworkQueryRepository`/`ArtworkService` somente se a ordenação exigir extensão do contrato.

Aplicar Pin Sans e os tokens existentes, neutros quentes, vermelho nas ações principais, cantos arredondados e pouca sombra. Retrato, imagem e texto orientam a hierarquia; reduzir fundos de painéis na apresentação de conteúdo. Textos de interface limitados a rótulos, ações e feedback necessários.

## Non-goals

- Integrações externas, importação de artigos ou catálogo completo de um artista.
- Novos metadados, título original, recomendações de artistas ou novas relações culturais.
- Reorganizar as bibliotecas gerais ou refazer os formulários de cadastro.
- Adotar as fontes, cores ou textos editoriais do Historia Arte.

## Heurística/regras

- Começar com a abertura parcial de textos longos, já que a preferência entre aberto e recolhido ainda não está definida. A decisão é reversível e não altera o conteúdo salvo.
- Evitar esconder todo o contexto atrás de uma seção fechada; limitar a largura e o comprimento visual das linhas para leitura confortável.
- Usar o ano de criação das obras na cronologia; datas de cadastro ou contemplação não substituem esse ano.
- Exibir somente obras cadastradas e vinculadas ao artista. Paginação e itens sem data permanecem disponíveis.
- Imagem escolhida como principal inicia a galeria; navegar entre imagens não muda a principal persistida.
- Ampliação suporta fechar com Escape, foco por teclado e retorno ao controle de origem. A navegação tem controles visíveis e identificados; interação não depende de gesto de arrastar.
- Preservar imagens integralmente, incluindo panorâmicas e verticais, sem recortar composição para preencher a área.
- Preservar os dados pessoais e os fluxos existentes de edição, exclusão, gerenciamento e recuperação de falhas.

## Acceptance criteria mínimos

- Abrir artista com e sem retrato, biografia ou datas, com composição consistente em desktop e celular.
- Consultar obras locais em ordem cronológica correta, inclusive após carregar mais e quando houver obras sem ano.
- Abrir obra com nenhuma, uma ou várias imagens; navegar e ampliar sem alterar a principal salva.
- Ler o trecho inicial de textos longos, expandir e recolher por teclado; textos curtos não exigem expansão.
- Acessar artista, avaliação, favorito, experiências, listas e manutenção da obra sem perder os comportamentos atuais.
- Validar foco, fechamento da ampliação, proporções das imagens, ausência de rolagem horizontal indevida e estados de carregamento/erro/vazio.

Referências visuais: [Albert Anker](https://historia-arte.com/artistas/albert-anker) e [Friso de Beethoven](https://historia-arte.com/obras/friso-de-beethoven).

## Implementação e validação

A biblioteca ganhou `sort=year`, com ano crescente, ID crescente para desempate e obras sem ano ao final, antes da paginação. A galeria usa ampliação nativa com `dialog`, sem dependências novas. O visualizador ocupa toda a tela, ajusta a imagem à área disponível sem recorte e sobrepõe controles compactos nas bordas; não reserva uma faixa para toolbar nem limita a imagem a 75% da altura. O trecho inicial de descrição/biografia usa limite compartilhado de 700 caracteres; o conteúdo salvo permanece integral. Não há migration.

Validação: formatação Kotlin, testes de serviço/HTTP e PostgreSQL para paginação cronológica, verificações de lint/formatação/tipos e build do frontend. Inicialização por `bootRun` e navegação com backend real em banco temporário validam favorito, avaliação, listas e edição. Testes de navegador em desktop e celular cobrem textos, proporções, navegação, ampliação, Tab/Escape e retorno de foco, paginação acima de 24 obras, estados vazio/erro e manutenção dos controles pessoais.

A revisão do visualizador compara o tamanho efetivo da imagem antes/depois da ampliação em cinco tamanhos de tela, incluindo celular em retrato/paisagem e desktop amplo. Imagens verticais, panorâmicas e a pintura de referência aproveitam a área disponível e ficam maiores que na galeria; Tab, Escape, legenda e navegação permanecem funcionais.
