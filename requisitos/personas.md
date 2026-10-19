# Personas — Central de Cobrança e Boletos

Pessoas, datas e decisões são **fictícias**. As personas mantêm interesses, linguagem e tensões consistentes ao longo das cinco sessões.

---

## Marina Azevedo — Product Owner de Cobrança
- **Papel:** facilita as reuniões, traduz negócio ↔ tecnologia e compila as user stories.
- **Objetivos:** MVP claro, entregável e rastreável.
- **Jeito de falar:** objetiva, recapitula no fim de cada tema ("Fechado.", "Vou preservar essa diferença").
- **Viés:** simplifica detalhes sob pressão — por isso a compilação final pode omitir nuances; **a transcrição manda**.

## Roberto Nunes — Gerente de Produto (patrocinador)
- **Papel:** decide prioridade e escopo; aprova o MVP.
- **Objetivos:** impacto, custo e experiência do cliente.
- **Jeito de falar:** direto, pergunta "o que volta pro cliente?", "isso entra agora?".
- **Viés:** adia infraestrutura pouco visível (retry, DLQ, auditoria) e corta escopo. Precisa ser convencido com impacto de negócio.

## Daniel Prado — Arquiteto de Soluções
- **Papel:** desenha a solução técnica.
- **Objetivos:** robustez e desacoplamento (aceite assíncrono, fila, cache, idempotência, DLQ, outbox).
- **Jeito de falar:** didático; explica o "porquê" com analogias para o pessoal de negócio.
- **Viés:** tende a antecipar complexidade; gosta de padrões "de livro".

## Sandra Lima — Coordenadora de Operações
- **Papel:** representa atendimento, backoffice e os clientes emissores (empresas).
- **Objetivos:** status rápidos e confiáveis; menos chamados repetidos.
- **Jeito de falar:** traz **casos concretos** ("acabei de emitir e não aparece").
- **Viés:** prioriza a dor imediata do atendimento; pouca paciência com termos técnicos.

## Caio Menezes — Especialista de Registro
- **Papel:** representa a integração com a instituição registradora.
- **Objetivos:** estados, confirmações e rejeições **precisos**; nenhum boleto "pronto" antes do registro.
- **Jeito de falar:** preciso, usa vocabulário do setor (título, convênio, espécie, linha digitável).
- **Viés:** vê tudo pela ótica do contrato com a registradora; pode subestimar a experiência do cliente emissor.

## Téo Mendonça — Segurança e Antifraude
- **Papel:** garante rastreabilidade e detecção de abuso.
- **Objetivos:** eventos de domínio e trilha de auditoria; **antifraude nunca bloqueia o fluxo principal**.
- **Jeito de falar:** curto, orientado a risco.
- **Viés:** quer registrar tudo — tensiona com a minimização de dados da Dra. Helena.

## Dra. Helena Sasaki — Compliance e Privacidade
- **Papel:** valida LGPD, acesso, auditoria e retenção.
- **Objetivos:** finalidade, base legal, minimização, acesso legítimo, retenção formal.
- **Jeito de falar:** formal e cautelosa; **não aceita prazos nem bases legais inventados** na reunião.
- **Viés:** conservadora; prefere "validar antes" a decidir na hora.

---

### Quem participa de qual sessão

| Persona | S1 | S2 | S3 | S4 | S5 |
|---|:-:|:-:|:-:|:-:|:-:|
| Marina | ✔ | ✔ | ✔ | ✔ | ✔ |
| Roberto | ✔ | ✔ | ✔ | ✔ | ✔ |
| Daniel | ✔ | ✔ | ✔ | ✔ | ✔ |
| Sandra | ✔ | ✔ | ✔ | ✔ | — |
| Caio | ✔ | ✔ | ✔ | ✔ | ✔ |
| Téo | — | — | — | ✔ | ✔ |
| Dra. Helena | — | — | — | — | ✔ |
