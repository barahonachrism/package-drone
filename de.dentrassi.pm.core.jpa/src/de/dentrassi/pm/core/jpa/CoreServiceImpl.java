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
package de.dentrassi.pm.core.jpa;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import de.dentrassi.pm.common.MetaKey;
import de.dentrassi.pm.common.service.AbstractJpaServiceImpl;
import de.dentrassi.pm.core.CoreService;
import de.dentrassi.pm.storage.jpa.GlobalPropertyEntity;

public class CoreServiceImpl extends AbstractJpaServiceImpl implements CoreService
{

    @Override
    public String getCoreProperty ( final String key, final String defaultValue )
    {
        return doWithTransaction ( ( em ) -> {
            final GlobalPropertyEntity pe = em.find ( GlobalPropertyEntity.class, key );
            if ( pe == null )
            {
                return defaultValue;
            }
            return pe.getValue ();
        } );
    }

    @Override
    public String getCoreProperty ( final String key )
    {
        return getCoreProperty ( key, null );
    }

    @Override
    public void setProperties ( final Map<String, String> properties )
    {
        doWithTransactionVoid ( ( em ) -> {
            properties.forEach ( ( key, value ) -> internalSet ( em, key, value ) );
        } );
    }

    @Override
    public void setCoreProperty ( final String key, final String value )
    {
        doWithTransactionVoid ( ( em ) -> {
            internalSet ( em, key, value );
        } );
    }

    protected void internalSet ( final EntityManager em, final String key, final String value )
    {
        GlobalPropertyEntity pe = em.find ( GlobalPropertyEntity.class, key );
        if ( value == null )
        {
            // delete
            if ( pe != null )
            {
                em.remove ( pe );
            }
        }
        else
        {
            if ( pe != null )
            {
                pe.setValue ( value );
            }
            else
            {
                pe = new GlobalPropertyEntity ();
                pe.setKey ( key );
                pe.setValue ( value );
            }
            em.persist ( pe );
        }
    }

    @Override
    public SortedMap<MetaKey, String> list ()
    {
        final SortedMap<MetaKey, String> result = new TreeMap<> ();

        doWithTransactionVoid ( ( em ) -> {
            final TypedQuery<GlobalPropertyEntity> q = em.createQuery ( String.format ( "select gp from %s as gp", GlobalPropertyEntity.class.getName () ), GlobalPropertyEntity.class );

            for ( final GlobalPropertyEntity gp : q.getResultList () )
            {
                result.put ( new MetaKey ( "core", gp.getKey () ), gp.getValue () );
            }
        } );

        return result;
    }
}
