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

import java.util.ArrayList;
import java.util.HashMap;

public class TERalignmentCN extends TERalignment
{
	public ArrayList<ArrayList<Comparable<String>>> cn = null;
	public ArrayList<ArrayList<Float>> cn_scores = null;

	public int ref_idx = -1;

	public ArrayList<ArrayList<Comparable<String>>> full_cn = null;
	public ArrayList<ArrayList<Float>> full_cn_scores = null;
	public ArrayList<ArrayList<Integer>> full_cn_sys = null;

	public ArrayList<Comparable<String>[]> hyps = null;
	public String[] orig_hyps = null;

	public ArrayList<float[]> hyps_scores = null;
	public String[] orig_hyps_scores = null;
	public float[] aftershift_scores;

	public float null_score = 0.0f;
	private boolean DEBUG = false;
	public TERalignmentCN(TERcost costfunc, TERpara params)
	{
		super(costfunc, params);
	}

	private String prtShift(Comparable<String>[][] ref, TERshift[] allshifts)
	{
		String to_return = "";
		int ostart, oend, odest;
		// int nstart, nend;
		int dist;
		String direction = "";
		if (allshifts != null)
		{
			for (int i = 0; i < allshifts.length; ++i)
			{
				TERshift[] oneshift = new TERshift[1];
				ostart = allshifts[i].start;
				oend = allshifts[i].end;
				odest = allshifts[i].newloc;
				if (odest >= oend)
				{
					// right
					// nstart = odest + 1 - allshifts[i].size();
					// nend = nstart + allshifts[i].size() - 1;
					dist = odest - oend;
					direction = "right";
				}
				else
				{
					// left
					// nstart = odest + 1;
					// nend = nstart + allshifts[i].size() - 1;
					dist = ostart - odest - 1;
					direction = "left";
				}
				to_return += "\nShift " + allshifts[i].shifted + " " + dist + " words " + direction;
				oneshift[0] = new TERshift(ostart, oend, allshifts[i].moveto, odest);
				to_return += getPraStr(ref, allshifts[i].aftershift, allshifts[i].alignment, oneshift, true);
			}
			to_return += "\n";
		}
		return to_return;
	}

	private String getPraStr(Comparable<String>[][] ref, Comparable<String>[] aftershift, char[] alignment,
			TERshift[] allshifts, boolean shiftonly)
	{
		String to_return = "";
		String rstr = "";
		String hstr = "";
		String estr = "";
		String sstr = "";
		HashMap<String, ArrayList<Integer>> align_info = new HashMap<String, ArrayList<Integer>>();
		ArrayList<Integer> shift_dists = new ArrayList<Integer>();
		int anum = 1;
		int ind_start = 0;
		int ind_end = 1;
		int ind_from = 2;
		int ind_in = 3;
		int ostart, oend, odest;
		int slen = 0;
		int nstart, nend, nfrom, dist;
		int non_inserr = 0;
		if (allshifts != null)
		{
			for (int i = 0; i < allshifts.length; ++i)
			{
				ostart = allshifts[i].start;
				oend = allshifts[i].end;
				odest = allshifts[i].newloc;
				slen = allshifts[i].size();
				if (odest >= oend)
				{
					// right
					nstart = odest + 1 - slen;
					nend = nstart + slen - 1;
					nfrom = ostart;
					dist = odest - oend;
				}
				else
				{
					// left
					nstart = odest + 1;
					nend = nstart + slen - 1;
					nfrom = ostart + slen;
					dist = (ostart - odest - 1) * -1;
				}
				// dist =
				// (allshifts[i].leftShift())?-1*allshifts[i].distance():allshifts[i].distance();
				shift_dists.add(dist);
				// System.out.println("[" + hyp[ostart] + ".." + hyp[oend] +
				// " are shifted " + dist);
				if (anum > 1)
					performShiftArray(align_info, ostart, oend, odest, alignment.length);
				ArrayList<Integer> val = align_info.get(nstart + "-" + ind_start);
				if (val == null)
				{
					ArrayList<Integer> al = new ArrayList<Integer>();
					al.add(anum);
					align_info.put(nstart + "-" + ind_start, al);
				}
				else
				{
					ArrayList<Integer> al = val;
					al.add(anum);
				}
				val = align_info.get(nend + "-" + ind_end);
				if (val == null)
				{
					ArrayList<Integer> al = new ArrayList<Integer>();
					al.add(anum);
					align_info.put(nend + "-" + ind_end, al);
				}
				else
				{
					ArrayList<Integer> al = val;
					al.add(anum);
				}
				val = align_info.get(nfrom + "-" + ind_from);
				if (val == null)
				{
					ArrayList<Integer> al = new ArrayList<Integer>();
					al.add(anum);
					align_info.put(nfrom + "-" + ind_from, al);
				}
				else
				{
					ArrayList<Integer> al = val;
					al.add(anum);
				}
				/*
				 * val = align_info.get("60-"+ind_start); if(val != null)
				 * System.out.println(((ArrayList) val).get(0)); else
				 * System.out.println("empty");
				 * 
				 * System.out.println("nstart: " + nstart + ", nend:" + nend +
				 * "," + ostart +"," + oend +","+ odest + "," +
				 * align_info.size());
				 */
				if (slen > 0)
				{
					for (int j = nstart; j <= nend; ++j)
					{
						val = align_info.get(j + "-" + ind_in);
						if (val == null)
						{
							ArrayList<Integer> al = new ArrayList<Integer>();
							al.add(anum);
							align_info.put(j + "-" + ind_in, al);
						}
						else
						{
							ArrayList<Integer> al = val;
							al.add(anum);
						}
					}
				}
				anum++;
			}
		}
		int hyp_idx = 0;
		int ref_idx = 0;
		ArrayList<Integer> val = null;
		if (alignment != null)
		{
			for (int i = 0; i < alignment.length; ++i)
			{
				String shift_in_str = "";
				String ref_wd = (ref_idx < ref.length) ? String.valueOf(TERutilities.join("|", ref[ref_idx])) : "";
				String hyp_wd = (hyp_idx < hyp.length) ? String.valueOf(aftershift[hyp_idx]) : "";
				int l = 0;
				if (alignment[i] != 'D')
				{
					val = align_info.get(hyp_idx + "-" + ind_from);
					if (val != null)
					{
						// System.out.println("hyp_idx: " + hyp_idx + "," +
						// hyp_wd);
						ArrayList<Integer> list = val;
						for (int j = 0; j < list.size(); ++j)
						{
							String s = "" + list.get(j);
							hstr += " @";
							rstr += "  ";
							estr += "  ";
							sstr += " " + s;
							for (int k = 1; k < s.length(); ++k)
							{
								hstr += " ";
								rstr += " ";
								estr += " ";
							}
						}
					}
					val = align_info.get(hyp_idx + "-" + ind_start);
					if (val != null)
					{
						// System.out.println("hyp_idx: " + hyp_idx + "," +
						// hyp_wd + "," + alignment.length);
						ArrayList<Integer> list = val;
						for (int j = 0; j < list.size(); ++j)
						{
							String s = "" + list.get(j);
							hstr += " [";
							rstr += "  ";
							estr += "  ";
							sstr += " " + s;
							for (int k = 1; k < s.length(); ++k)
							{
								hstr += " ";
								rstr += " ";
								estr += " ";
							}
						}
					}
					if (slen > 0)
					{
						val = align_info.get(hyp_idx + "-" + ind_in);
						if (val != null)
							shift_in_str = TERsgml.join(",", val);
						// if(val != null) System.out.println("shiftstr: " +
						// ref_idx + "," + hyp_idx + "-" + ind_in + ":" +
						// shift_in_str);
					}
				}
				switch (alignment[i])
				{
					case ' ' :
						l = Math.max(ref_wd.length(), hyp_wd.length());
						hstr += " " + hyp_wd;
						rstr += " " + ref_wd;
						estr += " ";
						sstr += " ";
						for (int j = 0; j < l; ++j)
						{
							if (hyp_wd.length() <= j)
								hstr += " ";
							if (ref_wd.length() <= j)
								rstr += " ";
							estr += " ";
							sstr += " ";
						}
						hyp_idx++;
						ref_idx++;
						non_inserr++;
						break;
					case 'S' :
					case 'Y' :
					case 'T' :
						l = Math.max(ref_wd.length(), Math.max(hyp_wd.length(), Math.max(1, shift_in_str.length())));
						hstr += " " + hyp_wd;
						rstr += " " + ref_wd;
						if (hyp_wd.equals("") || ref_wd.equals(""))
							System.out.println("unexpected empty: sym=" + alignment[i] + " hyp_wd=" + hyp_wd
									+ " ref_wd=" + ref_wd + " i=" + i + " alignment="
									+ TERutilities.join(",", alignment));
						estr += " " + alignment[i];
						sstr += " " + shift_in_str;
						for (int j = 0; j < l; ++j)
						{
							if (hyp_wd.length() <= j)
								hstr += " ";
							if (ref_wd.length() <= j)
								rstr += " ";
							if (j > 0)
								estr += " ";
							if (j >= shift_in_str.length())
								sstr += " ";
						}
						ref_idx++;
						hyp_idx++;
						non_inserr++;
						break;
					case 'P' :
						int min = alignment_r[i];
						if (alignment_h[i] < min)
							min = alignment_h[i];
						for (int k = 0; k < min; k++)
						{
							ref_wd = (ref_idx < ref.length) ? String.valueOf(ref[ref_idx]) : "";
							hyp_wd = (hyp_idx < hyp.length) ? String.valueOf(aftershift[hyp_idx]) : "";
							// System.out.println("Saying that " + ref_wd +
							// " & " + hyp_wd + " are P. " + alignment_r[i] +
							// " " +
							// alignment_h[i]);
							l = Math
									.max(ref_wd.length(), Math.max(hyp_wd.length(), Math.max(1, shift_in_str.length())));
							hstr += " " + hyp_wd;
							rstr += " " + ref_wd;
							if (hyp_wd.equals("") || ref_wd.equals(""))
								System.out.println("unexpected empty: sym=" + alignment[i] + " hyp_wd=" + hyp_wd
										+ " ref_wd=" + ref_wd + " i=" + i + " alignment="
										+ TERutilities.join(",", alignment));
							estr += " " + alignment[i];
							sstr += " " + shift_in_str;
							for (int j = 0; j < l; ++j)
							{
								if (hyp_wd.length() <= j)
									hstr += " ";
								if (ref_wd.length() <= j)
									rstr += " ";
								if (j > 0)
									estr += " ";
								if (j >= shift_in_str.length())
									sstr += " ";
							}
							ref_idx++;
							hyp_idx++;
							non_inserr++;
						}
						if (alignment_h[i] > alignment_r[i])
						{
							for (int k = alignment_r[i]; k < alignment_h[i]; k++)
							{
								ref_wd = (ref_idx < ref.length)
										? String.valueOf(TERutilities.join("|", ref[ref_idx]))
										: "";
								hyp_wd = (hyp_idx < hyp.length) ? String.valueOf(aftershift[hyp_idx]) : "";
								l = Math.max(hyp_wd.length(), shift_in_str.length());
								hstr += " " + hyp_wd;
								rstr += " ";
								estr += " P";
								sstr += " " + shift_in_str;
								for (int j = 0; j < l; ++j)
								{
									rstr += "*";
									if (j >= hyp_wd.length())
										hstr += " ";
									if (j > 0)
										estr += " ";
									if (j >= shift_in_str.length())
										sstr += " ";
								}
								hyp_idx++;
							}
						}
						else if (alignment_r[i] > alignment_h[i])
						{
							for (int k = alignment_h[i]; k < alignment_r[i]; k++)
							{
								ref_wd = (ref_idx < ref.length)
										? String.valueOf(TERutilities.join("|", ref[ref_idx]))
										: "";
								hyp_wd = (hyp_idx < hyp.length) ? String.valueOf(aftershift[hyp_idx]) : "";
								l = ref_wd.length();
								hstr += " ";
								rstr += " " + ref_wd;
								estr += " P";
								sstr += " ";
								for (int j = 0; j < l; ++j)
								{
									hstr += "*";
									if (j > 0)
										estr += " ";
									sstr += " ";
								}
								ref_idx++;
								non_inserr++;
							}
						}
						break;
					case 'D' :
						l = ref_wd.length();
						hstr += " ";
						rstr += " " + ref_wd;
						estr += " D";
						sstr += " ";
						for (int j = 0; j < l; ++j)
						{
							hstr += "*";
							if (j > 0)
								estr += " ";
							sstr += " ";
						}
						ref_idx += alignment_r[i];
						hyp_idx += alignment_h[i];
						non_inserr++;
						break;
					case 'I' :
						l = Math.max(hyp_wd.length(), shift_in_str.length());
						hstr += " " + hyp_wd;
						rstr += " ";
						estr += " I";
						sstr += " " + shift_in_str;
						for (int j = 0; j < l; ++j)
						{
							rstr += "*";
							if (j >= hyp_wd.length())
								hstr += " ";
							if (j > 0)
								estr += " ";
							if (j >= shift_in_str.length())
								sstr += " ";
						}
						hyp_idx++;
						break;
				}
				if (alignment[i] != 'D')
				{
					val = align_info.get((hyp_idx - 1) + "-" + ind_end);
					if (val != null)
					{
						ArrayList<Integer> list = val;
						for (int j = 0; j < list.size(); ++j)
						{
							String s = "" + list.get(j);
							hstr += " ]";
							rstr += "  ";
							estr += "  ";
							sstr += " " + s;
							for (int k = 1; k < s.length(); ++k)
							{
								hstr += " ";
								rstr += " ";
								estr += " ";
							}
						}
					}
				}
			}
		}
		// if(non_inserr != ref.length && ref.length > 1)
		// System.out.println("** Error, unmatch non-insertion erros " +
		// non_inserr +
		// " and reference length " + ref.length );
		String indent = "";
		if (shiftonly)
			indent = " ";
		to_return += "\n" + indent + "REF: " + rstr;
		to_return += "\n" + indent + "HYP: " + hstr;
		if (!shiftonly)
		{
			to_return += "\n" + indent + "EVAL:" + estr;
			to_return += "\n" + indent + "SHFT:" + sstr;
		}
		to_return += "\n";
		return to_return;
	}

	public void buildCN(int hyp_idx)
	{
		if (DEBUG)
		{
			/*
			 * System.out.println("Size alignment : "+alignment.length); for
			 * (int i = 0; i < alignment.length; i++) {
			 * System.out.println("al["+i+"] "+alignment[i]); }
			 */
			System.err.println("TERalignmentCN::buildCN START");
			System.err.println("TERalignmentCN::buildCN ref_idx = " + ref_idx + " and hyp_idx = " + hyp_idx);
		}

		if (full_cn == null)
		{
			full_cn = new ArrayList<ArrayList<Comparable<String>>>();
			full_cn_scores = new ArrayList<ArrayList<Float>>();
			full_cn_sys = new ArrayList<ArrayList<Integer>>();

			for (int i = 0; i < cn.size(); i++)
			{
				full_cn.add(new ArrayList<Comparable<String>>());
				full_cn_scores.add(new ArrayList<Float>());
				full_cn_sys.add(new ArrayList<Integer>());

				full_cn.get(i).addAll(cn.get(i));
				full_cn_scores.get(i).addAll(cn_scores.get(i));
				full_cn_sys.get(i).add(ref_idx);

				if (full_cn.get(i).size() > 1)
				{
					System.err.println("size t=" + full_cn.get(i).size() + " bigger than 1 .. exiting!");
					System.exit(0);
				}
			}
		}

		// System.err.println("\n---------------- buildCN : le cn :\n "+toCNString()+" ---------------- \n");
		// suppose that ref, hyp and alignment are specified
		int hi = 0, pos = 0;
		for (int i = 0; i < alignment.length; i++)
		{
			switch (alignment[i])
			{
				case ' ' : // correct
					addUnique(pos, hi);
					add(pos, hi, hyp_idx);
					pos++;
					hi++;
					break;
				case 'I' : // insertions
					cn.add(pos, new ArrayList<Comparable<String>>());
					cn_scores.add(pos, new ArrayList<Float>());
					cn.get(pos).add(aftershift[hi]);
					cn_scores.get(pos).add(aftershift_scores[hi]);
					cn.get(pos).add("NULL");
					cn_scores.get(pos).add(null_score);

					full_cn.add(pos, new ArrayList<Comparable<String>>());
					full_cn_scores.add(pos, new ArrayList<Float>());
					full_cn_sys.add(pos, new ArrayList<Integer>());

					full_cn.get(pos).add(aftershift[hi]);
					full_cn_scores.get(pos).add(aftershift_scores[hi]);
					full_cn_sys.get(pos).add(hyp_idx);

					full_cn.get(pos).add("NULL");
					full_cn_scores.get(pos).add(null_score);
					full_cn_sys.get(pos).add(-1); // -1 if for null_words

					pos++;
					hi++;
					break;
				case 'S' : // shift
				case 'Y' : // synonymes
				case 'T' : // stems

					addUnique(pos, hi);
					add(pos, hi, hyp_idx);
					pos++;
					hi++;
					break;
				case 'P' : // paraphrase
					int hl = alignment_h[i];
					int rl = alignment_r[i];
					if (DEBUG)
					{
						/*
						 * System.err.println("buildCN - paraphrase : ref_len="+rl
						 * +" hyp_len="+hl); System.err.println("ref :");
						 * for(int t=0; t<hl; t++)
						 * System.err.print(""+alignment[t]);
						 */
					}

					if (hl > rl) // hyp side is longer than ref side of
					// paraphrase
					{
						for (int j = 0; j < hl; j++)
						{
							if (j < rl)
							{
								addUnique(pos, hi);
								add(pos, hi, hyp_idx);
								pos++;
								hi++;
							}
							else
							{
								cn.add(pos, new ArrayList<Comparable<String>>());
								cn_scores.add(pos, new ArrayList<Float>());
								cn.get(pos).add(aftershift[hi]);
								cn_scores.get(pos).add(aftershift_scores[hi]);
								cn.get(pos).add("NULL");
								cn_scores.get(pos).add(null_score);

								full_cn.add(pos, new ArrayList<Comparable<String>>());
								full_cn_scores.add(pos, new ArrayList<Float>());
								full_cn_sys.add(pos, new ArrayList<Integer>());

								full_cn.get(pos).add(aftershift[hi]);
								full_cn_scores.get(pos).add(aftershift_scores[hi]);
								full_cn_sys.get(pos).add(hyp_idx);

								full_cn.get(pos).add("NULL");
								full_cn_scores.get(pos).add(null_score);
								full_cn_sys.get(pos).add(-1); // -1 if for
								// null_words

								pos++;
								hi++;
							}
						}
					}
					else if (rl > hl) // hyp side is shorter than ref side
					{
						for (int j = 0; j < rl; j++)
						{
							if (j < hl)
							{
								addUnique(pos, hi);
								add(pos, hi, hyp_idx);
								pos++;
								hi++;
							}
							else
							{
								addUniqueNULL(pos);
								addNULL(pos);
								pos++;
							}
						}
					}
					else
					// equal size
					{
						for (int j = 0; j < rl; j++)
						{
							addUnique(pos, hi);
							add(pos, hi, hyp_idx);
							pos++;
							hi++;
						}
					}
					break;
				case 'D' :
					addUniqueNULL(pos);
					addNULL(pos);
					pos++;
					break;
				default :
					System.err.println("Unknown alignment type : " + alignment[i]);
					break;
			}
		}
		if (DEBUG)
			System.err.println("TERalignmentCN::buildCN END");
	}

	private void verifySynchro()
	{
		boolean ok = true;
		if (cn.size() != full_cn.size())
		{
			System.err.println("CN size != full_CN size");
			ok = false;
		}

		if (full_cn.size() != full_cn_scores.size())
		{
			System.err.println("full_CN size != full_CN_scores size");
			ok = false;
		}
		if (full_cn.size() != full_cn_scores.size())
		{
			System.err.println("full_CN size != full_CN_sys size");
			ok = false;
		}

		for (int i = 0; i < full_cn.size(); i++)
		{
			int a = full_cn.get(i).size();
			int b = full_cn_scores.get(i).size();
			int c = full_cn_sys.get(i).size();

			if (a != b)
			{
				System.err.println("full_CN[" + i + "]=" + a + " size != full_CN_scores[" + i + "]=" + b + " size");
				for (int j = 0; j < a; j++)
					System.err.print("full_CN[" + j + "]=" + full_cn.get(i).get(j) + " ");
				System.err.println();
				for (int j = 0; j < b; j++)
					System.err.print("full_CN_scores[" + j + "]=" + full_cn_scores.get(i).get(j) + " ");
				System.err.println();
				ok = false;
			}
			if (a != c)
			{
				System.err.println("full_CN[" + i + "]=" + a + " size != full_CN_sys[" + i + "]=" + c + " size");
				for (int j = 0; j < a; j++)
					System.err.print("full_CN[" + j + "]=" + full_cn.get(i).get(j) + " ");
				System.err.println();
				for (int j = 0; j < c; j++)
					System.err.print("full_CN_sys[" + j + "]=" + full_cn_sys.get(i).get(j) + " ");
				System.err.println();
				ok = false;
			}
		}

		if (!ok)
		{
			System.err.println("Some errors occured .. exiting !");
			System.exit(0);
		}
		else
		{
			System.err.println("Synchro OK !");
		}
	}

	private void addUnique(int pos, int hi)
	{
		ArrayList<Comparable<String>> mesh = cn.get(pos);
		Comparable<String> word = aftershift[hi];
		int j;
		if (params.para().get_boolean(TERpara.OPTIONS.CASEON))
		{
			for (j = 0; j < mesh.size(); j++)
			{
				String w = (String) mesh.get(j);
				if (w.equals((String) word))
				{
					if (DEBUG)
					{
						System.err.println("proba for word "+w+" was "+cn_scores.get(pos).get(j)+" and becomes "+(cn_scores.get(pos).get(j)
						 + aftershift_scores[hi]));
					}
					cn_scores.get(pos).set(j, cn_scores.get(pos).get(j) + aftershift_scores[hi]);
					return;
				}
			}
		}
		else
		{
			for (j = 0; j < mesh.size(); j++)
			{
				String w = (String) mesh.get(j);
				if (w.equalsIgnoreCase((String) word))
				{
					if (DEBUG)
					{
						System.err.println("proba for word " + w + " was " + cn_scores.get(pos).get(j)
								+ " and becomes " + (cn_scores.get(pos).get(j) + aftershift_scores[hi]));
					}
					cn_scores.get(pos).set(j, cn_scores.get(pos).get(j) + aftershift_scores[hi]);
					return;
				}
			}
		}
		// System.err.println("UNIQ : add word "+word+" with score = "+aftershift_scores[hi]);
		mesh.add(word);
		cn_scores.get(pos).add(aftershift_scores[hi]);
	}

	private void add(int pos, int hi, int hyp_idx)
	{
		if (full_cn.size() <= pos)
			full_cn.add(new ArrayList<Comparable<String>>());
		if (full_cn_scores.size() <= pos)
			full_cn_scores.add(new ArrayList<Float>());
		if (full_cn_sys.size() <= pos)
			full_cn_sys.add(new ArrayList<Integer>());

		full_cn.get(pos).add(aftershift[hi]);
		full_cn_scores.get(pos).add(aftershift_scores[hi]);
		full_cn_sys.get(pos).add(hyp_idx);

		// System.err.println("FULL : add word "+aftershift[hi]+" with score = "+aftershift_scores[hi]+" at pos "+pos+", hi="+hi+", hyp_idx="+hyp_idx);
		// verifySynchro();
	}

	private void addUniqueNULL(int pos)
	{
		ArrayList<Comparable<String>> mesh = cn.get(pos);
		Comparable<String> word = "NULL";
		int j;
		if (params.para().get_boolean(TERpara.OPTIONS.CASEON))
		{
			for (j = 0; j < mesh.size(); j++)
			{
				String w = (String) mesh.get(j);
				if (w.equals((String) word))
				{
					// System.err.println("proba for word "+w+" was "+cn_scores.get(ri).get(j)+" and becomes "+(cn_scores.get(ri).get(j)
					// + null_score));
					cn_scores.get(pos).set(j, cn_scores.get(pos).get(j) + null_score);
					return;
				}
			}
		}
		else
		{
			for (j = 0; j < mesh.size(); j++)
			{
				String w = (String) mesh.get(j);
				if (w.equalsIgnoreCase((String) word))
				{
					// System.err.println("proba for word "+w+" was "+cn_scores.get(ri).get(j)+" and becomes "+(cn_scores.get(ri).get(j)
					// + null_score));
					cn_scores.get(pos).set(j, cn_scores.get(pos).get(j) + null_score);
					return;
				}
			}
		}
		// System.err.println("UNIQ : add word NULL with score = "+null_score+" at pos "+pos);
		mesh.add(word);
		cn_scores.get(pos).add(null_score);
	}

	private void addNULL(int pos)
	{
		// System.err.println("FULL : add word NULL with score = "+null_score+" at pos "+pos);
		ArrayList<Comparable<String>> mesh = full_cn.get(pos);
		Comparable<String> word = "NULL";
		for (int j = 0; j < mesh.size(); j++)
		{
			String w = (String) mesh.get(j);
			if (w.equals((String) word)) { return; }
		}

		full_cn.get(pos).add("NULL");
		full_cn_scores.get(pos).add(null_score);
		full_cn_sys.get(pos).add(-1); // -1 is for null_arcs
	}

	public void addHyp(Comparable<String>[] h)
	{
		if (hyps == null)
			hyps = new ArrayList<Comparable<String>[]>();
		hyps.add(h);
	}

	public String toCNString()
	{
		StringBuilder s = new StringBuilder("name cn1best\nnumaligns " + cn.size() + "\n\n");

		for (int i = 0; i < cn.size(); i++)
		{
			ArrayList<Comparable<String>> mesh = cn.get(i);
			ArrayList<Float> sc = cn_scores.get(i);
			s.append("align " + i + " ");

			for (int j = 0; j < mesh.size(); j++)
			{
				s.append((String) mesh.get(j)).append(" ").append(sc.get(j)).append(" ");
			}
			s.append("\n");
		}
		return s.toString();
	}

	public String toFullCNString(double[] sysWeights)
	{
		StringBuilder s = new StringBuilder("\nname cn1best\nnumaligns ");

		if (full_cn == null)
			s.append(0);
		else
			s.append(full_cn.size());

		s.append("\nnumsys ").append(ref_idx).append("\nnbsys ");
		s.append(sysWeights.length);
		s.append("\nsysweights");
		for (int i = 0; i < sysWeights.length; i++)
			s.append(" " + sysWeights[i]);
		s.append("\n\n");

		if (full_cn != null)
		{
			for (int i = 0; i < full_cn.size(); i++)
			{
				ArrayList<Comparable<String>> mesh = full_cn.get(i);
				ArrayList<Float> sc = full_cn_scores.get(i);
				ArrayList<Integer> sys = full_cn_sys.get(i);

				if (mesh.size() > 0)
				{
					s.append("align " + i + " ");

					for (int j = 0; j < mesh.size(); j++)
					{
						s.append((String) mesh.get(j));
						s.append(" ");
						s.append(sc.get(j));
						s.append(" ");
						s.append(sys.get(j));
						s.append(" ");
					}
					s.append("\n");
				}
			}
		}
		return s.toString();
	}

	public static String emptyFullCNString(double[] sysWeights)
	{
		StringBuilder s = new StringBuilder("\nname cn1best\nnumaligns ");

		s.append(0);

		s.append("\nnumsys ").append("-1").append("\nnbsys ");
		s.append(sysWeights.length);
		s.append("\nsysweights");
		for (int i = 0; i < sysWeights.length; i++)
			s.append(" " + sysWeights[i]);
		s.append("\n\n");

		return s.toString();
	}
	/*
	 * public static String toCNString(ArrayList<ArrayList<Comparable<String>>>
	 * cn) { StringBuilder s = new
	 * StringBuilder("name cn1best\nnumaligns "+cn.size()+"\n\n");
	 * 
	 * int i=0; for(ArrayList<Comparable<String>> mesh : cn) {
	 * s.append("align ").append(i).append(" "); i++; float proba = 1.0f /
	 * (float)mesh.size(); for(Comparable<String> word : mesh) {
	 * s.append(word).append(" ").append(proba).append(" "); } s.append("\n"); }
	 * 
	 * return s.toString(); }
	 */

	public String toString()
	{
		String s = "";
		if (orig_ref != null)
			s += "Original Reference: " + orig_ref + "\n";
		if (orig_hyp != null)
			s += "Original Hypothesis: " + orig_hyp + "\n";
		s += "Reference CN : \n" + toCNString();
		s += "\nHypothesis: " + TERutilities.join(" ", hyp) + "\nHypothesis After Shift: "
				+ TERutilities.join(" ", aftershift);
		if (alignment != null)
		{
			s += "\nAlignment: (";
			for (int i = 0; i < alignment.length; i++)
			{
				s += alignment[i];
			}
			s += ")";
		}
		if (allshifts == null)
		{
			s += "\nNumShifts: 0";
		}
		else
		{
			s += "\nNumShifts: " + allshifts.length;
			for (int i = 0; i < allshifts.length; i++)
			{
				s += "\n  " + allshifts[i];
			}
		}
		s += "\nScore: " + this.score() + " (" + this.numEdits + "/" + this.numWords + ")";
		return s;
	}

}
