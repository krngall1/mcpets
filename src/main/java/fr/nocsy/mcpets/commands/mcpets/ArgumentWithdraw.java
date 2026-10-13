package fr.nocsy.mcpets.commands.mcpets;

import fr.nocsy.mcpets.commands.AArgument;
import fr.nocsy.mcpets.data.PetVoucher;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ArgumentWithdraw extends AArgument {

    public ArgumentWithdraw(CommandSender sender, String[] args) {
        super("withdraw", new int[]{2}, sender, args, "/mcpets withdraw <pet>");
    }

    // Trade one of your own pets in for its voucher (only pets that are Withdrawable)
    @Override
    public void commandEffect() {
        PetVoucher.withdraw((Player) sender, args[1]);
    }

    @Override
    protected boolean additionalConditions() {
        return sender instanceof Player;
    }
}
