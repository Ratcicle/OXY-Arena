// kubejs/server_scripts/arco_server.js

ItemEvents.rightClicked("kubejs:arco_tempestade", (event) => {
  const { player, level, item } = event;

  // 1. Cooldown de 10 ticks (meio segundo). Isso faz ele atirar 2x mais rápido que o normal
  if (player.getCooldowns().isOnCooldown(item)) return;

  // 2. Sistema de munição
  if (!player.isCreative()) {
    let temFlecha = player.inventory.count("minecraft:arrow") > 0;
    if (!temFlecha) {
      level.runCommandSilent(
        `execute at ${player.username} run playsound minecraft:block.dispenser.fail player @a ~ ~ ~ 1 1.2`,
      );
      return;
    }

    level.server.runCommandSilent(`clear ${player.username} minecraft:arrow 1`);
    item.damageValue++;
    if (item.damageValue >= item.maxDamage) item.count--; // Quebra o arco se passar do limite
  }

  // 3. O Disparo
  let look = player.getLookAngle();
  let flecha = level.createEntity("arrow");

  // Posição: Sai exatamente do olho do jogador
  flecha.setPosition(player.x, player.getEyePosition().y() - 0.1, player.z);

  // Velocidade: 3.0 é a velocidade de um arco vanilla totalmente carregado
  flecha.setMotion(look.x * 3.0, look.y * 3.0, look.z * 3.0);

  // Dano: Injetamos o NBT de dano base da flecha (3.0d equivale a uns 6-9 de dano no alvo)
  // O 'pickup:1b' permite recolher a flecha do chão se você errar!
  flecha.mergeNbt(`{damage:3.0d, pickup:1b}`);

  // Define o atirador (para o jogo saber quem matou e a kill contar para você)
  flecha.setOwner(player);

  // Spawna a flecha
  flecha.spawn();

  // 4. Efeitos Visuais e Sonoros
  // Aplica o cooldown visual na barra
  player.getCooldowns().addCooldown(item.item, 10);

  // Toca o som do arco
  level.runCommandSilent(
    `execute at ${player.username} run playsound minecraft:entity.arrow.shoot player @a ~ ~ ~ 1 1.2`,
  );
});
