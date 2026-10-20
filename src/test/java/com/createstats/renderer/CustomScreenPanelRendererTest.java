package com.createstats.renderer;

import com.createstats.display.CustomModuleInstance;
import com.createstats.display.CustomScreenLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomScreenPanelRendererTest {
    @Test
    void gridFamilyFallsBackToBaseWhenVariantHasNoLayoutData() {
        final CustomScreenLayout layout = new CustomScreenLayout();
        final CustomModuleInstance module = new CustomModuleInstance();
        module.setId("velocity-a");
        module.setBounds(new CustomModuleInstance.GridBounds(10, 0, 4, 4));
        layout.addModule(module);

        assertEquals(CustomScreenLayout.VariantFamily.BASE,
                CustomScreenPanelRenderer.gridFamilyForLayout(layout, CustomScreenLayout.VariantFamily.SQUARE));
    }

    @Test
    void gridFamilyUsesActiveVariantWhenOverridesExist() {
        final CustomScreenLayout layout = new CustomScreenLayout();
        final CustomModuleInstance module = new CustomModuleInstance();
        module.setId("velocity-a");
        layout.addModule(module);

        final CustomScreenLayout.VariantLayout square = new CustomScreenLayout.VariantLayout();
        final CustomModuleInstance.VariantOverride override = new CustomModuleInstance.VariantOverride();
        override.setBounds(new CustomModuleInstance.GridBounds(1, 1, 4, 4));
        square.putModuleOverride("velocity-a", override);
        layout.setVariant(CustomScreenLayout.VariantFamily.SQUARE, square);

        assertEquals(CustomScreenLayout.VariantFamily.SQUARE,
                CustomScreenPanelRenderer.gridFamilyForLayout(layout, CustomScreenLayout.VariantFamily.SQUARE));
    }

    @Test
    void gridFamilyUsesActiveVariantWhenVisibilityChanges() {
        final CustomScreenLayout layout = new CustomScreenLayout();
        final CustomModuleInstance module = new CustomModuleInstance();
        module.setId("velocity-a");
        module.getVisibilityPolicy().set(CustomScreenLayout.VariantFamily.SQUARE, CustomModuleInstance.VisibilityPolicy.VisibilityMode.HIDE);
        layout.addModule(module);

        assertEquals(CustomScreenLayout.VariantFamily.SQUARE,
                CustomScreenPanelRenderer.gridFamilyForLayout(layout, CustomScreenLayout.VariantFamily.SQUARE));
    }
}
