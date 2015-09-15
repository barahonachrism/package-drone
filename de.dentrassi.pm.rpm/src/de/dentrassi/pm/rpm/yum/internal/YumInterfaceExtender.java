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
package de.dentrassi.pm.rpm.yum.internal;

import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;

import de.dentrassi.osgi.web.LinkTarget;
import de.dentrassi.pm.common.web.Modifier;
import de.dentrassi.pm.common.web.menu.MenuEntry;
import de.dentrassi.pm.rpm.Constants;
import de.dentrassi.pm.storage.AbstractChannelInterfaceExtender;
import de.dentrassi.pm.storage.channel.ChannelInformation;

public class YumInterfaceExtender extends AbstractChannelInterfaceExtender
{
    private static final Escaper PATH_ESC = UrlEscapers.urlPathSegmentEscaper ();

    @Override
    protected boolean filterChannel ( final ChannelInformation channel )
    {
        return channel.hasAspect ( Constants.YUM_ASPECT_ID );
    }

    @Override
    protected List<MenuEntry> getChannelActions ( final HttpServletRequest request, final ChannelInformation channel )
    {
        final List<MenuEntry> result = new LinkedList<> ();
        result.add ( new MenuEntry ( "YUM (by ID)", 6_000, new LinkTarget ( String.format ( "/yum/%s", channel.getId () ) ), Modifier.LINK, null ) );
        if ( channel.getName () != null )
        {
            result.add ( new MenuEntry ( "YUM (by name)", 6_000, new LinkTarget ( String.format ( "/yum/%s", PATH_ESC.escape ( channel.getName () ) ) ), Modifier.LINK, null ) );
        }
        return result;
    }

    @Override
    protected List<MenuEntry> getChannelViews ( final HttpServletRequest request, final ChannelInformation channel )
    {
        final List<MenuEntry> result = new LinkedList<> ();

        result.add ( new MenuEntry ( "Help", Integer.MAX_VALUE, "YUM", 6_000, new LinkTarget ( String.format ( "/ui/yum/help/%s", channel.getId () ) ), Modifier.DEFAULT, "info-sign" ) );

        return result;
    }

}
