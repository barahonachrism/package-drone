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
package org.eclipse.packagedrone.repo.importer.aether;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.packagedrone.utils.converter.JSON;

@JSON
public class ImportConfiguration
{
    private String repositoryUrl;

    private List<MavenCoordinates> coordinates = new LinkedList<> ();

    private boolean includeSources;

    private boolean allOptional;

    public void setRepositoryUrl ( final String repositoryUrl )
    {
        this.repositoryUrl = repositoryUrl;
    }

    public String getRepositoryUrl ()
    {
        return this.repositoryUrl;
    }

    public void setCoordinates ( final List<MavenCoordinates> coordinates )
    {
        this.coordinates = coordinates;
    }

    public List<MavenCoordinates> getCoordinates ()
    {
        return this.coordinates;
    }

    public void setIncludeSources ( final boolean includeSources )
    {
        this.includeSources = includeSources;
    }

    public boolean isIncludeSources ()
    {
        return this.includeSources;
    }

    public void setAllOptional ( final boolean allOptional )
    {
        this.allOptional = allOptional;
    }

    public boolean isAllOptional ()
    {
        return this.allOptional;
    }
}
