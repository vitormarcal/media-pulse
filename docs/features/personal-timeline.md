# Linha do tempo pessoal

## Problema e objetivo

Os históricos separados por domínio dificultam revisitar a vida cultural e física do owner. `/timeline` reúne o passado disponível em ordem decrescente, com navegação direta por mês/ano e régua lateral no desktop e no celular, mantida nesta entrega para avaliação de uso.

## Escopo e regras

- Música: um resumo por dia, número de reproduções e links para álbuns e artistas. A reprodução continua sendo o registro canônico; não cria eventos persistidos.
- Filmes e séries: cada sessão/registro de episódio, incluindo repetições.
- Livros e revistas: marcos de início e fim das jornadas, preservando releituras. Quero ler sem data de atividade não vira leitura. Situações atuais de pausa/abandono não são projetadas para uma data histórica inexistente.
- Jogos: início e fim de sessões, com conclusão/abandono identificado no fim quando informado. Backlog não é apresentado como jogo realizado.
- Artes: experiências com data, sem usar a data do cadastro como contemplação.
- Treinos: data/hora do início, duração e métricas disponíveis; link para o histórico de treinos, que não possui rota de detalhe individual.
- Adições ao arquivo local: `created_at` de álbuns, livros, filmes, séries, jogos e obras. Importações são identificadas como inclusão **local**, sem afirmar quando o owner descobriu a obra ou a adicionou na plataforma original. Não incluir episódios, faixas, edições ou artistas como adições separadas.
- Revistas não possuem data imutável de inclusão: `activity_date` é atualizável e não é usada como adição. Treinos já aparecem como atividade.
- Descobertas sem data explícita não são reconstruídas. Não há um evento `DISCOVERY` neste contrato inicial: adição local é o único marco de entrada disponível nesses modelos. Datas de lançamento, avaliações sem data e atualizações técnicas não viram eventos pessoais.

O fuso do arquivo é `America/Sao_Paulo`, explicitado na resposta. Datas civis de revistas/arte são preservadas; livros importados têm precisão diária, sem horário exibido. Música é agrupada nesse mesmo fuso e não recebe um horário fictício. Atividades com horário e adições retornam o instante original. Um dia pode conter vários marcos de uma mesma jornada; o resumo musical conta como um evento na régua.

## API/UI

Ver [contrato HTTP](../timeline-api.md). UI com filtros de domínio/tipo, imagens integrais, agrupamento por dia, carregamento progressivo e retry explícito. Registros existentes permanecem em falha de paginação; respostas de filtros antigos são descartadas. Filtros e mês de entrada ficam na URL e funcionam ao atualizar ou usar Voltar.

A régua compacta os meses sem registros, acompanha o período visível e permite clique, toque, arrasto e teclado (setas: mês disponível; Page Up/Down: 12 posições; Home/End: extremos). A seleção posiciona o histórico no mês escolhido e permite seguir em direção ao passado. O indicador de mês aparece ao passar o mouse, focar ou arrastar. A régua tem um único foco de teclado e área contínua de toque; pontos visuais não são alvos minúsculos independentes.

## Non-goals

Sem novas integrações, tabela genérica de eventos, recomendações, estatísticas avançadas, edição na cronologia ou reconstrução de datas ausentes. Não altera os históricos canônicos.

## Aceite e validação

- Todos os oito domínios consultáveis, com filtros refletidos na régua.
- Navegação para meses antigos sem carregar o intervalo desde hoje.
- Ordem determinística e paginação por cursor sem repetições em acervo estável, inclusive em horários empatados.
- Música diária, datas civis e datas de inclusão distintas das atividades.
- Régua e cards utilizáveis no desktop e no celular; teclado, foco e estados de erro acessíveis.
- Integração PostgreSQL cobre união dos domínios, datas antigas, fuso, releituras, exclusão de datas desconhecidas, filtros, saltos, cursor e validação HTTP.
- Validação executada: nove testes backend (incluindo três de integração PostgreSQL, sem skips), `ktlintFormat`, `npm run check` e build estático de produção. Chromium com APIs simuladas em 1440, 390 e 320px: régua sem sobreposição, teclado/mouse, saltos até 1970, reload/Voltar, filtros, música expansível, imagem em falha, estado vazio, retry independente e descarte de respostas antigas. O lint preserva um aviso já existente em `ArtistPageHero.vue`.

## Arquitetura

Controller fino, validação/cursor no service e consulta read-only no query repository. A união consulta as tabelas existentes, sem migration, dependência nova ou chamadas aos provedores. Imagens usam referências atuais do acervo e podem mudar conforme a curadoria. O backup continua sendo o banco e os armazenamentos próprios existentes.
