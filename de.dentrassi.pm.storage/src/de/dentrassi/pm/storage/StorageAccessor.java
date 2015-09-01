/*******************************************************************************
 * Copyright (c) 2014, 2015 IBH SYSTEMS GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package de.dentrassi.pm.storage;

import java.util.Set;

import de.dentrassi.pm.common.ArtifactInformation;

public interface StorageAccessor
{
    @Deprecated
    public void updateChannel ( String channelId, String name, String description );

    public Set<ArtifactInformation> getArtifacts ( String channelId );
}
