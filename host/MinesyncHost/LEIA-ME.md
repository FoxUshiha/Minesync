# MinesyncHost 2.1.2
- **Progresso real** para o site: backup, restauração, compactar e extrair informam a % (bytes já gravados/lidos ÷ total),
  arquivos, arquivo atual e tempo restante; instalação e downloads (jar, MineSync, runtime Java/Node/Python) informam a % do
  download; dependências (npm/pip), backup da central e migração também aparecem.
- Corrigido: o backup de servidores que não são Minecraft (Node/Python/Java) esperava 30 s à toa (mandava `save-all`).

# MinesyncHost 2.1.1
- O **server.jar pode ser renomeado, trocado, enviado e apagado** pelo explorador como qualquer arquivo (antes era protegido nos
  servidores Minecraft). Só o `eula.txt` e os argumentos do Forge continuam protegidos.
- O **arquivo principal** (o jar que liga) pode ser escolhido pelo cliente: precisa existir na pasta do servidor e ser `.jar`
  (Java/Minecraft). As flags do Java continuam travadas.

# MinesyncHost 2.1.0 — painel local só para vincular; tudo o mais pelo site

**O que mudou para quem usa**
- **Painel local (http://localhost:8765) só faz o vínculo com a sua conta**: pelo site, usuário e senha ou código. Não há
  mais usuários locais. Ele mostra a máquina medida (CPU e GHz máx., RAM e velocidade, disco e tipo/velocidade, internet,
  sistema), a divisão **sistema / seu / rede** e o endereço da central (com o botão **Voltar ao padrão** = https://www.foxsrv.net/).
  Só abre do próprio PC (127.0.0.1 / localhost) — nada do painel fica exposto na rede ou no Cloudflare.
- **Sem conta vinculada**: depois de 5 min conectada, a máquina inteira (menos a reserva do sistema) fica à venda na rede.
- **Com conta**: tudo é configurado no site (servers/host.html → Minhas máquinas): sua parte 0–100% (o seu servidor grátis), a
  reserva do sistema, limite de disco, Cloudflare (rápido ou permanente com token), IP fixo + portas + DNS, banda e "medir de novo".
- Corrigido o erro que deixava a host **sem recurso disponível**: a configuração antiga (`enabled`/`share_with_network`)
  bloqueava o agente; agora a host sempre liga para a central e registra na hora (sem esperar 30 s).

**Servidores**
- RAM com teto: `-Xmx` = plano − folga (15%, entre 160 MB e 1 GB, para o GC e o próprio Java) + limites de metaspace, code cache e
  memória direta. Vigia de RAM: aviso e `save-all` em 99%; acima do limite por 20 s (ou 115%) → para. Vigia de resposta: sem
  responder por 90 s (depois de 3 min ligado) → é parado.
- Gráficos ao lado do console (CPU 0–100% por núcleo: 4 núcleos = 400%, RAM, disco e ping).
- Paper/Purpur/Folia/Canvas/Spigot: instala sozinho o **MineSync** mais novo e verificado da central (storage/assets).
  Jar do servidor: primeiro da **biblioteca da central** (sha256 conferido), depois da internet.
- Novas operações da central: `config`, `bench`, `proxy` (túneis só para portas abertas pelo próprio servidor), `metrics`,
  `limits` (plano mudou / modo grátis), `retype` (trocar tipo/versão) e `wipe` (fatura vencida — a central já guardou o backup).
- Exportações e backups em zip nível 9.

**Limites honestos**
- Isolamento: no Linux com `bwrap` (bubblewrap) o servidor roda numa caixa fechada (só a pasta dele, PID/IPC/UTS próprios);
  no Windows não existe isolamento de arquivos de verdade sem código nativo — lá vale a pasta própria, o usuário do sistema que
  roda a host e a integridade conferida pela central.
- Cada servidor escuta em 127.0.0.1 numa porta própria (sem conflito); IP de rede próprio por servidor não é feito.
- A banda por servidor é cobrada/limitada no plano, mas a host ainda não "estrangula" o tráfego de cada um.

# MinesyncHost 2.0.0 — a host vira um NÓ da rede: tudo é controlado pela central (site)

> **Mudou o jeito de usar.** O painel local agora só configura o nó. Criar, abrir, gerenciar, arquivos e backups
> dos servidores é **pelo site da central** (`/servers/host.html`). O cliente **nunca** abre o endereço da sua host.

## 🖥️ Painel local (http://localhost:8765) — só isto
- **Painel**: uso do PC, estado da ligação com a central e os **servidores da central nesta host só para ver**
  (sem nome do cliente, sem arquivos: aparece "+1 servidor" e quanto ele usa).
- **Rede**: **Contribuir com a central** (ligado por padrão = aparecer no mercado), nome da host, vincular a conta
  (pelo site, usuário e senha da central ou código), **preços por núcleo / GB de RAM / GB de disco** e **banda**.
- **Recursos**: total que a host pode usar, **reserva do sistema**, **reserva do dono** (o seu servidor grátis),
  a **parte da central** (calculada: total − sistema − dono) e o **máximo por servidor de cliente**.
- **IP fixo / DNS** e **Cloudflare** (aumentam a reputação), **Configurações** (isolamento, idioma, limites de backup, senha).
- O painel local **não mexe** em contas nem em servidores da central: ligar, abrir arquivos ou apagar dá `central_managed`.

## 🛰 Ligação com a central (agente)
- A host abre uma conexão **de saída** (WebSocket) com a central (`/botcraft/hn/agent`) — sem IP fixo e sem abrir portas.
- Arquivos grandes (enviar/baixar/zip/backup) passam **em fluxo** pela central (sem guardar em memória).
- A central manda: criar, painel (as mesmas rotas do painel antigo: console, arquivos, compactar, backups, agendamentos,
  inicialização), apagar, suspender, verificar, exportar/importar (migração) e guardar backups.

## 🔐 Integridade
- Depois de cada operação da central e quando o servidor desliga, a host tira uma "foto" da pasta (tamanho + data
  de cada arquivo e **sha256** dos .jar). A cada 10 min confere: arquivo mudado/apagado/criado **fora da central**
  com o servidor desligado = **violação** (−100 a −500 de reputação; 3 em 30 dias ou apagar o servidor = **host suspensa 7 dias**).
- Tudo vai para o **log da host** que o admin vê. A mesma mudança não é punida duas vezes.
- É uma conferência de boa-fé: quem controla o PC pode modificar o próprio programa — por isso existe o **backup diário
  da central** em outra host, que permite **migrar** o servidor se a host sumir com ele.

## ☕ Runtimes por versão (isolados, baixados na hora)
- **Java 8, 11, 17, 21, 25** (Eclipse Temurin), **Node.js 18, 20, 22, 24** (nodejs.org) e **Python 3.10–3.13**
  (python-build-standalone): binários oficiais do sistema/arquitetura do PC, **conferidos com sha256**, guardados em
  `./runtimes/` (nada é instalado no sistema). O cliente escolhe a versão na aba **Inicialização** do site.
- Dependências na hora: `npm install` (com o npm do Node escolhido) e `pip install -r requirements.txt --target .pydeps`.
- **Horizon**: tipo novo (envie o executável do servidor Horizon em Arquivos; o padrão é `horizon-server`).
- Argumentos, flags, variáveis e arquivo principal: **travados para o cliente** — só o dono da host (ou o admin) muda, por servidor.

## ⭐ Servidor grátis do dono
- Cada host vinculada dá **1 servidor grátis** ao dono (até a reserva do dono) ou **ILIMITADO** (o PC inteiro — aí a host sai do mercado).

## config.yml novo
```yaml
enabled: true            # contribuindo
node:
  os_reserve:    { ram_mb: 2048, cores: 1, disk_gb: 20 }
  owner_reserve: { ram_mb: 0,    cores: 0, disk_gb: 0 }
  instance_max:  { ram_mb: 8192, cores: 4, disk_gb: 50 }
network:
  agent: true
  bandwidth_mbps: 100
  prices: { per_core: 0, per_gb_ram: 0, per_gb_disk: 0 }
```
**Não compartilhe**: `servers/.msh/` (inclui `integrity/`), `central-store/` (backups da central guardados nesta host),
`runtimes/`, `backups/`, `cloudflared/`.

# MinesyncHost 1.3.0 — host de verdade: contas, Java/Node.js/Python, inicialização, agendamentos e entrar pela rede

## 👥 Contas (aba **Usuários**, só o dono da host)
- **Criar contas**, cada uma com:
  - **limites**: quantidade de servidores, RAM, núcleos e disco, somando os servidores dela, mais backups e MB de backup;
  - **tipos liberados**;
  - **permissões da conta**: criar servidores, apagar os próprios, *tipos próprios* (Java com .jar próprio, Node.js, Python, trocar arquivo principal/Java/flags/variáveis), editar inicialização, agendamentos e convidar usuários;
  - **suspender** (derruba a sessão e desliga os servidores dela), **trocar a senha**, **observação** e a **conta da rede vinculada**.
- Campo vazio = o padrão de `accounts.default` (aba **Configurações › Contas da host**).
- **Criar servidor PARA uma conta**: no "Criar servidor" o dono escolhe a **conta dona**. O servidor entra no limite dela e ela só vê os dela.
- **Transferir** o servidor para outra conta (Ajustes, dono da host).
- **Subusuários** (aba **Usuários** do servidor): o dono do servidor dá acesso a outra conta escolhendo as permissões: console, ligar/desligar, ver arquivos, mexer nos arquivos, backups, inicialização, agendamentos e ajustes.
- **Criar conta pela tela de entrada** (`accounts.signup`, com código de convite opcional).
- **Minha conta**: limites e uso, permissões, trocar a senha (pede a atual) e vincular a conta da rede.
- **Atividade** por servidor: quem fez o quê e quando (últimas 300 ações).

## 🌐 Entrar com a conta da rede (sem código)
- **Pelo site**: o botão abre o site da rede numa aba nova, você só clica em **Confirmar** (já logado no site) e o painel entra sozinho.
  - Vale para **entrar no painel**, **vincular a sua conta** (Minha conta) e **vincular a HOST** (aba Rede).
- **Por usuário e senha da central**: a senha vai direto para a central e **não fica guardada**.
- A host recebe só o **usuário** da conta da rede (nunca a senha nem o SSN) e encerra a sessão da central na hora.
- `accounts.network_login`: liga/desliga entrar pela rede. `accounts.network_signup`: cria a conta da host sozinha na 1ª entrada, com os limites padrão.
- O código de vínculo antigo continua em "Ou com código".

## 🚀 Tipos e inicialização (aba **Inicialização**)
- **Tipos novos**:
  - **Java** (qualquer `.jar`: proxy, bot, servidor modificado…);
  - **Node.js** (`index.js` de exemplo que já sobe);
  - **Python** (`main.py` de exemplo).
- **Arquivo principal**: `server.jar` é o padrão, mas dá para escolher outro (ex.: `paper-custom.jar`, `bot.jar`, `src/index.js`).
  - Nos servidores de Minecraft o `server.jar`/`eula.txt` continuam protegidos: para usar um jar próprio, envie com outro nome e escolha como principal (precisa da permissão *Tipos próprios*).
- **Java por servidor**: Javas extras em `runtimes.java`, ex.: `17=/usr/lib/jvm/java-17-openjdk/bin/java`.
- **Flags** (JVM/Node/Python), **argumentos**, **variáveis de ambiente**.
  - Variáveis prontas: `{{PORT}} {{IP}} {{RAM}} {{MAIN}}`, mais `SERVER_PORT`, `SERVER_IP` e `SERVER_MEMORY` no ambiente.
  - `PATH`, `HOME`, `LD_*`, `JAVA_TOOL_OPTIONS` e `NODE_OPTIONS` são recusadas.
- **Comando para parar**: `stop` no Minecraft; vazio = sinal de parar (Node/Python).
- **"Pronto quando aparecer no console"** e **ligar de novo se cair** (até 3 vezes em 10 min).
- **Dependências**: `npm install` / `pip install -r requirements.txt --target .pydeps` ao ligar, só quando o `package.json`/`requirements.txt` muda.
- **Prévia do comando** que vai rodar.
- Isolamento igual aos outros: pasta própria, ambiente limpo e, no Linux com bubblewrap, caixa fechada (o Node/Java fora do /usr entra só-leitura).
- Node.js e Python usam os instalados no PC da host (`runtimes.node` / `runtimes.python` para outro caminho).

## ⏰ Agendamentos (aba **Agendamentos**)
- **Quando**: a cada X minutos, todo dia às HH:MM, ou em dias da semana às HH:MM.
- **O quê**: comandos no console (um por linha), reiniciar, ligar, desligar ou backup (respeita o "manter N" e os limites).
- "Só se estiver ligado", ativar/desativar e "Rodar agora".

## ☁️ Entrar com a Cloudflare (sem token para colar)
1. Na aba **Cloudflare**, clique em **Entrar com a Cloudflare**. A página da Cloudflare abre, você escolhe o domínio e autoriza.
2. A host recebe o certificado sozinha e mostra o domínio. Escolha o endereço (ex.: `painel` + `.seudominio.com`) e clique em **Criar túnel e ligar**.
3. A host cria o túnel, aponta o DNS, escreve o `tunnel.yml` (só o painel) e liga. Aparece **Abrir o painel pela internet**.
- **Sair da Cloudflare** desliga, apaga o túnel da conta (quando dá) e os arquivos daqui.
- O modo rápido (trycloudflare) e o token continuam em "Outras formas".
- **NÃO compartilhe** a pasta `cloudflared/`: ela guarda `cert.pem`, `tunnel-credentials.json` e `tunnel.yml`.

```yaml
accounts:
  signup: false          # qualquer um cria conta na tela de entrada
  signup_code: ""        # código de convite (vazio = sem código)
  network_login: true    # entrar com a conta da rede (site / usuário e senha da central)
  network_signup: false  # cria a conta da host na 1ª entrada pela rede
  default:
    perms: [create, delete, startup, schedules, subusers]   # + custom = tipos próprios
    types: []            # vazio = todos (Java/Node/Python só com "custom")
    max_servers: 1
    max_ram_mb: 2048
    max_cores: 1
    max_disk_gb: 10
runtimes:
  java: []               # ["17=/caminho/do/java"]
  node: ""               # vazio = o do PATH
  python: ""
```

# MinesyncHost 1.2.0 — backups e compactar/descompactar no explorador

## 🗄 Backups (aba **Backups** de cada servidor)
- **Fazer backup** com nome (opcional). Com o servidor **ligado**, a host manda `save-off` + `save-all flush`, espera o "Saved the game", copia e manda `save-on`: o mundo não sai pela metade.
- Guardados **fora da pasta do servidor** (`./backups/<servidor>/`), então o servidor e os plugins não mexem neles.
- **Limites por usuário**, somando todos os servidores dele: **quantidade** e **MB**.
  - Padrão no `config.yml` (`backups.max_count`, `backups.max_mb`) ou na aba **Configurações**.
  - Limite de cada usuário na aba **Usuários** (vazio = padrão).
  - Teto total da host: `backups.max_total_gb` (0 = sem teto).
- **Chegou no limite?** O botão abre a lista dos seus backups (de qualquer servidor seu) para escolher um **antigo e sobrescrever**. O antigo só é apagado **depois** que o novo terminou certo; se o novo falhar, o antigo continua.
- **Backup automático**: de X em X horas (1, 2, 3, 6, 12, 24, 48, 72 h ou semanal), mantendo N automáticos.
  - No limite, troca o automático mais antigo. Nunca troca backup manual nem travado.
  - O menor intervalo permitido é `backups.min_every_h`.
- **Travar (🔒)**: o backup não pode ser sobrescrito nem apagado (pela automação nem por engano) até destravar.
- **Restaurar**: desliga o servidor, extrai numa pasta ao lado, troca as pastas (sobras somem) e **liga de novo** se estava ligado. Durante a restauração, arquivos e ligar ficam bloqueados.
- **Baixar** o backup (.zip), **renomear**, **apagar**.

## 🗜 Compactar e descompactar (aba **Arquivos**)
- **Seleção múltipla** (caixinhas e "Todos"). Com a seleção você pode **Compactar**, **Baixar (.zip)**, **Descompactar** (se for um pacote) e **Apagar selecionados**.
- **Compactar** em `.zip`, `.tar.gz` ou `.tar` (pelo nome que você digitar). Por item: 📦 descompactar, ⬇ baixar (pasta = .zip na hora), 🗜 compactar.
- **Descompactar** `.zip`, `.tar.gz`/`.tgz` e `.tar` aqui ou numa pasta escolhida. Clicar num pacote também pergunta se quer descompactar.
- **Enviar e descompactar**: marque 📦 ao lado de "Enviar arquivo".
- **Baixar a pasta atual inteira** em `.zip` (⬇ .zip no topo).
- O progresso aparece no topo: arquivos, MB e o arquivo atual.
- **Segurança**:
  - Nada sai da pasta do servidor: `../`, caminhos absolutos, `C:` e atalhos/links simbólicos são recusados. Links simbólicos e "hard" de `.tar` são ignorados.
  - `server.jar`/`eula.txt` nunca são trocados por um pacote.
  - Limite de MB e de arquivos por descompactação (`archive.max_extract_mb`, `archive.max_entries`) e o disco do servidor: um "zip bomb" para no meio e o que já tinha sido escrito é apagado.
  - Compactar não segue atalhos (não "puxa" arquivos de fora) e respeita o disco do servidor.
  - Cada usuário só mexe nos servidores e backups dele; o dono vê todos.

```yaml
backups:
  enabled: true
  max_count: 5        # backups por usuário (padrão)
  max_mb: 10240       # MB de backup por usuário (padrão)
  max_total_gb: 0     # teto de todos os backups da host (0 = sem teto)
  min_every_h: 1      # menor intervalo do backup automático
archive:
  max_extract_mb: 4096
  max_entries: 200000
```

# MinesyncHost 1.1.0 — ponte da rede pela host
- **Servidores sem o MineSync entram na rede sem IP fixo** (Vanilla, Fabric, Forge e Externo): ao ligar, a host cadastra o servidor na central (o nome dele, ou MineServ### se já existir), mantém a sessão com MOTD, versão e jogadores lidos pelo ping do Minecraft, e abre a ponte (relay da central) até 127.0.0.1:porta — o mesmo protocolo do MineSync. Jogadores entram pelo "Jogar" do launcher MineSync, pela lista do jogo ou pelos portais.
- **Sem duplicar**: se o servidor tiver o MineSync em plugins/, quem faz a ponte é o plugin; tirou o plugin, a host volta a fazer com o mesmo cadastro.
- **Tipo Externo**: para um servidor que já roda por fora (Horizon, outro programa) neste PC ou na rede local — só a ponte. Ligar = aparecer na rede.
- **Por servidor**: "Rede sem IP fixo (ponte da host)" nos Ajustes. **Na host** (aba Rede): ponte ligada/desligada e o caminho direto pela rede local (`network.bridge_lan`, porta própria 4xxxx só para IPs da rede local; desligado por padrão para manter o isolamento).
- `limits.bandwidth_mbps` agora vale para o tráfego da ponte.
- Login dos jogadores num servidor sem MineSync: o do próprio servidor (`online-mode=true` pede conta Microsoft; `false` aceita qualquer nick). O login automático da conta FoxLand, voz, loja e portais vêm com o MineSync.

# MinesyncHost 1.0.0

Uma "hospedagem" leve num `.jar` só: transforma qualquer PC ou VPS numa host de servidores de Minecraft, com painel no navegador (português/inglês, tema claro/escuro).

## Instalar
1. Crie uma pasta e coloque o `MinesyncHost.jar` dentro (precisa do **Java 21** ou mais novo).
2. Rode `java -jar MinesyncHost.jar` (opções: `--port 8765`, `--dir PASTA`). Na primeira vez ele cria:
   ```
   ./MinesyncHost.jar
   ./config.yml      (enabled: false = hospedagem DESLIGADA até ligar no painel)
   ./cloudflared/    (o cloudflared é baixado sozinho quando a aba Cloudflare é ligada)
   ./servers/        (uma pasta por servidor · servers/.msh/ = usuários, servidores e a identidade da host)
   ./backups/        (1.2: backups dos servidores · NÃO compartilhe: são cópias completas dos servidores)
   (NÃO compartilhe também: servers/.msh/ — contas, servidores, atividade — e cloudflared/ — certificado e credenciais do túnel)
   ```
3. Abra **http://localhost:8765**, crie o usuário e a senha do dono e ligue a hospedagem.

## O que o painel faz
- **Servidores**: criar (Paper, Purpur, Folia, Canvas, Spigot, Vanilla, Fabric, Forge — sempre das fontes oficiais, com SHA-256 quando a fonte informa; não aceita `.jar` próprio como servidor), RAM 800 MB–64 GB, 1–32 núcleos, disco 4–128 GB, EULA obrigatório.
- **Ligar, desligar, reiniciar, forçar parada, reinstalar/trocar versão, apagar**; **console** ao vivo com comandos.
- **Explorador de arquivos**: navegar, editar texto, enviar, baixar, criar pasta, renomear, apagar — preso à pasta do servidor (sem `../`, sem atalhos para fora; `server.jar` e `eula.txt` protegidos).
- **Limites**: `-Xmx/-Xms`, `-XX:ActiveProcessorCount`, núcleos fixos (taskset no Linux, afinidade no Windows), disco conferido a cada minuto (passou → desliga), RAM física vigiada (estourou muito → força a parada), GC G1 automático.
- **Isolamento** (`isolated: true`, padrão): servidores só em 127.0.0.1 (a rede entra pela ponte do MineSync — nada exposto na rede local), ambiente limpo (nenhuma variável da host vaza), home na própria pasta. **No Linux com o bubblewrap** (`sudo apt install bubblewrap`): caixa fechada — o servidor só enxerga a própria pasta (em `/home/server`) e o Java só-leitura, com PID/IPC/UTS próprios.
- **Rede**: aparecer na página Host do site (sem o seu IP), nome, preços (por núcleo e por GB de RAM), moedas (COIN, BIPO, USD, BTC, Pix), teste grátis, vincular conta por código, trocar a central (e voltar para a padrão), IP fixo opcional (endereço/DNS e faixa de portas).
- **Cloudflare**: túnel rápido (trycloudflare.com) ou permanente (token + seu domínio) para abrir o painel pela internet.
- **Usuários**: o dono vê tudo; os outros só os próprios servidores. Senhas com PBKDF2, sessão HttpOnly/SameSite, freio de tentativas, proteção contra outro site mandar ações.
- **MineSync**: em servidores Paper/Purpur/Folia/Spigot/Canvas ele é instalado sozinho (opcional) — o servidor entra na rede sem IP fixo, com login automático, voz e portais.

## Compilar
`sh compilar.sh` (Linux/macOS) ou `COMPILAR.bat` (Windows) → `MinesyncHost.jar` (o gson vai dentro).
