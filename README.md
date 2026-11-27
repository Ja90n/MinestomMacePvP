# MinestomPvP (1.21 & Mace Update)

Een volledige Minecraft PvP en Combat implementatie voor [Minestom](https://github.com/Minestom/Minestom), geüpdatet naar de nieuwste Minestom versie (`2026.10.07-26.2`) met volledige ondersteuning voor **Minecraft 1.21 features** (zoals de Mace, Speren, Wind Charges) en 100% vanilla combat parity.

Bekijk het volledige, gedetailleerde overzicht van alle stappen en fixes in het [CHANGELOG.md](CHANGELOG.md).

---

## Belangrijkste Eigenschappen & Features

- **1.21 Wapen & Gevechtsfunctionaliteiten**:
  - **Mace (Strijdhamer)**: Smash attacks met val-schade schaling, AoE shockwave knockback en de *Wind Burst* betovering.
  - **Speer (Spears)**: Verhoogd bereik (reach), lunge betoveringen (I-III) voor voorwaartse dashes bij stoten en aanvallen, en het afwerpen van ruiters (dismounting).
  - **Wind Charges**: Paraboolvormige projectielen, wind charge jumps met valschadereset, AoE blast knockback en particle/geluidseffecten.
  - **Ender Pearls & Instant Pearl Catch (IPC)**: Precieze vanilla trajectory, impact teleportatie, en mid-air pearl-catching met Wind Charges (Tricky Trials mechaniek).
  - **Elytra & Firework Boost**: Volledig werkende Elytra vlucht, uitschakeling van crits tijdens het zweven, en vuurwerkraket-stuwkracht (firework boosting).
- **Vanilla Parity (Categorie B Fixes)**:
  - **Schild Blokkade**: Pitch-onafhankelijk blokkeren (kijken naar de voeten of de lucht omzeilt het schild niet meer) en de 5-tick warm-up delay.
  - **Armor Durability**: Slijtage van helm, borststuk, broek en laarzen bij normale klappen, explosies en pijlen conform vanilla $\max(1, \lfloor \text{damage} / 4 \rfloor)$.
  - **Mace Smash Parity**: Valschadereset blijft behouden wanneer de aanval wordt opgevangen door een schild; wapenslijtage en uitputting worden correct verwerkt.
  - **Critical Hits**: Kritieke slagen worden conform vanilla uitgeschakeld tijdens het vliegen met een Elytra.
  - **End Crystals**: Kettingreacties (daisy-chaining) werken doordat kristallen ontploffen bij explosieve schade, inclusief kill-attributie via `causingEntity`.
  - **Onblokkeerbare schades**: `LIGHTNING_BOLT`, `CAMPFIRE` en `OUTSIDE_BORDER` kunnen niet geblokkeerd worden door schilden.
  - **Knockback Weerstand**: Wind charge ontploffingen houden rekening met `Attribute.KNOCKBACK_RESISTANCE`.
- **Interactieve Testserver & Arena**:
  - Kant-en-klare testomgeving met kitselectie-menu's (wapens & armors).
  - Dummy NPC en PvP arena om alle combat mechanieken realtime te testen.
  - Rijdier-ondersteuning (getemde paarden met correcte shift-dismounting) en dynamische belichting via `LightingChunk`.

---

## Snel Starten

### Testserver Starten
Je kunt de interactieve testserver direct starten met:
```bash
./gradlew runServer
```
Verbind vervolgens met je Minecraft client (1.21.x) naar `localhost:25565`.

### Tests Uitvoeren
Voer de volledige unit test suite uit (49 tests):
```bash
./gradlew test --rerun
```

---

## Documentatie & Wijzigingen

Voor een overzicht van elke afzonderlijke wijziging, zie:
- [CHANGELOG.md](CHANGELOG.md)
