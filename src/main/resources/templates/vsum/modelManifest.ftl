Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: %pluginName
Bundle-SymbolicName: [=modelArtifactId];singleton:=true
Automatic-Module-Name: [=modelArtifactId]
Bundle-Version: 1.0.0.qualifier
Bundle-ClassPath: .
Bundle-Vendor: %providerName
Bundle-Localization: plugin
Bundle-RequiredExecutionEnvironment: JavaSE-17
Export-Package: [=exportPackageHeader]
Require-Bundle: org.eclipse.core.runtime,
 org.eclipse.emf.ecore;visibility:=reexport
Bundle-ActivationPolicy: lazy
