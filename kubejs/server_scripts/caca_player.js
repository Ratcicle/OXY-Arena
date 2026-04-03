const CACA_DURACAO = 20 * 60 * 5;
const CACA_INTERVALO_POSICAO = 20 * 60;
const CACA_BOSSBAR = "kubejs:caca_player";
var S = "\u00A7";

let cacaAtiva = false;
let alvoUUID = null;
let alvoNome = "";
let tempoCacaRestante = 0;

ServerEvents.commandRegistry((event) => {
  const { commands: Commands } = event;

  event.register(
    Commands.literal("evento").then(
      Commands.literal("caca").then(
        Commands.literal("start").executes((ctx) => {
          if (cacaAtiva) {
            ctx.source.sendSystemMessage(
              `${S}cJá existe uma caçada em andamento!`,
            );
            return 0;
          }

          let players = ctx.source.server.players.filter(
            (p) => !p.creative && !p.spectator,
          );

          if (players.length < 2) {
            ctx.source.sendSystemMessage(
              `${S}cPrecisa de pelo menos 2 jogadores ativos.`,
            );
            return 0;
          }

          let sorteado = players[Math.floor(Math.random() * players.length)];
          alvoUUID = String(sorteado.uuid);
          alvoNome = sorteado.username;
          cacaAtiva = true;
          tempoCacaRestante = CACA_DURACAO;

          let server = ctx.source.server;

          server.runCommandSilent(
            `bossbar add ${CACA_BOSSBAR} "${S}4Cacada: ${alvoNome}"`,
          );
          server.runCommandSilent(
            `bossbar set ${CACA_BOSSBAR} max ${CACA_DURACAO}`,
          );
          server.runCommandSilent(
            `bossbar set ${CACA_BOSSBAR} value ${CACA_DURACAO}`,
          );
          server.runCommandSilent(`bossbar set ${CACA_BOSSBAR} color red`);
          server.runCommandSilent(`bossbar set ${CACA_BOSSBAR} players @a`);
          server.runCommandSilent(`bossbar set ${CACA_BOSSBAR} visible true`);

          server.tell(
            `${S}4${S}l[CAÇADA] ${S}fO jogador ${S}c${S}l${alvoNome} ${S}ffoi marcado!`,
          );

          sorteado.potionEffects.add(
            "minecraft:glowing",
            CACA_DURACAO,
            0,
            false,
            false,
          );

          return 1;
        }),
      ),
    ),
  );
});

ServerEvents.tick((event) => {
  let server = event.server;

  if (cacaAtiva) {
    tempoCacaRestante--;

    if (server.tickCount % 20 === 0) {
      server.runCommandSilent(
        `bossbar set ${CACA_BOSSBAR} value ${tempoCacaRestante}`,
      );
    }

    if (
      tempoCacaRestante % CACA_INTERVALO_POSICAO === 0 &&
      tempoCacaRestante > 0
    ) {
      let alvo = server.players.find((p) => String(p.uuid) === alvoUUID);
      if (alvo) {
        server.tell(
          `${S}4Posicao do alvo (${alvoNome}): ${S}fX: ${Math.floor(alvo.x)}, Z: ${Math.floor(alvo.z)}`,
        );
      }
    }

    if (tempoCacaRestante <= 0) {
      vitoriaPresa(server);
    }
  }

  server.players.forEach((p) => {
    if (p.tags.contains("vencedor_caca")) {
      if (!p.potionEffects.isActive("minecraft:speed")) {
        p.potionEffects.add("minecraft:speed", 600, 0, false, false);
      }
      if (!p.potionEffects.isActive("minecraft:jump_boost")) {
        p.potionEffects.add("minecraft:jump_boost", 600, 0, false, false);
      }
    }
  });
});

EntityEvents.death((event) => {
  if (!cacaAtiva) return;

  let vitima = event.entity;
  if (!vitima.isPlayer() || String(vitima.uuid) !== alvoUUID) return;

  cacaAtiva = false;
  let server = vitima.server;
  server.runCommandSilent(`bossbar remove ${CACA_BOSSBAR}`);

  let killer = event.source.player;

  if (killer) {
    server.tell(
      `${S}6${S}lVITORIA! ${S}f${killer.username} abateu o alvo e recebeu o Kit de Combate!`,
    );

    killer.give(
      Item.of("minecraft:splash_potion", {
        "minecraft:potion_contents": {
          potion: "minecraft:strong_regeneration",
        },
      }),
    );
    killer.give(
      Item.of("minecraft:splash_potion", {
        "minecraft:potion_contents": { potion: "minecraft:strength" },
      }),
    );
    killer.give(
      Item.of("minecraft:splash_potion", {
        "minecraft:potion_contents": { potion: "minecraft:swiftness" },
      }),
    );
  } else {
    server.tell(
      `${S}cO alvo ${alvoNome} morreu para o mapa. Ninguem ganha premio.`,
    );
  }
});

function vitoriaPresa(server) {
  if (!cacaAtiva) return;
  cacaAtiva = false;

  server.runCommandSilent(`bossbar remove ${CACA_BOSSBAR}`);

  let alvo = server.players.find((p) => String(p.uuid) === alvoUUID);

  if (alvo) {
    alvo.give(Item.of("minecraft:netherite_ingot", 2));
    alvo.addTag("vencedor_caca");
    alvo.potionEffects.add("minecraft:speed", 2147483647, 0, false, false);
    alvo.potionEffects.add("minecraft:jump_boost", 2147483647, 0, false, false);

    server.runCommandSilent(
      `execute at ${alvo.username} run playsound minecraft:entity.wither.spawn ambient @a ~ ~ ~ 1 1`,
    );
    server.tell(
      `${S}a${S}lSOBREVIVENTE! ${S}f${alvoNome} resistiu e tornou-se um Super Humano!`,
    );
    alvo.tell(`${S}6Voce recebeu 2x Barras de Netherita e Buffs Permanentes!`);
  } else {
    server.tell(`${S}cO alvo da cacada deslogou e o premio foi perdido.`);
  }
}
