# Identidade do autor durante a importação do livro

Status: implementação solicitada pelo owner; implementado e validado localmente.

## Problema e objetivo

A importação já guardava evidências de ID e enriquecia perfis seguros, mas ainda resolvia os créditos pelo nome normalizado. Usar o ID do Hardcover no processamento de cada contribuição, preservando cadastros locais quando houver correspondência comprovada e distinguindo pessoas com o mesmo nome.

## Escopo e regras

1. V82 acrescenta authors.hardcover_import_id, positivo, opcional e único. Promove apenas evidências positivas únicas por autor e por ID externo, sem ausências/conflitos ou perfil manual divergente. Não muda IDs, nomes, fingerprints, livros, funções de contribuição ou leituras existentes.
2. Com ID externo válido, resolver primeiro por essa identidade, independentemente do nome recebido. Uma mudança de nome no provedor não recria nem renomeia automaticamente o autor local.
3. Um perfil manual explícito com o mesmo ID pode identificar um autor ainda sem identidade de importação, desde que não haja evidências conflitantes. Revalidar sob lock antes de associar. Perfil enriquecido e identidade de importação têm funções diferentes: alterar foto/biografia/vínculo de contexto não reatribui livros nem troca a identidade de importação já estabelecida.
4. Para novas identidades, criar um autor próprio. Usar o fingerprint de nome quando livre e um fingerprint de identidade do provedor se estiver ocupado. Não anexar um ID novo a um cadastro legado só porque o nome coincide. Casos antigos sem evidência permanecem preservados para revisão, sem fusão/separação ou remoção de créditos automática.
5. Com dois contribuidores de nome igual e IDs distintos na mesma edição, conservar ambos e suas funções. Não reduzir as contribuições a um mapa indexado apenas pelo nome.
6. Payload sem ID ou com ID inválido mantém o caminho legado por nome, sem inventar vínculo externo. A coleta de evidências usa a identidade já resolvida quando há ID positivo; não atribui outro ID a um homônimo pela grafia. Payload sem ID não invalida um vínculo de importação estabelecido.
7. Resolver IDs sob transação, com unicidade no banco e lock por identidade externa. Imports concorrentes do mesmo ID não podem criar duplicatas. Ordenar a resolução dos contribuidores por ID para reduzir inversões de lock.
8. Manter o enriquecimento de foto/contexto ao final da passagem completa, com proteção manual, intervalo entre tentativas e falhas independentes dos livros. Esta entrega muda a identificação no livro, não acrescenta fontes externas nem antecipa consultas de foto para cada contribuição.

## API/UI e arquivos principais

Sem novos endpoints ou mudança de interface. Alterações em Author, HardcoverUserBookProcessor e HardcoverAuthorCandidateRepository; novo HardcoverAuthorIdentityRepository; migração V82. Reutiliza os DTOs de contribuição com ID e o enriquecimento existente. Sem dependências novas.

## Fora do escopo

Wikipedia/Wikidata, busca por semelhança de nomes, fusão/separação retroativa de autores com vínculos incertos, renomeação automática local e reatribuição de créditos históricos sem prova de identidade.

## Critérios de aceite e validação

- Migração V1–V82 em PostgreSQL: vínculo seguro preserva o ID/nome e crédito local; evidência ausente, ambígua, compartilhada ou manual divergente não é promovida.
- Mesmo ID com nome diferente resolve o mesmo cadastro; homônimos com IDs distintos resolvem cadastros distintos; confirmação manual inequívoca pode recuperar autor legado.
- Concorrência com o mesmo ID e nomes diferentes produz um único autor; IDs diferentes com nome igual permanecem separados.
- Processador preserva funções e pessoas de uma edição com nomes iguais; payload legado continua importável.
- 43 testes backend passaram sem skips, incluindo regressões do enriquecimento manual/automático, armazenamento e importação. ktlintFormat, compilação e bootJar passaram. Não há alteração de frontend.

Publicação solicitada na versão 1.28.0, tag v1.28.0 e imagens 1.28.0/latest. Implantação no Mordor com verificação da V82, das identidades promovidas e dos totais existentes de autores, livros e leituras.
