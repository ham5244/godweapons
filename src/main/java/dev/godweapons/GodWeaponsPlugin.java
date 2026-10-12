package dev.godweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GodWeaponsPlugin extends JavaPlugin {

    private WeaponItems items;
    private NamespacedKey reachKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.items = new WeaponItems(this);
        this.reachKey = new NamespacedKey(this, "god_spear_reach");

        Bukkit.getPluginManager().registerEvents(new AbilityListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);

        // Passive effects (reach + speed) are refreshed a few times a second
        Bukkit.getScheduler().runTaskTimer(this, this::tickPassives, 20L, 5L);
        getLogger().info("God Weapons enabled.");
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) removeReach(p);
    }

    public WeaponItems items() {
        return items;
    }

    // ---------------------------------------------------------------- passives

    private void tickPassives() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            WeaponType held = items.getType(p.getInventory().getItemInMainHand());
            if (held == WeaponType.SPEAR) {
                applyReach(p);
                if (getConfig().getBoolean("spear.speed-passive", true)) {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SPEED, 30, 0, true, false, false));
                }
            } else {
                removeReach(p);
            }
        }
    }

    private void applyReach(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attr == null || attr.getModifier(reachKey) != null) return;
        double bonus = getConfig().getDouble("spear.reach-bonus", 1.5);
        attr.addTransientModifier(new AttributeModifier(
                reachKey, bonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    }

    private void removeReach(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attr == null) return;
        AttributeModifier mod = attr.getModifier(reachKey);
        if (mod != null) attr.removeModifier(mod);
    }

    // ----------------------------------------------------------------- command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + label + " give <sword|mace|spear|all> [player] | reload",
                    NamedTextColor.YELLOW));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(Component.text("God Weapons config reloaded.", NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("give") && args.length >= 2) {
            Player target;
            if (args.length >= 3) {
                target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found: " + args[2], NamedTextColor.RED));
                    return true;
                }
            } else if (sender instanceof Player self) {
                target = self;
            } else {
                sender.sendMessage(Component.text("Specify a player.", NamedTextColor.RED));
                return true;
            }

            String which = args[1].toLowerCase(Locale.ROOT);
            List<WeaponType> toGive = new ArrayList<>();
            if (which.equals("all")) {
                toGive.addAll(List.of(WeaponType.values()));
            } else {
                WeaponType t = WeaponType.fromId(which);
                if (t == null) {
                    sender.sendMessage(Component.text("Unknown weapon. Use sword, mace, spear or all.",
                            NamedTextColor.RED));
                    return true;
                }
                toGive.add(t);
            }

            for (WeaponType t : toGive) {
                ItemStack stack = items.create(t);
                // Drop at their feet if inventory is full
                target.getInventory().addItem(stack).values()
                        .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
                sender.sendMessage(Component.text("Gave " + t.displayName() + " to " + target.getName() + ".",
                        NamedTextColor.GREEN));
            }
            return true;
        }

        sender.sendMessage(Component.text("Usage: /" + label + " give <sword|mace|spear|all> [player] | reload",
                NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("give", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            out.addAll(List.of("sword", "mace", "spear", "all"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
