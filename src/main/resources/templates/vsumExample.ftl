package ${packageName}.vsum;

import tools.vitruv.framework.vsum.VirtualModelBuilder;
import ${packageName}.model.model.ModelFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import java.nio.file.Path;
import java.util.function.Consumer;
import mir.reactions.model2Model2.Model2Model2ChangePropagationSpecification;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.views.ViewType;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.remote.server.*;
import java.util.Scanner;
import java.io.IOException;

/**
 * This class provides an example how to define and use a VSUM.
 */
public class VSUMExample {
  public static void main(String[] args) {
    try {
      VitruvServer server = new VitruvServer(VSUMExample::createDefaultVirtualModel);
      server.start();
      Scanner scanner = new Scanner(System.in);
      while (!scanner.nextLine().equals("quit")) {
        
      }
      scanner.close();
      server.stop();
    } catch (IOException e) {
      System.out.println("Something went wrong " + e);
    }
  }

  private static VirtualModel createDefaultVirtualModel() {
    Collection<ViewType<?>> viewTypes = createDefaultViewTypes();
    VirtualModel model = new VirtualModelBuilder()
        .withStorageFolder(Path.of("vsumexample"))
        .withUserInteractorForResultProvider(new TestUserInteraction.ResultProvider(new TestUserInteraction()))
        .withChangePropagationSpecifications(new Model2Model2ChangePropagationSpecification()).withViewTypes(viewTypes)
        .buildAndInitialize();
      getDefaultView(model);
      return model;
  }

  private static View getDefaultView(VirtualModel vsum) {
    var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("default"));
    selector.getSelectableElements().forEach(it -> selector.setSelected(it, true));
    return selector.createView();
  }

  private static void modifyView(CommittableView view, Consumer<CommittableView> modificationFunction) {
    modificationFunction.accept(view);
    view.commitChanges();
  }
  
  private static Collection<ViewType<?>> createDefaultViewTypes() {
    List<ViewType<?>> viewTypes = new ArrayList<>();
    <#list models as model>
        <#include "viewTypes.ftl">
    </#list>
    return viewTypes;
  }

}