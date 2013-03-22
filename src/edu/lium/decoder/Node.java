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

import java.util.Vector;

public class Node
{
	public float time;
	public int id;
	
	public Vector<Link> backLinks;
	public Vector<Link> nextLinks;
	
	public Node(int i, float t)
	{
		id = i;
		time = t;
		backLinks = new Vector<Link>();
		nextLinks = new Vector<Link>();
	}
	
	public boolean equals(Node n)
	{
		boolean error = false;
		if(n.id != id)
		{
			error = true;
			System.err.println("*** orig.id="+id+" vs new.id"+n.id+"  [BAD]");
		}
		
		if(n.time != time)
		{
			error = true;
			System.err.println("*** orig.time="+time+" vs new.time"+n.time+"  [BAD]");
		}
		
		if(n.nextLinks.size() != nextLinks.size())
		{
			error = true;
			System.err.println("*** orig.nextLinks.size()="+nextLinks.size()+" vs new.nextLinks.size()"+n.nextLinks.size()+"  [BAD]");
		}
		
		if(n.backLinks.size() != backLinks.size())
		{
			error = true;
			System.err.println("*** orig.backLinks.size()="+backLinks.size()+" vs new.backLinks.size()"+n.backLinks.size()+"  [BAD]");
		}
		
		/*System.err.println("*** --- Checking nextLinks: ");
		for(int i=0; i<nextLinks.size(); i++)
		{
			if(nextLinks.get(i).equals(n.nextLinks.get(i)) == false)
				error = true;
		}
		System.err.println("*** --- Checking backLinks: ");
		for(int i=0; i<backLinks.size(); i++)
		{
			if(backLinks.get(i).equals(n.backLinks.get(i)) == false)
				error = true;
		}*/
		
		return !error;
	}
	
	@Override
	public String toString()
	{
		return "Node id="+id+" time="+time+" backLinks.size()="+backLinks.size()+" nextLinks.size()="+nextLinks.size();
	}
	
}
