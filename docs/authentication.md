# Autenticação do proprietário

O acervo continua público para consulta. Alterações exigem sessão do único owner; todos os endpoints `/api/admin/**`, inclusive leituras operacionais, e `/oauth/spotify/login` também exigem sessão. Não existe cadastro público ou nome de usuário.

## Primeiro acesso e recuperação

1. Gere um UUID aleatório e configure `AUTH_RESET_TOKEN` no ambiente do backend.
2. Inicie/reinicie a aplicação com essa configuração; a migration `V76__add_owner_auth.sql` cria a credencial única e o histórico de autorizações consumidas.
3. Abra `/auth/reset` e informe o mesmo UUID e a nova senha, com pelo menos 12 caracteres e no máximo 72 bytes em UTF-8.
4. Depois de salvar, abra `/login` e entre somente com a senha.

A senha é armazenada como BCrypt com custo 12. O UUID é armazenado somente como SHA-256 após seu consumo. Salvar a credencial e consumir a autorização ocorre na mesma transação com bloqueio da credencial única. Alterar a caixa das letras do UUID não cria outra autorização.

O env não é removido do processo: seu valor deixa de autorizar resets depois de consumido. Reiniciar, remover/recolocar a variável ou voltar a um UUID antigo não libera outro reset. Para recuperar acesso, configure um UUID novo e reinicie. Sem `AUTH_RESET_TOKEN`, a criação/reset fica indisponível e o login já existente continua funcionando. Antes da primeira senha, as operações protegidas ficam bloqueadas.

Reset não autentica automaticamente e invalida todas as sessões anteriores pela versão da credencial, inclusive em outras instâncias conectadas ao mesmo banco. Login troca o identificador da sessão; logout a invalida. Sessões ficam em memória e terminam após reinício ou 12 horas de inatividade. A instalação atual não requer armazenamento compartilhado de sessões.

Login e reset têm limites separados de dez tentativas por minuto por endereço de cliente, incluindo tentativas bem-sucedidas. Endereços compartilhados por NAT dividem o limite. O controle mantém até 10.000 janelas em memória; ao atingir essa capacidade, descarta a mais antiga. Excesso retorna `429`; aguarde um minuto. Esse limite é local à instância e reinicia com o processo.

## Configuração

| Variável | Default | Uso |
| --- | --- | --- |
| `AUTH_RESET_TOKEN` | vazio | UUID de autorização de criação/reset; nunca enviar ao frontend como configuração pública. |
| `AUTH_COOKIE_SECURE` | `true` | Cookie de sessão somente em HTTPS; use `false` exclusivamente para desenvolvimento via HTTP. |
| `PLEX_WEBHOOK_SECRET` | vazio | Segredo independente para o webhook; vazio bloqueia o webhook. |

Cookie de sessão usa HttpOnly e SameSite=Lax. Produção deve servir UI e API no mesmo domínio via HTTPS. No desenvolvimento em outra origem, configure `NUXT_PUBLIC_API_BASE`, as origens explícitas em `media-pulse.allowed-origin` e `AUTH_COOKIE_SECURE=false` se estiver usando HTTP. Use o mesmo hostname nos dois lados (por exemplo, `localhost` em ambos). CORS permite credenciais nas origens configuradas.

## Contratos HTTP

| Endpoint | Comportamento |
| --- | --- |
| `GET /api/auth/session` | Retorna `{ authenticated, csrfToken }`; cria sessão anônima se necessário. Resposta sem cache. |
| `POST /api/auth/login` | Body `{ password }`; autentica na sessão atual e troca seu ID. |
| `POST /api/auth/reset` | Body `{ token, password }`; consome o UUID, salva senha e invalida a sessão atual. |
| `POST /api/auth/logout` | Invalida a sessão atual. |

Os três POSTs de autenticação exigem cookie da sessão e `X-CSRF-Token` obtidos em `/api/auth/session`. Mutações autenticadas também exigem esse header. Após logout/reset, obtenha nova sessão antes de enviar outro POST. Token CSRF ausente/incorreto retorna `403`; ausência de sessão owner válida em operação protegida retorna `401`. Senha ou autorização inválida/consumida retorna `401`, senha fora dos limites retorna `400` e excesso de tentativas retorna `429`. Sucesso nos POSTs retorna `200` sem corpo.

O frontend centraliza credenciais/CSRF nas chamadas à API da instalação e oculta ações de escrita com a diretiva `v-owner`. Essa diretiva é apresentação; o filtro do backend é responsável por bloquear chamadas diretas. Leituras e navegação públicas continuam disponíveis. A sessão do navegador não é necessária para schedulers, workers ou clientes internos do backend.

## Exceções de integração e exploração

- **Plex:** configure uma URL de webhook no formato `https://seu-dominio/webhook/plex?secret=SEGREDO_DO_WEBHOOK`, usando exatamente o valor de `PLEX_WEBHOOK_SECRET`. O segredo é independente de `AUTH_RESET_TOKEN` e da senha, não exige sessão/CSRF e nunca deve ser versionado. Ausente/incorreto retorna `401`. Ao trocar o segredo, atualize ambos os lados e reinicie o backend. Configure o proxy para omitir a query desse endpoint dos logs de acesso.
- **Spotify:** iniciar OAuth exige sessão owner. O callback continua acessível ao provedor e valida o `state` gerado pelo fluxo já existente; não libera acesso administrativo. Polling e importações automáticas continuam independentes da sessão.
- **Filmografia:** `POST /api/people/{personId}/filmography/ensure` e `POST /api/people/{personId}/show-filmography/ensure` permanecem públicos, sem sessão/CSRF, para restaurar recortes ausentes/compactados. Mantêm coordenação, reutilização e espera após falhas existentes. Não criam obras ou créditos no catálogo. Reparo forçado, adição de obras e vínculos exigem sessão owner.

O histórico de UUIDs consumidos faz parte do backup do banco. Restaurar um backup também restaura a credencial e o histórico daquele momento; remova ou troque o UUID configurado ao recuperar um backup antigo.

## CORS: decisão de integração

Na validação com sessão, as APIs retornaram `500` porque a configuração anterior via `@Value` não vinculava a lista YAML e caía no default `*`. `allowCredentials=true` não aceita esse wildcard. A configuração agora usa `CorsProperties` com binding tipado e rejeita `*`. O filtro CORS roda antes da autorização para incluir os headers também em respostas `401`/`403`. Validar com o teste de binding e uma chamada a `/api/auth/session` com `Origin` permitido: deve retornar a origem explícita e `Access-Control-Allow-Credentials: true`.

## Validação de desenvolvimento

Executar `./server/gradlew -p server ktlintFormat` e os testes de autenticação/CORS, além de `npm --prefix frontend run check` e `npm --prefix frontend run build`. Os testes de banco usam PostgreSQL descartável via Testcontainers; conferir o relatório XML para garantir que não foram pulados.

Neste ambiente, o Docker recusou a API antiga usada pelo cliente de testes, fazendo o Testcontainers considerar Docker indisponível. Executar os testes com `JAVA_TOOL_OPTIONS=-Dapi.version=1.44` resolveu a detecção sem alterar dependências ou a configuração do serviço. Validar pelo relatório `OwnerAuthIntegrationTest`: dois testes executados e zero skips. Esse override é somente para o processo de teste quando houver essa incompatibilidade.

### Proxy HTTPS e sessão expirada

Um proxy que termina TLS encaminha HTTP ao backend. CORS precisa rodar depois do filtro de forwarded headers e antes da autorização; a ordem anterior causava `403 Invalid CORS request` mesmo para a origem pública correta. O proxy deve substituir os headers encaminhados por valores confiáveis, incluindo protocolo, host e endereço do cliente. Valide um POST com Origin HTTPS e X-Forwarded-Proto/Host/Port correspondentes.

O frontend renova a sessão/CSRF antes de login e reset, e descarta o CSRF local após respostas 401/403. Isso evita exigir duas tentativas depois que a sessão expira ou o backend reinicia. A tela de reset distingue 400 (senha), 401 (autorização), 403 (sessão/origem), 429 (limite) e falhas de conexão/servidor.
