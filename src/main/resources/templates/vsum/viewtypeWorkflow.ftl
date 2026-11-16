module [=viewtypeArtifactId]

import tools.vitruv.neojoin.vitruv.generator.AggregateEcoreGenerator
import org.eclipse.emf.mwe.utils.StandaloneSetup

var workspaceRoot = ".."

Workflow {

    bean = StandaloneSetup {
        scanClassPath = true
        platformUri = workspaceRoot
    }

    // generates the code of all view metamodels NeoJoin generated from the view definitions
    component = AggregateEcoreGenerator {
        genModels = "platform:/resource/[=viewtypeArtifactId]/[=generatedNeojoinSources]/ecore"
        srcPath = "platform:/resource/[=viewtypeArtifactId]/target/generated-sources/ecore"
        generateCustomClasses = false
    }

}
