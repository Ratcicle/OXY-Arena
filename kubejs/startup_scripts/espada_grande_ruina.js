// kubejs/startup_scripts/espada_ruina_startup.js

StartupEvents.registry("item", (event) => {
  // Definir como 'sword' corrige a posição de segurar na mão
  event
    .create("espada_grande_ruina", "sword")
    .displayName("Espada Grande da Ruína")
    .maxDamage(2500)
    .unstackable();
});

ItemEvents.modification((event) => {
  event.modify("kubejs:espada_grande_ruina", (item) => {
    // Matemática para 1.21.1 (Compensando os valores base do player):
    // Dano: 10 (alvo) - 1.0 (base) = 9.0
    item.attackDamage = 9.0;
    // Velocidade: 1.4 (alvo) - 4.0 (base) = -2.6
    item.attackSpeed = -2.6;
  });
});
