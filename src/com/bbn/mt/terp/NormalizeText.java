package com.bbn.mt.terp;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NormalizeText
{
	private static boolean remove_punc = false;
	private static boolean lower_case = true;
	private static boolean std_normalize = false;
	private static boolean number_normalize = false;
	private static boolean new_norm = false;
	private static final ArrayList<String[][]> equivStrings = new ArrayList<String[][]>(200);
	private static final HashMap<String, ArrayList<Integer>> equivlookup = new HashMap<String, ArrayList<Integer>>(500);
	private static final Pattern isNumpat = Pattern.compile("^[0-9]+$");
	private static final Pattern equivpat = Pattern.compile("^\\s*\\<p\\>(.*)<\\/p\\>\\s*$");
	private static final HashMap<String, String> NumHash = new HashMap<String, String>(200);

	private static boolean _initialized = false;

	private static int num_processed = 0;
	private static TERpara params = null;
	
	public static void setRemPunc(boolean b)
	{
		remove_punc = b;
	}
	public static void setLowerCase(boolean b)
	{
		lower_case = b;
	}
	public static void setStdNormalize(boolean b)
	{
		std_normalize = b;
	}
	public static void setNumberNormalize(boolean b)
	{
		number_normalize = b;
	}
	public static void setNewNorm(boolean b)
	{
		new_norm = b;
	}

	public static String get_info()
	{
		String s = ("Text Normalization\n" + "  Remove Punctuation:      " + remove_punc + "\n"
				+ "  Lower Case Text:         " + lower_case + "\n" + "  Standard Normalization:  " + std_normalize
				+ "\n" + "  New Normalization:       " + new_norm + "\n" + "  Normalize Numbers:       "
				+ number_normalize + "\n" + "  Number of Equiv Strings: " + equivStrings.size() + "\n"
				+ "  Num Strings Processed:   " + num_processed + "\n");
		return s;
	}

	public static void reinit(TERpara params)
	{
		_initialized = false;
		equivStrings.clear();
		init(params);
	}

	public static void init(TERpara p)
	{
		params = p;
		if (!_initialized)
		{
			// System.err.println("NORMALIZE = "+TERpara.para().get(TERpara.OPTIONS.NORMALIZE));
			setStdNormalize((Boolean) params.para().get(TERpara.OPTIONS.NORMALIZE));
			setNewNorm((Boolean) params.para().get(TERpara.OPTIONS.NEWNORM));
			setLowerCase(!(Boolean) params.para().get(TERpara.OPTIONS.CASEON));
			setRemPunc((Boolean) params.para().get(TERpara.OPTIONS.NOPUNCTUATION));
			setNumberNormalize((Boolean) params.para().get(TERpara.OPTIONS.NORM_NUMS));
			load_norm_file((String) params.para().get(TERpara.OPTIONS.NORM_FILE));
			_initialized = true;
		}
	}

	public static void load_norm_file(String fname)
	{
		if ((fname == null) || fname.equals(""))
			return;
		System.out.println("Loading normalization equivalences from " + fname);
		int num_sets = 0;
		int num_words = 0;
		try
		{
			BufferedReader fh = new BufferedReader(new FileReader(fname));
			String line;
			while ((line = fh.readLine()) != null)
			{
				line = line.trim();
				if (line.length() == 0)
					continue;
				Matcher im = equivpat.matcher(line);
				if (im.matches())
				{
					String[] phrases = im.group(1).split("\\<\\/p\\>\\s+\\<p\\>");
					if (phrases.length > 1)
					{
						String[][] ta = new String[phrases.length][];
						for (int i = 0; i < phrases.length; i++)
						{
							String[] psplt = phrases[i].split("\\s+");
							for (int j = 0; j < psplt.length; j++)
							{
								psplt[j] = psplt[j].intern();
							}
							ta[i] = psplt;
						}
						for (int i = 1; i < ta.length; i++)
						{
							for (int j = 0; j < ta[i].length; j++)
							{
								ArrayList<Integer> al = (ArrayList<Integer>) equivlookup.get(ta[i][j]);
								if (al == null)
								{
									al = new ArrayList<Integer>(3);
									al.add(equivStrings.size());
									equivlookup.put(ta[i][j], al);
								} else
								{
									int last = (Integer) al.get(al.size() - 1);
									if (last != equivStrings.size())
									{
										al.add(equivStrings.size());
									}
								}
							}
						}
						num_sets++;
						num_words += phrases.length;
						equivStrings.add(ta);
					}
				} else
				{
					System.err.println("Invalid line in " + fname + ": " + line);
					System.exit(-1);
				}
			}
			fh.close();
		} catch (IOException ioe)
		{
			System.out.println("Loading normalization file from " + fname + ": " + ioe);
			System.exit(-1);
		}

		System.out.println("  " + num_sets + " sets loaded with a total of " + num_words + " words");
		return;
	}

	public static String[] process(String s)
	{
		num_processed++;
		s = simple_norm(s);

		if (lower_case)
		{
			System.err.println("INFO NormalizeText process : lower_case");
			s = s.toLowerCase();
		}
		if (std_normalize)
		{
			System.err.println("INFO NormalizeText process : std_normalize");
			s = standard_normalize(s);
		}
		if (new_norm)
		{
			System.err.println("INFO NormalizeText process : new_norm");
			s = newnorm(s);
		}
		if (remove_punc)
		{
			System.err.println("INFO NormalizeText process : remove_punc");
			s = removePunctuation(s);
		}
		s = simple_norm(s);

		String[] tr = s.split("\\s+");
		if (number_normalize)
		{
			System.err.println("INFO NormalizeText process : number_normalize");
			for (int i = 0; i < tr.length; i++)
				tr[i] = parseNumber(tr[i]);
		}

		if (equivStrings.size() > 0)
			tr = norm_equivstrings(tr);

		int numgood = 0;
		for (int i = 0; i < tr.length; i++)
		{
			if (!(tr[i].equals("")))
				numgood++;
		}

		if (numgood == 0)
		{
			return new String[0];
		}

		if (numgood != tr.length)
		{
			String[] ntr = tr;
			ntr = new String[numgood];
			int cur = 0;
			for (int i = 0; i < tr.length; i++)
			{
				if (!(tr[i].equals("")))
					ntr[cur++] = tr[i];
			}
			tr = ntr;
		}

		for (int i = 0; i < tr.length; i++)
		{
			tr[i] = tr[i].intern();
		}

		return tr;
	}

	private static void find_poss_equiv(String[] s, TreeSet<Integer> eqvind, int minind)
	{
		if (eqvind == null)
		{
			eqvind = new TreeSet<Integer>();
		}
		for (int i = 0; i < s.length; i++)
		{
			ArrayList<Integer> al = (ArrayList<Integer>) equivlookup.get(s[i]);
			if (al != null)
			{
				for (int j = 0; j < al.size(); j++)
				{
					int ind = (Integer) al.get(j);
					if ((ind > minind) && (!eqvind.contains(ind)))
					{
						eqvind.add(ind);
						String[][] eqv = (String[][]) equivStrings.get(ind);
						find_poss_equiv(eqv[0], eqvind, ind);
					}
				}
			}
		}
		return;
	}
	private static TreeSet<Integer> find_poss_equiv(String[] s)
	{
		TreeSet<Integer> tr = new TreeSet<Integer>();
		find_poss_equiv(s, tr, -1);
		return tr;
	}

	public static String[] norm_equivstrings(String[] s)
	{
		TreeSet<Integer> eqvtc = find_poss_equiv(s);
		Iterator<Integer> it = eqvtc.iterator();
		while (it.hasNext())
		{
			int eqind = it.next();
			String[][] eqv = (String[][]) equivStrings.get(eqind);
			String[] to = eqv[0];
			for (int j = 1; j < eqv.length; j++)
			{
				String[] from = eqv[j];
				s = replace_str(s, from, to);
			}
		}
		return s;
	}

	private static String[] replace_str(String[] ps, String[] from, String[] to)
	{
		for (int i = 0; i < (ps.length - (from.length - 1)); i++)
		{
			boolean ok = true;
			for (int j = 0; ok && (j < from.length); j++)
			{
				if (!(ps[i + j].equals(from[j])))
					ok = false;
			}
			if (ok)
			{
				if (from.length != to.length)
				{
					String nps[] = new String[(ps.length + to.length) - from.length];
					for (int j = 0; j < i; j++)
						nps[j] = ps[j];
					for (int j = 0; j < to.length; j++)
						nps[i + j] = to[j];
					int startps = (i + from.length);
					int startnps = (i + to.length);
					for (int j = 0; j < (ps.length - startps); j++)
					{
						nps[j + startnps] = ps[j + startps];
					}
					ps = nps;
				} else
				{
					for (int j = 0; j < to.length; j++)
					{
						ps[i + j] = to[j];
					}
				}
				i += (to.length - 1);
			}
		}
		return ps;
	}

	public static String simple_norm(String s)
	{
		s = s.trim();
		s = s.replaceAll("\\s+", " "); // one space only between words
		return s;
	}

	public static String newnorm(String s)
	{
		s = s.replaceAll("\\B-\\B", " ");
		s = s.replaceAll("'s ", " 's "); // handle possessives
		s = s.replaceAll("'s$", " 's"); // handle possessives
		s = s.replaceAll("\\Bs' ", "s 's "); // handle possessives
		s = s.replaceAll("\\Bs'$", "s 's"); // handle possessives
		return s;
	}

	public static String standard_normalize(String s)
	{
		// language-independent part:
		s = s.replaceAll("<skipped>", ""); // strip "skipped" tags
		s = s.replaceAll("-\n", ""); // strip end-of-line hyphenation and join
		// lines
		s = s.replaceAll("\n", " "); // join lines
		s = s.replaceAll("&quot;", "\""); // convert SGML tag for quote to "
		s = s.replaceAll("&amp;", "&"); // convert SGML tag for ampersand to &
		s = s.replaceAll("&lt;", "<"); // convert SGML tag for less-than to >
		s = s.replaceAll("&gt;", ">"); // convert SGML tag for greater-than to <

		// language-dependent part (assuming Western languages):
		s = " " + s + " ";
		s = s.replaceAll("([\\{-\\~\\[-\\` -\\&\\(-\\+\\:-\\@\\/])", " $1 "); 
		// tokenize punctuation
		s = s.replaceAll("'s ", " 's "); // handle possessives
		s = s.replaceAll("'s$", " 's"); // handle possessives
		s = s.replaceAll("([^0-9])([\\.,])", "$1 $2 "); // tokenize period and
		// comma unless preceded by a digit
		s = s.replaceAll("([\\.,])([^0-9])", " $1 $2"); // tokenize period and
		// comma unless followed by a digit
		s = s.replaceAll("([0-9])(-)", "$1 $2 "); // tokenize dash when preceded
		// by a digit

		s = simple_norm(s);

		return s;
	}

	private static String removePunctuation(String str)
	{
		String s = str.replaceAll("[\\.,\\?:;!\"\\(\\)]", "");
		s = s.replaceAll("\\s+", " ");
		return s;
	}

	public static boolean isNum(String wd)
	{
		Matcher np = isNumpat.matcher(wd);
		return np.matches();
	}

	public static String parseNumber(String wd)
	{
		String pn = wd;
		pn = pn.replaceAll("[\\-,.$%]", "");
		if (isNum(pn))
			return pn;
		String n = (String) NumHash.get(pn.toLowerCase());
		if (n != null)
			return n;
		return wd;
	}

	static
	{
		NumHash.put("zero", "0");
		NumHash.put("one", "1");
		NumHash.put("two", "2");
		NumHash.put("three", "3");
		NumHash.put("four", "4");
		NumHash.put("five", "5");
		NumHash.put("six", "6");
		NumHash.put("seven", "7");
		NumHash.put("eight", "8");
		NumHash.put("nine", "9");
		NumHash.put("ten", "10");
		NumHash.put("eleven", "11");
		NumHash.put("twelve", "12");
		NumHash.put("thirteen", "13");
		NumHash.put("fourteen", "14");
		NumHash.put("fifteen", "15");
		NumHash.put("sixteen", "16");
		NumHash.put("seventeen", "17");
		NumHash.put("eighteen", "18");
		NumHash.put("nineteen", "19");
		NumHash.put("twenty", "20");
		NumHash.put("thirty", "30");
		NumHash.put("forty", "40");
		NumHash.put("fifty", "50");
		NumHash.put("sixty", "60");
		NumHash.put("seventy", "70");
		NumHash.put("eighty", "80");
		NumHash.put("ninety", "90");
		NumHash.put("hundred", "100");
		NumHash.put("thousand", "1000");
		NumHash.put("million", "1000000");
		NumHash.put("billion", "1000000000");
	}
}
