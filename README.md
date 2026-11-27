# MinestomMacePvP (26.2)

Een Minecraft PvP en Combat bibliotheek voor [Minestom](https://github.com/Minestom/Minestom), een fork van [TogAr2/MinestomPvP](https://github.com/TogAr2/MinestomPvP). Gebouwd voor Minestom `2026.10.07-26.2` met ondersteuning voor de combat van **Minecraft 26.2** (Mace, Speren, Wind Charges, Vuurballen). De moderne combat is gecontroleerd tegen de gedecompileerde vanilla 26.2 server.

Bekijk het volledige, gedetailleerde overzicht van alle stappen en fixes in het [CHANGELOG.md](CHANGELOG.md). De Engelstalige documentatie (gebruik, customization, events, eigen features) staat in [.github/README.md](.github/README.md).

---

## Installatie (JitPack)

De bibliotheek is beschikbaar via [JitPack](https://jitpack.io/#Ja90n/MinestomMacePvP). Gebruik `master-SNAPSHOT` voor de nieuwste versie, een release tag of een commit hash. Minestom zelf zit er niet in, die voeg je zelf toe.

**Gradle (Kotlin)**
```kts
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("net.minestom:minestom:2026.10.07-26.2")
    implementation("com.github.Ja90n:MinestomMacePvP:master-SNAPSHOT")
}
```

**Gradle (Groovy)**
```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'net.minestom:minestom:2026.10.07-26.2'
    implementation 'com.github.Ja90n:MinestomMacePvP:master-SNAPSHOT'
}
```

**Maven**
```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>net.minestom</groupId>
    <artifactId>minestom</artifactId>
    <version>2026.10.07-26.2</version>
  </dependency>
  <dependency>
    <groupId>com.github.Ja90n</groupId>
    <artifactId>MinestomMacePvP</artifactId>
    <version>master-SNAPSHOT</version>
  </dependency>
</dependencies>
```

De packages heten nog steeds `io.github.togar2.pvp`, dus bestaande code die MinestomPvP gebruikt blijft werken na het wisselen van de dependency.

### Basisgebruik
```java
MinestomPvP.init();
MinecraftServer.getGlobalEventHandler().addChild(CombatFeatures.modernVanilla().createNode());
```

---

## Belangrijkste Eigenschappen & Features

- **26.2 Wapens & Gevechtsfunctionaliteiten**:
  - **Mace (Strijdhamer)**: Smash attacks met val-schade schaling (4/2/1 per blok), density, breach, AoE shockwave knockback en de *Wind Burst* betovering (een echte explosie rond de aanvaller). Een smash kan ook een critical hit zijn.
  - **Speer (Spears)**: Jab met linkermuisknop (raakt alle entiteiten in het bereik van 2.0 - 4.5 blokken), charge met rechtermuisknop vasthouden (schade, knockback en ontzadelen afhankelijk van snelheid en duur) en Lunge (alleen na een jab, alleen horizontale impuls).
  - **Wind Charges**: Wind charge jumps, vanilla explosie-knockback (`(1 - afstand / 2r) * exposure * multiplier`, straal 1.2, multiplier 1.22) en terugkaatsen door ze te slaan.
  - **Vuurballen**: `LargeFireball` (6 schade en explosie) en `SmallFireball` (5 schade, brand en vuur plaatsen) met vanilla acceleratie en inertia, die ook terugkaatsen. Starten met `launch(instance, positie, richting)`.
  - **Ender Pearls & Instant Pearl Catch (IPC)**: Precieze vanilla trajectory, impact teleportatie, en mid-air pearl-catching met Wind Charges.
  - **Elytra & Firework Boost**: Elytra vlucht, geen crits tijdens het zweven, en vuurwerkraket-stuwkracht.
- **Vanilla Parity**:
  - **Attribute swapping**: Equipment-attributen (aanvalsschade, aanvalssnelheid, armor) worden aan het eind van de tick toegepast en de cooldown reset per tick bij een ander itemtype, zoals in vanilla. Een slot-wissel en aanval in dezelfde tick gebruikt dus de attributen van het oude item en de effecten van het nieuwe. Items met een eigen `ATTRIBUTE_MODIFIERS` component werken ook.
  - **Knockback weerstand**: `KNOCKBACK_RESISTANCE` schaalt horizontale én verticale knockback; explosies gebruiken `EXPLOSION_KNOCKBACK_RESISTANCE`.
  - **Sweeping**: Vanilla basisschade, cooldown en snelheidsdrempel; knockback alleen voor geraakte entiteiten.
  - **Schild Blokkade**: Pitch-onafhankelijk blokkeren en de 5-tick warm-up delay.
  - **Armor Durability**: Slijtage van alle armor-onderdelen conform vanilla.
  - **Mislukte aanvallen** kosten geen item-durability en geen uitputting.
  - **End Crystals & TNT**: Kettingreacties met kill-attributie via `causingEntity` en correcte explosie-exposure.
  - **Teams**: Teamgenoten kunnen elkaar niet raken zonder friendly fire; bondgenoten worden overgeslagen door sweeping en de mace-AoE.
  - **Onblokkeerbare schades**: `LIGHTNING_BOLT`, `CAMPFIRE` en `OUTSIDE_BORDER` kunnen niet geblokkeerd worden door schilden.
- **Interactieve Testserver & Arena**:
  - Kant-en-klare testomgeving met kitselectie-menu's (wapens & armors).
  - Dummy NPC en PvP arena om alle combat mechanieken realtime te testen.
  - Rijdier-ondersteuning (getemde paarden met correcte shift-dismounting) en dynamische belichting via `LightingChunk`.

### Bekende afwijkingen van vanilla
- De kinetic-tick van de speer draait op `PlayerTickEvent` in plaats van het exacte punt in vanilla's tick.
- Vuurballen raken elkaar niet onderling en de bibliotheek spawnt ze niet zelf (er zijn geen ghasts, blazes of dispensers).

---

## Snel Starten

### Testserver Starten
Je kunt de interactieve testserver direct starten met:
```bash
./gradlew runServer
```
Verbind vervolgens met je Minecraft client (26.2) naar `localhost:25565`.

### Tests Uitvoeren
Voer de volledige unit test suite uit:
```bash
./gradlew test --rerun
```

---

## Documentatie & Wijzigingen

Voor een overzicht van elke afzonderlijke wijziging, zie:
- [CHANGELOG.md](CHANGELOG.md)
