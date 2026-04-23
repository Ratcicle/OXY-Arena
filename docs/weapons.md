# OXY Arena — Catálogo de Armas

Documento de referência com todas as armas do mod, incluindo dano, velocidade de ataque e o detalhamento técnico das habilidades passivas e ativas (ticks, cooldowns, fórmulas, listeners e estado mantido server-side).

> **Como ler os números**
> - **Dano**: valor exibido no tooltip (`1 + bônus do tier + bônus do item`).
> - **Velocidade**: ataques por segundo (`4.0 + modificador`).
> - **Cooldown**: tempo entre usos da habilidade ativa (em ticks e em segundos — 20 ticks = 1 s).
> - **Listener**: classe e método que implementa o efeito server-side.

---

## 1. Tiers de Ferramenta

Definidos em [ModToolTiers.java](src/main/java/com/example/oxyarena/registry/ModToolTiers.java).

| Tier | Bônus de Dano | Durabilidade | Velocidade Mineração | Nível | Encantabilidade |
|---|---|---|---|---|---|
| Citrine | +1.8 | 220 | 5.5 | iron | 13 |
| Cobalt | +2.8 | 1400 | 7.5 | diamond | 11 |
| Incandescent | +3.0 | 100 | 8.0 | diamond | 10 |
| Ametra | +4.0 | 2031 | 9.0 | netherite | 15 |

---

## 2. Espadas (Tier Citrine)

### Citrine Sword — [CitrineSwordItem.java](src/main/java/com/example/oxyarena/item/CitrineSwordItem.java)
- **Dano:** 5.5 | **Velocidade:** 1.6/s
- **Ativa (botão direito):** aplica o efeito `oxyarena:citrine_blade_rush` por **100 ticks (5 s)**, definido em [ModMobEffects.java:20-27](src/main/java/com/example/oxyarena/registry/ModMobEffects.java#L20-L27). O efeito adiciona um modificador de `Attributes.ATTACK_SPEED` de **+1.4 (ADD_VALUE)** — em uma espada base 1.6/s isso quase dobra a cadência (até ~3.0/s).
- **Cooldown:** 300 ticks (15 s), aplicado via `Player#getCooldowns().addCooldown(this, 300)`.
- **Side check:** o efeito é adicionado apenas em `!level.isClientSide`; o cooldown é gravado em ambos os lados para sincronia da HUD.

---

## 3. Espadas (Tier Cobalt)

### Cobalt Sword — [CobaltSwordItem.java](src/main/java/com/example/oxyarena/item/CobaltSwordItem.java)
- **Dano:** 6.8 | **Velocidade:** 1.6/s
- **Passiva — Penetração de Armadura (25%):** implementada em [CombatWeaponEvents.handleCobaltSwordArmorPenetration](src/main/java/com/example/oxyarena/event/gameplay/CombatWeaponEvents.java#L329-L355).
  - Hook: `LivingIncomingDamageEvent`.
  - Adiciona um `DamageContainer.Reduction.ARMOR` modifier que recalcula a redução com `armor * (1 - 0.25)`, usando `CombatRules.getDamageAfterAbsorb` com a toughness real do alvo.
  - Resultado clampado em `[0, incomingDamage]` para nunca virar dano negativo nem amplificar o golpe.
  - Ignora fontes que já são `DamageTypeTags.BYPASSES_ARMOR` (não duplica efeito).

### Flaming Scythe — [FlamingScytheItem.java](src/main/java/com/example/oxyarena/item/FlamingScytheItem.java)
- **Dano:** 8.0 | **Velocidade:** 1.2/s
- **Ativa (botão direito):** ignita o jogador por `200/20 = 10 s` (`Player#igniteForSeconds`) e registra o portador em `FLAMING_SCYTHE_ACTIVE_UNTIL` por **200 ticks (10 s)** via [CombatWeaponEvents.activateFlamingScythe](src/main/java/com/example/oxyarena/event/gameplay/CombatWeaponEvents.java#L118-L126).
- **Cooldown:** 500 ticks (25 s).
- **Conversão fogo → cura:** [CombatWeaponEvents.onLivingDamagePre](src/main/java/com/example/oxyarena/event/gameplay/CombatWeaponEvents.java#L66-L82) — durante o efeito ativo, qualquer fonte com `DamageTypeTags.IS_FIRE` recebida pelo jogador tem `setNewDamage(0)` e o valor original vira `player.heal(fireDamage)`.
- **Burn-on-hit:** [handleFlamingScytheDamagePost](src/main/java/com/example/oxyarena/event/gameplay/CombatWeaponEvents.java#L302-L314) chama `target.igniteForSeconds(4.0F)` em cada hit confirmado enquanto o estado estiver ativo.
- **Cleanup:** `pruneFlamingScytheState` roda a cada tick removendo entradas vencidas.

### Lifehunt Scythe — [LifehuntScytheItem.java](src/main/java/com/example/oxyarena/item/LifehuntScytheItem.java)
- **Dano:** 10.0 | **Velocidade:** 0.8/s
- **Passiva:** sobrescreve `SwordItem#postHurtEnemy`. Cura **2.0 HP** por hit, mas só se `player.getAttackStrengthScale(0.5F) >= 1.0F` (ou seja, requer cooldown de ataque vanilla totalmente regenerado — bloqueia spam).
- **Ativa (botão direito):** aplica `ModMobEffects.LIFEHUNT_BLOODLUST` por **160 ticks (8 s)**. Enquanto o efeito está presente, a cura por hit sobe para **6.0 HP**.
- **Cooldown:** 1200 ticks (60 s).

---

## 4. Espadas (Tier Incandescent)

### Incandescent Sword — [IncandescentSwordItem.java](src/main/java/com/example/oxyarena/item/IncandescentSwordItem.java)
- **Dano:** 7.0 | **Velocidade:** 1.6/s
- **Passiva — Burn on Hit:** [handleIncandescentDamagePost](src/main/java/com/example/oxyarena/event/gameplay/CombatWeaponEvents.java#L316-L327) chama `target.igniteForSeconds(4.0F)` em cada hit corpo-a-corpo (sword/pickaxe/axe).
- **Passiva — Auto-dano:** [tickIncandescentMainHandDamage](src/main/java/com/example/oxyarena/event/gameplay/CombatWeaponEvents.java#L423-L436) roda a cada **20 ticks (1 s)**: para todo player não-creative segurando um item Incandescent, aplica `player.hurt(damageSources().magic(), 1.0F)`. Vale também para `INCANDESCENT_INGOT` e `INCANDESCENT_THROWING_DAGGER`.

> Mesmo comportamento se aplica a `Incandescent Pickaxe` e `Incandescent Axe`.

---

## 5. Espadas (Tier Ametra)

### Ametra Sword — [AmetraSwordItem.java](src/main/java/com/example/oxyarena/item/AmetraSwordItem.java)
- **Dano:** 7.0 | **Velocidade:** 1.7/s
- **Ativa (botão direito):** aplica `AMETRA_AWAKENING` por **400 ticks (20 s)**. O efeito ([ModMobEffects.java:32-44](src/main/java/com/example/oxyarena/registry/ModMobEffects.java#L32-L44)) modifica:
  - `Attributes.ATTACK_DAMAGE`: **+3.0** (ADD_VALUE)
  - `Attributes.ATTACK_SPEED`: **−0.3** (ADD_VALUE)
- **Cooldown:** 1200 ticks (60 s).
- **Sweep custom:** enquanto o efeito está ativo, `onSweepAttack` cancela o sweep vanilla (`event.setSweeping(false)`) e [handleAmetraDamagePost](src/main/java/com/example/oxyarena/event/gameplay/CombatWeaponEvents.java#L215-L268) faz uma varredura própria — cada alvo dentro de `weapon.getSweepHitBox(...)` recebe `1.0 + 0.75 * dano_principal`, com knockback `0.4F` no eixo do yaw e som `PLAYER_ATTACK_SWEEP`. Usa um set `AMETRA_SWEEP_ATTACKERS` para impedir recursão durante o `hurt()` secundário.
- **Cleanup:** [inventoryTick](src/main/java/com/example/oxyarena/item/AmetraSwordItem.java#L66-L77) remove o efeito automaticamente se o jogador trocar a espada principal.

### Murasama — [MurasamaItem.java](src/main/java/com/example/oxyarena/item/MurasamaItem.java)
- **Dano:** 7.0 | **Velocidade:** 1.9/s
- **Passiva — Crit a cada 3º hit:** [handleMurasamaDamagePre](src/main/java/com/example/oxyarena/event/gameplay/CombatWeaponEvents.java#L162-L185) mantém `MURASAMA_COMBO_COUNTS: Map<UUID,Integer>`. A cada hit:
  - incrementa o contador; quando atinge 3, multiplica `event.getNewDamage()` por **1.5** e zera o contador, marcando o atacante em `MURASAMA_CRIT_ATTACKERS`.
  - se o jogador trocar a arma da mão principal, o contador é zerado.
- **Pós-hit:** [handleMurasamaDamagePost](src/main/java/com/example/oxyarena/event/gameplay/CombatWeaponEvents.java#L187-L208) chama `player.crit(target)` (partículas + animação) e toca `SoundEvents.PLAYER_ATTACK_CRIT`.
- **Sem requisito vanilla:** ignora a regra do crítico vanilla (jump + falling).

### Rivers of Blood — [RiversOfBloodItem.java](src/main/java/com/example/oxyarena/item/RiversOfBloodItem.java)
- **Dano:** 6.0 | **Velocidade:** 1.6/s
- **Passiva — Sangramento (data-driven):** aplicação definida em [item_combat_status_applications/rivers_of_blood.json](src/main/resources/data/oxyarena/item_combat_status_applications/rivers_of_blood.json) — adiciona **35.0 buildup** de `oxyarena:bleed` por hit.
- **Status `bleed`** ([combat_statuses/bleed.json](src/main/resources/data/oxyarena/combat_statuses/bleed.json)):
  - `max_buildup`: 100 → **3 hits** enchem o medidor
  - `decay_delay_ticks`: 60 / `decay_per_tick`: 1.0 → após 3 s sem hits, perde 1 buildup/tick
  - **Proc:** `4.0 + 0.10 * maxHealth` de dano (`ModDamageTypes.BLEED_PROC`), reseta o medidor (`reset_on_proc: true`)
- **Pipeline:** [CombatStatusEvents.applyStatus](src/main/java/com/example/oxyarena/combatstatus/CombatStatusEvents.java#L150-L187). O buildup recebido é reduzido pela armadura do alvo (`1 - armor * 0.02`, clampado em `[0.45, 1.0]`).
- **HUD:** progresso quantizado em 0–100 e enviado via `CombatStatusSyncPayload` somente quando muda.

### Black Blade — [BlackBladeItem.java](src/main/java/com/example/oxyarena/item/BlackBladeItem.java)
- **Dano:** 8.0 | **Velocidade:** 1.0/s
- **Passiva — Pulsos pós-hit:** [handleBlackBladeDamagePost](src/main/java/com/example/oxyarena/event/gameplay/CombatWeaponEvents.java#L286-L300) chama [BlackBladeDamageHelper.schedulePassiveDamage](src/main/java/com/example/oxyarena/event/gameplay/BlackBladeDamageHelper.java#L30-L32). Agenda **3 pulsos** de **1.0 dano** cada, em intervalos de **2 ticks** (ticks +2, +4, +6 após o golpe). Tipo de dano: `ModDamageTypes.BLACK_BLADE_PULSE`.
- **Ativa — Projétil carregado:** `startUsingItem` com `UseAnim.SPEAR` e duração máxima 72000. Em `releaseUsing`, se `chargeTicks >= 20` (1 s), spawna `BlackBladeProjectile` com `SHOOT_POWER = 2.8F`, inacurácia `0`. Som: `WITHER_SHOOT`.
- **Pulsos do projétil:** ao acertar, `scheduleProjectileDamage` cria **5 pulsos** de **1.0 dano** a cada 2 ticks (`BLACK_BLADE_PROJECTILE`).
- **Implementação dos pulsos:** [applyGuaranteedPulse](src/main/java/com/example/oxyarena/event/gameplay/BlackBladeDamageHelper.java#L90-L101) zera `target.invulnerableTime` antes do `hurt()` (impede agrupamento de iframes) e restaura no `finally`.
- **Anti-recursão:** os tipos `BLACK_BLADE_PULSE/PROJECTILE` são filtrados em `handleBlackBladeDamagePost`, evitando que pulsos engatilhem novos pulsos.

### Earthbreaker — [EarthbreakerItem.java](src/main/java/com/example/oxyarena/item/EarthbreakerItem.java)
- **Dano:** 6.5 | **Velocidade:** 1.4/s
- **Ativa — Carregamento:** `UseAnim.SPEAR`. `releaseUsing` exige `chargeTicks >= 8` (mínimo).
- **Tiers de carga** ([EarthbreakerCrackHelper.java:46-50](src/main/java/com/example/oxyarena/event/EarthbreakerCrackHelper.java#L46-L50)):
  | Carga (ticks) | Tier | Passos | Espessura | Profundidade | Dano |
  |---|---|---|---|---|---|
  | 8–19 | 1 | 10 | 3 | 3 | 5.0 |
  | 20–39 | 2 | 17 | 5 | 6 | 8.0 |
  | ≥ 40 | 3 | 25 | 7 | 10 | 12.0 |
- **Direção:** `player.getLookAngle()` projetado no plano XZ e normalizado.
- **Mecânica server-side:** cada tick, a fissura cresce um passo na direção, removendo blocos (`tryRemoveBlock`) com `RESTORE_DELAY_TICKS = 120` (6 s) para reaparecimento. Faixa de altura para dano: `LAYER_DAMAGE_HEIGHT = 2.5D` acima.
- **Restrições:** ignora `BEDROCK`, `OBSIDIAN`, `CRYING_OBSIDIAN`, `RESPAWN_ANCHOR`, portais e blocos com `blockEntity` ou `destroySpeed > 20`.
- **Restauração inteligente:** se houver `LivingEntity` dentro do bloco no tick programado, adia a restauração em `RESTORE_RETRY_TICKS = 10` para não engaiolar jogadores.
- **Cooldown:** 240 ticks (12 s).
- **Death message dedicada:** `death.attack.earthbreaker_crack` (e variante PvP `earthbreaker_crack.player`).

### Kusabimaru — [KusabimaruItem.java](src/main/java/com/example/oxyarena/item/KusabimaruItem.java)
- **Dano:** 6.5 | **Velocidade:** 1.7/s
- **Ativa (botão direito):** abre janela de deflect de **4 ticks (0,2 s)** via [CounterMobilityEvents.activateKusabimaruDeflect](src/main/java/com/example/oxyarena/event/gameplay/CounterMobilityEvents.java#L105-L113).
- **Deflect de hits melee:** em `LivingIncomingDamageEvent`, se a janela está aberta e o atacante é resolvível, `event.setCanceled(true)` e dispara `handleSuccessfulKusabimaruDeflect`.
- **Deflect de projéteis:** em `ProjectileImpactEvent`, o projétil é cancelado e tratado pela mesma rotina (consume retorno `true` impedindo o impacto vanilla).
- **PvP — Stun:** efeito `KUSABIMARU_STUN` aplicado por **15 ticks (0,75 s)** com:
  - `Attributes.MOVEMENT_SPEED`: **−1.0 multiplicado total** (paralisia)
  - `Attributes.ATTACK_SPEED`: **−1.0 multiplicado total** (impede atacar)
- **Cooldown:** 30 ticks (1,5 s).
- **Áudio:** cadeia de até **6 sons** consecutivos com janela `30 ticks` para encadear deflects.

### Soul Reaper — [SoulReaperItem.java](src/main/java/com/example/oxyarena/item/SoulReaperItem.java)
- **Dano:** 7.0 | **Velocidade:** 1.0/s
- **Ativa (botão direito):** alterna a flag `SoulReaperAltered` em `CustomData` do stack e dispara [SoulReaperFireHelper.activate](src/main/java/com/example/oxyarena/event/SoulReaperFireHelper.java#L61-L78). Spawna fogo `SOUL` (forma alterada) ou `NORMAL` (padrão).
- **Padrão "estrela":** 4 cardinais + 4 diagonais a partir do `origin`. Cada raio se expande até **8 blocos** (`MAX_RANGE`), saltando 1 bloco para cima/baixo se necessário (`findNextFireCell`).
- **Pré-requisitos da célula** (`canUseAsFireCell`): suporte sólido embaixo, sem fluido, e a célula é ar/folhagem/replaceable. Ignora bedrock-line.
- **Dano:**
  - **Imediato:** ao acender, `damageEntitiesOnBlock` aplica **4.0** (`damageSources().magic()`) zerando `invulnerableTime` para garantir o hit.
  - **DoT:** entidades pisando em uma célula recebem `igniteForSeconds(8.0F)`. [adjustFireTickDamage](src/main/java/com/example/oxyarena/event/SoulReaperFireHelper.java#L329-L341) força o dano de tick de fogo para no mínimo **2.0** enquanto o registro `BURNING_ENTITIES` estiver vivo.
- **Persistência das células:** `FIRE_DURATION_TICKS = 8` (cada layer queima por 8 ticks ≈ 0,4 s antes de ser extinta).
- **Cooldown:** 100 ticks (5 s).
- **Limpeza no shutdown:** `clearServerState` remove os blocos restantes em `ServerStoppingEvent`.

### Spectral Blade — [SpectralBladeItem.java](src/main/java/com/example/oxyarena/item/SpectralBladeItem.java)
- **Dano:** 5.0 | **Velocidade:** 1.6/s | Tier base: Iron (atributos custom)
- **Passiva — Marca espectral:** [MarkReplayEvents.recordSpectralBladeHit](src/main/java/com/example/oxyarena/event/gameplay/MarkReplayEvents.java#L225-L240) roda em `LivingDamageEvent.Post`. Cada hit:
  - registra o último alvo em `SPECTRAL_BLADE_LAST_TARGETS: Map<ownerUUID, targetUUID>`
  - cria uma `SpectralMarkEntity` viva por `MAX_AGE_TICKS = 400` (20 s — diverge do tooltip que diz 20 s; valor exato é 20 s).
- **Ativa (botão direito):** [consumeSpectralBladeMarks](src/main/java/com/example/oxyarena/event/gameplay/MarkReplayEvents.java#L64-L100):
  - exige alvo dentro de `√64 = 8 blocos` (`SPECTRAL_BLADE_CONSUME_RANGE_SQR = 64.0`).
  - chama `SpectralMarkEntity.consumeMarks(...)` retornando o número de marcas detonadas.
  - dano final = `(float) consumedMarks` aplicado como `playerAttack`, com `invulnerableTime = 0` para garantir o hit.
  - usa `SPECTRAL_BLADE_BURST_ATTACKERS` como guard para impedir que o burst gere novas marcas.

### Assassin Dagger — [AssassinDaggerItem.java](src/main/java/com/example/oxyarena/item/AssassinDaggerItem.java)
- **Dano:** 4.0 | **Velocidade:** 2.0/s | Tier base: Iron (atributos custom: +3.0 dmg, −2.0 spd em `MAINHAND`)
- **Passiva — Backstab x2:** [MarkReplayEvents.recordAssassinDaggerHit](src/main/java/com/example/oxyarena/event/gameplay/MarkReplayEvents.java#L242-L295). Critério: `targetForward · attackDirection ≤ -0.35` (cone traseiro). Quando satisfeito, multiplica o dano por 2 antes do `hurt`.
- **Histórico de dano:** `ASSASSIN_DAGGER_DAMAGE_HISTORY: Map<owner, Map<target, Deque<AssassinDamageSnapshot>>>` retém hits dos últimos **40 ticks (2 s)** (`ASSASSIN_DAGGER_HISTORY_TICKS`). `cleanupAssassinDaggerHistory` poda entradas antigas a cada server tick.
- **Ativa (botão direito):** [activateAssassinDagger](src/main/java/com/example/oxyarena/event/gameplay/MarkReplayEvents.java#L118-L175):
  - alvo deve estar a no máximo `√64 = 8 blocos`.
  - reproduz o histórico mantendo o **espaçamento original** entre os hits (delay = `snapshot.tick - firstTick`).
  - cada pulso aplica `damage * 0.5` (`ASSASSIN_DAGGER_REPLAY_DAMAGE_MULTIPLIER`) — o primeiro pulso é instantâneo, os demais entram em `ASSASSIN_DAGGER_PENDING_REPLAY_PULSES`.
  - guard `ASSASSIN_DAGGER_REPLAY_ATTACKERS` impede que os pulsos sejam re-gravados como histórico.
- **Cooldown:** 100 ticks (5 s).

### Zero-Reverse — [ZeroReverseItem.java](src/main/java/com/example/oxyarena/item/ZeroReverseItem.java)
- **Dano:** 6.7 | **Velocidade:** 1.6/s
- **Ativa (botão direito):** [ZeroReverseRewindHelper.activate](src/main/java/com/example/oxyarena/event/ZeroReverseRewindHelper.java) procura no buffer circular o snapshot mais antigo a `currentTick - 80` ticks (`REWIND_TICKS = 80` = 4 s).
- **Buffer:** mantém `BUFFER_TICKS = 120` ticks (6 s) por jogador. Snapshot inclui posição (3D + dimensão), `health`, `foodData`, `fireTicks`, lista de `MobEffectInstance`, e velocidade.
- **Falha graciosa:** se não houver snapshot suficiente, toca `TRIPWIRE_CLICK_OFF` e devolve `InteractionResultHolder.fail` (não consome cooldown).
- **Cooldown:** 900 ticks (45 s).
- **Snapshot loop:** `onServerTickPost` registra o estado de cada player a cada tick.

### Necromancer Staff — [NecromancerStaffItem.java](src/main/java/com/example/oxyarena/item/NecromancerStaffItem.java)
- **Dano:** 5.0 | **Velocidade:** 1.6/s
- **Storage NBT:** lista `Souls` (List<CompoundTag>) e `SelectedSoulIndex` em `DataComponents.CUSTOM_DATA`. Capacidade: **8 almas**.
- **Captura:** ouvinte server-side em `NecromancerStaffEvents` armazena o entity tag do mob morto (hostil ou elite) — `addSoul` ignora se a lista estiver cheia.
- **Ativa (botão direito):** invoca a alma selecionada (`summonSelectedSoul`). Limite simultâneo: `MAX_ACTIVE_SUMMONS = 3`. Cooldown da invocação: `SUMMON_COOLDOWN_TICKS = 20` (1 s).
- **Shift+click:** retorna a invocação ativa (handled em `NecromancerStaffEvents`).
- **Cycling:** `cycleSelectedSoul` aplica `floorMod(index + dir, size)` e atualiza o tag.
- **Display:** tooltip mostra `getSoulCount/MAX_SOULS` — HUD própria mostra o nome da alma selecionada.

### Frozen Needle — [FrozenNeedleItem.java](src/main/java/com/example/oxyarena/item/FrozenNeedleItem.java)
- **Dano:** 5.8 | **Velocidade:** 2.0/s
- **Passiva — Frostbite (data-driven):** aplicação em [item_combat_status_applications/frozen_needle.json](src/main/resources/data/oxyarena/item_combat_status_applications/frozen_needle.json) — **25.0 buildup** por hit.
- **Status `frostbite`** ([combat_statuses/frostbite.json](src/main/resources/data/oxyarena/combat_statuses/frostbite.json)):
  - `max_buildup`: 100 → **4 hits** enchem o medidor
  - **Proc:** `2.0 + 0.05 * maxHealth` (`FROSTBITE_PROC`), reseta o medidor
  - **Pós-proc:** `post_proc_duration_ticks: 300` (15 s) com `post_proc_damage_taken_multiplier: 1.2` — alvo recebe **+20% de dano** durante o congelamento
  - `block_buildup_while_active: true` — não acumula durante o congelamento
  - `remove_active_on_fire_damage: true` — qualquer dano de fogo descongela e zera o medidor (ver [removeFireSensitiveActiveStatuses](src/main/java/com/example/oxyarena/combatstatus/CombatStatusEvents.java#L406-L426))
- **Aplicação proporcional à armadura** (mesma fórmula do bleed): buildup * `clamp(1 - armor*0.02, [0.45, 1.0])`.

### Ghost Saber — [GhostSaberItem.java](src/main/java/com/example/oxyarena/item/GhostSaberItem.java)
- **Dano:** 6.5 | **Velocidade:** 1.7/s
- **Ativa (botão direito):** [GhostSaberEvents.activate](src/main/java/com/example/oxyarena/event/gameplay/GhostSaberEvents.java#L66-L102):
  - dash até **6 blocos** (`DASH_RANGE = 6.0`) na direção do olhar, em **6 ticks** (`DASH_DURATION_TICKS`).
  - i-frames: `LivingIncomingDamageEvent` cancela qualquer dano recebido enquanto o atacante está em `GHOST_SABER_DAMAGE_ATTACKERS` (set rodando durante a propagação do dash).
  - dano por contato: **7.5** ao longo do segmento (`damageAlongSegment`).
- **Eco:** entidade `GhostSaberEchoEntity` é agendada para **30 ticks (1,5 s)** depois (`ECHO_DELAY_TICKS`), reproduz o trajeto em **6 ticks** com **5.0 dano** e fica **6 ticks** lingering.
- **Reset de cooldown:** kills causadas pelo dash ou pelo eco zeram o cooldown via `Player#getCooldowns().removeCooldown(...)`.
- **Cooldown:** 240 ticks (12 s).
- **HitRadius:** detecção via `distanceToSegmentSqr` com `HIT_RADIUS` calibrado.

### Zenith — [ZenithItem.java](src/main/java/com/example/oxyarena/item/ZenithItem.java)
- **Dano:** 7.0 | **Velocidade:** 1.6/s
- **Ativa (botão direito):** spawna **9 espadas orbitais** ([ZenithOrbitSwordEntity.spawnAll](src/main/java/com/example/oxyarena/entity/effect/ZenithOrbitSwordEntity.java#L134-L152)) seguindo o spec abaixo:

  | # | Espada base | Dano | Raio | Inclinação | Fase | Direção |
  |---|---|---|---|---|---|---|
  | 1 | Wooden Sword | 4.0 | 1.0 | −12° | 0° | + |
  | 2 | Stone Sword | 5.0 | 1.875 | −9° | 45° | − |
  | 3 | Iron Sword | 6.0 | 2.75 | −6° | 72° | + |
  | 4 | Golden Sword | 4.0 | 3.625 | −3° | 135° | − |
  | 5 | Diamond Sword | 7.0 | 4.5 | 0° | 144° | + |
  | 6 | Netherite Sword | 8.0 | 5.375 | +3° | 225° | − |
  | 7 | Citrine Sword | 5.5 | 6.25 | +6° | 216° | + |
  | 8 | Cobalt Sword | 6.8 | 7.125 | +9° | 315° | − |
  | 9 | Ametra Sword | 7.0 | 8.0 | +12° | 288° | + |

- **Duração:** `DURATION_TICKS = 50` (2,5 s).
- **Hitbox por tick:** `AABB(prevPos, currPos).inflate(CONTACT_RADIUS = 1.0)`. Cada espada mantém um set `hitTargetIds` — só atinge cada alvo **uma vez** por ativação.
- **Center offset:** `0.9D` acima do owner. `discardOwnedOrbitSwords` é chamado antes do spawn para evitar duplicação ao reativar.
- **Cooldown:** 350 ticks (17,5 s).

---

## 6. Espadas (Atributos Customizados)

### Black Diamond Sword — [BlackDiamondSwordItem.java](src/main/java/com/example/oxyarena/item/BlackDiamondSwordItem.java)
- **Dano:** 7.0 | **Velocidade:** 1.5/s | Tier base: Diamond
- **Passiva — Shred de durabilidade:** [handleBlackDiamondSwordDamagePost](src/main/java/com/example/oxyarena/event/gameplay/CombatWeaponEvents.java#L270-L284) chama:
  - [applyBlackDiamondArmorDurabilityDamage](src/main/java/com/example/oxyarena/event/gameplay/CombatWeaponEvents.java#L383-L396): cada peça de armadura do alvo (HEAD/CHEST/LEGS/FEET) recebe `hurtAndBreak(9, target, slot)`.
  - Se o alvo é `Player`, [applyBlackDiamondWeaponDurabilityDamage](src/main/java/com/example/oxyarena/event/gameplay/CombatWeaponEvents.java#L398-L407) e offhand: ambas as armas recebem `hurtAndBreak(10, target, slot)`.
- **Recompensa do evento `Bob Miniboss`** (não craftável fora do drop).

### Chocolate Sword — [ChocolateSwordItem.java](src/main/java/com/example/oxyarena/item/ChocolateSwordItem.java)
- **Dano:** 6.0 | **Velocidade:** 1.6/s | Tier base: Iron
- **Comestível:** registra `FoodComponent` (sobrescreve `getUseAnimation` para `EAT`) restaurando fome e saturação.
- **Speed I (5 s):** ao terminar de comer, aplica `MobEffects.MOVEMENT_SPEED, 100 ticks, lvl 0`.

---

## 7. Manopla / Arma de Disparo

### Elemental Gauntlet — [ElementalGauntletItem.java](src/main/java/com/example/oxyarena/item/ElementalGauntletItem.java)
- **Durabilidade:** 910
- **Ativa — Disparo automático:** `startUsingItem` com duração 72000. `onUseTick` dispara um projétil a cada **3 ticks** (`FIRE_INTERVAL_TICKS`), totalizando ~6,67 tiros/s enquanto o botão direito estiver pressionado.
- **Padrão de tiros:** `SHOT_PATTERN_LENGTH = 20`, com `variant = shotIndex % 4` (4 elementos cíclicos) e `knockbackBurst = (shotIndex % 5 == 4)` — **a cada 5º tiro** é um burst de knockback. O índice é persistido em `ElementalGauntletShotIndex` no `CustomData`.
- **Projétil** ([ElementalGauntletProjectile.java](src/main/java/com/example/oxyarena/entity/projectile/ElementalGauntletProjectile.java)):
  - `BASE_DAMAGE = 2.0`, `MAX_LIFE_TICKS = 50` (2,5 s), `SHOOT_POWER = 2.8`, `SHOOT_INACCURACY = 0.05`
  - **Homing:** raio de busca `8.0` blocos, cone `dot ≥ 0.2`, taxa de curva `0.16`, velocidade mínima `0.35`.
  - **Splash:** `2.6` blocos de raio em cone `dot ≥ 0.6` por **1.0 dano** atrás do alvo.
  - **Knockback:** `0.42` quando `knockback_burst = true`.
- **Custo:** cada disparo aplica `stack.hurtAndBreak(1, player, MAINHAND)`.

---

## 8. Arco e Escudo

### Cobalt Bow — [CobaltBowItem.java](src/main/java/com/example/oxyarena/item/CobaltBowItem.java)
- **Durabilidade:** 768
- **Passiva — Arrow Rain:** override de `customArrow` adiciona o tag `OxyArenaCobaltArrowRain` ao `getPersistentData()` da flecha.
- **Engatilhamento:** [ProjectileSpecialEvents](src/main/java/com/example/oxyarena/event/gameplay/ProjectileSpecialEvents.java) detecta o impacto e enfileira um `CobaltArrowRainWave`:
  - `COBALT_ARROW_RAIN_WAVES = 10` ondas
  - `COBALT_ARROW_RAIN_ARROWS_PER_TICK = 3` flechas por tick
  - `COBALT_ARROW_RAIN_RADIUS = 4.5` blocos (distribuição uniforme em disco via `radius * sqrt(rand)`)
  - `COBALT_ARROW_RAIN_HEIGHT = 16.0` blocos acima do alvo
  - `COBALT_ARROW_RAIN_DAMAGE = 0.5` por flecha-chuva
  - `COBALT_ARROW_RAIN_VELOCITY = 2.6F` (cai a `(0, -1, 0)`)
- **Anti-recursão:** flechas-chuva não voltam a engatilhar — o tag `Triggered` impede o disparo em cadeia.

### Cobalt Shield — [CobaltShieldItem.java](src/main/java/com/example/oxyarena/item/CobaltShieldItem.java)
- **Durabilidade:** 750
- **Passiva — Shockwave ao bloquear:** [handleCobaltShieldShockwave](src/main/java/com/example/oxyarena/event/gameplay/CombatWeaponEvents.java#L357-L381):
  - Trigger: `defender.isBlocking() && defender.isDamageSourceBlocked(source)` com `useItem == COBALT_SHIELD`.
  - Empurra todos os `LivingEntity` num raio de **4.5 blocos** com knockback `1.1F`, na direção radial (do defensor para o alvo). Se o alvo está no mesmo bloco, usa o eixo de visão como fallback.

---

## 9. Adagas Arremessáveis

### Citrine Throwing Dagger — [CitrineThrowingDaggerItem.java](src/main/java/com/example/oxyarena/item/CitrineThrowingDaggerItem.java)
- **Stack:** 16 | **Dano do projétil:** 3.0 ([CitrineThrowingDagger.java](src/main/java/com/example/oxyarena/entity/projectile/CitrineThrowingDagger.java))
- **Cooldown de uso:** 10 ticks (0,5 s).
- **Mecânica:** estende `AbstractArrow` com `SHOOT_POWER = 2.0F`, `SHOOT_INACCURACY = 0.5F`. Usa `pickup = CREATIVE_ONLY` para `hasInfiniteMaterials()`, caso contrário pickup normal. Som: `TRIDENT_THROW`.

### Incandescent Throwing Dagger — [IncandescentThrowingDaggerItem.java](src/main/java/com/example/oxyarena/item/IncandescentThrowingDaggerItem.java)
- **Stack:** 16 | **Dano do projétil:** 3.0 ([IncandescentThrowingDagger.java](src/main/java/com/example/oxyarena/entity/projectile/IncandescentThrowingDagger.java))
- **Passivas:**
  - **Arremessada:** ao impactar, ignita o alvo (incluído no projétil).
  - **Na mão principal:** entra no scan `tickIncandescentMainHandDamage` (1 dano a cada 1 s no portador), mesma lógica das ferramentas Incandescent.

---

## 10. Arremessáveis Especiais

### Zeus Lightning — [ZeusLightningItem.java](src/main/java/com/example/oxyarena/item/ZeusLightningItem.java)
- **Dano corpo-a-corpo:** 5.0 (atributos custom +4.0 dmg, −2.0 spd) | **Velocidade:** 2.0/s
- **Lançamento:** `UseAnim.SPEAR`, `THROW_THRESHOLD_TIME = 10` ticks (0,5 s) de carregamento mínimo. `SHOOT_POWER = 2.5F`.
- **Projétil ([ThrownZeusLightning.java](src/main/java/com/example/oxyarena/entity/projectile/ThrownZeusLightning.java)):**
  - `PROJECTILE_DAMAGE = 5.0`
  - No impacto: spawna `LightningBolt` na posição (efeito de raio vanilla).
- **Pickup:** `CREATIVE_ONLY` se `hasInfiniteMaterials()`, caso contrário recolhe.

### Storm Charge — [StormChargeItem.java](src/main/java/com/example/oxyarena/item/StormChargeItem.java)
- **Cooldown:** 10 ticks (0,5 s). `stack.consume(1, player)` por uso.
- **Lançamento:** `SHOOT_POWER = 1.5F` com inacurácia 1.0F (espalha bastante).
- **Self-boost:** [CounterMobilityEvents.grantStormChargeFallImmunity](src/main/java/com/example/oxyarena/event/gameplay/CounterMobilityEvents.java#L91-L99) — se o jogador está a ≤ `√36 = 6 blocos` da explosão, recebe **80 ticks (4 s) de imunidade a fall damage** (registrado em `STORM_CHARGE_FALL_IMMUNE_UNTIL`).
- **Implementa `ProjectileItem`** para uso em dispensers (`asProjectile`).

### Smoke Bomb — [SmokeBombItem.java](src/main/java/com/example/oxyarena/item/SmokeBombItem.java)
- **Stack:** 16 — utilitário.

---

## 11. Resumo Rápido por Dano

| Arma | Dano | Vel. (atk/s) | Tier |
|---|---|---|---|
| Lifehunt Scythe | 10.0 | 0.8 | Cobalt |
| Black Blade | 8.0 | 1.0 | Ametra |
| Flaming Scythe | 8.0 | 1.2 | Cobalt |
| Ametra Sword | 7.0 | 1.7 | Ametra |
| Murasama | 7.0 | 1.9 | Ametra |
| Soul Reaper | 7.0 | 1.0 | Ametra |
| Zenith | 7.0 | 1.6 | Ametra |
| Black Diamond Sword | 7.0 | 1.5 | Diamond |
| Incandescent Sword | 7.0 | 1.6 | Incandescent |
| Cobalt Sword | 6.8 | 1.6 | Cobalt |
| Zero-Reverse | 6.7 | 1.6 | Ametra |
| Earthbreaker | 6.5 | 1.4 | Ametra |
| Kusabimaru | 6.5 | 1.7 | Ametra |
| Ghost Saber | 6.5 | 1.7 | Ametra |
| Chocolate Sword | 6.0 | 1.6 | Iron |
| Rivers of Blood | 6.0 | 1.6 | Ametra |
| Frozen Needle | 5.8 | 2.0 | Ametra |
| Citrine Sword | 5.5 | 1.6 | Citrine |
| Spectral Blade | 5.0 | 1.6 | Iron |
| Necromancer Staff | 5.0 | 1.6 | Ametra |
| Zeus Lightning | 5.0 | 2.0 | — |
| Assassin Dagger | 4.0 | 2.0 | Iron |

---

## 12. Apêndice — Pipeline de Combate

A maior parte das passivas é despachada via [ModGameEvents.java](src/main/java/com/example/oxyarena/event/ModGameEvents.java) que age como roteador para os hooks NeoForge:

| Hook | Subscritores relevantes |
|---|---|
| `LivingDamageEvent.Pre` | `CombatWeaponEvents.handleMurasamaDamagePre`, conversão fogo→cura do Flaming Scythe, Soul Reaper fire-tick adjust, `MarkReplayEvents` |
| `LivingIncomingDamageEvent` | `CounterMobilityEvents` (Kusabimaru deflect, Storm Charge fall), `GhostSaberEvents` (i-frames), `CombatStatusEvents` (multiplier por status), `CombatWeaponEvents` (Cobalt sword pen, Cobalt shield shockwave) |
| `LivingDamageEvent.Post` | `CombatWeaponEvents` (Murasama crit FX, Black Blade pulse schedule, Black Diamond shred, Flaming Scythe burn, Incandescent burn, Ametra sweep), `CombatStatusEvents` (apply bleed/frostbite buildup), `MarkReplayEvents` (record marks/history) |
| `ProjectileImpactEvent` | `CounterMobilityEvents` (Kusabimaru projectile deflect), `ProjectileSpecialEvents` (Cobalt arrow rain trigger) |
| `SweepAttackEvent` | `CombatWeaponEvents.onSweepAttack` (cancela sweep vanilla durante Ametra Awakening) |
| `ServerTickEvent.Post` | Pulsos do Black Blade, decay de combat statuses, replay do Assassin Dagger, eco do Ghost Saber, shockwaves do Cobalt Shield queue, fissuras do Earthbreaker, padrão de fogo do Soul Reaper, snapshots do Zero-Reverse, Mantle/Slide/Step assist, Occult camouflage |

**Tipos de dano custom** ([data/oxyarena/damage_type/](src/main/resources/data/oxyarena/damage_type/)):
- `bleed_proc`, `frostbite_proc` — used pelos status data-driven
- `black_blade_pulse`, `black_blade_projectile` — pulsos da Black Blade (excluídos da própria detecção pra evitar loop)
- `earthbreaker_crack` — usado para mensagens de morte dedicadas
- `elemental_gauntlet_projectile`

**Status de combate data-driven** ([data/oxyarena/combat_statuses/](src/main/resources/data/oxyarena/combat_statuses/)):
- `bleed.json` (Rivers of Blood)
- `frostbite.json` (Frozen Needle)
- Carregados em runtime pelo `CombatStatusDataManager`. Vinculação item→status definida em `item_combat_status_applications/`.

---

*Documento gerado a partir do estado atual de [ModItems.java](src/main/java/com/example/oxyarena/registry/ModItems.java), dos arquivos de cada item em [src/main/java/com/example/oxyarena/item/](src/main/java/com/example/oxyarena/item/), dos handlers em [src/main/java/com/example/oxyarena/event/gameplay/](src/main/java/com/example/oxyarena/event/gameplay/) e dos data-packs em [src/main/resources/data/oxyarena/](src/main/resources/data/oxyarena/).*
