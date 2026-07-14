    component = EcoreGenerator {
        genModel = "platform:/resource/${item.packageName}/src/main/ecore/${item.modelName}"
        srcPath = "platform:/resource/${item.targetDir}/target/${item.modelDirectory}"
        generateCustomClasses = false   
    }
    