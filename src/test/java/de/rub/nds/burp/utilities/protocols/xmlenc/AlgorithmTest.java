/**
 * EsPReSSO - Extension for Processing and Recognition of Single Sign-On Protocols.
 * Copyright (C) 2015 Tim Guenther and Christian Mainka
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package de.rub.nds.burp.utilities.protocols.xmlenc;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 */
public class AlgorithmTest {

    @Test
    public void testList() {
        String[] uris = SymmetricAlgorithm.getURIs();
        assertNotNull(uris);
        assertNotNull(SymmetricAlgorithm.getByURI(uris[0]));
        uris = AsymmetricAlgorithm.getURIs();
        assertNotNull(uris);
        assertNotNull(AsymmetricAlgorithm.getByURI(uris[0]));
    }

}
