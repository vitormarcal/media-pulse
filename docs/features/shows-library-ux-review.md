# Revisão incremental da página Séries

Status: aprovado, implementado e validado. Versão de entrega: 1.37.0.

## Problema

O owner aprovou a composição principal atual da /shows e quer preservá-la. A página repete séries dentro do próprio destaque e em blocos subsequentes; a busca aparece após cerca de 5.900px no desktop e 17.200px no celular nas amostras inspecionadas. Panorama, histórico recente e catálogo repetem informações. Rótulos como Aberta, Fechada, Intocada e Últimos episódios misturam progresso pessoal, estado da produção e dados agregados por série. Contagens de recortes limitados parecem totais.

## Objetivo

Manter a abertura editorial que o owner gosta e tornar mais direta a consulta ao acervo de séries, com progresso e linguagem precisos. A revisão não deve reconstruir o bloco principal a partir da página de filmes nem transformar a entrada em um painel de filtros.

## Escopo

1. Preservar a composição principal de ShowsCollectionHero: título editorial integrado à esquerda, destaque à direita, secundários abaixo, atmosfera, proporções e tratamento visual das imagens existentes. Acrescentar busca na base do mesmo bloco. Não aproveitar a tarefa para alterar globalmente a home, filmes ou os detalhes de séries.
2. Deduplicar candidatos por showId antes de selecionar destaque e até quatro secundários. Priorizar no destaque a série com progresso parcial e atividade mais recente retornada por currently-watching; para o mesmo ID, preferir o candidato com progresso disponível. Demais candidatos seguem recência, excluindo o destaque. Se não houver séries em andamento no recorte, usar recentes com título Séries recentes, sem afirmar andamento. Com poucos registros, mostrar somente os disponíveis.
3. Em andamento deriva de episódios cadastrados parcialmente vistos e atividade nos últimos 120 dias. Identificar esse recorte discretamente, sem apresentar quantidade carregada como total global ou inferir preferência, abandono ou disponibilidade de lançamento. Não prometer Continuar reprodução ou próximo episódio: os contratos usados não fornecem esse dado nem acesso a um player.
4. Substituir os blocos Abertas agora, Rotação recente e Últimos episódios por essa abertura única. O acervo continua completo abaixo; repetição entre destaque e acervo é esperada. Listas vira acesso compacto junto da identificação, e Adicionar série fica contextual e exclusivo do owner, com formulário sob demanda e rascunho preservado ao recolher ou em erro.
5. Busca por título/nome original integrada à abertura padrão; filtros junto do acervo. Em busca, ano, sem episódio registrado ou cadastro ativo, apresentar diretamente controles e conteúdo. Ano significa ano do registro de visualização, não ano de estreia. Busca considera todo o acervo e sai do recorte anual explicitamente; não combinar por filtro local de uma resposta incompleta. Manter URLs, limpeza e voltar/avançar.
6. Acervo em grade regular responsiva por linhas, com capas integrais e títulos completos; carregamento automático por cursor ao se aproximar do fim, preservando ordem do backend. Erro mantém itens/cursor e interrompe tentativas automáticas até retry explícito. Descartar respostas de filtros anteriores, deduplicar IDs e desconectar observador ao sair. Sem botão de próxima página no fluxo normal.
7. Cartões priorizam título, ano disponível, progresso e última atividade uma vez. Usar n/total episódios vistos para progresso parcial quando o total for conhecido. Para todos os episódios vistos, referir-se somente aos episódios cadastrados, sem afirmar que a produção terminou. Sem registros, informar a ausência uma vez; sem total, não inventar porcentagem ou mostrar 0/0. Não repetir selo, porcentagem e contagem com o mesmo significado. Busca e recorte anual não mostram progresso global que o contrato não fornece.
8. Remover panoramas estatísticos separados e resumo ao final. Junto do título do acervo, mostrar somente total global de séries; no filtro sem episódio registrado, total correspondente; no ano, séries e registros de visualização daquele ano. Registros podem incluir reassistidas e não são episódios únicos. O campo rewatchesCount atual (registros menos séries distintas) não mede reassistidas e não deve ser exibido como Revisitas. Busca não recebe totais globais ou contagem limitada como total.
9. Falhas de andamento, recentes, catálogo e estatísticas não bloqueiam toda a página; oferecer recuperação contextual. Ausência de dados/imagem tem apresentação legível. Preservar contraste, foco, teclado e rolagem da faixa móvel; imagens continuam protagonistas. Alterações de cartões/grade devem ser específicas da /shows, preservando listas, pessoas, termos, detalhes e temporadas.

## API/UI

Reutilizar /api/shows/currently-watching, /recent, /library, /search, /stats e /year/{year}; não buscar o detalhe de cada série para montar os cartões. currently-watching usa activeWithinDays=120 e é um recorte limitado, não todo o histórico em andamento. recent agrega a última atividade por série; não contém uma lista de episódios com título/número. Progresso usa episódios distintos vistos; watchesCount conta registros e inclui repetições.

Busca retorna até 40 correspondências e pode combinar com sem episódio registrado, indicando o limite antes do filtro quando atingido. Consulta anual pode pedir até 1.000 séries e deve indicar eventual recorte. year.unwatched reúne séries sem nenhum registro em todo o histórico; não apresentá-las como ausentes daquele ano. Não alterar contratos ou deduzir datas/temporadas/episódios inexistentes.

Principais arquivos: pages/shows/index.vue; ShowsCollectionHero, ShowsLibraryFilters, ShowsLibraryGrid e ShowLibraryCard por opções específicas; composables/useShowsCollectionData e useShowsLibraryData; utilitário de apresentação de séries. Registrar a decisão em DESIGN.md e docs/shows-api.md. DESIGN seções 1, 3–8 e 17 orientam a solução; seção 18 preserva detalhes já revisados.

Sem migração, integração ou dependência nova prevista.

## Fora do escopo

Reformular a abertura aprovada; redesenhar detalhes/temporadas/listas; tradução ou correção automática do acervo; alterar importação; player; marcar episódios pela biblioteca; próximo episódio; classificação editorial gerada; novos filtros/ordenações sem contrato; relatório completo de episódios ou reassistidas. Esses recursos exigem descoberta própria.

## Critérios de aceite

- Comparação visual antes/depois no desktop grande, tablet e celular: composição principal preservada, séries sem repetição dentro do destaque, busca na base e filtros junto do acervo.
- Zero/uma/várias séries em andamento ou recentes, progresso parcial/completo/total ausente, múltiplos registros do mesmo episódio e imagens ausentes/em falha, sem inferências indevidas.
- Busca, ano da visualização, sem episódio registrado, limpar e voltar/avançar; limites e totais corretamente identificados.
- Carregamento automático sem clique, fim de cursor, erro sem loop/retry, deduplicação e troca de filtro com resposta pendente, preservando itens anteriores.
- Owner/visitante: cadastro sob demanda, rascunho e erro, Listas navegável; foco/teclado e nenhum transbordamento.
- Lint/formatação, tipos, build e navegador com APIs simuladas; conferência pública por leitura, sem mutações em produção. Publicação em etapa posterior.


## Resultado e validação

Implementação restrita à biblioteca de séries, com opções locais em cartão/grade; preservados os contratos e o desenho principal aprovado. Sem migração, dependência ou alteração de backend. O total global soma corretamente séries com registros e sem registros.

- Conferência da abertura com dados reais por leitura em 1920, 1440, 1024, 768, 390 e 320px; comparação antes/depois e faixa móvel por teclado, sem transbordamento horizontal. Busca aproximada em 1.160px no desktop e 1.340px no celular, antes em 5.900px e 17.200px nas amostras.
- Navegador com APIs simuladas em 1440/390px, visitante/owner: deduplicação de destaque e páginas, progresso parcial/completo/sem total, imagem ausente/em falha, filtros, limites, voltar/avançar, estados vazio/uma série, cadastro com rascunho e retry, falhas independentes, paginação sem loop e resposta antiga descartada após troca de filtro.
- Lint, formatação, tipos e build estático. Há aviso de lint preexistente em ArtistPageHero.vue, fora deste escopo. Listas não adota os estilos opcionais da grade revisada.

Entrega na release `v1.37.0`, com imagens `ghcr.io/vitormarcal/media-pulse:1.37.0` e `latest`. Aplicação no Mordor por pull de `latest` e recriação do serviço, preservando a configuração existente.
