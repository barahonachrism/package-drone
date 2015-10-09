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
package org.eclipse.packagedrone.repo.signing.pgp.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyPacket;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.eclipse.packagedrone.repo.signing.SigningService;
import org.eclipse.packagedrone.repo.signing.pgp.PgpHelper;

public class PgpSigningService implements SigningService
{
    private final PGPSecretKey secretKey;

    private final PGPPrivateKey privateKey;

    public static PgpSigningService create ( final File file, final String keyId, final String passphrase ) throws IOException, PGPException
    {
        try ( InputStream is = new FileInputStream ( file ) )
        {
            return new PgpSigningService ( is, keyId, passphrase );
        }
    }

    public PgpSigningService ( final InputStream keyring, final String keyId, final String passphrase ) throws IOException, PGPException
    {
        this.secretKey = PgpHelper.loadSecretKey ( keyring, keyId );
        if ( this.secretKey == null )
        {
            throw new IllegalStateException ( String.format ( "Signing key '%08X' could not be found", keyId ) );
        }
        this.privateKey = this.secretKey.extractPrivateKey ( new BcPBESecretKeyDecryptorBuilder ( new BcPGPDigestCalculatorProvider () ).build ( passphrase.toCharArray () ) );
    }

    @Override
    public void printPublicKey ( final OutputStream out ) throws IOException
    {
        final ArmoredOutputStream armoredOutput = new ArmoredOutputStream ( out );
        final PublicKeyPacket pubKey = this.privateKey.getPublicKeyPacket ();
        pubKey.encode ( new BCPGOutputStream ( armoredOutput ) );
        armoredOutput.close ();
    }

    @Override
    public void sign ( final InputStream in, final OutputStream out, final boolean inline ) throws Exception
    {
        final int digest = HashAlgorithmTags.SHA1;
        final PGPSignatureGenerator signatureGenerator = new PGPSignatureGenerator ( new BcPGPContentSignerBuilder ( this.privateKey.getPublicKeyPacket ().getAlgorithm (), digest ) );
        signatureGenerator.init ( PGPSignature.BINARY_DOCUMENT, this.privateKey );

        final ArmoredOutputStream armoredOutput = new ArmoredOutputStream ( out );

        if ( inline )
        {
            armoredOutput.beginClearText ( digest );
        }

        final byte[] buffer = new byte[4096];

        int rc;
        while ( ( rc = in.read ( buffer ) ) >= 0 )
        {
            if ( inline )
            {
                armoredOutput.write ( buffer, 0, rc );
            }
            signatureGenerator.update ( buffer, 0, rc );
        }

        armoredOutput.endClearText ();

        final PGPSignature signature = signatureGenerator.generate ();
        signature.encode ( new BCPGOutputStream ( armoredOutput ) );

        armoredOutput.close ();
    }

}
