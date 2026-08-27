plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // Vault's economy API and ItemsAdder's API are only published to jitpack.
    maven("https://jitpack.io")
    // Citizens (NPCs)
    maven("https://maven.citizensnpcs.co/repo")
    // FancyNpcs (NPCs)
    maven("https://repo.fancyinnovations.com/releases")
    // MythicMobs (custom mobs)
    maven("https://mvn.lumine.io/repository/maven-public/")
    // Oraxen (custom items)
    maven("https://repo.oraxen.com/releases")
    // PlaceholderAPI
    maven("https://repo.extendedclip.com/releases/")
    // ExcellentShop (NPC shop — SPEND_MONEY objective)
    maven("https://repo.nightexpressdev.com/releases")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // Player quest progress storage: SQLite by default, MySQL/MariaDB for a network of servers sharing
    // progress, both behind the same PlayerDataRepository interface.
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.3")

    // Metrics — see MetricsSetup's javadoc about the placeholder plugin id.
    implementation("org.bstats:bstats-bukkit:3.1.0")

    // Soft-depend: exposes %purrtechquest_...% placeholders when PlaceholderAPI is present.
    compileOnly("me.clip:placeholderapi:2.12.3")

    // Soft-depend: money rewards only work when Vault + an economy plugin are present, checked at runtime.
    // Excludes VaultAPI's own transitive (ancient) Bukkit dependency, which otherwise conflicts with paper-api.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    // Soft-depends: NPC quest givers (Citizens and/or FancyNpcs) and RPG-server custom item/mob objectives
    // and rewards (ItemsAdder/Oraxen custom items, MythicMobs custom mobs). All optional at runtime — see
    // the npc/ package and tracking/ItemMatcher, tracking/EntityMatcher for how presence is detected.
    compileOnly("net.citizensnpcs:citizens-main:2.0.43-SNAPSHOT") {
        exclude(group = "*", module = "*")
    }
    compileOnly("de.oliver:FancyNpcs:2.9.2")
    compileOnly("io.lumine:Mythic-Dist:5.13.0")
    compileOnly("com.github.LoneDev6:api-itemsadder:3.6.1")
    compileOnly("io.th0rgal:oraxen:1.218.0")
    // Soft-depend: SPEND_MONEY objective progress, reported via ExcellentShop's TransactionCompletedEvent.
    compileOnly("su.nightexpress.excellentshop:api:5.1.3")

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // MockBukkit-v1.21:3.133.2 fails to even boot its mock server against Paper 1.21.11 (internal tag-data
    // bug: "Invalid namespace key minecraft:chain"), so QuestService is tested by mocking the thin Bukkit
    // surface it actually touches (Player, JavaPlugin) instead of a full mock server.
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.18.0")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    test {
        useJUnitPlatform()
    }

    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21.11")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        // Relocate shaded libraries so they never collide with other plugins bundling the same ones.
        relocate("com.zaxxer.hikari", "eu.purrtech.purrtechQuest.libs.hikari")
        relocate("org.mariadb.jdbc", "eu.purrtech.purrtechQuest.libs.mariadb")
        relocate("org.bstats", "eu.purrtech.purrtechQuest.libs.bstats")
        // org.sqlite is deliberately NOT relocated: sqlite-jdbc ships a JNI native library whose compiled
        // .so/.dylib hardcodes "org/sqlite/core/NativeDB" as a native symbol name. Relocating the Java
        // package breaks that binding (NoClassDefFoundError at native-library load time) with no code-side
        // fix possible — confirmed by actually running the shaded jar, not just reasoning about it. This is
        // a known limitation of shading sqlite-jdbc; leaving it unrelocated is the standard workaround.
    }

    build {
        dependsOn(shadowJar)
    }
}
