const MINERACAO_DURACAO = 20 * 60 * 5;
const BOSSBAR_MIN = "kubejs:febre_mineracao";
var S = "\u00A7";

let eventoMineracaoAtivo = false;
let tempoMineracaoRestante = 0;

ServerEvents.commandRegistry((event) => {
  const { commands: Commands } = event;

  event.register(
    Commands.literal("evento").then(
      Commands.literal("mineracao")
        .then(
          Commands.literal("start").executes((ctx) => {
            if (eventoMineracaoAtivo) {
              ctx.source.sendSystemMessage(
                `${S}cO evento de mineração já está ativo!`,
              );
              return 0;
            }

            eventoMineracaoAtivo = true;
            tempoMineracaoRestante = MINERACAO_DURACAO;

            let server = ctx.source.server;

            server.runCommandSilent(
              `bossbar add ${BOSSBAR_MIN} "${S}eFebre da Mineração"`,
            );
            server.runCommandSilent(
              `bossbar set ${BOSSBAR_MIN} max ${MINERACAO_DURACAO}`,
            );
            server.runCommandSilent(
              `bossbar set ${BOSSBAR_MIN} value ${MINERACAO_DURACAO}`,
            );
            server.runCommandSilent(`bossbar set ${BOSSBAR_MIN} color yellow`);
            server.runCommandSilent(`bossbar set ${BOSSBAR_MIN} players @a`);
            server.runCommandSilent(`bossbar set ${BOSSBAR_MIN} visible true`);

            server.tell(
              `${S}e${S}lA FEBRE DA MINERAÇÃO COMECOU! ${S}fTodos os minérios vão dropar o ${S}ldobro${S}r${S}f por 5 minutos. Corram para as cavernas!`,
            );
            return 1;
          }),
        )
        .then(
          Commands.literal("stop").executes((ctx) => {
            finalizarEventoMineracao(ctx.source.server);
            return 1;
          }),
        ),
    ),
  );
});

ServerEvents.tick((event) => {
  if (!eventoMineracaoAtivo) return;
  if (event.server.tickCount % 20 !== 0) return;

  tempoMineracaoRestante -= 20;

  if (tempoMineracaoRestante <= 0) {
    finalizarEventoMineracao(event.server);
    return;
  }

  event.server.runCommandSilent(
    `bossbar set ${BOSSBAR_MIN} value ${tempoMineracaoRestante}`,
  );
});

function finalizarEventoMineracao(server) {
  if (!eventoMineracaoAtivo) return;

  eventoMineracaoAtivo = false;
  server.tell(
    `${S}cA Febre da Mineração terminou! Os minérios voltaram ao normal.`,
  );
  server.runCommandSilent(`bossbar remove ${BOSSBAR_MIN}`);
}

LootJS.modifiers((event) => {
  event.addBlockModifier(["#c:ores", "#forge:ores"]).modifyLoot("*", (item) => {
    if (eventoMineracaoAtivo) {
      item.count = item.count * 2;
    }

    return item;
  });
});
