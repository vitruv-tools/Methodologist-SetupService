module [=modelArtifactId]

import org.eclipse.emf.mwe2.ecore.EcoreGenerator
import org.eclipse.emf.mwe.utils.StandaloneSetup

var workspaceRoot = ".."

Workflow {

    bean = StandaloneSetup {
        scanClassPath = true
        platformUri = workspaceRoot
    }
<#list metamodels as metamodel>

    component = EcoreGenerator {
        genModel = "platform:/resource/[=modelArtifactId]/[=ecoreDirectory]/[=metamodel.genmodelFileName]"
        srcPath = "platform:/resource/[=modelArtifactId]/[=generatedSources]"
        generateCustomClasses = false
    }
</#list>

}
