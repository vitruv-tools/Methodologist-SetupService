package tools.vitruv.methodologist.setup.config;

import java.io.File;

/**
 * The MetamodelLocation class is used to store the location of a metamodel and its corresponding
 * genmodel.
 *
 * @param metamodel the ecore file
 * @param genmodel the genmodel file
 * @param nsUri the namespace URI of the metamodel's root package
 * @param modelDirectory the model directory of the genmodel
 * @param packageClassName the qualified name of the generated package interface
 */
public record MetamodelLocation(
    File metamodel, File genmodel, String nsUri, String modelDirectory, String packageClassName) {}
