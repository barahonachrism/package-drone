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
package org.eclipse.packagedrone.repo.importer;

import org.eclipse.packagedrone.web.LinkTarget;

public interface ImporterDescription
{
    public String getId ();

    public String getLabel ();

    public String getDescription ();

    public LinkTarget getConfigurationTarget ();
}
