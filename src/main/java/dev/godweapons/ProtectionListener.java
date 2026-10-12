package dev.godweapons;

import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Keeps the god weapons from being destroyed or stripped. */
public class ProtectionListener implements Listener {

    private final GodWeaponsPlugin plugin;

    public ProtectionListener(GodWeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Dropped god weapons never despawn and can't burn or blow up. */
    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("protect-dropped-items", true)) return;
        Item entity = event.getEntity();
        if (plugin.items().getType(entity.getItemStack()) == null) return;
        entity.setInvulnerable(true);
        entity.setUnlimitedLifetime(true);
        entity.setGlowing(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent event) {
        if (!plugin.getConfig().getBoolean("protect-dropped-items", true)) return;
        if (event.getEntity() instanceof Item item && plugin.items().getType(item.getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    /** Anvils could rename or merge it; grindstones would strip the enchants. */
    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        if (containsGod(event.getInventory())) event.setResult(null);
    }

    @EventHandler
    public void onGrindstone(PrepareGrindstoneEvent event) {
        if (containsGod(event.getInventory())) event.setResult(null);
    }

    private boolean containsGod(Inventory inv) {
        if (!plugin.getConfig().getBoolean("block-anvil-and-grindstone", true)) return false;
        for (ItemStack stack : inv.getContents()) {
            if (plugin.items().getType(stack) != null) return true;
        }
        return false;
    }
}
