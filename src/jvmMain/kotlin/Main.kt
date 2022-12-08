
import com.stepanov.bbf.reduktor.util.MsgCollector
import org.jetbrains.kotlin.cli.bc.K2Native
import org.jetbrains.kotlin.cli.bc.K2NativeCompilerArguments
import org.jetbrains.kotlin.config.Services

fun main(args: Array<String>) {
    val compiler = K2Native()
    val arguments = K2NativeCompilerArguments()
    val compilerArgumentsKlib = arrayOf(args[0],
        "-kotlin-home", "/home/oleg/.konan/kotlin-native-prebuilt-linux-x86_64-1.7.21/",
        "-p", "library",
        "-o", args[1]
    )
    val compilerArgumentsMain = arrayOf(args[2],
        "-kotlin-home", "/home/oleg/.konan/kotlin-native-prebuilt-linux-x86_64-1.7.21/",
        "-l", args[1],
        "-o", args[3]
    )

    K2Native().parseArguments(compilerArgumentsKlib , arguments)

    compiler.exec(MsgCollector, Services.EMPTY, arguments)


    if (MsgCollector.hasCompileError) {
        println(MsgCollector.compileErrorMessages[0])
    }
    if (MsgCollector.hasException) {
        println(MsgCollector.compileErrorMessages[0])
    }

    val argumentsMain = K2NativeCompilerArguments()
    K2Native().parseArguments(compilerArgumentsMain , argumentsMain)
    compiler.exec(MsgCollector, Services.EMPTY, argumentsMain)

    if (MsgCollector.hasCompileError) {
        println(MsgCollector.compileErrorMessages[0])
    }
    if (MsgCollector.hasException) {
        println(MsgCollector.compileErrorMessages[0])
    }

}