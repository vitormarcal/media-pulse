# Linha do tempo pessoal

## Problema e objetivo

Os históricos separados por domínio dificultam revisitar a vida cultural e física do owner. `/timeline` reúne o passado disponível em ordem decrescente, com navegação direta por mês/ano e régua lateral no desktop e no celular, mantida para avaliação de uso. A revisão de UX exibe somente atividades; inclusões técnicas no arquivo deixam de fazer parte da página.

## Escopo e regras

- Música: um resumo por dia, número de reproduções e links para álbuns e artistas. A reprodução continua sendo o registro canônico; não cria eventos persistidos.
- Filmes e séries: cada sessão/registro de episódio, incluindo repetições.
- Livros e revistas: marcos de início e fim das jornadas, preservando releituras. Quero ler sem data de atividade não vira leitura. Situações atuais de pausa/abandono não são projetadas para uma data histórica inexistente.
- Jogos: início e fim de sessões, com conclusão/abandono identificado no fim quando informado. Backlog não é apresentado como jogo realizado.
- Artes: experiências com data, sem usar a data do cadastro como contemplação.
- Treinos: data/hora do início, duração e métricas disponíveis; link para o histórico de treinos, que não possui rota de detalhe individual.
- A API preserva a consulta de adições ao arquivo local, sem exibi-las na página: `created_at` de álbuns, livros, filmes, séries, jogos e obras. Importações são identificadas como inclusão **local**, sem afirmar quando o owner descobriu a obra ou a adicionou na plataforma original. Não incluir episódios, faixas, edições ou artistas como adições separadas.
- Revistas não possuem data imutável de inclusão: `activity_date` é atualizável e não é usada como adição. Treinos já aparecem como atividade.
- Descobertas sem data explícita não são reconstruídas. Não há um evento `DISCOVERY` neste contrato inicial: adição local é o único marco de entrada disponível nesses modelos. Datas de lançamento, avaliações sem data e atualizações técnicas não viram eventos pessoais.

O fuso do arquivo é `America/Sao_Paulo`, explicitado na resposta. Datas civis de revistas/arte são preservadas; livros importados têm precisão diária, sem horário exibido. Música é agrupada nesse mesmo fuso e não recebe um horário fictício. Atividades com horário e adições retornam o instante original. Um dia pode conter vários marcos de uma mesma jornada; o resumo musical conta como um evento na régua.

## API/UI

Ver [contrato HTTP](../timeline-api.md). A página sempre envia `kind=ACTIVITY` para cards e períodos. O filtro de tipo foi removido; parâmetros legados `kind` na URL não habilitam inclusões. A API pública continua compatível com consultas de adições para outros consumidores, sem mudança no schema ou no cursor.

Abertura compacta com filtro por domínio; cards têm capas pequenas ao lado do texto também no celular. Resumo musical mostra até três álbuns com créditos, com expansão para os demais álbuns e links de artistas em seção separada. Sem álbuns, os artistas disponíveis aparecem diretamente. Expansão não remove links nem duplica artistas na lista principal de álbuns.

A régua mostra somente marcas anuais legíveis (distância mínima de 32px), mantendo o período visível num botão. Clique/toque/arrasto escolhe ano e abre painel com ano selecionável e meses em botões de 44px; apenas a escolha do mês navega. Meses sem atividade são desabilitados. Isso permite precisão com os 220 meses observados em produção, sem exigir seleção de pontos de aproximadamente 3px. Teclado mantém setas/mês, Page Up/Down/12 posições e Home/End/extremos; Enter/Espaço abre o painel. Escape e clique fora fecham com foco adequado. O painel não é modal.

Carregamento progressivo, retry independente, descarte de respostas antigas, filtros/mês na URL e Voltar/reload permanecem. Datas civis, links e paginação canônica não mudam.

## Non-goals

Sem novas integrações, tabela genérica de eventos, recomendações, estatísticas avançadas, edição na cronologia ou reconstrução de datas ausentes. Não altera os históricos canônicos.

## Aceite e validação

- Atividades dos oito domínios consultáveis, com filtro refletido na régua; nenhuma inclusão local na página.
- Navegação para meses antigos sem carregar o intervalo desde hoje.
- Ordem determinística e paginação por cursor sem repetições em acervo estável, inclusive em horários empatados.
- Música diária e datas civis preservadas; inclusões locais continuam disponíveis apenas na API.
- Régua e cards utilizáveis no desktop e no celular; teclado, foco e estados de erro acessíveis.
- Integração PostgreSQL cobre união dos domínios, datas antigas, fuso, releituras, exclusão de datas desconhecidas, filtros, saltos, cursor e validação HTTP.
- Validação executada: nove testes backend (incluindo três de integração PostgreSQL, sem skips), `ktlintFormat`, `npm run check` e build estático de produção. Chromium com APIs simuladas em 1440, 390 e 320px: régua sem sobreposição, teclado/mouse, saltos até 1970, reload/Voltar, filtros, música expansível, imagem em falha, estado vazio, retry independente e descarte de respostas antigas. O lint preserva um aviso já existente em `ArtistPageHero.vue`.

### Validação da revisão de UX

`npm run check` e build de produção concluídos. Chromium em 1440, 390 e 320px com snapshot de metadados/períodos reais e capas servidas pela produção: primeira atividade visível mais cedo, capas compactas, marcas anuais com distância mínima, resumo musical sem misturar artistas na prévia, requisições de página/períodos fixas em ACTIVITY (inclusive URL legada com ADDITION), seleção por toque em duas etapas, foco/Escape/clique fora, salto aos extremos, paginação sem perda, retry independente e seletor rolável em tela baixa. A simulação mantém os limites e estados de rede controlados, sem mutações em produção. Backend não foi alterado nesta revisão.

## Arquitetura

Controller fino, validação/cursor no service e consulta read-only no query repository. A união consulta as tabelas existentes, sem migration, dependência nova ou chamadas aos provedores. Imagens usam referências atuais do acervo e podem mudar conforme a curadoria. O backup continua sendo o banco e os armazenamentos próprios existentes.
