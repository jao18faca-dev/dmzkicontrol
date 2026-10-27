# DMZ Ki Control: Controle Perfeito de Aura

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
3. Aperte **N** (dá para trocar em *Controles → DMZ Ki Control*; o G é a tecla de transformação do DMZ) ou use o botão **Selar Aura** no Menu Utilitário do DMZ.
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
