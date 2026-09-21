# Treinos: registro e lembranças

## Problema e objetivo

Preservar exercícios e suas lembranças no arquivo pessoal, com captura manual rápida e navegação visual. A missão inclui atividades físicas além do consumo cultural.

## Escopo

- Corrida, pular corda e academia, com imagens fixas locais.
- Data, horário de início e duração em minutos inteiros obrigatórios.
- Distância em km obrigatória para corrida, com até três casas decimais.
- Quantidade inteira de pulos opcional para corda.
- Local opcional em texto livre, até 200 caracteres.
- Uma foto opcional por treino (JPEG/PNG, até 10 MB e 40 megapixels).

## API/UI

Contrato em [workouts-api.md](../workouts-api.md). A página `/workouts`, acessível por “Treinos”, mostra cards em ordem cronológica decrescente, filtros de categoria e paginação. Cada card mostra dia, horário, duração, métricas específicas e local, quando presente.

O registro abre pela escolha entre três cards visuais. A edição compacta mostra apenas os campos pertinentes à atividade e atalhos de duração. O dia e o horário atuais são sugestões ajustáveis; nenhum treino é salvo sem ação explícita. Local é revelado sob demanda. Foto tem prévia e pode ser trocada ou removida antes de salvar. Dados preenchidos são preservados quando há erro ou quando o diálogo é fechado.

O `DESIGN.md` orienta cores, tipografia, imagens, arredondamento e responsividade. A entrada deve ser leve, sem aparência de formulário extenso. Rótulos, foco visível, navegação por teclado e mensagens de erro permanecem acessíveis.

## Regras

Foto própria tem prioridade sobre a imagem da categoria. Início é armazenado como instante e exibido no fuso do navegador. Duração, distância e pulos informados devem ser positivos. Campos específicos de outra categoria são rejeitados pela API. Dados são locais; fotos usam o diretório existente de covers.

## Fora do escopo

Integrações, mapas, planos de treino, séries/cargas, metas, recursos sociais, categorias configuráveis e edição/exclusão de registros já salvos.

## Critérios de aceite

- Registrar e consultar as três atividades, incluindo corrida com distância e corda sem contagem.
- Salvar com ou sem local/foto; mostrar foto própria ou imagem padrão.
- Navegar por categoria e carregar mais registros.
- Registrar em celular e por teclado com campos claros e controles acessíveis.
- Preservar localmente os dados e imagens, com validação antes da persistência.

## Imagens fixas

Geradas com a ferramenta integrada `image_gen` e versionadas em `frontend/app/assets/images/workouts/`: `running.png`, `rope.png`, `gym.png`. Nenhum provedor externo é consultado para exibir essas imagens.

Prompt comum:

> Use case: illustration-story. Asset type: square category cover for a personal exercise journal. Subject: {subject}. Style: refined editorial gouache illustration with subtle paper texture, warm off-white and olive/sand neutrals, muted colors, soft daylight. Composition: central recognizable subject with breathing room, suitable for cropping into a card. No text, no logos, no watermark.

Subjects:

- running.png: A pair of running shoes on a quiet sunlit park path, no people
- rope.png: One skipping rope with two wooden handles neatly curved on a warm sand-colored exercise mat, no people
- gym.png: A pair of dumbbells beside a folded towel on a warm neutral gym floor, no people
