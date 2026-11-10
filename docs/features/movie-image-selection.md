# Seleção de imagens de filmes

Status: aprovado e implementado.

## Problema

O enriquecimento de filmes oferece somente o pôster e o fundo principais do TMDb, dentro de um fluxo de atualização de metadados. Falta explorar alternativas com a experiência já aprovada em jogos.

## Objetivo

Escolher quais imagens do TMDb guardar no filme, usando a experiência de seleção de jogos e preservando a capa atual.

## Escopo

- Pôsteres e fundos alternativos do filme identificado pelo vínculo TMDb local.
- Seleção múltipla, ampliação e salvamento explícito; manter escolhas ao fechar/reabrir durante a sessão.
- Reutilizar armazenamento e galeria existentes; falhas parciais preservam sucessos e mantêm as falhas selecionadas para retry.
- Reaproveitar a composição e os comportamentos de jogos, extraindo apenas o compartilhamento necessário; manter consultas e regras específicas de cada domínio.

## API/UI

“Escolher imagens” junto da galeria do filme, apenas para o owner. Seletor em tela cheia com nome do filme, retorno explícito, prévias integrais, seleção por checkbox, ampliação, contagem, limpar seleção e “Salvar N imagens”. Atualizar opções é separado de salvar; preservar teclado, foco, Escape e posição no seletor.

Consultar candidatos, conferir existência e salvar seleção nos [endpoints de imagens de filmes](../movies-api.md#escolher-imagens-do-tmdb), separados do enriquecimento de metadados. Reutilizar as tabelas e a integração de armazenamento existentes; sem migração prevista.

## Non-goals

Novos provedores, uploads manuais, logos, alterações automáticas de título/sinopse, troca implícita de capa, deduplicação própria e revisão dos demais domínios. A escolha de capa e a remoção continuam na galeria salva.

## Heurísticas e regras

- Consultar imagens somente pelo vínculo TMDb confirmado; se ausente, orientar a vinculação pelo fluxo existente.
- Buscar candidatos não salva arquivos. Guardar somente escolhas confirmadas.
- Enviar identificadores opacos revalidados no backend, sem aceitar URLs arbitrárias de download do navegador; até 50 seleções por requisição, como em jogos.
- Preservar capa e imagens atuais, inclusive em falhas ou reenvios. Trocar capa continua sendo uma escolha explícita na galeria.
- Reutilizar a apresentação de existência no acervo/Visual Library onde o contrato atual permitir, sem bloquear o seletor em indisponibilidade dessa consulta.

## Critérios de aceite

- Explorar pôsteres e fundos alternativos, ampliar e selecionar várias imagens sem salvar ao abrir/fechar.
- Salvar somente as selecionadas, refletindo o acervo na página sem alterar a capa atual.
- Preservar escolhas em falha HTTP e permitir retry apenas das falhas em sucesso parcial.
- Fluxo utilizável em desktop e celular, com teclado, foco e Escape corretos.
- Validar autenticação/CSRF, identificação dos candidatos, limites e persistência; conferir ausência de regressão no seletor de jogos.

## Validação

Testes de serviço, HTTP e integração local com o TMDb cobrem vínculo confirmado, filtro de referências, limites, autorização/CSRF, falha parcial e inclusão sem tocar na capa. Também passaram lint, formatação, tipos e build do frontend.

Smoke em Chromium com APIs simuladas, em 1440 e 390px, conferiu filmes e jogos: teclado, ampliação, retorno de foco, reabertura com seleção preservada, erro HTTP, salvamento parcial, retry e galeria atualizada. Incluiu um filme sem imagens iniciais para verificar que a primeira inclusão não fecha nem reinicia o seletor.
