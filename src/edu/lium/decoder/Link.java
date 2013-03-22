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

public class Link
{
	Node endNode;
	public int getWordId(){ return wordId; }
	public Node getEndNode(){ return endNode; }
	public float getPosterior(){ return posterior; }
	
	Node startNode;
	float probability; // probability of the word (not normalized and not posterior)
	float posterior; // variable set by the forward-backward algorithm
	int wordId;
	int id;
	ArrayList<Integer> sysids; // the set of system which proposed the word on this link
	
	public Link(Node from, Node target, float prob, float post, int word, ArrayList<Integer> sysids, int i)
	{
		this.startNode = from;
		this.endNode = target;
		this.probability = prob;
		this.posterior = post;
		this.wordId = word;
		if(sysids == null)
			this.sysids = new ArrayList<Integer>();
		else
			this.sysids = new ArrayList<Integer>(sysids);
		this.id = i;
		from.nextLinks.add(this);
		target.backLinks.add(this);
	}
	
	/*public Link(Node from, Node target, float prob, float post, int word, int i)
	{
		this(from, target, prob, prob, word, -1, i);
	}
	
	public Link(Node from, Node target, float prob, int word, int i)
	{
		this(from, target, prob, prob, word, i);
	}*/
    
	public Link(Link l)
	{
		this(l.startNode, l.endNode, l.probability, l.posterior, l.wordId, l.sysids, l.id);
	}
	
	@Override
	public String toString()
	{
		String s = "["+startNode.time+","+endNode.time+" : w="+wordId+", p="+probability+"]";
		return s;
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if((obj instanceof Link) == false)
			return false;
		
		boolean error = false;
		Link l = (Link) obj;
		
		if(probability != l.probability) 
		{
			error = true;
			System.err.println("*** orig.probability="+probability+" vs new.probability"+l.probability+"  [BAD]");
		}
		
		if(posterior != l.posterior) 
		{
			error = true;
			System.err.println("*** orig.posterior="+posterior+" vs new.posterior"+l.posterior+"  [BAD]");
		}
		
		if(wordId != l.wordId)
		{
			error = true;
			System.err.println("*** orig.wordId="+wordId+" vs new.wordId"+l.wordId+"  [BAD]");
		}
		
		if(sysids.get(0) != l.sysids.get(0))
		{
			error = true;
			System.err.println("*** orig.sysId="+sysids.get(0)+" vs new.sysId"+l.sysids.get(0)+"  [BAD]");
		}
		
		if(id != l.id)
		{
			error = true;
			System.err.println("*** orig.id="+id+" vs new.id"+l.id+"  [BAD]");
		}
		
		if(startNode.id != l.startNode.id || startNode.time != l.startNode.time)
			error = true;
		if(endNode.id != l.endNode.id || endNode.time != l.endNode.time)
			error = true;
		
		return !error;
	}
	
	public void addSysIDs(ArrayList<Integer> ids)
	{
		for (Integer i : ids)
		{
			if(!sysids.contains(i))
			{
				sysids.add(i);
			}
		}
		
	}
	
	
}
