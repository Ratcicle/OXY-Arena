// kubejs/startup_scripts/arco_startup.js

StartupEvents.registry("item", (event) => {
  // Remova o parâmetro 'bow', deixando apenas o nome do item
  event
    .create("arco_tempestade")
    .displayName("Arco da Tempestade")
    .maxDamage(384)
    .unstackable();
});
