# Enriquecimento de autores durante a importação Hardcover

Status: aprovado pela solicitação de implementação; implementado e validado localmente.

## Problema e objetivo

A importação de livros consultava somente o nome dos contribuidores. O enriquecimento recém-implementado exigia vínculo manual mesmo quando a contribuição da edição já oferecia identidade externa. Capturar essa evidência e preencher contexto automaticamente quando seguro, preservando escolhas manuais e o processamento dos livros.

## Escopo e regras

1. A consulta de user_books passa a incluir author.id nas contribuições da edição; payloads antigos sem ID continuam válidos. Não buscar pessoas pelo nome nem alterar fingerprints/autores/vínculos locais.
2. Guardar IDs observados por autor local, inclusive em livros já processados com sucesso, sem repetir jornadas. A resolução local usa o mesmo fingerprint do importador. Não criar autores só por consultar perfis externos.
3. Enriquecer após uma passagem completa pelo catálogo, deduplicando autores entre livros. Consultas limitadas por maxPages ou falhas na gravação de evidências adiam essa etapa; evidências já coletadas permanecem. Assim, a primeira página não escolhe um perfil antes de conhecer os demais nomes iguais no catálogo importado.
4. Um autor local precisa ter um único ID externo observado, não compartilhado por outro autor local. ID ausente é registrado como evidência não resolvida (zero), nunca usado para consultar um perfil. Evidências conflitantes são conservadas e exigem revisão manual; não há fusão ou descarte automático.
5. Perfis existentes antes da migração são manuais. Aplicar qualquer prévia manual protege o perfil inteiro contra importações posteriores. Perfis automáticos recebem apenas campos ausentes; nunca trocar de identidade automaticamente. Revalidar evidências e proteção manual sob lock antes de salvar, com unicidade externa mantida no banco.
6. Reutilizar o mapeamento, filtro de URLs, precisão de datas e armazenamento de retrato do fluxo manual. Foto falha não descarta texto; guardar somente a URL local efetivamente salva. Uma foto ausente pode ser tentada de novo em importação futura. Falhas de um autor não interrompem outros autores nem alteram o resultado dos livros já processados; cancelamento é propagado.
7. Consultas automáticas têm espaçamento de 1,5 segundo e intervalo mínimo de 24h entre tentativas para a mesma evidência. Observação repetida de contribuições não reinicia o intervalo. Não manter transação aberta durante consultas ou downloads.
8. Se um vínculo automático se tornar ambíguo depois, ocultar o contexto na resposta pública sem apagar o snapshot; a prévia manual permite revisão. Perfis confirmados manualmente continuam visíveis. A página permanece baseada em dados locais, sem novas consultas durante navegação.

## Arquivos, migração e API/UI

HardcoverApiClient e HardcoverGraphQL; HardcoverImportService; novo HardcoverAuthorImportService e HardcoverAuthorCandidateRepository; BookAuthorProfileRepository/BookQueryRepository; mapeamento compartilhado em BookAuthorEnrichmentService.

V81 acrescenta managed_manually a book_author_profiles (default true para preservar os perfis existentes) e cria hardcover_author_candidates com FK/candidatos persistidos e última tentativa. As APIs e a interface de revisão manual permanecem as mesmas. Sem dependências, fila ou novo agendador. O scheduler e o pipeline existentes já executam importações completas.

## Fora do escopo

Reidentificar ou separar homônimos locais, unificar pseudônimos, escolher entre candidatos conflitantes, atualizar valores já preenchidos, enriquecer séries/livros, incluir novos provedores ou associar autores não observados nas contribuições atuais. Evidências históricas não são apagadas automaticamente quando um livro deixa o catálogo.

## Critérios de aceite e validação

- ID de contribuição decodificado sem quebrar payloads antigos; livros novos e existentes fornecem evidências sem criar leituras duplicadas.
- Duplicação por livro não repete consulta; passagem parcial não enriquece; falha de evidência não produz correspondência incompleta.
- Homônimos, IDs ausentes e identidade compartilhada ficam para revisão. Conflito posterior oculta só o perfil automático, conservando os dados.
- Perfil manual preservado, inclusive confirmação concorrente ao download; automático preenche apenas lacunas e respeita intervalo de tentativa. Erros de foto/provedor não interrompem os livros.
- 40 testes backend passaram, sem skips: novos fluxos e regressões Hardcover, perfil manual, armazenamento e PostgreSQL com V1–V81. ktlintFormat, compilação e bootJar passaram. Sem alteração de frontend ou contrato HTTP.

Publicação solicitada na versão 1.27.0, tag v1.27.0 e imagens 1.27.0/latest, com implantação no Mordor e acompanhamento da importação dos autores existentes.

## Auditoria dos autores existentes antes da implantação

Consulta de leitura ao banco e às contribuições do catálogo atual: 158 autores locais, 173 registros de livro no Hardcover, 154 autores observados com correspondência única, nenhum conflito e nenhum nome remoto sem correspondência local. Quatro autores não aparecem nas contribuições atuais e permanecem preservados sem vínculo automático. Eduardo Foresti (local 19) corresponde ao ID 605110; Akira Toriyama (local 28), ao ID 201201. Nenhum perfil estava enriquecido antes da implantação. Não foi necessário separar homônimos ou fundir cadastros.

A identificação automática continua baseada em evidências persistidas, sem reescrever a deduplicação local por nome. Esta entrega não transforma o ID externo na chave primária dos autores nem resolve exceções por busca aproximada.
