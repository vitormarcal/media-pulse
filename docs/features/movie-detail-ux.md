# Detalhe de filme: revisão de UI, UX e copy

Escopo aprovado: reorganizar a página com a referência do detalhe de livro.

- Problema: contagens e datas repetidas, ações de catálogo em destaque e memória pessoal dispersa.
- Objetivo: priorizar identificação, comentários e sessões com menos ruído.
- UI: cabeçalho visual; marcações e nota compacta; comentários; sessões; sobre o filme; coleção e listas. Edição contextual e formulário de sessão sob demanda.
- API/dados: reutilizar contratos existentes, sem migração, dependência ou novo provedor.
- Fora do escopo: alterar importação, avaliações, regras de exclusão ou dados armazenados.
- Regras: comentários com uma data absoluta por item, sem contador/resumo; sessões sem card separado de estatísticas; manter edição, exclusão, registro, vínculos e galeria.
- Aceitação: informação pessoal antes dos créditos; títulos diretos; controles de owner preservados; layout responsivo; lint, formatação, tipos e build válidos.

## Acabamento após revisão visual

A página publicada de The Last House foi revisada em desktop e celular. Foram identificados cabeçalho alto, duplicação visual das duas imagens, nota deslocada por uma coluna vazia, elenco extenso no celular e rótulos repetidos.

- Cabeçalho menor, texto centralizado verticalmente e capa integral quando há até duas imagens; demais imagens continuam na galeria.
- Nota alinhada às marcações, sem descrição da escala competindo com o comentário.
- Elenco com seis nomes e “Ver elenco completo”/“Ver menos”, sem limitar a edição.
- Remoção de funções repetidas em direção/roteiro, origem manual nas sessões e seção pública de listas vazias.
- Publicação mantida em 1.14.0, com atualização explícita da tag existente solicitada pelo owner.

Validar ausência de overflow em desktop e celular, expansão do elenco e acesso à galeria, além dos checks do frontend e build de produção.

Correção visual: a capa única com `contain` dentro de uma caixa larga ganhou faixas cinzas e pareceu menor em produção. A prévia passa a seguir a proporção natural da imagem, sem fundo lateral, com altura máxima de 440px no desktop e 380px no celular. Manter a imagem integral e validar medidas renderizadas e acesso à galeria. Correção incluída na versão 1.14.0 existente.
