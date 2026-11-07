# FoxBridge — entrar em servidores sem IP fixo (MineBot 2.19 · MineSync 8.2 · BotCraft 4.33)

## Peças

| Onde | Arquivo | Papel |
|---|---|---|
| Servidor (MineSync) | `net/foxsrv/bridge/FoxBridge.java` | **Agente**: conexão de SAÍDA com a central (`wss://<site>/botcraft/fbridge/agent`, autenticada pela sessão do servidor). Cada stream vira um socket para `127.0.0.1:<porta do servidor>`. Manda o `info` (porta, IPs da rede local, Cloudflare, versão, jogadores). |
| Servidor (MineSync) | `net/foxsrv/bridge/CfMc.java` | **Cloudflare opcional**: `cloudflared tunnel --url tcp://127.0.0.1:<porta>` (quick) ou `cloudflared tunnel run --token …` (permanente). Baixa o binário certo (Windows/Linux, x64/arm) do GitHub oficial. |
| Servidor (MineSync) | `net/foxsrv/bridge/BridgeWeb.java` + `resources/web/config.html` | **Site local** de configuração (porta 24480): link com chave secreta no console e no `/minesync config`. |
| Central (BotCraft) | `lib/central/fbridge.js` | **Relay**: agentes + sessões dos launchers, troca de ids de stream, limite de banda, tíquetes de 2 min. |
| Central (BotCraft) | `api/botcraft.js` | `POST /api/launcher/fbridge/open` (tíquete + caminhos), `GET /api/fbridge/info?sid=` (público, sem IPs), `GET /api/fbridge/stats` (supremo). |
| Central (BotCraft) | `lib/central/launcher.js` | O apelido `<id>.foxbridge` é reconhecido no "entrar" (login automático, voz, celular, carteira). |
| Launcher (PC/Android) | `engine/lib/fbridge.js`, `engine/lib/wsc.js` | **Proxy local**: porta fixa por servidor (40000–59999, a mesma em todo PC), escolha do caminho com ping real do Minecraft, relay multiplexado. |
| Site | `modules/plugins/servers` | A lista global (`/servers/`), convites, curtidas, topo pago. |

## Caminho escolhido (cada conexão do jogo)

1. **Rede local** — os IPs privados que o agente anunciou (`FoxBridge.Lan`), ping de 0,9 s.
2. **IP público** — `Server.JavaIp` do MineSync ou o IP registrado do servidor, ping de 2,5 s.
3. **Cloudflare do plugin** (PC) — `cloudflared access tcp --hostname <host> --url 127.0.0.1:<porta>` e ping de 12 s.
4. **Relay da central** — se o servidor permitir (`FoxBridge.Relay`).

A escolha vale 5 min ou até falhar (aí tenta de novo, caindo para o relay). Nos caminhos 1–3 a central não fica no meio do tráfego.

## Protocolo (os dois lados da central)

Quadro binário: `[tipo u8][stream u32 BE][dados]`

| tipo | nome | sentido |
|---|---|---|
| 0 | JSON (hello, info, ping/pong, msg) | ambos |
| 1 | ABRIR (central → agente leva `{nick, ip}`) | launcher → agente |
| 2 | DADOS | ambos |
| 3 | FECHAR | ambos |
| 4 | ACK (u32 = bytes já entregues) | ambos |
| 5 | ABERTO | agente → launcher |

- **Crédito**: cada lado manda no máximo `janela` bytes (padrão 256 KB) sem ACK por stream — nada acumula na central.
- **Banda**: `fbridge.kbps_por_sessao` (padrão 8000). Com o balde no vermelho, a central segura os ACKs: o outro lado espera, sem perder dados.
- **Limites**: `max_sessoes_por_servidor` (300), `max_streams_por_sessao` (16), `max_agentes` (5000).
- **Tíquete**: 24 bytes aleatórios, 2 min, uso único, amarrado à conta (nick) e ao servidor.
- **Cifra**: TLS do site (wss). Em servidor `online-mode=true` o próprio Minecraft cifra a conexão de ponta a ponta.

## Configuração do MineSync (`config.yml`)

```yaml
FoxBridge:
  Enabled: true      # lista global + Jogar sem IP fixo
  Relay: true        # permitir o relay da central
  Lan: true          # anunciar os IPs da rede local
  TargetHost: ""     # onde o servidor escuta ("" = server-ip ou 127.0.0.1)
  TargetPort: 0
  MaxStreams: 512
Cloudflare:
  Enabled: false
  Mode: "quick"      # quick | token
  Token: ""          # túnel permanente (segredo!)
  Hostname: ""       # Public Hostname TCP → localhost:<porta>
ConfigSite:
  Enabled: true
  Port: 24480
  Bind: "0.0.0.0"
```

## Limites conhecidos (honesto)

- **IP de quem entra pelo relay**: o servidor vê `127.0.0.1` (como em qualquer túnel). O agente guarda o IP real por conexão (`FoxBridge.realIp(...)`) para quem quiser usar; bans por IP do próprio Minecraft não enxergam o IP real.
- **Cloudflare no Android**: o app não roda o `cloudflared`; no celular os caminhos são rede local, IP público e relay.
- **P2P com furo de NAT** (TCP hole punching) não foi feito: sem IP/porta aberta, o caminho é Cloudflare (se o dono ligar) ou o relay.
