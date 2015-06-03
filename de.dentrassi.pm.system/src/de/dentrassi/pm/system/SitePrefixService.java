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
package de.dentrassi.pm.system;

import de.dentrassi.pm.core.CoreService;

/**
 * A service which provides the site prefix to use
 * <p>
 * This is convenience service which uses the {@link SystemService} and the
 * {@link CoreService} in combination to provide either a detected or
 * configured site prefix.
 */
public interface SitePrefixService
{
    /**
     * Get the site prefix
     *
     * @return the site prefix
     */
    public String getSitePrefix ();
}
