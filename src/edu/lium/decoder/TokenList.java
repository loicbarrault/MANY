/*
 * Copyright 2009 Loic BARRAULT.  
 * Portions Copyright BBN and UMD (see LICENSE_TERP.txt).  
 * Portions Copyright 1999-2008 CMU (see LICENSE_SPHINX4.txt).
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "LICENSE.txt" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */
package edu.lium.decoder;

import java.util.ArrayList;

public class TokenList extends ArrayList<Token>
{
	private static final long serialVersionUID = -2555724904548780856L;
	
	@Override
	protected void removeRange(int fromIndex, int toIndex)
	{
		super.removeRange(fromIndex, toIndex);
	}
}
