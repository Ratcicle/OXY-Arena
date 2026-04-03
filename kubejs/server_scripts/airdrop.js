// kubejs/server_scripts/airdrop_evento.js

function iniciarAirdrop(ctx) {
  try {
    if (!ctx.source.isPlayer()) return 0;

    let servidor = ctx.source.server;
    let mundo = ctx.source.player.level;

    // Mesma area de nascimento do Jorginho
    let minX = 100;
    let maxX = 400;
    let minZ = 200;
    let maxZ = 500;

    let x = Math.floor(Math.random() * (maxX - minX + 1)) + minX;
    let z = Math.floor(Math.random() * (maxZ - minZ + 1)) + minZ;

    // Acha o chao para saber onde a queda termina
    let yGround = 300;
    while (yGround > -60) {
      let blockId = mundo.getBlock(x, yGround, z).id;
      if (
        blockId !== "minecraft:air" &&
        blockId !== "minecraft:cave_air" &&
        !blockId.includes("leaves") &&
        !blockId.includes("log")
      ) {
        break;
      }
      yGround--;
    }
    let finalY = yGround + 1;

    // 1. Tabela de loot customizada com pesos
    let lootPool = [
      { id: "minecraft:coal", min: 10, max: 30, weight: 60 },
      { id: "minecraft:iron_ingot", min: 4, max: 15, weight: 50 },
      { id: "minecraft:gold_ingot", min: 3, max: 10, weight: 40 },
      { id: "minecraft:arrow", min: 10, max: 32, weight: 38 },
      { id: "minecraft:ender_pearl", min: 3, max: 16, weight: 35 },
      { id: "minecraft:emerald", min: 1, max: 3, weight: 30 },
      { id: "minecraft:diamond", min: 1, max: 5, weight: 25 },
      { id: "minecraft:firework_rocket", min: 5, max: 20, weight: 25 },
      { id: "minecraft:iron_chestplate", min: 1, max: 1, weight: 18 },
      { id: "minecraft:gold_helmet", min: 1, max: 1, weight: 12 },
      { id: "minecraft:ancient_debris", min: 1, max: 3, weight: 10 },
      { id: "minecraft:diamond_boots", min: 1, max: 1, weight: 8 },
      { id: "minecraft:elytra", min: 1, max: 1, weight: 5 },
      { id: "minecraft:mace", min: 1, max: 1, weight: 3 },
    ];

    // 2. Sorteia exatamente 3 itens diferentes
    let selectedItems = [];
    let currentPool = lootPool.slice();

    for (let i = 0; i < 3; i++) {
      let totalWeight = currentPool.reduce((sum, item) => sum + item.weight, 0);
      let randomNum = Math.random() * totalWeight;
      let cumulative = 0;

      for (let j = 0; j < currentPool.length; j++) {
        cumulative += currentPool[j].weight;
        if (randomNum <= cumulative) {
          let picked = currentPool[j];
          let count =
            Math.floor(Math.random() * (picked.max - picked.min + 1)) +
            picked.min;

          selectedItems.push({ id: picked.id, count: count });
          currentPool.splice(j, 1);
          break;
        }
      }
    }

    // 3. Sorteia 3 slots aleatorios no bau
    let slots = [];
    while (slots.length < 3) {
      let r = Math.floor(Math.random() * 27);
      if (!slots.includes(r)) slots.push(r);
    }

    let itemsNbt = selectedItems
      .map(
        (item, idx) =>
          `{Slot:${slots[idx]}b,id:"${item.id}",count:${item.count}}`,
      )
      .join(",");

    // 4. Configuracao da animacao de queda
    let tagID = `airdrop_${x}_${z}`;
    let currentY = 120;

    servidor.runCommandSilent(
      `summon block_display ${x} ${currentY} ${z} {block_state:{Name:"minecraft:chest"},Tags:["airdrop_evento","${tagID}"]}`,
    );

    servidor.tell(
      `Um Suprimento da OXY foi avistado caindo em X: ${x}, Z: ${z}!`,
    );
    servidor.tell("Olhem para o céu! O primeiro a chegar leva tudo!");
    servidor.runCommandSilent(
      "execute at @a run playsound minecraft:entity.ender_dragon.growl master @a ~ ~ ~ 0.5 1",
    );

    // 5. Loop de queda: desce 1 bloco por segundo
    let dropStep = (cy) => {
      if (cy <= finalY) {
        servidor.runCommandSilent(`kill @e[type=block_display,tag=${tagID}]`);
        servidor.runCommandSilent(
          `setblock ${x} ${finalY} ${z} minecraft:chest{Items:[${itemsNbt}]}`,
        );

        servidor.runCommandSilent(`summon lightning_bolt ${x} ${finalY} ${z}`);
        servidor.tell(
          `O Suprimento tocou o solo em X: ${x}, Y: ${finalY}, Z: ${z}!`,
        );
        return;
      }

      servidor.runCommandSilent(
        `tp @e[type=block_display,tag=${tagID}] ${x} ${cy - 1} ${z}`,
      );

      servidor.scheduleInTicks(20, () => {
        dropStep(cy - 1);
      });
    };

    servidor.scheduleInTicks(20, () => {
      dropStep(currentY);
    });

    return 1;
  } catch (err) {
    if (ctx.source.isPlayer()) {
      ctx.source.player.tell(`[Debug] Erro Airdrop: ${err}`);
    }
    console.error("Erro Airdrop: " + err);
    return 0;
  }
}

ServerEvents.commandRegistry((event) => {
  const { commands: Commands } = event;

  event.register(
    Commands.literal("evento")
      .requires((src) => src.hasPermission(2))
      .then(
        Commands.literal("airdrop").then(
          Commands.literal("start").executes((ctx) => iniciarAirdrop(ctx)),
        ),
      ),
  );
});
