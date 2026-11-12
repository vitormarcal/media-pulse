# Enriquecimento de autores pelo Hardcover

Status: aprovado e implementado; validação local concluída.

## Problema

O detalhe do autor não tem retrato, biografia ou identidade externa. O importador consulta apenas author.name e resolve o autor local por fingerprint do nome; essa identidade não permite vincular um perfil externo com segurança. Funções de contribuição existem em book_authors, mas não estão no contrato de apresentação atual.

## Objetivo

Dar contexto e personalidade à página com foto e biografia persistidas, mediante correspondência revisada pelo owner, preservando livros, leituras, créditos, nome e URL locais.

## Escopo

1. Acrescentar vínculo explícito ao ID do autor no Hardcover e snapshot local dos campos aceitos, com origem e data de consulta. Preservar os IDs, fingerprints e associações existentes. Não reidentificar pessoas pelo nome ou fundir autores nesta entrega.
2. Oferecer Atualizar dados no detalhe do autor, apenas para o owner. Entrada por link de perfil Hardcover ou ID; consultar mostra prévia com nome, link de origem e contexto disponível para conferir a pessoa. Aceitar apenas formatos conhecidos de perfil, resolvidos pelo cliente Hardcover, sem buscar URLs arbitrárias.
3. Prévia permite selecionar foto, biografia, nascimento/falecimento, origem e links externos disponíveis. Campos ausentes não apagam dados salvos. Mostrar valores atuais e propostos; confirmar aplica somente a prévia consultada e os campos escolhidos. Alterar a entrada invalida a prévia; erros preservam a entrada e permitem retry.
4. Tornar a troca de vínculo explícita: antes de confirmar, mostrar a identidade atual e a nova. Nunca apresentar conteúdo da identidade antiga como pertencente à nova; ao trocar, substituir/limpar o snapshot anterior de forma atômica. Não trocar nome, membros ou créditos locais ao enriquecer.
5. Persistir o retrato pelo armazenamento de imagens existente, sem depender de URL remota para renderizar a página. Guardar fonte e atribuição disponível; não inventar autoria ou licença nem usar capa de livro como retrato. Falha ao obter imagem não pode comunicar aplicação bem-sucedida da foto: preservar a imagem existente e permitir nova tentativa. Texto pode ser aplicado separadamente.
6. Mostrar retrato integral e ampliável à direita no desktop, usando o visualizador compartilhado; no celular, identificação, retrato e biografia. Sem retrato, não reservar coluna vazia. Biografia expansível junto do nome, com fonte; datas preservam precisão (ano não vira data completa). Links opcionais em Sobre o autor, com protocolos seguros. Não confundir origem fornecida com nacionalidade ou nascimento.
7. Preservar grade regular, apresentação de livro único, ordenação, expansão, séries e histórico existentes. Estado sem enriquecimento continua completo e navegável. Navegação pública lê somente dados locais; nenhuma consulta ao provedor ao abrir o detalhe.

## API/UI

Estender AuthorDetailsResponse com contexto opcional. Adicionar endpoints POST específicos para consulta/prévia e aplicação do enriquecimento, protegidos pelo padrão owner/CSRF existente. Prévia não grava o perfil; aplicação rejeita prévia expirada ou incompatível com o vínculo/snapshot atual. Impedir conflito de vínculo de um mesmo perfil externo entre autores locais e explicar como revisar, sem fusão automática.

Arquivos principais: integração hardcover/HardcoverApiClient.kt e DTOs; novos serviço, repositório e controller específicos de enriquecimento de autor; api/books/AuthorDetailsResponse.kt e BookQueryRepository.kt; componentes AuthorPageHero.vue e novo painel contextual; pages/books/authors/[id].vue, types/books.ts e utils/books.ts. Reutilizar armazenamento de imagens e MediaImageGallery, preservando fluxos de livros e artistas musicais.

Migração: sim, Flyway posterior à última versão existente, para identidade externa e snapshot persistido. Não alterar a deduplicação do importador atual nesta entrega: o vínculo aceito é explícito, e sincronizações de livros não devem sobrescrevê-lo. Sem nova dependência ou framework. A implementação deve verificar o contrato real dos campos do Hardcover com consultas de leitura antes de consolidar os DTOs.

Direção visual: seções 17, 20 e 22 do DESIGN.md, mantendo superfícies brancas, fundo quente, largura de 1120px e manutenção junto do botão que a revela. Retrato não altera a composição dos cartões de livros.

## Fora do escopo

Enriquecimento de livros e catálogo externo de séries; importação em massa de biografias; novas fontes/Wikipedia; tradução automática; bibliografia completa; fusão/separação de autores; alteração global da identidade por nome; classificação retroativa de créditos; pseudônimos como novas entidades; múltiplas fotos/upload manual. Outros nomes podem auxiliar a conferência na prévia, sem criar vínculos locais.

## Regras

A documentação do Hardcover oferece os campos de autor, mas o preenchimento varia. Respostas ausentes ou vazias não são instruções de exclusão. Biografia é texto do provedor, sem HTML remoto executável; não resumir ou reescrever como informação verificada. Seleção de dados não autoriza substituir memórias pessoais nem consultar fontes secundárias. Sem identidade inequívoca, manter o perfil local sem enriquecimento.

## Critérios de aceite

- Prévia por ID e link válidos, entrada inválida, perfil ausente e falha do provedor; sem gravação antes da confirmação e sem perda de entrada em erro.
- Seleção parcial preserva campos não escolhidos; dados ausentes não apagam existentes. Troca de identidade, vínculo conflitante, prévia expirada/alterada e aplicação concorrente não misturam perfis.
- Persistência de texto e imagem com origem; falha de imagem identificada e recuperável, sem dependência remota na leitura. Nascimento/falecimento com ano ou data, origem ausente e links inválidos tratados.
- Autores 19 e 28 em desktop/celular, com/sem retrato e biografia, texto longo, galeria/teclado/Escape/foco; owner/visitante e navegação entre autores.
- Testes proporcionais de serviço, persistência/migração, contratos e autorização; ktlintFormat, testes backend, checks/build frontend e regressão da importação e dos detalhes existentes.


## Implementação e validação

V80 cria book_author_profiles; o importador por fingerprint e todos os vínculos existentes permanecem intactos. Retrato usa o armazenamento validado existente, com referência book_author no Visual Library e fallback local. Biografia e demais dados vêm do próprio Hardcover, sem Wikipedia ou traduções. A API continua acessível sem dados enriquecidos.

Backend: 41 testes passaram, sem skips, incluindo serviço, controle de acesso/CSRF, persistência em PostgreSQL Testcontainers aplicando V1–V80, decodificação de contratos Hardcover e armazenamento de imagem remoto/local. Casos incluem seleção parcial, ausência de dados, troca explícita de identidade, snapshot alterado, vínculo conflitante, falha de imagem sem aplicação parcial e retry sem foto. Testes usam respostas simuladas conforme o esquema documentado. Uma consulta autenticada de leitura ao Hardcover confirmou o contrato no perfil Akira Toriyama (201201): foto em assets.hardcover.app, datas completas, origem e links em lista; biografia vazia. Nenhum perfil local ou dado do provedor foi gravado.

Chromium em 1440px e 390px, owner/visitante: consulta com erro/retry, seleção, falha de aplicação com entrada preservada, atualização reativa do retrato e texto, fonte, ano sem data inventada, galeria por teclado/Escape/retorno do foco, biografia longa, invalidação da prévia ao alterar entrada, troca de identidade e perfil ausente. Regressões de autores 19/28, livro, revista, séries/universos. Frontend com lint/formatação/tipos/build; backend com ktlintFormat, testes e bootJar.

Publicação solicitada na versão 1.26.0, com tag v1.26.0 e imagens 1.26.0/latest. Implantação no Mordor pelo serviço Compose existente, incluindo migração V80 e verificação de leitura pública sem enriquecimento prévio.

## Evolução aprovada: importação automática

A restrição inicial de enriquecimento exclusivamente manual foi ampliada pela solicitação posterior do owner. Ver [enriquecimento na importação](book-author-import-enrichment.md): IDs de contribuições como evidência, perfis manuais protegidos, preenchimento de lacunas e tratamento conservador de ambiguidades. A revisão manual continua disponível para exceções.
