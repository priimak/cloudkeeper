package com.svbio.cloudkeeper.model.api.executor;

import com.svbio.cloudkeeper.model.api.Executable;
import com.svbio.cloudkeeper.model.api.ModuleConnector;
import scala.concurrent.Future;

/**
 * Extended {@link ModuleConnector} interface, providing additional functionality for use by
 * {@link SimpleModuleExecutor} instances.
 *
 * <p>It is implementation-dependent whether a module connector retrieves inputs lazily (that is, only when
 * {@link ModuleConnector#getInput(com.svbio.cloudkeeper.model.immutable.element.SimpleName)} is called) or before the
 * module connector is passed to {@link Executable#run(ModuleConnector)}.
 *
 * <p>It is guaranteed that all values passed to the module connector will only be committed to the staging area
 * after a call to {@link ExtendedModuleConnector#commit()}.
 */
public interface ExtendedModuleConnector extends ModuleConnector, AutoCloseable {
    /**
     * Write the values previously set via
     * {@link ModuleConnector#setOutput(com.svbio.cloudkeeper.model.immutable.element.SimpleName, Object)} to the
     * staging area.
     *
     * <p>Only this method writes values to the staging area. If this method is not called, the staging area is
     * guaranteed to be remain unmodified.
     *
     * @return Future that will be completed with an implementation-defined value on success and a
     *     {@link com.svbio.cloudkeeper.model.api.staging.StagingException} or {@link IncompleteOutputsException} on
     *     failure (unless the {@link Throwable} is not an {@link Exception}).
     */
    Future<Object> commit();

    /**
     * Releases all system resources acquired by the module connector.
     *
     * <p>The most common action performed by this method is to clean up the working directory, if it was previously
     * created by {@link ModuleConnector#getWorkingDirectory()}.
     *
     * <p>This method must be called once this instance is no longer needed (that is, after the simple module has been
     * executed).
     *
     * <p>Callers of this method are not expected to deal with runtime exceptions (and this method does not throw
     * checked exceptions). Implementations should log exceptions in this method as appropriate.
     */
    @Override
    void close();
}
