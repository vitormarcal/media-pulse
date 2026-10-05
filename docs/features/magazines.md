# Revistas: números e jornadas de leitura

## Problema

O arquivo pessoal ainda não oferece registro de leitura de revistas. A biblioteca de livros é alimentada pelo Hardcover e não representa o ciclo de uma publicação periódica com números e releituras próprios.

## Objetivo

Guardar localmente os números que o owner pretende ler, está lendo, leu ou abandonou, com progresso, datas, releituras e comentários. Oferecer uma experiência visual consistente com livros, filmes, séries e jogos.

## Escopo

- Seção Revistas independente de Livros.
- Publicação com nome obrigatório e ISSN opcional.
- Número vinculado à publicação, identificado por numeração e/ou mês/ano; capa e total de páginas opcionais.
- Cadastro manual da publicação e do número no mesmo fluxo de registro, reutilizando publicações e números já existentes.
- Jornadas com estados Quero ler, Lendo, Lida e Abandonada, datas de início e término e progresso.
- Releituras como novas jornadas do mesmo número, preservando as anteriores.
- Edição e exclusão de registros para corrigir erros.
- Comentários por número pelo recurso compartilhado dos outros domínios, disponíveis independentemente do estado da leitura.
- Biblioteca visual, busca, filtros por publicação e estado e histórico de leitura.

## API/UI

O backend terá contratos próprios sob `/api/magazines` para consultar e manter publicações, números e jornadas. Comentários estenderão o contrato existente `/api/comments/{mediaType}/{entityId}` para números de revistas, sem criar um sistema paralelo. Dados e capas serão armazenados localmente com os mecanismos existentes. A implementação exigirá migration Flyway para o domínio e sua participação nos comentários.

A navegação terá a entrada Revistas. A página `/magazines` seguirá a composição das bibliotecas existentes: cabeçalho visual com ação de adicionar, destaque para leituras em andamento e recentes, seguido de arquivo com busca, filtros, cards e “Carregar mais”. Nos recortes filtrados, priorizar os resultados, como nas bibliotecas atuais. Cada card representa um número e mostra publicação, identificação, capa ou fallback, estado/progresso e atividade recente; releituras não duplicam o número no catálogo.

O detalhe de um número combinará capa e metadados no cabeçalho, painel compacto de leitura, comentários compartilhados e histórico das jornadas. A publicação será um vínculo para seu recorte na biblioteca. Ações de iniciar, atualizar progresso, concluir e reler devem estar acessíveis no detalhe. A edição de uma jornada abre dentro do item correspondente no histórico, com foco e rolagem suave quando necessários. Mês/ano usa seleção explícita de mês e ano numérico em todos os navegadores. O registro permite escolher uma publicação existente ou criar uma nova sem sair do fluxo; campos opcionais são revelados sob demanda.

Referências existentes: bibliotecas em `frontend/app/pages/{books,movies,shows,games}/index.vue`, detalhes nos mesmos domínios, `BookLibraryCard`, `BookReadTimeline`, painéis de registro manual de filmes/jogos e `MediaCommentsPanel`. Reutilizar componentes compartilhados quando compatíveis e seguir a composição dos componentes de domínio quando houver diferenças de comportamento.

Seguir `DESIGN.md`: capas em destaque, grid responsivo, superfícies e neutros quentes, tipografia e tokens existentes, cantos arredondados e pouca sombra. Preservar hierarquia de cabeçalhos, espaçamentos, navegação, estados de carregamento/erro/vazio e tratamento de formulários das páginas atuais. Manter rótulos acessíveis, foco visível, operação por teclado, feedback de gravação e dados preenchidos em caso de erro.

## Non-goals

- Artigos individuais, assinaturas ou gestão de coleção física.
- Integrações externas, importação ou enriquecimento automático.
- Unificação dos modelos de livros e revistas ou criação de uma área conjunta Leituras.
- Notas, favoritos, listas e recursos sociais nesta entrega.
- Histórico de cada alteração intermediária de progresso; esta entrega conserva o progresso atual de cada jornada e as jornadas anteriores.

## Heurística/regras

- ISSN identifica a publicação, não o número; sua ausência não bloqueia o cadastro.
- Exigir numeração, mês/ano ou ambos para identificar um número. Não exigir catálogo prévio de números sem intenção de leitura.
- Quero ler não exige datas de leitura. Iniciar uma jornada registra a data de início escolhida pelo owner.
- Concluir uma jornada iniciada preserva seu início e registra o término informado. Marcar diretamente como Lida cria a jornada com início e término iguais à data de leitura escolhida.
- Releitura cria uma nova jornada; atualizações de estado e progresso alteram a jornada em curso. Permitir no máximo uma jornada em aberto por número.
- Progresso pode ser informado diretamente em porcentagem ou pela página atual. Quando houver total, calcular `página atual / total × 100`; sem total, permitir porcentagem direta e não inferir total de páginas.
- Validar porcentagem entre 0 e 100, total de páginas positivo, página atual não negativa e não superior ao total quando conhecido. Término não pode anteceder início.
- Concluir define progresso como 100%. Ao salvar porcentagem de 100% ou a página final, o backend muda o estado para Lida e preenche o término com o dia atual do servidor, preservando o início; sem início, usa o mesmo dia para ambos.
- Comentários pertencem ao número e continuam disponíveis entre jornadas, com o mesmo comportamento de criação, edição e exclusão dos demais domínios.
- Ordenar a biblioteca pela atividade de leitura mais recente; manter o histórico de cada número distinguindo todas as jornadas.

## Acceptance criteria mínimos

- Cadastrar um número com numeração e/ou mês/ano, com ou sem ISSN, capa e total de páginas.
- Registrar Quero ler, iniciar, atualizar progresso e concluir posteriormente sem perder a data inicial.
- Marcar diretamente como Lida com início e término iguais.
- Informar porcentagem diretamente e calcular porcentagem pela página atual quando houver total.
- Abandonar uma jornada e registrar releituras mantendo o histórico anterior.
- Criar, editar e excluir comentários em qualquer estado pelo recurso compartilhado.
- Corrigir ou excluir registros e consultar os dados após recarregar a página.
- Navegar por publicação e estado, buscar números e carregar mais itens sem duplicar cards por releitura.
- Apresentar biblioteca e detalhe com padrões visuais e de interação consistentes com os domínios existentes, inclusive em celular e por teclado.

### Navegação do acervo

“Adicionar número” cadastra uma edição, reutilizando uma publicação ou criando-a quando necessário. O filtro “Leitura” mantém os estados de jornada e acrescenta “Não lida” (nenhuma jornada concluída). Publicações mostra contagens e abre o acervo daquela publicação, agrupado pelo ano de edição; números sem data ficam ao final. A paginação é mantida.

`GET /api/magazines` aceita `unread=true`; ao filtrar por publicação, ordena por mês de edição decrescente, sem data ao final. `GET /api/magazines/publications` inclui `numbersCount`. `overview.recent` retorna pares `{issue, read}` das seis jornadas concluídas mais recentes, inclusive releituras e números com nova leitura ativa. Cards do acervo não mostram a data de atividade; cards de últimas leituras mostram o término da jornada.
