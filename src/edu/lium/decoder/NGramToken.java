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

import java.util.HashMap;
import java.util.Map.Entry;
import com.bbn.mt.terp.BLEUcounts;

public class NGramToken extends Token
{
	public HashMap<NGram, Integer> all_ngrams = null;
	public HashMap<NGram, Integer> previous_ngrams = null;
	public HashMap<NGram, Integer> new_ngrams = null;
	
	public NGramToken(Token pred, Node node)
	{
		super(pred, node, null, null, 0.0f, 0, 0, null);
		all_ngrams = new HashMap<NGram, Integer>();
		previous_ngrams = new HashMap<NGram, Integer>();
		new_ngrams = new HashMap<NGram, Integer>();
	}
	
	public void addNGram(NGram ngram)
	{
		if(all_ngrams.containsKey(ngram) == false)
		{
			all_ngrams.put(ngram, 1);
		}
		else
		{
			all_ngrams.put(ngram, all_ngrams.get(ngram)+1);
		}
	}
	
	public void addNewNGram(NGram ngram)
	{
		if(new_ngrams.containsKey(ngram) == false) //add this ngram for further extension
			new_ngrams.put(ngram, 1);
	}
	
	/*public void maybeAddNGram(NGram ngram, HashMap<NGram, Integer> refNgrams)
	{
		//if ngram is not in the ref, then ngram+word can't be in the ref, 
		//if(refNgrams.containsKey(ngram))  
		{
			addNGram(ngram);
		}
	}*/
	
	/**
	 * extends all previous_ngrams and put them in new_ngrams
	 * @param ws
	 */
	/*public void extendNGrams(String ws)
	{
		//System.err.println("extendNGrams START : "+previous_ngrams);
		for(Entry<NGram, Integer> entry : previous_ngrams.entrySet())
		{
			//extends ngrams
			if(entry.getKey().size() < BLEUcounts.max_ngram_size)
			{
				//logger.info("extending ngram "+ngram+" with word "+ws);
				NGram ng = new NGram(entry.getKey(), ws);
				
				if(all_ngrams.containsKey(ng) == false)
					all_ngrams.put(ng, 1);
				else
					all_ngrams.put(ng, all_ngrams.get(ng)+1);
				
				if(new_ngrams.containsKey(ng) == false)
					new_ngrams.put(ng, 1);
				else
					new_ngrams.put(ng, new_ngrams.get(ng)+1); 
			}
		}
		//System.err.println("extendNGrams END ");
	}*/
	
	public void extendUsefulNGrams(String ws)
	{
		//System.err.println("extendUsefulNGrams START : "+previous_ngrams);
		for(Entry<NGram, Integer> entry : previous_ngrams.entrySet())
		{
			//extends ngrams
			if(entry.getKey().size() < BLEUcounts.max_ngram_size)
			{
				//logger.info("extending ngram "+ngram+" with word "+ws);
				NGram ng = new NGram(entry.getKey(), ws);
				
				addNGram(ng); //add this ngram to the list of all ngrams
				addNewNGram(ng);
			}
		}
		//System.err.println("extendUsefulNGrams END ");
	}
	
	public void spreadNGramsOverNullTransition()
	{
		//System.err.println("spreadNGramsOverNullTransition START : "+previous_ngrams);
		for(Entry<NGram, Integer> entry : previous_ngrams.entrySet())
		{
			//spread ngrams
			//logger.info("spreading ngram "+ngram+" over NULL transition ");
			addNewNGram(entry.getKey());
			/*else
				previous_ngrams.put(ngram, previous_ngrams.get(ngram)+1);*/
		}
		//System.err.println("spreadNGramsOverNullTransition END ");
	}
	
	/**
	 * 
	 * @param node
	 */
	public void goToNode(Node node)
	{
		this.node = node;
		previous_ngrams = new_ngrams;
		new_ngrams = new HashMap<NGram, Integer>();
	}



	
	
}
