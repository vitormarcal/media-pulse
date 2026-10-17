# Migração do acervo local para o Visual Library

## Problema

Imagens existentes continuam no disco do Media Pulse mesmo com o Visual Library habilitado.

## Objetivo

Centralizar as imagens do arquivo pessoal no Visual Library, migrando o acervo por uma rotina configurável e liberando o armazenamento local após a troca segura dos vínculos.

## Escopo

- Abranger imagens locais vinculadas aos registros: revistas, jogos, obras de arte, retratos de artistas, livros e edições. Esse conjunto deve acompanhar todos os tipos de entidade já aceitos pelo armazenamento no Visual Library.
- Usar `media-pulse.visual-library.migration-enabled`, via `VISUAL_LIBRARY_MIGRATION_ENABLED`, desligada por padrão e independente da flag de novos uploads. Exigir Visual Library configurado quando a migração estiver ligada.
- Iniciar a rotina em segundo plano após a aplicação estar pronta, processando lotes pequenos, sem impedir o uso do produto. Enquanto habilitada, repetir a varredura com intervalo entre execuções para retomar pendências e falhas.
- Enviar bytes existentes com referências reais das entidades e os metadados aplicáveis, preservando vínculos, ordem, imagem principal e classificações de obras.
- Remover arquivos locais somente após confirmação do upload, leitura remota bem-sucedida e commit dos vínculos correspondentes.

## API/UI

Sem nova interface ou endpoint público. A configuração exige reiniciar a aplicação. Registrar progresso e resumo de processados, migrados, falhos e arquivos removidos nos logs, sem expor segredos.

## Non-goals

Ampliar a integração para música, filmes, séries ou treinos; baixar URLs externas já salvas, migrar imagens estáticas da interface, apagar arquivos sem vínculo conhecidos antes da migração, excluir assets remotos ou criar painel de administração.

## Heurística/regras

- Selecionar referências locais do armazenamento configurado; nunca aceitar caminhos fora dessa raiz nem escapar por links simbólicos.
- Upload e leitura de confirmação ocorrem fora da transação de atualização. Antes da troca, conferir se o vínculo ainda aponta para a imagem original; preservar alterações concorrentes.
- Arquivo compartilhado só pode ser removido quando não houver referência local restante em nenhum domínio abrangido.
- Falha de upload, validação, leitura ou banco mantém o arquivo e os vínculos locais; continuar os outros itens e tentar novamente em execução posterior.
- Reexecuções não duplicam vínculos nem reprocessam imagens já migradas; usar deduplicação e referências existentes do Visual Library.
- Prever retomada da remoção caso haja interrupção ou falha depois do commit. A implementação deve registrar explicitamente a pendência de limpeza para não confundi-la com arquivos órfãos anteriores.
- Desabilitar a rotina impede novas execuções; imagens remotas continuam acessíveis com a URL base configurada.
- Não usar Flyway para transferir arquivos ou chamar o serviço remoto. A V71 registra pendências de limpeza e permite retomar interrupções; incluir livros e edições reutiliza esse schema, sem nova migration.

## Acceptance criteria mínimos

- Flag desligada não migra nem remove arquivos; ligada migra o acervo elegível sem bloquear a inicialização.
- Páginas e galerias mantêm imagens, seleção principal, ordem e metadados após a troca. Capas de livros e edições são independentes; cada referência usa o ID real do respectivo registro.
- Bytes remotos são verificados antes da exclusão; referências locais compartilhadas impedem remoção prematura.
- Falhas e interrupções são recuperáveis, inclusive entre commit e remoção, sem perda de arquivos ainda necessários ou sobrescrita de alterações concorrentes.
- Testes proporcionais cobrem configuração, migração, falhas, compartilhamento, concorrência e retomada; documentação operacional descreve configuração, logs e backup.
