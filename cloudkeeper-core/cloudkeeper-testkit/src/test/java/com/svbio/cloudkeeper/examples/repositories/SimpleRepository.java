package com.svbio.cloudkeeper.examples.repositories;

import com.svbio.cloudkeeper.examples.modules.BinarySum;
import com.svbio.cloudkeeper.examples.modules.Decrease;
import com.svbio.cloudkeeper.examples.modules.GreaterOrEqual;
import com.svbio.cloudkeeper.examples.modules.ThrowingModule;
import com.svbio.cloudkeeper.model.bare.element.BareBundle;
import com.svbio.cloudkeeper.model.beans.element.MutableBundle;
import com.svbio.cloudkeeper.model.beans.element.MutablePackage;
import com.svbio.cloudkeeper.model.beans.element.MutablePluginDeclaration;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

public final class SimpleRepository implements TestKitBundleProvider {
    public static final URI BUNDLE_ID = TestKitRuntimeContextFactory.bundleIdentifier(SimpleRepository.class);

    @Override
    public BareBundle get() {
        return new MutableBundle()
            .setBundleIdentifier(BUNDLE_ID)
            .setPackages(Collections.singletonList(
                new MutablePackage()
                    .setQualifiedName(BinarySum.class.getPackage().getName())
                    .setDeclarations(Arrays.<MutablePluginDeclaration<?>>asList(
                        Shared.declarationFromClass(BinarySum.class),
                        Shared.declarationFromClass(Decrease.class),
                        Shared.declarationFromClass(GreaterOrEqual.class),
                        Shared.declarationFromClass(ThrowingModule.class)
                    ))
            ));
    }
}
