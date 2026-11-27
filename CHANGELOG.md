# Changelog - MinestomMacePvP

Dit document beschrijft in detail alle aanpassingen, stappen en verbeteringen die zijn doorgevoerd om volledige vanilla parity (Minecraft 1.21) te bereiken op de nieuwste Minestom versie (`2026.10.07-26.2`).

---

## Inhoudsopgave
1. [Stap 1: Minestom Upgrade & Core API Migratie](#stap-1-minestom-upgrade--core-api-migratie)
2. [Stap 2: 1.21 PvP Wapen- en Gevechtsfunctionaliteiten](#stap-2-121-pvp-wapen--en-gevechtsfunctionaliteiten)
3. [Stap 3: Interactieve Testserver (PvpServer)](#stap-3-interactieve-testserver-pvpserver)
4. [Stap 4: Paard Dismount & Lighting Engine](#stap-4-paard-dismount--lighting-engine)
5. [Stap 5: Categorie B Parity Discrepancies & Bugfixes](#stap-5-categorie-b-parity-discrepancies--bugfixes)
6. [Stap 6: Uitgebreide Unit Test Suite](#stap-6-uitgebreide-unit-test-suite)
7. [Stap 7: Attribute swapping & vanilla parity audit](#stap-7-attribute-swapping--vanilla-parity-audit)

---

## Stap 1: Minestom Upgrade & Core API Migratie
- **Minestom versie:** Bijgewerkt naar de nieuwste versie `2026.10.07-26.2`.
- **Breaking API Changes:**
  - Migratie van `ItemComponent` naar `DataComponents` voor component-gebaseerde data in Minestom (durability, unbreaking, custom model data).
  - Aanpassing van registry-aanroepen (`DamageType`, `SoundEvent`, `Enchantment`).
  - Correctie van `Sound.Source` en geluidsbron-identifiers (o.a. `Sound.Source.BLOCK` i.p.v. verouderde enums).
  - Synchronisatie van server tick rates met `ServerFlag.SERVER_TICKS_PER_SECOND`.

---

## Stap 2: 1.21 PvP Wapen- en Gevechtsfunctionaliteiten
### 1. Mace (Strijdhamer)
- **Smash Attack Mechanisme:** Valafstand wordt omgezet in extra kinetic schade volgens vanilla formules.
- **Valbescherming:** Wanneer een smash attack slaagt op een doelwit (of wordt geblokkeerd door een schild), wordt de valafstand van de aanvaller gereset naar 0 zodat er geen valschade wordt geleden.
- **AoE Shockwave Knockback:** Vijanden binnen 3.5 blokken rond het doelwit worden weggeslagen met afstands- en knockback-resistance schaling.
- **Wind Burst Betovering:** Geeft de aanvaller na een succesvolle smash hit direct een opwaartse impuls ($1.2$, $1.75$ of $2.2$ blocks/tick geschaald naar ticks per seconde: $24.0$ tot $44.0$ b/s).

### 2. Speer (Spears)
- **Aanvalsrange & Lunges:** Ondersteuning voor speeraanvallen met verhoogde reach en lunge-betoveringen.
- **Dismounting:** Treft rijdende spelers en haalt hen van hun rijdier af bij een succesvolle speerhit.

### 3. Wind Charges
- Geïmplementeerd in [`src/main/java/io/github/togar2/pvp/entity/projectile/WindCharge.java`](src/main/java/io/github/togar2/pvp/entity/projectile/WindCharge.java).
- **Wind Charge Jump:** Schieten onder je eigen voeten lanceert de speler verticaal omhoog (~$26$ blokken/sec) en reset valschade.
- **Ontploffing & Knockback:** Omringende entiteiten worden weggestoten op basis van afstand en `Attribute.KNOCKBACK_RESISTANCE`.
- Geluiden (`ENTITY_WIND_CHARGE_THROW`, `ENTITY_WIND_CHARGE_WIND_BURST`) en gust particles toegevoegd.

### 4. Ender Pearls
- Geïmplementeerd in projectile pipeline met correcte parabool-fysica, impactdetectie, spelerteleportatie en vanilla valschade (5.0 damage).

---

## Stap 3: Interactieve Testserver (PvpServer)
- Geïmplementeerd in [`src/test/java/io/github/togar2/pvp/test/PvpServer.java`](src/test/java/io/github/togar2/pvp/test/PvpServer.java).
- **Interactieve Kit Selectie GUI:** Spelers kunnen wapens (Mace, Diamond Sword, Spear, Netherite Axe, Bow, Crossbow, Wind Charges, Ender Pearls, Shield) en armor sets (Netherite, Diamond, Iron) kiezen.
- **Modusselectie:**
  - Vechten tegen een interactieve **NPC Dummy** (met configureerbare armor en knockback).
  - Vechten tegen **andere spelers** in de arena.
- **Paarden:** Spawnbaar getemd paard met zadel om ruiterspeelstijl en speer-dismounting te testen.

---

## Stap 4: Paard Dismount & Lighting Engine
- **Paard Dismount Fix:**
  - `PlayerInputEvent` en `ClientInputPacket` luisteren naar de Shift/Sneak toets om passagiers direct te laten afstappen (`entity.removePassenger(player)`).
  - Extra fallback commando `/dismount` en `RightClickEntityEvent` ondersteuning.
- **Lighting Engine Fix:**
  - `arenaInstance.setChunkSupplier(LightingChunk::new)` geactiveerd voor dynamische Minestom lichtberekening.
  - Skylight ingeschakeld (`skylight(true)`), vaste daglichttijd op tick 6000 gezet en sea lanterns grid geplaatst om donkere schaduwen te voorkomen.
  - Night Vision effect toegepast als extra verlichting.

---

## Stap 5: Categorie B Parity Discrepancies & Bugfixes
In deze stap zijn alle 8 geïdentificeerde Categorie B vanilla combat bugs aangepakt:

### 1. Schild Pitch Bug (`VanillaBlockFeature.java`)
- **Probleem:** Schildblokkades berekenden het inproduct via `entityPos.direction()`, waarin de kijkhoek (pitch) was meegenomen. Wanneer een speler naar beneden (voeten) of naar boven (lucht) keek, faalde de dot-product berekening waardoor aanvallen van voren dwars door het schild gingen.
- **Oplossing:** Pitch wordt nu geforceerd op `0.0f` voor de horizontale blikrichting:
  ```java
  Vec entityRotation = entityPos.withPitch(0.0f).direction().withY(0).normalize();
  Vec attackerDirection = entityPos.asVec().sub(attackerPos.asVec()).withY(0);
  if (attackerDirection.isZero()) return true;
  attackerDirection = attackerDirection.normalize();
  return attackerDirection.dot(entityRotation) < 0.0;
  ```

### 2. Schild 5-Tick Warm-Up Vertraging (`VanillaBlockFeature.java`)
- **Probleem:** In vanilla duurt het 5 ticks ($0.25$ seconde) na het heffen van een schild voordat inkomende aanvallen worden geblokkeerd (`Player.getCurrentItemUseTime() >= 5`). Voorheen blokkeerde het schild al op tick 0.
- **Oplossing:** 5-tick warm-up verificatie toegevoegd:
  ```java
  if (entity instanceof Player player && player.getCurrentItemUseTime() < 5) {
      return false;
  }
  ```

### 3. Armor Durability Degradatie (`VanillaDamageFeature.java`)
- **Probleem:** `itemDamageFeature.damageArmor` werd uitsluitend aangeroepen als `typeInfo.damagesHelmet()` waar was (vallende aambeelden/stalactieten). Normale schade van zwaarden, speren, maces, pijlen en explosies tastte de armor durability nooit aan.
- **Oplossing:** Voor alle schadesoorten die armor niet omzeilen (`!typeInfo.bypassesArmor() && amount > 0`), wordt nu durability schade toegebracht aan de helm, borstplaat, broek en laarzen conform de vanilla formule $\max(1, \lfloor \text{amount} / 4 \rfloor)$.

### 4. Mace Smash Valschadereset bij Schildblokkade (`VanillaAttackFeature.java`)
- **Probleem:** Als een doelwit een mace smash hit blokkeerde met een schild, annuleerde `VanillaDamageFeature` het event. Daardoor retourneerde `target.damage()` de waarde `false`, waarna `VanillaAttackFeature` direct vroegtijdig afbrak zonder `fallFeature.resetFallDistance(attacker)` aan te roepen. De aanvaller stierf daardoor bij landing aan valschade. Tevens verloren aanvallers geen wapendurability of exhaustion bij een geblokkeerde slag.
- **Oplossing:** In het `!damageSucceeded` blok wordt `fallFeature.resetFallDistance(attacker)` nu altijd aangeroepen bij een mace smash, en worden wapendurability en speleruitputting (`addAttackExhaustion`) verwerkt conform vanilla.

### 5. Elytra Zweven Kritieke Slagen Beperking (`VanillaCriticalFeature.java`)
- **Probleem:** `shouldCrit` controleerde niet op `!attacker.isFlyingWithElytra()`, waardoor spelers tijdens het zweven met een elytra omlaag altijd critical hits konden uitdelen.
- **Oplossing:** `!attacker.isFlyingWithElytra()` toegevoegd aan de critical hit voorwaarden.

### 6. End Crystal Kettingreacties & Kill Attributie (`CrystalEntity.java` & `VanillaExplosionSupplier.java`)
- **Probleem:** `CrystalEntity.damage()` bevatte een check `!DamageTypeInfo.of(damage.getType()).explosive()`, waardoor explosies van andere end crystals geen kettingreactie veroorzaakten. Tevens werd bij de explosie niet doorgegeven welke speler het kristal had opgeblazen.
- **Oplossing:** De `!explosive()` check is verwijderd zodat kristallen elkaars explosie oppikken. Met behulp van `CompoundBinaryTag` wordt de veroorzakende speler (`causingEntity`) en brandtag (`fire`) meegegeven aan `instance.explode()`. Tevens geeft `VanillaExplosionSupplier` nu de exacte explosie-locatie mee als `sourcePosition` in het `Damage` object, zodat schilden explosies correct kunnen blokkeren als de speler ernaartoe kijkt.

### 7. Niet-blokkeerbare Schadesoorten (`DamageTypeInfo.java`)
- **Probleem:** `DamageType.LIGHTNING_BOLT`, `CAMPFIRE` en `OUTSIDE_BORDER` ontbraken als `unblockable(true)`.
- **Oplossing:** Geregistreerd in `INFO_MAP`:
  - `LIGHTNING_BOLT`: `fire(true).unblockable(true)`
  - `CAMPFIRE`: `bypassesArmor(true).fire(true).unblockable(true)`
  - `OUTSIDE_BORDER`: `bypassesArmor(true).unblockable(true)`

### 8. Wind Charge Knockback Weerstand & Velocity Sync (`WindCharge.java`)
- **Probleem:** Knockback hield geen rekening met de `Attribute.KNOCKBACK_RESISTANCE` van het slachtoffer, en verstuurde geen onmiddellijke netwerk-update naar de client (`sendImmediateVelocityUpdate()`).
- **Oplossing:** Horizontale snelheid wordt nu geschaald met `Math.max(0.0, 1.0 - kbRes)` en gesynchroniseerd met `CombatPlayer.sendImmediateVelocityUpdate()`.

---

## Stap 6: Uitgebreide Unit Test Suite
Alle mechanieken worden automatisch geverifieerd via JUnit 5 tests in [`src/test/java/io/github/togar2/pvp/test/`](src/test/java/io/github/togar2/pvp/test/):
- [`CategoryBFixesTest.java`](src/test/java/io/github/togar2/pvp/test/CategoryBFixesTest.java):
  - `testShieldPitchIndependence()`
  - `testShieldWarmupDelay()`
  - `testArmorDurabilityDegradation()`
  - `testMaceFallDamageResetOnShieldBlock()`
  - `testWindChargeKnockbackResistance()`
  - `testEndCrystalDaisyChainAndAttribution()`
- [`CriticalHitTest.java`](src/test/java/io/github/togar2/pvp/test/CriticalHitTest.java):
  - `testElytraPreventsCrit()`
- [`DamageTypeInfoTest.java`](src/test/java/io/github/togar2/pvp/test/DamageTypeInfoTest.java):
  - `testUnblockableTypes()`
- **Resultaat:** **45 / 45 tests succesvol geslaagd (`BUILD SUCCESSFUL`).**

---

## Stap 7: Elytra & Firework Rocket Boost
- **Elytra Functionaliteit:**
  - Vliegen met de Elytra (door in de lucht op spatie/jump te drukken) wordt door Minestom ondersteund via `START_FLYING_ELYTRA` en `setFlyingWithElytra(true)`.
  - Landen op de grond beëindigt het zweven automatisch via `refreshOnGround(true)` en roept `PlayerStopFlyingWithElytraEvent` aan.
  - Vliegen met Elytra voorkomt kritieke slagen (`VanillaCriticalFeature`).
- **Firework Rocket Boost ([`VanillaFireworkRocketFeature.java`](src/main/java/io/github/togar2/pvp/feature/projectile/VanillaFireworkRocketFeature.java) & [`FireworkRocketEntity.java`](src/main/java/io/github/togar2/pvp/entity/projectile/FireworkRocketEntity.java))**:
  - `PlayerUseItemEvent` luistert naar het afschieten van vuurwerkraketten tijdens het vliegen met een Elytra.
  - De speler ontvangt een opwaartse/voorwaartse stuwkracht in de kijkrichting (`boostSpeed`), gesynchroniseerd via `sendImmediateVelocityUpdate()`.
  - De vuurwerkraket hecht zich aan de vliegende speler (`meta.setShooter(player)`), produceert deeltjes en geluid (`ENTITY_FIREWORK_ROCKET_LAUNCH`), en versnelt de speler gedurende zijn levensduur.
- **Armor Selectie Menu ([`PvpServer.java`](src/test/java/io/github/togar2/pvp/test/PvpServer.java))**:
  - In het Armor Menu (Slot 15) is de optie **Elytra Kit** toegevoegd: trekt direct een Elytra aan op de borst (aangevuld met Netherite helm, broek en laarzen) en geeft de speler 64x Firework Rockets in de inventory.
- **Wapen Selectie Menu Fix ([`PvpServer.java`](src/test/java/io/github/togar2/pvp/test/PvpServer.java))**:
  - `mat.name()` retourneert in Minestom de lowercase sleutel (`"minecraft:netherite_spear"`). De controle gebruikte voorheen `contains("SPEAR")` (hoofdletters), waardoor klikken op speren niet geregistreerd werd. Dit is opgelost met `mat.name().toLowerCase().contains("spear")`.
- **Armor Equip Sound NPE Fix ([`ArmorMaterial.java`](src/main/java/io/github/togar2/pvp/enums/ArmorMaterial.java) & [`PvpServer.java`](src/test/java/io/github/togar2/pvp/test/PvpServer.java))**:
  - Null-check toegevoegd op `entity.getInstance()` in `ArmorMaterial.java` voordat het aantrekgeluid wordt afgespeeld.
  - In `PvpServer.java` wordt `setInstance` nu voltooid voordat armor wordt toebedeeld aan gespawnde dummy's en NPCs.

---

## Stap 8: Instant Pearl Catch (IPC - Wind Charge + Ender Pearl)
- **Probleem:** In vanilla 1.21 Tricky Trials botsen Ender Pearls en Wind Charges in de lucht op elkaar (bevestigd als bedoeld door Mojang via [MC-268407](https://bugs.mojang.com/browse/MC-268407)). In de praktijk vlogen wind charges echter nog steeds dwars door gegooide pearls heen ("de windcharge gaat nogsteeds door de pearl heen"). Dit kwam door twee cruciale oorzaken:
  1. **`hasEntityCollision()` was `false` in Minestom:** `CustomEntityProjectile` initialiseerde `collidesWithEntities = false`. In Minestom's interne physics loop (`EntityCollision.checkCollision`) stond letterlijk: `if (!e.hasEntityCollision()) continue;`. Hierdoor negeerde de physics engine alle gegooide projectielen compleet en werd er nooit een botsing gedetecteerd.
  2. **Discrete Ticks & Snelheidsverschil:** Een Ender Pearl ($35$ b/s) en Wind Charge ($50$ b/s) verplaatsen zich $1.5$ tot $2.5$ blok per tick en hebben kleine hitboxes ($0.25$ blok). Discrete swept checks tegen statische posities misten elkaar wanneer de wind charge de pearl in dezelfde tick inhaalde.
  3. **Volgorde van Explosiekracht:** In `WindCharge.explode()` werd de radiale opwaartse stuwkracht uitgevoerd vóórdat de speler geteleporteerd was. Hierdoor stond de speler nog op de grond en ontving geen opwaartse boost in de lucht.
- **Oplossing ([`WindCharge.java`](src/main/java/io/github/togar2/pvp/entity/projectile/WindCharge.java) & [`ThrownEnderpearl.java`](src/main/java/io/github/togar2/pvp/entity/projectile/ThrownEnderpearl.java))**:
  - `hasEntityCollision()` geactiveerd (`return true`) op zowel `WindCharge` als `ThrownEnderpearl`.
  - **Continue Lijnsegment-Interceptie (`checkPearlInterception` & `checkWindChargeInterception`)**: Tijdens elke tick wordt de gevlogen baan van de wind charge als 3D-lijnsegment vergeleken met de positie en het baan-segment van vliegende ender pearls. Wanneer ze elkaar binnen $1.5$ blok passeren, onderschept de wind charge de pearl direct in de lucht.
  - **Teleportatie Vóór Explosiekracht:** In `WindCharge.explode()` wordt de eigenaar van de pearl nu direct als allereerste stap naar het botsingspunt in de lucht geteleporteerd. Vervolgens vangt de speler direct de opwaartse `LAUNCH_UPWARD_POWER` wind burst, wordt valschade gereset, en wordt de speler omhoog gelanceerd voor de iconische 1.21 IPC combo.
- **Geverifieerd:** Twee gerichte unit tests in [`EnderpearlTest.java`](src/test/java/io/github/togar2/pvp/test/EnderpearlTest.java):
  - `testInstantPearlCatch`: directe botsing en mid-air teleportatie.
  - `testInFlightProjectileInterception`: actieve in-flight onderschepping van een vliegende pearl door een achteropkomende wind charge, inclusief teleportatie en opwaartse stuwkracht ($> 10$ b/s).
  - Alle **49 unit tests** slagen foutloos (`./gradlew test --rerun`).

---

## Stap 9: Spear Lunge Parity Fix (Voorwaartse Dash, Lucht-Jabs & Cooldown Synchronisatie)
- **Probleem:** Bij het gebruiken van een speer met de **Lunge** betovering (Lunge I - III) schoot de speler niet naar voren ("de lunge werkt niet, ik ga niet vooruit"). Dit had drie onderliggende oorzaken:
  1. **Geen Lucht-Jab Detectie:** Lunge werd voorheen alleen getriggerd in `performAttack(attacker, target)` wanneer een entiteit direct geraakt werd. In vanilla Minecraft (en 1.21 Combat Test) voert de speler een **Jab** aanval uit bij linkerklik, zelfs zonder een doelwit in het vizier (in de lucht springen en stoten om gaten te overbruggen of vijanden te naderen). Omdat Minestom bij klikken in de lucht geen `EntityAttackEvent` maar een `PlayerHandAnimationEvent` stuurt, gebeurde er helemaal niets.
  2. **Cooldown Reset Race-Condition door Packet Ordering:** Wanneer een Minecraft-client een entiteit aanvalt, stuurt hij eerst `ClientAnimationPacket` (handzwaai) en daarna `ClientAttackPacket`. `VanillaAttackCooldownFeature` reset de cooldown direct bij de handanimatie (`player.getAliveTicks()`). Toen `performAttack` controleerde of `cooldownProgress >= 0.999`, was de cooldown zojuist gereset naar tick 0 (voortgang $\approx 0.02$). Hierdoor werd elke speeraanval tegen entiteiten direct geannuleerd!
  3. **Vaste Grondwrijving door $Y=0$:** De stootrichting werd geforceerd op `withY(0)`. Wanneer een speler op de grond stond met $Y=0$, zorgde de grondwrijving (`0.546`) ervoor dat de speler na een fractie van een blok stilstond.
- **Oplossing:**
  - **`VanillaAttackCooldownFeature`:** `PREVIOUS_ATTACKED_TICKS` toegevoegd. Wanneer `resetCooldownProgress` in dezelfde tick wordt aangeroepen als een aanval, berekent `getAttackCooldownProgress` de werkelijke cooldown vóór de klik. Speeraanvallen worden niet langer per ongeluk geannuleerd.
  - **Lucht-Jabs & Rechterklik Lunge ([`VanillaAttackFeature.java`](src/main/java/io/github/togar2/pvp/feature/attack/VanillaAttackFeature.java))**:
    - `PlayerHandAnimationEvent` en `PlayerUseItemEvent` listeners toegevoegd die `handleSpearLungeAction` aanroepen.
    - Speer-jab en lunge worden nu correct uitgevoerd bij zowel aanvallen op doelwitten als stoten in de lucht!
    - `LAST_LUNGE_TICK` tag toegevoegd om dubbele lunges binnen dezelfde tick te voorkomen.
  - **Lunge Impuls & Physics Parity:**
    - Wanneer de speler op de grond staat, wordt een subtiele opwaartse impuls ($0.15$) meegegeven, waardoor de speler de grondwrijving doorbreekt en soepel ~1 tot ~3 blokken vooruit glijdt (afhankelijk van Lunge I-III).
    - In de lucht (springen of vallen) behoudt de lunge de volledige 3D-kijkhoek en lanceert de speler voorwaarts.
    - `fallFeature.resetFallDistance(attacker)` toegevoegd om valschade te voorkomen bij het lungeren.
    - Directe netwerksynchronisatie via `sendImmediateVelocityUpdate()` en geluidseffecten (`ITEM_SPEAR_LUNGE_1/2/3`).
    - Uitputting ($4.0 \times \text{level}$) en duurzaamheidsschade ($1$) worden correct toegepast.
- **Geverifieerd:** Twee nieuwe tests toegevoegd aan [`SpearKineticTest.java`](src/test/java/io/github/togar2/pvp/test/SpearKineticTest.java):
  - `testSpearLungeSwingInAir`: controleert voorwaartse impuls ($> 20$ b/s) bij stoten in de lucht.
  - `testSpearLungeOnEntityAttack`: controleert succesvolle aanval en voorwaartse lunge bij het raken van een entiteit.
  - Alle **48 unit tests** slagen foutloos (`./gradlew test --rerun`).






---

## Stap 7: Attribute swapping & vanilla parity audit
- **Attribute swapping:** vanilla past equipment-attributes (attack damage, attack speed, armor, ...) pas aan het einde van de tick toe en
  reset de attack-cooldown pas in `Player#tick` bij een ander itemtype. Een hotbar-wissel en aanval in dezelfde tick gebruikt dus de attributen
  van het oude item, en de speciale effecten (mace smash, enchants, bijl-schildbreker) van het nieuwe item.
  - `VanillaEquipmentFeature` synchroniseert nu per tick (`syncEquipment`); `CombatPlayerImpl` onderdrukt de directe toepassing van Minestom.
  - `VanillaAttackCooldownFeature` reset de cooldown per tick bij een ander itemtype en niet meer bij een gemiste zwaai.
  - Items met een expliciete `ATTRIBUTE_MODIFIERS` component vervangen de default-modifiers (ook een lege lijst) en worden correct toegepast.
- **Knockback resistance:** schaalt nu zowel horizontale als verticale knockback; bij 1.0 is er geen knockback. De aanvaller wordt ook vertraagd als het doelwit alles weerstaat.
- **Sweeping:** basisschade is de schade na cooldown + mace-bonus (zonder crit/enchants), enchant-schade wordt per doelwit toegevoegd en alles wordt met de cooldown vermenigvuldigd.
- **Mace smash:** AoE-knockback `(3.5 - d) * 0.7 * (valafstand > 5 ? 2 : 1) * (1 - resistance)`, vaste y-push 0.7, aanvaller y-velocity 0.01, lucht-geluid als het doelwit niet op de grond staat.
- **Aanvalsbereik:** afstand van oog tot bounding box van het doelwit, `interaction range + 1.0` (was voet-tot-oog `+ 3.0`).
- **Gecontroleerd tegen de gedecompileerde vanilla 26.2 server (`Player#attack`, `MaceItem`, `ExplodeEffect`, `ServerExplosion`, `LivingEntity#knockback`):**
  - Mace smash kan in vanilla wel een critical hit zijn; dat was onterecht uitgeschakeld.
  - Een mislukte aanval kost geen item-durability en geen uitputting; sprint-knockback-geluid wordt ook bij een mislukte hit afgespeeld; smash-geluiden komen bovenop de normale aanvalsgeluiden.
  - Sweeping: snelheidsdrempel is `horizontale beweging < movement speed * 2.5` (was te streng, waardoor lopen nooit sweepte) en knockback alleen voor entiteiten die echt schade kregen.
  - Mace AoE slaat spelers in creative-flight over.
  - **Wind Burst** is nu de vanilla explosie (straal 3.5 om de aanvaller, multiplier 1.2/1.75/2.2, alleen bij valafstand >= 1.5 en niet vliegend) in plaats van een vaste opwaartse snelheid.
  - **Wind charge** gebruikt nu straal 1.2 en multiplier 1.22 met de vanilla formule `(1 - afstand / 2r) * exposure * multiplier`, en explosies worden alleen tegengehouden door `EXPLOSION_KNOCKBACK_RESISTANCE` (niet `KNOCKBACK_RESISTANCE`).
  - `VanillaExplosionSupplier.getExposure` gaf altijd 0 in open lucht door een verkeerd geplaatste shape; dat beinvloedde ook TNT en end crystals.
- **Speer opnieuw opgebouwd naar vanilla 26.2** (`VanillaSpearAttacks`, gecontroleerd tegen `PiercingWeapon`, `KineticWeapon`, `Player#stabAttack`, `ProjectileUtil`):
  - **Jab (linkermuisknop)** via `PlayerStabEvent`: raakt alle entiteiten op de kijklijn binnen `ATTACK_RANGE` (min 2.0, max 4.5, margin 0.125, +beweging langs de kijkrichting), geblokt door blokken, met `MINIMUM_ATTACK_CHARGE`. Schade is de `ATTACK_DAMAGE` attribuutwaarde, geschaald met de cooldown; daarna reset de cooldown en volgt Lunge.
  - **Charge (rechtermuisknop vasthouden)** per tick: voorwaarden per `KineticWeapon` (max-duur, min snelheid, min relatieve snelheid), contact-cooldown van 10 ticks per entiteit, basisschade is de *basis*waarde van `ATTACK_DAMAGE` + `floor(relatieve snelheid * multiplier)`, ontzadelen en knockback uit de condities.
  - Een gewone aanvalsklik met een speer wordt genegeerd, zoals in vanilla.
  - Stab-knockback: eerst 0.4, daarna de enchantment/attribuut-knockback; de aanvaller wordt vertraagd. Item-durability 1 per geraakt levend doelwit, uitputting 0.1.
  - **Lunge** alleen na een jab: impuls `look * (1,0,1) * 0.458 * level` (geen verticale component en geen reset van valschade), alleen zonder voertuig/elytra/water en met honger >= 7 (of creative); kost 1 durability en `4 * level` uitputting.
  - `KnownMovement` houdt per tick bij hoeveel een entiteit bewoog (vanilla `getKnownMovement`); sweeping gebruikt dat nu ook.
- **Resterende afwijkingen opgelost:**
  - `ATTACK_KNOCKBACK` is nu een float (knockback-API en `AttackValues` gebruiken `double`), zoals vanilla `getKnockback`.
  - Wind charges worden teruggekaatst door ze te slaan (`WindCharge#deflect`): kijkrichting als snelheid, de slaande entiteit wordt eigenaar, niet in de eerste 5 ticks.
  - `TeamUtil` (vanilla `isAlliedTo` / `canHarmPlayer`): teamgenoten kunnen elkaar niet raken tenzij friendly fire aanstaat (`VanillaDamageFeature`, speerhits), en worden overgeslagen door sweeping en de mace-AoE.
- **Nog bewust anders:** de kinetic-tick draait op `PlayerTickEvent` in plaats van op het exacte punt in vanilla's `LivingEntity#tick`; projectielen anders dan de wind charge (bijv. vuurballen) worden niet teruggekaatst omdat de lib die entiteiten niet zelf beheert.
