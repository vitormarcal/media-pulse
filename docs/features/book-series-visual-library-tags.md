# Tags de séries nas capas do Visual Library

## Problema

As séries locais não aparecem nas tags das capas de livros e edições no Visual Library, inclusive quando o vínculo é criado depois do envio da imagem.

## Objetivo

Permitir encontrar capas pelas séries dos livros, mantendo as tags coerentes com a organização local e preservando a curadoria pessoal do Visual Library.

## Escopo

- Enviar os nomes das séries de cada livro como tags nas capas canônicas e nas capas de suas edições.
- Incluir novos uploads, capas migradas e capas já armazenadas no Visual Library.
- Atualizar automaticamente após associação, remoção, exclusão ou renomeação de série, inclusive nome aplicado a partir do Hardcover.
- Registrar falhas e permitir nova tentativa sem desfazer a alteração local.

## API/UI

Usar o contrato existente de tags gerenciadas por referência, com grupo `book-series`, revisão monotônica e referências `book`/`book_edition`. A implementação local do Visual Library aceita grupos nomeados; validar o contrato com os dois serviços antes de finalizar. Não introduzir uma integração paralela nem alterar o Visual Library sem necessidade comprovada.

Migration Flyway posterior à V73 registrará revisão e pendência de sincronização por livro, incluindo o acervo existente. Processamento em lotes retomáveis atualizará referências remotas já salvas, sem reenviar arquivos. Novos uploads e migrações usarão os mesmos nomes e revisões.

O detalhe do livro apresentará pendência/erro e ação de nova tentativa seguindo os padrões existentes de sincronização de movimentos de obras e DESIGN.md. O contrato de livros terá estado de sincronização e um endpoint explícito de retry; nenhuma escolha adicional será exigida no fluxo normal de associação de séries.

## Non-goals

- Tags de universos, autores ou outros metadados nesta entrega.
- Migrar ou baixar capas locais/externas apenas para acrescentar tags.
- Alterar a ordem dos livros, importar séries do Hardcover automaticamente ou enviar imagens sem vínculo no acervo.

## Heurística/regras

- A lista de tags corresponde a todas as séries locais do livro; edições herdam essa mesma lista.
- Normalizar espaços e deduplicar sem diferenciar caixa. Usar o nome da série sem prefixos ou abreviações.
- Não truncar nomes para caber nos limites. Nomes acima de 48 caracteres UTF-16 ou conflitos com o limite de oito tags da imagem geram pendência com motivo explícito.
- Gerenciar somente as tags desta referência e deste grupo. Preservar tags pessoais, antigas sem procedência e tags necessárias a outras referências de um asset compartilhado.
- Remover um vínculo retira somente a contribuição correspondente do Media Pulse; a mesma tag pode continuar visível por curadoria pessoal ou outra referência.
- Sincronizar após commit local, com a revisão mais recente. Respostas antigas não podem limpar uma pendência nova nem sobrescrever uma revisão posterior.
- Falha ou indisponibilidade remota preserva séries, vínculos e capas locais e permite retomada.
- Processar somente referências remotas reconhecidas pelo Media Pulse; nenhuma consulta ao Hardcover é necessária.

## Acceptance criteria mínimos

- Associar um livro a duas séries faz as tags aparecerem em sua capa e nas capas das edições no Visual Library.
- Renomear, remover ou excluir uma série atualiza as contribuições correspondentes sem perder tags pessoais ou compartilhadas.
- Atualizar capas remotas já existentes e novos envios, sem duplicar assets ou reenviar arquivos no backfill.
- Manter os dados locais em falhas e conflitos de limite, mostrar o motivo e concluir após nova tentativa ou retomada.
- Validar concorrência de revisões, múltiplas edições, assets compartilhados, mudanças pelo Hardcover e preservação do acervo existente.
