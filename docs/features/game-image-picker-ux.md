# Seleção visual de imagens de jogos

## Problema

A lista de candidatos alonga a página do jogo, afasta o botão de salvar e apresenta estados de existência com o mesmo peso visual da seleção.

## Objetivo

Permitir escolher e conferir imagens em um espaço dedicado, distinguindo claramente uma escolha pessoal de uma imagem já existente.

## Escopo

- “Escolher imagens” abre um seletor em tela cheia, com retorno ao jogo e preservação das escolhas.
- Imagens em destaque, checkbox de seleção visível, tipo e provedor em hierarquia secundária.
- Barra fixa com contagem, limpar seleção e ação “Salvar N imagens”.
- Ampliação com navegação e marcação; retornar à seleção não fecha o seletor.
- Indicações “Já no jogo” e “No Visual Library” visualmente distintas da seleção.

## API/UI

Reutilizar os endpoints e o lookup progressivo atuais. Usar o padrão de cores quentes, tipografia, cantos e foco de DESIGN.md. Cards têm estado de seleção vermelho; existência é informativa e neutra. Nenhuma migration ou dependência.

## Non-goals

Alterar backend, deduplicação, armazenamento, limite de seleção ou capa; ocultar/bloquear imagens existentes; adicionar filtros, novas persistências ou gerenciamento de assets.

## Heurística/regras

- Clique na imagem amplia; checkbox marca. Estado de existência não usa o símbolo de seleção.
- Ausência de correspondência não cria badge “nova”. Verificação usa indicador discreto. Falha geral aparece uma vez e não bloqueia escolhas.
- Fechar/reabrir preserva seleções e resultados durante a sessão. Atualizar opções é uma ação separada e preserva seleções ainda disponíveis.
- A lista rola dentro do seletor; cabeçalho e barra de salvar permanecem acessíveis. Lookup observa somente candidatos que se aproximam da área visível.
- Escape na ampliação retorna ao seletor; Escape no seletor retorna ao jogo. Manter foco contido no diálogo ativo e devolver ao controle de origem, sem liberar a rolagem da página durante a ampliação.
- Sucesso mantém o seletor aberto com feedback; falhas parciais permanecem selecionadas para retry.

## Critérios de aceite

- Selecionar e salvar em desktop/celular sem perder o acesso à contagem e à confirmação.
- Diferenciar escolhas e existência, sem confundir imagem existente com item selecionado.
- Ampliar, navegar, marcar, voltar, fechar e reabrir preservando escolhas e foco.
- Manter lookup progressivo, estados de erro/ausência e funcionamento da galeria salva.

## Validação

Lint, Prettier, tipos e build estático passaram. Chromium com APIs simuladas em 1440, 768, 390 e 320px confirmou seletor em tela cheia, cabeçalho/barra de ações fixos, lookup progressivo, foco contido, navegação/ampliação, marcações e rolagem preservadas ao fechar/reabrir. Conferido espaço separado entre imagem ampliada e seus controles, inclusive em capas verticais.

Cenários adicionais cobriram aviso único em indisponibilidade, retry de falha parcial, limpar sem salvar, retry de busca, fechar durante busca ou envio, e perda da sessão durante a ampliação sem deixar a página com rolagem bloqueada. A galeria salva foi revalidada em desktop, tablet e celular. Nenhum dado real ou endpoint de provedor foi alterado durante os testes.
