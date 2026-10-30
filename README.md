# DragonMineZ Evolution

Add-on de expansao para o DragonMineZ (antigo "DMZ Ki Control"). O id interno continua `dmzkicontrol` para manter configs, itens e mundos.

## Selamento de Aura

Add-on para **DragonMineZ 2.2.x** (Forge 1.20.1) inspirado no Goku da saga Goku Black.
Ele **prende a aura dentro do corpo** para não desperdiçar ki e consegue manter o
**Super Saiyajin Blue** gastando bem menos.

É um add-on de verdade: o DragonMineZ original continua na pasta `mods` e este jar vai junto.
O add-on segue as regras da [wiki de Add-on Development](https://wiki.dragonminez.com/pt/wiki/Add-on_Development):
- usa só o pacote estável `com.dragonminez.common`, os eventos `DMZEvent` e o slot do Menu Utilitário;
- não usa mixins nem classes internas do DMZ;
- tem canal de rede próprio, e o servidor valida tudo o que o cliente envia.

## Como se joga

1. Aprenda a skill **Controle de Ki** (`kicontrol`) com um mestre (Roshi, Goku, Kaioh, Vegeta…).
2. Transforme-se em qualquer forma.
3. Aperte **N** (dá para trocar em *Controles → DragonMineZ Evolution*; o G é a tecla de transformação do DMZ) ou use o botão **Selar Aura** no Menu Utilitário do DMZ.
   - Com a **cutscene** ligada, a câmera contorna o personagem enquanto ele leva a mão ao peito e puxa a aura para dentro. Aperte N de novo para pular.
   - Com a cutscene desligada, ele só leva a mão ao peito e sela. Para alternar, clique com o **botão direito** em *Selar Aura* no Menu Utilitário ou mude `cutscene.mode` em `config/dmzkicontrol-client.toml` (`ALWAYS`, `ONCE_PER_SESSION` ou `OFF`).
4. Com a aura selada:
   | Efeito | Como funciona |
   |---|---|
   | **Upkeep de ki reduzido** | Devolve parte do custo por segundo da forma, até **-80% no Blue** com domínio total. A estamina da forma também cai (metade da redução, configurável). |
   | **Aura escondida** | A aura permanente some enquanto o selo está ativo e volta quando você libera. |
   | **Um pouco mais forte** | O bônus entra **no multiplicador da forma** do DMZ, como uma porcentagem dele: no SSJ3 (×3,3), +8% vira ×3,56. Isso sobe STR, SKP e PWR (e metade em DEF), então aparece no power level e nos status. Também dá chance de crítico extra. |
   | **Ki não vaza** | A regeneração de ki fica um pouco maior (+15% com domínio total). |
   | **Domínio por forma** | Cada forma tem o próprio domínio. A curva é orgânica: sobe rápido no começo e fica cada vez mais lenta perto da perfeição. O treino rende **1,75×** lutando, **1,5×** forçando o limite (tensão entre 40% e 90%) e menos perdendo o controle. Ter domínio alto da forma no DMZ também acelera. |
5. **Tensão (cansaço):** um medidor no estilo dos medidores laterais do DMZ, do lado direito da tela, enche enquanto a aura está selada. Quanto maior o domínio, mais devagar ele enche, e formas fortes como o Blue cansam o dobro. Golpes recebidos e técnicas de ki também enchem o medidor.
   - **50%:** cansaço, a estamina vai caindo.
   - **75%:** o corpo pesa (Lentidão I).
   - **90%:** dor, perde um pouco de vida a cada 2 s (a tensão sozinha nunca mata).
   - **100%:** a aura explode para fora, o selo quebra e você perde 15% do ki atual.
   - Com a aura livre, a tensão desce. Só dá para selar de novo abaixo de 60%.
   - A partir de 90% de domínio a tensão some: é o controle perfeito, e o medidor fica roxo com "MAX".
6. **Liberação de Ki:** soltar o selo de propósito depois de 20 s ou mais dá um pico de poder por 6 s. A recarga é de 30 s.
7. Voltar à forma base, morrer ou deslogar encerra o selo. Trocar de forma mantém o selo, mas reinicia a contagem de tensão.

O medidor mostra a tensão, as marcas de 50% e 90%, o domínio e a Liberação. Ele usa as texturas e a fonte do próprio DMZ.
Enquanto o selo está ativo, as bordas da tela ganham um brilho suave, que fica vermelho com a tensão alta.
Lado, posição, escala e brilho ficam em `config/dmzkicontrol-client.toml`.

## Raça Cerealiana (Granolah)
O add-on instala a raça **Cerealiano** no sistema de raças do DMZ (`config/dragonminez/races/cerealian/`). Ele só cria os arquivos que estiverem faltando, então suas edições são mantidas. Para voltar aos padrões, apague a pasta.
- **Aparência:** modelo humano com orelhas pontudas e cabelo editável, um pouco mais baixo, e heterocromia (um olho bronze). As cores podem ser trocadas na criação do personagem.
- **Atributos:** voltados para precisão de ki (escala maior de PWR e SKP) e com um pouco menos de vida.
- **Formas** (skill *Formas Super*):
  - **Olho Desperto**, nível 1;
  - **Olhos Gêmeos**, nível 2, que exige 50% de domínio no Olho Desperto.
  - **Poder do Desejo**, nível 3: o Granolah depois do desejo. Cabelo longo (o estado SSJ3 do seu cabelo), corpo um pouco maior e aura verde. **Não se compra com TP: só pelo desejo ao Toronbo** (veja abaixo).
  - **Além do Limite**, nível 4: queimar a própria vida para ir além, como o Granolah contra o Gas. Gasta vida enquanto estiver ativa (como o Kaioken). Exige 50% de domínio no Poder do Desejo e libera na missão 26 da saga do Granolah.
  - Quem já tinha a raça instalada ganha as duas formas e os preços dos níveis novos sem perder o que editou. Se você apagar uma delas, ela não volta.
- **Esferas do Dragão de Cereal e o Toronbo:** como no mangá, são só duas esferas (1 e 2 estrelas), bem pequenas, espalhadas no Planeta Cereal. O Monaito te dá o Radar de Cereal na missão 11 da saga do Granolah. Juntando as duas aparece o Toronbo, o dragão com jeito de axolote, que realiza um desejo:
  - **Ser o guerreiro mais forte do universo:** para um Cerealiano, libera o Poder do Desejo e cobra anos de vida: a vida máxima cai 10% para sempre (`cerealian.toronboLifeCost`). Para outras raças, o Toronbo só desperta o potencial (TP).
  - Sementes dos Deuses ou TP.
- **Cabelo verde-água** por padrão.
- **Olho Cereal** (segure **B**): gasta um pouco de ki por segundo e dá:
  - zoom de atirador;
  - contorno em todos que têm ki ao seu redor, mesmo atrás das paredes;
  - **círculos vermelhos nos pontos fracos** de quem está na mira. Cada golpe corpo a corpo consome um ponto (+25% de dano, ignora 20% da defesa). Um tiro que acerta em cima de um ponto é um **tiro preciso** (+37,5%).
  - Tiros de longe ganham até +30% de dano, conforme a distância.
  - As formas multiplicam os bônus do olho: ×1,5 no Olho Desperto e ×2 nos Olhos Gêmeos.
- **Skill Mira** (menu de skills do DMZ, só para Cerealianos): nível 1 de graça, níveis 2 a 4 com TP. Cada nível revela mais um ponto fraco (coração → cabeça → costelas → coluna), aumenta o alcance e reduz o ki gasto. O nível 2 libera a **Flecha de Ki**.
- **Flecha de Ki:** técnica do DMZ exclusiva dos Cerealianos. É uma flecha de luz que atravessa até 3 alvos.
- **Tiro de ki:** a rajada básica dos Cerealianos vira um tiro fino, rápido e sem espalhar, em vez de uma bola de ki.
- **Passivas:**
  - **Leitura de Trajetória:** chance de desviar de projéteis, 9% a 18% conforme a Mira.
  - **Olhar Preciso:** +1,5% de crítico por nível da Mira.
- **Mira Cereal** (item): a lente de atirador. Vai na cabeça ou no slot Head Tech, como o scouter. Para Cerealianos dá +1 ponto fraco, +16 blocos de alcance e metade do ki gasto. Receita:
  ```
   [ametista]
  [ouro][luneta][ouro]
   [redstone]
  ```
- Os valores ficam no bloco `cerealian` de `config/dmzkicontrol.json`.

## Flechas e laser no choque de ki

- **Parry:** bloquear no tempo certo (a janela de parry do DMZ) rebate a flecha de volta em quem atirou,
  e devolve o laser.
- **Choque:** flecha contra rajada de ki, a mais forte passa; flecha contra uma onda de ki (Kamehameha,
  Galick Gun...) trava na ponta da onda por um instante e quem tiver mais poder vence (a flecha carregada
  pesa mais). Vale para o ki do DMZ, Sparking, Noea e Overhaul.
- **Laser:** derruba rajadas mais fracas no caminho; onda de ki ou rajada mais forte segura o laser.
- Desliga com `cerealian.kiClashAndParry: false`.

## Luta às cegas (visão de ki)

Sem enxergar, você sente o ki. O mundo apaga (só um contorno de poucos blocos em volta, para não cair
de penhasco) e quem está por perto aparece como uma silhueta brilhando na cor do próprio ki, até
através das paredes. Quanto mais forte em relação a você, mais brilha; ki maligno (mobs hostis) aparece
vermelho. Rajadas de ki aparecem como riscos de luz e uma onda de ki sai de você de tempos em tempos.
Quem está com a aura selada (Aura Control) quase some. É só para jogadores.

- **Venda de Treino** (1 lã preta + 2 linhas): na cabeça, liga a visão de ki. Em combate o TP que o DMZ
  dá rende +20% (+5% por nível do Ki Sense, até +50%).
- **Taiyoken:** quem tem Ki Sense não fica cego de vez. O clarão passa rápido e você luta de olhos
  fechados pelo ki até a cegueira acabar, como o Goku contra o Tenshinhan.
- Alcance: 6 blocos + 6 por nível do Ki Sense (até 48). Tudo ajustável em `kiVision` no
  `config/dmzkicontrol.json`.

## Treino do Senhor Kaioh: pegar o Bubbles

O Bubbles aparece perto do Senhor Kaioh do DMZ (ou pelo ovo gerador). Clique nele com o botão direito
para começar: você tem 60 segundos para encostar nele (ou bater, não machuca), sem voar, sem
teletransporte e sem dash, só correndo e pulando, como o Goku. Ele foge, muda de direção, pula para o
lado quando você chega perto e para para dançar quando você fica para trás.

- 5 níveis: a cada vitória ele fica mais rápido (a velocidade acompanha a sua) e pula mais.
- Recompensa: TP do DMZ, o custo de 1 a 3 pontos de atributo no seu nível; a primeira vitória em cada
  nível vale o dobro. Depois de pegar, 10 minutos de descanso.
- Tudo ajustável em `bubbles` no `config/dmzkicontrol.json`.

## Sagas do Moro e do Granolah

Duas sagas do Dragon Ball Super vindas do **DMZ Ultra**, de DarkForest (usadas com permissão), adaptadas para o DMZ 2.2:

- **O Prisioneiro da Patrulha Galáctica** (27 missões): Jaco, Merus, Saganbo, Seven-Three e as três fases do Moro.
- **Granolah, o Sobrevivente** (28 missões): os Heeter (Oil, Macki, Elec e as três fases do Gas), o Granolah normal e com o desejo, o Monaito e o Black Freeza no fim.

Como entram no jogo:
- Ao abrir o mundo, o add-on escreve as sagas em `world/dragonminez/sagas` e `world/dragonminez/quests`. Arquivo que você editou não é sobrescrito.
- Com o DMZ Sparking, a saga do Moro abre depois do filme do Broly. Sem o Sparking, abre depois da saga do Buu. A do Granolah vem depois da do Moro.
- O nível e a força dos inimigos continuam a curva do Sparking (sem saltos).
- Jaco fica na Casa do Kame; Merus e Granolah, na casa do Goku. Monaito e outro Granolah ficam no **Planeta Cereal**, que a nave espacial libera depois da primeira missão do Granolah. Os do Planeta Cereal aparecem depois de reabrir o mundo uma vez.
- Os inimigos usam o sistema de inimigos de saga do próprio DMZ (voo, aura, ki, combos).
- Para desligar: `sagas.installMoroAndGranolah = false` no `config/dmzkicontrol.json`.

## Comandos
| Comando | O que faz |
|---|---|
| `/auracontrol` | Mostra a forma atual (id `grupo:forma`), o selo, o domínio, o ki poupado, o poder, o crítico e a tolerância |
| `/auracontrol toggle` | Igual à tecla N |
| `/auracontrol list` | Domínio em todas as formas treinadas |
| `/auracontrol reload` | Recarrega `config/dmzkicontrol.json` (op) |
| `/auracontrol mastery set <alvos> <0-100>` | Define o domínio na forma atual (op) |
| `/auracontrol mastery setform <alvos> <grupo:forma> <0-100>` | Define o domínio numa forma específica (op) |
| `/auracontrol mastery reset <alvos>` | Zera o domínio (op) |

## Balanceamento: `config/dmzkicontrol.json`
O arquivo é criado na primeira execução. Para cada forma, o valor final é **valor do tier × domínio (0 a 1)**.
- `forms`: ids exatos (veja com `/auracontrol`, por exemplo `"supersaiyan:supersaiyan3"`).
- `rules`: busca por trecho do id. **A primeira regra que casar vence.**
- `defaultTier`: usado quando nada casa.
- Cada tier tem `maxKiReduction`, `maxPowerBonus`, `maxCritBonus` e `trainTimeMultiplier` (quanto maior, mais difícil de dominar).

Regras padrão (ki poupado e tempo de domínio, em relação aos 15 min base):
| Trecho do id | Ki poupado | Tempo de domínio | Por quê |
|---|---|---|---|
| `blue` | 80% | 2× | o princípio da saga Black |
| `god` | 60% | 1,5× | |
| `ultrainstinct` / `ultraego` | 30% | 2,5× | |
| `kaioken` | 15% | | |
| `supersaiyan4` | 40% | | |
| `supersaiyan3` | 25% | | é notoriamente gastão |
| `supersaiyan2` | 40% | | |
| `mastered` | 60% | rápido | o SSJ Full Power já é eficiente |
| `grade` | 20% | | as graduações 2 e 3 são ineficientes |

Também dá para ajustar: `requiredSkill` (deixe `""` para liberar o selo sem a skill),
`stackFormPenalty` (o selo rende metade com Kaioken ou UI por cima), `hideAura`,
`auraToggleReleasesSeal`, `chargingReleasesSeal`, `energyRegenBonus` e os blocos `strain` e `burst`.

## Super Saiyajin God e Blue (opcional)
O DragonMineZ 2.2 não traz o SSJ Blue de fábrica: só o SSJ God do ritual. Para ter o Blue, siga estes passos:
1. Copie `extras/saiyan_godforms.json` para `config/dragonminez/races/saiyan/forms/` no servidor ou no mundo.
2. Rode `/dmzreload config`.
3. Libere a skill de formas divinas: `/dmzskill set godforms 2 <jogador>`. O nível 1 dá o SSJ God; o nível 2 dá o SSJ Blue, que pede 25% de domínio no God.

O Blue foi feito para ser forte (×5,5) e **muito caro** (drena 0,95). É aí que o selo faz diferença.
Todos os valores ficam no próprio JSON, no formato de [Custom Forms](https://wiki.dragonminez.com/wiki/Custom_Forms).

## Como gerar o .jar

**Opção A: GitHub (sem instalar nada)**
1. Crie um repositório no GitHub e envie esta pasta.
2. Abra a aba **Actions**, escolha **Build add-on jar** e clique em **Run workflow**.
3. O workflow baixa o DragonMineZ do Modrinth sozinho, compila e publica o `dmzkicontrol-2.0.0.jar` em **Artifacts**.

**Opção B: no PC (Java 17)**
1. Coloque o jar do DMZ em `libs/` com o nome `dragonminez-2.2.1.jar`. Use o jar normal, **não** o `-slim`. Se a sua versão for outra, ajuste `dmz_version` em `gradle.properties`.
2. Rode `gradlew build` (Windows: `gradlew.bat build`). O jar sai em `build/libs/`.
3. Para testar: `gradlew runClient`. O usuário de dev "Dev" está na whitelist das versões alfa do DMZ.

O add-on precisa estar no cliente e no servidor, junto com a mesma versão do DMZ.
**Não** use junto com Legendary Tooltips, Epic Fight ou Better Combat, porque o DMZ se recusa a iniciar com eles.

## Como funciona por dentro
O DMZ cobra o upkeep da forma uma vez por segundo, usando `StatsData.getEffectiveEnergyDrain()`.
Esse método é público, então o add-on sabe exatamente quanto será cobrado. A cada tick ele compara o ki
com o tick anterior, descontando a regeneração do mesmo tick (`EnergyRegenEvent`). Quando o upkeep
aparece, ele devolve `redução × upkeep`.
Gastos de ataque, dash, esquiva e troca de forma são ignorados (`KiAttackFireEvent`, `PlayerDashEvent`, …).
A sincronização com o cliente usa o próprio delta-sync do DMZ.

Licença: GPL-3.0-or-later, a mesma família do DragonMineZ.


## Creditos

- Sagas do Moro e do Granolah, modelos e texturas dos personagens delas e o Planeta Cereal: **DMZ Ultra**, de DarkForest, usados com permissão.

- **Dragon Block Noea**, de ButterJaffa e contribuidores: o DragonMineZ Evolution reconhece as formas do Noea
  (Rose, Beast, Golden/Black, Orange Piccolo, Ki Divino) no Aura Control quando os dois estao instalados.
  Feito com permissao do autor. Nenhum codigo ou asset do Noea e incluido neste mod.
- **DMZ Sparking**, de Yami: o DragonMineZ Evolution se conecta as sagas do Super (os niveis do Aura Control
  acompanham a licao do Whis e o arco do Black), a meditacao (meditar selado treina o dominio e alivia a tensao)
  e ao Blue Aperfeicoado (ja transforma com a aura selada). Feito com permissao do autor; nenhum codigo ou asset
  do Sparking e incluido neste mod.
- Com os dois instalados, o Instinto Superior usa so o desvio do Sparking (o desvio automatico do Noea e
  desligado por um mixin opcional; volta com `integrations.noeaUltraInstinctDodge: true` em
  `config/dmzkicontrol.json`). A raiva crescente do Ultra Ego do Noea continua igual.
- **DragonMineZ**, base de tudo.
