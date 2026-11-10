# Detalhe de filme: UI, UX e copy

Escopo aprovado: reorganizar o detalhe com a simplicidade editorial de livros, preservando imagens e memória pessoal.

## Experiência final

- Cabeçalho branco uniforme: identificação e sinopse à esquerda; memória pessoal abaixo do texto; capa/galeria à direita. No celular: identificação, imagem, memória pessoal.
- Nota compacta junto do título dos comentários; favorito/abandonado próximos. Comentários sem contador/resumo e com uma data absoluta por item, sem card aninhado.
- Capa única com proporção natural, integral e sem faixas laterais; altura máxima de 440px no desktop e 380px no celular. Com até duas imagens, mostrar só a capa; demais ficam na galeria. A partir de três, manter o mosaico.
- Sinopse longa expansível; título pode quebrar sem overflow.
- Sessões e “Sobre o filme” em superfícies brancas consistentes, sem cards internos. Datas de sessão sem estatísticas repetidas; registro sob demanda.
- Créditos, gêneros/tags e empresas com subtítulos neutros. Elenco inicia com seis nomes, com expansão; edição não limita nomes.
- Edição contextual, enriquecimento via “Atualizar dados”, galeria e confirmações preservados. Listas vazias ocultas para visitantes.

## Decisões

A avaliação visual publicada revelou cabeçalho com grande área vazia, nota isolada e tratamentos diferentes para seções relacionadas. Integrar memória pessoal ao espaço ao lado da capa resolve a composição sem diminuir a imagem. Fundo branco uniforme deixa a arte definir a personalidade visual; não duplicar a capa como fundo. A prévia com `contain` em caixa larga criou faixas cinzas: usar dimensões naturais limitadas pela área disponível. As seções 15 e 16 de DESIGN.md foram reconciliadas para distinguir capa integral e mosaico.

## Escopo técnico e aceitação

Reutilizar contratos e dados existentes, sem API, migração, dependência ou novo provedor. Não alterar importação, escala de avaliação, exclusões ou dados armazenados. Manter os demais domínios inalterados; o tratamento editorial de comentários é opcional.

Validar layout em desktop, tablet e celular, ausência de overflow, proporção da capa, galeria e retorno do foco, expansão do elenco/sinopse e formulários de owner. Conferir ausência de imagens, títulos/sinopses longos e diferentes quantidades de imagens. Rodar lint, formatação, tipos e build de produção. Manter a publicação na versão 1.14.0 existente conforme solicitação do owner.
