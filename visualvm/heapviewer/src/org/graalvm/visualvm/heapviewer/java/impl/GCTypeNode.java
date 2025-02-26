/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.graalvm.visualvm.heapviewer.java.impl;

import java.awt.Font;
import org.graalvm.visualvm.heapviewer.java.InstancesContainer;
import org.graalvm.visualvm.heapviewer.model.DataType;
import org.graalvm.visualvm.heapviewer.ui.HeapViewerRenderer;
import org.graalvm.visualvm.lib.profiler.api.icons.Icons;
import org.graalvm.visualvm.lib.profiler.api.icons.ProfilerIcons;
import org.graalvm.visualvm.lib.ui.swing.renderer.LabelRenderer;
import org.openide.util.NbBundle;

/**
 *
 * @author Jiri Sedlacek
 */
@NbBundle.Messages({
    "ClassesContainer_MoreNodes=<another {0} GC roots left>",
    "ClassesContainer_SamplesContainer=<sample {0} GC roots>",
    "ClassesContainer_NodesContainer=<GC roots {0}-{1}>"
})
class GCTypeNode extends InstancesContainer.Objects {
    
    GCTypeNode(String name) {
        super(name, DataType.CLASS.getUnsupportedValue());
    }
    
    
    protected String getMoreNodesString(String moreNodesCount)  {
        return Bundle.ClassesContainer_MoreNodes(moreNodesCount);
    }
    
    protected String getSamplesContainerString(String objectsCount)  {
        return Bundle.ClassesContainer_SamplesContainer(objectsCount);
    }
    
    protected String getNodesContainerString(String firstNodeIdx, String lastNodeIdx)  {
        return Bundle.ClassesContainer_NodesContainer(firstNodeIdx, lastNodeIdx);
    }
    
    
    static class Renderer extends LabelRenderer implements HeapViewerRenderer {
        
        Renderer() {
            setIcon(Icons.getIcon(ProfilerIcons.RUN_GC));
            setFont(getFont().deriveFont(Font.BOLD));
        }
        
    }
    
}
