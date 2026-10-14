package com.oneshop.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oneshop.item.SprotaItem;
import com.oneshop.network.S2COpenShopScreenPayload;
import com.oneshop.service.PlayerAccountService;
import com.oneshop.service.ShopTransactionService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

public class OneShopCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("oneshop")
                        .then(Commands.literal("balance")
                                .executes(OneShopCommand::querySelfBalance)
                                .then(Commands.literal("set")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                                        .executes(OneShopCommand::setPlayerBalance)
                                                )
                                        )
                                )
                                .then(Commands.literal("get")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(OneShopCommand::queryOtherBalance)
                                        )
                                )
                        )
                        .then(Commands.literal("shop")
                                .then(Commands.literal("list")
                                        .executes(OneShopCommand::listShopCategories)
                                )
                                .then(Commands.literal("entry")
                                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .executes(OneShopCommand::queryShopEntry)
                                        )
                                )
                                .then(Commands.literal("stock")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1))
                                                        .executes(OneShopCommand::updateShopStock)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("edit")
                                .requires(source -> source.hasPermission(2))
                                .executes(OneShopCommand::openAdminEditor)
                        )
        );
    }

    private static int openAdminEditor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("commands.oneshop.edit.not_a_player"));
            return 0;
        }

        PlayerAccountService.syncBalance(player);
        ShopTransactionService.syncShopDataIfChanged(player);
        PacketDistributor.sendToPlayer(player, new S2COpenShopScreenPayload(true));
        return 1;
    }

    private static int querySelfBalance(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("commands.oneshop.balance.not_a_player"));
            return 0;
        }

        double balance = PlayerAccountService.getBalance(player);
        source.sendSuccess(() -> Component.translatable(
                "commands.oneshop.balance.query",
                String.format(Locale.ROOT, "%.2f", balance),
                SprotaItem.CURRENCY_SYMBOL
        ).withStyle(ChatFormatting.GOLD), false);

        return (int) balance;
    }

    private static int queryOtherBalance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");

        double balance = PlayerAccountService.getBalance(target);
        source.sendSuccess(() -> Component.translatable(
                "commands.oneshop.balance.other",
                target.getDisplayName(),
                String.format(Locale.ROOT, "%.2f", balance),
                SprotaItem.CURRENCY_SYMBOL
        ).withStyle(ChatFormatting.GOLD), false);

        return (int) balance;
    }

    private static int setPlayerBalance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        double amount = DoubleArgumentType.getDouble(context, "amount");

        PlayerAccountService.setBalance(target, amount);

        source.sendSuccess(() -> Component.translatable(
                "commands.oneshop.balance.set.success",
                target.getDisplayName(),
                String.format(Locale.ROOT, "%.2f", amount),
                SprotaItem.CURRENCY_SYMBOL
        ).withStyle(ChatFormatting.GREEN), true);

        if (source.getEntity() != target) {
            target.sendSystemMessage(Component.translatable(
                    "commands.oneshop.balance.set.notify",
                    String.format(Locale.ROOT, "%.2f", amount),
                    SprotaItem.CURRENCY_SYMBOL
            ).withStyle(ChatFormatting.GOLD));
        }

        return 1;
    }

    private static int listShopCategories(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var registry = com.oneshop.shop.ServerShopRegistry.getInstance();
        var categories = registry.getCategories(source.getServer());

        if (categories.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No shop categories available.").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== Shop Categories (" + categories.size() + ") ===").withStyle(ChatFormatting.GOLD), false);
        for (var cat : categories) {
            source.sendSuccess(() -> Component.literal(" - " + cat.id() + " [")
                    .append(cat.displayName())
                    .append("]: " + cat.entries().size() + " items (sortOrder: " + cat.sortOrder() + ")")
                    .withStyle(ChatFormatting.AQUA), false);
        }

        return categories.size();
    }

    private static int queryShopEntry(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "id");
        var registry = com.oneshop.shop.ServerShopRegistry.getInstance();
        var entryOpt = registry.getEntry(source.getServer(), id);

        if (entryOpt.isEmpty()) {
            source.sendFailure(Component.literal("Shop entry not found: " + id));
            return 0;
        }

        var entry = entryOpt.get();
        String stockStr = entry.isUnlimited() ? "Unlimited (-1)" : String.valueOf(entry.maxStock());
        source.sendSuccess(() -> Component.literal("=== Shop Entry: " + entry.id() + " ===").withStyle(ChatFormatting.GOLD), false);
        if (entry.hasCustomTitle()) {
            source.sendSuccess(() -> Component.literal("Title: " + entry.customTitle()), false);
        }
        if (!entry.item().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Item: ").append(entry.item().getDisplayName()), false);
        } else if (entry.hasTags()) {
            source.sendSuccess(() -> Component.literal("Tags: " + String.join(", ", entry.tags())), false);
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Buy Price: %.2f %s | Sell Price: %.2f %s",
                entry.buyPrice(), SprotaItem.CURRENCY_SYMBOL,
                entry.sellPrice(), SprotaItem.CURRENCY_SYMBOL)), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Trade Options: Buy=%s, Sell=%s",
                entry.allowBuy(), entry.allowSell())).withStyle(ChatFormatting.AQUA), false);
        if (entry.hasPermissionRequirement()) {
            source.sendSuccess(() -> Component.literal("Permission: " + entry.permission()).withStyle(ChatFormatting.GRAY), false);
        }
        if (!entry.runCommandsOnBuy().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Commands on buy: " + String.join(", ", entry.runCommandsOnBuy())).withStyle(ChatFormatting.DARK_GRAY), false);
        }

        return 1;
    }

    private static int updateShopStock(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "id");
        int amount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "amount");
        var registry = com.oneshop.shop.ServerShopRegistry.getInstance();

        boolean success = registry.updateStock(source.getServer(), id, amount);
        if (!success) {
            source.sendFailure(Component.literal("Shop entry not found: " + id));
            return 0;
        }

        com.oneshop.service.ShopTransactionService.broadcastShopData(source.getServer());

        String stockStr = amount == -1 ? "Unlimited" : String.valueOf(amount);
        source.sendSuccess(() -> Component.literal("Updated stock for '" + id + "' to " + stockStr).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
