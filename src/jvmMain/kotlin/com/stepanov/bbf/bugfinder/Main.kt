package com.stepanov.bbf.bugfinder

import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.PropertyConfigurator
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    //Init log4j
    PropertyConfigurator.configure("src/main/resources/bbfLog4j.properties")
    if (!CompilerArgs.getPropAsBoolean("LOG")) {
        Logger.getRootLogger().level = Level.OFF
        Logger.getLogger("bugFinderLogger").level = Level.OFF
        Logger.getLogger("mutatorLogger").level = Level.OFF
        Logger.getLogger("reducerLogger").level = Level.OFF
        Logger.getLogger("transformationManagerLog").level = Level.OFF
    }
    val file = File(CompilerArgs.baseDir).listFiles()?.filter { it.path.endsWith(".kt") }?.random() ?: exitProcess(0)
    SingleFileBugFinder(file.absolutePath).findBugsInFile()
    exitProcess(0)
//    val results = mutableMapOf<String, Pair<Double, Double>>()
//    var fl = false
//    for (f in File("/home/zver/IdeaProjects/kotlinBugs/performance/tmp").listFiles()) {
//        //if (f.name == "oalqvzn_FILE.kt") fl = true
//        //if (!fl) continue
//        println(f.name)
//        val p = Project.createFromCode(f.readText())
//        if (!JVMCompiler().checkCompiling(p)) continue
//        val (a, b) = PerformanceOracle.initWithAverageResults(p, CompilerArgs.getCompilersList()) ?: (-1.0 to -1.0)
//        println("AVERAGE COMP DEVIATION = $a")
//        println("AVERAGE EXEC DEVIATION = $b")
//        results[f.name] = a to b
//        println("---------------------------------------")
//    }
//    results.entries.sortedByDescending { it.value.first }.forEach(::println)
//    println("EXECUTION:")
//    results.entries.sortedByDescending { it.value.second }.forEach(::println)
//    exitProcess(0)
//    JVMCompiler("").tryToCompile(Project.createFromCode("fun main(){ val a = 1 } "))
//    for (f in File("tmp/arrays").listFiles().filter { it.isFile && it.path.endsWith("kt") }) {
//        println(f.name)
//        val project = Project.createFromCode(File("tmp/myTest.kt").readText())
//        //val project = Project.createFromCode(f.readText())
//        PerformanceOracle.init(project, CompilerArgs.getCompilersList())
//        SingleFileBugFinder().findBugsInFile()
//        exitProcess(0)
//    }
    //(10 + 12 + 14) / 3
    //Mo = 10
    //D = (0 + 4 + 16) / 3 = 6.666
    //sigma = 2.6
    //10 +- 2.6


//    var fl = false
//    for (f in File("tmp/arrays").listFiles().filter { it.isFile && it.path.endsWith("kt") }) {
//        if (f.name == "typeToStringInnerGeneric.kt") fl = true
//        if (!fl) continue
//        println(f.name)
//        //val p = Project.createFromCode(f.readText())
//        val p = Project.createFromCode(File("tmp/myTest.kt").readText())
//        val ktFile = p.files.first().psiFile as? KtFile ?: continue
//        val ctx = PSICreator.analyze(ktFile, p) ?: continue
//        val classes = ktFile.getAllPSIChildrenOfType<KtClass>()
//        val cl = classes.last().getDeclarationDescriptorIncludingConstructors(ctx) as ClassDescriptor
//        val randomInstanceGenerator = RandomInstancesGenerator(ktFile)
//        val rtg = RandomTypeGenerator
//        rtg.setFileAndContext(ktFile, ctx)
//        //check flatMap
//        val type = rtg.generateType("ArrayDeque<Boolean?>")!!
//        repeat(500) {
//            println(randomInstanceGenerator.generateValueOfType(type))
//        }
//        exitProcess(0)
//        classes.forEach { cl ->
//            if (cl.isAnnotation()) return@forEach
//            val interfaceType = (cl.getDeclarationDescriptorIncludingConstructors(ctx) as? ClassDescriptor)?.defaultType
//            if (interfaceType == null) {
//                println("CANT GET TYPE FOR ${cl.text}")
//                return@forEach
//            }
//            val instance = randomInstanceGenerator.generateValueOfType(interfaceType)
//            println("INSTANCE = ${instance}")
//            if (instance.isEmpty()) {
//                println("CANT GET INSTANCE FOR ${cl.text}")
//            }
//        }
//    }
//    exitProcess(0)
//
//    exitProcess(0)
//    println(JVMCompiler("-Xuse-old-backend").checkCompiling(p))
//    //ProjectInstrumenter.instrument()
//    exitProcess(0)
//    val p = Project.createFromCode(File("/home/zver/IdeaProjects/bbfgradle/tmp/arrays/all-compatibility.kt").readText())
//    println(JVMCompiler("-Xuse-old-backend").checkCompiling(p))
//    val files = File(CompilerArgs.baseDir).listFiles()?.filter { it.path.endsWith(".kt") }!!.toList()
//    val sumCov = mutableSetOf<CoverageEntry>()
//    for (f in files) {
//        println(f.name)
//        //if (f.name != "slowHtmlLikeDsl.kt") continue
//        val p = Project.createFromCode(f.readText())
//        //println(f.name)
//        CoverageGuider.init("", p)
//        val cov = CoverageGuider.getCoverage(p, listOf(JVMCompiler()))
//        sumCov += cov.keys.toList()
//        val intersect = sumCov.intersect(CoverageGuider.desiredCoverage)
//        println("INTERSECT = ${intersect.size}")
//        val diff = CoverageGuider.desiredCoverage - sumCov
//        println("DIFF = ${diff.size}")
//        if (diff.isEmpty()) {
//            println("EEEE")
//            exitProcess(0)
//        }
//    }
//    println(sumCov.intersect(CoverageGuider.desiredCoverage))
    exitProcess(0)
//    val parser = ArgumentParsers.newFor("bbf").build()
//    parser.addArgument("-r", "--reduce")
//        .required(false)
//        .help("Reduce mode")
//    parser.addArgument("-f", "--fuzz")
//        .required(false)
//        .help("Fuzzing mode")
//    parser.addArgument("-c", "--clean")
//        .required(false)
//        .action(Arguments.storeTrue())
//        .help("Clean directories with bugs from bugs that are not reproduced")
//    parser.addArgument("-d", "--database")
//        .required(false)
//        .action(Arguments.storeTrue())
//        .help("Database updating")
//    val arguments = parser.parseArgs(args)
//    arguments.getString("reduce")?.let {
//        //TODO
//        exitProcess(0)
//    }
//    arguments.getString("fuzz")?.let {
//        require(File(it).isDirectory) { "Specify directory to take files for mutation" }
//        val file = File(it).listFiles()?.random() ?: throw IllegalArgumentException("Wrong directory")
//        SingleFileBugFinder(file.absolutePath).findBugsInFile()
//        exitProcess(0)
//    }
//    if (arguments.getString("database") == "true") {
//        NodeCollector(CompilerArgs.baseDir).collect()
//        exitProcess(0)
//    }
//    if (arguments.getString("clean") == "true") {
//        FalsePositivesDeleter().cleanDirs()
//        exitProcess(0)
//    }
}
