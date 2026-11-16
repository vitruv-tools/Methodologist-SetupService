<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.0"?>

<plugin>
<#list viewTypes as viewType>

   <!-- view metamodel of [=viewType.fileName] -->
   <extension point="org.eclipse.emf.ecore.generated_package">
      <package
            uri="[=viewType.nsUri]"
            class="[=viewType.packageInterface]"
            genModel="[=viewType.genmodelPath]"/>
   </extension>
</#list>

</plugin>
