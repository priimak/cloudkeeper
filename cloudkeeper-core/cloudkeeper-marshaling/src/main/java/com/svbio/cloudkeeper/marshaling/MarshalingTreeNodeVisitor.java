package com.svbio.cloudkeeper.marshaling;

import com.svbio.cloudkeeper.marshaling.MarshalingTreeNode.ByteSequenceNode;
import com.svbio.cloudkeeper.marshaling.MarshalingTreeNode.MarshaledObjectNode;
import com.svbio.cloudkeeper.marshaling.MarshalingTreeNode.MarshaledReplacementObjectNode;
import com.svbio.cloudkeeper.marshaling.MarshalingTreeNode.RawObjectNode;

import javax.annotation.Nullable;

/**
 * Visitor of marshaling-tree nodes, in the style of the visitor design pattern.
 */
public interface MarshalingTreeNodeVisitor<T, P> {
    /**
     * Visits an object node.
     */
    @Nullable
    T visitRawObjectNode(RawObjectNode node, @Nullable P parameter);

    /**
     * Visits a marshaled-object node.
     */
    @Nullable
    T visitMarshaledObjectNode(MarshaledObjectNode node, @Nullable P parameter);

    /**
     * Visits a marshaled-object node.
     */
    @Nullable
    T visitMarshaledReplacementNode(MarshaledReplacementObjectNode node, @Nullable P parameter);

    /**
     * Visits a byte-sequence node.
     */
    @Nullable
    T visitByteSequenceNode(ByteSequenceNode node, @Nullable P parameter);
}
