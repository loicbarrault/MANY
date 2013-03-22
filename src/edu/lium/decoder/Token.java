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

import edu.cmu.sphinx.linguist.WordSequence;

public class Token implements Comparable<Token>
{
	float score;
	Token pred;
	Node node;
	WordSequence history;
	WordSequence lmhistory;
	
	float lm_score;
	int nb_words;
	int nb_nulls;
	int[] word_by_sys;
	
	public Token(){}
	
	/*public Token(float score, Token pred, Node node, WordSequence history, WordSequence lmhistory, float lm_score, int nb_words, int nb_nulls, int[] word_by_sys)
	{
		this.score = score;
		this.pred = pred;
		this.node = node;
		this.history = history;
		this.lmhistory = lmhistory;
		
		this.lm_score = lm_score;
		this.nb_words = nb_words;
		this.nb_nulls = nb_nulls;
		this.word_by_sys = word_by_sys;
	}*/
	
	public Token(Token pred, Node node, WordSequence history, WordSequence lmhistory, float lm_score, int nb_words, int nb_nulls, int[] word_by_sys)
	{
		this.pred = pred;
		this.node = node;
		this.history = history;
		this.lmhistory = lmhistory;
		
		this.lm_score = lm_score;
		this.nb_words = nb_words;
		this.nb_nulls = nb_nulls;
		this.word_by_sys = word_by_sys;
		
	}
	
	
	
	public Node getNode() { return node;}
	public float getScore() { return score;}
	public void setScore(float score) { this.score = score;}
	public WordSequence getHistory() { return history;}
	
	public int compareTo(Token tok)
	{
		if(score > tok.score)
			return 1;
		else if(score < tok.score)
			return -1;
		return 0;
	};
}
