<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.0"?>

<plugin>
<#list metamodels as metamodel>
<#list metamodel.packages as package>

   <extension point="org.eclipse.emf.ecore.generated_package">
      <package
            uri="[=package.nsUri]"
            class="[=package.packageInterface]"
            genModel="[=ecoreDirectory]/[=metamodel.genmodelFileName]"/>
   </extension>
</#list>
</#list>

</plugin>
