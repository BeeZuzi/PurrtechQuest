package eu.purrtech.purrtechQuest.integration;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Resolves Vault's {@link Economy} service, if Vault and a compatible economy plugin are both installed.
 * Returns {@code null} otherwise — callers must handle that, money rewards are never mandatory.
 */
public final class VaultEconomyHook {

    private VaultEconomyHook() {
    }

    public static Economy resolve() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }
}
