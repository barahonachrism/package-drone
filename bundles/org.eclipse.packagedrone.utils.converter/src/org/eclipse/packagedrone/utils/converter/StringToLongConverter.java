/*******************************************************************************
 * Copyright (c) 2014 IBH SYSTEMS GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package org.eclipse.packagedrone.utils.converter;

public class StringToLongConverter implements Converter
{
    public static final StringToLongConverter INSTANCE = new StringToLongConverter ();

    @Override
    public boolean canConvert ( final Class<?> from, final Class<?> to )
    {
        if ( from.equals ( String.class ) && to.equals ( Long.class ) )
        {
            return true;
        }
        return false;
    }

    @Override
    public Long convertTo ( final Object value, final Class<?> clazz )
    {
        if ( value == null )
        {
            return null;
        }

        try
        {
            final String str = value.toString ();
            if ( str.isEmpty () )
            {
                return null;
            }

            return Long.parseLong ( value.toString () );
        }
        catch ( final NumberFormatException e )
        {
            throw new ConversionException ( String.format ( "'%s' is not a number", value ) );
        }
    }
}
