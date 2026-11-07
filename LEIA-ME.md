# MineBot 2.23.2: 🔌 WebDesk que não cai, erros de banco e modais no celular

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher` | **WebDesk**: autentica na hora (não espera o ajudante do Windows), batimento a cada 20 s (percebe conexão morta e religa), volta sozinho depois de suspender o PC, não apaga o vínculo por um erro passageiro da central, não fica brigando com outro processo do mesmo vínculo · o motor não cai mais por uma promessa rejeitada (ex.: banco da central local fechando) · fila do banco da central local fecha sem erro | **2.23.2** |
| `site/modules/plugins/webdesk` | O PC não fica mais "offline" depois de a central recarregar/atualizar o módulo · erro de banco não desvincula · batimento | **1.1.0** |
| `site/modules/plugins/botcraft` | `no such table: sh_discord` corrigido (e `sh_dusers`, `whatsapp_users`, `adv_users` no banco certo) · recarga do plugin para todos os relógios e não gera mais erros de banco · a central não cai por promessa rejeitada | **4.39.1** |
| `site/modules/plugins/servers` | Portal de hosts no celular: modais sempre dentro da tela, com margem e rolagem | **1.6.1** |
| `site/modules/plugins/serverhub` | Banco compartilhado com a BotCraft · modais no celular | **1.3.2** |
| `android` | Leva a central embutida corrigida | **2.16.2** |

**Depois de copiar:** reinicie a central (o servidor do site) e atualize o launcher nos PCs que usam o WebDesk.

# MineBot 2.23.1: 🚑 correção — a central voltou a funcionar

| Pasta | O que mudou | Versão |
|---|---|---|
| `site/modules/plugins/botcraft` | Uma rota nova da 4.36 (`/api/hn/inst/:id/*`) era recusada pelo **Express 5** e derrubava **todas** as rotas da BotCraft (404 em tudo e 429 de tanto os apps repetirem). Corrigida, e agora uma rota recusada nunca mais derruba as outras | **4.37.1** |
| `site/modules/plugins/servers` | A mesma proteção contra rota recusada | 1.4.0 |
| `launcher` · `android` | Levam a central corrigida | **2.23.1** · **2.16.1** |

**Depois de copiar:** reinicie a central (o servidor do site) — não basta só trocar os arquivos com ela aberta.

# MineBot 2.23.0: 🌐 /server e /invite pela rede, botão de downloads e mods na central

| Pasta | O que mudou | Versão |
|---|---|---|
| `minesync` | **`/server <nome>`** com TAB dos servidores da rede (o mesmo JOGAR do portal, sem IP fixo) · **`/invite <jogador> [servidor]`** (vazio = este servidor; notificação, Zap, Discord, WhatsApp e botão [ENTRAR] no jogo) | **8.5.0** |
| `site/modules/plugins/botcraft` | Mods em `storage/assets/mods/<pasta>/` mais robusto (subpastas, ignora -sources, pastas novas como neoforge/quilt viram download) · modal sempre atualizado | **4.37.0** |
| `site/modules/plugins/servers` | Botão **⬇ Baixar launcher e mods** (modal) na lista · `/servers/api/ms/invite` | **1.4.0** |
| `site/modules/plugins/webdesk` | Downloads pelo mesmo modal da central | **1.0.5** |
| `launcher` · `android` | Levam tudo acima (MineSync 8.5.0 e a central embutida) | **2.23.0** · **2.16.0** |

# MineBot 2.22.2: ⌨️ MineSync 8.4.4 — comandos do plugin em branco e com TAB

| Pasta | O que mudou | Versão |
|---|---|---|
| `minesync` | `/skin`, `/skins`, `/login`, `/register`, `/mudarsenha`, `/codigo`… agora registrados no servidor: brancos e com sugestão no TAB (antes vermelhos e sem TAB) | **8.4.4** |
| `launcher` · `android` | Levam o MineSync 8.4.4 | **2.22.2** · **2.15.2** |

# MineBot 2.22.1: 🔑 MineSync 8.4.3 — permissões do LuckPerms funcionando

| Pasta | O que mudou | Versão |
|---|---|---|
| `minesync` | `/skin` e `/skins` obedecem a `minesync.skin` mesmo com o SkinsRestorer instalado (antes ele tomava o comando e ignorava a permissão do MineSync); app Gov liberado por `minesync.gov.admin`; `minesync.imgmap.trusted` (imagens sem aprovação); **`/minesync perms <jogador>`** mostra ✔/✘ de cada permissão | **8.4.3** |
| `launcher` · `android` | Levam o MineSync 8.4.3 (servidor embutido) | **2.22.1** · **2.15.1** |

# MineBot 2.22.0: 🛰 REDE DE HOSTS — a central controla tudo, a host só contribui com o PC

| Pasta | O que mudou | Versão |
|---|---|---|
| `host/MinesyncHost` | Painel local só para **contribuir** (ligado por padrão), **vincular a conta**, **IP/DNS/Cloudflare** e **recursos** (total, reserva do sistema, reserva do dono, parte da central, máximo por cliente). **Agente** ligado à central (sem IP fixo), servidores da central só para ver, **integridade** (sha256, violações), **runtimes por versão** baixados na hora e isolados (Java 8–25, Node.js 18–24, Python 3.10–3.13), tipo **Horizon** | **2.0.0** |
| `site/modules/plugins/botcraft` | **hostnet**: mercado, compra em COIN com garantia, painel do servidor pela central (arquivos/backups em fluxo), reputação, violações e suspensão, **backup diário da central** numa host de armazenamento, **migração**, **tickets** e **admin supremo** | **4.36.0** |
| `site/modules/plugins/servers` | `/servers/host.html` = portal de hosts (Contratar, Meus servidores, Minhas hosts, Suporte, Admin) | **1.3.0** |
| `launcher/MineBot-Launcher` · `android/MineBot-Android` | Levam o BotCraft 4.36.0 e o Servidores 1.3.0 (central embutida) | **2.22.0** · **2.15.0** |

**Configure na central** (`hostnet.escrow_ssn` = a conta que segura os pagamentos; ou pelo site em Admin › Configuração)
e marque pelo menos **uma host como armazenamento de backups** (Admin › Hosts). Troque a `GATEWAY_ADMIN_PASSWORD` se ainda não trocou.

# MineBot 2.21.4: 🦊 tudo pela rede (sem IP fixo) — portais, mod com Social/celular/notificações e correções do 26.3

| Pasta | O que mudou | Versão |
|---|---|---|
| `mods` (Fabric e Forge, 26.3) | **Corrigido**: nome/MOTD/fotos na lista "Servidores da rede", o celular dentro do jogo e as teclas (o 26.3 usa SDL). **Novo**: celular no navegador já logado, **Social** (convidar amigos para o servidor atual pela rede, convites, notificações), **avisos na tela**, **portais pela rede** (o mod abre a ponte quando você clica em JOGAR) | **1.5.1** |
| `minesync` | **Portais pela rede**: o JOGAR usa SEMPRE a ponte (launcher ou mod do jogador), nunca o IP declarado de um servidor da rede (`Portals.DirectFallback` para quem quiser o IP como último recurso) | **8.4.2** |
| `launcher` · `android` | Levam o MineSync 8.4.2. A lista de servidores já entra pela rede (`fbridge/join`: rede local → IP só se responder → relay) | **2.21.4** · **2.14.4** |

# MineBot 2.21.3: 🖥️ MinesyncHost 1.3.0 — host de verdade (contas, Java/Node.js/Python, backups, Cloudflare e conta da rede pelo site)

| Pasta | O que mudou | Versão |
|---|---|---|
| `host/MinesyncHost` | **1.2**: backups (limites por usuário, automático, sobrescrever um antigo no limite, travar, restaurar) e compactar/descompactar no explorador. **1.3**: **contas** com limites, permissões, tipos liberados, suspender; **criar servidor para uma conta**; **subusuários** com permissões por servidor; **tipos Java (.jar próprio), Node.js e Python**; **Inicialização** (arquivo principal, Java por servidor, flags, argumentos, variáveis, parar, "pronto quando", ligar se cair, npm/pip); **agendamentos**; **atividade**; **entrar/vincular com a conta da rede pelo site (só confirmar) ou usuário e senha da central**; **Entrar com a Cloudflare** (cria o túnel e o DNS sozinho) | **1.3.0** |
| `site/modules/plugins/botcraft` | O site confirma os pedidos do painel MinesyncHost (texto próprio no diálogo) | **4.35.2** |
| `launcher/MineBot-Launcher` · `android/MineBot-Android` | Levam o BotCraft 4.35.2 (central embutida) | **2.21.3** · **2.14.3** |

# MineBot 2.21.2: 🎮 o MOD sozinho entra na rede — servidores na tela inicial, sem IP fixo e voz de proximidade

| Pasta | O que mudou | Versão |
|---|---|---|
| `mods` (Fabric e Forge, Minecraft 26.3) | **Servidores da rede** na tela inicial (ícone, fotos, descrição, jogadores, busca) · **Entrar sem IP fixo** pela ponte FoxBridge do próprio mod (rede local → IP público → relay da central), com login automático · **Adicionar à lista** do Multijogador · **Voz de proximidade** sem o launcher (apertar para falar V ou detecção de voz, mudo, surdo, volumes, indicador no jogo) | **1.5.0** |
| `site/modules/plugins/botcraft` | Downloads › Mods com a descrição nova | **4.35.1** |
| `launcher/MineBot-Launcher` · `android/MineBot-Android` | Levam o BotCraft 4.35.1 (central embutida) | **2.21.2** · **2.14.2** |

O mod Horizon (Rust) continua 1.x: no Horizon, quem faz a ponte e a voz é o app **MineSync** (Android) / o launcher.

# MineBot 2.21.1: 🌉 a MinesyncHost faz a ponte da rede sozinha — Vanilla, Fabric, Forge e servidores externos (Horizon…) sem IP fixo

| Pasta | O que mudou | Versão |
|---|---|---|
| `host/MinesyncHost` | **Ponte da rede pela host**: todo servidor SEM o plugin MineSync (Vanilla, Fabric, Forge, Externo) é cadastrado na rede, mantém a sessão (MOTD/versão/jogadores pelo ping do Minecraft) e recebe os jogadores pelo relay da central — igual ao MineSync. Com o MineSync instalado, a ponte é do plugin (sem servidor duplicado). Novo tipo **Externo** (só a ponte para um servidor que já roda neste PC ou na rede local — Horizon, outro programa). Opcional: caminho direto pela rede local. Limite de banda aplicado | **1.1.0** |
| `minesync` | Portais: endereço 127.0.0.1/rede local nunca vira destino de transferência (esses entram pela ponte) | **8.4.1** |
| `site/modules/plugins/servers` | A lista não mostra mais 127.0.0.1/rede local como endereço público | **1.2.1** |
| `launcher/MineBot-Launcher` · `android/MineBot-Android` | Levam o MineSync 8.4.1 e o plugin de servidores 1.2.1 | **2.21.1** · **2.14.1** |

# MineBot 2.21.0: 🌀 portais de servidor · launcher agora se chama MineSync · MinesyncHost.jar (hospedagem) · Downloads com mods

| Pasta | O que mudou | Versão |
|---|---|---|
| `minesync` | **/server-portal place <servidor \| ip:porta \| random> [min]** — parede de quadros com ícone, nome, MOTD, descrição, até 5 fotos com setas e **JOGAR** (o jogador é transferido na hora; rodízio justo no random) · `accepts-transfers=true` no server.properties | **8.4.0** |
| `launcher/MineBot-Launcher` | Instalado aparece como **MineSync** (atalhos, janela, bandeja, desinstalar) — os arquivos na central continuam com o mesmo nome, a atualização automática segue igual · **eventos da central** (o "Jogar" do portal adiciona o servidor na lista do Minecraft e entra) | **2.21.0** |
| `android/MineBot-Android` | Nome **MineSync** · portais (mesmos eventos) | **2.14.0** |
| `host/MinesyncHost` | **NOVO** — a hospedagem leve: painel web, servidores (Paper, Purpur, Folia, Canvas, Spigot, Vanilla, Fabric, Forge), console, arquivos, limites, isolamento, Cloudflare, rede | **1.0.0** |
| `site/modules/plugins/botcraft` | `/api/ms/portal/go` + `/api/launcher/events` (long polling) · registro de hosts `/api/host/*` · Downloads em abas (Launcher, **Mods**, **Servidor** com MinesyncHost e tutorial) · `storage/assets/mods/<fabric\|forge\|horizon>/` | **4.35.0** |
| `site/modules/plugins/servers` | Botões **Downloads** e **Host** no topo · página **/servers/host.html** (Criar · Hospedar · Baixar) · `/servers/api/portal` | **1.2.0** |
| `site/storage/assets/mods/` | Pastas dos mods (coloque os .jar compilados dos mods aqui) | — |

**Portais:** pendure os quadros na parede (3x3 ou maior fica melhor), use `/server-portal place random 5` e clique num quadro. Quem clica em JOGAR com o launcher MineSync aberto é transferido na hora (até para servidores sem IP fixo); sem launcher, servidores com IP fixo transferem direto. O servidor de destino precisa de `accepts-transfers=true` (o MineSync 8.4 liga sozinho; reinicie uma vez).

# MineBot 2.20.1: 🔑 login automático pela rede local e pela FoxBridge (sem pedir senha) · "Adicionar" aparece na lista de servidores do jogo

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | Arma o login automático **na hora em que o jogo entra** num servidor FoxBridge (também pela lista do Minecraft ou Conexão direta) · responde ao pedido de tíquete dos servidores da **rede local** (porta 24471) · servidores adicionados entram no `servers.dat` de **todas** as instâncias Java (e voltam se sumirem) · link `minebot://add/<id>` | **2.20.1** |
| `android/MineBot-Android` | Mesmas correções do login automático (FoxBridge + rede local) | **2.13.1** |
| `minesync` | **Relay FoxBridge:** a central já confere a conta do launcher que abriu a conexão → login automático direto, conferido pela porta na entrada (conta diferente do nick = expulso) · **rede local:** pede o tíquete assinado ao launcher do jogador (desafio novo, preso a um IP do servidor) · confere tudo isso **antes** da central (mais rápido e funciona com ela fora) | **8.3.1** |
| `site/modules/plugins/servers` | "Adicionar" no site agora **adiciona** (abre o launcher já colocando o servidor na lista do Minecraft) | **1.1.1** |
| demais | Sem mudança desde a 2.20.0 | — |

**Por que pedia senha:** o login automático só era armado para o servidor que o launcher achava que você ia jogar (o do "Jogar"). Entrando pela lista do jogo ou por Conexão direta, o tíquete ficava preso a outro servidor. E pelo relay o servidor vê 127.0.0.1, então o MineSync não aproveitava a conta que a central já tinha conferido.

**Firewall (rede local):** no PC de quem joga, o Windows pode perguntar sobre a porta **24471** do launcher — permita em "Redes privadas". No PC do servidor, a porta **24480** (site local do MineSync) também ajuda.

# MineBot 2.20.0: 🕸 malha P2P (atualizações e vizinhos), Android atualizando de novo, login automático pela rede local, /mudarsenha com código, /minesync config já logado, perfil estilo Roblox, voz adaptativa

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | Aba **Atualizações** (separada) com notificações · **Servidores › Malha da rede** (mapa) · **Perfil** (skin, bio, seus servidores) · atualização pela **malha P2P** (vizinhos + central em pedaços, assinatura + SHA-256) · guarda as versões publicadas (Windows e Android) para os vizinhos · login automático entregue **direto ao plugin** · voz com folga adaptativa e som abafado pela distância | **2.20.0** |
| `android/MineBot-Android` | **Atualização automática corrigida** · aba **Atualizações** (Mais) com a malha e as notificações · Servidores › Malha da rede · Perfil · mesmas melhorias de login automático e voz | **2.13.0** |
| `site/modules/plugins/botcraft` | Canal de atualização do **Android (.apk)** com SHA-256 · **manifesto assinado** (Ed25519) · malha (`/api/launcher/mesh/hello`) · `/api/ms/oplink` · `/api/ms/pwreset/*` · `/api/ms/autologin/keys` | **4.34.0** |
| `site/modules/plugins/servers` | Aba **Perfil** (skin desenhada, bio, skins, seus servidores) · login pelo link do `/minesync config` | **1.1.0** |
| `minesync` | `/minesync config` → **link clicável já logado no site** (OP = a conta dele, criada na hora se não tinha; console = o dono) · **LocalAuth** (chaves públicas das contas guardadas no servidor: login automático sem central) · **/mudarsenha /changepass** com código de 2 etapas | **8.3.0** |
| `site/modules/central`, `site/modules/minecraft`, `webdesk`, `serverhub`, `jus`, `mods`, `horizon-mod` | Sem mudança desde a 2.19 (Horizon continua 0.7.7: o `horizon-android-0_7_7_4.zip` enviado é idêntico ao anterior) | — |

**🤖 Por que o Android parou de achar versões novas (corrigido):**
1. A central lia a versão do `.json` ao lado do APK **antes** da versão de dentro do APK — um `.json` velho fazia o celular achar que já estava na mais nova. Agora vale a de dentro do APK.
2. Um download interrompido no fim deixava um pedaço que a central respondia com "416" — o app ficava preso para sempre. Agora apaga e recomeça.
3. Agora o APK tem **canal próprio** igual ao do Windows: envie o `.apk` em Feedback › Atualizações (o mesmo botão do `.exe`), a versão é lida de dentro, publique — os celulares baixam e conferem o SHA-256.

**🕸 Malha P2P:** cada launcher (PC e celular) se anuncia na central, na rede local (UDP 24472) e para os vizinhos que já conhece. A central **assina** o manifesto de cada versão; o launcher guarda a chave da central e aceita a versão vinda de outro launcher só com assinatura certa + SHA-256. Baixar = pedaços de 2 MB de vários lugares ao mesmo tempo (os vizinhos com melhor reputação/ping + a central); arquivo ruim → o vizinho perde reputação e baixa de novo só da central. Depois de atualizar, o launcher **guarda** a versão e entrega aos outros. Com a central fora, os manifestos e os arquivos continuam circulando. Vizinhos vistos há mais de 24 h somem do mapa e não são repassados; depois de 7 dias saem da lista local.

**🗺 Malha da rede (mapa):** Servidores › Malha da rede (PC) e Servidores › Malha (celular): a central em cima como **portal do Nether** (aceso = conectada), você no meio (PC ou celular), os launchers e os servidores com MineSync em volta, ligados por fios, distância pelo ping; offline fica escuro. Zoom com a roda/pinça, arrastar para mover.

**🔑 Login automático sem central / na rede local:** o MineSync baixa da central a lista nick → chaves públicas das contas (a cada 10 min, guarda em `autologin-keys.json`). O launcher logado assina o tíquete e entrega **direto ao plugin** (site local do MineSync, porta 24480, `POST /al/arm`) além da central. Só quem está logado na conta dona do nick consegue.

**🔐 /mudarsenha (/changepass, /esqueciasenha):** manda um código de 6 dígitos para a conta dona do nick — notificação do launcher (com botão de confirmar), e-mail, DM do Discord e WhatsApp vinculados. Digita o código no chat (ou `/codigo 123456`) ou confirma no launcher → a senha deste servidor é apagada e pede `/register` de novo (ou entra pelo launcher logado, que registra sozinho).

**🌐 /minesync config:** manda no chat (clicável) e no console o link de **uso único (15 min)** para o site **já logado** — na página do servidor e no Perfil. OP sem conta: a conta é criada na hora, ligada ao nick (depois ele põe e-mail e cria a senha em Conta). Continua mostrando também o link do site local de rede (FoxBridge/Cloudflare).

**🎙 Voz:** folga adaptativa pela variação de chegada (40–280 ms, como o Discord/WebRTC) — ping alto ou rede instável não engasga; emenda sem estalo quando falta pacote; mais longe = mais baixo **e mais abafado**. O lado (esquerda/direita) segue o seu olhar. Simple Voice Chat continua compatível (ponte da 2.18).

**⚠️ Não compartilhe:** `storage/assets/update-signing.json` da central (a chave que assina as atualizações), `plugins/MineSync/autologin-keys.json`, `plugins/MineSync/config-site.key`, `mesh/` do launcher, além da lista de sempre. Lembrete: troque a `GATEWAY_ADMIN_PASSWORD` da moeda bitcoin.

---

# MineBot 2.19.0: 🌍 lista global de servidores (estilo Roblox), FoxBridge (entrar sem IP fixo), Cloudflare no MineSync, convites, WebDesk

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | Servidores › **Explorar** (lista global) com **Jogar** que entra até sem IP fixo, **Adicionar**, convites; aba **Host** separada; links `minebot://`; WebDesk com mouse de mão única | **2.19.0** |
| `android/MineBot-Android` | Abas **Servidores** (lista global) e **Host**; Jogar pelo FoxBridge; convites viram notificação do Android | **2.12.0** |
| `site/modules/plugins/servers` | **NOVO** — a lista global em `/servers/`: fotos, descrição, mapa, versão, curtidas, impulso, topo pago, convites | **1.0.0** |
| `site/modules/plugins/botcraft` | **FoxBridge**: agente dos servidores + relay (streams multiplexados, crédito/ACK, limite de banda) e o apelido `<id>.foxbridge` | **4.33.0** |
| `site/modules/plugins/webdesk` | Mouse de mão única (o eco atrasado do PC não volta o cursor) + botões **E** e **D** do mouse na barra | **1.0.4** |
| `site/modules/plugins/serverhub` | Bedrock só no plugin (texto dos campos) | **1.3.1** |
| `site/modules/central`, `site/modules/minecraft` | `minecraft/submodules/minecraft/minecraft.js` (embed do Discord), `central/api/site/index.html` e `central/submodules/admin/admin.js`: **sem Bedrock (IP/porta/NetherNet) no site e no Discord** | — |
| `minesync` | **FoxBridge** (conexão de saída com a central), **Cloudflare opcional** (try cloudflare ou túnel permanente, baixa o cloudflared sozinho), **site local de configuração** (`/minesync config`), registro automático `MineServ###` com senha aleatória | **8.2.0** |
| `mods`, `horizon-mod`, `jus` | Sem mudança | 1.4.0 · — · 1.0.0 |

**Para compilar:** extraia numa pasta **nova e vazia** e rode `COMPILAR-WINDOWS.bat`, `COMPILAR-ANDROID.bat` ou `COMPILAR-MODS.bat`. O MineSync: `minesync\COMPILAR.bat` (ou use o `MineSync-8.2.0.jar` pronto).

**🌍 Lista global de servidores (site `/servers/`, launcher Servidores › Explorar, Android Servidores):**
- Ordem: os **impulsionados com pagamento** (o mesmo boost do site: PIX, Bitcoin, cartão) no topo → os **mais ativos** (jogadores agora, depois entradas da semana) → os **mais curtidos**. Busca e filtros (Em alta, Jogadores, Curtidos, Mais jogados), abas **Meus** e **Convites**.
- Cada servidor: até **5 fotos**, descrição e tags (o dono edita), MOTD colorido, versão, jogadores, mapa, site do servidor, **IP para copiar** (quando tem IP público).
- **Jogar** (launcher): abre a porta local fixa do servidor e escolhe o melhor caminho — **rede local → IP público → Cloudflare do plugin → relay da central** — e abre o Minecraft (Java no PC; no Android, Horizon/Java pelo app). No site, Jogar abre o launcher (`minebot://join/<id>`) já na tela do servidor; o jogo só começa quando você confirma no launcher.
- **Adicionar**: o servidor fica salvo na lista do Minecraft (`127.0.0.1:<porta fixa>`) e a porta abre sozinha quando o launcher liga.
- **Convidar amigos**: vai pelo **Zap** (e no chat do jogo, se o amigo estiver online num servidor — com link clicável), **notificação da conta** (sino do site, launcher, aviso do Windows, notificação do Android), **DM do Discord** e **WhatsApp** vinculados. Aceitar = entrar no servidor. Limite de 40 convites por hora.

**🦊 FoxBridge (servidor sem IP fixo):** o MineSync liga **para fora** (`wss://<site>/botcraft/fbridge/agent`), sem abrir porta. O launcher pede um tíquete de 2 min (uso único) e cada conexão do jogo vira um stream multiplexado; controle de fluxo por crédito (janela de 256 KB + ACK) e limite de banda por sessão segurando os ACKs (nada se perde). Com um caminho direto funcionando, a central sai do meio. Detalhes em `FOXBRIDGE.md`.

**☁️ Cloudflare no MineSync (opcional, desligado por padrão):** `Cloudflare.Enabled: true` — modo **quick** (try cloudflare, sem conta) ou **token** (túnel permanente: hostname TCP → `localhost:<porta>`). O plugin baixa o `cloudflared` certo para o sistema e sobe o túnel junto com o servidor (enable/reload/restart). Configure tudo pelo **site local** do plugin: o link (com chave secreta) sai no console e no `/minesync config` (só OP/console).

**🆕 Servidor novo:** o MineSync se registra sozinho como **`MineServ###`** com **senha aleatória** (fica no `config.yml`) e já entra na lista global com o FoxBridge ligado.

**🖱 WebDesk:** o PC obedece a posição do site/launcher/app (cada movimento numerado; o PC aplica só o mais novo e, se atrasou, força o mouse de volta para a posição digital); só quando alguém mexe no mouse **físico** do PC (com você parado) o cursor virtual acompanha. Botões **🖱E** (toque = clique, segurar = arrastar) e **🖱D** na barra, nos modos mouse e toque.

**🚫 Bedrock fora do site e do Discord:** o IP/porta Bedrock (e o botão NetherNet) não aparecem mais no site, na lista pública nem no embed do Discord — continua só no plugin Minecraft.

**⚠️ Não compartilhe:** `plugins/MineSync/config-site.key` (chave do site local), o `Cloudflare.Token` do `config.yml`, `plugins/MineSync/cloudflared/`, `fbridge.json` do launcher, além da lista de sempre. Lembrete: troque a `GATEWAY_ADMIN_PASSWORD` da moeda bitcoin (está em texto puro).

---

# MineBot 2.18.0: voz direta (UDP) e ponto a ponto, MineSync que se atualiza sozinho

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | Voz nova (UDP direto no servidor, ponto a ponto, buffer sem estalo), host de servidor com o MineSync mais novo do site | **2.18.0** |
| `android/MineBot-Android` | A mesma voz nova; Horizon 0.7.7; conta Microsoft opcional também no Minecraft Java | **2.11.0** |
| `site/modules/plugins/botcraft` | Tíquetes da voz UDP, salas ponto a ponto, sinalização WebRTC, canal de atualização do MineSync | **4.32.0** |
| `site/modules/plugins/serverhub` | Voz do site: buffer novo e ligação direta (WebRTC) | **1.3.0** |
| `site/modules/plugins/jus`, `webdesk` | Sem mudança | 1.0.0 · 1.0.3 |
| `minesync` | Porta UDP de voz (ponte com o Simple Voice Chat), atualização automática | **8.1.0** |
| `mods` | Manda a posição do jogador ao launcher (voz ponto a ponto) | **1.4.0** |

**Para compilar:** extraia numa pasta **nova e vazia** e rode `COMPILAR-WINDOWS.bat`, `COMPILAR-ANDROID.bat` ou `COMPILAR-MODS.bat`. O MineSync: `minesync\COMPILAR.bat` (ou use o `MineSync-8.1.0.jar` pronto).

**🎙 Voz (launcher PC/Android, Zap e site):**
- **O caminho mais direto que existir**, nesta ordem:
  1. **UDP direto no servidor (MineSync 8.1+):** o launcher fala com a porta UDP do servidor (padrão **24464**, abra no firewall/roteador como a do Simple Voice Chat). O próprio MineSync faz a proximidade e a ponte com o Simple Voice Chat (o que o launcher fala toca no SVC e vice-versa). Áudio cifrado (AES-256-GCM) com um tíquete que a central entrega e o servidor confere sozinho — vale 12 h, então continua funcionando se a central cair.
  2. **Pela central** (como antes), quando a porta UDP está fechada ou para o site.
  3. **Ponto a ponto entre launchers** (WebRTC, UDP e cifrado), quando o servidor **não tem MineSync**: a posição vem do jogo aberto (mod, Horizon ou agente Java; PC e Android) e o volume/lado são calculados em quem ouve. A conexão é combinada pela central; sem ela, pela **rede local** (mesmo roteador) ou pelo **try cloudflare** de cada launcher.
- **Ligação do Zap:** direta entre os dois aparelhos (WebRTC) quando dá; senão pela central. **Em ligação você continua ouvindo quem está perto.**
- **Sem "chuvisco" e sem travar:** o som toca numa thread de áudio própria com folga que se ajusta sozinha (40–200 ms), disfarce de pacote perdido e emendas sem estalo. UDP: pacote perdido não segura os outros (no WebSocket/TCP segurava — era a "travada").
- **Qualidade:** voz Opus a **20 kbps** (limpa, sem som de rádio), música do SpotyCraft a **32 kbps**.
- Na aba **Voz** do launcher: chaves "Voz direta (UDP)", "Ponto a ponto" e "Try Cloudflare", e por onde a voz está indo agora.

**🧩 MineSync que se atualiza sozinho (8.1.0 em diante):**
- No site: **Feedback → Atualizações → MineSync (plugin dos servidores)** — envie o `MineSync.jar`. A central lê a versão do `plugin.yml`, confere o SHA-256 e publica.
- Os servidores perguntam a cada 30 min (`AutoUpdate.CheckMinutes`). Se a versão da central for **maior**, baixam, conferem tamanho, SHA-256 e `plugin.yml`, **apagam o MineSync.jar velho** e põem o novo em `plugins/MineSync.jar` (no Windows, com o jar em uso, vai para `plugins/update/`). Basta **reiniciar** o servidor.
- Aviso no console, para os OPs online (e quando um OP entra) e no **celular do launcher** de quem é dono, admin ou OP vinculado do servidor — de novo a cada 15 min, até reiniciar. `/minesync update` confere na hora.

**🖥 Host de servidor (launcher Windows e Android):** o servidor novo já nasce com a pasta `plugins` e o **MineSync mais novo publicado no site** (SHA-256 conferido; sem internet, o que vem no instalador). Ao ligar, confere de novo.

**📱 Android 2.11.0:** Horizon 0.7.7 (o `horizon_android\horizon-android-src.zip` já é o 0.7.7 com os ajustes do MineBot). **Conta Microsoft opcional também no Minecraft Java do app:** as versões vêm da central da FoxLand (que guarda tudo) ou da Mojang sem login; "Exigir conta Microsoft para baixar" volta a regra para a versão pública.

**⚠️ Não compartilhe:** `plugins/MineSync/voice-udp.key` (chave da voz do servidor), `config/minebot/autologin.bin`, além da lista de sempre.

---

# MineBot 2.17.12: launcher sem exigir conta Microsoft

| Pasta | O que tem | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | Conta Microsoft opcional (veja o LEIA-ME do launcher); traz a pasta `build\` que faltava | **2.17.12** |
| `site/modules/plugins` | botcraft, serverhub, jus e webdesk (as mesmas que vêm dentro do launcher) | BotCraft 4.31.3 |
| `minesync` | Só o `MineSync-7.2.0.jar` (o mesmo do servidor de dentro do launcher) | 7.2.0 |
| `mods` | Código dos mods Fabric/Forge | 1.3.3 |
| `horizon-mod` | Mod do MineBot para o Horizon | — |

**Para compilar o launcher:** extraia o zip numa pasta **nova e vazia** e rode `COMPILAR-WINDOWS.bat` (ou `launcher\MineBot-Launcher\COMPILAR.bat`).

**Não vem neste pacote:** o projeto Android (2.9.0) e o código-fonte do MineSync 7.2.0. Use os que você já tem.

---

# MineBot 2.17.8: 🔐 login automático assinado (chave da conta) e 🚕 Uber com o happy ghast funcionando

| Pasta | O que mudou | Versão |
|---|---|---|
| `site/modules/plugins/botcraft` | Login automático assinado: chaves públicas das contas, tíquetes do jogo aberto, rotas `/api/launcher/autologin/*` | **4.31.0** |
| `site/modules/plugins/serverhub` | Sem mudança | 1.2.6 |
| `site/modules/plugins/jus` | Sem mudança | 1.0.0 |
| `launcher/MineBot-Launcher` | Chave derivada da senha, tíquete assinado ao abrir o jogo/entrar no servidor, batimento a cada 60 s | **2.17.8** |
| `minesync/MineSync` | Aceita o tíquete assinado (qualquer IP), espera o tíquete no pré-login, cache curto; happy ghast do Uber corrigido | **7.1.0** |
| `android/MineBot-Android` | O mesmo login automático assinado | **1.10.8** |
| `mods` | O mesmo login automático assinado quando o mod é usado sem o launcher | **1.3.3** |

**Só os plugins do site:** o zip `MineBot-2.17.8-plugins-do-site.zip` tem `modules/plugins/botcraft`, `serverhub` e `jus`. Extraia na raiz do site e reinicie. Ele não traz `config.yml`, `data/`, `storage/` nem `node_modules/`.

**🔐 Login automático assinado (launcher, Android e mod → central → MineSync):**
- **Por que parava:** o servidor só reconhecia "veio pelo launcher" quando o IP que o launcher mostrava à central era **igual** ao IP que o servidor via. IPv4 × IPv6, CGNAT, proxy, túnel ou troca de IP do provedor quebravam o login automático.
- **Agora:**
  1. ao entrar na conta com a **senha**, o launcher deriva uma **chave privada Ed25519 da senha** (PBKDF2-SHA256, 200 000 rodadas). A senha não é guardada; a semente fica cifrada no aparelho (AES-256-GCM). A central só recebe a chave **pública**. Mesma senha → mesma chave em qualquer aparelho. Quem entrou pelo navegador (código) ganha uma chave aleatória;
  2. quando o jogo abre (ou entra em outro servidor), o launcher **assina** `{nick, servidor, hora, nonce}` e arma um **tíquete** na central; enquanto o jogo está aberto ele **renova a cada 60 s**. Jogo fechado → desarmado na hora (sem batimento, morre em 3 min);
  3. no pré-login o MineSync pergunta à central: o tíquete vivo da conta dona do nick vale **em qualquer IP**. Na primeira entrada ele fica **preso ao IP/UUID** que o servidor viu: outra pessoa com o mesmo nick, de outro IP, não entra.
- **Segurança:** assinatura com mais de 5 min, nonce repetido, pedido alterado, chave de outra conta ou nick que não é o da conta → recusado. Tíquete ainda livre só vale no servidor armado (ou em outro servidor do **mesmo dono**, para redes com proxy).
- **MineSync 7.1:** se um nick de conta ainda não foi reconhecido, espera até ~5 s no pré-login pelo tíquete (`Access.Launcher.WaitForTicket`), "não é do launcher" fica só 3 s em cache, tenta a central de novo quando ela demora, e com a central fora do ar mantém por 30 min a última resposta "é do launcher" do mesmo nick+IP. Corrigido: na rede local o login automático nunca valia na entrada.
- **Compatível:** o login pelo IP de antes continua funcionando para launchers e servidores antigos.
- **Mod sem o launcher (1.3.3):** faz o mesmo (chave derivada da senha no login do mod, batimento a cada 60 s).

**🚕 Uber — happy ghast:**
- **Corrigido:** dá para **pisar em cima** dele (colisão) e o piloto **controla** de novo.
- **Causa:** sem piloto o plugin desligava a "percepção" do ghast (`setAware(false)`). No Paper isso pula toda a IA do servidor, inclusive a parte do happy ghast que vira plataforma e que passa o controle ao primeiro passageiro.
- **Agora:** a IA fica sempre ligada. Para ele não sair andando sozinho, só os objetivos de passear e seguir são removidos. Ninguém mexe na velocidade dele enquanto há alguém em cima. O arreio continua sempre no lugar.

---

# MineBot 2.17.7: ⚖️ Jus — a justiça dos servidores (plugin novo do site), Gov › Meu perfil e contas da rede

| Pasta | O que mudou | Versão |
|---|---|---|
| `site/modules/plugins/jus` | **NOVO** plugin do site: tribunal, polícia, processos, BOs, código penal, calculadora de penas, contestações. Site em `/jus/` | **1.0.0** |
| `site/modules/plugins/botcraft` | Ponte para o Jus, Gov › Meu perfil, conta global (lista + login automático), celular do site com o app Jus | **4.30.0** |
| `site/modules/plugins/serverhub` | Sem mudança | 1.2.6 |
| `launcher/MineBot-Launcher` | App ⚖️ Jus no celular, Gov › Meu perfil para todos | **2.17.7** |
| `minesync/MineSync` | Prisão com distância, mapa impresso com linhas no item, arquivo de processos, contas da rede | **7.0.0** |
| `android/MineBot-Android` | App ⚖️ Jus e Gov › Meu perfil | **1.10.7** |
| `mods` | Sem mudança | 1.3.2 |

**Só os plugins do site:** o zip `MineBot-2.17.7-plugins-do-site.zip` tem `modules/plugins/botcraft`, `serverhub` e `jus`. Extraia na raiz do site e reinicie. Ele não traz `config.yml`, `data/`, `storage/` nem `node_modules/`.

**⚖️ Jus** (app no celular do launcher, do Android e do site; site em `/jus/`; no idioma do launcher):
- **Início, aberto a todos:**
  - situação no servidor: preso até quando, multas;
  - prisões **neste servidor** e o **total da rede**; o perfil Jus mostra as prisões de **todos** os servidores;
  - BOs que citam você e processos em seu nome;
  - contestar **multas** e **prisões** com até **10 imagens de 1 MB**;
  - abrir **boletim de ocorrência**;
  - **imprimir** o histórico (mapa na mão, que dá para entregar a outros jogadores) ou **baixar** a imagem; mais abaixo, as mesmas informações da rede inteira.
- **Cargos da justiça:** Ministro da Justiça (acesso total), Juiz, Promotor e Advogado, com a lista de quem está em cada um. Cada cargo tem as suas permissões e um grupo do jogo.
- **Polícia:**
  - contratar e demitir, com hierarquia;
  - cargos com permissões: prender, soltar, representar, multar, remover multa, contratar, demitir, criar cargos.
  - Ao contratar, o servidor dá o grupo (`lp user {player} parent add {group}`).
- **Advogados:** qualquer pessoa escolhe um e conversa pelo **Zap**.
- **Processos:**
  - abertos por advogados, promotores e juízes; o juiz vê os em aberto;
  - documento em estilo "carta", compatível com o celular, com até 10 imagens, movimentações, status e instância;
  - ficam **30 dias**, depois vão para o **armazenamento do servidor** pelo MineSync. De lá só abrem com a pessoa **online** no servidor, e o app guarda uma cópia **no aparelho**.
- **BOs:** o cidadão abre sozinho; o juiz ou a polícia abrem um processo a partir do BO ou arquivam. Sem atendimento, o BO some em **7 dias**.
- **Sentença (calculadora penal):**
  1. escolha o jogador, anexe processos, escolha o status, a instância e os crimes;
  2. ajuste a **régua de dosimetria de 1% a 10×**: o cálculo é automático;
  3. **Aplicar** registra, prende pelos comandos configurados e imprime na hora o **mapa da prisão** (dados e foto) na mão do juiz.
  - O dono pode exigir uma **distância máxima em blocos** entre quem aplica e o preso.
  - O mapa pode ser impresso de novo pelo histórico do jogador ou do servidor.
- **Código penal:** vem com **48 crimes** baseados na vida real, com a pena em **minutos** no lugar de meses. O dono (e o Ministro) adiciona, edita e remove crimes; é a calculadora do servidor.
- **Configuração:**
  - comandos e placeholders de prender e soltar (padrão CMI pelo console: `cmi jail {player} {min}m {jail}`, `cmi unjail {player}`) e de grupo — só o dono do servidor ou OP mudam;
  - distância máxima, online obrigatório, teto de pena, multas da sentença e instâncias;
  - histórico.
- As multas são as **multas do sistema** (Banco › Multas / Gov).

**Gov › Meu perfil (qualquer conta):** identidade com foto, nick, usuário, número de identidade, **final do SSN censurado**, **UUID**, WhatsApp, e-mail e Discord vinculados (mascarados), além das pendências (multas, prisão, processos, BOs). A identidade sai como **mapa** no jogo, com o UUID e os dados nas linhas do item, ou é salva na Galeria/baixada.

**Contas da rede (MineSync 7.0 + BotCraft 4.30):**
- Vinculou a conta num servidor, vale em **todos** os servidores com o MineSync.
- Cada servidor baixa a lista simples (**ID + nick**) de todas as contas FoxLand e guarda em `accounts-global.txt`. Assim o nick fica **reservado** até num servidor recém-criado e com a central fora do ar. A lista é atualizada a cada 10 min.
- **Login automático** em outro servidor: pelo launcher (como antes) ou pelo **mesmo IP** de um login confirmado em outro servidor da rede nas últimas 12 h, com o mesmo UUID. Esse servidor também fica vinculado sozinho.

**O que não deu para testar aqui:** a parte em C# (janela do Windows) não compila neste ambiente — compile com `COMPILAR-WINDOWS.bat`. O MineSync foi compilado e o desenho dos painéis foi conferido; a central, o motor e as telas foram testados.

# MineBot 2.17.6: painéis com editor profissional, Feedback em tickets e atualização automática

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | Feedback abaixo do WebDesk (tickets com imagens), 🔔 central de notificações, atualização automática com animação, mais leve | **2.17.6** |
| `site/modules/plugins/botcraft` | Painéis com layout **livre** (arrastar e redimensionar), Feedback em tickets (imagens, 7 dias), canal de atualização do launcher | **4.29.0** |
| `site/modules/plugins/serverhub` | Botão "✨ Editor profissional" e layout livre na aba Painéis | **1.2.6** |
| `minesync/MineSync` | Desenha o layout livre dos painéis (posição, tamanho e foto de cada botão) | **6.9.0** |
| `android/MineBot-Android` | Feedback em tickets com imagens e o nome do aparelho | **1.10.6** |
| `mods` | Sem mudança | 1.3.2 |

**Só os plugins do site:** o zip `MineBot-2.17.6-plugins-do-site.zip` tem só `modules/plugins/botcraft` e `modules/plugins/serverhub` — extraia na raiz do site (ou copie as duas pastas para `./modules/plugins/`) e reinicie. Ele não traz `config.yml`, `data/`, `storage/` nem `node_modules/`, então nada seu é sobrescrito.

**Painéis — editor profissional (BotCraft → Painéis, ou ServerHub → Painéis → ✨ Editor profissional):**
- Layout **✨ Livre**: cada botão fica **onde você quiser e do tamanho que quiser**. Na Prévia: toque para escolher, arraste para mover, puxe os cantos para o tamanho; setas do teclado ajustam (Shift = 4×, Alt = fino).
- Encaixe na grade (1 / 2,5 / 5 %), guia de centro, Centralizar, Largura toda, Frente/Trás, Duplicar, "Organizar em grade", Editar/Testar e Prévia grande.
- **Foto personalizada por botão:** foto de fundo do botão inteiro, foto em cima do texto ou ícone; cor do texto, borda, cantos, sombra e tamanho do texto. Ação "Só enfeite (sem clique)" para imagens e avisos.
- Cabeçalho, título, texto e imagem da página também se posicionam (liga/desliga, alinhamento, tamanho, cor).
- Até 24 botões no layout livre. Painéis antigos continuam iguais. Precisa do **MineSync 6.9** no servidor.
- Segurança: só quem administra o servidor (ou o admin supremo) edita e envia imagens; as imagens enviadas são só PNG/JPEG.

**Feedback em tickets:**
- Launcher: aba **💬 Feedback logo abaixo do WebDesk**. Site: `/botcraft/feedback.html`. Celular (launcher, Android e site): app Feedback. Servidor: `/minesync feedback <msg>`.
- Até **3 imagens de 1 MB** por mensagem (maiores são comprimidas antes de enviar). Só quem mandou e o admin supremo veem.
- Cada ticket mostra **origem** (servidor ou launcher), **versão** do plugin/launcher, **Windows 10 / Windows 11 / Android + aparelho**, **data e hora**, **usuário/nick** e **foto de perfil**.
- O admin supremo responde como num ticket (texto e imagens), marca novo/lido/resolvido e apaga. A pessoa pode responder de volta.
- A resposta aparece nas **notificações do launcher (🔔)**, no celular e como **notificação do Windows**. Clicar abre a conversa. Feedback de servidor: a resposta chega no chat do jogador.
- Tudo é apagado **7 dias depois da última mensagem** (imagens também).

**Canal de atualização (admin supremo: Feedback → ⬆️ Atualizações, no site e no launcher):**
1. Envie o instalador `MineBot_Launcher.exe` — a versão é **lida de dentro do .exe** (ou escreva outra).
2. Clique em **Publicar** (pode publicar com outra versão, ou só mudar a versão anunciada e as notas).
3. Os launchers perguntam à central de tempos em tempos; se a versão publicada for **maior**, baixam sozinhos em segundo plano, conferem o SHA-256 e avisam "Atualização pronta".
4. O usuário clica em **Atualizar agora** (no 🔔 do launcher ou no aviso do Windows): o launcher **fecha, mostra a animação de atualização, instala e abre sozinho**, com as mesmas configurações.
- "Baixar atualizações sozinho" vem **ligado** (Opções e 🔔). Instalar é sempre com o clique do usuário.

**Launcher mais leve:** nada de atualização na inicialização (1ª checagem 3–4 min depois), download direto para o disco com limite de velocidade (mais baixo com o jogo aberto), consultas à central espaçadas e só com a janela visível. "Servir a rede" continua **desligado por padrão**. Nenhuma função foi removida.

**O que não deu para testar aqui:** a parte em C# (janela do Windows: animação do instalador, clique no aviso do Windows) não compila neste ambiente — compile com `COMPILAR-WINDOWS.bat`. O resto foi testado (central, motor, telas do site e do launcher, MineSync compilado).

# MineBot 2.17.5: Download Launcher no site, Feedback e launcher mais leve

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | Aba **Feedback** na lateral (o admin supremo vê todos). "Ajudar a rede" desligado por padrão e menos memória | **2.17.5** |
| `site/modules/plugins/botcraft` | Botão **⬇ Download Launcher** com modal, feedbacks na central e a página `/botcraft/feedback.html` | **4.28.0** |
| `site/modules/plugins/serverhub` | O mesmo botão Download Launcher no menu | **1.2.5** |
| `minesync/MineSync` | `/minesync feedback <mensagem>` | **6.8.0** |
| `android/MineBot-Android` | App Feedback no celular | **1.10.5** |
| `mods` | Sem mudança | 1.3.2 |

**Botão "⬇ Download Launcher" no menu de cima do site (computador e celular):**
- Aparece no BotCraft, nos Painéis, nos sites dos servidores e no ServerHub, logado ou não, sempre com o nome "Download Launcher".
- Abre um modal com o **MineBot Launcher para Windows** (e o instalador completo, quando existe), o **Android (APK)** e o **plugin MineSync**, com versão e tamanho.
- O que ainda não estiver em `storage/assets` aparece como "Em breve".
- `#download` no endereço abre o modal direto.
- No celular, o modal sobe de baixo, com os botões largos.

**Feedback:**
- **Launcher:** aba **💬 Feedback** na lateral, vinculada à conta. Tem nota de 1 a 5, categoria e comentário, e mostra os feedbacks que você mandou com a resposta da equipe.
  - Vai junto a versão do launcher, o sistema, a conta e o servidor em que a pessoa está.
- **Celular** (launcher, Android e site): app **Feedback**.
- **Servidor:** `/minesync feedback <mensagem>` (permissão `minesync.feedback`, liberada para todos).
  - Chega em nome do servidor, com a versão do plugin, o software do servidor e o nick de quem mandou.
- **Admin supremo:** na aba Feedback do launcher (com um pontinho quando chega um novo) e em **`/botcraft/feedback.html`** no site (link "💬 Feedback" no menu).
  - Vê todos, filtra por novos, lidos, resolvidos e origem, busca e vê a nota média dos últimos 30 dias.
  - Pode marcar como lido ou resolvido, responder e apagar. A resposta chega como notificação no celular de quem mandou.
- **Anti-spam:** 6 por hora por conta e 4 por hora por jogador num servidor.

**Launcher mais leve (as funções continuam as mesmas):**
- **"Ajudar a rede"** (hospedar bots e IA da rede, FoxLand Peer, central local automática) **vem desligado**.
  - Quem estava com ele ligado pelo padrão antigo passa a desligado **uma vez**, ao atualizar.
  - Para ligar de novo: Opções › Geral.
- A janela oculta da voz só fica aberta com a voz ligada ou enquanto uma música do SpotyCraft toca.
- Janelas escondidas (na bandeja ou por trás do jogo) pedem ao WebView2 para liberar memória.
- O WebView2 abre sem os serviços de fundo da Edge (tradução, SmartScreen, compras, atualização de componentes) e com teto de memória do JavaScript.
- O motor (Node) tem teto de heap (384 MB) e coleta a memória mais cedo.

# MineBot 2.17.4: a música do SpotyCraft chega a quem está perto (voice chat do launcher E Simple Voice Chat)

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | O play no celular transmite a música pela voz do launcher, como uma fonte presa ao seu personagem | **2.17.4** |
| `site/modules/plugins/botcraft` | A central distribui a música para os launchers perto e manda ao MineSync tocar no Simple Voice Chat | **4.27.0** |
| `minesync/MineSync` | Canal de música no Simple Voice Chat (dobro da distância, categoria "SpotyCraft") | **6.7.0** |
| `android/MineBot-Android` | Igual ao launcher | **1.10.4** |
| `mods` | Sem mudança | 1.3.2 |

**Como funciona agora (o play no celular do launcher ou do Android):**
1. A página da voz do launcher baixa a música do cache, codifica em Opus de música (48 kbps) e transmite junto com a sua posição. Funciona até com a voz do launcher desligada: aí só a música vai, sem microfone e sem ouvir ninguém.
2. A central manda a música para:
   - quem está perto com o **voice chat do launcher, do Android ou do site**, no **dobro do alcance da voz**, mais baixa de longe e com o lado pela direção;
   - o **MineSync**, que toca no **Simple Voice Chat** num canal preso a você, com o **dobro da distância do SVC** e a categoria **SpotyCraft** no menu do mod (cada um baixa ou desliga).
3. Você ouve pelo celular. Quem ouve pelo launcher não recebe dobrado pelo SVC.
4. Pausar ou parar para para todo mundo. Pular para outro ponto da música faz o jogo acompanhar.

**Se não der pela voz** (página da voz fechada, ou o servidor sem a voz do MineSync), o launcher cai no caminho antigo: o MineSync baixa e toca no Simple Voice Chat. O app mostra **por que** não tocou no jogo, em vez de ficar em silêncio, e mostra por onde está tocando.

**Requisitos no servidor:**
- MineSync 6.7.0 com a voz ligada (`VoiceChat.FoxVoice`), que manda as posições.
- Para quem usa o mod do SVC, o plugin **Simple Voice Chat**.

**Testado de ponta a ponta:** central local + motor do launcher + a página da voz rodando no Chromium + um ouvinte com launcher a 40 blocos + um MineSync simulado com um jogador do SVC.
- Com a voz ligada e com a voz desligada, o ouvinte do launcher recebeu a música (mais baixa de longe) e o MineSync recebeu os quadros para o SVC, presos ao UUID do dono.
- Parar, tocar de novo do minuto 1 e o caminho de reserva também foram testados.

# MineBot 2.17.3: SpotyCraft funcionando de ponta a ponta (e a loja do celular)

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | Aceita enviar músicas de até 10 MB; pedido grande demais vira um erro claro | **2.17.3** |
| `site/modules/plugins/botcraft` | O id de cada pedido ao MineSync não é mais apagado (música no jogo, loja, Uber) | **4.26.1** |
| `minesync/MineSync` | Lê a música, o pedido da loja e a corrida pelos campos novos (e os antigos) | **6.6.1** |
| `android/MineBot-Android` | Igual ao launcher | **1.10.3** |
| `mods` | Sem mudança | 1.3.2 |

**O que estava errado:**
- **"O launcher não respondeu" ao importar a música:** o motor do launcher (e do Android) só aceitava pedidos de até 2 MB e cortava a conexão no meio.
  - Agora aceita até 40 MB no envio da música e da cópia para o jogo.
  - Qualquer pedido grande demais responde "arquivo grande demais (máx. X MB)" em vez de derrubar a conexão.
  - A central aceita até 24 MB por envio.
  - A cópia WAV para o jogo fica em no máximo 10 MB.
- **"Tocar no jogo" não terminava:** na fila do celular, o `id` da música apagava o `id` do pedido. O MineSync respondia a um pedido que não existia e a central esperava até estourar o tempo.
  - Agora o id do pedido vale sempre, e a música vai em `track`.
  - O mesmo erro afetava a **loja do celular**, que mandava `id: ''` e nunca recebia resposta (agora `order_id`), e o **aceitar corrida** do Uber (agora `ride`).
- **Tempos alinhados:** o servidor tem até 90 s para baixar e preparar a música, e o celular espera 100 s.

**Testado de ponta a ponta:** central local + motor do launcher + MineSync simulado.
- Envio de um MP3 de 9,6 MB e de um OGG com a cópia WAV para o jogo.
- Lista, tocar no celular (baixa para o cache e depois usa o cache), tocar no jogo (o servidor baixa a música inteira e responde) e parar.

# MineBot 2.17.2: vínculo de conta sem Discord, notificações, login herdado nos sites, voz mais limpa e mais estabilidade

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | Conta › **🎮 Servidores**, app **Notificações**, navegador do celular já logado, celular que cabe na tela em pé, voz e conexões mais estáveis | **2.17.2** |
| `site/modules/plugins/botcraft` | Vínculo da conta com os servidores, notificações da conta, login do celular nos sites, voz que não acumula atraso | **4.26.0** |
| `site/modules/plugins/serverhub` | Voz do navegador mais limpa (o restante é a sua 1.2.4) | 1.2.4 |
| `minesync/MineSync` | Vínculo pelo `/login`, `/register` (próprio, AuthMe, nLogin, OpeNLogin) e `/sync` | **6.6.0** |
| `mods` | Notificações e Contas no celular do mod | **1.3.2** |
| `android/MineBot-Android` | O mesmo celular novo e a mesma estabilidade | **1.10.2** |

**Vínculo da conta FoxLand com os servidores (sem Discord):**
- O vínculo sempre exige prova dos **dois lados**: a senha do servidor (ou o `/sync` no jogo) **e** a conta FoxLand.
- **Entrou pelo launcher ou pelo mod:** o servidor vincula sozinho.
- **Entrou com o mesmo nick e fez `/login` ou `/register`** (no login do MineSync, AuthMe, nLogin ou OpeNLogin):
  - se o launcher ou o mod dessa conta estiver jogando do mesmo IP, vincula sozinho;
  - senão chega uma notificação: confirme em **Conta › 🎮 Servidores** no launcher, no app **Notificações › Contas** do celular (launcher, Android, site ou mod).
- **`/sync`** (reserva):
  - `/sync` sozinho pede o vínculo;
  - `/sync FL123456` usa o código gerado na conta (vale 15 min, uma vez).
  - O `/sync <usuário do Discord>` continua igual.
- **Nick de conta FoxLand que entra sem o launcher:**
  - com senha neste servidor (ou já vinculado), entra e faz `/login`;
  - sem senha, pode fazer `/register`, mas fica **parado até a conta confirmar**. Se a conta recusar, é expulso e a senha é apagada. Se sair sem confirmar, a senha também some. Ninguém "ocupa" o seu nick.
- Na conta aparecem: os servidores vinculados (com Desvincular), os pedidos e os servidores onde o seu nick já jogou e ainda falta vincular.

**Notificações:**
- Multas (recebida, paga, cancelada), banco (recebeu ou enviou), curtidas, seguidores, respostas e vínculos de conta.
- O aviso desce por cima e fica na central de notificações (toque na barra de cima), com scroll e "Carregar mais".
- O app **🔔 Notificações** tem a lista completa, com filtros e a aba **Contas**.
- Guardadas na sua conta: aparecem no launcher, no Android, no site e no mod.

**Sites já logados:** o navegador do celular abre os sites do sistema (BotCraft, MineGram, ServerHub…) já logado na conta do launcher.
- Usa um código de uso único e um cookie que vale **só dentro do celular** (particionado), não no seu navegador normal.

**Gov leve e celular que cabe na tela em pé:**
- Abas, chips e botões quebram linha em vez de sair da tela; diálogos e menus rolam por dentro.
- A roda do mouse rola o que é de lado, e as listas longas só desenham o que aparece.
- As fotos do Gov carregam aos poucos.

**Voz mais limpa:**
- Áudio a 24 kbps.
- Buffer de rede maior e adaptativo (60 a 400 ms).
- A primeira sílaba não é mais cortada (pré-fala de 20 ms).
- Com a rede engasgada, o áudio atrasado é descartado, no aparelho e na central, em vez de acumular atraso.

**Estabilidade (menos erros de HTTP):**
- **Leituras** tentam de novo sozinhas, até 4 vezes, em queda de rede, 502–504, 520–524 da Cloudflare e 429 (esperando o que a central pedir).
- **Ações** (pagar, multar, transferir) só repetem quando com certeza não chegaram à central. Nunca repetem um pagamento que pode ter passado.
- O celular do site espera a internet voltar antes de tentar de novo.

# MineBot 2.17.1: SpotyCraft, central completa, editor de fotos e Gov rápido

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | App **SpotyCraft**, editor de fotos na Galeria, proporções na Câmera, Gov rápido com cache, loja que segue o servidor. Botão para ligar a central e **Baixar a central (zip)** | **2.17.1** |
| `site/modules/plugins/botcraft` | SpotyCraft (músicas da conta), Gov com cache e lista de jogadores em páginas | **4.25.0** |
| `site/modules/plugins/serverhub` | Sem mudança (a sua 1.2.4) | 1.2.4 |
| `minesync/MineSync` | **SpotyCraft** no Simple Voice Chat: músicas do celular e `/spoty play <link>` | **6.5.0** |
| `mods` | App SpotyCraft no celular do mod e o **Celular+** (o celular completo já logado, sem o launcher) | **1.3.1** |
| `android/MineBot-Android` | Os mesmos apps novos do celular | **1.10.1** |

**SpotyCraft (app verde de caixa de som):**
- **Enviar:** MP3 e áudio em geral, até 10 MB por música (1 GB e 300 músicas por conta).
  - A música fica na sua conta, na central.
  - Formatos que o servidor não decodifica (OGG, M4A, FLAC…) ganham uma cópia WAV leve para o jogo, feita no próprio celular.
- **Tocar no celular:** o launcher baixa para um cache temporário (`spoty-cache`).
  - Depois de 40 min sem tocar, o arquivo some do aparelho e fica só na sua conta.
  - Se tocar de novo, baixa de novo.
- **Tocar no jogo** (chave ligada por padrão):
  - o MineSync do servidor em que você está toca a música no **Simple Voice Chat**, presa ao seu personagem: a fonte do som anda com você;
  - alcança o **dobro** da distância da voz do servidor e fica mais baixa de longe;
  - o servidor guarda uma cópia local em `plugins/MineSync/spoty`;
  - quem ouve pode baixar ou desligar a categoria **SpotyCraft** no menu do Simple Voice Chat.
- **No servidor sem o app:** `/spoty play <link de um mp3/wav>`, `/spoty stop` e `/spoty vol <5-100>`.
  - `/spoty play` é só para OP (`minesync.spoty`).
  - O app do celular usa `minesync.spoty.app`, liberado para todos por padrão. Para bloquear, tire essa permissão de um grupo.
  - Links da rede interna (127.x, 192.168.x, 10.x…) são recusados.

**Gov rápido:**
- Abre na hora com o que já estava salvo no aparelho e atualiza por trás.
- A lista de jogadores vem em páginas de 40 e carrega mais ao rolar.
- A central guarda a lista do servidor por 2 min, e o botão ⟳ força atualizar.

**Galeria e Câmera:**
- A Galeria tem um **editor** (✏️): efeitos, brilho, contraste, saturação, vinheta e nitidez.
  - Também dá para cortar (livre, 1:1, 4:3, 3:4, 16:9 ou 9:16), girar, espelhar e desfazer.
  - **Salvar cópia** não mexe na foto original.
- A Câmera escolhe a proporção da foto: tela toda, retrato 9:16, quadrado, paisagem 16:9 ou 3:4.

**Loja:** ao entrar num servidor, o app Loja recarrega sozinho e mostra a loja desse servidor.

**Central completa (substitui a oficial se ela parar):**
- No launcher, a aba **Cloudflare › Rede local** liga a central neste PC pelo botão, mesmo com "Ajudar a rede" desligado.
- A chave **Aceitar servidores e celulares da rede de casa** deixa a central ouvir no IP do PC.
- **⬇ Baixar a central (zip)** gera `MineBot-Central.zip` na pasta Downloads:
  - traz `start.bat` (Windows) e `start.sh` (Linux), que baixam o Node 22 sozinhos na 1ª vez e religam se cair;
  - a central roda o site BotCraft, o celular pelo navegador, WebDesk, ServerHub e o **protocolo do MineSync**;
  - os servidores ligam direto nela: `Api.Url: "http://ip:8790/"`;
  - o zip não leva nenhum dado, senha ou banco deste PC.
- **Usar outra central:** aponta o launcher para a central do zip, ou volta para a oficial.

**Mod sem o launcher:**
- Tudo do celular do mod fala direto com a central, com login automático. O SpotyCraft entrou no mod também.
- O ícone **Celular+** abre o celular completo, igual ao do launcher, já logado na sua conta, no navegador.


# MineBot 2.17.0: mapa sincronizado com o navegador, Multas, app Gov e Calculadora

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | Mapa, rastro e waypoints sobem para a sua conta. Celular com **Calculadora**, **Gov** e a aba **Multas** no Banco | **2.17.0** |
| `site/modules/plugins/botcraft` | Multas e Gov na central (com o Discord e o WhatsApp). Mapa sincronizado no celular do navegador. Base: a sua 4.23.1 | **4.24.0** |
| `site/modules/plugins/serverhub` | Sem mudança (a sua 1.2.4) | 1.2.4 |
| `minesync/MineSync` | Classe nova `net.foxsrv.gov.Gov`, que substitui o MineInvoice: `/multa`, `/multas`, `/gov`, prisão e silêncio próprios, comandos de console configuráveis | **6.4.0** |
| `mods` | Calculadora, Multas (no Banco) e Gov no celular do mod | **1.3.0** |
| `android/MineBot-Android` | O mesmo mapa sincronizado e os mesmos apps novos do celular | **1.10.0** |

**Mapa pelo navegador (ou em outro aparelho), com a mesma conta:**
- Enquanto você joga, o launcher manda para a central:
  - os blocos do mapa que o minimapa já desenhou (só os que mudaram, comprimidos);
  - a sua posição e o destino (waypoint ou Uber), a cada ~2 s;
  - o **rastro** por onde você andou (a linha azul).
- Depois de atualizar, o mapa que você já tinha explorado também sobe, aos poucos.
- No celular do site (`/botcraft/phone/`), o app **Mapa** mostra o que você já explorou, o rastro, os waypoints, a linha até o destino e a corrida do Uber, com **🟢 ao vivo** enquanto você está no jogo.
- O app **Waypoints** também abre no navegador. Marcar, seguir ou apagar um waypoint lá aparece no launcher em ~2 s, e o contrário também. Se os dois lados mudaram ao mesmo tempo, os waypoints dos dois ficam.
- Só a sua conta vê o seu mapa.

**Multas (substituem o MineInvoice; pode tirar o `MineInvoice.jar`):**
- **De onde vêm:**
  - no jogo: `/multa <jogador> <valor> [motivo]` (OP);
  - pelo app Gov do celular;
  - pelo Discord do servidor (`/adv multar` do módulo adv, no servidor do Discord ligado ao servidor de Minecraft pelo ServerHub › Discord).
- **Onde aparecem:**
  - no Banco › **Multas**: o total, o total de cada servidor (com a foto) e cada multa;
  - no jogo: `/multas`, com os botões [Pagar] e [Pagar tudo].
- **Pagar:** tudo de uma vez, só as de um servidor ou uma por uma. Cada servidor desconta na moeda dele (Vault), mesmo com você fora do jogo. Parte vai para quem aplicou (`OwnerTax`) e o resto para `Gov.Fines.ServerAccount`.
- **Pagou num lugar, sai de todos:** jogo, celular, site e Discord, onde o total e o cargo de multa do adv são atualizados.
- **Avisos:** no chat do jogo, no celular e no **WhatsApp** (para quem tem a conta ligada ao WhatsApp).
- Com multa pendente, as permissões de `Gov.Fines.PermissionsBlacklist` ficam bloqueadas no servidor, igual ao MineInvoice.
- As multas antigas do MineInvoice não são importadas. Elas ficam num arquivo próprio dele (`invoices.dat`). Cobre ou cancele essas antes de remover o jar.

**App Gov 🏛️ (moderação completa no celular):**
- **Quem abre:** o dono ou admin do servidor no site, quem é **OP** no servidor (o MineSync manda a lista de OPs à central a cada 3 min) e quem tem um **cargo** do Gov.
- **Servidores:** todos em que a conta tem acesso, com a foto (o logo do site do servidor), quem está online, as multas pendentes e quantos estão presos.
- **Abas de cada servidor:**
  - **Multas:** pendentes, pagas e todas; **＋ Multar** (online, por nick ou da busca); cancelar.
  - **Membros:** todo mundo que já entrou, online e offline, com busca e filtros (online, com multa, com cargo).
    - Na página do jogador: UUID, primeira e última vez, mundo e posição, vida, fome, nível, modo de jogo, saldo, grupos, whitelist, banido, OP, tempo de jogo, mortes, abates e os **placeholders** do PlaceholderAPI (lista em `Gov.Placeholders`).
    - Botões: multar, expulsar, banir/desbanir, prender/soltar, silenciar, whitelist e cargo.
  - **Whitelist:** ligar/desligar, adicionar e tirar. É a whitelist do MineSync.
  - **Presos:** presos, banidos e silenciados, com o tempo que falta e o botão de soltar. Com tempo, o Gov solta sozinho quando acaba.
  - **Cargos:** nome, cor, as permissões no app (ver/aplicar/cancelar multas, membros, whitelist, expulsar, banir, prender, silenciar, cargos, histórico) e o **grupo de permissões** do servidor.
    - Quem recebe o cargo entra no grupo pelo `Gov.Commands.SetGroup` (LuckPerms: `lp user {player} parent add {group}`).
    - Um cargo nunca pode punir um OP nem dar permissões que quem está dando não tem.
  - **Comandos:** os comandos de console do MineSync, com placeholders (`{player} {reason} {by} {time} {minutes} {group} {jail} {amount}` + PlaceholderAPI). Também: os motivos prontos, o valor máximo da multa, o tempo padrão de prisão e o espelho no Discord/WhatsApp. Só o dono/admin muda.
  - **Histórico:** quem fez o quê.
- **Sem plugin de prisão ou de mute:** o MineSync prende e silencia sozinho.
  - Defina a prisão com `/gov setjail`.
  - O preso só usa os comandos de `Gov.Jail.AllowedCommands` e volta para a prisão se sair e entrar de novo.
  - Ao ser solto, volta para onde estava.

**Calculadora 🧮:** + − × ÷, %, ±, parênteses, potência, raiz, memória, histórico e o teclado do PC. Está no launcher, no Android, no site e no mod.

**Celular:** um app marcado para o dock que não cabia lá (o dock tem 4 lugares) **sumia** da tela inicial. Agora ele vai para a grade. No site, era assim que o Mapa ficava escondido.

**Atualizar:**
1. **Site:**
   - copie `site/modules/plugins/botcraft` e `serverhub` por cima das pastas do site, mantendo `config.yml` e `data/`;
   - reinicie o site;
   - as tabelas novas (`bc_fines`, `bc_gov_*`, `bc_map_*`) são criadas sozinhas.
2. **Servidor:**
   - troque o `MineSync.jar` pelo `MineSync-6.4.0.jar` e reinicie;
   - o `config.yml` ganha a seção `Gov:` sozinho;
   - tire o `MineInvoice.jar` e apague o `NetherNet.java` que sobrou da versão antiga do MineSync.
3. **Launcher:** `COMPILAR.bat`. **Mods:** `mods\COMPILAR-MODS.bat`. **Android:** `COMPILAR-ANDROID.bat`.

# MineBot 2.16.10: minimapa de volta (com o mod também) + rotas pelo mapa

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | Minimapa volta a aparecer em todas as versões, inclusive no 26.3 com Fabric/Forge (o mod). Waypoints no minimapa. Clique no mapa grande para **traçar rota** ou salvar waypoint | **2.16.10** |

**Por que o minimapa sumiu:** desde a 2.16.0, o launcher **escondia o minimapa quando o mod do MineBot estava no jogo**. No 26.3, com Fabric ou Forge, o mod entra sozinho, então o minimapa nunca aparecia. A regra saiu: o minimapa aparece em qualquer versão, com ou sem o mod. O mod continua com a bússola do GPS no topo da tela.

**Rotas e waypoints:**
- **Minimapa:** mostra os **waypoints** do mundo como pontos coloridos. O que estiver fora do alcance vira um losango na borda, apontando para onde ele fica. O que você está seguindo tem contorno branco. A linha do GPS e a **linha amarela do UberCraft** continuam.
- **Mapa grande** (tecla do mapa ou 🗺): **clique sem arrastar** num ponto para abrir um menu:
  - **🧭 Traçar rota até aqui**: a linha aparece no minimapa, no mapa e no celular;
  - **📍 Salvar waypoint aqui (e seguir)**, com nome;
  - clicando num waypoint: **Seguir** ou **Apagar**;
  - **✖ Parar a rota**;
  - durante uma corrida do Uber, o menu mostra a linha amarela da corrida.

# MineBot 2.16.9: app Android só com o Horizon (compila sem o Amethyst)

| Pasta | O que mudou | Versão |
|---|---|---|
| `android/MineBot-Android` | Só o Horizon (Rust) e o celular. O Minecraft Java (Amethyst) saiu do build, e com ele os erros de compilação. Ganhou Skins (com a prévia 3D), Mercado e UberCraft no menu. As automações saíram | **1.9.8** |
| `launcher/MineBot-Launcher` | O app BotCraft do celular não aparece no Android | **2.16.9** |

**Por que o Android não compilava:** todos os erros vinham do Amethyst, o Minecraft Java dentro do app:
- `libglxshim.so` repetida;
- `Namespace lwjgl3x` repetido;
- agora o `DownloadMirror.FOXLAND`, um campo que a versão nova do Amethyst não tem mais.

Como o app agora é **só o Horizon**, o `COMPILAR-ANDROID.bat` **não compila mais o Amethyst**. Isso tira essa fonte de erros e economiza cerca de 1 GB de download e vários minutos de compilação. Para incluir o Java de novo: `COMPILAR.bat -Java`. Nesse caso o `FOXLAND` é lido por reflexão e não quebra mais a compilação.

**O app (1.9.8):**
- **Jogar:** só Horizon, com o `horizon.zip` embutido.
  - O zip fica ao lado do compilador: na pasta principal, ao lado do `COMPILAR-ANDROID.bat`, ou dentro de `android/MineBot-Android`.
  - Tem instâncias e as opções de **qualidade**: distância, **resolução**, **FPS máximo** e **limite de RAM**.
- **No jogo:**
  - toque na tela, **mouse virtual** (🖱), teclado (⌨), pausa e voz (🎙);
  - mapa (🗺), câmera (👁) e o menu **MB**, que abre o celular, a carteira, o inventário, os prints e o editor de botões.
- **Menu "Mais":**
  - Celular, MineGram, Zap e Conta (login automático nos servidores);
  - **Skins** (nova: prévia 3D, usar, apagar, adicionar de PNG, de nick ou de link);
  - **Mercado** e **UberCraft** (novos), Voz, Vídeos e DLP, Controles e Configurações.
- **Saíram:** as automações (BotCraft) e o Minecraft Java.
- O **WebDesk** (controlar o PC pelo navegador) é do launcher do Windows. Ele controla o computador, então não existe no app do celular.

Rode o `COMPILAR-ANDROID.bat` numa pasta nova. O Horizon já compilado é reaproveitado.

# MineBot 2.16.8: MineSync 6.3.4 (Folia sem erro + login automático na rede local)

| Pasta | O que mudou | Versão |
|---|---|---|
| `minesync/MineSync` | Sem o erro `UnsupportedOperationException` no Folia ao entrar; login automático para quem joga no mesmo PC/rede do servidor | **6.3.4** |
| `site/modules/plugins/botcraft` | A central confere o login da rede local | **BotCraft 4.22.2** |
| `launcher/MineBot-Launcher` | Leva a central e o MineSync novos | **2.16.8** |

**Erro do log (Folia 26.1.2):** `Could not pass event PlayerJoinEvent to MineSync … UnsupportedOperationException at SiteLink.onJoin`.
- O aviso do site ao entrar e o aviso de foto/tópico novo usavam o agendador do Bukkit, que **não existe no Folia**.
- Agora usam o agendador do próprio jogador, que funciona no Paper e no Folia.
- Revisei o resto do plugin: os outros pontos já tinham o caminho do Folia.

**Rede local (`player coming from 127.0.0.1 … automatic login by IP is OFF`):**
- Quem entra com IP local (o mesmo PC do servidor, `127.0.0.1`, ou a rede de casa) agora **entra direto pelo launcher/mod**.
- A condição é a central ver que o "entrar" do launcher/mod daquela conta veio **da mesma rede do servidor** (o mesmo IP público), ou de uma central local.
- **Segurança:** quem chega de fora por um túnel (playit, cloudflared) também aparece como `127.0.0.1`. Esse jogador não tem um "entrar" vindo da rede do servidor, então continua precisando do `/login`.
- Para desligar: `Access.Launcher.LocalAutoLogin: false`.
- A skin da conta também é aplicada nesses jogadores.

**Instalar:** troque o jar do servidor pelo `minesync/MineSync/MineSync-6.3.4.jar` e atualize o BotCraft da central (`site/modules/plugins/botcraft`). Na sua pasta do MineSync, **apague `src/net/foxsrv/NetherNet.java`**: ele ainda está lá (o log mostra a versão 6.3.1 rodando).

# MineBot 2.16.7: UberCraft "indisponível" corrigido

| Pasta | O que mudou | Versão |
|---|---|---|
| `site/modules/plugins/botcraft` | O celular acha o servidor em que você está **online agora** (antes valia o servidor escolhido no launcher, que podia ser outro ou nenhum) | **BotCraft 4.22.1** |
| `minesync/MineSync` | Acha o jogador pelo nick da conta mesmo com maiúsculas diferentes ou o prefixo do Bedrock; o app mostra o **motivo** quando o Uber está desligado | **6.3.3** |
| `launcher/MineBot-Launcher` | O UberCraft não diz mais "entre num servidor" quando você entrou pela lista do jogo ou do Horizon | **2.16.7** |
| `android/MineBot-Android` | O mesmo do launcher | **1.9.7** |

**Por que aparecia "indisponível" (as três causas, todas corrigidas):**
1. O launcher só perguntava ao servidor **escolhido na tela inicial**. Quem entrava pela lista de servidores do jogo ou do Horizon ficava com "entre num servidor com MineSync".
2. A central usava o endereço do launcher **antes** de ver onde você estava online. Se o endereço era de outro servidor da rede, a pergunta ia para o servidor errado, e lá você estava "offline".
3. O MineSync procurava o jogador pelo nick **exato**. Nick com maiúsculas diferentes, ou do Bedrock (`.Nick`), não era achado.

Se ainda aparecer "indisponível", a mensagem agora diz o motivo. Por exemplo:
- `uber.yml → Enabled: false`;
- `UberCraft.jar antigo instalado com Uber.ReplaceExternal: false`;
- o erro de quando o Uber não iniciou.

**Importante:** atualize o **MineSync do servidor para o 6.3.3** (o jar está em `minesync/MineSync/`) **e o BotCraft da central** (`site/modules/plugins/botcraft`). Na sua pasta do MineSync ainda está o `src/net/foxsrv/NetherNet.java`: **apague** antes de compilar. Ele não existe mais e pode quebrar a compilação, deixando o servidor no jar antigo.

# MineBot 2.16.6: só Java 17 ou mais novo (Windows e Android)

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | O Minecraft Java (1.14.4+) e os servidores do PC usam Java 17 ou mais novo; nunca baixa o Java 8 | **2.16.6** |
| `mods/` | Celular no canto direito, subindo de baixo, como o do launcher | **1.2.5** |
| `android/MineBot-Android` | O APK não leva mais o Java 8. O Java 17 do app vira o Java padrão; a lista de versões começa no 1.14.4 | **1.9.6** |

- Do 1.14.4 ao 1.20.4 o jogo abre no **Java 17**. Do 1.20.5 em diante, no 21 ou no 25, conforme a versão pedir.
- No Android, o Java 17 é instalado na 1ª vez que abrir o Minecraft Java. Ele aparece como "Preparando o Java 17…" e vira o padrão do Amethyst.
- O APK fica cerca de 27 MB menor (sem o Java 8).
- Tudo do 2.16.5 continua valendo. Isso inclui a correção do `Namespace 'org.angelauramc.lwjgl3x'`: o log que você mandou ainda é do 2.16.4. Extraia numa pasta nova e rode `COMPILAR-ANDROID.bat`.

# MineBot 2.16.5: APK compilando, login automático no Horizon, UberCraft sempre no celular, binds na hora, Ligações no Zap e instâncias com o jogo aberto

| Pasta | O que mudou | Versão |
|---|---|---|
| `android/MineBot-Android` | Passa do erro `Namespace 'org.angelauramc.lwjgl3x' is used in multiple modules`; login automático ao abrir o Horizon; no Horizon só as automações leves | **1.9.5** |
| `launcher/MineBot-Launcher` | Login automático no Horizon, binds na hora, instâncias com "jogo aberto" e Fechar, trocar o motor não abre o jogo sozinho | **2.16.5** |
| `site/modules/plugins/botcraft` | Aba **Ligações** no Zap (histórico), UberCraft sempre na tela do celular | **BotCraft 4.22.0** |
| `minesync/MineSync` | O UberCraft embutido assume sozinho se o `UberCraft.jar` antigo estiver instalado | **6.3.2** |
| `mods/` | Sem o launcher: login automático já ao abrir o jogo, UberCraft no celular direto na central, Zap → Ligações | **1.2.4** |

## Android: erro de compilação
O log novo passou da `libglxshim.so` e parou no `processReleaseMainManifest`. Os dois pacotes de bibliotecas nativas do LWJGL que vêm com o Amethyst (3.3.3 e 3.4.1) usam o mesmo namespace, `org.angelauramc.lwjgl3x`.

O build agora resolve isso de dois jeitos:
- coloca no `gradle.properties` do app as mesmas opções que o próprio Amethyst usa (`android.uniquePackageNames=false` e `android.nonTransitiveRClass=false`);
- dá um namespace próprio ao `.aar` que repete o namespace. Esses `.aar` só têm bibliotecas nativas.

Rode `COMPILAR-ANDROID.bat` numa pasta nova. Não precisa de `-Clean`.

## Login automático pelo Horizon
- **Antes:** o launcher só avisava a central quando um servidor estava escolhido na tela inicial. Quem entrava pela lista de servidores do próprio Horizon ficava sem o login automático.
- **Agora:**
  - o aviso sai sempre que o Horizon abre, no PC e no Android;
  - é renovado a cada hora enquanto o Horizon estiver aberto;
  - o MineSync de qualquer servidor da rede reconhece o nick e o IP.

## UberCraft no celular
- O app **UberCraft** aparece **sempre** no celular do launcher, do site, do Android e do mod. Antes ele sumia quando o servidor não respondia a tempo.
  - Sem servidor, ou com o Uber desligado, o app abre e explica o motivo.
- **MineSync 6.3.2:** se o `UberCraft.jar` antigo estiver na pasta `plugins`, o MineSync desliga ele e liga o UberCraft embutido. O celular só fala com o embutido. Os warps e a config do antigo são copiados, e o `/uber` e o `/ubergui` voltam a ser do MineSync. Para manter o antigo, use `Uber.ReplaceExternal: false`.

## Horizon: só as automações leves (o BotCraft completo é no Java)
- No Horizon, com ou sem o mod do MineBot, o app BotCraft mostra as automações do Horizon: anti-AFK, defesa, minerar, cortar árvore, colher, comer e renascer sozinho.
- O **AFK** e a **defesa automática** da aba "AFK e defesa" também valem no Horizon:
  - ficou parado o tempo configurado → as automações do Horizon ligam;
  - mexeu no jogo → elas desligam;
  - parado com monstro perto → ele bate.
- O celular, o minimapa e a câmera livre continuam no Horizon.

## BindCraft na hora
- Cada toque no atalho responde na hora e entra numa fila daquele atalho. Apertou 5 vezes, saem 5 envios, sem perder nenhum. Antes era um por segundo e o resto sumia.
- Atalhos diferentes rodam ao mesmo tempo. A fila aceita até 30 toques por atalho.

## Zap: aba Ligações
- Nova aba **Ligações** no Zap (launcher, site e Android), no celular do mod em **Zap → Ligações**.
- Mostra o histórico de chamadas: feitas ↗, recebidas ↙ e perdidas (em vermelho), com a duração e há quanto tempo.
- 📞 liga de volta, e tocar no nome abre a conversa.

## Mod sem o launcher
- **Login automático:** o mod avisa a central assim que o jogo abre (com a conta FoxLand logada no mod) e renova a cada 50 min. Em qualquer servidor da rede, o MineSync reconhece você na entrada.
  - O nick do Minecraft precisa ser o nick ligado à conta (tela **Conta** do mod).
- **UberCraft no celular do mod:** funciona direto pela central, sem o launcher (pedir, cancelar, motorista).

## Instâncias (launcher)
- A instância com o jogo aberto mostra **🟢 jogo aberto** e o botão **⏹ Fechar**, que fecha o jogo dela.
- Na tela inicial, o **Jogar** e o **Fechar** valem para a instância **escolhida**:
  - com outra instância escolhida, o **Jogar** aparece;
  - se o jogo de outra instância estiver aberto, o launcher pergunta, fecha esse jogo e abre o desta.
- **Trocar o motor** (Horizon → Java) não abre o jogo sozinho. Se o Horizon ainda estava preparando, ele não abre; é preciso clicar em **Jogar**.

# MineBot 2.16.4: o APK do Android compila

| Pasta | O que mudou | Versão |
|---|---|---|
| `android/MineBot-Android` | A `libglxshim.so` repetida (Amethyst + kopper-zink) é tirada do `amethyst.aar` antes de montar o APK | **1.9.4** |

O resto é igual ao 2.16.3 (launcher 2.16.3, BotCraft 4.21.0, MineSync 6.3.1, mods 1.2.3).
**Extraia numa pasta nova** (o log mostrava a compilação rodando dentro da pasta `MineBot-2.16.2`) e rode `COMPILAR-ANDROID.bat`. Não precisa de `-Clean`: o Amethyst já compilado é reaproveitado.

# MineBot 2.16.3: fotos do Zap viram mapa no jogo, celular realista no mod, mod direto na central e skin no mundo privado

| Pasta | O que mudou | Versão |
|---|---|---|
| `android/MineBot-Android` | **Compila de novo**: a `libglxshim.so` repetida (Amethyst + kopper-zink) não trava mais o APK | **1.9.3** |
| `site/modules/plugins/botcraft` | Fotos do Zap chegam ao jogo como anexo clicável; botão "Pegar no jogo" | **BotCraft 4.21.0** |
| `minesync/MineSync` | Anexo da foto no chat → mapa na mão (privado, só de quem recebeu) | **6.3.1** |
| `mods/` | Celular realista, só com o mod direto na central, skin da conta no mundo privado | **1.2.3** |
| `launcher/MineBot-Launcher` | Rota do "Pegar no jogo" no celular | **2.16.3** |

## Erro de compilação do Android
O log parava em `:app:mergeReleaseNativeLibs` com `2 files found with path 'lib/arm64-v8a/libglxshim.so'`, porque a biblioteca vem no Amethyst e no kopper-zink. O `tools/build.ps1` agora escolhe uma só (`pickFirsts` para `libglxshim.so` e para qualquer `.so` repetida). Rode `COMPILAR-ANDROID.bat` de novo; não precisa do `-Clean`.

## Foto do Zap → mapa na mão (igual aos tickets)
- Quando alguém manda uma **foto** pelo Zap (celular do launcher, do site, do Android ou do mod) e você está num servidor com o MineSync, aparece no chat do jogo:
  `📱 Zap de Fulano » 📷 foto` e logo embaixo `📎 [🖼 foto de Fulano] (clique para ver a foto num mapa)`.
  Clicando, a foto vem num **mapa na sua mão**. É o mesmo sistema dos tickets.
- `/zap` sem argumentos também mostra as fotos recentes como anexo clicável.
- No celular (launcher, site, Android), ao abrir a foto há o botão **🗺 Pegar no jogo (mapa na mão)**. A mesma opção fica no menu da mensagem (botão direito ou toque longo). No celular do mod há o botão **Pegar a última foto num mapa**.
- **Segurança:**
  - A foto é **privada**: só quem recebeu pode pegar (o clique de outra pessoa é recusado).
  - O plugin baixa a foto pela sessão do servidor, só com o jogador **online naquele servidor** e **na conversa**. Não existe link público.
  - O mapa some se for jogado no chão.
  - Para **pôr em moldura** é preciso a permissão de confiança `minesync.imgmap` (`Images.Zap.FramesNeedPermission: true`).
  - Para desligar tudo: `Images.Zap.Enabled: false`.

## Mod (Forge/Fabric 26.3) 1.2.3
- **Celular realista** dentro do jogo, como o celular do launcher, quando o launcher não está aberto:
  - aparelho com moldura e notch, papel de parede e barra de status (hora, sinal, bateria);
  - ícones coloridos dos apps em grade, listas em cartões e botões arredondados;
  - balões do Zap (verde para os seus, com hora e ✓✓) e barra de navegação (voltar, início, fechar).
  
  Com o launcher aberto, continua abrindo o celular completo do launcher.
- **Só com o mod (sem launcher):** o mod fala direto com a central.
  - Login da conta, login automático nos servidores da rede (o MineSync recebe) e skins.
  - Zap, ligação, MineGram, Banco e Uber vão para os plugins pela central.
  - Os waypoints ficam no próprio mod.
  - Se você entrar na conta já dentro de um servidor, o mod avisa a central na hora. Não precisa sair e entrar de novo.
- **Skin da conta no mundo privado:** no mundo de um jogador, na LAN ou num servidor sem o MineSync, o mod aplica no **seu** personagem a skin ativa da conta FoxLand. É a mesma que o plugin aplica nos servidores da rede, inclusive o modelo fino. Ela também aparece na prévia 3D da tela inicial e muda sozinha quando você troca a skin no site, no launcher ou no celular. Para desligar, use `local_skin=false` em `config/minebot/minebot.properties`.

# MineBot 2.16.2: o celular completo dentro do jogo com o mod + diagnóstico da voz

| Pasta | O que mudou | Versão |
|---|---|---|
| `mods/` | O botão/tecla do celular abre o celular COMPLETO do launcher dentro da janela do jogo | **1.2.2** |
| `launcher/MineBot-Launcher` | Atende o pedido do mod e volta a abrir o celular com a tecla no jogo com o mod | **2.16.2** |
| `site/modules/plugins/botcraft` | A voz conta para o celular se a ponte com o Simple Voice Chat está ligada | **BotCraft 4.20.1** |
| `android/MineBot-Android` | App Voz com o diagnóstico | **1.9.2** |

## Celular do mod igual ao do launcher
- **Com o MineBot Launcher aberto (Windows):** a tecla do celular (Shift direito) e o botão **Celular** da tela inicial e do menu de pausa abrem o **mesmo celular do launcher e do site**, dentro da janela do jogo. Tem tudo: interface completa, fotos do MineGram, Zap com áudio e ligação, Uber com a linha amarela, Waypoints, Voz, Banco, Loja, Empresa, notificações. Ele acompanha a janela do jogo e fecha com a mesma tecla.
- **Sem o launcher aberto:** continua o celular simples do próprio mod, feito com os botões do Minecraft.
- **Por que assim:** desenhar uma página de verdade (HTML com fotos) dentro do Java do Minecraft precisa de um navegador embutido no jogo, e não existe um para o 26.3. O launcher já tem esse navegador, então ele desenha o celular dentro da área do jogo, ligado à janela dele.
- Para usar o celular simples mesmo com o launcher aberto: `launcher_phone=false` no `config/minebot/minebot.properties`.

## Voz: a correção está no site (BotCraft 4.20)
- A causa de a sua voz não chegar foi corrigida na 2.16.1 (BotCraft 4.20.0): com o mod do Simple Voice Chat conectado, a central descartava a voz que vinha do launcher.
- **Atualize o site** (`site/modules/plugins/botcraft`) e **reinicie a central**. Sem isso a voz continua não chegando, mesmo com o launcher novo.
- **Novo:** o app **Voz** diz se está tudo ligado:
  - "Ponte com o Simple Voice Chat ligada";
  - ou que o servidor está sem o SVC ou sem a voz da FoxLand.
  - No servidor, `/minesync voice` mostra "SVC bridge on".
- **MineSync:** o zip `MineSync_84f190` que você mandou tem o `src/net/foxsrv/NetherNet.java` sobrando (de uma versão antiga). Apague esse arquivo antes de compilar. O jar `MineSync-6.3.0.jar` deste pacote já está certo.

---

# MineBot 2.16.1: a sua voz chega no jogo e no Simple Voice Chat + app Voz

| Pasta | O que mudou | Versão |
|---|---|---|
| `site/modules/plugins/botcraft` | A voz de quem tem o mod do Simple Voice Chat e fala pelo launcher não é mais jogada fora | **BotCraft 4.20.0** |
| `launcher/MineBot-Launcher` | App **Voz** no celular | **2.16.1** |
| `android/MineBot-Android` | App **Voz** no celular | **1.9.1** |
| `mods/` | App **Voz** no celular dentro do jogo; Uber e voz na tela inicial do celular | **1.2.1** |

O MineSync (6.3.0) e o ServerHub (1.2.2) não mudaram.

## A sua voz não chegava no jogo nem no Simple Voice Chat
- **Causa:** com o mod do Simple Voice Chat conectado no seu jogo, a central descartava a voz que vinha do launcher, porque esperava que você falasse pelo mod. Se o microfone do mod estivesse mudo, sem a tecla ou com outro dispositivo, ninguém ouvia você: nem quem usa o launcher, nem quem usa o SVC. Você continuava ouvindo todo mundo.
- **Agora vale quem está falando:**
  - falou pelo launcher, a voz vai para todo mundo: launcher, navegador e Simple Voice Chat;
  - falou pelo mod do SVC, vai pelo mod;
  - os dois ao mesmo tempo: o mod vale e o launcher não dobra a voz.
- Também corrigi uma falha da 2.16.0: quem tinha o SVC e o launcher juntos podia deixar de ouvir quem fala pelo launcher.
- **Para valer:** atualize o **site** (`site/modules/plugins/botcraft`) e reinicie. O launcher novo também tira um bloqueio do microfone que a 2.16.0 ligava quando via o SVC na pasta de mods.

## App Voz (celular do launcher, do Android e do mod)
- Tudo do chat de voz num lugar só:
  - **ligar/desligar**, **microfone mudo**, **ensurdecer** e **supressor de ruído** (RNNoise);
  - **modo**: detecção de voz ou aperte para falar;
  - **volume geral**, **volume do microfone** e **sensibilidade**;
  - o **volume de cada pessoa** perto.
- Mostra se você está conectado, falando, e o nível do microfone.
- No mod: celular → **Voz**, pelo launcher aberto. O **Uber** fica na tela inicial do celular do mod.

## O que foi testado aqui
- **Voz e Simple Voice Chat** (teste novo): quem tem o SVC e fala pelo launcher chega no SVC e nos launchers; falando pelos dois, não dobra; quem usa o launcher ouve quem usa o SVC. O mesmo teste **falha** na versão anterior, o que confirma a causa.
- **App Voz:** liga, desliga e salva os volumes, sem erros de JS.
- **Regressão:** central (31) e long polling (6), instâncias/GPS (21), ligações launcher↔site, celular do launcher e do site, Instâncias, Waypoints. O mod compila.

---

# MineBot 2.16.0: mod dentro do jogo, instâncias, Horizon embutido, GPS e Android sem fechar

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | Instâncias, mod MineBot instalado sozinho no Fabric/Forge 26.3, sem painel por cima com o mod, Horizon embutido (`horizon_26.3_<versão>`), GPS/Waypoints | **2.16.0** |
| `android/MineBot-Android` | Horizon não fecha mais (WebView), Amethyst compila, Horizon dentro do APK, instâncias, linha do GPS no minimapa | **1.9.0** |
| `mods/` | Celular DENTRO do jogo, tela inicial estilo Bedrock (conta, central, skin 3D, celular), ponte com o launcher, agente de automações embutido, bússola do GPS | **1.2.0** |
| `site/modules/plugins/botcraft` | Junta o zip FoxLand-2026-11-02 (painéis) + voz em um canal só + long polling de volta | **BotCraft 4.19.0** |
| `site/modules/plugins/serverhub` | O do zip FoxLand-2026-11-02 + a voz do site certa | **ServerHub 1.2.2** |
| `minesync/MineSync` | Junta as duas 6.2.0 (NetherNet fora + painéis + celular na hora + skins) | **MineSync 6.3.0** |

## Instalar (nesta ordem)
1. **Site:** copie `site/modules/plugins/botcraft` e `site/modules/plugins/serverhub` por cima. O `config.yml`, o `data/` e o `storage/` ficam.
2. **Servidores:** troque o jar pelo `minesync/MineSync/MineSync-6.3.0.jar` e reinicie.
3. **horizon.zip (opcional, recomendado):** ponha o seu `horizon.zip` **ao lado do `COMPILAR-WINDOWS.bat`**, na pasta principal. O launcher e o APK já saem com o Horizon pronto. Veja "Horizon embutido" abaixo.
4. **Mods:** rode `COMPILAR-MODS.bat` (o `COMPILAR-WINDOWS.bat` pergunta e faz isso por você). O launcher compilado depois já leva os mods.
5. **Windows:** `COMPILAR-WINDOWS.bat`.
6. **Android:** `COMPILAR-ANDROID.bat`, mantendo `keystore\` e `_build\`.

## Android (1.9.0)
- **O Horizon fechava ao abrir:** a voz/celular (WebView) do processo do jogo usava a mesma pasta de dados do app, e o Android derrubava o jogo ("Using WebView from more than one process…"). Agora cada processo (Horizon e Minecraft Java) tem a sua pasta.
- **Minecraft Java compilando:** o erro `constant expression required` (`case R.id…` no `JavaGUILauncherActivity`) acontecia porque o Amethyst virou biblioteca. O `build.ps1` troca esses `switch` por `if/else`, com o mesmo comportamento.
- **Horizon dentro do APK:** com o `horizon.zip` ao lado do compilador, ele vai para dentro do APK. O app instala sozinho na 1ª vez e quando você atualiza o app, sem baixar do site a cada teste. Limite de ~1,9 GB.
- **Instâncias:** em Jogar → Instância você cria e escolhe instâncias. Cada uma tem motor, versão, qualidade e a sua pasta de jogo.
- **GPS:** a linha até o waypoint (ou a amarela do Uber) aparece no minimapa do Minecraft Java.

## Mod MineBot 1.2.0 (Fabric/Forge 26.3)
- **No launcher:** ao jogar o 26.3 com Fabric ou Forge, o launcher põe o mod sozinho na pasta `mods` da instância. Também tira as cópias antigas e mantém os seus outros mods.
- **Sem painel por cima:** com o mod no jogo, o launcher não abre o celular, o mapa nem o minimapa por cima. Tudo fica no mod, dentro do jogo.
- **Tela inicial estilo Bedrock:**
  - em cima, à esquerda, **só a conta** (entrar e sair);
  - em cima, à direita, a **central** (padrão `https://www.foxsrv.net/`; quem hospeda no PC usa `http://127.0.0.1:8790` ou o IP da rede de casa);
  - do lado direito, a **skin em 3D** (arraste para girar) com **Skins** e **Celular**.
- **Celular dentro do jogo** (Shift direito ou o botão), uma tela do próprio Minecraft, sem app externo:
  - **Zap:** conversa e ligação;
  - **MineGram:** feed, curtir e comentar;
  - **Banco:** saldos e transferir;
  - **Uber:** pedir, cancelar, plantão, aceitar e cancelar corrida;
  - **Waypoints, Automações, Skins, Conta e Central.**
  - A ligação em andamento fica sempre em cima (atender, recusar, desligar).
- **Com o launcher aberto:** a conta, a central (online ou local), o GPS, as automações e a voz são os do launcher. O login nos servidores é automático.
- **"Entre num mundo primeiro" corrigido:**
  - o agente das automações não achava o jogo no Fabric/Forge, que usam outro class loader. O BotAgent 3.0.3 acha;
  - com o jogo aberto por **outro** launcher, o próprio mod liga o agente e avisa o launcher.
- **Bússola do GPS** no topo da tela, até o waypoint seguido (ou a corrida do Uber).

## Voz em um canal só
- Com o **Simple Voice Chat** no jogo, o mod avisa o launcher e a voz de **proximidade** vai só pelo SVC. As **ligações** do celular continuam pelo launcher, com RNNoise.
- A central não manda a proximidade de novo para esse aparelho, e não sai voz dobrada.
- Quem joga pelo launcher e quem joga com o SVC se ouvem pela ponte do MineSync, como antes.

## Instâncias (launcher do Windows)
- **Jogar → Instância** ou a aba **Instâncias**.
- Escolher uma versão em **Versões** cria (ou usa) a instância dessa versão e tipo.
- Cada instância tem a sua pasta (`minecraft\profiles\<id>`). A primeira, "FoxLand", continua na pasta de antes.
- Botões: **Jogar**, **Pasta**, **Mods** (abre a pasta `mods` para pôr os seus), **Configurar** (nome, versão, tipo, RAM, argumentos do Java) e **tirar da lista** (a pasta fica no PC).
- A instância **Horizon 26.3** está sempre na lista. A pasta dela é a do Horizon (abaixo).

## Horizon embutido
- O launcher roda o Horizon na pasta **`%LOCALAPPDATA%\FoxLand\horizon_26.3_<versão do mod>`** (`HORIZON_LAUNCHER_HOME`). Ficam nela:
  - as versões;
  - o jogo compilado com o mod;
  - os arquivos preparados;
  - o marcador `minebot-mod.json`.
- **Para fazer o horizon.zip:** jogue uma vez pelo launcher (ele compila o mod), abra a pasta pela instância Horizon → **Pasta**, zipe a pasta inteira e ponha o zip ao lado do `COMPILAR-WINDOWS.bat`.
- **Windows:**
  - até 700 MB, o `horizon.zip` vai dentro do instalador;
  - maior que isso, sai em `out\horizon.zip`. Distribua junto com o `MineBot_Launcher.exe` e o instalador copia.
  - Na 1ª vez, o launcher abre o zip e não baixa nem compila de novo.
- **Android:** o mesmo zip vai dentro do APK. Ele precisa ter o `horizon-resources.json` (os arquivos preparados), como o que você já punha no site.
- A pasta mudou, então quem já usava o Horizon compila o mod de novo uma vez (ou usa o `horizon.zip`).

## Waypoints e Uber com GPS
- **App Waypoints** (celular do launcher, do Android e do mod):
  - **Marcar aqui**, por coordenadas ou tocando no mapa;
  - **Seguir** desenha uma **linha** até o lugar no minimapa e no mapa, como o destino do GTA. Ao chegar, avisa e desliga.
- **Uber:** com uma corrida pedida ou aceita, a linha fica **amarela**. Primeiro vai até o embarque e depois até o destino.
  - A corrida fica na barra do celular, e o botão **Cancelar corrida** está no app.
- A linha é reta (direção e distância): ela não desvia de montanhas.

## O que foi testado aqui
- **Central:** 31 testes, mais 6 do long polling.
- **Launcher e GPS:** 21 testes de instâncias e GPS, além do motor de verdade (mod/hello, conta, GPS, voz com SVC, chave errada recusada).
- **Mod com o motor de verdade:** a ponte com o launcher (conta, waypoint, hello). O mod compila contra as classes do Minecraft.
- **Telas:** a aba Instâncias, o app Waypoints e o mapa com a linha, todos os apps do celular (launcher e site) e as ligações (launcher↔site). Sem erros de JS.
- **Plugin:** `MineSync-6.3.0.jar` e o BotAgent 3.0.3 compilados aqui.
- **Código:** C# e Java com chaves conferidas; todos os JS com `node --check`.

**Não deu para testar aqui:**
- a compilação do `.exe`, do APK (Flutter/Amethyst/Horizon) e dos mods com o Gradle de verdade;
- o mod dentro do Minecraft 26.3 de verdade. O desenho da moldura do celular, a bússola e a skin 3D usam partes do jogo achadas na hora; se alguma não existir nessa versão, só aquele desenho não aparece e o resto funciona;
- o Android num celular. Se der erro, mande o log.

**Limites:**
- **Horizon:** o celular do Horizon continua por cima do jogo. O mod do Horizon (Rust) é só das automações, sem celular dentro.
- **Fotos do MineGram:** no celular do mod as fotos abrem no site, porque o Minecraft não mostra imagens da internet nas telas.

## NÃO compartilhe (fica fora dos zips)
- Zips, `config.yml`, `.db`, `.key`, backups, `assinatura\`, `key.properties`.
- O seu `horizon.zip`.
- `%LOCALAPPDATA%\DLPNet`, `%LOCALAPPDATA%\FoxLand` (com o `engine.json` e o `horizon_26.3_*`), `secrets.bin`, `data/peer.json`, `plugins/MineSync/peer/identity.json`.
- O `drive/` do plugin, o `data/` dos peers, `local/ia/storage/`, o `storage/` da central.
- `plugins/MineSync/access.yml`, `plugins/MineSync/legacy/`, `FoxLand servers\`, `central\`, `cloudflared\`, `overlay-bg.jpg`, `modules/plugins/botcraft/data/` (com `siteimg/` e `panelimg/`).
- Android: `keystore\` (e `senha.txt`) e `_build\`.
- As conversas do CraftZap (`bc_ph_msgs`).
- ServerHub: `config.yml` / `data/` (`serverhub.db`), `install.json`, `selfbot.json`.
- Mods: `config/minebot/` (`account.bin`, `waypoints.json`).
- O `.oauth.key` do e-mail e os tokens de bot do Discord.
- Troque o `GATEWAY_ADMIN_PASSWORD` da moeda bitcoin (está em texto puro).

---

# MineBot 2.15.0: Android com toque e overlays, qualidade do Horizon, voz leve, notificações no celular

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | Freecam dentro do overlay, notificações e ligação sempre à mostra no celular, voz mais leve e sem eco, skins nos dois sentidos | **2.15.0** |
| `android/MineBot-Android` | Toque e overlays no Horizon, qualidade/RAM do Horizon, Minecraft Java compilando (Amethyst sem espaço no caminho) | **1.8.1** |
| `site/modules/plugins/botcraft` | Voz sem áudio repetido, histórico de ligações no MineZap, skins nos dois sentidos, moderação do MineGram | **BotCraft 4.18.0** |
| `minesync/MineSync` | Feito em cima do **6.1.1** que você mandou + long polling + skins nos dois sentidos | **MineSync 6.2.0** |
| `mods/` | Compila no Minecraft 26.x (sem GLFW/`setScreen` direto), o `COMPILAR-MODS.bat` acha o JDK sozinho | **1.1.0** |

O ServerHub (1.2.0) e o mod do Horizon (1.0.0) não mudaram, só o `voice.js` do ServerHub (voz nova).

## Instalar (nesta ordem)
1. **Site:** copie `site/modules/plugins/botcraft` e `site/modules/plugins/serverhub/site/voice.js` por cima. O `config.yml` e o `data/` ficam.
2. **Servidores:** troque o `MineSync.jar` pelo `minesync/MineSync/MineSync-6.2.0.jar` (o `config.yml` fica; as chaves novas têm padrão).
3. **Windows:** extraia numa pasta **nova e vazia** e rode o `COMPILAR-WINDOWS.bat` da raiz.
4. **Android:** ponha `android\MineBot-Android` por cima do seu, **mantendo `keystore\` e `_build\`**, e rode o `COMPILAR-ANDROID.bat` da raiz. Compila com o Flutter, igual ao 1.7.2.
5. **Mods:** rode `COMPILAR-MODS.bat` da raiz (ou `mods\COMPILAR-MODS.bat`).

## Android (1.8.1)
- **O toque não chegava ao jogo e os overlays não apareciam:** as janelas por cima do Horizon usavam um tipo que o Android recusa na tela do jogo (erro de token). Agora usam o tipo "painel" da própria tela do jogo, com uma segunda tentativa automática. Os controles de toque, o celular, a voz e o mapa aparecem de novo.
- **Menos travadas:** os comandos de toque iam um por um, cada um esperando a resposta do anterior. Agora vão por uma fila só, e os movimentos de câmera se juntam num só.
- **Qualidade do Horizon:** em **Jogar → Horizon**, escolha Leve / Médio / Alto e ajuste a distância de visão, a resolução (%), o FPS máximo e a **RAM**. Vale ao abrir o jogo.
- **Minecraft Java:** o `ndk-build` do Amethyst não aceita espaço no caminho. A compilação agora usa uma pasta sem espaço (`%LOCALAPPDATA%\MineBot-build`) ou uma letra de unidade temporária.
- **Erro do `arboard` / "cannot find Clipboard":** corrigido no ajuste do Horizon (o trecho que tira o `arboard` era pulado).

## Freecam dentro do jogo
- A freecam abre a **tela do bot da sua conta** (a do site, mas sem o cabeçalho) numa janela grande **por cima do jogo**, e não no navegador.
- "← Voltar" fecha e volta para o jogo. Funciona no launcher do Windows e no celular dos mods.

## Skins nos dois sentidos
- **Trocou no launcher:**
  - com você online, a skin muda no servidor na hora;
  - com você offline, muda quando você entrar.
- **Usou `/skin` no jogo:** a skin vai para o histórico de skins da sua conta no launcher (até 40).
- A central confere tudo antes de guardar: só aceita skin assinada pela Mojang, de `textures.minecraft.net`, PNG 64×64.
- Para desligar no servidor: `Skins.SyncAccount: false` no `config.yml` do MineSync.

## Voz mais leve e sem áudio repetido
- **Áudio repetido:** quem tinha o launcher e o site (ou o mod) abertos recebia o mesmo áudio duas vezes, e o microfone podia ir duas vezes. Agora a central entrega **uma vez por pessoa** (no aparelho que você está usando) e aceita **um microfone por pessoa**.
- **Leve e sem chiado:** Opus de voz a 16 kbps, com a maior qualidade de codificação e correção de perda de pacotes. Não fica com som de rádio.
- **Internet lenta:** o buffer se ajusta sozinho. Cresce quando os pacotes atrasam e volta a diminuir. Pacotes repetidos ou atrasados demais são descartados.
- Vale para: voz embutida, Simple Voice Chat, site, mod, launcher e app.

## Celular virtual: notificações como no Android
- **Central de notificações:** puxe ou toque na barra de cima. Tem "Limpar tudo" e ✕ em cada notificação, e mostra ícones na barra.
- **Ligação sempre à mostra:**
  - **tocando:** aviso por cima com toque;
  - **em andamento:** faixa verde com o tempo em **todas as telas** e notificação fixa ("em andamento"). Um toque volta para a chamada;
  - **não atendida:** "Chamada perdida".
- **MineZap:** as ligações entram no histórico da conversa (atendida com a duração, não atendida, recusada, cancelada), com o botão "Ligar".
- Mensagem nova no MineZap vira notificação.

## MineGram
- Toque no **nome ou na foto** para ir ao perfil. O perfil mostra as publicações em grade, e um toque abre a publicação.
- **Moderação (admin supremo):** no "⋯" de qualquer publicação aparece **"🛡 Apagar (moderação: conteúdo proibido)"**. Ela some do MineGram, do site e do Discord.
  - Só aparece para a conta dona do site. A central confere de novo no servidor, então ninguém mais consegue apagar.

## Mods 1.1.0
- **"JAVA_HOME is set to an invalid directory":** o `COMPILAR-MODS.bat` agora procura o JDK nas pastas de instalação, igual ao compilador do plugin:
  - versões: 25, 21, depois 22–27;
  - pastas: Java, Adoptium, Microsoft, Corretto, Zulu, Liberica, OpenJDK, Semeru;
  - se não achar, usa um `JAVA_HOME` válido e depois o Java do Android Studio.
- **Erros de compilação** (`org.lwjgl.glfw`, `Screens.getButtons`, `setScreen`, `mc.screen`): essas partes mudaram de lugar no Minecraft 26.x. Agora o mod as acha em tempo de execução, e os eventos do Forge também.

## O que foi testado aqui
- **Central:** 31 testes, mais 6 do long polling.
- **Ligações:** voz launcher↔site, ligação no launcher (75 quadros em 1,5 s, sem perda).
- **Celular:**
  - todos os apps (Windows, site, desktop, Empresa, câmera/navegador);
  - notificações, faixa da ligação, chamada perdida e menu de moderação do MineGram. Sem erros de JS.
- **Plugin:** `MineSync-6.2.0.jar` compilado aqui.
- **Mods:** compilam contra as classes do Minecraft 26.x (sem GLFW no classpath).
- **Código:** C# e Java com chaves conferidas; todos os JS com `node --check`.

**Não deu para testar aqui:** a compilação do `.exe` (C#), do APK (Flutter, Horizon e Amethyst) e dos mods com o Gradle de verdade. Também não deu para testar o toque num celular real. Se der erro, mande o log.

## NÃO compartilhe (fica fora dos zips)
- Zips, `config.yml`, `.db`, `.key`, backups, `assinatura\`, `key.properties`.
- `%LOCALAPPDATA%\DLPNet`, `%LOCALAPPDATA%\FoxLand`, `secrets.bin`, `data/peer.json`, `plugins/MineSync/peer/identity.json`.
- O `drive/` do plugin, o `data/` dos peers, `local/ia/storage/`, o `storage/` da central.
- `plugins/MineSync/access.yml`, `plugins/MineSync/legacy/`, `FoxLand servers\`, `central\`, `cloudflared\`, `overlay-bg.jpg`, `modules/plugins/botcraft/data/siteimg/`.
- Android: `keystore\` (e `senha.txt`) e `_build\`.
- As conversas do CraftZap (`bc_ph_msgs`).
- ServerHub: `config.yml` / `data/`, `install.json`, `selfbot.json`.
- Mods: `config/minebot/` (`account.bin`, `phone-browser\`).
- Troque o `GATEWAY_ADMIN_PASSWORD` da moeda bitcoin (está em texto puro).

---

# MineBot 2.14.0: celular rápido, câmera ao vivo, Android compilando, mods Forge/Fabric

| Pasta | O que mudou | Versão |
|---|---|---|
| `launcher/MineBot-Launcher` | Câmera ao vivo, celular mais rápido, navegador na FoxLand, `COMPILAR.bat` que acha o projeto sozinho | **2.14.0** |
| `android/MineBot-Android` | Compila de novo (Horizon e Minecraft Java), Java dentro do APK, navegador no Android, opção do mod | **1.8.0** |
| `site/modules/plugins/botcraft` | Fila do celular com *long polling* (os apps do servidor respondem na hora) | **BotCraft 4.17.0** |
| `minesync/MineSync` | Fala com a central na hora (long polling) e responde em paralelo | **MineSync 6.2.0** |
| `mods/` (novo) | O mod para quem não usa o launcher: Forge e Fabric 26.3 | **1.0.0** |

O ServerHub (1.2.0) e o mod do Horizon (1.0.0) não mudaram.

## Instalar (nesta ordem)
1. **Site:** copie `site/modules/plugins/botcraft` por cima. O `config.yml` e o `data/` ficam.
2. **Servidores:** troque o `MineSync.jar` pelo `minesync/MineSync/MineSync-6.2.0.jar` (o `config.yml` fica; as chaves novas têm padrão).
3. **Windows:** extraia o zip numa pasta **nova e vazia** e rode o `COMPILAR-WINDOWS.bat` da raiz (ou o `COMPILAR.bat` dentro de `launcher\MineBot-Launcher`).
4. **Android:** ponha `android\MineBot-Android` por cima do seu, **mantendo `keystore\` e `_build\`**, e rode o `COMPILAR-ANDROID.bat` da raiz (ou o `COMPILAR.bat` dentro da pasta).
5. **Mods (opcional):** rode `mods\COMPILAR-MODS.bat`. Veja `mods/LEIA-ME.md`.

## Erros de compilação corrigidos
- **Windows: "O argumento ...\build\build.ps1 para o parâmetro -File não existe".**
  - Era um `COMPILAR.bat` antigo (do 2.10) numa pasta misturada com a versão nova.
  - Agora o `COMPILAR.bat` procura o `build\build.ps1` sozinho: na própria pasta, em `MineBot-Launcher\` e em `launcher\MineBot-Launcher\`. Se não achar, diz o que fazer.
  - Na raiz do pacote também há um `COMPILAR-WINDOWS.bat` e um `COMPILAR-ANDROID.bat`.
- **Android, Horizon: `duplicate key android = []` no `Cargo.toml`.**
  - Os ajustes do MineBot eram aplicados de novo por cima dos da compilação anterior (`_build\horizon` ficava com a linha repetida).
  - Agora o código do Horizon volta ao original a cada compilação (só a pasta `target\` fica, para compilar rápido). O ajuste também confere se a linha já existe.
- **Android, Minecraft Java: "Cannot find a Java installation ... languageVersion=8".**
  - O `MioLibPatcher` do Amethyst pedia um JDK 8 instalado no PC. Agora ele compila para Java 8 com o JDK que você já tem (`javac --release 8`), sem baixar nada.
- **Android, Java dentro do APK.**
  - Os Javas vinham do nightly.link, que expira (por isso "não baixou o Java 8 / jre-21").
  - Agora vêm dos **releases fixos** do próprio Amethyst no GitHub. Entram no APK: **Java 8, 17, 21 e 25**.
  - Ficam guardados em `_build\jre-cache` e não baixam de novo.

## Celular muito mais rápido
- **Empresa (e Banco, Loja, Uber), a causa da lentidão:**
  - o MineSync perguntava à central a cada 2 s, numa fila só, e cada resposta esperava a próxima pergunta;
  - cada toque no app levava de 2 a 5 s.
- **A correção:**
  - **MineSync 6.2 + BotCraft 4.17:** a central segura a pergunta aberta (*long polling*) e entrega o pedido **na hora**;
  - as respostas saem em paralelo. Testado aqui: o pedido chega ao servidor em menos de 0,3 s e a resposta volta na hora;
  - **app Empresa:** abre na hora com a última lista guardada e atualiza por trás, sem tela de "carregando". Os detalhes das suas empresas já vêm buscados antes de você tocar;
  - **todos os apps:** leituras iguais ao mesmo tempo viram uma só, e voltar para uma tela não busca tudo de novo.
- **Horizon:** o minimapa lia o mundo inteiro pelo mod a cada poucos segundos, o que pesava o jogo e o motor do launcher.
  - Agora, no Horizon (com ou sem o mod), o mapa vem pelo leitor leve: um pedaço por vez.

## Câmera do celular ao vivo (Windows)
- A tela da Câmera mostra o jogo **em tempo real** (cerca de 9 quadros por segundo), com o selo "● AO VIVO", como a câmera de um celular.
- **Não tira mais print ao abrir.** A foto só sai quando você aperta o botão branco.
  - Ela vai para a Galeria com o filtro e o recorte escolhidos.
  - A miniatura abre a Galeria e "📤" compartilha (MineGram, Zap).
- **Como funciona:** o app do PC copia só a janela do jogo, mesmo com o celular por cima. Se a placa de vídeo não deixar, ele copia a tela e deixa o celular e o minimapa fora da foto (Windows 10 2004+).
- **Tela cheia exclusiva** (imagem preta): a câmera avisa para usar F11 (janela sem bordas). No Horizon, ela usa o print do próprio jogo, devagar e sem salvar na Galeria.
- Os outros apps (Zap, MineGram) também usam a câmera ao vivo para "Tirar foto".

## Navegador do celular
- **A página inicial agora é `https://www.foxsrv.net/`.** O Google continua nos favoritos e a busca usa o Google.
- **Sites que não abrem dentro de outra página** (bancos, login do Google…) são detectados sozinhos e abrem em tela cheia, em vez de mostrar uma página em branco.
- **No Android também funciona:**
  - os cliques dentro dos sites ficam no celular (antes iam para o navegador do Android);
  - o "tela cheia" abre numa tela do próprio app, com "← MineBot" para voltar;
  - dentro do Horizon, abre por cima do jogo, sem sair dele.

## Horizon: mod ligado ou desligado (Windows e Android)
- **Windows:** Jogar → Horizon → "Mod do MineBot no Horizon". **Android:** Jogar → Horizon → "Mod MineBot".
- **Ligado:** todas as automações, a câmera livre e o minimapa pelo mod.
- **Desligado:** o Horizon puro, mais rápido. O celular, a voz e o minimapa continuam por cima do jogo.

## Mods Forge e Fabric (novo: `mods/`)
Para quem joga sem o launcher:
- entrar na conta FoxLand **na tela inicial do Minecraft** (usuário e senha, ou pelo navegador);
- skins;
- login automático nos servidores da rede;
- **o celular no Shift direito:** MineGram, Zap com ligação, Banco, Loja, Empresa, Uber, Navegador e a mesma voz, numa janela presa ao jogo.

Detalhes e como compilar: `mods/LEIA-ME.md`.

## O que foi testado aqui
- **Central:** os 31 testes de antes e os 6 novos do long polling, todos passando. Entre eles:
  - o pedido chega ao servidor em menos de 0,3 s;
  - dois MineSync nunca pegam o mesmo pedido;
  - o MineSync antigo continua funcionando.
- **MineSync 6.2.0:** compilou (`MineSync-6.2.0.jar`).
- **Celular (navegador de verdade, Chromium):**
  - a câmera mostra os quadros ao vivo, não tira print ao abrir, salva só no botão e desliga ao sair;
  - o navegador abre na FoxLand, e o site que recusa iframe abre em tela cheia;
  - os testes de antes passam: Empresa, celular do site, PC, BindCraft.
- **Mods:**
  - a parte da conta, contra uma central falsa: login, senha errada, celular logado, login automático, skins, token criptografado e sair;
  - o resto compilou contra versões mínimas das classes do Minecraft, do Fabric e do Forge.
- **Android:**
  - o ajuste do `Cargo.toml` rodado três vezes seguidas no código do Horizon: o arquivo continua válido;
  - o `build.gradle.kts` do MioLibPatcher modificado é aceito pelo Gradle.
- **Sintaxe:** `node --check` em todo o JavaScript mudado.

## Não deu para testar aqui
Faltam neste ambiente os compiladores e o acesso aos repositórios:
- **O C# do launcher.** Não há compilador C# aqui: a câmera ao vivo (`Camera.cs`) foi escrita em C# 5 e conferida à mão. Rode o `COMPILAR-WINDOWS.bat`.
- **O APK.** Faltam o Flutter, o Android SDK e o acesso ao Maven e ao crates.io.
  - Corrigi os três erros do seu log.
  - Se aparecer um próximo, ele vem com a mensagem certa no passo [3/7] ou [4/7]. Me mande.
- **Os mods de verdade.** Não há acesso aos repositórios do Minecraft, do Fabric e do Forge. Se o Gradle reclamar de algum nome, mande a mensagem.
- **Servidores reais e duas pessoas usando ao mesmo tempo.**

## NÃO compartilhe
- **Arquivos:** os zips, `config.yml`, os `.db`, os `.key`, os backups, `assinatura\` e `key.properties`.
- **Pastas do PC:** `%LOCALAPPDATA%\DLPNet`, `%LOCALAPPDATA%\FoxLand`, `secrets.bin`, `data/peer.json` e `overlay-bg.jpg`.
- **Do MineSync:** `plugins/MineSync/peer/identity.json`, o `drive/`, `plugins/MineSync/access.yml` e `plugins/MineSync/legacy/`.
- **Da rede:** o `data/` dos peers, `local/ia/storage/`, o `storage/` da central, `FoxLand servers\`, `central\`, `cloudflared\` e `modules/plugins/botcraft/data/siteimg/`.
- **Do Android:** `keystore\` (com o `senha.txt`) e `_build\`.
- **Do mod:** `config/minebot/` (o `account.bin` e o `phone-browser\`).
- **Do ServerHub:** o `config.yml`, o `data/`, o `install.json` e o `selfbot.json`.
- **As conversas do CraftZap** (`bc_ph_msgs`).
- Troque a senha `GATEWAY_ADMIN_PASSWORD` do coin bitcoin (está em texto puro).

---

# MineBot 2.13.1: o launcher compila de novo

Só o C# do launcher mudou.

**Erro corrigido:** `Overlay.cs(407): Acessador get ou set esperado`. Um comentário no fim de uma linha do minimapa
"engolia" a chave `}` que fechava o bloco.

**Também corrigido (o compilador ia parar neles depois desse):**
- uma variável chamada `out` (palavra reservada do C#) em `TrayApp.cs`;
- o método `Layout` da janela do celular tinha o mesmo nome do evento `Layout` do Windows Forms. Ele virou
  `PlaceWindow`.

**Conferido aqui:**
- chaves, parênteses e colchetes de todos os `.cs` (app, setup e bootstrap). O mesmo teste acusa o erro do 2.13.0;
- nenhum recurso de C# 6 ou mais novo (o `csc.exe` do Windows é C# 5);
- nenhuma palavra reservada usada como nome.

**Não conferido aqui:** a compilação completa (não há compilador C# neste ambiente). Rode o `COMPILAR.bat`.

---

# MineBot 2.13.0: ligações com som, voz no navegador que conecta e o celular inteiro no site (copie por cima do 2.12)

| Pasta | O que mudou | Versão |
|---|---|---|
| `site/modules/plugins/botcraft` | Voz: o navegador conecta sempre e a ligação toca no aparelho em uso; **celular no navegador** em `/botcraft/phone/` | **BotCraft 4.14.0** |
| `site/modules/plugins/serverhub` | Voz do site com a ligação aberta e o botão "📱 Celular" no menu | **ServerHub 1.2.0** |
| `launcher/MineBot-Launcher` | Microfone aberto nas ligações e áudio que não fica "suspenso" | **2.13.0** |
| `android/MineBot-Android` | O mesmo celular (arquivos sincronizados) | 2.13.0 |

O MineSync (6.1.0) e o mod do Horizon (1.0.0) não mudaram.

## Ligação "praticamente muda" (corrigido)
Havia quatro causas, uma em cada ponta:
1. **Launcher:** a ligação passava pela detecção de voz da proximidade (RNNoise ≥ 0,6), que cortava quase tudo. Agora o
   microfone fica **aberto a ligação inteira**, como um telefone, e respeita só o "microfone mudo".
2. **Launcher:** a janela de voz fica escondida, e o navegador interno podia deixar o som **suspenso**. Agora o
   launcher libera o som sem precisar de clique e tenta retomar o áudio sozinho.
3. **Site:**
   - com o microfone em "desligado", a ligação ia muda. Agora atender ou ligar já abre o microfone (o clique é a
     permissão);
   - fora do jogo, o site também não **ouvia** a ligação. Agora ouve.
4. **Detecção de voz da proximidade:** o padrão baixou de 0,6 para 0,4, para não cortar as palavras.

Testado aqui com o áudio de verdade (Opus) em dois sentidos:
- launcher ↔ navegador, com microfone simulado;
- o navegador mandou cerca de 75 quadros em 1,5 s e o outro lado decodificou.

## "Conectando…" para sempre no navegador (corrigido)
- **Antes:** a central só liberava a voz do site com o personagem online num servidor e com o launcher fechado.
- **Agora:** o navegador **sempre** conecta.
  - Fora do jogo: ligações e o celular.
  - No jogo: também a voz de proximidade, junto com o Simple Voice Chat do servidor (a ponte do MineSync).
- Launcher e navegador podem ficar ligados ao mesmo tempo. A ligação toca no aparelho que você usou por último.

## Celular no navegador: `https://<site>/botcraft/phone/` (botão "📱 Celular" no ServerHub)
- É a mesma tela do celular do launcher, no PC (o aparelho no meio da tela) ou no celular (tela cheia).
- **Funciona no navegador:** MineGram, Zap (conversas, fotos, áudios e **ligações**), Banco (carteira, transferir,
  extrato, claim), Loja do servidor, Empresa, Uber, Navegador.
- **Só no launcher** (mexem no jogo do seu PC): BotCraft, Mapa, Câmera, Galeria, Câmera livre, BindCraft e
  Configurações.
- **Como funciona:**
  - entra com a conta do site (cookie);
  - as rotas do launcher aceitam essa sessão só no celular e só com o cabeçalho `X-FL-Web: 1`, então outro site não
    consegue usar a sua conta.
- **Arquivos:** `site/phone/` é gerado a partir do celular do launcher por `tools/sync-web-phone.js`. Rode esse
  script de novo quando mudar o celular do launcher.

## Testado aqui
- **Central:** 31/31.
- **Voz pela central:** launcher e navegador juntos, ligação nos dois sentidos com volume cheio, e reconexão do
  launcher sem derrubar o site.
- **Celular do site no Chromium:** PC e celular, todos os apps do navegador, com a CSP do site.
- **Celular do launcher:** sem erros de JavaScript.

## Não testado aqui
- **C#:** uma linha nova (liberar o som sem clique). Rode o `COMPILAR.bat`.

---

# MineBot 2.12.0: celular por cima do jogo estilo Discord, BindCraft e uma tecla só (copie por cima do 2.11)

Só o **launcher** e o **celular** mudaram. A central (BotCraft 4.13.0), o MineSync 6.1.0 e o mod do Horizon são os do 2.11.

## Teclas: tudo no celular agora
| Tecla | O que faz |
|---|---|
| **Shift direito** | Abre e fecha o celular. Se o celular ficou aberto e você voltou a jogar, ela traz o celular de volta. |
| **F6** | Liga e desliga as automações na sua conta. |
| **F2** | É o print do próprio jogo: ele já aparece na **Galeria** do celular (Java e Horizon) e vai para a área de transferência. |
| **F11** | Deixa o jogo em **tela cheia sem bordas** do launcher (veja abaixo). F11 de novo volta como estava. |

- As teclas M (mapa) e B (celular extra) saíram. A troca é feita uma vez, sozinha, e essas teclas ficam livres para o
  BindCraft.
- O mapa continua no app Mapa do celular.

## App novo: BindCraft (ícone de prompt `>_`)
- Cada atalho é uma tecla (com Ctrl/Alt/Shift se quiser) que manda um ou mais comandos ou mensagens no jogo.
  Exemplo: **M → `/tell Amigo cheguei`**. Várias linhas viram uma sequência, com a espera que você escolher.
- Só dispara com a **janela do jogo na frente** e com o chat, a placa ou o livro fechados.
- Funciona no Minecraft Java e no Horizon (com ou sem o mod).
- **"Segurar a tecla":** a tecla não chega ao jogo. Use quando o jogo ou um mod também usa essa tecla.
- Tem botão **Testar**, ideias prontas (/spawn, /home, /tpaccept…) e ligar/desligar por atalho.
- **Limites:**
  - 20 atalhos, com 10 linhas cada;
  - um disparo por segundo por atalho;
  - Shift direito, F6 e F11 não podem ser usados.

## Telas pretas, jogo congelado e briga de janelas (corrigido)
### O celular e o minimapa ficam "presos" ao jogo, como o overlay do Discord
- Eles não ficam mais "sempre no topo" de tudo: viraram janelas da própria janela do jogo.
- Clicar em outro programa não esconde nada nem puxa o foco de volta. Antes o celular puxava a frente a cada 0,3 s,
  e era isso que deixava ruim trocar de janela.
- Com o jogo atrás de outro programa, as sobreposições continuam sobre o jogo. Com o jogo minimizado, elas somem
  junto.
- O celular só fecha pela tecla, pelo Esc ou pelo botão.
- O minimapa aparece sempre sobre o jogo, mesmo sem o jogo estar em foco.

### Jogo congelado ou preto ao abrir o celular
1. **Fim da tela cheia exclusiva no Java.** O Minecraft nessa tela cheia se minimiza quando outra janela vem para a
   frente, e a tela fica preta ou congelada. Agora o jogo abre em janela e o F11 vira tela cheia sem bordas, então o
   jogo continua rodando atrás do celular.
2. **Nada de foto do jogo para o celular.** A cópia da tela travava o jogo um instante e saía preta em tela cheia. O
   celular agora é uma janela pequena com o jogo ao vivo em volta.

Uma opção nova em Configurações › Teclas desliga a tela cheia sem bordas.

## Instalar
Rode o `COMPILAR.bat` do launcher e troque o instalador.

## Testado aqui
- **BindCraft no motor:** regras dos atalhos e disparo em sequência.
- **BindCraft no celular:** criar, testar e salvar um atalho, no Chromium e no modo do WebView2.
- O resto do celular sem erros de JavaScript.

## Não testado aqui
- **C# não compilado** (não há compilador neste ambiente). Ele cuida de:
  - o gancho de teclado (atalhos, F11);
  - as janelas presas ao jogo;
  - a tela cheia sem bordas.
  
  Rode o `COMPILAR.bat` e teste no PC.

---

# MineBot 2.11.0: Horizon com todas as automações, celular corrigido e app Empresa completo (copie por cima do 2.10)

| Pasta | O que é | Versão |
|---|---|---|
| `site/modules/plugins/botcraft` | Central: rota nova do app Empresa (`/api/launcher/phone/company`) | **BotCraft 4.13.0** |
| `site/modules/plugins/serverhub` | Sem mudança desde o 2.10 | 1.1.0 |
| `launcher/MineBot-Launcher` | Launcher Windows: mod do Horizon instalado sozinho, celular em janela própria, app Empresa novo | **2.11.0** |
| `android/MineBot-Android` | Arquivos alterados do Android: mod do Horizon no APK, motor ligando no mod, app Empresa novo | 2.11.0 |
| `minesync/MineSync` | Código + **`MineSync-6.1.0.jar` pronto** (app Empresa respondendo no celular) | **6.1.0** |
| `horizon-mod/minebot_agent` | O mod nativo do Horizon (Rust), com os testes | **1.0.0** |
| `HORIZON-MOD.md` | Como o mod funciona, segurança e limites | |

## O que mudou

### Horizon (Rust) com todas as automações: mod "MineBot Agent"
- **Por que:** o BotAgent (Java) é o que deixa as automações, a câmera livre e o minimapa funcionarem. No Horizon só
  havia o socket de controle, que responde perguntas pequenas. Por isso só as automações leves funcionavam nele.
- **O que é:** um mod nativo do Horizon, feito com o modelo oficial (horizon-mixins) e escrito em Rust. Dentro do
  jogo, ele responde a **mesma API do BotAgent**: mundo em seções, entidades, jogadores, inventário, telas, chat,
  teclas, olhar, cavar, usar, atacar e conectar.
- **Resultado:** o peer da BotCraft, o minimapa, a experiência da IA e a câmera livre usam o Horizon pelo mesmo
  caminho do Java, sem nenhuma diferença.
- **Instala sozinho (Windows):** ao jogar pelo Horizon, o launcher faz três coisas:
  1. cria a instância "MineBot" no Horizon (os seus outros mods do Horizon ficam como estão);
  2. roda `horizon-launcher native-build --trust-native-code`, que baixa o Rust oficial e compila o jogo com o mod;
  3. abre o jogo.
  
  A 1ª vez leva de 5 a 30 minutos. O progresso aparece em Motor › Horizon.
- **Se a compilação falhar:**
  1. o launcher tenta de novo na versão do Horizon em que o mod foi escrito;
  2. se falhar de novo, abre o Horizon normal (automações leves) e mostra o motivo.
- **Quando o Horizon atualiza** e o mod some da versão nova, ele é instalado de novo na vez seguinte.
- **Botões novos** em Motor › Horizon: "🔧 Instalar o mod do MineBot" e a caixa "Mod do MineBot no Horizon"
  (desligada = Horizon puro).
- **Android:** o `tools/patch_horizon.ps1` coloca o mod dentro do Horizon do APK. O app passa a porta e o token, e o
  motor do celular liga no mod igual ao Minecraft Java. Se o Horizon mudar e o gancho não bater, o APK sai com o
  Horizon sem o mod.
- **Segurança:**
  - só liga quando o MineBot abre o jogo;
  - escuta só em 127.0.0.1, com token, e recusa pedidos de navegador;
  - respeita o "No AI" do Horizon.

### Celular: tela preta com Shift direito / M (corrigido)
- **A causa:** a janela do celular cobria o jogo inteiro e usava uma *foto* do jogo como fundo. Em tela cheia,
  DirectX ou OpenGL (Minecraft e Horizon), o Windows entrega essa foto **preta**. Além disso, o celular só aparecia
  depois da 1ª resposta do motor.
- **Agora:**
  - o celular é uma **janela do tamanho dele**, no canto do jogo, com cantos arredondados, e o jogo continua aparecendo
    e rodando em volta;
  - clicar no jogo fecha o celular;
  - o celular aparece na hora;
  - foto preta não vira fundo;
  - se faltar arquivo, aparece a explicação em vez de uma tela preta;
  - o Painel, o mapa grande e a câmera livre continuam do tamanho do jogo.

### App Empresa completo (nada de "a resposta aparece no chat")
- **Tudo acontece dentro do celular:**
  - **Resumo:** carteiras COIN e moeda do servidor, seu cargo e o que você pode fazer;
  - **Membros:** contratar (com sugestão de quem está online e perto), demitir, mudar cargo;
  - **Cargos:** salários e permissões;
  - **Caixa:** depositar e sacar;
  - **Vender:** anuncia o item da mão na loja em nome da empresa;
  - **Lojas:** NPCs de loja (colocar, listar, remover);
  - sair da empresa.
- A resposta de cada ação volta para o app (o MineSync 6.1 devolve as mensagens em vez de mandar no chat).
- **Criar empresa e editar cargos e salários** abre o painel da FoxLand dentro do celular.
- **Fora do servidor**, dá para ver, contratar, demitir e mudar cargos. **Caixa, venda e NPCs** precisam de você no
  servidor (mexem no seu saldo, na sua mão e na sua posição).

## Instalar o 2.11
1. **Central.** Copie `site/modules/plugins/botcraft`.
2. **Servidores.** Troque o MineSync pelo `minesync/MineSync/MineSync-6.1.0.jar`. O launcher também leva o 6.1.0 em
   `engine/mcserver`.
3. **Launcher.** Rode `COMPILAR.bat`. O mod vai junto no instalador x64, em `engine\horizon\native\minebot_agent`.
4. **Android.** Copie `android/MineBot-Android` por cima e gere o APK. Ele já leva `horizon_android/native` e o
   `tools/patch_horizon.ps1` novo.

## Teste antes de distribuir (2.11)
### Testado aqui
- **Mod (Rust):**
  - 11 testes de unidade;
  - um teste de ponta a ponta: o peer **de verdade** da BotCraft (`facade.js`) ligado num Horizon simulado com o mod.
    O bot entrou no mundo, leu blocos e registros, viu entidades e jogadores, andou, cavou, mandou chat e olhou.
- **Celular:**
  - no Chromium, no modo do WebView2: janela grande, janela pequena e `#app=map`;
  - o app Empresa: todas as abas, contratar e depositar.
- **MineSync 6.1.0:** compilado.
- **Central:** 31/31.

### Não deu para testar aqui
- **O mod compilado dentro do Horizon de verdade.** Este ambiente não baixa os crates do Rust, então a cola com o jogo
  (`glue.rs`) foi conferida linha a linha contra o código do Horizon de 01/11/2026 (`f8bfa06`), mas não compilada.
  - Se a compilação falhar, o launcher mostra o erro e abre o Horizon normal.
  - O log completo fica no `native/build.log` do Horizon.
- **O C# (janela do celular).** Rode o `COMPILAR.bat`.
- **Num servidor real:** caixa, venda e NPC da empresa.

---

# MineBot 2.10.0: atualização completa (copie por cima do MineBot 2.9)

| Pasta | O que é | Versão |
|---|---|---|
| `site/modules/plugins/botcraft` | Central: celular novo, banco, MineGram, Zap, loja, Uber, voz, instaladores, claim | **BotCraft 4.12.0** |
| `site/modules/plugins/serverhub` | Painel do dono + MineGram web + **🎙️ Voice no navegador** | **ServerHub 1.1.0** |
| `launcher/MineBot-Launcher` | Projeto inteiro do launcher para Windows (instalador, instalador pequeno, motor, celular novo) | **2.10.0** |
| `android/MineBot-Android` | Arquivos alterados do Android: motor, celular novo, microfone do WebView | 2.10.0 |
| `minesync/MineSync` | Código + **`MineSync-6.0.0.jar` pronto** (Uber, banco, loja, /zap, ponte com o Simple Voice Chat) | **6.0.0** |
| `NOTAS-VOZ.md` | Voz: Simple Voice Chat, navegador, chamadas (protocolo e limites) | |
| `PHONE_API.md` | Como escrever um app novo para o celular | |

## Instalar
1. **Central.** Copie `site/modules/plugins/botcraft` e `serverhub` para `modules/plugins/`. Recarrega sozinho em até 15 s; `config.yml`, `data/` e `storage/` são mantidos.
2. **Servidores.**
   - Troque o jar do MineSync pelo `minesync/MineSync/MineSync-6.0.0.jar`.
   - **Remova o `UberCraft.jar`**: o Uber agora está dentro do MineSync e importa as warps e a configuração na primeira vez. Enquanto o UberCraft estiver instalado, o Uber do MineSync fica desligado.
3. **Launcher (Windows).**
   - Rode `launcher/MineBot-Launcher/COMPILAR.bat`. Ele gera:
     - `out\MineBot_Launcher.exe`, o instalador completo;
     - `out\MineBot_Launcher_Web.exe`, o instalador pequeno.
   - Ponha o instalador completo em `storage/assets/MineBot_Launcher.exe`, junto com o `MineSync.jar` (o 6.0.0 renomeado). Versões antigas vão em `storage/assets/launcher/MineBot_Launcher-<versão>.exe`.
4. **Android.** Copie `android/MineBot-Android` por cima e gere o APK.

## Teste antes de distribuir
- **O C# não foi compilado aqui** (não há compilador C# neste ambiente). Rode o `COMPILAR.bat` e veja se compila sem erro antes de distribuir.
- **O que foi testado aqui:**
  - a lógica da central: banco, MineGram, Zap, loja, Uber, voz, vínculo e Discord;
  - o celular no Chromium, todos os apps, sem erros de JavaScript;
  - o MineSync 6.0.0 compilado;
  - a central carregando com as 202 rotas.
- **O que ainda não rodou de verdade:** num servidor real 1.21.6 (happy ghast do Uber, Simple Voice Chat) e no launcher real.
