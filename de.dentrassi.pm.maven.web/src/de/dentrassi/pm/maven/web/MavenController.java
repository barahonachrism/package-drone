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
package de.dentrassi.pm.maven.web;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import de.dentrassi.osgi.web.Controller;
import de.dentrassi.osgi.web.LinkTarget;
import de.dentrassi.osgi.web.ModelAndView;
import de.dentrassi.osgi.web.RequestMapping;
import de.dentrassi.osgi.web.ViewResolver;
import de.dentrassi.osgi.web.controller.ControllerInterceptor;
import de.dentrassi.osgi.web.controller.binding.PathVariable;
import de.dentrassi.pm.common.web.InterfaceExtender;
import de.dentrassi.pm.common.web.Modifier;
import de.dentrassi.pm.common.web.menu.MenuEntry;
import de.dentrassi.pm.sec.web.controller.HttpContraintControllerInterceptor;
import de.dentrassi.pm.sec.web.controller.SecuredControllerInterceptor;
import de.dentrassi.pm.storage.channel.ChannelInformation;
import de.dentrassi.pm.storage.channel.ChannelService;
import de.dentrassi.pm.storage.channel.ChannelService.By;
import de.dentrassi.pm.storage.channel.ReadableChannel;
import de.dentrassi.pm.storage.web.utils.Channels;
import de.dentrassi.pm.system.SitePrefixService;

@Controller
@ViewResolver ( "/WEB-INF/views/%s.jsp" )
@ControllerInterceptor ( SecuredControllerInterceptor.class )
@ControllerInterceptor ( HttpContraintControllerInterceptor.class )
public class MavenController implements InterfaceExtender
{
    private ChannelService service;

    private SitePrefixService sitePrefixService;

    public void setService ( final ChannelService service )
    {
        this.service = service;
    }

    public void setSitePrefixService ( final SitePrefixService sitePrefixService )
    {
        this.sitePrefixService = sitePrefixService;
    }

    @Override
    public List<MenuEntry> getActions ( final HttpServletRequest request, final Object object )
    {
        if ( object instanceof ChannelInformation )
        {
            final List<MenuEntry> result = new LinkedList<> ();

            final ChannelInformation channel = (ChannelInformation)object;
            if ( channel.hasAspect ( "maven.repo" ) )
            {
                result.add ( new MenuEntry ( "Maven Repository", 20_000, new LinkTarget ( "/maven/" + channel.getId () ), Modifier.LINK, null ) );
            }

            return result;
        }

        return null;
    }

    @Override
    public List<MenuEntry> getViews ( final HttpServletRequest request, final Object object )
    {
        if ( object instanceof ChannelInformation )
        {
            final List<MenuEntry> result = new LinkedList<> ();

            final ChannelInformation channel = (ChannelInformation)object;
            final Map<String, String> model = new HashMap<> ( 1 );
            model.put ( "channelId", channel.getId () );
            result.add ( new MenuEntry ( "Help", Integer.MAX_VALUE, "Maven", 2_000, LinkTarget.createFromController ( MavenController.class, "help" ).expand ( model ), Modifier.LINK, "info-sign" ) );

            return result;
        }

        return null;
    }

    @RequestMapping ( "/channel/{channelId}/help.maven" )
    public ModelAndView help ( @PathVariable ( "channelId" ) final String channelId)
    {
        return Channels.withChannel ( this.service, channelId, ReadableChannel.class, channel -> {

            final Map<String, Object> model = new HashMap<> ();

            model.put ( "mavenRepo", channel.hasAspect ( "maven.repo" ) );
            model.put ( "channel", channel.getInformation () );
            model.put ( "deployGroups", this.service.getChannelDeployGroups ( By.id ( channelId ) ).orElse ( Collections.emptyList () ) );
            model.put ( "sitePrefix", this.sitePrefixService.getSitePrefix () );

            return new ModelAndView ( "helpMaven", model );
        } );
    }
}
