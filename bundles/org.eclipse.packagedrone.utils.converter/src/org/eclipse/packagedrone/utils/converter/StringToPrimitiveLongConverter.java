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
package org.eclipse.packagedrone.utils.converter;

public class StringToPrimitiveLongConverter implements Converter
{
    public static final StringToPrimitiveLongConverter INSTANCE = new StringToPrimitiveLongConverter ();

    @Override
    public boolean canConvert ( final Class<?> from, final Class<?> to )
    {
        if ( from.equals ( String.class ) && to.equals ( long.class ) )
        {
            return true;
        }
        return false;
    }

    @Override
    public Object convertTo ( final Object value, final Class<?> clazz )
    {
        if ( value == null )
        {
            return null;
        }

        final String str = value.toString ();

        return Long.parseLong ( str );
    }
}
