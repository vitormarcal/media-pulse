# Filmografia completa ao explorar uma pessoa

Status: implementado; validação e publicação na versão 1.8.4.

## Problema

Filmografias de pessoas não favoritas são compactadas após sete dias da última sincronização. Ao explorar filmes ou séries, o owner recebe um recorte de vínculos locais pendentes e precisa solicitar outra atualização para acessar a lista completa. O primeiro clique já expressa a intenção de explorar esse conteúdo.

## Objetivo

Ao escolher Ver filmes ou Ver séries, apresentar a filmografia completa usando o arquivo local sempre que disponível e restaurando os dados externos automaticamente quando necessário. Preservar a compactação e os créditos canônicos existentes.

## Escopo

- Filmografias de filmes e séries na página da pessoa, com processamento independente.
- Consulta condicional ao TMDb iniciada pela exploração explícita do recorte.
- Reutilização dos estados e timestamps de `person_filmography_syncs`.
- Feedback de carregamento e falha, mantendo os dados locais conhecidos disponíveis.
- Nenhuma migration prevista: o estado necessário já existe no banco.

## API/UI

- Manter os GETs de filmografia como leituras exclusivamente locais.
- POSTs `POST /api/people/{personId}/filmography/ensure` e `POST /api/people/{personId}/show-filmography/ensure`: garantir a disponibilidade do recorte completo, consultando o estado local antes de decidir pela chamada externa.
- A decisão fica no serviço. O frontend não determina a validade dos dados apenas pela quantidade de membros ou pelo seu cache em memória.
- Expor estado suficiente para distinguir lista completa, compactada, nunca sincronizada e falha, inclusive quando os membros estiverem vazios.
- O clique Ver filmes/Ver séries dispara a garantia de disponibilidade. Apenas abrir o perfil ou selecionar uma aba ainda não explorada não baixa as duas filmografias.
- Mostrar Carregando filmografia… durante a operação. A página e os créditos locais continuam utilizáveis.
- Na falha, preservar a lista local, comunicar o erro e oferecer Tentar novamente.
- A tentativa explícita pode reutilizar o POST administrativo existente para ignorar a espera de retentativa. Esse endpoint continua disponível para curadoria.
- Preservar cards, agrupamentos por histórico, seleção do recorte, adição e vínculo de obras na própria página.
- Seguir DESIGN.md: tokens e superfícies quentes existentes, controles arredondados, foco visível e feedback acessível. Não exibir compactação, snapshots ou timestamps como instruções ao owner.

## Non-goals

- Alterar o prazo de sete dias ou a proteção de favoritos.
- Renovar o prazo por visualização ou baixar a cada acesso.
- Implementar a futura renovação periódica de snapshots concluídos após 30 dias.
- Baixar filmes e séries simultaneamente sem solicitação.
- Criar filmes, séries ou créditos durante a consulta da filmografia.
- Introduzir filas externas, frameworks ou novas dependências.

## Heurística/regras

1. Se há sincronização bem-sucedida e o recorte não foi compactado, reutilizar a lista local, inclusive uma lista legitimamente vazia. Ter mais de sete dias, por si só, não invalida dados que ainda não foram compactados.
2. Se nunca houve sincronização ou o recorte está compactado, consultar o TMDb e salvar a lista completa atomicamente.
3. O sucesso atualiza `synced_at`, limpa erro e `compacted_at` e reinicia o período de retenção. A leitura de dados completos não modifica esses campos.
4. Uma falha preserva membros e créditos anteriores, registra a tentativa e não renova a retenção.
5. Após falha recente, a abertura normal respeita a espera de um dia já adotada. Tentar novamente é a ação explícita que pode ignorá-la; não criar ciclos de chamadas automáticas em falha.
6. Coordenar chamadas concorrentes para a mesma pessoa e tipo de mídia, inclusive entre worker e exploração, reavaliando a disponibilidade antes de baixar novamente.
7. A compactação diária e a exclusão de favoritos permanecem. Favoritar não restaura automaticamente um recorte já compactado; a exploração usa a mesma regra condicional.
8. Os workers de primeira sincronização permanecem ativos. A restauração de dados compactados ocorre por demanda de exploração.

## Critérios de aceite mínimos

- Primeiro clique em recorte compactado ou nunca sincronizado restaura e apresenta a lista completa, sem segundo clique de atualização.
- Recorte completo, incluindo lista vazia, é retornado localmente sem chamada ao TMDb nem alteração do prazo.
- Reabrir ou alternar recortes já carregados não repete downloads dentro do mesmo período de retenção.
- Filmes e séries mantêm sincronização, estado de falha e retenção independentes.
- Falha mantém os dados conhecidos visíveis; nova tentativa tem feedback e não gera chamadas em loop.
- Chamadas concorrentes não provocam downloads simultâneos duplicados para a mesma pessoa e recorte.
- Compactação após sete dias e proteção de favoritos continuam validadas.
- GETs continuam sem efeitos externos; explorar não adiciona obras ou créditos ao catálogo.
- Interface distingue carregamento, falha e ausência confirmada de membros.

## Checklist de execução

- [x] Adicionar leitura explícita de disponibilidade no repositório.
- [x] Implementar garantia condicional e coordenação de chamadas nos serviços de filmes e séries.
- [x] Expor POSTs e estado nos DTOs, preservando GETs e reparos atuais.
- [x] Integrar o clique de exploração e feedback no painel existente.
- [x] Testar reutilização, restauração, lista vazia, falha, concorrência e retenção.
- [x] Atualizar contratos e documentação, executar ktlintFormat e checks do frontend, validar o fluxo no navegador.

## Principais arquivos previstos

- `server/.../repository/PersonFilmographyRepository.kt`
- `server/.../service/person/PersonFilmographyService.kt`
- `server/.../service/person/PersonShowFilmographyService.kt`
- `server/.../controller/people/PeopleController.kt`
- `server/.../api/movies/MovieDtos.kt`
- `frontend/app/components/people/PersonScreenographyPanel.vue`
- `frontend/app/types/movies.ts`
- Testes correspondentes de serviços, repositório e contratos HTTP.
- `docs/people-api.md`, `docs/openapi.yaml` e `docs/features/periodic-external-snapshot-refresh.md` para alinhar a exceção de restauração sob demanda com a política atual.
