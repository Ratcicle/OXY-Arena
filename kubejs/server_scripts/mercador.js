const MERCADOR_DURACAO = 20 * 60 * 5;
const MERCADOR_BOSSBAR = "kubejs:mercador_evento";
var S = "\u00A7";

let mercadorAtivo = false;
let mercadorTempoRestante = 0;

function iniciarMercador(ctx) {
  try {
    if (mercadorAtivo) {
      ctx.source.sendSystemMessage(`${S}cJa existe um mercador ativo no mundo.`);
      return 0;
    }

    if (!ctx.source.isPlayer()) return 0;

    let jogador = ctx.source.player;
    let mundo = jogador.level;
    let servidor = ctx.source.server;

    let minX = 100;
    let maxX = 400;
    let minZ = 200;
    let maxZ = 500;

    let x = Math.floor(Math.random() * (maxX - minX + 1)) + minX;
    let z = Math.floor(Math.random() * (maxZ - minZ + 1)) + minZ;

    let y = 319;
    while (y > -60) {
      let blockId = mundo.getBlock(x, y, z).id;
      if (
        blockId !== "minecraft:air" &&
        blockId !== "minecraft:cave_air" &&
        !blockId.includes("leaves") &&
        !blockId.includes("log")
      ) {
        break;
      }
      y--;
    }

    let spawnY = y + 1;
    let nbt = `{NoAI:1b,Invulnerable:1b,CustomNameVisible:1b,Tags:["mercador_evento"],CustomName:'{"text":"Jorginho","color":"gold","bold":true}',VillagerData:{profession:"minecraft:armorer",level:5,type:"minecraft:plains"},Offers:{Recipes:[{buy:{id:"minecraft:coal",count:5},sell:{id:"minecraft:iron_ingot",count:1},maxUses:99999,rewardExp:0b},{buy:{id:"minecraft:iron_ingot",count:5},sell:{id:"minecraft:gold_ingot",count:2},maxUses:99999,rewardExp:0b},{buy:{id:"minecraft:gold_ingot",count:5},sell:{id:"minecraft:diamond",count:1},maxUses:99999,rewardExp:0b},{buy:{id:"minecraft:emerald",count:1},sell:{id:"minecraft:diamond",count:1},maxUses:99999,rewardExp:0b},{buy:{id:"minecraft:diamond",count:5},buyB:{id:"minecraft:gold_ingot",count:5},sell:{id:"minecraft:netherite_ingot",count:1},maxUses:99999,rewardExp:0b}]}}`;

    servidor.runCommandSilent(
      `summon minecraft:villager ${x} ${spawnY} ${z} ${nbt}`,
    );

    mercadorAtivo = true;
    mercadorTempoRestante = MERCADOR_DURACAO;

    servidor.runCommandSilent(
      `bossbar add ${MERCADOR_BOSSBAR} "${S}6Mercador: Jorginho"`,
    );
    servidor.runCommandSilent(
      `bossbar set ${MERCADOR_BOSSBAR} max ${MERCADOR_DURACAO}`,
    );
    servidor.runCommandSilent(
      `bossbar set ${MERCADOR_BOSSBAR} value ${MERCADOR_DURACAO}`,
    );
    servidor.runCommandSilent(`bossbar set ${MERCADOR_BOSSBAR} color yellow`);
    servidor.runCommandSilent(`bossbar set ${MERCADOR_BOSSBAR} players @a`);
    servidor.runCommandSilent(`bossbar set ${MERCADOR_BOSSBAR} visible true`);

    servidor.tell(
      `${S}6${S}l[Evento] ${S}fO mercador ${S}eJorginho ${S}facaba de chegar em ${S}aX: ${x}, Y: ${spawnY}, Z: ${z}${S}f!`,
    );
    servidor.tell(
      `${S}7Ele vai embora em 5 minutos! Facam suas trocas rapidamente!`,
    );

    servidor.runCommandSilent(
      "execute at @a run playsound minecraft:ui.toast.challenge_complete master @a ~ ~ ~ 1 1",
    );

    return 1;
  } catch (err) {
    if (ctx.source.isPlayer()) {
      ctx.source.player.tell(`${S}c[Debug do KubeJS] Erro no script: ${err}`);
    }
    console.error("Erro ao rodar Jorginho: " + err);
    return 0;
  }
}

function finalizarMercador(server) {
  if (!mercadorAtivo) return;

  mercadorAtivo = false;
  mercadorTempoRestante = 0;

  server.runCommandSilent(
    "tp @e[type=minecraft:villager,tag=mercador_evento] ~ -500 ~",
  );
  server.runCommandSilent(
    "kill @e[type=minecraft:villager,tag=mercador_evento]",
  );
  server.runCommandSilent(`bossbar remove ${MERCADOR_BOSSBAR}`);
  server.tell(
    `${S}6${S}l[Evento] ${S}fO tempo acabou! O mercador Jorginho foi embora...`,
  );
}

ServerEvents.commandRegistry((event) => {
  const { commands: Commands } = event;

  event.register(
    Commands.literal("evento")
      .requires((src) => src.hasPermission(2))
      .then(
        Commands.literal("mercador").then(
          Commands.literal("start").executes((ctx) => iniciarMercador(ctx)),
        ),
      ),
  );
});

ServerEvents.tick((event) => {
  if (!mercadorAtivo) return;
  if (event.server.tickCount % 20 !== 0) return;

  mercadorTempoRestante -= 20;

  if (mercadorTempoRestante <= 0) {
    finalizarMercador(event.server);
    return;
  }

  event.server.runCommandSilent(
    `bossbar set ${MERCADOR_BOSSBAR} value ${mercadorTempoRestante}`,
  );
});