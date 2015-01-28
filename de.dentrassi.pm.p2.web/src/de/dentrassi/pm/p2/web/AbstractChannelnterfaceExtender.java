/*******************************************************************************
 * Copyright (c) 2015 Jens Reimann.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.pm.p2.web;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import de.dentrassi.pm.storage.Channel;
import de.dentrassi.pm.storage.web.InterfaceExtender;
import de.dentrassi.pm.storage.web.menu.MenuEntry;

public abstract class AbstractChannelnterfaceExtender implements InterfaceExtender
{
    @Override
    public List<MenuEntry> getActions ( final HttpServletRequest request, final Object object )
    {
        if ( object instanceof Channel )
        {
            return getChannelAction ( request, (Channel)object );
        }
        return null;
    }

    protected List<MenuEntry> getChannelAction ( final HttpServletRequest request, final Channel channel )
    {
        return null;
    }

    @Override
    public List<MenuEntry> getViews ( final HttpServletRequest request, final Object object )
    {
        if ( object instanceof Channel )
        {
            return getChannelViews ( request, (Channel)object );
        }
        return null;
    }

    protected List<MenuEntry> getChannelViews ( final HttpServletRequest request, final Channel channel )
    {
        return null;
    }
}
