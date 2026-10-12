package dev.godweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class WeaponItems {

    private final GodWeaponsPlugin plugin;
    private final NamespacedKey typeKey;

    public WeaponItems(GodWeaponsPlugin plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "god_weapon");
    }

    /** Returns which god weapon this stack is, or null if it isn't one. */
    public WeaponType getType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return WeaponType.fromId(id);
    }

    public ItemStack create(WeaponType type) {
        FileConfiguration cfg = plugin.getConfig();
        Material material;
        switch (type) {
            case SWORD -> material = Material.NETHERITE_SWORD;
            case MACE -> material = Material.MACE;
            default -> {
                Material spear = Material.matchMaterial("NETHERITE_SPEAR");
                material = spear != null ? spear : Material.TRIDENT; // fallback for servers without spears
            }
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(type.displayName(), type.color(), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.setUnbreakable(true);
        meta.lore(lore(type));
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);

        // Enchants (looked up by key so a missing enchant on some version just gets skipped)
        enchant(item, "unbreaking", cfg.getInt("common.unbreaking", 5));
        if (cfg.getBoolean("common.mending", true)) enchant(item, "mending", 1);

        switch (type) {
            case SWORD -> {
                enchant(item, "sharpness", cfg.getInt("sword.sharpness", 10));
                enchant(item, "looting", 5);
                enchant(item, "sweeping_edge", 5);
                enchant(item, "fire_aspect", 3);
            }
            case MACE -> {
                enchant(item, "density", cfg.getInt("mace.density", 10));
                enchant(item, "breach", cfg.getInt("mace.breach", 5));
                enchant(item, "wind_burst", cfg.getInt("mace.wind-burst", 3));
                enchant(item, "fire_aspect", 2);
            }
            case SPEAR -> {
                enchant(item, "sharpness", cfg.getInt("spear.sharpness", 8));
                enchant(item, "lunge", cfg.getInt("spear.lunge", 3));
                enchant(item, "looting", 5);
                enchant(item, "fire_aspect", 2);
            }
        }
        return item;
    }

    private void enchant(ItemStack item, String key, int level) {
        if (level <= 0) return;
        Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        if (e != null) item.addUnsafeEnchantment(e, level);
    }

    private List<Component> lore(WeaponType type) {
        List<Component> lines = new ArrayList<>();
        lines.add(line("Forged for the ranked arena", NamedTextColor.DARK_GRAY));
        lines.add(Component.empty());
        switch (type) {
            case SWORD -> {
                lines.add(line("Passive: Lifesteal", NamedTextColor.RED));
                lines.add(line("  Heal from the damage you deal.", NamedTextColor.GRAY));
                lines.add(line("Ability [F]: Judgment Dash", NamedTextColor.YELLOW));
                lines.add(line("  Dash forward, striking everything in your path.", NamedTextColor.GRAY));
            }
            case MACE -> {
                lines.add(line("Passive: Featherfall", NamedTextColor.GOLD));
                lines.add(line("  Take no fall damage while holding it.", NamedTextColor.GRAY));
                lines.add(line("Ability [F]: Cataclysm Slam", NamedTextColor.YELLOW));
                lines.add(line("  Launch skyward, then crash down with a shockwave.", NamedTextColor.GRAY));
            }
            case SPEAR -> {
                lines.add(line("Passive: Long Reach + Swiftness", NamedTextColor.AQUA));
                lines.add(line("  Extra reach and Speed I while holding it.", NamedTextColor.GRAY));
                lines.add(line("Ability [F]: Thunder Pierce", NamedTextColor.YELLOW));
                lines.add(line("  Hurl a bolt down a long line, marking everything it hits.", NamedTextColor.GRAY));
            }
        }
        lines.add(Component.empty());
        lines.add(Component.text("Unbreakable", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        return lines;
    }

    private Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
