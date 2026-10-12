# Artes visuais: obras, artistas e experiências

## Problema

O arquivo cultural pessoal não permite preservar descobertas de pinturas, esculturas, fotografias artísticas e outras obras visuais. Essas obras não têm progresso ou conclusão comparáveis a leituras e sessões audiovisuais; uma mesma obra pode ser conhecida digitalmente e contemplada presencialmente em outro momento.

## Objetivo

Registrar localmente obras conhecidas ou em descoberta, seus artistas e as experiências pessoais associadas, com imagens e organização por listas. Oferecer captura rica em informações, sem exigir dados que o owner não possui.

## Escopo

- Domínio próprio de artes visuais, separado dos modelos de livros, audiovisual e música.
- Cadastro manual de obra com título obrigatório; artista, ano, tipo, movimento, técnica e descrição opcionais. Tipos incluem pintura, escultura, fotografia artística e outras manifestações visuais.
- Artistas locais reutilizáveis, com nome obrigatório e informações opcionais, como biografia, país de origem, datas de nascimento/falecimento e retrato. Página própria com seus dados e somente as obras cadastradas no arquivo.
- Cadastro da obra e da primeira experiência no mesmo fluxo. Oferecer data, contato digital ou presencial, impressões e origem da descoberta, com nome/local e link opcionais. Permitir salvar com informações parciais, sem exigir uma experiência completa.
- Novas experiências independentes para contemplações posteriores, preservando o histórico da mesma obra.
- Avaliação opcional e favorito vinculados à obra, independentes das experiências.
- Galeria com múltiplas imagens por obra, adicionadas por upload ou URL, com escolha da imagem principal e remoção de imagens. Preservar proporções originais.
- Listas pessoais de obras com nome e descrição opcional. Criar, editar e excluir listas; adicionar e remover obras. Uma obra pode participar de várias listas.
- Biblioteca visual com busca e filtros por artista, tipo, movimento, favorito e lista; detalhe da obra, páginas de artistas e páginas de listas.
- Edição e exclusão para correção dos registros, sem apagar uma obra ao remover uma experiência, imagem ou vínculo com lista.

## API/UI

Contratos próprios sob `/api/artworks` para obras, artistas, experiências, imagens e listas. Reaproveitar avaliação e infraestrutura de imagens existentes quando compatíveis, sem criar uma plataforma genérica de objetos culturais. Os metadados, relacionamentos, experiências e arquivos de imagem devem permanecer locais; uma imagem adicionada por URL deve ser incorporada ao armazenamento existente, preservando a URL de origem quando disponível.

A navegação terá a entrada Artes visuais, com biblioteca em `/artworks`, detalhe de obra em `/artworks/{id}`, artistas em `/artworks/artists/{id}` e listas em `/artworks/lists/{id}`. O cadastro permite selecionar um artista existente ou criar um sem sair do fluxo. Metadados, imagens e primeira experiência ficam acessíveis no mesmo fluxo, com campos opcionais agrupados para evitar um formulário extenso obrigatório.

O detalhe mostra imagem principal, galeria, metadados, artista, avaliação, favorito, listas e histórico de experiências. A página do artista reúne informações e obras locais; não pressupõe conhecer seu catálogo completo. As listas oferecem recortes pessoais como Impressionistas e Abstratas, sem exigir classificação formal.

Seguir `DESIGN.md`: imagens como conteúdo principal, galeria responsiva, neutros quentes, tokens e tipografia existentes, cantos arredondados e pouca sombra. Exibir imagens integralmente na biblioteca e no detalhe, sem recortes automáticos. Preservar padrões de navegação, formulários e estados de carregamento, erro e vazio; garantir teclado, foco visível e manutenção dos dados preenchidos em caso de falha.

A implementação exigirá migration Flyway para obras, artistas, experiências, imagens e listas/vínculos, além da integração com avaliações quando necessária. Principais áreas afetadas: `server/src/main/kotlin/dev/marcal/mediapulse/server/{api,controller,model,repository,service}/artwork`, migrations, testes proporcionais, `frontend/app/pages/artworks`, componentes e contratos de domínio, navegação e encaminhamento SPA. Atualizar `docs/artworks-api.md`, `docs/openapi.yaml`, `README.md`, `frontend/README.md`, `DESIGN.md` e o roadmap de `PRODUCT_CONSTITUTION.md` junto da implementação. Contratos implementados e detalhes de operação: [Artes visuais API](../artworks-api.md).

## Non-goals

- Busca, importação ou enriquecimento externo nesta entrega.
- Busca futura no Wikimedia poderá sugerir imagens para seleção explícita antes de incorporá-las à galeria; não será automática nem limitada a uma imagem.
- Catálogo completo de obras de um artista ou criação automática de obras não conhecidas.
- Estados Quero conhecer, Lendo, Assistindo, Concluída ou fluxo de progresso.
- Descobertas transversal, consultas culturais entre domínios ou reorganização global em Leituras, Audiovisual, Música e Artes visuais.
- Listas entre domínios, regras automáticas de inclusão ou sistema separado de coleções com comportamento equivalente às listas pessoais.
- Plataforma genérica de entidades culturais ou novas dependências sem necessidade demonstrada.

## Heurística/regras

- Cadastrar uma obra já representa conhecê-la ou estar conhecendo-a. A data de cadastro é técnica e não deve ser apresentada como data de contemplação.
- Obra e experiência têm identidades distintas. Múltiplas experiências não duplicam a obra na biblioteca nem substituem registros anteriores.
- Apresentar a primeira experiência no cadastro, mas criar o registro somente se houver informações da experiência. Permitir data, tipo, origem ou impressões desconhecidos; não inventar datas nem inferir contato digital de um link.
- Experiências usam `DIGITAL_VIEWING` ou `IN_PERSON` quando o tipo é informado. Nome/local e link da origem complementam o registro, sem exigir entidade própria de museu, exposição ou site.
- Avaliação segue a escala existente no produto. Favorito e avaliação pertencem à obra; impressões pertencem à experiência.
- Movimento artístico é metadado opcional, independente das listas. Uma lista chamada Impressionistas não altera automaticamente o movimento das obras incluídas.
- Reutilizar artistas escolhidos no cadastro; autoria desconhecida não impede salvar. Não unir automaticamente artistas ou obras apenas por nomes iguais.
- Não exigir imagem nem limitar a obra a uma imagem. Sem imagens, usar fallback consistente. A primeira imagem pode ser a principal por padrão; permitir trocar e, ao removê-la, usar outra disponível ou fallback.
- Validar arquivos e downloads com os mecanismos e limites existentes. Falha ao adicionar uma imagem não deve apagar a obra nem imagens previamente salvas.
- Remover uma lista exclui apenas a lista e seus vínculos. Remover um artista vinculado exige desvinculação explícita e preserva as obras. Excluir uma obra exige confirmação e remove seus registros dependentes, preservando artistas e listas.

## Acceptance criteria mínimos

- Cadastrar uma obra com dados parciais e completar seus metadados posteriormente, sem obrigar ano, artista, imagem ou experiência completa.
- Cadastrar obra e primeira experiência juntas, preservando as informações fornecidas de data, tipo, impressões e origem.
- Registrar contemplação digital e visita presencial da mesma obra, editar ou excluir experiências e preservar as restantes.
- Criar e reutilizar artistas; abrir sua página e consultar os dados e as obras locais vinculadas.
- Avaliar e favoritar uma obra, mantendo essas escolhas independentes das experiências.
- Adicionar mais de uma imagem por arquivo e URL, escolher a principal e remover imagens sem recortar a composição na galeria.
- Criar listas como Impressionistas e Abstratas, incluir a mesma obra em ambas e remover vínculos sem excluir a obra.
- Buscar obras e filtrar a biblioteca pelos recortes definidos, sem duplicar cards por experiência ou participação em listas.
- Consultar todos os registros após recarregar a página, usando dados e imagens locais.
- Navegar e registrar informações em celular e por teclado, com feedback de gravação e recuperação de erros consistentes com o produto.

### Revisão de UX/UI

Cadastro mantém título, artista e ano e torna tipo, movimento e técnica visíveis. Tipo oferece categorias comuns e Outra com texto próprio; exemplos curtos orientam movimento e técnica. País de origem usa nomes de países, com preservação dos valores anteriores na migration V66 para revisão manual.

A revisão dos detalhes segue [Detalhes de artista e obra](artworks-detail-layout.md): galeria ampla com navegação e ampliação na página, ficha compacta e leitura confortável. Textos curtos aparecem completos; descrições e biografias longas começam com um trecho expansível. Artistas reúnem retrato opcional, dados e biografia, com obras locais agrupadas por ano de criação. Controles de imagens e listas aparecem por ações de gerenciamento. Tipos personalizados, dados preenchidos, múltiplas imagens, correções e histórico permanecem disponíveis. Integração com Wikipedia foi adiada; o cadastro continua manual.

## Armazenamento opcional de imagens

A centralização de novas imagens no Visual Library estende o armazenamento local descrito neste MVP. Consulte o [escopo aprovado](visual-library-images.md) e a [operação](../visual-library-images.md). Os dados do domínio permanecem no Media Pulse; imagens locais existentes continuam disponíveis.

## Movimentos e estilos múltiplos

[Spec aprovada](artwork-movements.md): lista com autocomplete aberto, sugestões iniciais + termos do acervo, filtro por pertencimento e correção automática das imagens remotas após salvar. A migration V67 preserva o texto legado como um item; listas pessoais permanecem independentes. Falhas de sincronização preservam dados locais e permitem retry.
