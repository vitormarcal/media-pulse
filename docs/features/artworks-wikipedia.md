# Artes visuais: Wikipedia e revisão das telas

Status: integração adiada a pedido do owner. A revisão de UX/UI segue separadamente, sem endpoints, importação ou referências externas novas.

## Problema

O cadastro exige copiar dados de obras e artistas da Wikipedia. Tipo, técnica, movimento e nacionalidade geram dúvidas. Nos detalhes, textos longos e controles de edição competem com a obra e com o histórico pessoal.

## Objetivo

Preencher dados a partir de um link da Wikipedia, permitir revisão antes de salvar e tornar cadastro e detalhes mais claros, seguindo `DESIGN.md`, com pouco texto de interface.

## Escopo

- Entrada pelo link da Wikipedia no cadastro e na edição de obras e artistas.
- Prévia editável com os dados disponíveis da obra: título atual, artista, ano, tipo, técnica, movimento e descrição. Artista com nome, biografia, país de origem e datas disponíveis.
- Sugerir o artista relacionado à obra quando houver identificação confiável; permitir reutilizar um artista local ou importar seus dados. Permitir informar o link do artista separadamente quando necessário.
- Sugerir imagens da obra e retrato do artista para seleção explícita, reaproveitando Wikimedia e armazenamento local. Preservar múltiplas imagens e a escolha da principal.
- Guardar referências de origem dos dados e das imagens, inclusive identificadores externos disponíveis, página/revisão consultada e autoria/licença quando fornecidas.
- Substituir Nacionalidade por País de origem, com nomes de países, como França. Preservar os valores anteriores para revisão durante a migração.
- Tipo com seletor: Pintura, Escultura, Fotografia artística, Desenho, Gravura e Outra; permitir especificar outro tipo e preservar tipos existentes.
- Técnica e movimento com exemplos curtos nos campos. Descrição e biografia opcionais; rótulos diretos e nenhuma prosa promocional.
- Revisar cadastro, edição e detalhes de obras e artistas: hierarquia, espaçamento, leitura em celular, textos longos expansíveis e controles de gerenciamento sob demanda.

## API/UI

A ação Wikipedia recebe um link, consulta os provedores e devolve uma prévia; essa consulta não grava no catálogo. A revisão usa o formulário existente. A confirmação salva somente os dados e imagens escolhidos, inclusive no enriquecimento de registros existentes.

Usar Wikipedia para texto de introdução, Wikidata para campos estruturados disponíveis e Commons para imagens. Textos importados permanecem editáveis e têm fonte acessível. Descrição/biografia não são geradas por IA. APIs de leitura continuam usando somente dados locais.

O detalhe prioriza imagem e ficha compacta, avaliação e experiências pessoais. Descrição/biografia têm expansão explícita. Adicionar imagens, gerenciar listas e editar registros não exigem formulários permanentemente abertos. Preservar a composição integral das imagens e os tokens, tipografia, grid e estados acessíveis de `DESIGN.md`.

Principais áreas: `integration/wikimedia`, DTOs/controladores/serviços/repositórios de `artwork`, componentes e páginas de obras/artistas, tipos e estilos de artes visuais, testes e documentação de domínio/OpenAPI. Migration Flyway necessária para país de origem e referências externas, preservando os registros existentes. Não adicionar dependências sem necessidade demonstrada.

## Non-goals

- Busca por nome, importação de catálogo completo ou sincronização periódica.
- Pesquisa irrestrita no Commons; começar pelas imagens associadas às páginas consultadas.
- Título original, tradução automática ou mudança na política de títulos.
- Alteração dos estados de experiência ou integração transversal entre domínios.
- Descrições, biografias ou textos decorativos gerados por IA.

## Heurística/regras

- Aceitar links de artigos da Wikipedia; resolver a página e seus vínculos pelas APIs oficiais, sem buscar URLs arbitrárias.
- Usar o idioma do link para o texto; campos ausentes continuam vazios. Não deduzir datas, país, técnica ou movimento de um nome ou de uma descrição.
- Mostrar a origem dos candidatos a país de origem; ambiguidades e valores antigos dependem de revisão, sem assumir que cidadania ou local de nascimento são equivalentes.
- Importação não cria experiências nem altera data de descoberta, impressões, favorito, avaliação ou listas.
- Enriquecer um registro existente exige seleção explícita dos campos que serão substituídos; preservar os demais.
- Preferir identidade externa estável para reconhecer obras/artistas já vinculados; nomes iguais não autorizam união automática.
- Imagens só entram após seleção. Falhas preservam o registro e as imagens já salvas, com nova tentativa apenas das pendências.
- Preservar a fonte dos metadados separadamente da origem de uma experiência: Wikipedia pode fornecer a ficha, enquanto uma revista continua sendo a descoberta.

## Acceptance criteria mínimos

- Colar um link de obra ou artista e revisar os dados disponíveis antes de salvar.
- Cadastrar uma obra com artista existente ou com dados importados, sem exigir campos ausentes.
- Enriquecer uma obra/artista cadastrado preservando dados pessoais e campos não selecionados.
- Selecionar imagens e consultar dados e arquivos locais após recarregar, sem consultar Wikipedia durante a leitura.
- Consultar a fonte do texto e a origem/atribuição das imagens importadas.
- Manter cadastro manual funcionando quando Wikipedia estiver indisponível ou o artigo não tiver dados estruturados.
- Preservar registros anteriores ao migrar País de origem e continuar editando tipos personalizados.
- Exibir obras e artistas com imagem em destaque, textos expansíveis, gerenciamento sob demanda, rótulos curtos e navegação por teclado/celular.

Referências técnicas: [TextExtracts](https://www.mediawiki.org/wiki/Extension:TextExtracts) e [Imageinfo](https://www.mediawiki.org/wiki/API:Imageinfo), além da integração local documentada em [Foto de artista via Wikimedia](artist-wikimedia-image.md).
