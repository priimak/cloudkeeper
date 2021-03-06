package com.svbio.cloudkeeper.dsl.modules;

import com.svbio.cloudkeeper.dsl.CompositeModule;
import com.svbio.cloudkeeper.dsl.CompositeModulePlugin;
import com.svbio.cloudkeeper.dsl.ModuleFactory;
import com.svbio.cloudkeeper.dsl.exception.DanglingChildException;
import com.svbio.cloudkeeper.dsl.exception.RedundantFieldException;
import com.svbio.cloudkeeper.dsl.exception.UnassignedFieldException;
import com.svbio.cloudkeeper.model.immutable.Location;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Test that there is a bijection between {@link com.svbio.cloudkeeper.model.bare.element.module.BareModule} fields and child modules.
 */
public class ModuleFieldsAndChildrenBijectionTest {
    @CompositeModulePlugin("Test module to verify that child modules are assigned to member fields.")
    public abstract static class DanglingChildModule extends CompositeModule<DanglingChildModule> {{
        child(Sum.class);
    }}

    @Test
    public void danglingChildTest() throws Exception {
        try {
            ModuleFactory.getDefault().create(DanglingChildModule.class);
            Assert.fail("Expected exception.");
        } catch (DanglingChildException exception) {
            Location location = exception.getLocation();
            Assert.assertTrue(location.getSystemId().contains(DanglingChildModule.class.getName()));
            Assert.assertEquals(location.getLineNumber(), 22);
        }
    }

    public abstract static class RedundantChildModule extends CompositeModule<RedundantChildModule> {
        public abstract InPort<Integer> someNumber();
        public abstract InPort<Integer> otherNumber();

        Sum child = child(Sum.class).
            firstPort().from(someNumber()).
            secondPort().from(otherNumber());
        Sum otherChild = child;
    }

    @Test
    public void redundantChildTest() throws Exception {
        try {
            ModuleFactory.getDefault().create(RedundantChildModule.class);
            Assert.fail("Expected exception.");
        } catch (RedundantFieldException exception) {
            Assert.assertEquals(
                new HashSet<>(exception.getFields()),
                new HashSet<>(Arrays.asList("child", "otherChild"))
            );
            Location location = exception.getLocation();
            Assert.assertTrue(location.getSystemId().contains(RedundantChildModule.class.getName()));
            Assert.assertEquals(location.getLineNumber(), 41);
        }
    }

    @CompositeModulePlugin("Test module to verify that all Module fields reference have instances.")
    public abstract static class UnassignedChildModule extends CompositeModule<UnassignedChildModule> {
        Sum sum;
    }

    @Test
    public void unassignedFieldTest() throws Exception {
        try {
            ModuleFactory.getDefault().create(UnassignedChildModule.class);
            Assert.fail("Expected exception.");
        } catch (UnassignedFieldException exception) {
            Assert.assertEquals(exception.getField().getName(), "sum");
        }
    }
}
