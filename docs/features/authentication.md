# Autenticação do proprietário

## Problema

O acervo deve continuar disponível para consulta pública, mas alterações e operações de curadoria e administração precisam ser exclusivas do proprietário da instalação.

## Objetivo

Permitir acesso autenticado a um único owner, usando apenas senha, com criação e recuperação de acesso controladas por uma autorização de uso único configurada no ambiente.

## Escopo

- Login por senha, sem nome de usuário nem cadastro público.
- Sessão do owner e logout.
- Consulta pública do acervo, incluindo páginas e imagens destinadas à navegação pública.
- Proteção no backend das alterações e operações de curadoria/administração; esconder essas ações na interface pública.
- Criação e redefinição da senha mediante UUID aleatório configurado no env e informado também na tela.
- Persistência local do hash da senha e dos identificadores das autorizações consumidas, por migration Flyway.
- Preservação das importações e workers automáticos, que não dependem de uma sessão no navegador.

## API/UI

- Tela de login com apenas senha e ação de logout quando autenticado.
- Tela de criação/reset com UUID de autorização e nova senha; não expor o UUID configurado ao navegador.
- Sem sessão válida, APIs protegidas recusam a operação, inclusive quando chamadas diretamente.
- A UI pública preserva a consulta e oferece acesso ao login; ações de edição e administração ficam disponíveis somente ao owner autenticado.
- Rotas e contratos concretos serão definidos na implementação, seguindo a arquitetura existente e o `DESIGN.md`.

## Non-goals

- Múltiplos usuários, perfis, permissões configuráveis ou cadastro público.
- Recuperação por e-mail, login social ou provedor externo de identidade.
- Tornar a consulta do acervo privada.
- Implementar a renovação periódica de snapshots ou uma nova área administrativa completa nesta entrega.

## Heurística/regras

- Um UUID novo configurado no env passa a autorizar criação/reset após iniciar a aplicação. Sem UUID configurado, esse fluxo fica indisponível; uma senha já criada continua válida para login.
- O solicitante precisa informar o UUID configurado e a nova senha. Conhecer apenas a URL da tela não autoriza a operação.
- Uma tentativa inválida não consome a autorização nem altera a senha existente.
- Salvar a senha e consumir a autorização acontece atomicamente: apenas uma tentativa pode concluir com o mesmo UUID.
- O consumo é persistido no banco. Reiniciar, remover e recolocar o mesmo UUID, ou reutilizar um UUID consumido anteriormente, não libera outro reset.
- A aplicação não precisa remover a variável do ambiente: depois do consumo, ela perde o efeito. Outro UUID permite uma nova redefinição.
- Guardar somente o hash da senha e uma representação não reversível dos UUIDs consumidos. Não registrar senha ou UUID em logs, respostas ou arquivos versionados.
- Redefinir a senha invalida as sessões anteriores.
- Antes da primeira senha, alterações e operações administrativas permanecem protegidas; a consulta pública continua disponível.
- Classificar endpoints pela finalidade, não somente pelo verbo HTTP. Leituras operacionais e GETs com efeitos de administração também precisam de proteção.
- Webhook Plex exige segredo independente configurado no env e informado na URL. Iniciar OAuth Spotify exige sessão owner; callback continua acessível com validação de state.
- Os POSTs condicionais de filmografia (`ensure`) continuam públicos; atualização forçada, adição de obras e vínculos exigem sessão owner.
- O owner configura a autorização em ambiente seguro e aceita a possibilidade de alguém que obtenha o UUID consumi-la primeiro.

## Critérios de aceitação mínimos

- Visitantes consultam o acervo sem login e não conseguem executar operações protegidas pela interface nem pela API.
- O owner cria a primeira senha com o UUID do env, entra apenas com senha e consegue sair.
- UUID incorreto, ausente ou consumido não permite criar/redefinir senha.
- Tentativas concorrentes com o mesmo UUID produzem no máximo uma criação/reset bem-sucedido.
- O consumo sobrevive a reinícios, inclusive quando um UUID antigo é configurado novamente.
- Um UUID novo permite redefinir a senha; a senha e as sessões anteriores deixam de permitir acesso.
- Sem UUID no env, o login existente e a consulta pública continuam funcionando, mas criação/reset ficam indisponíveis.
- Importações automáticas e integrações continuam funcionando conforme as políticas explicitamente definidas para suas entradas.
- Testes cobrem proteção HTTP, ciclo de sessão, consumo único e redefinição; documentação de configuração e contratos acompanha a implementação.

## Checklist de execução

- [x] Confirmar consulta pública, owner único e reset por UUID consumível.
- [x] Definir exceções Plex, Spotify e filmografia.
- [x] Adicionar migration V76, repositório e serviço de credencial.
- [x] Implementar sessão, proteção HTTP, CSRF e limite de tentativas.
- [x] Implementar login/reset e visibilidade dos controles do owner.
- [x] Concluir validação e documentação operacional/HTTP.

## Principais arquivos

- Backend: `OwnerAuthProperties.kt`, `OwnerAccessFilter.kt`, `OwnerAuthController.kt`, `OwnerAuthDtos.kt`, `OwnerAuthService.kt` e `OwnerAuthRepository.kt`, nos diretórios existentes de suas responsabilidades.
- Configuração: `application.yml`, `PropertiesConfig.kt`, `CorsConfig.kt`, `CorsProperties.kt`, `SpaForwardController.kt`, `server/build.gradle.kts`.
- Persistência: `V76__add_owner_auth.sql`.
- Frontend: `useOwnerAuth.ts`, plugin `owner-auth.client.ts`, middleware `owner.global.ts`, páginas `/login` e `/auth/reset`, navegação/layout, CSS compartilhado e controles de escrita dos domínios.
- Validação: `OwnerAuthIntegrationTest.kt`, `OwnerAuthServiceTest.kt` e `CorsPropertiesTest.kt`.
- Documentação: `docs/authentication.md`, `docs/openapi.yaml`, `docs/operations-api.md`, `README.md` e `frontend/README.md`.
