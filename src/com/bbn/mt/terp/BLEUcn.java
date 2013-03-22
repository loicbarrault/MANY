package com.bbn.mt.terp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Logger;
import edu.lium.decoder.Graph;
import edu.lium.decoder.Link;
import edu.lium.decoder.MANYcn;
import edu.lium.decoder.NGram;
import edu.lium.decoder.NGramToken;
import edu.lium.decoder.Node;

public class BLEUcn
{
	private boolean DEBUG = false;
	private Logger logger = null;
	// private LogMath logMath = null;

	public int[] ngram_counts = null;

	/*
	 * private final double T = 10.0; private final double NGRAM_PRECISION =
	 * 0.85; private final double NGRAM_DECAY_RATIO = 0.72;
	 */

	public BLEUcounts bc = null;

	public BLEUcn(int id)
	{
		logger = Logger.getLogger("BLEUcn" + id);
		// logMath = new LogMath(1.0001f, true);

		bc = new BLEUcounts();
	}

	public BLEUcn(int id, int max_ngram_size)
	{
		this(id);
		if (max_ngram_size != BLEUcounts.max_ngram_size)
		{
			BLEUcounts.max_ngram_size = max_ngram_size;
			bc = new BLEUcounts();
		}
	}

	/**
	 * 
	 * @param cn
	 * @param refs
	 *            : : all the references provided
	 */
	public void calc(MANYcn cn, String[] refs)
	{
		bc.translation_length = cn.getSize();
		List<String[]> refs_list = new ArrayList<String[]>(refs.length);
		for (int i = 0; i < refs.length; i++)
			refs_list.add(refs[i].split("\\s+"));

		calc(cn, refs_list);
	}

	/**
	 * 
	 * @param cn
	 * @param ref
	 *            : the only one ref provided
	 */
	public void calc(MANYcn cn, String ref)
	{
		bc.translation_length = cn.getSize();
		List<String[]> refs_list = new ArrayList<String[]>(1);
		refs_list.add(ref.split("\\s+"));
		calc(cn, refs_list);
	}

	public void calc(MANYcn cn, List<String[]> refs)
	{
		bc.translation_length = cn.getSize();

		// System.err.println("translation length : " + translation_length);

		// List<String> hyp_ls = Arrays.asList(hyp);
		List<List<String>> ref_lsar = new ArrayList<List<String>>(refs.size());
		for (int i = 0; i < refs.size(); i++)
			ref_lsar.add(Arrays.asList(refs.get(i)));

		// max_ngram_size = max_order
		HashMap<NGram, Integer> RefsCounts = count_max_ngram_ref(ref_lsar);

		/*System.out.println("--------------");
		System.out.println("Ref contains : ");
		for (NGram ngram : RefsCounts.keySet())
		{
			System.out.println(MANYutilities.join(ngram, " ") + " # :" + RefsCounts.get(ngram));
		}
		System.out.println("--------------");
		System.out.flush();*/

		// logger.info("Start counting hyp ngrams ...");
		// HashMap<NGram, Integer> HypCounts = count_ngrams(cn);
		HashMap<NGram, Integer> HypCounts = faster_count_ngrams(cn, RefsCounts);
		// logger.info("End counting hyp ngrams ...");

		/*System.out.println("--------------");
		System.out.println("Hyp contains : ");
		for (NGram ngram : HypCounts.keySet())
		{
			System.out.println(MANYutilities.join(ngram, " ") + " # :" + HypCounts.get(ngram));
		}*/

		/*
		 * for (Integer ngram_id : HypCounts.keySet()) {
		 * System.out.println(MANYutilities.join(id2ngram.get(ngram_id), " ") +
		 * " # :" + HypCounts.get(ngram_id)); }
		 */
		// System.out.println("--------------");

		for (Entry<NGram, Integer> entry : HypCounts.entrySet())
		{
			int order = entry.getKey().getOrder();
			/*System.err.println("NGRAM : " + entry.getKey() + " order : " + entry.getKey().getOrder());*/

			bc.ngram_counts[order] += entry.getValue();

			Integer ref = RefsCounts.get(entry.getKey());
			if (ref != null)
			{
				if (ref >= entry.getValue())
				{
					bc.ngram_counts_clip[order] += entry.getValue();

					/*System.err.println("NGRAM [" + order + "] " + entry.getKey() + " :  NO CLIP to " + entry.getValue()
							+ " (ref=" + ref + ") -> " + "NGRAM_COUNT_CLIP[" + order + "] = "
							+ bc.ngram_counts_clip[order]);*/

				}
				else
				{
					bc.ngram_counts_clip[order] += ref;

					/*System.err.println("NGRAM [" + order + "] " + entry.getKey() + " :  CLIP to " + ref + " (hyp="
							+ entry.getValue() + ") -> " + "NGRAM_COUNT_CLIP[" + order + "] = "
							+ bc.ngram_counts_clip[order]);*/

				}
			}
			else
			{
				/*System.err.println("******************* NGRAM [" + order + "] " + entry.getKey() + " NOT IN REF ...");*/
			}
		}

		int closest_diff = 9999, length;
		bc.closest_ref_length = 9999;
		for (int i = 0; i < ref_lsar.size(); ++i)
		{
			// for brevety : closest ref
			length = ref_lsar.get(i).size();
			int diff = Math.abs(cn.getSize() - length);
			if (diff < closest_diff)
			{
				closest_diff = diff;
				bc.closest_ref_length = length;
				// System.err.println("REF LEN : "+closest_ref_length);
			}
			else if (diff == closest_diff && length < bc.closest_ref_length)
			{
				bc.closest_ref_length = length;
				// System.err.println("REF LEN : "+closest_ref_length);
			}
		}
	}
	/*
	 * public HashMap<NGram, Float> count_ngrams(MANYcn cn) { if (DEBUG)
	 * logger.info("count_ngrams START "); if (cn == null) {
	 * logger.info("Lattice or writer is null"); return null; } else if (DEBUG)
	 * { logger.info("Initialization OK !"); }
	 * 
	 * // to_return contains all n-grams with their probability HashMap<NGram,
	 * Float> ngrams = new HashMap<NGram, Float>();
	 * 
	 * int origine = 0, cible = 1; int i = 0;
	 * 
	 * ArrayList<Node> nodes = new ArrayList<Node>(); Node n = cn.firstNode;
	 * nodes.add(n);
	 * 
	 * Token t = new Token(0.0f, null, n, null, null); TokenList tokens[] = new
	 * TokenList[2]; for (int j = 0; j < tokens.length; j++) tokens[j] = new
	 * TokenList(); TokenList lastTokens = new TokenList(); float sum = 0.0f;
	 * tokens[origine].add(t); int nbIter = 0; while (tokens[origine].isEmpty()
	 * == false) { for (Token pred : tokens[origine]) { //
	 * System.err.println("--AVANT update : "+pred.getHistory()); for (Link l :
	 * pred.getNode().nextLinks) { String ws = cn.getWordFromId(l.getWordId());
	 * // logger.info("## link #"+i+" id="+l.getWordId()+" w="+ws); //
	 * logger.info("next node = "+l.endNode.id); i++; WordSequence ns = null;
	 * float nscore = pred.getScore();
	 * 
	 * // this is a first link, create history if not a null word if
	 * (pred.getHistory() == null) { if (Graph.null_node.equals(ws)) {
	 * 
	 * } else { ns = new WordSequence(new Word[] {new Word(ws, null, false)});
	 * //
	 * logger.info("... Creating word sequence with only "+ws+" gives -> "+ns.
	 * toText()); } } else // not a first link -> update the history if not a
	 * null word { if (Graph.null_node.equals(ws)) { //
	 * logger.info("## link #"+i+" id="+l.getWordId()+" w="+ws); // no need to
	 * add null in the ngrams ... //
	 * logger.info("... Adding "+ws+" (null_node) to the word sequence, -> "
	 * +ns.toText()); ns = new WordSequence(pred.getHistory().getWords()); }
	 * else { // ns = pred.getHistory().addWord(new Word(ws, null, // false),
	 * maxHist); // keep all the history ns = pred.getHistory().addWord(new
	 * Word(ws, null, false), pred.getHistory().size() + 1); //
	 * logger.info("... Adding "
	 * +ws+" to the word sequence, -> "+pred.getHistory()+" to give "+ns); } }
	 * // This is not a null word if (Graph.null_node.equals(ws) == false) { //
	 * logger.info("## link #"+i+" id="+l.getWordId()+" w="+ws); //
	 * logger.info("Link proba : "
	 * +l.getPosterior()+" -> en log :"+logMath.linearToLog(l.getPosterior()));
	 * nscore += logMath.linearToLog(l.getPosterior()); }
	 * 
	 * // System.err.println("--APRES update : "+ns);
	 * 
	 * Token tok = new Token(nscore, pred, l.getEndNode(), ns, null); if
	 * (l.getEndNode() == cn.lastNode) { //
	 * logger.info("adding "+tok.node.time+" to lastTokens");
	 * lastTokens.add(tok); sum = logMath.addAsLinear(sum, tok.getScore());
	 * 
	 * if (tok.getHistory() != null) { //
	 * logger.info("path : "+tok.getHistory().toString()); Word[] wws =
	 * tok.getHistory().getWords(); for (int ll = 0; ll < wws.length; ll++) {
	 * for (int m = 0; m < max_ngram_size; m++) { if (ll + m < wws.length) {
	 * NGram wn = MANYutilities.getNGram(wws, ll, ll + m); if (wn != null) { if
	 * (ngrams.containsKey(wn)) { float sc = ngrams.get(wn); //
	 * System.err.println("Updating '"+MANYutilities.join(wn, //
	 * " ")+"' score = "+(sc+tok.getScore())); ngrams.put(wn,
	 * logMath.addAsLinear(sc, tok.getScore())); } else { //
	 * System.err.println("Adding '"+MANYutilities.join(wn, //
	 * " ")+"' score = "+tok.getScore()); ngrams.put(wn, tok.getScore()); } } }
	 * } } } else logger.info("path : EMPTY !");
	 * 
	 * } else { tokens[cible].add(tok); } } }
	 * 
	 * tokens[origine].clear(); cible = origine; origine = (cible + 1) % 2;
	 * 
	 * System.err.println("End of iteration" + nbIter + ", Nb tokens : " +
	 * tokens[origine].size()); // if(nbIter == 5) System.exit(0);
	 * 
	 * nbIter++; }
	 * 
	 * for (Entry<NGram, Float> entry : ngrams.entrySet()) {
	 * entry.setValue(entry.getValue() - sum); }
	 * 
	 * if (DEBUG) logger.info("count_ngrams END ");
	 * 
	 * return ngrams; }
	 */

	private HashMap<NGram, Integer> count_ngram(List<String> ref)
	{
		/*
		 * System.err.println("count_ngram START"); int j = 0; for (String s :
		 * ref) { System.err.println("REF[" + j + "] : '" + s + "'"); j++; }
		 */

		HashMap<NGram, Integer> cnts = new HashMap<NGram, Integer>();
		ArrayList<NGram> sl = new ArrayList<NGram>();

		for (int a = 0; a < ref.size(); a++) // for each reference
		{
			String[] reftok = ref.get(a).split("\\s+");
			for (String mesh : reftok)
			{
				int prev_size = sl.size(); // number of previous ngrams

				// add all 1-gram of the current mesh at the end of the list
				// System.out.println("Adding word '" + mesh + "' as unigram");
				NGram lst = new NGram(mesh);
				sl.add(lst);

				// extends all previous n-gram (if any)
				for (int i = 0; i < prev_size; i++) // foreach previous ngrams
				{
					// System.out.print("Expending ngram " +
					// MANYutilities.join(sl.get(i), " ") + " to give '");
					NGram newlst = (NGram) sl.get(i).clone();
					newlst.add(mesh);
					// System.out.println(MANYutilities.join(newlst, " ")+"'");
					sl.add(newlst);
				}

				// remove all ngrams already counted
				for (int i = prev_size - 1; i >= 0; i--)
				{
					sl.remove(i);
				}

				// updates counts
				for (int i = sl.size() - 1; i >= 0; i--)
				{
					// remove all n-gram of length len
					if (sl.get(i).getOrder() == BLEUcounts.max_ngram_size)
						sl.remove(i);
					else
					{
						Integer ct = cnts.get(sl.get(i));
						if (ct == null)
						{
							ct = 0;
						}
						cnts.put(sl.get(i), ct + 1);
					}
				}
			}
		}

		/*
		 * for (List<String> ngram : cnts.keySet()) {
		 * System.err.println("REF NGRAM " + MANYutilities.join(ngram, " ") +
		 * " COUNT : " + cnts.get(ngram)); }
		 */

		return cnts;
	}

	private HashMap<NGram, Integer> count_max_ngram_ref(List<List<String>> sens)
	{

		if (DEBUG)
			logger.info("count_max_ngram_ref START sens.size = " + sens.size());

		HashMap<NGram, Integer> to_return = new HashMap<NGram, Integer>();

		for (int i = 0; i < sens.size(); i++)
		{
			// System.err.println("Counting ngrams of ref : " +
			// MANYutilities.join(sens.get(i), " "));
			HashMap<NGram, Integer> rcnt = count_ngram(sens.get(i));

			for (Entry<NGram, Integer> entry : rcnt.entrySet())
			{
				Integer f = to_return.get(entry.getKey());
				if (f == null || f < entry.getValue())
				{
					to_return.put(entry.getKey(), entry.getValue());
				}
			}

			// to_return.add(rcnt);

			/*
			 * System.err.println("yop Ref contains : ");
			 * 
			 * for (List<String> ngram : rcnt.keySet()) {
			 * System.err.println(MANYutilities.join(ngram, " ") + " # :" +
			 * rcnt.get(ngram)); } System.err.println("--------------");
			 */

			/*
			 * if (cnts == null) { cnts = rcnt; } else { for (NGram ls :
			 * rcnt.keySet()) { float r_num = rcnt.get(ls); if
			 * (cnts.containsKey(ls)) { float m_num = cnts.get(ls); if (r_num >
			 * m_num) cnts.put(ls, m_num); } else { cnts.put(ls, r_num); } } }
			 */
		}
		if (DEBUG)
			logger.info("count_ngram_ref END ");

		for (Entry<NGram, Integer> entry : to_return.entrySet())
		{
			try
			{
				bc.ngram_counts_ref[entry.getKey().getOrder()] += entry.getValue();
			}
			catch (ArrayIndexOutOfBoundsException aiob)
			{
				System.err.println("NGRAM : " + entry.getKey() + " order = " + entry.getKey().getOrder());
				aiob.printStackTrace();
				System.exit(0);
			}
		}

		return to_return;
	}
	/*
	 * public HashMap<NGram, Integer> count_ngrams(MANYcn cn) { if (DEBUG)
	 * logger.info("count_ngrams START "); if (cn == null) {
	 * logger.info("Lattice or writer is null"); return null; } else if (DEBUG)
	 * { logger.info("Initialization OK !"); }
	 * 
	 * ArrayList<Node> nodes = new ArrayList<Node>(); Node n = cn.firstNode,
	 * endNode = null; nodes.add(n);
	 * 
	 * NGramToken little_tok = new NGramToken(0.0f, null, n);
	 * 
	 * int nbIter = 0;
	 * 
	 * /* BufferedWriter writer = null; try { writer = new BufferedWriter(new
	 * FileWriter("log.log.log")); } catch (IOException ioe) {
	 * System.err.println("I/O error when creatng file log.log.log " + ioe);
	 * ioe.printStackTrace(); }
	 */
	/*
	 * while (little_tok.getNode() != cn.lastNode) { for (int ll = 0; ll <
	 * little_tok.getNode().nextLinks.size(); ll++) { Link l =
	 * little_tok.getNode().nextLinks.get(ll); String ws =
	 * cn.getWordFromId(l.getWordId());
	 * 
	 * // this is a first link, create history if not a null word if
	 * (Graph.null_node.equals(ws)) { } else { little_tok.extendNGrams(ws);
	 * 
	 * // adding current word as new ngram NGram ngram = new NGram(ws);
	 * little_tok.addNGram(ngram); } if (ll == 0) endNode = l.getEndNode(); else
	 * if (endNode != l.getEndNode()) { logger.info(
	 * "faster_count_ngrams : 2 arcs from the same node do not end at the same node -> not a CN ?"
	 * ); System.exit(0); } }
	 * 
	 * /* try { writer.write("ALL_NGRAMS : "+little_tok.all_ngrams+"\n"); writer
	 * .write("PREVIOUS_NGRAMS : "+little_tok.previous_ngrams+"\n");
	 * writer.write("NEW_NGRAMS : "+little_tok.new_ngrams+"\n");
	 * writer.write("**** END ITERATION " + nbIter + "\n"); writer.flush(); }
	 * catch (Exception e) { e.printStackTrace(); }
	 */
	/*
	 * little_tok.goToNode(endNode); // logger.info("END ITERATION " + nbIter);
	 * // if(nbIter == 10) System.exit(0); nbIter++; }
	 * 
	 * /* try { writer.close(); } catch (IOException e) { e.printStackTrace(); }
	 */

	/*
	 * if (DEBUG) logger.info("count_ngrams END ");
	 * 
	 * return little_tok.all_ngrams; }
	 */

	public HashMap<NGram, Integer> faster_count_ngrams(MANYcn cn, HashMap<NGram, Integer> ref_ngrams)
	{
		if (DEBUG)
			logger.info("count_ngrams START ");
		if (cn == null)
		{
			logger.info("Lattice or writer is null");
			return null;
		}
		else if (DEBUG)
		{
			logger.info("Initialization OK !");
		}

		ArrayList<Node> nodes = new ArrayList<Node>();
		Node n = cn.firstNode, endNode = null;
		nodes.add(n);

		NGramToken little_tok = new NGramToken(null, n);
		int nbIter = 0;

		// bc.ngram_counts = new int[max_ngram_size];
		// int[] prev_ngram_counts = null;
		// int[] new_ngram_counts = new int[max_ngram_size];

		while (little_tok.getNode() != cn.lastNode)
		{
			// prev_ngram_counts = Arrays.copyOf(new_ngram_counts,
			// new_ngram_counts.length);
			// new_ngram_counts = new int[max_ngram_size];

			for (int ll = 0; ll < little_tok.getNode().nextLinks.size(); ll++)
			{
				Link l = little_tok.getNode().nextLinks.get(ll);
				String ws = cn.getWordFromId(l.getWordId());

				if (Graph.null_node.equals(ws))
				{
					// il faut ajouter tous les ngrams de previous ngram dans
					// new ngrams
					little_tok.spreadNGramsOverNullTransition();
				}
				else
				{
					// extends the ngrams
					little_tok.extendUsefulNGrams(ws);
					/*
					 * for (int ne = 0; ne < max_ngram_size - 1; ne++) {
					 * bc.ngram_counts[ne + 1] += prev_ngram_counts[ne];
					 * new_ngram_counts[ne + 1] += prev_ngram_counts[ne]; }
					 */

					// adding current word as new 1-gram
					NGram ngram = new NGram(ws);
					little_tok.addNGram(ngram);
					little_tok.addNewNGram(ngram);
					
					// new_ngram_counts[0] += 1;
					// bc.ngram_counts[0] += 1;
				}
				if (ll == 0)
					endNode = l.getEndNode();
				else if (endNode != l.getEndNode())
				{
					logger
							.info("faster_count_ngrams : 2 arcs from the same node do not end at the same node -> not a CN ?");
					System.exit(0);
				}
			}

			little_tok.goToNode(endNode);
			// logger.info("END ITERATION " + nbIter);
			// if(nbIter == 10) System.exit(0);
			nbIter++;
		}

		if (DEBUG)
			logger.info("count_ngrams END ");

		return little_tok.all_ngrams;
	}

}
