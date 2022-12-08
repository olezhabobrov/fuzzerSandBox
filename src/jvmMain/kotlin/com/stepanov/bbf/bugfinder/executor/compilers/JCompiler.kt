package com.stepanov.bbf.bugfinder.executor.compilers

//class JCompiler : CommonCompiler() {
//    override fun checkCompiling(pathToFile: String): Boolean {
//        return true
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    override fun getErrorMessageWithLocation(pathToFile: String): Pair<String, List<CompilerMessageLocation>> {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    private fun decompileToJava(path: String): String = K2JConverter().convert(path, false)
//
//    override fun compile(path: String, includeRuntime: Boolean): CompilingResult {
//        //Path - path to .kt file
//        // First we need to decompile it to java
//        try {
//            val javaFilesPaths = decompileToJava(path).split(" ")
//            if (javaFilesPaths.isEmpty() || javaFilesPaths.all { it.trim().isEmpty() }) return CompilingResult(-1, "")
//            val javaFiles = javaFilesPaths.map { File(it) }
//            if (javaFiles.isEmpty()) return CompilingResult(-1, "")
//            val pathToTmpDir = CompilerArgs.pathToTmpFile.substringBeforeLast('/') + "/tmp"
//            File(pathToTmpDir).deleteRecursively()
//            File(pathToTmpDir).mkdirs()
//            val compiler = ToolProvider.getSystemJavaCompiler()
//            val diagnostics = DiagnosticCollector<JavaFileObject>()
//            val manager = compiler.getStandardFileManager(diagnostics, null, null)
//            val sources = manager.getJavaFileObjectsFromFiles(javaFiles)
//            val classPath =
//                (CompilerArgs.jvmStdLibPaths
//                        + CompilerArgs.getAnnoPath("13.0")
//                        + CompilerArgs.pathToTmpFile)
//                    .joinToString(":")
//            val options = mutableListOf("-classpath", classPath, "-d", pathToTmpDir)
//            val task = compiler.getTask(null, manager, diagnostics, options, null, sources)
//            task.call()
//            val errorDiagnostics = diagnostics.diagnostics.filter { it.kind == Diagnostic.Kind.ERROR }
//            return if (errorDiagnostics.isEmpty()) {
//                CompilingResult(0, pathToTmpDir)
//            } else {
//                CompilingResult(-1, "")
//            }
//        } catch (e: Exception) {
//            return CompilingResult(-1, "")
//        }
//    }
//
//    override fun tryToCompile(pathToFile: String): KotlincInvokeStatus {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    override fun tryToCompile(project: Project): KotlincInvokeStatus {
//        TODO("Not yet implemented")
//    }
//
//    override fun isCompilerBug(pathToFile: String): Boolean {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    override fun exec(path: String, streamType: Stream): String {
//        val pathToLib = (CompilerArgs.jvmStdLibPaths + path).joinToString(":")
//        val tmpFiles = File(path).listFiles().filter { it.absolutePath.contains("Tmp") }
//        val tmp = tmpFiles.find { it.name.contains("TmpKt.class") }
//            ?: tmpFiles.find { it.name.contains("Tmp0Kt.class") }
//            ?: tmpFiles.first()
//        return commonExec("java -cp $pathToLib ${tmp.nameWithoutExtension}")
//    }
//
//    override val compilerInfo: String
//        get() = "JAVA"
//    override val pathToCompiled: String
//        get() = "tmp/tmp.jar"
//}