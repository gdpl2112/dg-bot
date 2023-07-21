package io.github.gdpl2112.dg_bot.built;

import net.mamoe.mirai.console.command.CommandManager;
import net.mamoe.mirai.console.extension.PluginComponentStorage;
import net.mamoe.mirai.console.plugin.jvm.JavaPlugin;
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescriptionBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * @author github-kloping
 * @date 2023-07-21
 */
public class BuiltPlugin extends JavaPlugin {
    public static final BuiltPlugin INSTANCE = new BuiltPlugin();

    public BuiltPlugin() {
        super(new JvmPluginDescriptionBuilder("io.github.gdpl2112.dg_bot.built.BuiltPlugin", "1.0")
                .author("kloping")
                .name("dg-built-plugin")
                .build());
    }

    @Override
    public void onLoad(@NotNull PluginComponentStorage $this$onLoad) {
        super.onLoad($this$onLoad);
        CommandManager.INSTANCE.registerCommand(CommandLine.INSTANCE, true);
    }
}
