# Design System: Pinterest

## 1. Visual Theme & Atmosphere

Pinterest's website is a warm, inspiration-driven canvas that treats visual discovery like a lifestyle magazine. The design operates on a soft, slightly warm white background with Pinterest Red (`#e60023`) as the singular, bold brand accent. Unlike the cool blues of most tech platforms, Pinterest's neutral scale has a distinctly warm undertone — grays lean toward olive/sand (`#91918c`, `#62625b`, `#e5e5e0`) rather than cool steel, creating a cozy, craft-like atmosphere that invites browsing.

The typography uses Pin Sans — a custom proprietary font with a broad fallback stack including Japanese fonts, reflecting Pinterest's global reach. At display scale (70px, weight 600), Pin Sans creates large, inviting headlines. At smaller sizes, the system is compact: buttons at 12px, captions at 12–14px. The CSS variable naming system (`--comp-*`, `--sema-*`, `--base-*`) reveals a sophisticated three-tier design token architecture: component-level, semantic-level, and base-level tokens.

What distinguishes Pinterest is its generous border-radius system (12px–40px, plus 50% for circles) and warm-tinted button backgrounds. The secondary button (`#e5e5e0`) has a distinctly warm, sand-like tone rather than cold gray. The primary red button uses 16px radius — rounded but not pill-shaped. Combined with warm badge backgrounds (`hsla(60,20%,98%,.5)` — a subtle yellow-warm wash) and photography-dominant layouts, the result is a design that feels handcrafted and personal, not corporate and sterile.

**Key Characteristics:**
- Warm white canvas with olive/sand-toned neutrals — cozy, not clinical
- Pinterest Red (`#e60023`) as singular bold accent — never subtle, always confident
- Pin Sans custom font with global fallback stack (including CJK)
- Three-tier token architecture: `--comp-*` / `--sema-*` / `--base-*`
- Warm secondary surfaces: sand gray (`#e5e5e0`), warm badge (`hsla(60,20%,98%,.5)`)
- Generous border-radius: 16px standard, up to 40px for large containers
- Photography-first content — pins/images are the primary visual element
- Dark near-purple text (`#211922`) — warm, with a hint of plum

## 2. Color Palette & Roles

### Primary Brand
- **Pinterest Red** (`#e60023`): Primary CTA, brand accent — bold, confident red
- **Green 700** (`#103c25`): `--base-color-green-700`, success/nature accent
- **Green 700 Hover** (`#0b2819`): `--base-color-hover-green-700`, pressed green

### Text
- **Plum Black** (`#211922`): Primary text — warm near-black with plum undertone
- **Black** (`#000000`): Secondary text, button text
- **Olive Gray** (`#62625b`): Secondary descriptions, muted text
- **Warm Silver** (`#91918c`): `--comp-button-color-text-transparent-disabled`, disabled text, input borders
- **White** (`#ffffff`): Text on dark/colored surfaces

### Interactive
- **Focus Blue** (`#435ee5`): `--comp-button-color-border-focus-outer-transparent`, focus rings
- **Performance Purple** (`#6845ab`): `--sema-color-hover-icon-performance-plus`, performance features
- **Recommendation Purple** (`#7e238b`): `--sema-color-hover-text-recommendation`, AI recommendation
- **Link Blue** (`#2b48d4`): Link text color
- **Facebook Blue** (`#0866ff`): `--facebook-background-color`, social login
- **Pressed Blue** (`#617bff`): `--base-color-pressed-blue-200`, pressed state

### Surface & Border
- **Sand Gray** (`#e5e5e0`): Secondary button background — warm, craft-like
- **Warm Light** (`#e0e0d9`): Circular button backgrounds, badges
- **Warm Wash** (`hsla(60, 20%, 98%, 0.5)`): `--comp-badge-color-background-wash-light`, subtle warm badge bg
- **Fog** (`#f6f6f3`): Light surface (at 50% opacity)
- **Border Disabled** (`#c8c8c1`): `--sema-color-border-disabled`, disabled borders
- **Hover Gray** (`#bcbcb3`): `--base-color-hover-grayscale-150`, hover border
- **Dark Surface** (`#33332e`): Dark section backgrounds

### Semantic
- **Error Red** (`#9e0a0a`): Checkbox/form error states

## 3. Typography Rules

### Font Family
- **Primary**: `Pin Sans`, fallbacks: `-apple-system, system-ui, Segoe UI, Roboto, Oxygen-Sans, Apple Color Emoji, Segoe UI Emoji, Segoe UI Symbol, Ubuntu, Cantarell, Fira Sans, Droid Sans, Helvetica Neue, Helvetica, ヒラギノ角ゴ Pro W3, メイリオ, Meiryo, ＭＳ Ｐゴシック, Arial`

### Hierarchy

| Role | Font | Size | Weight | Line Height | Letter Spacing | Notes |
|------|------|------|--------|-------------|----------------|-------|
| Display Hero | Pin Sans | 70px (4.38rem) | 600 | normal | normal | Maximum impact |
| Section Heading | Pin Sans | 28px (1.75rem) | 700 | normal | -1.2px | Negative tracking |
| Body | Pin Sans | 16px (1.00rem) | 400 | 1.40 | normal | Standard reading |
| Caption Bold | Pin Sans | 14px (0.88rem) | 700 | normal | normal | Strong metadata |
| Caption | Pin Sans | 12px (0.75rem) | 400–500 | 1.50 | normal | Small text, tags |
| Button | Pin Sans | 12px (0.75rem) | 400 | normal | normal | Button labels |

### Principles
- **Compact type scale**: The range is 12px–70px with a dramatic jump — most functional text is 12–16px, creating a dense, app-like information hierarchy.
- **Warm weight distribution**: 600–700 for headings, 400–500 for body. No ultra-light weights — the type always feels substantial.
- **Negative tracking on headings**: -1.2px on 28px headings creates cozy, intimate section titles.
- **Single font family**: Pin Sans handles everything — no secondary display or monospace font detected.

## 4. Component Stylings

### Buttons

**Primary Red**
- Background: `#e60023` (Pinterest Red)
- Text: `#000000` (black — unusual choice for contrast on red)
- Padding: 6px 14px
- Radius: 16px (generously rounded, not pill)
- Border: `2px solid rgba(255, 255, 255, 0)` (transparent)
- Focus: semantic border + outline via CSS variables

**Secondary Sand**
- Background: `#e5e5e0` (warm sand gray)
- Text: `#000000`
- Padding: 6px 14px
- Radius: 16px
- Focus: same semantic border system

**Circular Action**
- Background: `#e0e0d9` (warm light)
- Text: `#211922` (plum black)
- Radius: 50% (circle)
- Use: Pin actions, navigation controls

**Ghost / Transparent**
- Background: transparent
- Text: `#000000`
- No border
- Use: Tertiary actions

### Cards & Containers
- Photography-first pin cards with generous radius (12px–20px)
- No traditional box-shadow on most cards
- White or warm fog backgrounds
- 8px white thick border on some image containers

### Inputs
- Email input: white background, `1px solid #91918c` border, 16px radius, 11px 15px padding
- Focus: semantic border + outline system via CSS variables

### Navigation
- Clean header on white or warm background
- Pinterest logo + search bar centered
- Pin Sans 16px for nav links
- Pinterest Red accents for active states

### Image Treatment
- Pin-style masonry grid (signature Pinterest layout)
- Rounded corners: 12px–20px on images
- Photography as primary content — every pin is an image
- Thick white borders (8px) on featured image containers

## 5. Layout Principles

### Spacing System
- Base unit: 8px
- Scale: 4px, 6px, 7px, 8px, 10px, 11px, 12px, 16px, 18px, 20px, 22px, 24px, 32px, 80px, 100px
- Large jumps: 32px → 80px → 100px for section spacing

### Grid & Container
- Masonry grid for pin content (signature layout)
- Centered content sections with generous max-width
- Full-width dark footer
- Search bar as primary navigation element

### Whitespace Philosophy
- **Inspiration density**: The masonry grid packs pins tightly — the content density IS the value proposition. Whitespace exists between sections, not within the grid.
- **Breathing above, density below**: Hero/feature sections get generous padding; the pin grid is compact and immersive.

### Border Radius Scale
- Standard (12px): Small cards, links
- Button (16px): Buttons, inputs, medium cards
- Comfortable (20px): Feature cards
- Large (28px): Large containers
- Section (32px): Tab elements, large panels
- Hero (40px): Hero containers, large feature blocks
- Circle (50%): Action buttons, tab indicators

## 6. Depth & Elevation

| Level | Treatment | Use |
|-------|-----------|-----|
| Flat (Level 0) | No shadow | Default — pins rely on content, not shadow |
| Subtle (Level 1) | Minimal shadow (from tokens) | Elevated overlays, dropdowns |
| Focus (Accessibility) | `--sema-color-border-focus-outer-default` ring | Focus states |

**Shadow Philosophy**: Pinterest uses minimal shadows. The masonry grid relies on content (photography) to create visual interest rather than elevation effects. Depth comes from the warmth of surface colors and the generous rounding of containers.

## 7. Do's and Don'ts

### Do
- Use warm neutrals (`#e5e5e0`, `#e0e0d9`, `#91918c`) — the warm olive/sand tone is the identity
- Apply Pinterest Red (`#e60023`) only for primary CTAs — it's bold and singular
- Use Pin Sans exclusively — one font for everything
- Apply generous border-radius: 16px for buttons/inputs, 20px+ for cards
- Keep the masonry grid dense — content density is the value
- Use warm badge backgrounds (`hsla(60,20%,98%,.5)`) for subtle warm washes
- Use `#211922` (plum black) for primary text — it's warmer than pure black

### Don't
- Don't use cool gray neutrals — always warm/olive-toned
- Don't use pure black (`#000000`) as primary text — use plum black (`#211922`)
- Don't use pill-shaped buttons — 16px radius is rounded but not pill
- Don't add heavy shadows — Pinterest is flat by design, depth from content
- Don't use small border-radius (<12px) on cards — the generous rounding is core
- Don't introduce additional brand colors — red + warm neutrals is the complete palette
- Don't use thin font weights — Pin Sans at 400 minimum

## 8. Responsive Behavior

### Breakpoints
| Name | Width | Key Changes |
|------|-------|-------------|
| Mobile | <576px | Single column, compact layout |
| Mobile Large | 576–768px | 2-column pin grid |
| Tablet | 768–890px | Expanded grid |
| Desktop Small | 890–1312px | Standard masonry grid |
| Desktop | 1312–1440px | Full layout |
| Large Desktop | 1440–1680px | Expanded grid columns |
| Ultra-wide | >1680px | Maximum grid density |

### Collapsing Strategy
- Pin grid: 5+ columns → 3 → 2 → 1
- Navigation: search bar + icons → simplified mobile nav
- Feature sections: side-by-side → stacked
- Hero: 70px → scales down proportionally
- Footer: dark multi-column → stacked

## 9. Agent Prompt Guide

### Quick Color Reference
- Brand: Pinterest Red (`#e60023`)
- Background: White (`#ffffff`)
- Text: Plum Black (`#211922`)
- Secondary text: Olive Gray (`#62625b`)
- Button surface: Sand Gray (`#e5e5e0`)
- Border: Warm Silver (`#91918c`)
- Focus: Focus Blue (`#435ee5`)

### Example Component Prompts
- "Create a hero: white background. Headline at 70px Pin Sans weight 600, plum black (#211922). Red CTA button (#e60023, 16px radius, 6px 14px padding). Secondary sand button (#e5e5e0, 16px radius)."
- "Design a pin card: white background, 16px radius, no shadow. Photography fills top, 16px Pin Sans weight 400 description below in #62625b."
- "Build a circular action button: #e0e0d9 background, 50% radius, #211922 icon."
- "Create an input field: white background, 1px solid #91918c, 16px radius, 11px 15px padding. Focus: blue outline via semantic tokens."
- "Design the dark footer: #33332e background. Pinterest script logo in white. 12px Pin Sans links in #91918c."

### Iteration Guide
1. Warm neutrals everywhere — olive/sand grays, never cool steel
2. Pinterest Red for CTAs only — bold and singular
3. 16px radius on buttons/inputs, 20px+ on cards — generous but not pill
4. Pin Sans is the only font — compact at 12px for UI, 70px for display
5. Photography carries the design — the UI stays warm and minimal
6. Plum black (#211922) for text — warmer than pure black

## 10. Registro de treinos

A captura de treinos começa pela escolha entre cards de atividade com imagem. Os dados essenciais aparecem em um card compacto, com métricas específicas por categoria e atalhos de duração. Local e foto são opcionais; detalhes adicionais são revelados sob demanda. Evitar formulário extenso ou sequência longa de etapas. Preservar rótulos acessíveis, foco visível, uso por teclado e erros legíveis.

No histórico, a foto pessoal tem prioridade sobre a imagem fixa da categoria. Cards mostram data/horário, atividade e métricas, com filtros simples e layout responsivo. Reutilizar os tokens e o tratamento visual das páginas existentes.

## 11. Revistas

Reutilizar os cabeçalhos de coleção/biblioteca, cards de capa existentes em livros e filmes. Revistas usa grid regular responsivo, autorizado para preservar a sequência cronológica por linhas; livros mantém seu layout atual. No detalhe, seguir o cabeçalho compacto e os comentários de livros. Usar títulos diretos (Revistas, Leituras, Comentários), sem frases decorativas ou explicações repetidas.

Mês/ano de publicação usa seleção de mês e ano numérico, com a mesma apresentação em Firefox e Chromium. Editar uma leitura abre o formulário no próprio item do histórico; manter foco visível e rolagem suave apenas quando necessária, respeitando movimento reduzido. Ao salvar 100%, refletir a conclusão retornada pelo backend.

No acervo de revistas, a ação é “Adicionar número” e o filtro é “Leitura”. Últimas leituras mostra jornadas concluídas em cards compactos, com data de término explícita; Todos os números mantém capas grandes sem data de atividade ambígua. Publicações oferece contagens do acervo e navegação por publicação, com números agrupados pelo ano da edição. Sem data fica ao final.

Separar o dashboard de Revistas, o catálogo de Publicações e o arquivo de cada publicação em páginas próprias. No arquivo, usar o hero compartilhado com rótulo “Último número”, escolhido por mês/ano e numeração decrescentes. Mostrar apenas a contagem no hero; ISSN em detalhes recolhidos. Busca local e filtro Leitura não repetem o contexto Publicação; limpar mantém esse contexto. Mostrar porcentagem nos cards apenas para Lendo, evitando 0% junto de Quero ler e 100% junto de Lida.

## 12. Artes visuais

A biblioteca usa galeria masonry responsiva com imagens em proporção original, sem recortes. No detalhe da obra, o cabeçalho centralizado reúne título, artista com retrato opcional e ano; a galeria ampla vem antes da ficha técnica e descrição. Navegação, miniaturas e ampliação na própria página preservam a composição integral e não alteram a imagem principal salva. A ampliação ocupa toda a tela sobre superfície escura quente; a imagem usa a maior área disponível sem recorte, com controles discretos sobrepostos nas bordas e legenda compacta. Não limitar a imagem ampliada a uma altura menor que a da galeria. Aceitar Escape, navegação por teclado e devolver o foco ao controle de origem.

No artista, retrato e nome abrem a página com país e datas, seguidos da biografia. Sem retrato, não reservar coluna vazia. Textos curtos aparecem completos; textos longos mostram um trecho inicial com Continuar leitura e Recolher. Usar largura confortável de leitura e fundo livre de painéis nessas áreas. As obras locais são agrupadas pelo ano de criação em grid cronológico, autorizado para preservar a ordem por linhas e entre páginas; Sem ano fica ao final. Nascimento e falecimento aparecem como contexto quando informados. Favorito, avaliação, experiências e listas continuam acessíveis.

Cadastro mostra título, artista, ano, tipo, movimento e técnica. Tipo usa seletor com categorias comuns e Outra com texto próprio. Técnica e movimento usam exemplos curtos nos campos. País de origem usa nomes de países. Dados adicionais do artista e descrição são revelados sob demanda. Data e contato não são preenchidos automaticamente; dados opcionais não bloqueiam o registro.

Gerenciar imagens e Gerenciar listas abrem os controles de manutenção e movem o foco para o formulário. Excluir obra/artista aparece durante edição, preservando a leitura como estado padrão. Listas atuais permanecem visíveis; remoção e escolha de principal aparecem durante gerenciamento. Preservar campos, feedback e novas tentativas em caso de erro. Reutilizar tokens, tipografia, cantos arredondados e foco acessível existentes. Usar rótulos diretos e evitar prosa decorativa.

Movimentos e estilos usa seleção múltipla: autocomplete nativo com sugestões iniciais e termos do acervo, entrada livre, Adicionar/Enter e chips removíveis por teclado. Salvar inclui o último termo digitado. Preservar tokens, espaçamento, cantos e foco existentes. Detalhe mostra termos separados e pendências de atualização das imagens com ação de nova tentativa, sem impedir a leitura da obra.

## 13. Capas de livros e jogos

“Alterar capa” revela seleção de arquivo, prévia e salvar na própria página. Preservar arquivo e prévia em erro para nova tentativa; sucesso atualiza a imagem exibida sem esconder o feedback. Usar superfícies quentes, controles de 16px, foco acessível e placeholders existentes. Upload manual de livro troca a capa canônica; a edição da leitura continua prioritária na apresentação dos dados de edição.

No detalhe de jogos, “Escolher imagens” abre um seletor nativo em tela cheia, com o nome do jogo, retorno explícito e superfície quente. Cabeçalho e barra inferior permanecem acessíveis; somente a área de opções rola. A barra mostra contagem, limpar seleção e “Salvar N imagens”. Fechar/reabrir preserva as escolhas durante a sessão, sem salvá-las automaticamente. Atualizar opções é uma ação secundária separada.

Os cards priorizam a imagem sem recorte, com ampliação no clique e checkbox em uma área de toque de 44px. A seleção usa checkbox e borda vermelha; tipo/provedor/dimensões são secundários. Estado de existência tem ícone próprio, sem símbolo de seleção: “Já no jogo” em superfície escura quente e “No Visual Library” em superfície areia. Reservar espaço para o estado para evitar saltos de layout. Verificação é um spinner discreto, respeitando movimento reduzido; ausência não gera badge “nova”. Falha geral aparece uma única vez e não bloqueia escolhas. Não ocultar nem bloquear imagens existentes.

Clicar na prévia abre o original no visualizador escuro, com navegação e checkbox para selecionar sem fechar. “Voltar à seleção” e Escape retornam ao seletor com foco e rolagem preservados; Escape no seletor retorna ao jogo. O foco fica contido no diálogo ativo, e a página permanece sem rolagem enquanto qualquer diálogo estiver aberto. Preservar escolhas e feedback em falhas; falhas parciais ficam selecionadas para retry. Deduplicação e revisão de duplicadas pertencem ao Visual Library; não manter histórico de URLs de origem.

O cabeçalho de jogos mostra uma prévia do acervo: capa maior acompanhada de até três imagens complementares. As prévias preenchem os cards com recorte central (`object-fit: cover`), evitando barras laterais quando a proporção do arquivo difere da área disponível; a composição integral fica disponível ao ampliar. O mosaico se adapta a uma, duas, três ou quatro imagens; com uma só, mostrar apenas a imagem, sem coluna vazia. Sem imagens, não reservar espaço de galeria. A ação visível “Ver todas as N imagens” (ou “Ver imagem”) fica junto da prévia e abre o visualizador completo; clicar em qualquer prévia abre essa mesma galeria na imagem correspondente. Não repetir o acervo em uma galeria sempre aberta abaixo do cabeçalho. Esta revisão prioriza acesso ao histórico pessoal sem perder a apresentação visual do jogo.

A galeria salva é pública e abre sob demanda, em tela cheia sobre superfície escura quente, com miniaturas, contador, setas, Escape e retorno do foco. Preservar a composição integral e usar a área disponível sem recorte. Ao trocar de imagem, remover a anterior imediatamente e apresentar carregamento discreto até a nova estar pronta. Falhas mostram “Imagem indisponível”, sem impedir navegação. O seletor de novas imagens continua separado da consulta ao acervo.

No visualizador salvo, o owner vê “Definir como capa” ou “Capa atual”, e “Remover imagem”, para a imagem atual. A confirmação aparece junto dessas ações, com confirmar/cancelar e aviso de substituição automática da capa. Reservar espaço para controles e feedback sem sobrepor a imagem, inclusive no celular. Durante uma alteração, bloquear ações e navegação concorrentes; preservar fechar/Escape. Ao trocar capa, manter a mesma imagem em visualização apesar da reordenação. Ao remover, continuar na posição disponível; ao remover a última, fechar, liberar rolagem e devolver foco ao cabeçalho. Erros preservam confirmação e retry; teclado e foco permanecem no diálogo enquanto aberto.

## 14. Séries de livros e universos

Listagem de séries usa cards com mosaico de até quatro capas locais, nome, autores com links e contagem do acervo, seguindo listas de álbuns e filmes. Reutilizar os mesmos cards no contexto do livro e na página do autor. Universos derivam capas da ordem de suas séries e livros.

No detalhe, apresentar capa representativa e nome, autores, descrição expansível e universos no cabeçalho; os livros seguem a ordem de leitura em cards sequenciais. Editar série/universo revela metadados locais, associações e controles de reordenação/remoção; manter exclusão dentro da edição. Não confundir publicação concluída no Hardcover com leitura pessoal nem contagem externa com acervo local.

Gerenciar séries distingue associar a uma série existente, criar uma série e buscar sugestões do Hardcover. Cada sugestão identifica sua origem, mostra capas e livros locais, marca os já associados e permite escolher o destino local. Metadados têm seleção explícita e comparação com valores atuais; preservar escolhas e feedback em falha. Usar tokens, superfícies quentes, controles de 16px, foco visível, operação por teclado e composição responsiva das listas existentes.

Pendências de tags das capas aparecem junto das séries do livro em um bloco discreto com superfície quente, texto de estado/erro e botão de retry com raio de 16px. Preservar leitura e atribuição utilizáveis durante falhas externas; não expor revisões, leases ou detalhes de implementação na UI.

## 15. Imagens de filmes

Filmes reutilizam o visualizador de jogos. O cabeçalho segue a composição definida na seção 16: com uma ou duas imagens, mostrar a capa integral em sua proporção natural; com três ou mais, mostrar mosaico com capa e até três imagens complementares, preenchidas com recorte central. “Ver todas as N imagens” abre a galeria completa, e cada prévia abre a imagem correspondente. Sem imagens, não reservar coluna vazia. A galeria completa abre sob demanda, com imagem integral, miniaturas, setas, Escape, carregamento independente por original e retorno de foco. Curadoria de capa e remoção ficam no visualizador para o owner, com confirmação, retry e substituição automática da capa removida. Não repetir a galeria abaixo do cabeçalho. Preservar pessoas, termos, empresas, coleção, listas e sessões. Esta regra de capa integral é específica de filmes; jogos mantêm a prévia descrita na seção 13.

O owner pode abrir “Escolher imagens” junto da galeria, inclusive sem imagens salvas. Filmes compartilham o seletor de tela cheia de jogos, com pôsteres e fundos do TMDb, mantendo rótulos próprios do domínio. Preservar seleção ao voltar, ampliação, foco, scroll e retry parcial. Salvar adiciona imagens sem alterar a capa; curadoria continua no visualizador salvo.

Pendências de tags de coleção aparecem junto da coleção no detalhe, em superfície quente e controles de 16px, com mensagem e retry. A falha de classificação não bloqueia cadastro, imagens ou leitura. Não expor revisão, lease ou detalhes internos na interface.

## 16. Detalhe de filme

Compor o cabeçalho como uma superfície branca uniforme, sem degradê, arte de fundo ou sombra. No desktop, título, ano/título original e sinopse ocupam a coluna esquerda, com memória pessoal logo abaixo; capa/galeria ficam à direita, alinhadas ao topo. No celular, a ordem é identificação, imagem e memória pessoal. A área de comentários usa o próprio fundo do cabeçalho, sem outro card dentro dele. Nota compacta fica junto do título dos comentários, e favorito/abandonado próximos. Evitar colunas vazias e títulos decorativos como “Filme”. Sinopses longas têm “Ler mais”/“Ler menos”, sem perder o texto completo.

Com uma ou duas imagens no acervo, mostrar a capa integral na proporção natural, sem fundo lateral ou `contain` em uma caixa larga. Limites de altura: 440px no desktop e 380px no celular, respeitando a largura disponível. O arredondamento acompanha a imagem, sem faixas cinzas. Com três ou mais imagens, manter o mosaico e a galeria definidos na seção 15.

Comentários seguem a hierarquia de livros: título simples, data absoluta uma vez e texto, sem contagem ou resumo do último comentário. Preservar edição, exclusão e composição sob demanda. Nota e marcações mantêm seus controles sem explicações automáticas da escala competindo com o comentário. Não mostrar blocos pessoais vazios para visitantes.

Sessões e “Sobre o filme” usam a mesma superfície branca, borda discreta, padding de 20–32px e cantos de 28px; separar grupos internos com espaçamento e linhas suaves, sem cards aninhados. Sessões mostram datas e origens importadas, sem rótulo manual ou estatísticas repetidas; registrar sessão revela o formulário junto do histórico. Créditos, gêneros/tags e empresas têm subtítulos consistentes e neutros. Reservar vermelho às ações principais, sem usá-lo em rótulos decorativos. Elenco começa com seis nomes e expansão explícita; edição mantém todos visíveis. Não repetir a função quando já for o título do grupo.

Edição fica junto da seção, apenas para o owner; “Atualizar dados” revela o enriquecimento e os identificadores. Coleção e listas permanecem navegáveis; ocultar listas vazias para visitantes, mantendo organização para o owner. Usar largura de página de até 1120px, espaçamento de 32px entre seções, tokens existentes e foco visível. Não adicionar duração enquanto o contrato de detalhe não fornecer esse dado.

## 17. Revisão gradual dos domínios

Revisar UI, UX e copy de um domínio por vez, no seu próprio ritmo. O detalhe de filme das seções 15 e 16 é uma referência aprovada de composição: superfícies brancas sobre fundo quente, hierarquia editorial, memória pessoal próxima da obra e poucas caixas internas. Usar essa direção nas próximas revisões, adaptando-a ao conteúdo e aos fluxos de cada domínio.

Consistência significa compartilhar tokens, tipografia, espaçamento, tratamento das superfícies e clareza das ações; não exige telas idênticas nem transpor literalmente a disposição de filmes. Preservar as particularidades e a linguagem de cada entidade: séries precisam acomodar temporadas, episódios e progresso; livros podem manter uma apresentação mais editorial; filmes valorizam imagens e sessões.

A próxima revisão prevista é Séries (TV). Livros já recebeu uma revisão de conteúdo e UX que deve ser preservada; quando chegar sua vez, comparar com a direção atual e ajustar somente o que trouxer benefício. Diferenças entre páginas durante essa evolução gradual são esperadas e não justificam padronização em massa nem desfazer resultados aprovados. Cada revisão deve avaliar o domínio, conferir desktop e celular e registrar suas decisões específicas neste documento, sem converter uma escolha local em regra universal.

## 18. Detalhe de série e temporada

O detalhe de série usa o cabeçalho branco e a galeria sob demanda de filmes, com marcações pessoais junto da identificação. O owner pode abrir “Escolher imagens” junto da galeria para ampliar e salvar pôsteres e fundos do TMDb. O seletor compartilhado preserva a capa e mantém as escolhas que falharam para nova tentativa. Na galeria salva, séries e filmes compartilham os controles do owner para definir a capa e remover um vínculo com confirmação. Progresso e temporadas aparecem imediatamente abaixo. A contagem geral aparece uma vez, e cada temporada mostra sua própria contagem. Temporadas sem rota são texto. Atualizar episódios fica junto do progresso.

Nota, comentários e sessões ficam em Atividade, com registro sob demanda. Sobre a série reúne créditos, gêneros, tags e listas, com edição contextual e atualização de dados sob demanda.

O detalhe de temporada usa cabeçalho compacto com retorno à série e uma contagem de progresso. Os episódios aparecem em ordem, separados por linhas. Marcar como assistido registra a hora atual. Opções de registro oferecem data personalizada e marcação em lote até o episódio escolhido. Se houver falha parcial, recarregar os episódios e mostrar quantos foram registrados. A atualização de dados da temporada é aberta pelo owner junto da lista.


## 19. Detalhe de álbum

Usar largura de até 1120px e painéis brancos uniformes sobre o fundo quente, com cantos de 28px e espaçamento de 32px. Identificação e comentários ficam à esquerda da capa no desktop; no celular, a ordem é identificação, capa e memória pessoal. A capa mantém a proporção original, sem recorte, arte de fundo ou faixas acrescentadas; a composição favorece as capas quadradas de música. Créditos preservam nomes, ordem, conectivos e links individuais dos artistas. Mostrar o ano uma vez.

Faixas vêm logo após o cabeçalho, em linhas compactas separadas por bordas suaves, preservando discos, posições e ordem. Reproduções e última escuta têm rótulos claros. A nota existente permanece visível e sua alteração é revelada sob demanda, sem repetir explicações da escala. Revisar duplicatas fica junto das faixas.

Comentários usam o tratamento editorial compartilhado, sem contagem nem resumo redundante; nota compacta do álbum fica junto deles. Ocultar memória pessoal vazia para visitantes. Histórico reúne reproduções de faixas, primeira data registrada e última escuta; os dias recentes são revelados sob demanda. Registros não equivalem a audições completas. Sobre o álbum reúne gêneros, tags e listas, com edição contextual e atualização MusicBrainz preservando prévia e confirmação. Busca/filtro de faixas, duração e imagens adicionais ficam fora desta revisão.


### Contexto e ampliação do álbum

A capa do álbum abre o visualizador compartilhado por clique ou “Ver imagem”, preservando composição integral, Escape, foco e scroll. Com uma capa não mostrar setas ou miniaturas desnecessárias; sem capa, não mostrar ação de ampliação. A consulta de novas imagens continua fora desta entrega.

Tipo e primeiro lançamento compõem uma única linha de contexto junto dos créditos, respeitando a precisão da data e evitando repetir o ano. A introdução aparece abaixo dos créditos e antes da memória pessoal, no próprio fundo branco, com “Ler mais”/“Ler menos” para textos longos. Mostrar atribuição, idioma, fonte e licença em texto secundário legível. Sem introdução, não reservar espaço. Links externos ficam em Sobre o álbum. “Consultar contexto” oferece prévia e confirmação independentes da atualização de créditos e marcações.


No álbum, o painel Atualizar dados aparece imediatamente após o cabeçalho Sobre o álbum, junto do botão que o revela, antes de gêneros, listas e links. Recolher/reabrir preserva a entrada durante a sessão. A busca MusicBrainz oferece também Link ou identificador, disponível mesmo sem candidatos. Consultar mostra a mesma prévia com álbum, artistas e link de origem; somente confirmar aplica. Erros preservam a entrada, alterações invalidam a prévia e consultas bloqueiam ações concorrentes. Manter labels, foco e layout responsivo dos controles existentes.


Faixas de álbum podem ter 🔥 discreto junto do título para destacar a maior contagem pessoal de reproduções nesse álbum. Exigir ao menos três reproduções e duas faixas; em empate, destacar até três líderes, sem marcar se todas tiverem a mesma contagem ou houver mais de três líderes. Preservar ordem original, contagens e notas. Explicar “Entre as mais ouvidas por você neste álbum” em tooltip por mouse e foco de teclado, com nome acessível. Não usar o termo popularidade, alterar o fundo das linhas ou acrescentar animação.


## 20. Detalhe de artista musical

Usar até 1120px e painéis brancos uniformes, bordas discretas, cantos de 28px e 32px entre seções. O cabeçalho mostra nome, origem e datas identificadas; pessoa usa nascimento/falecimento e grupos formação/encerramento, sem apresentar nascimento como atividade. Biografia expansível fica na identificação, com idioma, fonte/revisão e licença. Retrato integral e ampliável usa até 300px de largura e 360px de altura, sem arte de fundo ou capa de álbum como substituição. No celular, a ordem é identificação, retrato e biografia. Sem retrato, não reservar coluna vazia. Autoria, fonte e licença da imagem permanecem públicas.

Álbuns no acervo vêm primeiro, em grid de capas quadradas por linhas, específico do detalhe para preservar a ordenação Mais ouvidos/Ano de lançamento. Mostrar a contagem uma vez, créditos com links e metadados compactos. Não apresentar o acervo como discografia externa completa. Explorar discografia e revisar duplicatas ficam nessa seção para o owner, sob demanda, sem expor manutenção ao visitante.

Mais ouvidas por você usa linhas compactas, apenas com reproduções registradas, álbum de referência navegável e última escuta identificada. Não repetir 🔥 em um ranking que já indica os destaques. Histórico reúne total, primeira data registrada, última escuta e dias recentes sob demanda; não repetir contagens no cabeçalho nem calcular cobertura entre recortes distintos.

Sobre o artista reúne gêneros ativos, links e outros nomes recolhidos. Origem e gêneros ocultos só aparecem durante gerenciamento pelo owner. Atualizar dados abre o painel junto do botão e preserva estado/feedback no refresh. Vínculo manual e busca permitem revisar a correspondência antes de confirmar; biografia tem consulta/prévia/confirmação próprias, sem alterar outros metadados. Reutilizar os componentes de visualização e atribuição dos álbuns, preservando as particularidades do artista.


## 21. Detalhe de livro

Usar largura de até 1120px, painéis brancos uniformes com padding de 20–32px, cantos de 28px e 32px entre seções, preservando a apresentação editorial dos textos. No desktop, identificação/autores/ano e sinopse ficam à esquerda, capa integral à direita. No celular, identificação, capa e sinopse. A posição da capa foi alinhada aos demais domínios por decisão explícita do owner. Usar largura de até 260px e altura de até 400px (360px no celular), sem recorte ou arte de fundo. Ver capa e clique na imagem abrem o visualizador compartilhado, com Escape, foco e scroll preservados. Alterar capa fica junto da imagem e mantém upload, prévia e retry, alterando somente a capa canônica.

Leituras vêm imediatamente após o cabeçalho, preservando jornadas, releituras, datas e status. Não repetir status no cabeçalho, 0% de Quero ler ou 100% de uma jornada concluída. Identificar a edição por jornada quando diferirem, sem inferir datas ou edição ausentes. Comentários e nota vêm em seguida, sem caixa interna por comentário, contagem ou resumo repetido. Preservar decimais da nota importada e resenhas Hardcover como conteúdo de origem externa, inclusive sem data disponível. Ocultar comentários vazios para visitantes, mantendo composição para o owner; indicar edição de comentário discretamente.

Sobre o livro reúne séries e edições; séries vazias ficam ocultas para visitantes, e gestão permanece contextual para o owner. Pendências de tags de capas e retry aparecem apenas no contexto do owner. A edição da leitura mais recente é identificada, com outras edições recolhidas e ISBNs sob detalhes. Livro, edição e jornada continuam distintos. Não acrescentar edição local de leituras/nota ou novos provedores nesta revisão.

O cabeçalho de livros usa composição opt-in; revistas mantêm sua apresentação e o uso não compacto da timeline por autores permanece inalterado. Reutilizar tokens e controles existentes, com foco visível e estados de erro legíveis.


## 22. Detalhe de autor de livros

Usar até 1120px, painéis brancos, cantos de 28px e 32px entre seções. Cabeçalho compacto com nome e contexto pessoal em uma linha: livros no acervo, leituras concluídas e em andamento, sem repetir contagens. Leituras concluídas são sessões e podem incluir releituras. Não usar capa de livro como retrato do autor nem reservar uma coluna para imagem ausente.

Livros no acervo aparecem primeiro, com capas integrais em grade regular, créditos de coautoria navegáveis e ano disponível. Ordenar por título ou ano de publicação, do mais recente ao mais antigo, com anos ausentes ao final e desempate por título/id. Não apresentar o acervo como bibliografia completa, nem data de resenha como atividade de leitura. Preservar a biblioteca e seus cartões compartilhados.

Leituras recentes vêm depois, em linhas com livro navegável, estado e datas identificadas de início/fim, sem repetição relativa/absoluta, origem técnica ou progresso redundante de conclusão. Informar o recorte quando houver mais registros que os até 20 retornados. Séries no acervo ficam ao final. Ocultar histórico e séries sem conteúdo; erro de carregamento oferece nova tentativa. Foto, biografia e enriquecimento de identidade exigem uma etapa própria.


No autor, manter grade regular por linhas, sem masonry: a ordem de títulos e volumes deve ser previsível. Exibir inicialmente até 12 livros, com Ver todos/Mostrar menos quando houver mais; a ordenação considera todo o acervo antes do recorte. Omitir o próprio autor nos créditos dos cartões, preservando links de coautores com o rótulo Com. Ordenação numérica de títulos não unifica nomes ou edições diferentes. Cartões de séries precisam do contexto de estilos book-groups, limitado ao próprio bloco, com mosaico de altura controlada e largura responsiva.


Com um único livro no autor, usar composição própria em toda a largura: capa integral à esquerda e informações à direita no desktop; capa seguida das informações em uma coluna no celular. Manter a grade regular para vários livros. Créditos adicionais preservam links e usam Outros créditos, pois o contrato não distingue funções como autoria, tradução e outras contribuições.


## 23. Séries literárias e universos

As listagens e detalhes usam até 1120px, 32px entre seções e painéis brancos com borda discreta, cantos de 28px e padding de 20–32px. Não usar gradientes, rótulos decorativos vermelhos ou títulos repetidos no cabeçalho. Listagens mantêm mosaicos em grade regular com largura limitada por cartão. Criação fica sob demanda junto da identificação, apenas para o owner, preservando nome e feedback em erro ou ao recolher/reabrir.

No detalhe, nome, créditos locais navegáveis e quantidade local aparecem uma vez. A imagem é explicitamente do acervo, sem sugerir capa própria da série; à direita no desktop, integral e com até 320px de altura. No celular, identificação antes da imagem e descrição depois. Sem imagem ou em falha, remover a coluna. Descrição longa expande sob demanda, sem tradução automática.

Ordem no acervo vem depois do cabeçalho: lista ordenada de todos os livros em um painel único, com linhas, capas integrais e títulos navegáveis. A posição representa a sequência salva, não o volume oficial nem uma cronologia inferida. Universo usa Séries neste universo, com links para os detalhes de série. Sem masonry, filtros ou reordenação por título.

Sobre a série reúne universos e dados persistidos do Hardcover. Identificar contagens externas e situação editorial; não inferir progresso pessoal ou volumes ausentes. O autor principal externo não se repete quando tem o mesmo nome de um crédito local, mas permanece disponível na prévia de manutenção. Esse tratamento visual não cria nem unifica identidades.

Edição abre junto do botão no cabeçalho; mover e remover aparecem junto do membro durante edição. Preservar inclusão ao final, associação de universo, exclusão confirmada e atualização de metadados com prévia e campos selecionados. Visitantes não recebem instruções para controles indisponíveis. Usar estilos opt-in nas páginas revisadas, preservando cartões e atribuição usados por autores e livros.


## 24. Contexto enriquecido do autor de livros

O vínculo Hardcover é revisado explicitamente por link ou ID, com prévia e seleção dos campos disponíveis. Atualizar dados fica em Sobre o autor, junto do painel que revela; entrada/seleção permanecem em erro. Troca de identidade exige confirmação e explicita a remoção do contexto anterior, preservando livros e leituras. Não inferir identidade por nome.

Quando disponível, retrato integral e ampliável fica à direita da identificação/biografia no desktop, com até 280px de largura e 360px de altura, e fonte pública. No celular, identificação, retrato e biografia. Sem retrato ou em falha, não reservar coluna. Biografia preserva o texto do provedor, expande sob demanda e identifica a fonte. Datas distinguem nascimento/falecimento e preservam precisão; origem não é apresentada como nacionalidade. Links externos ficam em Sobre o autor. A grade de livros e seus créditos permanecem independentes desse contexto. Não exibir licença de imagem inventada ou usar capa como retrato.

## 25. Revistas: publicação, número e jornadas

Revistas mantêm contratos e fluxos locais próprios, distintos de livros. Usar largura de até 1120px, painéis brancos sobre fundo quente, borda discreta, cantos de 28px, padding de 20–32px e 32px entre seções. Cabeçalhos e cartões são específicos do domínio, sem modificar os componentes compartilhados de livros ou filmes e sem fundos com arte duplicada.

No detalhe do número, nome da publicação navegável e identificação (número/mês/ano) ficam à esquerda; capa integral à direita, com até 240px de largura e 360px de altura. No celular, identificação antes da capa, com até 320px de altura. Clique ou Ver capa abre o visualizador compartilhado, com Escape e retorno do foco. Sem imagem ou em falha, não reservar coluna nem oferecer ampliação. Atualizar o número/capa deve atualizar o visualizador sem depender de recarregar a página.

Leituras aparecem em seguida, em painel único com linhas compactas. Ler/Atualizar leitura/Reler fica junto do título; editar e excluir ficam junto da jornada. Mostrar estado uma vez, datas identificadas de início/fim e progresso pertinente: página atual/total quando disponível, caso contrário porcentagem em leitura, sem 0% em Quero ler ou 100% numa conclusão. Preservar datas civis, releituras, abandono e correções existentes. Exclusão de jornada preserva comentários, e sua confirmação explicita isso.

Comentários vêm depois, no tratamento editorial compartilhado, sem contagens ou resumo do último comentário. Ocultar a seção vazia para visitantes, mantendo composição para o owner. Sobre o número reúne páginas, ISSN e acesso à publicação, com edição sob demanda e erro/feedback junto do contexto. Preservar exclusão confirmada do número com suas jornadas e comentários, distinguindo-a da exclusão de leitura.

Publicação tem cabeçalho compacto e contagem uma vez. A capa representativa, quando houver, é do Último número publicado, identificada e navegável para o número; não muda ao filtrar. ISSN e edição de nome/ISSN ficam em Sobre a publicação; explicar que alterações afetam todos os números. Adicionar número e todos os controles de manutenção são exclusivos do owner, inclusive em rotas com add=1. Formulários mantêm entrada/feedback em erro e rascunhos ao recolher/reabrir.

Acervo, Em andamento, Últimas leituras e listagem de publicações usam grade regular por linhas, com capas integrais. Um único item ocupa uma composição adaptativa em toda a largura: capa/texto lado a lado no desktop e empilhados no celular. Dentro do arquivo de uma publicação, priorizar número e mês/ano no cartão, sem repetir a publicação. Preservar agrupamento anual, busca, filtro de leitura/Não lida, paginação e rotas legadas. Não apresentar um seletor com uma única ordenação disponível. Cada releitura no destaque recente representa sua jornada, sem confundir a leitura atual do número.

Manter o fluxo de dados salvos com capa pendente: retry envia apenas a capa e não recria número ou jornada. Não acrescentar provedor, OCR, artigos, progresso agregado, imagens adicionais ou avaliação nesta revisão.


## 26. Refinamento de obra e artista visual

Esta revisão evolui as superfícies do detalhe previstas na seção 12. A obra preserva identificação compacta e galeria ampla com imagem integral, adequada a composições verticais e panorâmicas. Não mostrar contador com uma única imagem. Navegar não altera a principal salva; ampliação preserva teclado, Escape e foco, e falhas oferecem nova tentativa.

Nota e experiências vêm imediatamente após a galeria. Ocultar blocos pessoais vazios para visitantes. Sobre a obra reúne ficha técnica, descrição, listas e manutenção contextual, sem contagens redundantes ou cartões aninhados. Origem do artista não é proveniência da obra; pendências de sincronização aparecem apenas para o owner.

Artista usa painel branco com nome/contexto e biografia à esquerda, retrato integral ampliável à direita, até 280px por 360px. No celular: identificação, retrato e biografia. Sem imagem ou em falha, não reservar coluna. Obras no acervo mantêm grade cronológica regular e paginação global; mostrar ano no grupo e datas de vida na identificação, sem repetição nos cartões. A biblioteca geral continua masonry.

Painéis de texto e memória usam até 1120px, padding de 20–32px, cantos de 28px e 32px entre seções, sobre o fundo quente. Descrição e biografia nestes detalhes têm prévia de 420 caracteres visíveis e expansão. Interpretar somente pares simples de negrito/itálico; preservar texto salvo, marcadores incompletos e HTML literal escapado, sem parser geral. Os estilos são específicos dos detalhes revisados.

## 27. Página principal de filmes

A entrada padrão da /movies tem composição editorial: identificação discreta fora de painel e destaque do último filme antes dos controles. A busca fica na base da mesma superfície do destaque; filtros de sessão e ano ficam junto do acervo. Em busca, filtro ou cadastro ativo, mostrar diretamente controles e resultados, sem a abertura editorial. Evitar um grande painel de filtros como primeiro elemento visual. Usar superfícies brancas sobre fundo quente, borda discreta, cantos de 28px, padding de 20–32px e 32px entre seções. Nesta página, usar largura de até 1480px e grade regular por linhas, como exceção explícita ao masonry geral: os pôsteres ficam integrais em áreas 2:3, com títulos e metadados abaixo. Usar quatro/três/duas colunas conforme o espaço, mantendo duas no celular e uma em larguras inferiores a 340px. Não alterar a grade de listas, coleções ou outros domínios. Na abertura editorial de filmes, permitir fundo suave derivado da capa, com baixa opacidade (aproximadamente 8%) e cartões claros que preservam contraste. Essa exceção é específica do bloco principal e substitui a decisão anterior de branco uniforme; não se aplica ao detalhe do filme ou a outras páginas.

A abertura usa composição integrada inspirada na home: destaque do último filme à esquerda e três anteriores em coluna à direita, com busca na base do conjunto. No destaque, pôster integral à esquerda, até 420px de altura, e título/contexto próximos à direita, alinhados à base. Título até 40px, sem competir com a imagem. Imagem e título levam ao detalhe quando existe rota, e busca fica fora desse link. Os secundários têm pôster integral com 104px de largura no desktop (88px no celular) e informações compactas; títulos quebram livremente em linhas, sem truncamento ou altura fixa para o texto. A faixa móvel acomoda a imagem maior em cartões de largura responsiva. Não repetir os filmes em faixa externa. No celular, destaque primeiro, imagem de até 280px antes do título de 28px, seguido de secundários em faixa horizontal curta e busca; não empilhar três cartões antes dela. Fundo é decorativo e não recebe descrição acessível. Sem imagem ou em falha, remover a coluna da capa e o fundo derivado, preservando texto e busca. Com um único recente, não reservar coluna de secundários. Não inventar sinopse/nota a partir de dados ausentes. O endpoint agrupa por filme: não apresentar esse recorte como histórico completo de sessões/releituras. Falha ou ausência de recentes mantém a busca acessível. Cartões do acervo mostram título, ano disponível, última sessão e contagem somente quando há múltiplas sessões; ausência de sessão é informada uma vez e não prova que o owner nunca viu o filme.

Ano da sessão distingue o histórico do ano de lançamento. Selecionar ano abre os filmes com sessão naquele ano; a busca por título sai do ano explicitamente e pode combinar com Sem sessão registrada. Limpar filtros retorna ao acervo. Não filtrar apenas uma página carregada como se fosse todo o catálogo. Informar limites de busca e recorte anual quando atingidos, sem usar o tamanho da resposta como total.

Não colocar Resumo do histórico após a grade com carregamento contínuo. Mostrar somente o contexto do recorte junto ao título: total de filmes no acervo, total sem sessão no filtro correspondente, ou filmes e sessões do ano selecionado. Na busca, omitir estatísticas globais e não tratar o tamanho dos resultados limitados como total. Datas extremas e outros indicadores ficam fora desta página. Estatísticas e recentes falham independentemente do acervo; erro dos totais/anos e retry ficam junto dos filtros, sem exibir números antigos ou presumidos. Paginação carrega automaticamente ao se aproximar do fim dos itens, sem botão de próxima página, preservando ordem e posição dos itens existentes. Mostrar estado discreto de carregamento, parar ao fim do cursor e desconectar o observador ao sair. Em erro, preservar itens/cursor e interromper tentativas automáticas até o retry explícito; descartar respostas de filtros anteriores. Cadastro abre junto da ação, mantém rascunho ao recolher e não aparece automaticamente após busca vazia. As opções visuais dos cartões e da grade são específicas desta página; outras rotas permanecem independentes.
