package com.svbio.cloudkeeper.filesystem;

import com.svbio.cloudkeeper.model.api.RuntimeContext;
import com.svbio.cloudkeeper.model.api.staging.InstanceProvider;
import com.svbio.cloudkeeper.model.api.staging.InstanceProvisionException;
import com.svbio.cloudkeeper.model.api.staging.StagingArea;
import com.svbio.cloudkeeper.model.api.staging.StagingAreaProvider;
import com.svbio.cloudkeeper.model.runtime.execution.RuntimeAnnotatedExecutionTrace;
import scala.concurrent.ExecutionContext;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class StagingAreaProviderImpl implements StagingAreaProvider {
    private static final long serialVersionUID = 2144916691023639055L;

    private final URI baseURI;
    private final ArrayList<URI> hardlinkEnabledURIs;

    StagingAreaProviderImpl(Path basePath, List<Path> hardlinkEnabledPaths) {
        baseURI = basePath.toUri();
        hardlinkEnabledURIs = hardlinkEnabledPaths.stream()
            .map(Path::toUri)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public StagingArea provideStaging(RuntimeContext runtimeContext, RuntimeAnnotatedExecutionTrace executionTrace,
            InstanceProvider instanceProvider) throws InstanceProvisionException {
        ExecutionContext executionContext = instanceProvider.getInstance(ExecutionContext.class);
        Path basePath = Paths.get(baseURI);
        List<Path> hardlinkEnabledPaths = hardlinkEnabledURIs
            .stream()
            .map(Paths::get)
            .collect(Collectors.toList());
        return new FileStagingArea.Builder(runtimeContext, executionTrace, basePath, executionContext)
            .setHardLinkEnabledPaths(hardlinkEnabledPaths)
            .build();
    }
}
