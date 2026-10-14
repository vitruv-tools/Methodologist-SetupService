   <extension point="org.eclipse.emf.ecore.generated_package">
      <!-- @generated model -->
      <package
            uri="${item.nsUri}"
            class="${item.packageClassName}"
            genModel="src/main/ecore/${item.genmodelName}"/>
   </extension>