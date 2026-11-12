# Revisão de séries literárias e universos

Status: aprovado, implementado e validado localmente.

## Problema

As páginas usam a composição anterior: largura de 1480px, título grande, gradiente nas listagens, rótulos decorativos vermelhos e um cartão por membro. No detalhe da série, dados locais e externos disputam o cabeçalho; nome de autor e contagens se repetem. No celular, a capa antecede a identificação. Listagens repetem o nome da entidade em vários títulos e mantêm criação permanentemente aberta para o owner.

Inspeção em produção: Dragon Ball (Kanzenban), /books/series/3, com 34 livros, e /books/universes, vazio, em 1440px e 390px. Nenhum universo cadastrado; seu detalhe foi analisado pelo componente e contrato, sem criar dados reais. A ordem salva da série já organiza os volumes, mesmo com variações de título.

## Objetivo

Priorizar navegação pelo acervo e pela ordem pessoal, com superfícies e hierarquia alinhadas às seções 17, 21 e 22 de DESIGN.md. Preservar diferenças entre série (livros) e universo (séries).

## Escopo

1. Listagens com identificação compacta, largura de até 1120px, painéis brancos sem gradientes, contagem uma vez e mosaicos contidos em grade regular. Singular e poucos itens devem ocupar o espaço com equilíbrio, sem crescer até preencher toda a tela.
2. Criação sob demanda junto do título, exclusiva do owner, com nome preservado em falha, erro contextual e nova tentativa. Visitantes veem estado vazio simples, sem orientação para controles indisponíveis.
3. Detalhe com retorno, nome e créditos locais navegáveis. Imagem representativa do acervo à direita no desktop; no celular, identificação antes da imagem e descrição. Preservar proporção integral e não sugerir que a capa de um livro é uma capa própria da série. Sem imagem, não reservar coluna vazia. Não criar nova galeria nesta revisão.
4. Descrição expansível, sem tradução automática. A ordem dos livros ou séries vem imediatamente após o cabeçalho, num painel branco único com linhas compactas, miniaturas integrais e nomes navegáveis. Preservar todos os membros e a ordem salva; sem masonry ou reordenação inferida de títulos.
5. Na série, usar Ordem no acervo com breve identificação de que se trata da ordem salva. No universo, Séries neste universo. A posição mostrada é ordinal local, não número oficial de volume ou cronologia universal.
6. Sobre a série reúne universos navegáveis e contexto Hardcover. Distinguir quantidade local de contagens externas e conclusão editorial de conclusão de leitura; não calcular progresso pessoal nem inferir volumes faltantes por subtração. Não repetir o autor principal externo quando for equivalente à atribuição local; preservar o dado na prévia de manutenção.
7. Edição e atualização aparecem junto das respectivas seções, apenas para o owner. Preservar nome/descrição, busca de livro, inclusão ao final, associação de universo, reordenação por botões, remoção e exclusão confirmada. Atualização Hardcover mantém prévia e aplicação seletiva; campos não selecionados e ordem local permanecem intactos. Controles de mover ficam junto do membro durante edição.

## API/UI

Reutilizar BookGroup, BookGroupDetails, BookGroupMember e as APIs existentes de listagem, criação, manutenção, ordem e prévia/aplicação de metadados. O detalhe de universo contém séries; não inventar lista agregada de todos os livros ou progresso ausente do contrato. Não acrescentar descrição editável de universo, atualmente inexistente no formulário/API de atualização.

Arquivos principais: frontend/app/components/books/BookGroupsIndex.vue, BookGroupPage.vue, BookGroupCard.vue e assets/css/book-groups.css. Avaliar usos compartilhados em autor, livro e painéis de atribuição antes de alterar estilos. Registrar a direção aprovada em DESIGN.md e docs/books-api.md.

Migração: não. Novos endpoints ou dependências: não previstos.

## Fora do escopo

Catálogo externo completo, ingestão de volumes ausentes, tradução de descrição, capa própria/upload, novas integrações, progresso de leitura, unificação de títulos/edições, múltiplas ordens, hierarquia entre universos e redesign da biblioteca geral.

## Regras

Dados externos são contexto persistido, não consultas durante navegação. Livros do acervo não equivalem à bibliografia completa. Atualização de metadados não associa membros nem substitui posições pessoais. Preservar links individuais de créditos e identidades locais; não deduzir equivalência por nome aproximado.

## Critérios de aceite

- Desktop/celular: séries de 1, 3 e 34 livros, universo simulado com 1 e várias séries, nomes extensos, sem capa/falha, vazio e erro/recuperação; sem transbordamento.
- Visitante sem manutenção; owner cria, edita, adiciona, associa, move, remove e confirma exclusão, com falhas e feedback preservado.
- Prévia e aplicação seletiva Hardcover preservam campos não escolhidos, membros e ordem; contagens e estados externos não sugerem progresso pessoal.
- Teclado, foco e expansão de descrição; navegação livro/série/autor/universo; regressão de autor 19/28, detalhe do livro e atribuição. Lint, formatação, tipos e build.


## Validação realizada

- Lint, formatação, tipos e build do frontend; permanece somente o aviso preexistente de ordem de atributos em ArtistPageHero.vue.
- Chromium em 1440px e 390px, owner e visitante, com APIs simuladas e os 34 membros reais da série Dragon Ball: ordem preservada, descrição expansível, posição responsiva da capa, contexto externo sem repetição do autor e ausência de transbordamento.
- Edição de nome com falha e retry, mover membro, buscar/adicionar/remover livro, associar universo, prévia/aplicação apenas da descrição Hardcover preservando nome e ordem; exclusão com confirmação e criação com erro/entrada preservada e sucesso.
- Séries com 1 e 3 livros, capa quebrada e ausente, vazio e erro de carregamento com recuperação; universo simulado com série navegável e listagem vazia. Nenhuma mutação em produção.
- Regressões em desktop/celular: autores 19 e 28, livro (owner/visitante) e revista, incluindo cartões de série, capa, comentários, associação e retry de tags.
- Sem alteração de backend, endpoints ou migração. Publicação solicitada na versão 1.25.0, com tag v1.25.0 e imagens 1.25.0/latest, para implantação no Mordor pelo serviço Compose existente.
