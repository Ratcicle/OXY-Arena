// kubejs/server_scripts/nevoa.js

function iniciarNevoa(ctx) {
  try {
    let srv = ctx.source.server;

    // 1. Limites do Evento
    let minX = 100;
    let maxX = 400;
    let minZ = 200;
    let maxZ = 500;
    let tamanhoFinal = 50;

    // 2. Sorteio do Centro Seguro
    let raioFinal = Math.floor(tamanhoFinal / 2);
    let cx =
      Math.floor(Math.random() * (maxX - raioFinal - (minX + raioFinal) + 1)) +
      (minX + raioFinal);
    let cz =
      Math.floor(Math.random() * (maxZ - raioFinal - (minZ + raioFinal) + 1)) +
      (minZ + raioFinal);

    // 3. Calcula o tamanho inicial
    let maxDistX = Math.max(Math.abs(cx - minX), Math.abs(maxX - cx));
    let maxDistZ = Math.max(Math.abs(cz - minZ), Math.abs(maxZ - cz));
    let tamanhoInicial = Math.max(maxDistX, maxDistZ) * 2;

    // 4. Calcula o tempo exato
    let blocosParaPerder = tamanhoInicial - tamanhoFinal;
    let tempoFechamento = Math.floor(blocosParaPerder * 0.5); // 0.5 segundos por bloco

    // Execucao da fronteira
    srv.runCommandSilent(`worldborder center ${cx} ${cz}`);
    srv.runCommandSilent(`worldborder set ${tamanhoInicial}`);
    srv.runCommandSilent("worldborder damage buffer 0");
    srv.runCommandSilent("worldborder damage amount 2");
    srv.runCommandSilent("worldborder warning distance 15");
    srv.runCommandSilent(`worldborder set ${tamanhoFinal} ${tempoFechamento}`);

    // Anuncios
    srv.tell(
      "§4§l[Alerta da Ruína] §cA Névoa começou a fechar a área do evento!",
    );
    srv.tell(
      `§7A zona segura está diminuindo em direção a X: ${cx}, Z: ${cz}. Fiquem atentos!`,
    );
    srv.runCommandSilent(
      "execute at @a run playsound minecraft:entity.wither.spawn master @a ~ ~ ~ 1 0.5",
    );

    return 1;
  } catch (err) {
    if (ctx.source.isPlayer()) {
      ctx.source.player.tell(`§c[Debug Nevoa] Ocorreu um erro: ${err}`);
    }
    console.error("Erro Nevoa: " + err);
    return 0;
  }
}

function pararNevoa(ctx) {
  try {
    let srv = ctx.source.server;

    srv.runCommandSilent("worldborder set 29999984");
    srv.runCommandSilent("worldborder center 0 0");
    srv.tell("§a§l[Evento] §fA Névoa da Ruína se dissipou. A área está livre.");

    return 1;
  } catch (err) {
    if (ctx.source.isPlayer()) {
      ctx.source.player.tell(`§c[Debug Nevoa] Erro no stop: ${err}`);
    }
    console.error("Erro Nevoa stop: " + err);
    return 0;
  }
}

ServerEvents.commandRegistry((event) => {
  const { commands: Commands } = event;

  event.register(
    Commands.literal("evento")
      .requires((src) => src.hasPermission(2))
      .then(
        Commands.literal("nevoa")
          .then(Commands.literal("start").executes((ctx) => iniciarNevoa(ctx)))
          .then(Commands.literal("stop").executes((ctx) => pararNevoa(ctx))),
      ),
  );
});
