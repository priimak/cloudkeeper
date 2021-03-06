package com.svbio.cloudkeeper.model.runtime.execution;

import com.svbio.cloudkeeper.model.bare.execution.BareElementPatternTarget;

import javax.annotation.Nonnull;
import java.util.regex.Pattern;

public interface RuntimeElementPatternTarget extends RuntimeOverrideTarget, BareElementPatternTarget {
    @Override
    @Nonnull
    Pattern getPattern();
}
