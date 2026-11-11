# Destaque pessoal de faixas do álbum

## Problema e objetivo

Identificar visualmente as músicas mais ouvidas pelo owner dentro de um álbum, dando personalidade à lista sem alterar sua ordem ou sugerir popularidade global.

## Escopo aprovado

- Mostrar 🔥 junto do título das faixas com maior contagem de reproduções naquele álbum.
- Exigir pelo menos três reproduções da líder e duas faixas cadastradas.
- Em empate, destacar todas as líderes apenas quando forem até três. Se houver mais líderes ou todas as faixas tiverem a mesma contagem, não destacar nenhuma.
- Preservar discos, posições, ordem, avaliações e contagens. Sem gráfico, ranking separado ou controles de filtro.

## API/UI

Reutilizar playCount já retornado em GET /api/music/albums/{albumId}. Calcular o destaque nos dados de apresentação. Mostrar explicação “Entre as mais ouvidas por você neste álbum” por mouse e foco de teclado, com nome acessível. Aplicar ao owner e visitantes, como as contagens pessoais já públicas.

Arquivos: AlbumTrackList.vue, types/music.ts, utils/music.ts, utilitário de seleção e DESIGN.md. Sem alteração de API, migração ou dependência.

## Regras e fora do escopo

Usar indicador pequeno, sem alterar fundo das linhas ou adicionar vermelho decorativo. O dado representa reproduções registradas de faixas no álbum, sem misturar outros álbuns ou avaliações. Popularidade Spotify, favoritos, ordenação, animação e outros domínios ficam fora do escopo.

## Critérios de aceite

Conferir líder única, empate de até três, empate maior, todas iguais, poucas reproduções, álbum vazio/uma faixa, ordem original e atualização das contagens. Validar explicação por mouse e teclado em desktop/celular e ausência de overflow. Rodar checks e build do frontend.

## Validação realizada

- 11 verificações do seletor: vazio, faixa única, zero/poucas reproduções, líder única, empates de duas/três/mais líderes, todas iguais e ausência de mutação da entrada.
- Chromium em 1440px e 390px, owner/visitante: oito distribuições de contagens, preservação da ordem e contagens de indicadores; explicação por foco e hover; tooltip dentro da largura disponível e ausência de overflow.
- Checks e build do frontend. Sem mudança de backend para o destaque. Versão avançada posteriormente para 1.21.0 a pedido do owner, com commit e push; sem release ou deploy nesta etapa.
