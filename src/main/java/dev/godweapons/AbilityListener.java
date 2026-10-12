package dev.godweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AbilityListener implements Listener {

    private final GodWeaponsPlugin plugin;
    private final Map<UUID, Map<WeaponType, Long>> cooldowns = new HashMap<>();
    /** Players currently mid-Cataclysm-Slam (immune to the fall damage). */
    private final Set<UUID> slamming = new HashSet<>();

    public AbilityListener(GodWeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ activation

    /** F key (swap hand) triggers the ability of the weapon in the main hand. */
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        // The item currently in the main hand is the one being moved to the off hand
        WeaponType type = plugin.items().getType(event.getOffHandItem());
        if (type == null) return;

        event.setCancelled(true);

        if (p.getGameMode() == GameMode.SPECTATOR) return;
        long now = System.currentTimeMillis();
        long readyAt = cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
                .getOrDefault(type, 0L);
        if (now < readyAt) {
            double left = (readyAt - now) / 1000.0;
            p.sendActionBar(Component.text(String.format("%s on cooldown: %.1fs", type.displayName(), left),
                    NamedTextColor.RED));
            return;
        }

        double cd = plugin.getConfig().getDouble(type.id() + ".ability.cooldown", 10);
        cooldowns.get(p.getUniqueId()).put(type, now + (long) (cd * 1000));

        switch (type) {
            case SWORD -> judgmentDash(p);
            case MACE -> cataclysmSlam(p);
            case SPEAR -> thunderPierce(p);
        }
    }

    // --------------------------------------------------------------- sword

    private void judgmentDash(Player p) {
        double range = plugin.getConfig().getDouble("sword.ability.range", 8);
        double damage = plugin.getConfig().getDouble("sword.ability.damage", 12);

        World world = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Set<UUID> hit = new HashSet<>();

        for (int i = 1; i <= (int) range; i++) {
            Location pt = eye.clone().add(dir.clone().multiply(i));
            world.spawnParticle(Particle.ELECTRIC_SPARK, pt, 8, 0.25, 0.25, 0.25, 0.05);
            world.spawnParticle(Particle.SWEEP_ATTACK, pt, 1);
            for (Entity e : world.getNearbyEntities(pt, 1.5, 1.5, 1.5)) {
                if (isValidTarget(p, e) && hit.add(e.getUniqueId())) {
                    LivingEntity le = (LivingEntity) e;
                    le.damage(damage, p);
                    world.strikeLightningEffect(le.getLocation());
                }
            }
        }

        Vector vel = dir.clone().multiply(1.8);
        vel.setY(Math.max(vel.getY(), 0.25));
        p.setVelocity(vel);
        p.setFallDistance(0f);
        world.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
        world.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.6f);
        p.sendActionBar(Component.text("Judgment Dash!", NamedTextColor.RED));
    }

    /** Sword passive: heal a percentage of the damage dealt. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwordHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) return;
        if (plugin.items().getType(p.getInventory().getItemInMainHand()) != WeaponType.SWORD) return;

        double pct = plugin.getConfig().getDouble("sword.lifesteal-percent", 20) / 100.0;
        double heal = event.getFinalDamage() * pct;
        if (heal <= 0) return;

        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max != null ? max.getValue() : 20.0;
        p.setHealth(Math.min(maxHealth, p.getHealth() + heal));
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.8, 0), 2, 0.3, 0.2, 0.3);
    }

    // ---------------------------------------------------------------- mace

    private void cataclysmSlam(Player p) {
        double launch = plugin.getConfig().getDouble("mace.ability.launch-power", 1.7);

        slamming.add(p.getUniqueId());
        Vector v = p.getVelocity();
        v.setY(launch);
        p.setVelocity(v);
        p.setFallDistance(0f);

        World world = p.getWorld();
        world.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
        world.spawnParticle(Particle.CLOUD, p.getLocation(), 25, 0.4, 0.1, 0.4, 0.1);
        p.sendActionBar(Component.text("Cataclysm Slam!", NamedTextColor.GOLD));

        final double startY = p.getLocation().getY();
        new BukkitRunnable() {
            int ticks = 0;
            double peakY = startY;

            @Override
            public void run() {
                ticks++;
                if (!p.isOnline() || p.isDead()) {
                    slamming.remove(p.getUniqueId());
                    cancel();
                    return;
                }
                peakY = Math.max(peakY, p.getLocation().getY());

                // Help the player come down hard once they've peaked
                if (ticks > 8 && p.getVelocity().getY() < 0 && !p.isOnGround()) {
                    Vector vel = p.getVelocity();
                    if (vel.getY() > -1.5) {
                        vel.setY(vel.getY() - 0.25);
                        p.setVelocity(vel);
                    }
                }

                boolean landed = ticks >= 5 && p.isOnGround();
                if (landed || ticks > 120) {
                    slamming.remove(p.getUniqueId());
                    cancel();
                    if (landed) shockwave(p, Math.max(0, peakY - p.getLocation().getY()));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void shockwave(Player p, double fallen) {
        double radius = plugin.getConfig().getDouble("mace.ability.radius", 6);
        double base = plugin.getConfig().getDouble("mace.ability.damage", 8);
        double perBlock = plugin.getConfig().getDouble("mace.ability.bonus-damage-per-block", 0.5);
        double maxBonus = plugin.getConfig().getDouble("mace.ability.max-bonus-damage", 14);
        double kb = plugin.getConfig().getDouble("mace.ability.knockback", 1.4);

        double damage = base + Math.min(maxBonus, fallen * perBlock);
        World world = p.getWorld();
        Location center = p.getLocation();

        world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5f, 0.8f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
        world.spawnParticle(Particle.EXPLOSION, center, 3, 0.8, 0.1, 0.8, 0);
        for (int i = 0; i < 32; i++) {
            double a = (Math.PI * 2 / 32) * i;
            for (double r = 1; r <= radius; r += 1.5) {
                Location ring = center.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r);
                world.spawnParticle(Particle.CLOUD, ring, 1, 0.05, 0.05, 0.05, 0.02);
            }
        }

        for (Entity e : world.getNearbyEntities(center, radius, radius / 2, radius)) {
            if (!isValidTarget(p, e)) continue;
            LivingEntity le = (LivingEntity) e;
            le.damage(damage, p);
            Vector push = le.getLocation().toVector().subtract(center.toVector());
            push.setY(0);
            if (push.lengthSquared() < 0.0001) push = new Vector(0, 0, 0);
            else push.normalize();
            push.multiply(kb).setY(0.6);
            le.setVelocity(push);
        }
        p.sendActionBar(Component.text(String.format("Slam! %.0f damage", damage), NamedTextColor.GOLD));
    }

    /** Mace passive: no fall damage while holding it (and during the slam itself). */
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player p)) return;
        boolean holdingMace = plugin.items().getType(p.getInventory().getItemInMainHand()) == WeaponType.MACE;
        if (holdingMace || slamming.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // --------------------------------------------------------------- spear

    private void thunderPierce(Player p) {
        double range = plugin.getConfig().getDouble("spear.ability.range", 25);
        double damage = plugin.getConfig().getDouble("spear.ability.damage", 10);
        int glow = (int) (plugin.getConfig().getDouble("spear.ability.glowing-seconds", 6) * 20);
        int slow = (int) (plugin.getConfig().getDouble("spear.ability.slowness-seconds", 3) * 20);

        World world = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Set<UUID> hit = new HashSet<>();
        Location last = eye;

        for (double d = 1; d <= range; d += 0.75) {
            Location pt = eye.clone().add(dir.clone().multiply(d));
            // Stop the bolt at solid walls
            if (pt.getBlock().getType().isSolid()) break;
            last = pt;
            world.spawnParticle(Particle.END_ROD, pt, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.ELECTRIC_SPARK, pt, 2, 0.1, 0.1, 0.1, 0.02);
            for (Entity e : world.getNearbyEntities(pt, 1.2, 1.2, 1.2)) {
                if (isValidTarget(p, e) && hit.add(e.getUniqueId())) {
                    LivingEntity le = (LivingEntity) e;
                    le.damage(damage, p);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glow, 0, false, false, true));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slow, 1, false, true, true));
                    world.strikeLightningEffect(le.getLocation());
                }
            }
        }

        world.strikeLightningEffect(last);
        world.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.3f, 1.0f);
        p.sendActionBar(Component.text("Thunder Pierce!", NamedTextColor.AQUA));
    }

    // ------------------------------------------------------------- helpers

    private boolean isValidTarget(Player user, Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        if (e.getUniqueId().equals(user.getUniqueId())) return false;
        if (le.isDead()) return false;
        if (e instanceof org.bukkit.entity.ArmorStand) return false;
        if (e instanceof Player target) {
            GameMode gm = target.getGameMode();
            return gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE;
        }
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        slamming.remove(event.getPlayer().getUniqueId());
        cooldowns.remove(event.getPlayer().getUniqueId());
    }
}
