# Existência de imagens na seleção de jogos

## Problema

A seleção não informa se o Visual Library já possui os mesmos bytes ou se o jogo já tem a referência correspondente.

## Objetivo

Informar a existência durante a escolha, mantendo o Visual Library responsável pelos assets e pela deduplicação.

## Escopo

- Verificação progressiva das opções visíveis em pequenos lotes no backend.
- Indicar “Já no jogo” e “No Visual Library”, também no visualizador ampliado.
- Preservar seleção, navegação e salvamento durante verificações e falhas.

## API/UI

Novo `POST /api/games/{gameId}/images/lookup`, protegido por owner e CSRF, recebe IDs transitórios de até oito candidatos e devolve estados por opção. O backend usa `POST /api/assets/lookup` do Visual Library 0.27.0 com o token já configurado. A interface envia lotes de até quatro opções conforme entram na tela.

## Non-goals

Persistir URLs/hashes/histórico de verificação, deduplicar por similaridade, ocultar ou bloquear opções, criar assets durante consulta, alterar a API do Visual Library, adicionar migrations/dependências.

## Heurística/regras

- Buscar originais apenas dos IDs revalidados nos resultados dos provedores; nunca baixar URL enviada pelo navegador.
- Calcular SHA-256 dos bytes completos do original, nunca da miniatura ou da URL. Descartar bytes e hashes após a requisição.
- Comparar IDs retornados com as referências remotas atuais da capa e da galeria do jogo. Sem vínculo no jogo, um asset encontrado é “No Visual Library”.
- Limitar cada download a 10 MiB e manter os timeouts existentes. Processar um lote da interface por vez.
- Resultado negativo significa somente ausência de bytes exatos no índice; não exclui cópias parecidas ou legados sem hash.
- Estado é transitório e informativo. Falha/serviço desabilitado é informado uma vez, sem classificar opções como novas. A interface não bloqueia seleção ou envio e ignora respostas de uma busca anterior.
- Salvar continua pelo upload existente para registrar referência/tags e obter o ID; o lookup não substitui esse passo. Após envio bem-sucedido com Visual Library ativo, o estado pode ser atualizado para “Já no jogo”.

## Critérios de aceite

- Diferenciar asset no Visual Library, referência já no jogo, ausência e falha de verificação.
- Não baixar todas as opções de uma vez nem enviar arquivos durante consulta.
- Manter opções selecionáveis e preservar escolhas ao verificar, navegar, salvar ou falhar.
- Validar autenticação, hashes de bytes, respostas remotas inválidas, serviço ausente, concorrência de buscas e original diferente de miniatura.

## Validação

Testes de serviço conferem hash dos bytes originais, quatro estados, origem ausente, indisponibilidade, vínculo pela capa/galeria e ausência de persistência/upload na consulta. O cliente foi exercitado com servidor HTTP local para autenticação Bearer, lote com repetição, ausência, 401/404 e respostas inválidas. O contrato HTTP local também foi validado.

Frontend passou lint, Prettier, tipos e build. Smoke Chromium em 1440 e 390px, com APIs simuladas e 24 opções, verificou consulta somente das opções visíveis, lotes de até quatro, uma requisição por vez, seleção sem bloqueio em todos os estados, avisos na ampliação e envio somente ao confirmar. Outra rodada reteve resposta antiga, iniciou nova busca e confirmou que o resultado antigo não substitui a nova verificação; falha e consulta pendente preservaram as escolhas. Nenhum acervo real foi usado.

A revisão visual do [seletor em tela cheia](game-image-picker-ux.md) mantém os estados da API: `IN_GALLERY` aparece como “Já no jogo” e `IN_LIBRARY` como “No Visual Library”, com ícones distintos da seleção. Verificação fica discreta; ausência não ganha badge e falha geral é informada uma vez.
