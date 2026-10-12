package dev.godweapons;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public enum WeaponType {
    SWORD("sword", "God Sword", NamedTextColor.RED),
    MACE("mace", "God Mace", NamedTextColor.GOLD),
    SPEAR("spear", "God Spear", NamedTextColor.AQUA);

    private final String id;
    private final String displayName;
    private final TextColor color;

    WeaponType(String id, String displayName, TextColor color) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public TextColor color() {
        return color;
    }

    public static WeaponType fromId(String id) {
        if (id == null) return null;
        for (WeaponType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }
}
