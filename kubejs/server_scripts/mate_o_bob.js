const BOSS_AREA = {
  minX: 100,
  maxX: 400,
  minZ: 200,
  maxZ: 500,
};
var S = "\u00A7";

function randomBossCoord(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

let bobAtivo = false;

ServerEvents.commandRegistry((event) => {
  const { commands: Commands } = event;

  event.register(
    Commands.literal("evento").then(
      Commands.literal("miniboss").then(
        Commands.literal("start").executes((ctx) => {
          if (bobAtivo) {
            ctx.source.sendSystemMessage(
              `${S}cO Bob já está aterrorizando o mapa!`,
            );
            return 0;
          }

          let server = ctx.source.server;
          let level = server.overworld();

          let bossX, bossY, bossZ, blocoChao;
          let tentativas = 0;

          do {
            bossX = randomBossCoord(BOSS_AREA.minX, BOSS_AREA.maxX);
            bossZ = randomBossCoord(BOSS_AREA.minZ, BOSS_AREA.maxZ);
            bossY = level.getHeight("motion_blocking", bossX, bossZ);
            blocoChao = level.getBlock(bossX, bossY - 1, bossZ).id;
            tentativas++;
          } while (
            (blocoChao === "minecraft:water" ||
              blocoChao === "minecraft:lava") &&
            tentativas < 50
          );

          let bob = level.createEntity("minecraft:zombie");
          bob.setPosition(bossX + 0.5, bossY, bossZ + 0.5);

          bob.setCustomName(Text.of(`${S}c${S}lBob, O Destruidor`));
          bob.setCustomNameVisible(true);
          bob.addTag("boss_bob");

          let capacete = Item.of("minecraft:diamond_helmet").enchant(
            "minecraft:protection",
            4,
          );
          let peitoral = Item.of("minecraft:diamond_chestplate").enchant(
            "minecraft:protection",
            4,
          );
          let calca = Item.of("minecraft:diamond_leggings").enchant(
            "minecraft:protection",
            4,
          );
          let bota = Item.of("minecraft:diamond_boots").enchant(
            "minecraft:protection",
            4,
          );
          let espadaBoss = Item.of("minecraft:diamond_sword")
            .enchant("minecraft:sharpness", 4)
            .enchant("minecraft:knockback", 2);

          bob.setItemSlot("head", capacete);
          bob.setItemSlot("chest", peitoral);
          bob.setItemSlot("legs", calca);
          bob.setItemSlot("feet", bota);
          bob.setItemSlot("mainhand", espadaBoss);

          bob.setAttributeBaseValue("minecraft:generic.max_health", 200);
          bob.setHealth(200);
          bob.potionEffects.add("minecraft:speed", 999999, 0, false, false);

          bob.spawn();
          bobAtivo = true;

          server.tell(
            `${S}4${S}lUM MINIBOSS SURGIU! ${S}cBob ${S}fapareceu nas coordenadas ${S}eX: ${Math.floor(bossX)}, Y: ${Math.floor(bossY)}, Z: ${Math.floor(bossZ)}${S}f!`,
          );
          server.tell(
            `${S}6Corram para encontrá-lo! Quem der o último golpe ganhará uma Espada de Diamante e um Livro de Afiação 4 e Repulsão 2!`,
          );

          return 1;
        }),
      ),
    ),
  );
});

EntityEvents.death((event) => {
  let entity = event.entity;

  if (entity.tags.contains("boss_bob")) {
    bobAtivo = false;

    let killer = event.source.actual;
    let server = entity.server;

    if (killer && killer.isPlayer()) {
      let livro = Item.of("minecraft:enchanted_book")
        .enchant("minecraft:sharpness", 4)
        .enchant("minecraft:knockback", 2);

      let espadaPrata = Item.of("minecraft:diamond_sword");

      killer.give(livro);
      killer.give(espadaPrata);

      server.tell(
        `${S}a${S}lO jogador ${killer.username} derrotou o Bob e levou a Espada e o Livro Encantado!`,
      );
    } else {
      server.tell(
        `${S}cO Bob morreu para o ambiente e ninguem levou o premio...`,
      );
    }
  }
});
