package me.ryanhamshire.GriefPrevention;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ClaimGui implements InventoryHolder {

    public enum GuiType {
        MAIN, MEMBERS, MANAGE_USER, CONFIRM_REMOVE, CONFIRM_PROMOTE
    }

    private final Claim claim;
    private final GuiType type;
    private final UUID targetUser;

    public ClaimGui(Claim claim, GuiType type) {
        this(claim, type, null);
    }

    public ClaimGui(Claim claim, GuiType type, UUID targetUser) {
        this.claim = claim;
        this.type = type;
        this.targetUser = targetUser;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }

    public Claim getClaim() {
        return claim;
    }

    public GuiType getType() {
        return type;
    }

    public UUID getTargetUser() {
        return targetUser;
    }

    public static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                List<String> loreList = new ArrayList<>();
                for (String l : lore)
                    loreList.add(l);
                meta.setLore(loreList);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createPlayerHead(UUID uuid) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(op);
            meta.setDisplayName("§b" + (op.getName() != null ? op.getName() : uuid.toString()));
            List<String> lore = new ArrayList<>();
            lore.add("§7Click to manage this user.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void openMainGui(Player player, Claim claim) {
        Inventory inv = Bukkit.createInventory(new ClaimGui(claim, GuiType.MAIN), 27, "Claim Management");

        // Background/Glass
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++)
            inv.setItem(i, glass);

        inv.setItem(11, createItem(Material.PLAYER_HEAD, "§6Trusted Members", "§7Manage who can access your claim."));

        // Info item
        String ownerName = claim.getOwnerName();
        ArrayList<String> builders = new ArrayList<>();
        ArrayList<String> containers = new ArrayList<>();
        ArrayList<String> accessors = new ArrayList<>();
        ArrayList<String> managers = new ArrayList<>();
        claim.getPermissions(builders, containers, accessors, managers);

        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Owner: §e" + ownerName);
        if (!managers.isEmpty()) {
            infoLore.add("§7Admins:");
            for (String manager : managers) {
                try {
                    UUID uuid = UUID.fromString(manager);
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    infoLore.add(" §8- §b" + (op.getName() != null ? op.getName() : manager));
                } catch (Exception e) {
                    infoLore.add(" §8- §b" + manager);
                }
            }
        }
        inv.setItem(13, createItem(Material.BOOK, "§dClaim Info", infoLore.toArray(new String[0])));

        String privacyStatus = claim.isPrivacyEnabled ? "§aEnabled" : "§cDisabled";
        String toggleMsg = "§7Current: " + privacyStatus;
        inv.setItem(15, createItem(Material.ENDER_EYE, "§bToggle Privacy", "§7Restrict entry to trusted members.",
                toggleMsg, " ", "§7Click to toggle."));

        player.openInventory(inv);
    }

    public static void openMembersGui(Player player, Claim claim) {
        ArrayList<String> builders = new ArrayList<>();
        ArrayList<String> containers = new ArrayList<>();
        ArrayList<String> accessors = new ArrayList<>();
        ArrayList<String> managers = new ArrayList<>();
        claim.getPermissions(builders, containers, accessors, managers);

        Set<String> allTrusted = new HashSet<>();
        allTrusted.addAll(builders);
        allTrusted.addAll(containers);
        allTrusted.addAll(accessors);
        allTrusted.addAll(managers);

        // Remove public/all entries
        allTrusted.remove("public");
        allTrusted.remove("all");

        Inventory inv = Bukkit.createInventory(new ClaimGui(claim, GuiType.MEMBERS), 54, "Trusted Members");

        int slot = 0;
        for (String id : allTrusted) {
            if (slot >= 54)
                break;
            try {
                UUID uuid = UUID.fromString(id);
                inv.setItem(slot++, createPlayerHead(uuid));
            } catch (IllegalArgumentException e) {
                // Not a UUID, skip or handle (like group trust)
            }
        }

        // Back button
        inv.setItem(49, createItem(Material.ARROW, "§7Back"));

        player.openInventory(inv);
    }

    public static void openManageUserGui(Player player, Claim claim, UUID targetUser) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetUser);
        String name = op.getName() != null ? op.getName() : targetUser.toString();
        Inventory inv = Bukkit.createInventory(new ClaimGui(claim, GuiType.MANAGE_USER, targetUser), 27,
                "Manage: " + name);

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++)
            inv.setItem(i, glass);

        inv.setItem(11, createItem(Material.RED_TERRACOTTA, "§cRemove from Trust", "§7Revoke all permissions."));
        inv.setItem(15, createItem(Material.GOLD_BLOCK, "§6Promote to Admin", "§7Allows them to manage trust."));

        inv.setItem(22, createItem(Material.ARROW, "§7Back"));

        player.openInventory(inv);
    }

    public static void openConfirmGui(Player player, Claim claim, GuiType type, UUID targetUser) {
        String title = type == GuiType.CONFIRM_REMOVE ? "Confirm Removal" : "Confirm Promotion";
        Inventory inv = Bukkit.createInventory(new ClaimGui(claim, type, targetUser), 27, title);

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++)
            inv.setItem(i, glass);

        inv.setItem(11, createItem(Material.LIME_CONCRETE, "§aConfirm"));
        inv.setItem(15, createItem(Material.RED_CONCRETE, "§cCancel"));

        player.openInventory(inv);
    }
}
