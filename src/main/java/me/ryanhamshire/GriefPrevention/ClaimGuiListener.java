package me.ryanhamshire.GriefPrevention;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

public class ClaimGuiListener implements Listener {

    private final GriefPrevention plugin;

    public ClaimGuiListener(GriefPrevention plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) event.getWhoClicked();

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ClaimGui))
            return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR)
            return;
        if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE)
            return;

        ClaimGui gui = (ClaimGui) holder;
        Claim claim = gui.getClaim();
        ItemStack clicked = event.getCurrentItem();

        switch (gui.getType()) {
            case MAIN -> handleMain(player, claim, clicked);
            case MEMBERS -> handleMembers(player, claim, clicked, event.getSlot());
            case MANAGE_USER -> handleManageUser(player, claim, gui.getTargetUser(), clicked);
            case CONFIRM_REMOVE -> handleConfirmRemove(player, claim, gui.getTargetUser(), clicked);
            case CONFIRM_PROMOTE -> handleConfirmPromote(player, claim, gui.getTargetUser(), clicked);
        }
    }

    private void handleMain(Player player, Claim claim, ItemStack clicked) {
        if (clicked.getType() == Material.PLAYER_HEAD) {
            // Trusted Members
            player.closeInventory();
            ClaimGui.openMembersGui(player, claim);
        } else if (clicked.getType() == Material.ENDER_EYE) {
            // Toggle Privacy
            claim.isPrivacyEnabled = !claim.isPrivacyEnabled;
            plugin.dataStore.writeClaimToStorage(claim);

            if (claim.isPrivacyEnabled) {
                GriefPrevention.sendMessage(player, TextMode.Success, Messages.ClaimPrivacyEnabled);

                // Eject unauthorized players currently in the claim
                for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
                    if (otherPlayer.getWorld().equals(claim.getLesserBoundaryCorner().getWorld())
                            && claim.contains(otherPlayer.getLocation(), true, false)) {
                        if (claim.allowAccess(otherPlayer) != null
                                && !otherPlayer.hasPermission("griefprevention.ignoreclaims")) {
                            plugin.ejectPlayer(otherPlayer);
                            GriefPrevention.sendMessage(otherPlayer, TextMode.Warn,
                                    "You have been ejected from this claim because privacy was enabled.");
                        }
                    }
                }
            } else {
                GriefPrevention.sendMessage(player, TextMode.Success, Messages.ClaimPrivacyDisabled);
            }

            // Refresh the GUI
            ClaimGui.openMainGui(player, claim);
        }
    }

    private void handleMembers(Player player, Claim claim, ItemStack clicked, int slot) {
        if (clicked.getType() == Material.ARROW) {
            // Back to main
            player.closeInventory();
            ClaimGui.openMainGui(player, claim);
            return;
        }

        if (clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta == null || meta.getOwningPlayer() == null)
                return;
            UUID targetUUID = meta.getOwningPlayer().getUniqueId();
            player.closeInventory();
            ClaimGui.openManageUserGui(player, claim, targetUUID);
        }
    }

    private void handleManageUser(Player player, Claim claim, UUID targetUser, ItemStack clicked) {
        if (clicked.getType() == Material.ARROW) {
            // Back to members
            player.closeInventory();
            ClaimGui.openMembersGui(player, claim);
            return;
        }

        if (clicked.getType() == Material.RED_TERRACOTTA) {
            // Confirm Remove
            player.closeInventory();
            ClaimGui.openConfirmGui(player, claim, ClaimGui.GuiType.CONFIRM_REMOVE, targetUser);
        } else if (clicked.getType() == Material.GOLD_BLOCK) {
            // Confirm Promote
            player.closeInventory();
            ClaimGui.openConfirmGui(player, claim, ClaimGui.GuiType.CONFIRM_PROMOTE, targetUser);
        }
    }

    private void handleConfirmRemove(Player player, Claim claim, UUID targetUser, ItemStack clicked) {
        if (clicked.getType() == Material.LIME_CONCRETE) {
            // Confirmed remove - drop all permissions
            String idToDrop = targetUser.toString();

            // Check the executor has permission to manage the claim
            if (claim.checkPermission(player, ClaimPermission.Manage, null) != null) {
                player.sendMessage("§cYou don't have permission to manage this claim's trust.");
                player.closeInventory();
                return;
            }

            // RESTRICTION: Admin cannot remove owner or other admins
            boolean isOwner = player.getUniqueId().equals(claim.ownerID);
            boolean targetIsOwner = targetUser.equals(claim.ownerID);
            boolean targetIsAdmin = claim.managers.contains(idToDrop);

            if (targetIsOwner) {
                player.sendMessage("§cYou cannot remove the claim owner!");
                player.closeInventory();
                return;
            }

            if (!isOwner && targetIsAdmin) {
                player.sendMessage("§cOnly the owner can remove other admins.");
                player.closeInventory();
                return;
            }

            claim.dropPermission(idToDrop);
            plugin.dataStore.saveClaim(claim);

            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUser);
            String name = target.getName() != null ? target.getName() : targetUser.toString();
            player.sendMessage("§aRemoved §e" + name + "§a from the claim's trust.");
            player.closeInventory();

        } else if (clicked.getType() == Material.RED_CONCRETE) {
            // Cancelled - go back to manage user
            player.closeInventory();
            ClaimGui.openManageUserGui(player, claim, targetUser);
        }
    }

    private void handleConfirmPromote(Player player, Claim claim, UUID targetUser, ItemStack clicked) {
        if (clicked.getType() == Material.LIME_CONCRETE) {
            // Confirmed promote to admin (Manage permission)
            // ONLY owner can promote/demote admins
            if (!player.getUniqueId().equals(claim.ownerID)) {
                player.sendMessage("§cOnly the claim owner can promote members to admin.");
                player.closeInventory();
                return;
            }

            String idToAdd = targetUser.toString();
            if (!claim.managers.contains(idToAdd)) {
                claim.managers.add(idToAdd);
            }
            plugin.dataStore.saveClaim(claim);

            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUser);
            String name = target.getName() != null ? target.getName() : targetUser.toString();
            player.sendMessage("§aPromoted §e" + name + "§a to claim admin. They can now manage trust.");
            player.closeInventory();

        } else if (clicked.getType() == Material.RED_CONCRETE) {
            // Cancelled - go back to manage user
            player.closeInventory();
            ClaimGui.openManageUserGui(player, claim, targetUser);
        }
    }
}
