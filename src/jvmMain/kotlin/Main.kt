import com.stepanov.bbf.reduktor.util.MsgCollector
import org.jetbrains.kotlin.cli.bc.K2Native
import org.jetbrains.kotlin.cli.bc.K2NativeCompilerArguments
import org.jetbrains.kotlin.config.Services

fun main() {
    val compiler = K2Native()
    val arguments = K2NativeCompilerArguments()
    val compilerArguments = arrayOf("resources/kotlinTest.kt",
        "-kotlin-home", "/home/oleg/.konan/kotlin-native-prebuilt-linux-x86_64-1.7.21/")
    K2Native().parseArguments(compilerArguments , arguments)

    compiler.exec(MsgCollector, Services.EMPTY, arguments)

    MsgCollector
}