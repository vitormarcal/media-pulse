# Detalhe de filme: UI, UX e copy

Escopo aprovado: reorganizar o detalhe com a simplicidade editorial de livros, preservando imagens e memória pessoal.

## Experiência final

- Abertura editorial seguindo séries e filmes: navegação dentro do bloco, título amplo e sinopse à esquerda, capa integral dominante à direita e fundo suave da própria capa (8%).
- Até quatro imagens adicionais da própria obra abaixo, sem repetir a capa; proporções naturais, inclusive horizontais. Com duas imagens, a complementar já fica visível.
- Sem capa ou em falha, remover coluna e fundo derivado. Ausência de imagens não bloqueia leitura ou seleção de imagens pelo owner.
- No celular: identificação, capa, faixa horizontal de complementares e memória pessoal. Capa limitada a 440px no tablet e 380px no celular.
- Nota, marcações e comentários na base do bloco, separados por linha discreta. Comentários longos não alteram a altura da abertura.
- Cada imagem abre seu índice na galeria existente, com retorno de foco. Ver todas e Escolher imagens junto do conjunto; curadoria permanece no visualizador.
- Sinopse expansível e título sem overflow. Sessões, créditos, empresas, termos, listas e coleção preservados.

## Decisões

A composição editorial aprovada nas bibliotecas passa a representar uma única obra no detalhe. A capa domina o conjunto e as complementares preservam seu formato, substituindo o mosaico compacto. A direção pode evoluir gradualmente nos demais domínios, conforme DESIGN.md; esta implementação altera apenas o detalhe de filmes.

## Escopo técnico e aceitação

Reutilizar contratos e dados existentes, sem API, migração, dependência ou novo provedor. Não alterar importação, escala de avaliação, exclusões ou dados armazenados. Manter os demais domínios inalterados; o tratamento editorial de comentários é opcional.

Validar layout em desktop, tablet e celular, ausência de overflow, proporção da capa, galeria e retorno do foco, expansão do elenco/sinopse e formulários de owner. Conferir ausência de imagens, títulos/sinopses longos e diferentes quantidades de imagens. Rodar lint, formatação, tipos e build de produção.
