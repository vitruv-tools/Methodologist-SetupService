package ${packageName}.vsum;

import tools.vitruv.framework.vsum.VirtualModelBuilder;

import java.nio.file.Path;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import org.junit.jupiter.api.Disabled;

/**
 * This class provides an example how to define and use a VSUM.
 */
public class VSUMExampleTest {

  static final Path projectPath = Path.of("target/vsumexample");

  @BeforeAll
  static void setup() {
    Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("model", new XMIResourceFactoryImpl());
  }

  @Disabled
  @Test
  void test() {
    VirtualModel vsum = createDefaultVirtualModel();
    Assertions.assertNotNull(getDefaultView(vsum), "Creating the default view failed");
  }

  private VirtualModel createDefaultVirtualModel() {
    return new VirtualModelBuilder()
        .withStorageFolder(projectPath)
        .withUserInteractorForResultProvider(new TestUserInteraction.ResultProvider(new TestUserInteraction()))
        .withChangePropagationSpecifications(<#list changePropagationSpecifications as specification>new ${specification}()<#sep>, </#list>)
        .buildAndInitialize();
  }

  private View getDefaultView(VirtualModel vsum) {
    var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("default"));
    selector.getSelectableElements().forEach(it -> selector.setSelected(it, true));
    return selector.createView();
  }

}