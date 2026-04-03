const INUNDACAO_AREA = {
  minX: 100,
  maxX: 400,
  minZ: 200,
  maxZ: 500,
};

const INUNDACAO_MIN_Y = -64;
const INUNDACAO_MAX_Y = 70;
const INUNDACAO_DURACAO = 20 * 60 * 5;
const INUNDACAO_BOSSBAR = "kubejs:inundacao";
const RESTAURACAO_MAX_Y = 62;
var S = "\u00A7";

let inundacaoAtiva = false;
let inundacaoDrenando = false;
let drenagemRapida = false;
let nivelAguaAtual = INUNDACAO_MIN_Y;
let tempoInundacaoRestante = 0;
let afogados = new Set();
let restaurandoRios = false;
let nivelRestauracaoAtual = 30;

function dentroDaAreaInundacao(x, z) {
  return (
    x >= INUNDACAO_AREA.minX &&
    x <= INUNDACAO_AREA.maxX &&
    z >= INUNDACAO_AREA.minZ &&
    z <= INUNDACAO_AREA.maxZ
  );
}

ServerEvents.commandRegistry((event) => {
  const { commands: Commands } = event;

  event.register(
    Commands.literal("evento").then(
      Commands.literal("inundacao")
        .then(
          Commands.literal("start").executes((ctx) => {
            if (inundacaoAtiva || inundacaoDrenando) {
              ctx.source.sendSystemMessage(
                `${S}cO dilúvio já está a acontecer (ou a secar)!`,
              );
              return 0;
            }

            inundacaoAtiva = true;
            inundacaoDrenando = false;
            drenagemRapida = false;
            nivelAguaAtual = INUNDACAO_MIN_Y;
            tempoInundacaoRestante = INUNDACAO_DURACAO;
            afogados.clear();

            let server = ctx.source.server;

            server.runCommandSilent(
              `bossbar add ${INUNDACAO_BOSSBAR} "${S}bDilúvio Iminente!"`,
            );
            server.runCommandSilent(
              `bossbar set ${INUNDACAO_BOSSBAR} max ${INUNDACAO_DURACAO}`,
            );
            server.runCommandSilent(
              `bossbar set ${INUNDACAO_BOSSBAR} value ${INUNDACAO_DURACAO}`,
            );
            server.runCommandSilent(
              `bossbar set ${INUNDACAO_BOSSBAR} color blue`,
            );
            server.runCommandSilent(
              `bossbar set ${INUNDACAO_BOSSBAR} players @a`,
            );
            server.runCommandSilent(
              `bossbar set ${INUNDACAO_BOSSBAR} visible true`,
            );

            server.tell(
              `${S}b${S}lAs nuvens escurecem... ${S}r${S}bA água das profundezas começa a subir!`,
            );
            server.tell(
              `${S}3Procurem terrenos altos! A água vai subir ate a camada 70 nos próximos 5 minutos!`,
            );

            return 1;
          }),
        )
        .then(
          Commands.literal("stop").executes((ctx) => {
            iniciarDrenagem(ctx.source.server);
            return 1;
          }),
        )
        .then(
          Commands.literal("limpar_tudo").executes((ctx) => {
            inundacaoAtiva = false;
            inundacaoDrenando = true;
            drenagemRapida = true;
            nivelAguaAtual = INUNDACAO_MAX_Y + 5;

            ctx.source.server.runCommandSilent(
              `bossbar remove ${INUNDACAO_BOSSBAR}`,
            );
            ctx.source.server.tell(
              `${S}aDrenagem de Emergência ativada! Sugando toda a água do mapa...`,
            );

            return 1;
          }),
        )
        .then(
          Commands.literal("restaurar_rios").executes((ctx) => {
            if (inundacaoAtiva || inundacaoDrenando) {
              ctx.source.sendSystemMessage(
                `${S}cEspere a inundação ou drenagem terminar primeiro!`,
              );
              return 0;
            }

            restaurandoRios = true;
            nivelRestauracaoAtual = 30;
            ctx.source.server.tell(
              `${S}9Iniciando a restauração mágica dos leitos dos rios e oceanos...`,
            );

            return 1;
          }),
        ),
    ),
  );
});

EntityEvents.death((event) => {
  if (!inundacaoAtiva) return;

  let entity = event.entity;
  if (!entity.isPlayer()) return;

  if (event.source.type === "minecraft:drown") {
    afogados.add(String(entity.uuid));
    entity.tell(
      `${S}cAs águas foram mais fortes! Ficas fora da premiação dessa inundação.`,
    );
  }
});

ServerEvents.tick((event) => {
  let server = event.server;
  let level = server.overworld();

  if (inundacaoAtiva) {
    tempoInundacaoRestante--;

    if (server.tickCount % 20 === 0) {
      server.runCommandSilent(
        `bossbar set ${INUNDACAO_BOSSBAR} value ${tempoInundacaoRestante}`,
      );
    }

    if (tempoInundacaoRestante <= 0) {
      iniciarDrenagem(server);
      return;
    }

    if (server.tickCount % 20 === 0 && nivelAguaAtual <= INUNDACAO_MAX_Y) {
      for (let x = INUNDACAO_AREA.minX; x <= INUNDACAO_AREA.maxX; x++) {
        for (let z = INUNDACAO_AREA.minZ; z <= INUNDACAO_AREA.maxZ; z++) {
          let bloco = level.getBlock(x, nivelAguaAtual, z);
          if (
            bloco.id === "minecraft:air" ||
            bloco.id === "minecraft:cave_air" ||
            bloco.id === "minecraft:short_grass" ||
            bloco.id === "minecraft:tall_grass"
          ) {
            bloco.set("minecraft:water");
          }
        }
      }

      if (nivelAguaAtual % 10 === 0 && nivelAguaAtual > 0) {
        server.tell(`${S}3A água ja atingiu a camada Y: ${nivelAguaAtual}!`);
      }
      nivelAguaAtual++;
    }
  }

  if (inundacaoDrenando) {
    let delayDrenagem = drenagemRapida ? 1 : 10;

    if (server.tickCount % delayDrenagem === 0) {
      for (let x = INUNDACAO_AREA.minX; x <= INUNDACAO_AREA.maxX; x++) {
        for (let z = INUNDACAO_AREA.minZ; z <= INUNDACAO_AREA.maxZ; z++) {
          let bloco = level.getBlock(x, nivelAguaAtual, z);
          if (bloco.id === "minecraft:water") {
            bloco.set("minecraft:air");
          }
        }
      }

      nivelAguaAtual--;

      if (nivelAguaAtual < INUNDACAO_MIN_Y) {
        inundacaoDrenando = false;
        drenagemRapida = false;
        server.tell(
          `${S}eO sol volta a brilhar e a água recuou completamente. O evento terminou!`,
        );
      }
    }
  }

  if (restaurandoRios) {
    if (server.tickCount % 2 === 0) {
      for (let x = INUNDACAO_AREA.minX; x <= INUNDACAO_AREA.maxX; x++) {
        for (let z = INUNDACAO_AREA.minZ; z <= INUNDACAO_AREA.maxZ; z++) {
          let bloco = level.getBlock(x, nivelRestauracaoAtual, z);
          let biomeId = String(bloco.biomeId);

          if (
            biomeId.includes("river") ||
            biomeId.includes("ocean") ||
            biomeId.includes("swamp") ||
            biomeId.includes("beach")
          ) {
            if (
              bloco.id === "minecraft:air" ||
              bloco.id === "minecraft:cave_air"
            ) {
              bloco.set("minecraft:water");
            }
          }
        }
      }

      nivelRestauracaoAtual++;

      if (nivelRestauracaoAtual > RESTAURACAO_MAX_Y) {
        restaurandoRios = false;
        server.tell(
          `${S}aRestauração natural concluída! Os rios e oceanos voltaram ao normal.`,
        );
      }
    }
  }
});

function iniciarDrenagem(server) {
  if (!inundacaoAtiva) return;

  inundacaoAtiva = false;
  inundacaoDrenando = true;
  drenagemRapida = false;
  nivelAguaAtual = Math.min(nivelAguaAtual, INUNDACAO_MAX_Y);

  server.runCommandSilent(`bossbar remove ${INUNDACAO_BOSSBAR}`);
  server.tell(`${S}bO tempo acabou! A água começa a secar aos poucos...`);

  server.players.forEach((player) => {
    if (dentroDaAreaInundacao(player.x, player.z)) {
      if (!afogados.has(String(player.uuid))) {
        let arco = Item.of("minecraft:bow")
          .enchant("minecraft:infinity", 1)
          .enchant("minecraft:power", 3);

        player.give(arco);
        player.tell(
          `${S}aSobreviveste ao grande dilúvio! Receba o Arco Mágico!`,
        );
      } else {
        player.tell(
          `${S}cFoste engolido pelas águas. Nao recebeste a recompensa do evento.`,
        );
      }
    }
  });
}
