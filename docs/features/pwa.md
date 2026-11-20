# PWA no Android

## Problema e objetivo
Acessar o Media Pulse pela tela inicial do Android, em janela própria, reutilizando o frontend e backend existentes.

## Escopo aprovado
Manifesto com identidade, ícones PNG de 192 e 512 pixels com área segura para máscaras do Android, abertura standalone e página independente de falta de conexão com tentativa de recarregar a URL original.

Sem APIs novas, migração, notificações, acervo offline ou sincronização. Consultas e alterações continuam dependendo do servidor.

## Regras
O plugin registra o service worker somente no build de produção e em contexto seguro. O worker armazena apenas /offline.html, tenta navegações pela rede e usa essa página quando a requisição falha. Não intercepta APIs, imagens, scripts, estilos ou escritas, nem armazena respostas de autenticação. Erros HTTP do servidor continuam visíveis.

A página offline atende abertura/recarregamento após uma visita online que tenha instalado o worker. Navegação interna já aberta continua usando os estados de erro existentes. Não há redirecionamento automático, para preservar formulários e rascunhos.

Atualizações do worker seguem o ciclo normal do navegador, sem forçar recarregamento de abas abertas. Ao alterar a página offline, incrementar a versão do cache em sw.js. Remover somente caches com o prefixo media-pulse-offline-.

## Aceite e validação manual
1. Servir o build por HTTPS acessível no Android. HTTP por IP da rede local não atende ao requisito; localhost é uma exceção para desenvolvimento no próprio dispositivo.
2. No Chrome, abrir a instalação e usar o menu para instalar/adicionar à tela inicial. Confirmar nome e ícone.
3. Abrir pelo ícone e confirmar janela própria e navegação.
4. Depois de uma visita online e ativação do worker, ficar offline e recarregar uma página: deve aparecer Sem conexão com o Media Pulse.
5. Reconectar e tocar Tentar novamente: deve voltar à mesma URL.
6. Em DevTools, Application, confirmar manifesto, worker e cache contendo apenas offline.html. APIs e escritas devem continuar usando rede.
7. Validar tanto hospedagem combinada Spring quanto frontend standalone Nginx.

## Arquivos principais
frontend/nuxt.config.ts, frontend/app/plugins/pwa.client.ts, frontend/public/manifest.webmanifest, frontend/public/sw.js, frontend/public/offline.html, frontend/public/pwa/, frontend/nginx.conf e StaticResourcesConfig.kt.

## Referências
- [Manifesto e instalação](https://web.dev/articles/install-criteria)
- [Atualização do service worker](https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerRegistration/updateViaCache)
