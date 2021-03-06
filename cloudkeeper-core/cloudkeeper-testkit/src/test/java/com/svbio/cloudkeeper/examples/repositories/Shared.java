package com.svbio.cloudkeeper.examples.repositories;

import com.svbio.cloudkeeper.dsl.ModuleFactory;
import com.svbio.cloudkeeper.model.beans.element.MutablePluginDeclaration;

final class Shared {
    private Shared() {
        throw new AssertionError(String.format("No %s instances for you!", getClass().getName()));
    }

    static MutablePluginDeclaration<?> declarationFromClass(Class<?> clazz) {
        return MutablePluginDeclaration.copyOfPluginDeclaration(ModuleFactory.getDefault().loadDeclaration(clazz));
    }
}
