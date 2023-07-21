package io.github.gdpl2112.dg_bot.built

import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.java.JCompositeCommand
import net.mamoe.mirai.console.internal.data.builtins.AutoLoginConfig
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.BotConfiguration

class CommandLine private constructor() : JCompositeCommand(BuiltPlugin.INSTANCE, "k") {
    companion object {
        @JvmField
        val INSTANCE = CommandLine()
    }

    init {
        description = "kloping 内置插件 命令"
    }

    @OptIn(ConsoleExperimentalApi::class)
    @Description("重新登录;必须配置自动登录")
    @SubCommand("reLogin")
    suspend fun CommandSender.reLogin(@Name("指定id") id: Long) {
        sendMessage("OK")
    }
}