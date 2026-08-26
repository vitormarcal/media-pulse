# Enriquecimento automático de mídia

## Problema

Filmes e séries entram no Media Pulse por caminhos diferentes — Plex, webhook, criação manual ou reconciliação de identificadores. Quando cada caminho trata o enriquecimento de forma própria, o catálogo fica parcialmente preenchido e a interface pode apresentar dados ausentes como se fossem inexistentes.

O proprietário do arquivo não deve precisar saber qual provedor consultar ou qual botão acionar para obter pessoas, empresas, termos e metadados básicos.

## Objetivo

Fazer com que o enriquecimento seja uma capacidade automática, assíncrona e local do catálogo:

- a ingestão nunca depende da disponibilidade do provedor;
- IDs conhecidos são reconciliados automaticamente;
- dados derivados são persistidos no banco local;
- cada etapa pode falhar e ser repetida independentemente;
- a UI comunica estados relevantes, sem transformar pendências em lacunas definitivas.

## Princípios

- O banco local é o arquivo canônico; TMDb, IMDb, TVDb e Plex são fontes de entrada e enriquecimento.
- Automação é o fluxo padrão; ações manuais são mecanismos de correção e curadoria.
- Importação e registro de consumo têm prioridade sobre chamadas externas.
- Um resultado vazio depois de uma sincronização concluída é diferente de uma sincronização que ainda não aconteceu.
- A solução deve reutilizar serviços, timestamps e padrões existentes antes de introduzir infraestrutura nova.
- O enriquecimento de cada domínio deve respeitar o modelo local e não importar conceitos só porque outro domínio os possui.

## Escopo por domínio

### Filmes

O enriquecimento automático pode incluir resolução IMDb → TMDb, metadados básicos, coleção oficial, gêneros e tags, pessoas e créditos principais, empresas de produção e imagens quando fizer parte do fluxo de catálogo.

### Séries

O primeiro recorte deve incluir resolução IMDb → TMDb quando houver suporte, metadados básicos, pessoas e créditos principais, temporadas e episódios conforme as regras existentes.

Empresas, termos e outros recortes só devem ser adicionados quando houver modelo, regra de negócio e experiência de UI definidos para séries. A implementação não deve copiar automaticamente o escopo de filmes.

## Fluxo de processamento

```text
ingestão local → identificadores conhecidos → resolução de identidade
    → fila local de etapas pendentes → worker assíncrono → persistência por etapa
```

Todos os caminhos que criam ou atualizam uma entidade devem convergir para a mesma regra: importação Plex, webhooks, criação manual, fluxos de watch e aplicação manual de metadados.

O caminho de entrada grava primeiro o registro local. O worker pode ser acionado depois da importação, por scheduler ou por uma ação explícita de reparo.

## Identidade e etapas

- Um vínculo TMDb existente não deve ser sobrescrito automaticamente.
- IMDb deve localizar TMDb quando o vínculo TMDb estiver ausente.
- Para séries, TVDb continua sendo identificador próprio e não substituto silencioso de TMDb.
- Conflitos com outro registro local exigem reconciliação segura.
- Falhas ou ausência de correspondência devem ficar registradas como estado, não interromper a ingestão.

Cada etapa deve consultar o identificador local necessário, persistir o resultado, marcar seu próprio timestamp, ser idempotente e poder falhar sem desfazer etapas já concluídas. Uma falha de créditos não deve impedir empresas ou termos; uma falha de identidade mantém dependentes pendentes.

## Estados

- `PENDING`: há etapa aguardando processamento ou retentativa;
- `COMPLETE`: todas as etapas aplicáveis foram concluídas, inclusive quando uma fonte retornou lista vazia;
- `BLOCKED`: não há identidade suficiente ou a resolução automática não encontrou correspondência.

O detalhe da entidade deve expor o estado agregado e, quando útil, o estado individual por etapa. O estado não deve ser inferido apenas pela quantidade de itens retornados.

Tentativas de resolução sem correspondência devem registrar quando ocorreram. Retentativas usam intervalo controlado para evitar que identificadores inválidos ocupem continuamente o início da fila.

## Operação

A primeira implementação deve preferir uma fila implícita baseada nos timestamps de sincronização existentes e em um worker protegido contra concorrência. O worker deve processar lotes pequenos, respeitar limites dos provedores, continuar após falhas, registrar progresso, repetir pendências e não bloquear webhooks ou importações.

Fila externa ou infraestrutura distribuída só deve ser considerada se o modelo local deixar de ser suficiente para o uso pessoal do produto.

## UX e UI

No uso normal, dados enriquecidos aparecem como parte natural da página. Enquanto uma etapa estiver pendente, a UI pode mostrar uma indicação discreta como “Completando informações…” e atualizar automaticamente por um período curto.

A UI não deve apresentar “nenhuma pessoa”, “nenhuma empresa” ou “nenhuma marcação” antes de a respectiva etapa terminar. Depois de `COMPLETE`, uma lista vazia é resultado válido e a seção pode ser omitida quando isso preservar a clareza visual.

Em `BLOCKED`, a UI explica o próximo passo em linguagem de produto. Sincronização, atualização da fonte e vínculo manual permanecem no modo de curadoria, como ações secundárias. Mensagens técnicas e detalhes de retry não pertencem ao fluxo normal.

## API e documentação

Cada domínio deve documentar endpoints de leitura do estado, endpoints de reparo, etapas automáticas, identificadores aceitos e regras de pendência, conclusão e bloqueio.

Os endpoints `sync-tmdb` existentes podem continuar disponíveis para curadoria e backfill, mas não devem ser o fluxo principal da UI quando o worker automático estiver ativo.

Detalhes específicos permanecem em [`movies-api.md`](../movies-api.md), [`shows-api.md`](../shows-api.md), [`plex-movie-ingestion.md`](../plex-movie-ingestion.md) e [`plex-show-ingestion.md`](../plex-show-ingestion.md).

## Non-goals

- Criar um framework genérico de enriquecimento para todos os domínios.
- Introduzir fila externa ou arquitetura distribuída.
- Fazer chamadas externas síncronas dentro de webhooks.
- Sobrescrever silenciosamente correções manuais.
- Importar todos os dados de um provedor sem recorte de produto.
- Fazer filmes e séries compartilharem entidades ou regras que o domínio não suporta.

## Critérios de aceitação

- Filme ou série importado pelo Plex entra no catálogo mesmo quando o provedor está indisponível.
- Item com identificador suficiente é enriquecido automaticamente após a ingestão.
- IMDb sem vínculo TMDb é tentado automaticamente e não fica processando infinitamente.
- Falha em uma etapa não desfaz as demais.
- Pendências são retomadas pelo worker sem ação manual.
- UI diferencia pendência, resultado vazio e bloqueio.
- Usuário não precisa iniciar sincronização no fluxo normal.
- Ações manuais continuam disponíveis para correção e curadoria.
- Documentos de filmes e séries não contradizem o comportamento adotado.
