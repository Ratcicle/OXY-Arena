const AREA = {
  minX: 100,
  maxX: 400,
  minZ: 200,
  maxZ: 500,
};

const MAX_ZOMBIES = 1000;
const SPAWN_PER_WAVE = 10;
const SPAWN_INTERVAL = 20;
const EVENT_DURATION = 20 * 60 * 5;
const bossbarId = "kubejs:apocalipse";
var S = "\u00A7";

let eventoAtivo = false;
let tempoRestante = 0;
let mortosPorZumbi = new Set();

function dentroDaArea(x, z) {
  return x >= AREA.minX && x <= AREA.maxX && z >= AREA.minZ && z <= AREA.maxZ;
}

function randomCoord(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

ServerEvents.commandRegistry((event) => {
  const { commands: Commands } = event;

  event.register(
    Commands.literal("evento").then(
      Commands.literal("apocalipse")
        .then(
          Commands.literal("start").executes((ctx) => {
            if (eventoAtivo) {
              ctx.source.sendSystemMessage(`${S}cEvento ja esta ativo.`);
              return 0;
            }

            eventoAtivo = true;
            tempoRestante = EVENT_DURATION;
            mortosPorZumbi.clear();

            let server = ctx.source.server;

            server.runCommandSilent(
              `bossbar add ${bossbarId} "${S}cApocalipse Zumbi"`,
            );
            server.runCommandSilent(
              `bossbar set ${bossbarId} max ${EVENT_DURATION}`,
            );
            server.runCommandSilent(
              `bossbar set ${bossbarId} value ${EVENT_DURATION}`,
            );
            server.runCommandSilent(`bossbar set ${bossbarId} color red`);
            server.runCommandSilent(`bossbar set ${bossbarId} players @a`);
            server.runCommandSilent(`bossbar set ${bossbarId} visible true`);

            server.tell(`${S}c${S}lAPOCALIPSE ZUMBI INICIADO!`);
            return 1;
          }),
        )
        .then(
          Commands.literal("stop").executes((ctx) => {
            finalizarEvento(ctx.source.server);
            return 1;
          }),
        ),
    ),
  );
});

ServerEvents.tick((event) => {
  if (!eventoAtivo) return;
  if (event.server.tickCount % SPAWN_INTERVAL !== 0) return;

  tempoRestante -= SPAWN_INTERVAL;
  if (tempoRestante <= 0) {
    finalizarEvento(event.server);
    return;
  }

  let server = event.server;
  let level = server.overworld();

  server.runCommandSilent(`bossbar set ${bossbarId} value ${tempoRestante}`);

  let zombies = level
    .getEntities()
    .filter((e) => e.type == "minecraft:zombie" && dentroDaArea(e.x, e.z));

  if (zombies.length >= MAX_ZOMBIES) return;

  for (let i = 0; i < SPAWN_PER_WAVE; i++) {
    let x = randomCoord(AREA.minX, AREA.maxX);
    let z = randomCoord(AREA.minZ, AREA.maxZ);
    let y = level.getHeight("motion_blocking", x, z);

    let zombie = level.createEntity("minecraft:zombie");
    zombie.setPosition(x + 0.5, y, z + 0.5);
    zombie.setItemSlot("head", Item.of("minecraft:diamond_helmet"));
    zombie.spawn();
  }
});

EntityEvents.death((event) => {
  if (!eventoAtivo) return;

  let entity = event.entity;
  if (!entity.isPlayer()) return;

  let killer = event.source.actual;
  if (killer && killer.type === "minecraft:zombie") {
    mortosPorZumbi.add(String(entity.uuid));
    entity.tell(
      `${S}cVoce foi infectado! Sem macas douradas para voce neste evento.`,
    );
  }
});

function finalizarEvento(server) {
  if (!eventoAtivo) return;

  eventoAtivo = false;

  server
    .overworld()
    .getEntities()
    .filter((e) => e.type == "minecraft:zombie" && dentroDaArea(e.x, e.z))
    .forEach((e) => e.discard());

  server.tell(`${S}a${S}lEvento finalizado!`);
  server.runCommandSilent(`bossbar remove ${bossbarId}`);

  server.players.forEach((player) => {
    if (dentroDaArea(player.x, player.z)) {
      if (!mortosPorZumbi.has(String(player.uuid))) {
        player.give(Item.of("minecraft:golden_apple", 5));
        player.tell(`${S}6Voce sobreviveu ao apocalipse! +5 Macas Douradas`);
      } else {
        player.tell(`${S}cVoce morreu para zumbis e nao recebeu recompensa.`);
      }
    }
  });
}