# Seleção de imagens de séries

Status: aprovado e implementado.

## Problema

O enriquecimento de séries oferece o pôster e o fundo principais do TMDb. Falta explorar e guardar alternativas com o seletor já usado em filmes.

## Objetivo

Escolher pôsteres e fundos para a galeria da série, preservando a capa atual.

## Escopo

Pôsteres e fundos alternativos do TMDb para séries com vínculo confirmado. Reutilizar o seletor compartilhado, a galeria e o armazenamento de séries.

## API e UI

“Escolher imagens” junto da galeria, inclusive quando ela estiver vazia. O owner pode ampliar, selecionar várias e salvar. A seleção se mantém ao voltar durante a sessão. Consultar candidatos, conferir presença no acervo e salvar escolhas por endpoints de imagens de séries, com os mesmos contratos do seletor de filmes. Reutilizar as tabelas atuais.

## Fora do escopo

Imagens de temporadas e episódios, novos provedores, upload manual, exclusão e troca de capa pelo seletor.

## Regras

- Usar o vínculo TMDb salvo. Na ausência dele, orientar o vínculo pelo fluxo existente de atualização de dados.
- Validar os IDs de candidatos no backend e aplicar o limite de 50 imagens por salvamento.
- Guardar apenas imagens confirmadas, preservando a capa atual. Usar o Visual Library quando habilitado e o armazenamento local nos demais casos.
- Informar imagens já vinculadas ou no Visual Library quando a consulta estiver disponível. Em falha parcial, atualizar a galeria e manter as escolhas que falharam para nova tentativa.

## Critérios de aceite

- Conferir prévias integrais, seleção múltipla, ampliação, navegação por teclado e salvamento em desktop e celular.
- Conferir abertura sem imagens iniciais, ausência de vínculo TMDb, preservação da capa e nova tentativa após falha parcial.
- Validar autorização, limites e persistência, além dos fluxos existentes de filmes e jogos no seletor compartilhado.
