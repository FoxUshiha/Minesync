# MineBot Agent: o mod do MineBot para o Horizon (Rust)

## Para que serve
Faz o Horizon responder, dentro do jogo, a mesma API HTTP local do BotAgent do Minecraft Java. Com ela, tudo do
MineBot que antes era "só no Java" funciona no Horizon:
- as automações da BotCraft na sua conta;
- a câmera livre / visão 3D;
- o minimapa e o mapa grande;
- a experiência da IA.

## Como liga
1. O MineBot abre o Horizon com `MINEBOT_AGENT=<arquivo>`, `MINEBOT_AGENT_TOKEN=<token>` e `MINEBOT_NICK`.
2. No primeiro quadro, o mod escuta em `127.0.0.1:<porta livre>` e grava `{port, token, pid, agent:"minebot-horizon"}`
   no arquivo.
3. O launcher confere o `/ping` com o token e passa a tratar o Horizon como o Java (`game.setExternal`, `selfbot.json`).
   - No Android, o motor faz o mesmo com `game.attach`.

## Por dentro
- **Leituras grandes no fio do jogo, com limite de tempo por quadro** (`glue.rs`): seções 16³ do mundo no formato
  run-length do BotAgent, com a luz; entidades; jogadores; registros.
- **Ações pelos comandos de controle do próprio Horizon** (os mesmos do `horizon-ctl`), com as regras do jogo:
  `key`, `mouse`, `look`, `turn`, `look-at`, `attack`, `interact`, `interact-entity`, `break`, `select`,
  `click-slot`, `drop`, `chat`, `respawn`, `close`, `connect`, `disconnect`, `render-distance`, `screenshot`, `quit`.
- **Rotas do BotAgent atendidas:**
  - `/ping`, `/selftest`;
  - `/state`, `/tick`, `/entities`, `/players`, `/registry`, `/sections`, `/block`;
  - `/inventory`, `/container`, `/chat`;
  - `/keys`, `/release`, `/look`, `/turn`, `/lookat`, `/click`, `/swing`, `/attack`, `/interact`, `/use`,
    `/releaseuse`, `/useon`;
  - `/dig`, `/digstop`, `/dig/status`;
  - `/slot`, `/invclick`, `/invclicks`, `/drop`, `/chat/send`;
  - `/respawn`, `/closescreen`, `/connect`, `/title`, `/disconnect`, `/quit`, `/render`, `/ungrab`, `/frame` (PNG).
- **Ainda sem equivalente no Horizon** (o `/selftest` lista):
  - editar placas;
  - sons, mapas de item e placas lidas;
  - cliques THROW/SWAP/CLONE;
  - FPS e esconder a janela (respondem ok, sem efeito).

## Segurança
- Desligado se o jogo não foi aberto pelo MineBot.
- Só `127.0.0.1`, com token (`X-Token`) comparado em tempo constante.
- Pedido com `Origin` (navegador) é recusado e nenhum cabeçalho CORS é enviado.
- No máximo 32 conexões e pedidos de até 64 KiB.
- Respeita o "No AI" do Horizon: com ele ligado, nenhum comando chega ao jogo.

## Compilação
- **Windows:** sozinho, pelo `horizon-launcher native-build` (o launcher do Horizon baixa o Rust oficial).
  - O pacote vai sem versão presa, então compila no Horizon que o jogador tem.
  - Se falhar, o MineBot tenta na versão em que o mod foi escrito (`f8bfa06…`).
- **Android:** o `tools/patch_horizon.ps1` coloca os dois módulos no `game.rs` e o gancho no `native_frame`.
- **Testes:** `cd horizon-mod/minebot_agent && cargo test` (sem precisar do Horizon).
