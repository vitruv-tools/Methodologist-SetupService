package tools.vitruv.methodologist.setup.vsum.service.metamodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class EcoreLocationsTest {

  private static final String ECORE =
      """
      <ecore:EPackage name="model" nsURI="http://example.org/model">
        <eClassifiers xsi:type="ecore:EClass" name="System">
          <eStructuralFeatures xsi:type="ecore:EAttribute" name="name"/>
        </eClassifiers>
        <eClassifiers xsi:type="ecore:EClass" name="Component">
          <eStructuralFeatures xsi:type="ecore:EAttribute" name="name"/>
        </eClassifiers>
      </ecore:EPackage>
      """;

  @Test
  void findsAFeatureWithinItsClass() {
    assertEquals(6, EcoreLocations.lineOf(ECORE, List.of("model", "Component", "name")));
    assertEquals(3, EcoreLocations.lineOf(ECORE, List.of("model", "System", "name")));
  }

  @Test
  void fallsBackToTheInnermostElementFound() {
    assertEquals(5, EcoreLocations.lineOf(ECORE, List.of("model", "Component", "missing")));
    assertNull(EcoreLocations.lineOf(ECORE, List.of("other")));
  }
}
