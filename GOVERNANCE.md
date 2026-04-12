Antes de implementar ou alterar qualquer funcionalidade no Media Pulse, siga estas regras.

# Objetivo

Manter o projeto simples, incremental, seguro e coerente com a arquitetura já existente.

# Arquitetura obrigatória

## 1. Namespace e estrutura

O backend vive em `dev.marcal.mediapulse.server`.

Estrutura base esperada:

- `api/` -> contratos HTTP (request/response DTOs)
- `config/` -> configuração Spring e binding de properties
- `controller/` -> endpoints HTTP
- `integration/` -> clientes e integrações externas
- `model/` -> modelos internos de domínio e integração
- `repository/` -> acesso a dados
- `repository/query/` e repositórios de leitura -> consultas read-only/relatórios
- `repository/crud/` e repositórios específicos -> persistência e lookup operacional
- `service/` -> regras de negócio, pipelines e orquestração
- `util/` -> helpers técnicos

Não introduza uma estrutura paralela sem necessidade clara.

## 2. Persistência

- Toda mudança de schema passa por Flyway em `server/src/main/resources/db/migration`
- Não criar tabelas fora de migrations
- Não depender de `ddl-auto` para evolução de schema

## 3. Estilo de código

- Kotlin idiomático
- DTOs e modelos simples com `data class` quando fizer sentido
- Controllers finos
- Regra de negócio em `service`
- Repositórios responsáveis por acesso a dados, não por fluxo de negócio
- Evitar classes gigantes e abstrações prematuras

## 4. Testes

Quando a mudança altera comportamento relevante, adicionar ou atualizar testes proporcionais ao risco:

- teste de serviço para regra de negócio importante
- teste de repositório para query/persistência crítica
- teste de integração quando o contrato HTTP ou fluxo entre camadas for sensível

Cobertura total não é requisito, mas partes críticas não devem ficar sem validação.

## 5. Documentação

Atualize a documentação sempre que houver mudança em:

- migrations
- endpoints HTTP
- variáveis/configurações
- comportamento operacional relevante
- decisões não óbvias descobertas durante debugging ou integração

Arquivos a revisar conforme o caso:

- `README.md`
- `docs/*.md`
- `docs/openapi.yaml` quando o contrato publicado mudar
- `frontend/README.md` se o fluxo de UI/local dev mudar

## 6. Regra da simplicidade

Se houver duas soluções viáveis:

- escolha a mais simples
- evite frameworks extras
- evite abstrações antes da hora

## 7. Regra incremental

Para mudanças maiores:

- escreva um checklist curto
- explicite arquivos principais a alterar
- cite migrations necessárias, se houver
- confirme o critério de aceite antes de implementar

## 8. Escopo

- Não adicionar funcionalidades fora do escopo pedido
- Não corrigir incidentalmente partes não relacionadas sem necessidade

## 9. Critérios mínimos de qualidade

O resultado final deve:

- compilar
- subir com `./server/gradlew bootRun` quando a configuração necessária estiver presente
- preservar separação clara de responsabilidades
- não deixar documentação contradizendo o comportamento real do código

## 10. Finalização obrigatória

Após mudanças de código no backend:

- execute `./server/gradlew ktlintFormat`
- corrija problemas revelados por formatação/checagens relacionadas
- só finalize quando o estado estiver consistente

Se a tarefa for apenas documentação e nenhum arquivo Kotlin for alterado, não é necessário rodar `ktlintFormat`.

## 11. Documentation-first

- Antes de implementar ou depurar, consulte `README.md`, `docs/` e notas de migração quando forem relevantes
- Reutilize padrões já documentados em vez de criar caminhos paralelos
- Se docs e código divergirem, alinhe explicitamente um dos lados

## 12. Higiene operacional

- Não documente segredos reais em arquivos versionados
- Prefira variáveis de ambiente e exemplos neutros
- Ao registrar comportamento de provedores externos, documente sintomas, hipótese/causa, decisão tomada e como validar
- Nunca ler `server/src/main/resources/application-local.yml` para análise, documentação ou implementação
- Trate `application-local.yml` como arquivo local do usuário para testes com dados reais, fora do escopo normal de inspeção

# Regra para novas features

Antes de escrever código em uma feature nova ou refactor amplo, você deve:

1. escrever um checklist curto com no máximo 12 itens
2. dizer quais arquivos principais serão criados/alterados
3. informar se haverá migration
4. confirmar o critério de aceite

Só então implementar.
