# Detalhe de filme: UI, UX e copy

Escopo aprovado: reorganizar o detalhe com a simplicidade editorial de livros, preservando imagens e memória pessoal.

## Experiência final

- Abertura editorial seguindo séries e filmes: navegação dentro do bloco, título e sinopse alinhados ao topo à esquerda, capa integral à direita limitada a 560px e fundo difuso da própria capa (6%), com camada clara atrás do texto. Títulos longos recebem escala menor.
- Até quatro imagens adicionais da própria obra abaixo, sem repetir a capa; altura comum de 180px no desktop e 140px em telas menores, com larguras proporcionais e rolagem horizontal quando necessário. Preservar imagens inteiras, inclusive horizontais. Com duas imagens, a complementar já fica visível.
- Sem capa ou em falha, remover coluna e fundo derivado. Ausência de imagens não bloqueia leitura ou seleção de imagens pelo owner.
- No celular: identificação, capa, sinopse, faixa horizontal de complementares e memória pessoal. Capa limitada a 440px no tablet e 380px no celular.
- Nota, marcações e comentários na base do bloco, separados por linha discreta. Comentários longos não alteram a altura da abertura.
- Cada imagem abre seu índice na galeria existente, com retorno de foco. Ver todas e Escolher imagens junto do conjunto; curadoria permanece no visualizador.
- Sinopse expansível e título sem overflow. Sessões, créditos, empresas, termos, listas e coleção preservados.

## Decisões

A composição editorial aprovada nas bibliotecas passa a representar uma única obra no detalhe. A capa domina o conjunto e as complementares preservam seu formato, substituindo o mosaico compacto. A conferência com Matrix e Humanist Vampire revelou excesso de altura, títulos longos desproporcionais e vazios entre imagens de formatos diferentes; limitar a capa, reduzir títulos longos e uniformizar a altura da faixa resolve esses casos. No celular, a capa antecede a sinopse para apresentar a obra mais cedo. A direção pode evoluir gradualmente nos demais domínios, conforme DESIGN.md; esta implementação altera apenas o detalhe de filmes.

## Escopo técnico e aceitação

Reutilizar contratos e dados existentes, sem API, migração, dependência ou novo provedor. Não alterar importação, escala de avaliação, exclusões ou dados armazenados. Manter os demais domínios inalterados; o tratamento editorial de comentários é opcional.

Validar layout em desktop, tablet e celular, ausência de overflow, proporção da capa, galeria e retorno do foco, expansão do elenco/sinopse e formulários de owner. Conferir ausência de imagens, títulos/sinopses longos e diferentes quantidades de imagens. Rodar lint, formatação, tipos e build de produção.
