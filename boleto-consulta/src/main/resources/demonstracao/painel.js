"use strict";
const $ = id => document.getElementById(id);
let boleto = null, ultimoPedido = null, timer = null, geracao = 0, ocupado = false;
let evidencias = new Map(), respostas = [];
const ajuda = {
  A: "A registradora simulada aprova. O registro final e os eventos são persistidos de verdade.",
  R: "Controle DEMO-R- escolhe rejeição somente no modo demonstração. Não é erro de transporte.",
  F: "Controle DEMO-F- torna a simulação indisponível. Artemis faz retry/backoff e encaminha à quarentena."
};
function texto(tag, valor, classe) {
  const el = document.createElement(tag);
  el.textContent = valor;
  if (classe) el.className = classe;
  return el;
}
function status(mensagem) { $("status").textContent = mensagem; }
function botoes() {
  ["emitir", "novo", "cenario"].forEach(id => $(id).disabled = ocupado);
  ["repetir", "conflitar"].forEach(id => $(id).disabled = ocupado || !ultimoPedido);
  ["consultar", "atualizar"].forEach(id => $(id).disabled = ocupado || !boleto);
  $("parar").disabled = !timer;
}
function novo() {
  const endereco = {logradouro: "Rua de Teste, 1", cidade: "Brasilia", uf: "DF", cep: "70000000"};
  const hoje = new Date().toISOString().slice(0, 10);
  const vencimento = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);
  $("pedido").value = JSON.stringify({
    convenio: "123456",
    numero_controle_cliente: `DEMO-${$("cenario").value}-${crypto.randomUUID().slice(0, 12)}`,
    beneficiario: {nome: "Empresa Sintetica", tipo_documento: "CNPJ", numero_documento: "00000000000000", endereco},
    pagador: {nome: "Pessoa Sintetica", tipo_documento: "CPF", numero_documento: "00000000000", endereco},
    valor_nominal: 150.00, data_emissao: hoje, data_vencimento: vencimento, especie: "DM"
  }, null, 2);
  $("scenario-help").textContent = ajuda[$("cenario").value];
}
async function http(url, opcoes = {}) {
  const correlation = crypto.randomUUID();
  const response = await fetch(url, {...opcoes, headers: {
    "Content-Type": "application/json", "X-Correlation-ID": correlation
  }, signal: AbortSignal.timeout(12000)});
  const raw = await response.text();
  let body;
  try { body = raw ? JSON.parse(raw) : null; }
  catch { body = {resposta_nao_json: raw.slice(0, 2000)}; }
  $("correlacao").textContent = response.headers.get("X-Correlation-ID") || correlation;
  return {status: response.status, body, correlation: $("correlacao").textContent};
}
function resposta(titulo, resultado) {
  respostas.unshift({titulo, ...resultado, hora: new Date().toLocaleTimeString()});
  respostas = respostas.slice(0, 30);
  $("responses").replaceChildren(...respostas.map(item => {
    const details = document.createElement("details");
    details.append(texto("summary", `${item.hora} · ${item.titulo} · HTTP ${item.status}`),
      texto("small", `correlation_id: ${item.correlation}`),
      texto("pre", JSON.stringify(item.body, null, 2) ?? "(sem corpo)"));
    details.open = item === respostas[0];
    return details;
  }));
}
function parar(mensagem) {
  if (timer) clearTimeout(timer);
  timer = null;
  geracao++;
  if (mensagem) status(mensagem);
  botoes();
}
function node(nome, lista, alerta = false) {
  const card = document.querySelector(`[data-node="${nome}"]`);
  card.className = lista.length ? (alerta ? "warning" : "observed") : "";
  card.querySelector("p").textContent = lista.length ? lista[lista.length - 1].detalhe : "Sem evidência nesta janela.";
}
function render() {
  const lista = [...evidencias.values()].sort((a, b) => a.instante.localeCompare(b.instante) || a.id.localeCompare(b.id));
  const etapas = nomes => lista.filter(r => nomes.includes(r.etapa));
  node("aceite", etapas(["ACEITE_PERSISTIDO", "IDEMPOTENCIA", "CONFLITO"]));
  node("fila", etapas(["COMANDO_PUBLICADO"]));
  node("registro", etapas(["COMANDO_RECEBIDO", "REGISTRADORA", "RESULTADO_PERSISTIDO", "PROCESSAMENTO_CONCLUIDO", "FALHA_PROCESSAMENTO"]),
    etapas(["FALHA_PROCESSAMENTO"]).length > 0 && !etapas(["RESULTADO_PERSISTIDO"]).length);
  node("evento", etapas(["EVENTO_PUBLICADO", "EVENTO_RECEBIDO"]));
  node("cache", etapas(["CACHE_HIT", "CACHE_MISS", "CACHE_PREENCHIDO", "CACHE_INVALIDADO"]));
  $("timeline").replaceChildren(...lista.map(r => {
    const li = document.createElement("li");
    li.append(texto("time", new Date(r.instante).toLocaleTimeString()), texto("strong", `${r.servico} · ${r.etapa}`),
      texto("p", r.detalhe), texto("small", `correlation_id: ${r.correlacao || "não disponível nesta fronteira"}`));
    return li;
  }));
  if (!lista.length) $("timeline").append(texto("li", "Sem rastros retidos para este UUID. Verifique perfil, reinícios e limite da janela."));
}
async function atualizar(token = geracao) {
  const id = boleto;
  const result = await http(`/demonstracao/api/fluxos/${id}`);
  if (token !== geracao || id !== boleto) return;
  if (result.status !== 200) {
    resposta("Diagnóstico indisponível", result);
    throw new Error(`Diagnóstico retornou HTTP ${result.status}`);
  }
  const fontes = result.body;
  const avisos = [];
  for (const nome of ["emissor", "registro"]) {
    const fonte = fontes[nome];
    avisos.push(texto("li", `${nome}: ${fonte.disponivel ? "diagnóstico acessível" : fonte.erro}`));
    for (const r of fonte.dados?.rastros || []) evidencias.set(r.id, r);
  }
  for (const r of fontes.consulta.rastros) evidencias.set(r.id, r);
  while (evidencias.size > 6000) evidencias.delete(evidencias.keys().next().value);
  avisos.push(texto("li", "consulta: diagnóstico local acessível"));
  const q = fontes.quarentena;
  const card = document.querySelector('[data-node="quarentena"]');
  card.className = q.dados?.encontrada ? "warning" : "";
  card.querySelector("p").textContent = q.disponivel ?
    (q.dados.encontrada ? "Mensagem deste UUID observada no broker (browse, sem consumir)." :
      `Não observada entre ${q.dados.examinadas} mensagens examinadas.${q.dados.limite_atingido ? " Limite de 200 atingido; ausência inconclusiva." : ""}`) :
    q.erro;
  avisos.push(texto("li", `quarentena: ${q.disponivel ? "consulta real ao broker" : q.erro}`));
  $("fontes").replaceChildren(...avisos);
  render();
}
async function consultar(token = geracao) {
  const id = boleto;
  const result = await http(`/boletos/${id}`);
  if (token !== geracao || id !== boleto) return;
  resposta("Consulta do boleto", result);
  $("estado").textContent = result.status === 200 ? result.body.status :
    `HTTP ${result.status} (não é um novo estado de negócio)`;
}
function acompanhar() {
  const token = geracao, fim = Date.now() + 45000;
  const tick = async () => {
    try {
      await consultar(token);
      if (token !== geracao) return;
      await atualizar(token);
      if (token !== geracao) return;
      if (Date.now() >= fim) { parar("Acompanhamento pausado após 45 s. Use os botões para continuar observando."); return; }
      status("Acompanhando HTTP, rastros e quarentena reais. Você pode pausar a qualquer momento.");
      timer = setTimeout(tick, 1500);
      botoes();
    } catch (err) {
      if (token === geracao) parar(`Acompanhamento interrompido: ${err.message}. Nenhuma etapa foi presumida como sucesso.`);
    }
  };
  timer = setTimeout(tick, 100);
  botoes();
}
async function emitir(tipo) {
  parar();
  let pedido;
  try {
    pedido = tipo === "novo" ? JSON.parse($("pedido").value) : structuredClone(ultimoPedido);
  } catch (err) { status(`JSON inválido: ${err.message}`); return; }
  if (tipo === "conflito") pedido.valor_nominal = Number(pedido.valor_nominal) + 1;
  ocupado = true; botoes();
  try {
    const result = await http("/demonstracao/api/emissoes", {method: "POST", body: JSON.stringify(pedido)});
    resposta(tipo === "conflito" ? "Conflito solicitado" : tipo === "repetir" ? "Repetição" : "Emissão", result);
    if (result.status === 202 && result.body?.identificador) {
      if (boleto !== result.body.identificador) {
        evidencias.clear();
        $("fontes").replaceChildren();
        node("quarentena", []);
      }
      boleto = result.body.identificador;
      ultimoPedido = structuredClone(pedido);
      $("uuid").textContent = boleto;
      $("estado").textContent = `${result.body.status} (aceite original; consulte o resultado atual)`;
      render();
      status("HTTP 202: aceite persistido; iniciando observação, sem presumir registro.");
      acompanhar();
    } else {
      status(`Resposta real HTTP ${result.status}. Verifique o JSON abaixo; não criamos um resultado fictício.`);
      if (boleto) await atualizar();
    }
  } catch (err) { status(`Falha HTTP: ${err.message}. Se houve perda de resposta, repita o mesmo controle para evitar duplicação.`); }
  finally { ocupado = false; botoes(); }
}
async function manual(acao) {
  parar();
  ocupado = true; botoes();
  try { await acao(); status("Observação manual concluída. Acompanhamento automático pausado."); }
  catch (err) { status(`Falha na observação: ${err.message}`); }
  finally { ocupado = false; botoes(); }
}
$("novo").addEventListener("click", novo);
$("cenario").addEventListener("change", novo);
$("emitir").addEventListener("click", () => emitir("novo"));
$("repetir").addEventListener("click", () => emitir("repetir"));
$("conflitar").addEventListener("click", () => emitir("conflito"));
$("consultar").addEventListener("click", () => manual(async () => { await consultar(); await atualizar(); }));
$("atualizar").addEventListener("click", () => manual(() => atualizar()));
$("parar").addEventListener("click", () => parar("Acompanhamento pausado. Processamento no backend continua."));
novo(); botoes();
