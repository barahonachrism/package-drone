/*******************************************************************************
 * Copyright (c) 2015 IBH SYSTEMS GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package de.dentrassi.pm.aspect.cleanup;

import de.dentrassi.pm.aspect.cleanup.CleanupTester.Action;
import de.dentrassi.pm.storage.channel.ArtifactInformation;

public class ResultEntry
{
    private final ArtifactInformation artifact;

    private final Action action;

    public ResultEntry ( final ArtifactInformation artifact, final Action action )
    {
        this.artifact = artifact;
        this.action = action;
    }

    public Action getAction ()
    {
        return this.action;
    }

    public ArtifactInformation getArtifact ()
    {
        return this.artifact;
    }
}
