# FoxVoice 2.18 / BotCraft 4.32 / MineSync 8.1 — voz direta (UDP), ponto a ponto e ligação direta

| Caminho | Quando | Quem faz a proximidade |
|---|---|---|
| UDP direto (launcher → motor → porta UDP 24464 do MineSync) | servidor com MineSync 8.1+ e porta aberta | o MineSync |
| Central (WSS) | porta UDP fechada, site, Android sem UDP | a central |
| Ponto a ponto (WebRTC, canal de dados sem reenvio) | servidor sem MineSync | quem ouve (posição pelo canal) |
| Ligação do Zap | sempre que der WebRTC; senão central | — (volume cheio) |

**UDP** (`launcher/engine/lib/vudp.js` ⇄ `minesync/src/net/foxsrv/voice/UdpVoice.java`):
- `HELLO 'F''V' 1 tlen(u16) tíquete iv(12) GCM(k, {n, ts}, aad "FVH1")` → `WELCOME 'F''V' 2 sid(u32) iv(12) GCM(k, hello, aad "FVW1")`
- `DATA 'F''V' 3 sid(u32) ctr(u64) GCM(k, quadro, aad = os 15 bytes do cabeçalho)`, nonce = `dir('C'|'S') 'F' 'V' 1 ctr(u64)`; anti-replay com janela de 64
- `ERR 'F''V' 5 código` (1 sessão perdida → HELLO de novo · 2 tíquete de outro servidor · 3 vencido · 4 fora do jogo)
- tíquete = AES-256-GCM(chave do servidor, `{n: nick, k: chave da sessão, e: validade (12 h)}`); a chave do servidor vai do MineSync para a central no `cfg` das posições (`udp_port`, `udp_key`, `udp_host`)
- quadros dentro: os mesmos do WebSocket (1 JSON · 2 microfone · 3 alguém perto)

**Central ⇄ MineSync:** `10 + uuid + seq + flags + opus` (quem fala pelo UDP → quem está só na central) · `11 + uuid + seq + opus` (quem fala pela central → launchers no UDP). Linha de posição ganhou `a[9]` = 1 (launcher ligado direto).

**Ponto a ponto:** `{t:'room', key}` → `{t:'mesh', list}`; `{t:'rtc', to, d, call?}` → `{t:'rtc', from, d, call?}`; `{t:'p2p', call, on}`; `{t:'tunnel', url}`. Sem central: correio do motor (`lib/vmesh.js`) por broadcast UDP 24465 (rede local) e try cloudflare (`POST /p2p`), mensagens com HMAC da sala.

**Reprodução:** AudioWorklet `mb-play` (launcher) / `fv-play` (site): fila por falante, folga 2–10 quadros, repete o último quadro mais baixo até 3× (emendado), acelera 3% quando a fila cresce, rampas de 5 ms, limitador suave.

---

# FoxVoice 4.12 — ponte com o Simple Voice Chat, voz no navegador e chamadas

## O que foi feito

| Parte | Arquivo |
|---|---|
| Central (roteamento, ponte SVC, navegador, chamadas) | `central/botcraft/lib/central/voice.js` |
| Rotas HTTP da central | `central/botcraft/api/botcraft.js` (bloco da voz, bloco `/api/voice/web` e `/api/voice/call/*` depois de `/api/voice/stats`, bloco `/api/launcher/call/*` depois de `/api/launcher/wallet/server`) |
| MineSync — ponte com o SVC (reflexão) | `minesync/src/net/foxsrv/voice/SvcBridge.java` (novo) |
| MineSync — FoxVoice (envio/recebimento) | `minesync/src/net/foxsrv/voice/FoxVoice.java` |
| MineSync — config | `minesync/resources/config.yml` → `VoiceChat.FoxVoice.SvcBridge: true` |
| Navegador — motor de áudio | `central/serverhub/site/voice.js` (novo, `window.FoxVoiceWeb`) |
| Navegador — tela | `central/serverhub/site/app.js` (rota `#/voice`, `viewVoice()`), `index.html` (menu "🎙️ Voice"), `style.css` (classes `.v*`) |
| Site do servidor | `central/botcraft/site/s.html` (link "🎙️ Voice" → `/serverhub/#/voice`) |
| Launcher — rotas de chamada | `launcher/engine/lib/callroutes.js` (novo) |

`Sync.java` **não foi alterado**: a ponte nasce dentro de `FoxVoice.start()` (que já roda no `onEnable`).

## Formato do áudio

SVC e FoxVoice usam o **mesmo Opus**: 48 kHz, mono, quadros de 20 ms (960 amostras). Conferido no jar
`voicechat-bukkit-2.6.20` e no `voice.html` do launcher (`AudioEncoder` opus 48000/1 canal/`frameDuration: 20000`).
Como cada pacote Opus se descreve sozinho (byte TOC), os pacotes passam **sem decodificar/recodificar** nos dois
sentidos — não foi preciso usar `createDecoder()/createEncoder()` do SVC.

## Protocolo (WebSocket binário; byte 0 = tipo)

Já existiam: `1` JSON · `2` (cliente → central) `seq u16 + opus` · `3` (central → cliente) `idx u16 + vol u8 + pan i8 + seq u16 + 0 + opus` · `4` (central → MineSync) `uuid(16) + opus` (gravação).

Novos:

| Tipo | Sentido | Formato | Uso |
|---|---|---|---|
| `5` | MineSync → central | `uuid(16) + flags(u8) + opus` | microfone de quem usa o mod SVC. flags bit0 = sussurro (a central usa metade do alcance) |
| `6` | central → MineSync | `uuid(16) + flags(u8) + opus` | voz de quem fala pelo launcher/navegador, para tocar no SVC. flags bit0 = também gravar (aí o tipo 4 não é mandado) |

Campos novos no JSON:

- `pos` (MineSync → central): cada linha ganhou `a[8]` = `1` se o jogador está com o mod do SVC **instalado, conectado e ligado**. `cfg` ganhou `svc` (ponte pronta) e `svc_dist` (distância de voz do SVC).
- `hello` (central → MineSync): `svc_bridge: 1`.
- Cliente (launcher/navegador) recebe também: `{t:'note', e:'svc_active'}` (o microfone foi ignorado porque o mod está conectado), `{t:'call', call}` (chamadas), `{t:'error', e:'launcher_active'}` (navegador recusado), `{t:'where', server, name, ingame, svc, speak, listen, server_voice}` (só o navegador, a cada 2 s). `hello` ganhou `name, ingame, svc, kind`. `peers` ganhou `svc:true` (falante do mod) e `web:true`.
- O launcher ignora mensagens que não conhece → **nada quebra no launcher atual**.

Endpoints novos:

- WS `/botcraft/voice/web?t=…` — navegador (mesmo protocolo do launcher; começa **mudo**, só ouvindo, até mandar `{t:'state', mute:false}`).
- `GET /botcraft/api/voice/web` (cookie da conta) → `{ nick, ingame, server, launcher_active, svc, enabled }`.
- `POST /botcraft/api/voice/web` (cookie) → `{ token, url, nick, server, cfg }` ou `not_in_game` / `launcher_active` / `nick_required` / `voice_disabled`.
- Chamadas — launcher (`x-fl-token`): `POST /botcraft/api/launcher/call/start {nick}`, `POST …/call/answer {id, accept}`, `POST …/call/end {id}`, `GET …/call/state`. Navegador (cookie): os mesmos em `/botcraft/api/voice/call/*`.
- `B.voice.positions(sid)` → `[{ name, uuid, w, x, y, z, at }]` (só leitura, atualizados nos últimos 15 s).

## Regras (eco, duplicação, permissões)

- **SVC → launcher/navegador**: só voz de **proximidade**. O MineSync descarta: jogador em grupo NORMAL ou ISOLADO (grupo ABERTO passa, como no próprio SVC), quem desligou o voice chat no mod (`isDisabled`), espectador, evento cancelado por outro plugin. O SVC só dispara o evento para quem tem `voicechat.speak`; a central confere de novo pela permissão amostrada.
- **launcher/navegador → SVC**: canal de ENTIDADE do SVC preso ao jogador que fala (o som sai dele e o acompanha), na distância de voz do SVC, categoria de volume **"MineBot / Navegador"** (`foxvoice`) no menu do mod. O filtro tira o próprio falante, quem tem o FoxVoice aberto, quem não tem `voicechat.listen` e quem está em grupo ISOLADO. A central só manda o tipo 6 se houver alguém com o mod perto (economia de banda).
- **Sem eco**: o que veio do SVC nunca volta ao SVC (só áudio de launcher/navegador vira tipo 6); ninguém recebe a própria voz (a central compara o nick).
- **Mod + launcher ao mesmo tempo (caminho escolhido)**: a pessoa **fala pelo mod** (o microfone do launcher/navegador é ignorado e o cliente recebe `note: svc_active`); **ouve** quem usa o mod pelo mod (a central não manda o áudio do SVC para o launcher dela) e quem usa launcher/navegador pelo launcher/navegador (o MineSync não toca esses no mod para ela). Para falar pelo launcher, ela precisa desligar o voice chat no mod (ou não ter o mod).
- **Navegador × launcher da mesma conta**: o launcher tem prioridade. Navegador aberto + launcher conecta → o navegador cai (`replaced`). Launcher conectado → o navegador é recusado (`launcher_active`). Assim não há ciclo de derrubadas (o launcher religa sozinho a cada 4 s).
- **Navegador só com o personagem online**: o token só sai se o nick da conta aparece nas posições de um MineSync conectado (últimos 10 s). Se sair do jogo, para de ouvir/falar (sem posição); se trocar de servidor, segue sozinho.
- **Limites**: até 60 quadros/s por falante (balde de 120) — launcher, navegador e SVC; quadro Opus até 1400 bytes; fila de envio do MineSync limitada (1500 quadros, descarta o que atrasar).
- `voicechat.speak` / `voicechat.listen` valem igual ao FoxVoice de antes. Config da central: `voz.web: false` desliga só o navegador.

## Chamadas (WhatsApp do celular)

- `ringing` (30 s, depois `no_answer`) → `active` → `ended` (motivo `hangup | declined | canceled | no_answer`; o estado `ended` aparece em `call/state` por 15 s).
- Uma chamada por pessoa (`busy` / `peer_busy`). A chave é o nick. `peer_offline` se quem recebe não está com a voz ligada nem jogando.
- Em `active`, a voz de cada lado vai **só** para o outro (volume 255, centro), sem proximidade e **sem** as permissões `voicechat.*`: a central manda `{t:'perm', speak:true, listen:true}` aos dois clientes na hora (o `voice.html` só manda áudio com `canSpeak`) e devolve as permissões do servidor no fim. Quem estiver perto não ouve. Durante a chamada, a pessoa continua ouvindo a proximidade normalmente.
- Ao tocar, se quem recebe está num servidor com MineSync, sai um aviso no jogo: `B.phone.push(sid, nick, 'notify', {title:'📞 Chamada', text})` (B.phone é buscado na hora — ele nasce depois da voz).
- O áudio da chamada passa pela **conexão de voz do launcher** (`voice.html` escondido), então funciona com o celular fechado — **desde que a voz esteja ligada** (`cfg.voice.on`). As respostas de start/answer/state trazem `voice_connected`. Se vier `false`, o celular deve ligar a voz: `POST voice/settings {on:true}` (rota que já existe no motor), ou o motor passa `ensureVoice` para `createCallRoutes`.
- Motor: no `engine.js`, espalhar nas ROUTES:
  ```js
  ...require('./lib/callroutes').createCallRoutes({ api, loggedIn: () => loggedIn(), L }),
  ```
  (opcional: `ensureVoice: async () => { /* mesmo efeito de 'POST voice/settings' {on:true} */ }`).
- Rotas do motor: `'POST call/start' {nick}`, `'POST call/answer' {id, accept}`, `'POST call/end' {id}`, `'GET call/state'`.
- O navegador (`#/voice`) também liga/atende (precisa estar com a voz do navegador ligada, que exige o personagem online).

## Navegador (ServerHub → "🎙️ Voice", `/serverhub/#/voice`)

- Mostra conta/personagem e servidor (ou "entre em um servidor"), botão grande **Ouvir**, microfone opcional (**desligado / aperte para falar / aberto** com porta de ruído), botão grande de segurar para falar (toque) e Espaço no PC, medidores de entrada e saída, volume geral e por jogador, quem está falando perto (launcher, navegador ou mod SVC), e o cartão de chamada.
- Decodifica/codifica com **WebCodecs** (`AudioDecoder`/`AudioEncoder`, Opus). Sem WebCodecs Opus, a página mostra que o navegador não serve (não há decodificador Opus no servidor e nenhuma lib pura em JS foi adicionada).
- O microfone usa o AudioContext na taxa nativa do aparelho e reamostra para 48 kHz (linear), o que evita o erro do Firefox com taxas diferentes. Captura por AudioWorklet (ou ScriptProcessor se não houver).
- **HTTPS é obrigatório para o microfone** (e para o AudioWorklet). O áudio só começa com um toque (regra dos navegadores, principalmente no celular).

## Versões / requisitos

- Central: BotCraft com este `voice.js`. Num recarregamento do plugin a instância antiga é trocada sozinha (detecta a falta de `callStart`), mas os launchers conectados caem e religam.
- MineSync: este código (6.0.0). SVC Bukkit/Paper com a API 2.x — conferido contra `voicechat-bukkit-2.6.20` (métodos: `getSenderConnection`, `getOpusEncodedData`, `isWhispering`, `Group.getType`, `createEntityAudioChannel`, `setFilter`, `flush`, `setCategory`, `registerVolumeCategory`, `getVoiceChatDistance`). Métodos opcionais que faltarem numa versão antiga são pulados (ex.: sem `setFilter` pode haver áudio duplicado para quem tem mod + launcher; sem `Group.getType` todo grupo é tratado como fechado).
- Navegadores: Chrome/Edge/Opera 94+ e Chrome Android têm `AudioDecoder`/`AudioEncoder` com Opus. Firefox 130+ desktop tem WebCodecs. **Safari/iOS: não verificado** — versões antigas não têm `AudioEncoder/AudioDecoder`; a página testa com `isConfigSupported` e avisa.

## Não verificado (sem servidor real)

1. Ponte SVC num servidor de verdade: o registro de um **segundo** `VoicechatPlugin` (`foxsrv_foxvoice`) junto do hook antigo (`foxsrv_minesync`), o `initialize` receber já o `VoicechatServerApi` (há o reforço via `VoicechatServerStartedEvent`), e o som do canal de entidade sair certo no cliente do mod.
2. Folia: `SvcBridge` lê `GameMode` e chama a API do SVC fora da thread da região (só leitura/envio de rede — o próprio SVC faz igual), não testado em Folia.
3. Qualidade/latência no navegador (jitter buffer fixo ~80 ms; sem RNNoise — usa o supressor do navegador), e Safari.
4. Compilação do MineSync: o `javac` de todo o `src/` (com o pacote `uber` já completo) passa **sem erros**. O `compilar.sh` só falha no empacotamento porque falta o `MineSync.jar` base na pasta `minesync/` (o `jar xf ../MineSync.jar`). Com o base de `/root/w481/out/MB/minesync/MineSync/MineSync.jar`, o jar sai normalmente (testado numa pasta à parte). O `config.yml` continua YAML válido.
5. A lógica da central foi testada com um teste local (servidor HTTP + clientes WebSocket simulando MineSync, launcher, navegador): proximidade, mudo inicial do navegador, mod+launcher, SVC → launcher/navegador, sem eco, limite de taxa, `positions()`, chamadas (tocar, ocupado, atender, volume cheio, ninguém perto ouve, fim), launcher derrubando o navegador.
