package com.svbio.cloudkeeper.interpreter;

import akka.actor.ActorRef;
import akka.util.Timeout;
import com.svbio.cloudkeeper.model.api.CloudKeeperEnvironment;
import com.svbio.cloudkeeper.model.api.WorkflowExecutionBuilder;
import com.svbio.cloudkeeper.model.api.staging.InstanceProvider;
import com.svbio.cloudkeeper.model.api.staging.StagingAreaProvider;
import com.svbio.cloudkeeper.model.bare.element.module.BareModule;
import com.svbio.cloudkeeper.model.util.ImmutableList;
import scala.concurrent.ExecutionContext;

import java.util.Objects;

final class CloudKeeperEnvironmentImpl implements CloudKeeperEnvironment {
    private final ExecutionContext executionContext;
    private final String instanceProviderActorPath;
    private final InstanceProvider instanceProvider;
    private final InterpreterPropsProvider interpreterPropsProvider;
    private final StagingAreaProvider stagingAreaProvider;
    private final ActorRef administrator;
    private final ActorRef masterInterpreter;
    private final ActorRef executor;
    private final ImmutableList<EventSubscription> eventSubscriptions;
    private final boolean cleaningRequested;
    private final boolean retrieveResults;
    private final Timeout remoteAskTimeout;
    private final Timeout localAskTimeout;

    CloudKeeperEnvironmentImpl(ExecutionContext executionContext, String instanceProviderActorPath,
            InstanceProvider instanceProvider, InterpreterPropsProvider interpreterPropsProvider,
            StagingAreaProvider stagingAreaProvider, ActorRef administrator, ActorRef masterInterpreter,
            ActorRef executor, ImmutableList<EventSubscription> eventSubscriptions, boolean cleaningRequested,
            boolean retrieveResults, Timeout remoteAskTimeout, Timeout localAskTimeout) {
        this.executionContext = Objects.requireNonNull(executionContext);
        this.instanceProviderActorPath = Objects.requireNonNull(instanceProviderActorPath);
        this.instanceProvider = Objects.requireNonNull(instanceProvider);
        this.interpreterPropsProvider = Objects.requireNonNull(interpreterPropsProvider);
        this.stagingAreaProvider = Objects.requireNonNull(stagingAreaProvider);
        this.administrator = Objects.requireNonNull(administrator);
        this.masterInterpreter = Objects.requireNonNull(masterInterpreter);
        this.executor = Objects.requireNonNull(executor);
        this.eventSubscriptions = Objects.requireNonNull(eventSubscriptions);
        this.cleaningRequested = cleaningRequested;
        this.retrieveResults = retrieveResults;
        this.remoteAskTimeout = Objects.requireNonNull(remoteAskTimeout);
        this.localAskTimeout = Objects.requireNonNull(localAskTimeout);
    }

    ExecutionContext getExecutionContext() {
        return executionContext;
    }

    String getInstanceProviderActorPath() {
        return instanceProviderActorPath;
    }

    InstanceProvider getInstanceProvider() {
        return instanceProvider;
    }

    InterpreterPropsProvider getInterpreterPropsProvider() {
        return interpreterPropsProvider;
    }

    StagingAreaProvider getStagingAreaProvider() {
        return stagingAreaProvider;
    }

    ActorRef getAdministrator() {
        return administrator;
    }

    ActorRef getMasterInterpreter() {
        return masterInterpreter;
    }

    ActorRef getExecutor() {
        return executor;
    }

    ImmutableList<EventSubscription> getEventSubscriptions() {
        return eventSubscriptions;
    }

    boolean isCleaningRequested() {
        return cleaningRequested;
    }

    boolean isRetrieveResults() {
        return retrieveResults;
    }

    Timeout getRemoteAskTimeout() {
        return remoteAskTimeout;
    }

    Timeout getLocalAskTimeout() {
        return localAskTimeout;
    }

    @Override
    public WorkflowExecutionBuilder newWorkflowExecutionBuilder(BareModule module) {
        Objects.requireNonNull(module);

        return new WorkflowExecutionBuilderImpl(this, module);
    }
}
