# Movimentos e estilos de obras

Spec aprovada: seleção múltipla com autocomplete de lista inicial e termos do acervo, entrada livre, normalização e filtro por pertencimento. Novos uploads enviam movimentos separadamente; editar sincroniza imagens remotas automaticamente. Falhas preservam os dados locais e registram pendência para retry.

Migrar movement em um item sem separar vírgulas. Preservar tags pessoais e referências compartilhadas no Visual Library; limites geram conflito explícito. Dados antigos sem procedência não são apagados automaticamente. Sem classificação automática, hierarquias, traduções ou sincronização de outros metadados.

Implementação: migration V67, movimentos JSONB, revisão e estado de sincronização por obra, endpoint de retry, atualização externa após commit, compare-and-set da revisão. Novo contrato de tags vinculadas no Visual Library com procedência. Autocomplete usa datalist nativo e chips removíveis com tokens existentes.

Validação: 42 testes de backend relacionados à alteração, typecheck e build do frontend; 43 testes e build do Visual Library. Smoke com PostgreSQL e os dois serviços reais confirmou correção de várias imagens, preservação de tags pessoais/compartilhadas, conflito de limite sem perder a edição, indisponibilidade e retry após reinício. Navegador em 390 e 1280 px confirmou sugestões, entrada livre, deduplicação, remoção, inclusão do termo ainda digitado ao salvar, persistência e limpeza da seleção, sem overflow ou exceções.

Após salvar os metadados, uma tentativa adicional de envio de imagens mantém esses campos desabilitados para não descartar alterações silenciosamente. A entrega exige atualizar também o Visual Library para o contrato de tags vinculadas; a API anterior da versão 0.6.0 não fornece essa atualização.

Publicação autorizada pelo usuário: Media Pulse 1.2.0 e Visual Library 0.7.0, com commit e push em ambos os repositórios.
