package com.svbio.cloudkeeper.contracts;

import com.svbio.cloudkeeper.examples.modules.Fibonacci;
import com.svbio.cloudkeeper.model.api.staging.InstanceProvider;
import com.svbio.cloudkeeper.model.api.staging.StagingArea;
import com.svbio.cloudkeeper.model.api.staging.StagingAreaProvider;
import com.svbio.cloudkeeper.model.immutable.execution.ExecutionTrace;
import com.svbio.cloudkeeper.model.runtime.execution.RuntimeAnnotatedExecutionTrace;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import scala.concurrent.Future;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;

/**
 * Contract test for remote staging-provider functionality.
 *
 * <p>This test does <em>not</em> include testing the basic {@link StagingAreaContract}.
 * Users of this contract test should always make sure that the basic test passes first.
 */
public final class RemoteStagingAreaContract {
    private final StagingAreaContractProvider stagingAreaContractProvider;
    private final InstanceProvider specificInstanceProvider;
    @Nullable private StagingAreaContractHelper helper;
    @Nullable private InstanceProvider instanceProvider;

    /**
     * Constructor.
     *
     * @param stagingAreaContractProvider staging-area factory
     * @param specificInstanceProvider Instance provider that needs to provide staging-area specific interfaces/classes.
     */
    public RemoteStagingAreaContract(StagingAreaContractProvider stagingAreaContractProvider,
            InstanceProvider specificInstanceProvider) {
        this.stagingAreaContractProvider = Objects.requireNonNull(stagingAreaContractProvider);
        this.specificInstanceProvider = Objects.requireNonNull(specificInstanceProvider);
    }

    @BeforeClass
    public void setup() {
        stagingAreaContractProvider.preContract();

        helper = new StagingAreaContractHelper(stagingAreaContractProvider, Fibonacci.class);
        instanceProvider = helper.getInstanceProvider(specificInstanceProvider);
    }

    private <T> T await(Future<T> awaitable) throws Exception {
        return stagingAreaContractProvider.await(awaitable);
    }

    @Test
    public void testSerialization() throws Exception {
        assert helper != null && instanceProvider != null;

        StagingArea sumStaging = helper
            .createStagingArea("testSerialization")
            .resolveDescendant(ExecutionTrace.valueOf("/loop/sum"));
        RuntimeAnnotatedExecutionTrace executionTrace = sumStaging.getAnnotatedExecutionTrace();
        await(sumStaging.putObject(ExecutionTrace.valueOf(":in:num1"), 2));
        await(sumStaging.putObject(ExecutionTrace.valueOf(":in:num2"), 3));
        await(sumStaging.putObject(ExecutionTrace.valueOf(":out:sum"), 5));

        StagingAreaProvider freshStagingAreaProvider = sumStaging.getStagingAreaProvider();

        byte[] byteArray;
        try (
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)
        ) {
            objectOutputStream.writeObject(freshStagingAreaProvider);
            byteArray = byteArrayOutputStream.toByteArray();
        }

        StagingAreaProvider deserializedProvider;
        try (
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArray);
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)
        ) {
            deserializedProvider = (StagingAreaProvider) objectInputStream.readObject();
        }

        StagingArea reconstructedStaging
            = deserializedProvider.provideStaging(helper.getRuntimeContext(), executionTrace, instanceProvider);

        Assert.assertEquals(
            await(reconstructedStaging.getObject(ExecutionTrace.valueOf(":out:sum"))),
            (Integer) await(reconstructedStaging.getObject(ExecutionTrace.valueOf(":in:num1")))
            + (Integer) await(reconstructedStaging.getObject(ExecutionTrace.valueOf(":in:num2")))
        );
    }
}
