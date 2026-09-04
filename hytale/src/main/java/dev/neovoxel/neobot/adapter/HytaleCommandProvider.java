package dev.neovoxel.neobot.adapter;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.neovoxel.neobot.command.CommandProvider;
import dev.neovoxel.neobot.NeoBotHytale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class HytaleCommandProvider extends CommandProvider {
    private final NeoBotHytale plugin;

    public HytaleCommandProvider(NeoBotHytale plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    @Override
    public void registerCommand() {
        plugin.getCommandRegistry().registerCommand(new NHytaleCommand(this));
    }

    public class NHytaleCommand extends AbstractCommand {
        private HytaleCommandProvider provider;
        protected NHytaleCommand(HytaleCommandProvider provider) {
            super("neobot", "Commands for NeoBot");
            setAllowsExtraArguments(true);
            this.provider = provider;
        }

        @Override
        protected @Nullable CompletableFuture<Void> execute(@NotNull CommandContext ctx) {
            String input = ctx.getInputString();
            List<String> args = Arrays.stream(input.split(" ")).collect(Collectors.toList());
            if (!args.isEmpty()) {
                String first = args.get(0);
                if (first.equals("neobot") || first.equals("/neobot")) {
                    args.remove(0);
                }
            }
            provider.onCommand(new HytaleCommandSender(ctx.sender()), args.toArray(new String[0]));
            return null;
        }
    }

}
