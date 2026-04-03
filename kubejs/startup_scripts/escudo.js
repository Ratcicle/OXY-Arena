// server_scripts/shield_durability.js

ItemEvents.modification(event => {
  event.modify('minecraft:shield', item => {
    item.maxDamage = 20;
  });
});