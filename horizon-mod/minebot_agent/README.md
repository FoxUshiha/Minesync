# MineBot Agent (mod nativo do Horizon)

Faz o Horizon (o Minecraft em Rust) falar a mesma "língua" do BotAgent do Minecraft Java do MineBot. Com ele,
**todas as automações da BotCraft, a câmera livre / visão 3D e o minimapa** funcionam no Horizon, pelo mesmo
caminho do Java (o peer do MineBot não precisa saber qual jogo está aberto).

## Como instala
Sozinho: o MineBot Launcher leva esta pasta em `engine/horizon/mods/minebot_agent` e, ao abrir o Horizon pela
primeira vez (ou depois de uma atualização do Horizon), roda

    horizon-launcher native-build <esta pasta> --trust-native-code

O launcher do Horizon baixa o Rust dele e compila o jogo com o mod (a 1ª vez demora alguns minutos; as outras são
rápidas). Se a compilação falhar, o MineBot abre o Horizon normal (automações leves) e mostra o motivo.

## Segurança
- Só liga quando o jogo foi aberto pelo MineBot (variáveis `MINEBOT_AGENT` e `MINEBOT_AGENT_TOKEN`). Aberto de
  outro jeito, o Horizon fica exatamente como o original.
- Escuta só em `127.0.0.1`, com token (cabeçalho `X-Token`), compara o token em tempo constante e recusa pedidos de
  navegador (cabeçalho `Origin`).
- Respeita o "No AI" do Horizon: com ele ligado, nenhum comando chega ao jogo.
- As ações usam os comandos de controle do próprio Horizon (os mesmos do `horizon-ctl`), com as regras do jogo.

## Arquivos
| Arquivo | O que é |
|---|---|
| `mixins/minebot_agent/horizon-mixin.toml` | O pacote: 2 módulos e 2 ganchos (`native_frame`, `native_mods`) |
| `mixins/minebot_agent/runtime.rs` | API HTTP, JSON, formatos do BotAgent (também a biblioteca Cargo desta pasta, com os testes) |
| `mixins/minebot_agent/glue.rs` | O lado do jogo: seções do mundo, entidades, jogadores, registros, comandos |
| `frame.rs`, `list.rs` | Os dois ganchos |

Testes (sem o Horizon): `cargo test`.

## O que ainda não existe no Horizon
Editar placas pelo bot, sons, mapas de item, cliques de inventário THROW/SWAP/CLONE, mudar o FPS e esconder a janela
(`/selftest` lista). O resto do BotAgent responde no mesmo formato.

Licença: MIT.
