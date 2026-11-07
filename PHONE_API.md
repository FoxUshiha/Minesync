# Contrato do celular novo (MineBot Launcher 3.0) — para quem escreve um app

O celular é `engine/ui/phone.html` (casca) + `engine/ui/phone/core.js` + `engine/ui/phone/core.css`.
Cada app é um arquivo `engine/ui/phone/apps/<id>.js`, carregado como `<script src="phone/apps/<id>.js">`,
que só chama `Phone.register({...})`. Sem módulos ES, sem dependências.

```js
Phone.register({
  id: 'uber',                       // único, [a-z0-9]
  name: 'UberCraft',
  icon: '🚕',                       // emoji OU string SVG ('<svg …>…</svg>')
  color: 'linear-gradient(145deg,#111,#333)',   // fundo do ícone
  order: 60,                        // posição na grade (menor = antes)
  dock: false,                      // true = fica na barra de baixo da tela inicial
  async available(ctx) { return true; },        // opcional: false esconde o ícone
  async badge(ctx) { return 0; },               // opcional: número no ícone (0/null = nada)
  open(ctx, params) { return el; },             // devolve o elemento raiz da tela do app
});
```

`ctx` (o mesmo para todos os apps):

| | |
|---|---|
| `ctx.api(path, body?)` | `fetch('/app/api/' + path)`; GET sem `body`, POST com `body` (JSON). Resolve com o JSON; rejeita com `Error` (`e.code`, `e.message` já em português quando o motor traduz). |
| `ctx.h(tag, attrs, ...kids)` | cria elemento. `attrs`: `class`, `style`, `on<evento>`, `value`, `checked`, `disabled`, outros viram atributos. Texto SEMPRE por nó de texto (nunca innerHTML com dado de usuário). |
| `ctx.bar(title, sub?, ...acoes)` | barra superior do app (com botão voltar). `acoes` = elementos (botões) à direita. |
| `ctx.page(barEl, bodyEl)` | monta uma tela (barra + corpo rolável). Devolve o elemento. |
| `ctx.push(el)` | abre uma subtela por cima (o "voltar" do Android volta). |
| `ctx.back()` | volta uma tela (fecha o app se for a primeira). |
| `ctx.home()` | volta para a tela inicial. |
| `ctx.toast(msg, bad?)` | aviso rápido. |
| `ctx.confirm(title, text, okLabel?)` | `Promise<boolean>`. |
| `ctx.prompt(title, label, value?)` | `Promise<string|null>`. |
| `ctx.sheet(title, items)` | menu de baixo; `items: [{label, icon?, value}]` → `Promise<value|null>`. |
| `ctx.openWeb(url, title?)` | abre no app Navegador do celular (aba nova). |
| `ctx.openApp(id, params?)` | abre outro app. |
| `ctx.spin()` | elemento "carregando". |
| `ctx.empty(text)` | elemento de lista vazia. |
| `ctx.head(nick, px?)` | URL da cabeça do Minecraft do nick. |
| `ctx.fmt.money(n, dec?)`, `ctx.fmt.ago(ms)` | formatação. |
| `ctx.state` | `{ logged, nick, server: {host, port} | null, profile }` (atualizado sozinho). |
| `ctx.on(evento, fn)` | `'resume'` (o celular abriu de novo), `'server'` (mudou de servidor). |

Classes CSS prontas (core.css): `list`, `item` (linha com `.av` avatar, `.grow`, `.meta`), `card`, `btn` (+ `pri`, `ok`, `bad`, `w` = largura toda, `sm`),
`field` (rótulo + input), `seg` (abas segmentadas, botões com `.on`), `chips`, `chip`, `pill`, `mut`, `small`, `row`, `col`, `grid2`, `tabs`.
Tema Android (Material 3), escuro, variável `--acc` por app: `ctx.page` aceita `{ accent:'#hex' }` como 3º argumento.

## Rotas do motor
Arquivo `engine/lib/<nome>routes.js` exportando `create<Nome>Routes({ api, siteBase, loggedIn, curSrv, nick, L })`
que devolve `{ 'GET x/y': async (body, query) => …, 'POST x/z': async (body) => … }`.
`api(p, body?, {timeout?})` chama a central (`/botcraft/api/<p>`) com o token do launcher.
`curSrv()` → `{ srv, h, port }` (servidor do jogo aberto; `h` vazio = nenhum). O `engine.js` espalha essas rotas em `ROUTES`.

## Central → MineSync do servidor
Em `botcraft/lib/central/phone.js`: `B.phone.ask(sid, nick, kind, payload, ms)` (espera a resposta do MineSync) e
`B.phone.push(sid, nick, kind, payload)` (sem resposta). No MineSync, `net.foxsrv.site.PhoneLink.handle()` recebe
`{ id, nick, kind, ...payload }` e responde com `replyData(tok, id, JsonObject)`. Cada recurso novo usa um `kind` próprio
(ex.: `uber`, `bank`, `voice`) e um `op` dentro do payload.
Servidor do jogador na central: `await B.launcher.voiceTarget(ssn, host ? {host, port} : null)` → `{ sid, name } | null`.

## 2.17.1 — SpotyCraft (launcher, Android e site)
| Rota do celular | Central | O que faz |
|---|---|---|
| `GET spoty/list` | `GET /api/launcher/spoty/list` | músicas da conta `{ tracks:[{id,title,artist,mime,size,dur,game,plays,at}], used }` |
| `POST spoty/upload` `{data,name,dur,voice?}` | `POST /api/launcher/spoty/upload` | envia (≤10 MB); `voice` = WAV mono para o jogo |
| `POST spoty/voice` `{id,data}` | `POST /api/launcher/spoty/voice` | a cópia para o jogo depois |
| `POST spoty/rename` / `POST spoty/delete` | idem | renomear / apagar |
| `GET spoty/src?id=` | launcher: cache temporário (`spoty-cache`, 40 min) · site: `GET /api/launcher/spoty/url` (link assinado 3 h) | o áudio para tocar no aparelho |
| `POST spoty/play` `{id,volume,local}` | `POST /api/launcher/spoty/play` | toca no jogo (MineSync kind `spoty`, Simple Voice Chat) |
| `POST spoty/stop` | `POST /api/launcher/spoty/stop` | para no jogo |
| `POST camera/save` `{data}` | (launcher/Android) | salva uma cópia na galeria (editor de fotos) |

## 2.17.2 — Notificações, vínculo da conta, login nos sites
| Rota do celular | Central | O que faz |
|---|---|---|
| `GET notif?limit=&before=&since=` | `GET /api/launcher/notif` | `{ items:[{id,kind,icon,title,text,app,read,at}], more, unread }` |
| `POST notif/read` `{all}`/`{ids}` · `POST notif/delete` | idem | marcar lidas / apagar |
| `GET acct` | `GET /api/launcher/acct` | `{ nick, links, requests, unlinked }` |
| `POST acct/confirm` `{id, ok}` · `POST acct/code` · `POST acct/unlink` `{server_id}` | idem | confirmar/recusar, código `/sync FL…`, desvincular |
| `GET web/sso?url=` | `POST /api/launcher/web` | endereço de uso único que abre o site do sistema já logado (cookie só dentro do celular) |

## 2.17.6 — Feedback em tickets e atualização do launcher
- `POST feedback` `{ text, rating?, category?, images?: [dataURL…] }` → `{ id }` (até 3 imagens PNG/JPEG/WEBP de 1 MB; o motor junta `source`, `version` e `platform`: Windows pelo build, Android com o aparelho).
- `GET feedback/mine` → `{ items, admin, keep_days: 7 }` (cada item: `status`, `unread`, `msgs`, `images`, `last_at`, `expires_at`, `os`, `avatar`).
- `GET feedback/thread?id=` → `{ item, first_images, messages: [{ admin, author, text, images, at }], mine, admin }` (dono ou admin supremo).
- `POST feedback/reply` `{ id, text, images? }` (dono). Admin: `GET feedback/admin`, `POST feedback/admin/update` `{ id, status?, reply?, images? }`, `POST feedback/admin/delete`.
- Notificação `kind: 'feedback'`, `app: 'feedback'`, `ref: 'fb:<id>'` → o celular abre `openApp('feedback', { open: id })`.
- Canal de atualização (admin supremo): `GET updates/admin`, `POST updates/upload/start|chunk|finish|cancel`, `POST updates/publish|version|delete`.
- Motor do launcher (Windows): `GET update/status`, `POST update/check|download|install`, `POST update/auto { on }`.

## 2.17.7 — Jus (justiça dos servidores) e Gov › Meu perfil
- Rotas do motor `jus/*` → plugin Jus do site (`/jus/api/*`):
  - **GET:** `servers home profile arrests players penal config roles lawyers disputes dispute/get bos bo/get procs proc/get archive archive/get fines log card avatar env`;
  - **POST:** `penal/save config/save roles/save roles/delete members/set dispute dispute/decide bo bo/handle proc proc/move proc/archive sentence/preview sentence arrest release fine fine/cancel print`.
- Arquivo local: `jus/local`, `jus/local/get`, `jus/local/save`, `jus/local/delete`.
- `api('/outro-plugin/...')`: caminho com `/` na frente vai para a raiz do site, não para `/botcraft/api`.
- Notificações do Jus: `app: 'jus'`, `ref: 'proc:<id>' | 'bo:<id>' | 'arrest:<id>'` → `openApp('jus', { open })`.
- `window.JusCards.{drawCard, printCard, saveCard, avData}`: cartões de 128 px (mapa) ou 512 px (salvar). O app Gov usa para a identidade.
- Gov: `GET gov/me` (perfil e identidade de qualquer conta).
