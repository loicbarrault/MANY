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
package com.bbn.mt.terp;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

public abstract class TERutilities
{
	private TERutilities()
	{
	}

	public static String join(String[] s, String delimiter)
	{
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < s.length; ++i)
		{
			if (i > 0)
			{
				buffer.append(delimiter);
			}
			buffer.append(s[i]);
		}
		return buffer.toString();
	}

	public static String join(final String delimiter, final Comparable<String>[] objs)
	{
		if (objs == null)
			return "";
		if (objs.length == 0)
			return "";
		String delim = delimiter;
		if (delimiter == null)
			delim = "";

		StringBuilder buffer = new StringBuilder(String.valueOf(objs[0]));
		for (int i = 1; i < objs.length; i++)
		{
			buffer.append(delim).append(String.valueOf(objs[i]));
		}
		return buffer.toString();
	}

	public static <T> String join(final String delimiter, final Iterable<T> objs)
	{
		if (objs == null)
			return "";
		Iterator<T> iter = objs.iterator();
		if (!iter.hasNext())
			return "";
		String delim = delimiter;
		if (delimiter == null)
			delim = "";
		StringBuilder buffer = new StringBuilder(String.valueOf(iter.next()));
		while (iter.hasNext())
			buffer.append(delim).append(String.valueOf(iter.next()));
		return buffer.toString();
	}

	public static String join(String delimiter, String format, double[] arr)
	{
		if (arr == null)
			return "";
		String delim = delimiter;
		if (delim == null)
			delim = "";
		StringBuilder buffer = new StringBuilder(String.format(format, arr[0]));
		for (int i = 1; i < arr.length; i++)
		{
			buffer.append(delim).append(String.format(format, arr[i]));
		}
		return buffer.toString();
	}
	public static String join(String delimiter, double[] arr)
	{
		if (arr == null)
			return "";
		if (arr.length == 0)
			return "";
		String delim = delimiter;
		if (delimiter == null)
			delim = "";

		StringBuilder buffer = new StringBuilder(String.valueOf(arr[0]));
		for (int i = 1; i < arr.length; i++)
		{
			buffer.append(delim).append(String.valueOf(arr[i]));
		}
		return buffer.toString();
	}
	public static String join(String delimiter, Double[] arr)
	{
		if (arr == null)
			return "";
		if (arr.length == 0)
			return "";
		String delim = delimiter;
		if (delimiter == null)
			delim = "";

		StringBuilder buffer = new StringBuilder(String.valueOf(arr[0]));
		for (int i = 1; i < arr.length; i++)
		{
			buffer.append(delim).append(String.valueOf(arr[i]));
		}
		return buffer.toString();
	}
	public static String join(String delimiter, int[] arr)
	{
		if (arr == null)
			return "";
		if (arr.length == 0)
			return "";
		String delim = delimiter;
		if (delimiter == null)
			delim = "";

		StringBuilder buffer = new StringBuilder(String.valueOf(arr[0]));
		for (int i = 1; i < arr.length; i++)
		{
			buffer.append(delim).append(String.valueOf(arr[i]));
		}
		return buffer.toString();
	}
	public static String join(String delimiter, String format, float[] arr)
	{
		if (arr == null)
			return "";
		String delim = delimiter;
		if (delim == null)
			delim = "";
		StringBuilder buffer = new StringBuilder(String.format(format, arr[0]));
		for (int i = 1; i < arr.length; i++)
		{
			buffer.append(delim).append(String.format(format, arr[i]));
		}
		return buffer.toString();
	}
	public static String join(String delimiter, float[] arr)
	{
		if (arr == null)
			return "";
		if (arr.length == 0)
			return "";
		String delim = delimiter;
		if (delimiter == null)
			delim = "";

		StringBuilder buffer = new StringBuilder(String.valueOf(arr[0]));
		for (int i = 1; i < arr.length; i++)
		{
			buffer.append(delim).append(String.valueOf(arr[i]));
		}
		return buffer.toString();
	}

	public static String join(String delimiter, String format, Float[] arr)
	{
		if (arr == null)
			return "";
		String delim = delimiter;
		if (delim == null)
			delim = "";
		StringBuilder buffer = new StringBuilder(String.format(format, arr[0]));
		for (int i = 1; i < arr.length; i++)
		{
			buffer.append(delim).append(String.format(format, arr[i]));
		}
		return buffer.toString();
	}
	public static String join(String delimiter, Float[] arr)
	{
		if (arr == null)
			return "";
		if (arr.length == 0)
			return "";
		String delim = delimiter;
		if (delimiter == null)
			delim = "";

		StringBuilder buffer = new StringBuilder(String.valueOf(arr[0]));
		for (int i = 1; i < arr.length; i++)
		{
			buffer.append(delim).append(String.valueOf(arr[i]));
		}
		return buffer.toString();
	}

	/*
	 * public static String join(String delim, List<String> arr) { if (arr ==
	 * null) return ""; if (delim == null) delim = new String(""); String s =
	 * new String(""); for (int i = 0; i < arr.size(); i++) { if (i == 0) { s +=
	 * arr.get(i); } else { s += delim + arr.get(i); } } return s; }
	 */
	public static String join(String delimiter, char[] arr)
	{
		if (arr == null)
			return "";
		if (arr.length == 0)
			return "";
		String delim = delimiter;
		if (delimiter == null)
			delim = "";

		StringBuilder buffer = new StringBuilder(String.valueOf(arr[0]));
		for (int i = 1; i < arr.length; i++)
		{
			buffer.append(delim).append(String.valueOf(arr[i]));
		}
		return buffer.toString();
	}

	public static class Index_Score implements Comparable<Index_Score>
	{
		public int index = -1;
		public double score = 0.0;
		private Index_Score()
		{
		};
		public Index_Score(int idx, double sc)
		{
			index = idx;
			score = sc;
		}
		// @Override
		public int compareTo(Index_Score o)
		{
			if (score > o.score)
				return 1;
			if (score < o.score)
				return -1;
			return 0;
		}

		public String toString()
		{
			return "" + index + " (" + score + ")";
		}

	}

	public static String toCNString(ArrayList<ArrayList<Comparable<String>>> cn, ArrayList<ArrayList<Float>> cn_scores)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < cn.size(); i++)
		{
			ArrayList<Comparable<String>> mesh = cn.get(i);
			ArrayList<Float> sc = cn_scores.get(i);
			sb.append("align " + i + " ");

			for (int j = 0; j < mesh.size(); j++)
			{
				sb.append((String) mesh.get(j)).append(" ").append(sc.get(j)).append(" ");
			}
			sb.append("\n");
		}
		return sb.toString();
	}

	public static void generateParams(String paramsFile, String outFile, String backboneFile, String backboneScoresFile, int backboneIndex,
			ArrayList<String> hypsFiles, ArrayList<String> hypsScoresFiles, int[] hypsIndex, 
			float[] costs, float[] sysWeights,	String shift_constraint, 
			String wordnet, String shift_word_stop_list, String paraphrases, 
			boolean porter_stemmer, boolean case_sensitive, boolean create_cn)
	{
		StringBuilder sb = new StringBuilder();

		sb.append("Reference File (filename)                : ").append(backboneFile);
		if (backboneScoresFile != null)
			sb.append("\nReference Scores File (filename)         : ").append(backboneScoresFile);
		sb.append("\nHypothesis Files (list)                  : ").append(TERutilities.join(" ", hypsFiles));
		sb.append("\nHypothesis Scores Files (list)           : ").append(TERutilities.join(" ", hypsScoresFiles));
		sb.append("\nOutput Prefix (filename)                 : ").append(outFile);
		sb.append("\nDefault Deletion Cost (float)            : ").append(costs[0]);
		sb.append("\nDefault Stem Cost (float)                : ").append(costs[1]);
		sb.append("\nDefault Synonym Cost (float)             : ").append(costs[2]);
		sb.append("\nDefault Insertion Cost (float)           : ").append(costs[3]);
		sb.append("\nDefault Substitution Cost (float)        : ").append(costs[4]);
		sb.append("\nDefault Match Cost (float)               : ").append(costs[5]);
		sb.append("\nDefault Shift Cost (float)               : ").append(costs[6]);

		sb.append("\nOutput Formats (list)                    : ").append("cn param");
		
		if(create_cn)
			sb.append("\nCreate confusion Network (boolean)       : ").append("true");
		else
			sb.append("\nCreate confusion Network (boolean)       : ").append("false");
		
		if(case_sensitive)
			sb.append("\nCase Sensitive (boolean)                 : ").append("true");
		else
			sb.append("\nCase Sensitive (boolean)                 : ").append("false");

		if (shift_constraint != null)
		{
			sb.append("\nShift Constraint (string)                : ").append(shift_constraint);
			if ("relax".equals(shift_constraint))
			{
				if (porter_stemmer)
					sb.append("\nUse Porter Stemming (boolean)            : ").append("true");
				else
					sb.append("\nUse Porter Stemming (boolean)            : ").append("false");

				if (wordnet != null && !"".equals(wordnet))
				{
					sb.append("\nUse WordNet Synonymy (boolean)           : ").append("true");
					sb.append("\nWordNet Database Directory (filename)    : ").append(wordnet);
				}
				if (paraphrases != null && !"".equals(paraphrases))
					sb.append("\nPhrase Database (filename)               : ").append(paraphrases);
				
				sb.append("\nShift Stop Word List (string)            : ").append(shift_word_stop_list);
			}
		}
		

		if (backboneIndex >= 0)
			sb.append("\nReference Index (integer)                : ").append(backboneIndex);
		if (hypsIndex != null)
			sb.append("\nHypotheses Indexes (integer list)        : ").append(TERutilities.join(" ", hypsIndex));
		if (sysWeights != null)
			sb.append("\nSystems weights (double list)            : ").append(TERutilities.join(" ", sysWeights));

		BufferedWriter outWriter = null;
		if (paramsFile != null)
		{
			try
			{
				// outWriter = new PrintStream(paramsFile, "ISO8859_1");
				// outWriter = new PrintStream(paramsFile, "UTF-8");
				outWriter = new BufferedWriter(new FileWriter(paramsFile));
				outWriter.write(sb.toString());
				outWriter.close();
			}
			catch (IOException ioe)
			{
				System.err.println("I/O erreur durant creation output file " + String.valueOf(paramsFile) + " " + ioe);
			}
		}
	}

	public static void generateParams(String paramsFile, String outFile, String ref, ArrayList<String> hypsFiles, ArrayList<String> hypsScoresFiles, 
			float[] costs, String wordnet, String shift_word_stop_list, String paraphrases)
	{
		generateParams(paramsFile, outFile, ref, null, -1, hypsFiles, hypsScoresFiles, null, 
				costs, null, "relax",
				wordnet, shift_word_stop_list, paraphrases, 
				true, true, true);
	}

	public static void generateParams(String paramsFile, String outFile, String ref, ArrayList<String> hypsFiles, ArrayList<String> hypsScoresFile, 
			String wordnet, String shift_word_stop_list, String paraphrases)
	{
		float[] costs = new float[7];
		costs[0] = 1.0f;
		costs[1] = 1.0f;
		costs[2] = 1.0f;
		costs[3] = 1.0f;
		costs[4] = 1.0f;
		costs[5] = 0.0f;
		costs[6] = 1.0f;

		generateParams(paramsFile, outFile, ref, null, -1, hypsFiles, hypsScoresFile, null, 
				costs, null, "relax", 
				wordnet, shift_word_stop_list, paraphrases, 
				true, true, true);
	}
	
	public static void generateParams(String paramsFile, String outFile, String backboneFile, String backboneScoresFile, int backboneIndex,
			ArrayList<String> hypsFiles, ArrayList<String> hypsScoresFiles, int[] hypsIndex, 
			float[] costs, float[] sysWeights, String shift_constraint, 
			String wordnet, String shift_word_stop_list, String paraphrases)
	{
		
		generateParams( paramsFile,  outFile,  backboneFile,  backboneScoresFile,  backboneIndex, hypsFiles, hypsScoresFiles, hypsIndex,
				costs, sysWeights, shift_constraint, 
				wordnet, shift_word_stop_list, paraphrases, 
				true, true, true);
	}
	
	public static void generateParams(String paramsFile, String outfile, String backboneFile, String backboneScoresFile, int backboneIndex, ArrayList<String> hypsFiles, ArrayList<String> hypsScoresFiles, int[] hypsIndex,
			float[] costs, float[] sysWeights, String wordnet, String shift_word_stop_list, String paraphrases)
	{
		generateParams(paramsFile, outfile, backboneFile, backboneScoresFile, backboneIndex, hypsFiles, hypsScoresFiles, hypsIndex, 
				costs, sysWeights, "relax", 
				wordnet, shift_word_stop_list, paraphrases, 
				true, true, true);
	}
}
